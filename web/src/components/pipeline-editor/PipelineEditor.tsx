"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
    ReactFlow, Controls, MiniMap, Background, BackgroundVariant,
    useNodesState, useEdgesState, addEdge,
    type Connection, type Edge, type Node,
    type ReactFlowInstance,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { Save, Loader2, Trash2, LayoutGrid, GripVertical, AlertTriangle } from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import {
    MemoWorkflowNode, buildNodeTypeDefs, buildReactFlowNodeTypes,
    setCurrentNodeTypeDefs, type WorkflowNodeData, type NodeTypeDef,
} from "./PipelineNodes";
import { useNodeTypeDefinitions, type NodeTypeDefinition } from "@/hooks/use-node-type-definitions";
import { COLOR_THEMES } from "./colorThemes";
import { resolveIcon } from "./iconMap";
import DynamicConfigForm, { type CustomWidgetProps } from "./DynamicConfigForm";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

type Block = {
    id: string; name: string; type: string; activeVersion: number; description: string;
    versions?: { version: number; content: Record<string, unknown> }[];
};

type Pipeline = {
    id: string; name: string; description: string; active: boolean;
    visualNodes?: VisualNodeData[]; visualEdges?: VisualEdgeData[];
};

type VisualNodeData = {
    id: string; type: string; label: string; x: number; y: number;
    blockId?: string; data?: Record<string, string>;
};

type VisualEdgeData = {
    id: string; source: string; target: string;
    sourceHandle?: string; targetHandle?: string; label?: string;
};

/* ------------------------------------------------------------------ */
/*  Legacy blockType -> new nodeType mapping                           */
/* ------------------------------------------------------------------ */

const LEGACY_TYPE_MAP: Record<string, string> = {
    prompt: "aiClassification",
    regex: "piiScanner",
    extractor: "textExtraction",
    router: "condition",
    enforcer: "governance",
    mcp: "trigger",
};

function resolveNodeType(type: string): string {
    return LEGACY_TYPE_MAP[type] ?? type;
}

/* ------------------------------------------------------------------ */
/*  Data-driven helpers (replace hardcoded switch statements)          */
/* ------------------------------------------------------------------ */

/** Map of definitions keyed by key for fast lookup */
function buildDefMap(definitions: NodeTypeDefinition[]): Map<string, NodeTypeDefinition> {
    return new Map(definitions.map(d => [d.key, d]));
}

/** Derive step type from execution category (replaces NODE_TO_STEP_TYPE map) */
function deriveStepType(def: NodeTypeDefinition | undefined): string {
    if (!def) return "BUILT_IN";
    switch (def.executionCategory) {
        case "ACCELERATOR": return "ACCELERATOR";
        case "GENERIC_HTTP": return "ACCELERATOR";
        case "SYNC_LLM": return "SYNC_LLM";
        case "ASYNC_BOUNDARY": return "LLM_PROMPT";
        case "NOOP": return "BUILT_IN";
        default: return "BUILT_IN";
    }
}

/** Simple mustache-style template interpolation */
function interpolate(template: string | undefined, config: Record<string, string>, blockName?: string, blockVersion?: number): string {
    if (!template) return "";
    return template
        .replace(/\{\{(\w+)\}\}/g, (_, key) => {
            if (key === "blockName") return blockName ?? "";
            if (key === "blockVersion") return blockVersion != null ? String(blockVersion) : "?";
            return config[key] ?? "";
        })
        .trim();
}

/** Data-driven summary builder (replaces hardcoded buildSummary switch) */
function buildSummary(
    nodeType: string, config: Record<string, string> | undefined,
    blockName?: string, blockVersion?: number,
    defMap?: Map<string, NodeTypeDefinition>
): string {
    const c = config ?? {};
    const def = defMap?.get(nodeType);
    if (def?.summaryTemplate) {
        const result = interpolate(def.summaryTemplate, c, blockName, blockVersion);
        if (result) return result;
    }
    // Fallback for unconfigured state
    if (def?.compatibleBlockType && !blockName) return "No block selected";
    return "";
}

/** Data-driven validation (replaces hardcoded isNodeConfigured switch) */
function isNodeConfigured(
    nodeType: string, config: Record<string, string> | undefined,
    blockId?: string, defMap?: Map<string, NodeTypeDefinition>
): boolean {
    const def = defMap?.get(nodeType);
    if (!def?.validationExpression) return true; // no validation = always configured
    const expr = def.validationExpression;

    // Simple expression evaluation
    if (expr === "blockId") return !!blockId;
    if (expr.startsWith("config.")) {
        const key = expr.slice(7);
        return !!(config ?? {})[key];
    }
    // Comma-separated: all must be present
    if (expr.includes(",")) {
        return expr.split(",").every(k => !!(config ?? {})[k.trim()]);
    }
    // Single config key
    return !!(config ?? {})[expr];
}

/* ------------------------------------------------------------------ */
/*  Topological sort                                                   */
/* ------------------------------------------------------------------ */

function topoSort(nodeList: Node[], edgeList: Edge[]): Node[] {
    const adj = new Map<string, string[]>();
    const inDegree = new Map<string, number>();
    nodeList.forEach(n => { adj.set(n.id, []); inDegree.set(n.id, 0); });
    edgeList.forEach(e => {
        adj.get(e.source)?.push(e.target);
        inDegree.set(e.target, (inDegree.get(e.target) ?? 0) + 1);
    });

    const queue = nodeList.filter(n => (inDegree.get(n.id) ?? 0) === 0).map(n => n.id);
    const sorted: string[] = [];
    while (queue.length) {
        const id = queue.shift()!;
        sorted.push(id);
        for (const next of (adj.get(id) ?? [])) {
            inDegree.set(next, (inDegree.get(next) ?? 0) - 1);
            if (inDegree.get(next) === 0) queue.push(next);
        }
    }
    const remaining = nodeList.filter(n => !sorted.includes(n.id))
        .sort((a, b) => a.position.y - b.position.y);
    return [...sorted.map(id => nodeList.find(n => n.id === id)!), ...remaining];
}

/* ------------------------------------------------------------------ */
/*  Rules Editor custom widget (escape hatch for complex UI)          */
/* ------------------------------------------------------------------ */

function RulesEditorWidget({ config, onConfigChange }: CustomWidgetProps) {
    let rules: { field: string; operator: string; value: string; categoryName: string; categoryId: string; sensitivityLabel: string; confidence: string }[] = [];
    try { rules = JSON.parse(config.rules || "[]"); } catch { /* empty */ }

    const updateRules = (updated: typeof rules) => onConfigChange("rules", JSON.stringify(updated));
    const addRule = () => updateRules([...rules, { field: "fileName", operator: "contains", value: "", categoryName: "", categoryId: "", sensitivityLabel: "INTERNAL", confidence: "0.90" }]);
    const removeRule = (idx: number) => updateRules(rules.filter((_, i) => i !== idx));
    const updateRule = (idx: number, key: string, val: string) => {
        const copy = [...rules];
        copy[idx] = { ...copy[idx], [key]: val };
        updateRules(copy);
    };

    return (
        <div className="space-y-3">
            {rules.map((rule, idx) => (
                <div key={idx} className="p-2 bg-gray-50 border border-gray-200 rounded-md space-y-1.5">
                    <div className="flex items-center justify-between">
                        <span className="text-[10px] font-bold text-gray-500">Rule {idx + 1}</span>
                        <button onClick={() => removeRule(idx)} className="text-[10px] text-red-500 hover:text-red-700">Remove</button>
                    </div>
                    <select value={rule.field} onChange={e => updateRule(idx, "field", e.target.value)}
                        className="w-full text-xs border border-gray-300 rounded px-2 py-1">
                        <option value="fileName">File Name</option>
                        <option value="mimeType">MIME Type</option>
                        <option value="text">Text Content</option>
                    </select>
                    <select value={rule.operator} onChange={e => updateRule(idx, "operator", e.target.value)}
                        className="w-full text-xs border border-gray-300 rounded px-2 py-1">
                        <option value="contains">Contains</option>
                        <option value="startsWith">Starts With</option>
                        <option value="endsWith">Ends With</option>
                        <option value="matches">Regex Match</option>
                        <option value="equals">Equals</option>
                    </select>
                    <input value={rule.value} onChange={e => updateRule(idx, "value", e.target.value)}
                        placeholder="Value..." className="w-full text-xs border border-gray-300 rounded px-2 py-1" />
                    <input value={rule.categoryName} onChange={e => updateRule(idx, "categoryName", e.target.value)}
                        placeholder="Category name..." className="w-full text-xs border border-gray-300 rounded px-2 py-1" />
                    <select value={rule.sensitivityLabel} onChange={e => updateRule(idx, "sensitivityLabel", e.target.value)}
                        className="w-full text-xs border border-gray-300 rounded px-2 py-1">
                        <option value="PUBLIC">Public</option>
                        <option value="INTERNAL">Internal</option>
                        <option value="CONFIDENTIAL">Confidential</option>
                        <option value="RESTRICTED">Restricted</option>
                    </select>
                </div>
            ))}
            <button onClick={addRule} className="w-full text-xs font-medium text-blue-600 hover:text-blue-800 py-1.5 border border-dashed border-blue-300 rounded-md hover:bg-blue-50 transition-colors">
                + Add Rule
            </button>
            <p className="text-[10px] text-gray-500">
                Rules are evaluated in order. First match wins and skips the LLM.
            </p>
        </div>
    );
}

const CUSTOM_WIDGETS: Record<string, React.ComponentType<CustomWidgetProps>> = {
    rulesEditor: RulesEditorWidget,
};

/* ------------------------------------------------------------------ */
/*  Node Inspector (data-driven)                                       */
/* ------------------------------------------------------------------ */

function NodeInspector({ node, blocks, onUpdate, defMap }: {
    node: Node;
    blocks: Block[];
    onUpdate: (id: string, updates: Partial<WorkflowNodeData>) => void;
    defMap: Map<string, NodeTypeDefinition>;
}) {
    const d = node.data as WorkflowNodeData;
    const nodeType = d.nodeType ?? "textExtraction";
    const config = d.config ?? {};
    const def = defMap.get(nodeType);

    // Test node state
    const [testing, setTesting] = useState(false);
    const [testResult, setTestResult] = useState<Record<string, string | number | boolean | null> | null>(null);

    const setConfig = useCallback((key: string, value: string) => {
        const newConfig = { ...config, [key]: value };
        onUpdate(node.id, { config: newConfig });
    }, [config, node.id, onUpdate]);

    // Block compatibility from definition
    const compatibleBlockTypes = def?.compatibleBlockType ? [def.compatibleBlockType] : [];
    const compatibleBlocks = blocks.filter(b => compatibleBlockTypes.includes(b.type));

    return (
        <div className="w-72 bg-white border-l border-gray-200 overflow-y-auto shrink-0">
            {/* Header */}
            <div className="px-4 py-3 border-b border-gray-200 bg-gray-50">
                <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Node Inspector</h4>
                <p className="text-[10px] text-gray-400 mt-0.5 font-mono">{node.id}</p>
            </div>

            <div className="p-4 space-y-4">
                {/* Label */}
                <div>
                    <label className="text-[10px] font-semibold text-gray-500 uppercase block mb-1">Label</label>
                    <input
                        value={d.label}
                        onChange={e => onUpdate(node.id, { label: e.target.value })}
                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5 focus:ring-2 focus:ring-blue-200 focus:border-blue-400 outline-none"
                    />
                </div>

                {/* Description */}
                <div>
                    <label className="text-[10px] font-semibold text-gray-500 uppercase block mb-1">Description</label>
                    <input
                        value={d.description ?? ""}
                        onChange={e => onUpdate(node.id, { description: e.target.value })}
                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5 focus:ring-2 focus:ring-blue-200 focus:border-blue-400 outline-none"
                        placeholder="Optional description..."
                    />
                </div>

                {/* Performance warning */}
                {def?.performanceWarning && (
                    <div className="flex items-start gap-2 p-2.5 bg-amber-50 border border-amber-200 rounded-md">
                        <AlertTriangle className="size-4 text-amber-500 shrink-0 mt-0.5" />
                        <p className="text-[11px] text-amber-700">{def.performanceWarning}</p>
                    </div>
                )}

                {/* Dynamic config form from schema */}
                <div className="border-t border-gray-100 pt-3">
                    <h5 className="text-[10px] font-semibold text-gray-500 uppercase mb-2">Configuration</h5>
                    <DynamicConfigForm
                        schema={def?.configSchema as Record<string, unknown> | undefined}
                        config={config}
                        onConfigChange={setConfig}
                        customWidgets={CUSTOM_WIDGETS}
                    />
                    {def?.description && (
                        <p className="text-[10px] text-gray-500 mt-2">{def.description}</p>
                    )}
                </div>

                {/* Block selector (for applicable types) */}
                {compatibleBlocks.length > 0 && (
                    <div className="border-t border-gray-100 pt-3">
                        <h5 className="text-[10px] font-semibold text-gray-500 uppercase mb-2">Linked Block</h5>
                        <select
                            value={d.blockId ?? ""}
                            onChange={e => {
                                const blockId = e.target.value || undefined;
                                const block = blockId ? blocks.find(b => b.id === blockId) : undefined;
                                onUpdate(node.id, {
                                    blockId,
                                    blockName: block?.name,
                                    blockVersion: block?.activeVersion,
                                });
                            }}
                            className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5"
                        >
                            <option value="">-- None --</option>
                            {compatibleBlocks.map(b => (
                                <option key={b.id} value={b.id}>{b.name} (v{b.activeVersion})</option>
                            ))}
                        </select>
                        {d.blockId && d.blockVersion && (
                            <div className="mt-1.5 flex items-center justify-between">
                                <span className="text-[10px] text-gray-400">Version: v{d.blockVersion}</span>
                                <a href="/ai/blocks" target="_blank" className="text-[10px] text-blue-500 hover:text-blue-700 font-medium">
                                    Edit in Block Library
                                </a>
                            </div>
                        )}
                    </div>
                )}

                {/* Meta info */}
                <div className="border-t border-gray-100 pt-3">
                    <p className="text-[10px] text-gray-400">Type: <span className="font-semibold capitalize">{nodeType}</span></p>
                    {def?.executionCategory && (
                        <p className="text-[10px] text-gray-400">Execution: <span className="font-semibold">{def.executionCategory}</span></p>
                    )}
                </div>

                {/* Test Node */}
                {def?.executionCategory && def.executionCategory !== "NOOP" && (
                    <div className="border-t border-gray-100 pt-3">
                        <button
                            disabled={testing}
                            onClick={async () => {
                                setTesting(true);
                                setTestResult(null);
                                try {
                                    const { data } = await api.post("/admin/pipelines/test-node", {
                                        nodeType,
                                        config,
                                    });
                                    setTestResult(data);
                                } catch {
                                    setTestResult({ success: false, error: "Request failed" });
                                } finally {
                                    setTesting(false);
                                }
                            }}
                            className="w-full px-3 py-1.5 text-xs font-medium rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-50 flex items-center justify-center gap-1.5"
                        >
                            {testing ? <Loader2 className="size-3 animate-spin" /> : <AlertTriangle className="size-3" />}
                            {testing ? "Testing..." : "Test Node"}
                        </button>
                        {testResult && (
                            <div className={`mt-2 p-2.5 rounded-md text-[11px] ${
                                testResult.success ? "bg-green-50 border border-green-200" : "bg-red-50 border border-red-200"
                            }`}>
                                <p className={`font-semibold ${testResult.success ? "text-green-700" : "text-red-700"}`}>
                                    {testResult.success ? "Pass" : "Fail"}
                                </p>
                                {testResult.message && <p className="text-gray-600 mt-0.5">{String(testResult.message)}</p>}
                                {testResult.error && <p className="text-red-600 mt-0.5">{String(testResult.error)}</p>}
                                {testResult.latencyMs != null && (
                                    <p className="text-gray-500 mt-0.5">Latency: {String(testResult.latencyMs)}ms</p>
                                )}
                                {testResult.classifyStatus != null && (
                                    <p className="text-gray-500 mt-0.5">Classify endpoint: HTTP {String(testResult.classifyStatus)}</p>
                                )}
                                {testResult.classifyResponse && (
                                    <pre className="mt-1 text-[9px] text-gray-500 bg-white rounded p-1.5 overflow-x-auto max-h-32 overflow-y-auto whitespace-pre-wrap break-all">
                                        {String(testResult.classifyResponse)}
                                    </pre>
                                )}
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}

/* ------------------------------------------------------------------ */
/*  Main editor component                                              */
/* ------------------------------------------------------------------ */

export default function PipelineEditor({ pipeline, onSaved }: {
    pipeline: Pipeline; onSaved: () => void;
}) {
    const [blocks, setBlocks] = useState<Block[]>([]);
    const [saving, setSaving] = useState(false);
    const [selectedNode, setSelectedNode] = useState<string | null>(null);
    const reactFlowWrapper = useRef<HTMLDivElement>(null);
    const [rfInstance, setRfInstance] = useState<ReactFlowInstance | null>(null);

    // Load node type definitions from backend
    const { definitions, error: nodeTypeError, refresh: refreshNodeTypes } = useNodeTypeDefinitions();

    // Build derived data structures from definitions
    const defMap = useMemo(() => buildDefMap(definitions), [definitions]);

    const nodeTypeDefs = useMemo(() => {
        const defs = buildNodeTypeDefs(definitions);
        setCurrentNodeTypeDefs(defs); // update the module-level ref for WorkflowNode
        return defs;
    }, [definitions]);

    const reactFlowNodeTypes = useMemo(() => {
        const keys = definitions.map(d => d.key);
        return buildReactFlowNodeTypes(keys, MemoWorkflowNode as unknown as React.ComponentType<import("@xyflow/react").NodeProps>);
    }, [definitions]);

    // Build palette from definitions grouped by category
    const paletteCategories = useMemo(() => {
        const groups = new Map<string, { type: string; label: string; iconName: string }[]>();
        const order: string[] = [];
        for (const def of definitions) {
            if (!groups.has(def.category)) {
                groups.set(def.category, []);
                order.push(def.category);
            }
            groups.get(def.category)!.push({
                type: def.key,
                label: def.displayName,
                iconName: def.iconName,
            });
        }
        return order.map(name => ({
            name: name.replace(/_/g, " "),
            items: groups.get(name)!,
        }));
    }, [definitions]);

    // Load blocks
    useEffect(() => {
        api.get("/admin/blocks").then(({ data }) => setBlocks(data)).catch(() => {});
    }, []);

    // Convert pipeline visual data to React Flow format with legacy compat
    const initialNodes: Node[] = useMemo(() => {
        if (!pipeline.visualNodes?.length) {
            return [
                {
                    id: "trigger-1", type: "trigger", position: { x: 300, y: 0 },
                    data: { label: "File Upload", nodeType: "trigger", config: { triggerType: "upload" }, summary: "Type: upload", configured: true } as WorkflowNodeData,
                },
                {
                    id: "extract-1", type: "textExtraction", position: { x: 300, y: 120 },
                    data: { label: "Text Extraction", nodeType: "textExtraction", config: { extractor: "tika" }, summary: "Tika (default)", configured: true } as WorkflowNodeData,
                },
                {
                    id: "pii-1", type: "piiScanner", position: { x: 300, y: 240 },
                    data: { label: "PII Scanner", nodeType: "piiScanner", summary: "No block selected", configured: false } as WorkflowNodeData,
                },
                {
                    id: "classify-1", type: "aiClassification", position: { x: 300, y: 360 },
                    data: { label: "AI Classification", nodeType: "aiClassification", summary: "No block selected", configured: false } as WorkflowNodeData,
                },
                {
                    id: "condition-1", type: "condition", position: { x: 300, y: 480 },
                    data: { label: "Confidence Check", nodeType: "condition", config: { field: "confidence", operator: ">", value: "0.8" }, summary: "confidence > 0.8", configured: true } as WorkflowNodeData,
                },
                {
                    id: "governance-1", type: "governance", position: { x: 200, y: 620 },
                    data: { label: "Governance", nodeType: "governance", summary: "Retention + policies", configured: true } as WorkflowNodeData,
                },
                {
                    id: "review-1", type: "humanReview", position: { x: 450, y: 620 },
                    data: { label: "Human Review", nodeType: "humanReview", summary: "Manual review", configured: true } as WorkflowNodeData,
                },
            ];
        }
        return pipeline.visualNodes.map(n => {
            const mappedType = resolveNodeType(n.type);
            const block = n.blockId ? blocks.find(b => b.id === n.blockId) : undefined;
            const config = n.data ?? {};
            const nodeData: WorkflowNodeData = {
                label: n.label,
                nodeType: mappedType,
                blockId: n.blockId,
                blockName: block?.name,
                blockVersion: block?.activeVersion,
                description: config.description ?? "",
                config,
                summary: buildSummary(mappedType, config, block?.name, block?.activeVersion, defMap),
                configured: isNodeConfigured(mappedType, config, n.blockId, defMap),
            };
            return {
                id: n.id,
                type: mappedType,
                position: { x: n.x, y: n.y },
                data: nodeData,
            };
        });
    }, [pipeline.visualNodes, blocks, defMap]);

    const initialEdges: Edge[] = useMemo(() => {
        if (!pipeline.visualEdges?.length) {
            return [
                { id: "e-t-ext", source: "trigger-1", target: "extract-1", animated: true, style: { stroke: "#94a3b8", strokeWidth: 2 } },
                { id: "e-ext-pii", source: "extract-1", target: "pii-1", animated: true, style: { stroke: "#94a3b8", strokeWidth: 2 } },
                { id: "e-pii-cls", source: "pii-1", target: "classify-1", animated: true, style: { stroke: "#94a3b8", strokeWidth: 2 } },
                { id: "e-cls-cond", source: "classify-1", target: "condition-1", animated: true, style: { stroke: "#94a3b8", strokeWidth: 2 } },
                { id: "e-cond-gov", source: "condition-1", sourceHandle: "true", target: "governance-1", animated: true, style: { stroke: "#22c55e", strokeWidth: 2 } },
                { id: "e-cond-rev", source: "condition-1", sourceHandle: "false", target: "review-1", animated: true, style: { stroke: "#ef4444", strokeWidth: 2 } },
            ];
        }
        return pipeline.visualEdges.map(e => ({
            id: e.id, source: e.source, target: e.target,
            sourceHandle: e.sourceHandle, targetHandle: e.targetHandle,
            label: e.label, animated: true,
            style: { stroke: "#94a3b8", strokeWidth: 2 },
        }));
    }, [pipeline.visualEdges]);

    const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
    const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

    const onConnect = useCallback((params: Connection) => {
        let strokeColor = "#94a3b8";
        if (params.sourceHandle === "true" || params.sourceHandle === "approved") strokeColor = "#22c55e";
        else if (params.sourceHandle === "false" || params.sourceHandle === "rejected" || params.sourceHandle === "fail") strokeColor = "#ef4444";
        else if (params.sourceHandle === "error") strokeColor = "#ef4444";
        else if (params.sourceHandle === "retry") strokeColor = "#f59e0b";

        setEdges((eds) => addEdge({
            ...params,
            animated: true,
            style: { stroke: strokeColor, strokeWidth: 2 },
        }, eds));
    }, [setEdges]);

    /* ---- Update node data from inspector ---- */
    const updateNodeData = useCallback((nodeId: string, updates: Partial<WorkflowNodeData>) => {
        setNodes(nds => nds.map(n => {
            if (n.id !== nodeId) return n;
            const current = n.data as WorkflowNodeData;
            const merged = { ...current, ...updates };
            if (updates.config || updates.blockId !== undefined || updates.blockName !== undefined) {
                merged.summary = buildSummary(
                    merged.nodeType ?? "textExtraction",
                    merged.config,
                    merged.blockName,
                    merged.blockVersion,
                    defMap,
                );
                merged.configured = isNodeConfigured(
                    merged.nodeType ?? "textExtraction",
                    merged.config,
                    merged.blockId,
                    defMap,
                );
            }
            return { ...n, data: merged };
        }));
    }, [setNodes, defMap]);

    /* ---- Drag-and-drop from palette ---- */
    const onDragOver = useCallback((event: React.DragEvent) => {
        event.preventDefault();
        event.dataTransfer.dropEffect = "move";
    }, []);

    const onDrop = useCallback((event: React.DragEvent) => {
        event.preventDefault();

        const type = event.dataTransfer.getData("application/reactflow-type");
        const label = event.dataTransfer.getData("application/reactflow-label");
        if (!type || !rfInstance || !reactFlowWrapper.current) return;

        const bounds = reactFlowWrapper.current.getBoundingClientRect();
        const position = rfInstance.screenToFlowPosition({
            x: event.clientX - bounds.left,
            y: event.clientY - bounds.top,
        });

        const id = `${type}-${Date.now()}`;
        const nodeData: WorkflowNodeData = {
            label,
            nodeType: type,
            config: {},
            summary: buildSummary(type, {}, undefined, undefined, defMap),
            configured: isNodeConfigured(type, {}, undefined, defMap),
        };
        const newNode: Node = { id, type, position, data: nodeData };
        setNodes(nds => [...nds, newNode]);
    }, [rfInstance, setNodes, defMap]);

    /* ---- Delete selected ---- */
    const deleteSelected = useCallback(() => {
        setNodes(nds => nds.filter(n => !n.selected));
        setEdges(eds => {
            const deletedIds = new Set(nodes.filter(n => n.selected).map(n => n.id));
            return eds.filter(e => !e.selected && !deletedIds.has(e.source) && !deletedIds.has(e.target));
        });
        setSelectedNode(null);
    }, [setNodes, setEdges, nodes]);

    /* ---- Auto layout ---- */
    const autoLayout = useCallback(() => {
        const sorted = topoSort(nodes, edges);
        const Y_GAP = 140;
        const X_CENTER = 300;
        setNodes(sorted.map((n, i) => ({
            ...n,
            position: { x: X_CENTER, y: i * Y_GAP },
        })));
    }, [nodes, edges, setNodes]);

    /* ---- Save ---- */
    const handleSave = useCallback(async () => {
        // Warn about multiple LLM nodes
        const llmNodeCount = nodes.filter(n => {
            const nt = (n.data as WorkflowNodeData).nodeType;
            const d = defMap.get(nt ?? "");
            return d?.performanceImpact === "high";
        }).length;

        if (llmNodeCount > 1) {
            const minTime = llmNodeCount * 10;
            const maxTime = llmNodeCount * 30;
            if (!window.confirm(
                `This pipeline has ${llmNodeCount} LLM nodes. Each adds 10-30 seconds of processing time.\n\n` +
                `Estimated total: ${minTime}-${maxTime} seconds per document.\n\nSave anyway?`
            )) {
                return;
            }
        }

        setSaving(true);
        try {
            const visualNodes: VisualNodeData[] = nodes.map(n => {
                const d = n.data as WorkflowNodeData;
                return {
                    id: n.id,
                    type: d.nodeType ?? n.type ?? "textExtraction",
                    label: d.label,
                    x: n.position.x,
                    y: n.position.y,
                    blockId: d.blockId,
                    data: { ...(d.config ?? {}), description: d.description ?? "" },
                };
            });

            const visualEdges: VisualEdgeData[] = edges.map(e => ({
                id: e.id, source: e.source, target: e.target,
                sourceHandle: e.sourceHandle ?? undefined,
                targetHandle: e.targetHandle ?? undefined,
                label: typeof e.label === "string" ? e.label : undefined,
            }));

            const orderedNodes = topoSort(nodes, edges);
            const steps = orderedNodes.map((n, i) => {
                const d = n.data as WorkflowNodeData;
                const nt = d.nodeType ?? "textExtraction";
                const block = d.blockId ? blocks.find(b => b.id === d.blockId) : undefined;
                const def = defMap.get(nt);
                return {
                    order: i + 1,
                    name: d.label,
                    description: d.description ?? "",
                    type: deriveStepType(def),
                    enabled: true,
                    blockId: d.blockId ?? null,
                    blockVersion: block?.activeVersion ?? null,
                    config: d.config ?? {},
                };
            });

            await api.put(`/admin/pipelines/${pipeline.id}`, {
                ...pipeline, visualNodes, visualEdges, steps,
            });

            toast.success("Pipeline saved");
            onSaved();
        } catch { toast.error("Save failed"); }
        finally { setSaving(false); }
    }, [nodes, edges, blocks, pipeline, onSaved, defMap]);

    /* ---- MiniMap color (data-driven from colorTheme) ---- */
    const minimapColor = useCallback((n: Node) => {
        const nt = (n.data as WorkflowNodeData)?.nodeType ?? n.type ?? "";
        const def = defMap.get(nt);
        const theme = def ? COLOR_THEMES[def.colorTheme] : undefined;
        return theme?.minimap ?? "#6b7280";
    }, [defMap]);

    const selectedNodeObj = useMemo(() => {
        if (!selectedNode) return null;
        return nodes.find(n => n.id === selectedNode) ?? null;
    }, [selectedNode, nodes]);

    return (
        <div className="h-full flex flex-col">
            {/* Toolbar */}
            <div className="flex items-center justify-between px-4 py-2 bg-white border-b border-gray-200 shrink-0">
                <div className="flex items-center gap-2">
                    <h3 className="text-sm font-semibold text-gray-900">{pipeline.name}</h3>
                    <span className={`px-2 py-0.5 text-[10px] font-medium rounded-full ${pipeline.active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>
                        {pipeline.active ? "Active" : "Inactive"}
                    </span>
                </div>
                <div className="flex items-center gap-2">
                    <button onClick={autoLayout}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-gray-300 text-gray-700 text-xs font-medium rounded-md hover:bg-gray-50 transition-colors">
                        <LayoutGrid className="size-3" /> Auto Layout
                    </button>
                    <button onClick={deleteSelected}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-red-200 text-red-600 text-xs font-medium rounded-md hover:bg-red-50 transition-colors">
                        <Trash2 className="size-3" /> Delete
                    </button>
                    <button onClick={handleSave} disabled={saving}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-md hover:bg-blue-700 disabled:opacity-50 transition-colors">
                        {saving ? <Loader2 className="size-3 animate-spin" /> : <Save className="size-3" />} Save
                    </button>
                </div>
            </div>

            {/* 3-column layout: Palette | Canvas | Inspector */}
            <div className="flex-1 flex min-h-0">
                {/* Left: Node Palette */}
                <div className="w-56 bg-white border-r border-gray-200 overflow-y-auto shrink-0">
                    <div className="px-3 py-2.5 border-b border-gray-100">
                        <h4 className="text-[10px] font-semibold text-gray-500 uppercase tracking-wider">Node Palette</h4>
                    </div>
                    <div className="p-2 space-y-3">
                        {nodeTypeError && (
                            <div className="px-2 py-2 bg-red-50 border border-red-200 rounded text-[10px] text-red-700">
                                <p>{nodeTypeError}</p>
                                <button onClick={refreshNodeTypes} className="mt-1 text-red-600 underline hover:text-red-800">Retry</button>
                            </div>
                        )}
                        {paletteCategories.map(cat => (
                            <div key={cat.name}>
                                <h5 className="text-[9px] font-bold text-gray-400 uppercase tracking-widest px-1 mb-1.5">{cat.name}</h5>
                                <div className="space-y-1">
                                    {cat.items.map(item => {
                                        const ntDef = nodeTypeDefs[item.type];
                                        const Icon = resolveIcon(item.iconName);
                                        return (
                                            <div
                                                key={item.type}
                                                draggable
                                                onDragStart={(e) => {
                                                    e.dataTransfer.setData("application/reactflow-type", item.type);
                                                    e.dataTransfer.setData("application/reactflow-label", item.label);
                                                    e.dataTransfer.effectAllowed = "move";
                                                }}
                                                className={`flex items-center gap-2 px-2.5 py-2 rounded-md cursor-grab active:cursor-grabbing border border-transparent hover:border-gray-200 hover:bg-gray-50 transition-colors select-none ${ntDef?.bg ?? "bg-gray-50"}`}
                                            >
                                                <GripVertical className="size-3 text-gray-300 shrink-0" />
                                                <div className={`size-6 rounded flex items-center justify-center shrink-0 ${ntDef?.iconBg ?? ""} ${ntDef?.text ?? "text-gray-500"}`}>
                                                    <Icon className="size-3.5" />
                                                </div>
                                                <span className="text-xs font-medium text-gray-700 truncate">{item.label}</span>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Center: Canvas */}
                <div className="flex-1" ref={reactFlowWrapper}>
                    <ReactFlow
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
                        onEdgesChange={onEdgesChange}
                        onConnect={onConnect}
                        onInit={setRfInstance}
                        onNodeClick={(_, node) => setSelectedNode(node.id)}
                        onPaneClick={() => setSelectedNode(null)}
                        onDrop={onDrop}
                        onDragOver={onDragOver}
                        nodeTypes={reactFlowNodeTypes}
                        fitView
                        snapToGrid
                        snapGrid={[20, 20]}
                        deleteKeyCode={["Backspace", "Delete"]}
                        className="bg-gray-50"
                    >
                        <Controls position="bottom-left" className="!bg-white !border-gray-200 !shadow-sm" />
                        <MiniMap position="bottom-right" className="!bg-white !border-gray-200" nodeColor={minimapColor} />
                        <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="#e5e7eb" />
                    </ReactFlow>
                </div>

                {/* Right: Inspector */}
                {selectedNodeObj && (
                    <NodeInspector
                        node={selectedNodeObj}
                        blocks={blocks}
                        onUpdate={updateNodeData}
                        defMap={defMap}
                    />
                )}
            </div>
        </div>
    );
}
