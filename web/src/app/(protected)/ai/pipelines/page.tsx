"use client";

import dynamic from "next/dynamic";
import { useCallback, useEffect, useState } from "react";
import {
    Workflow, Plus, Pencil, Trash2, Save, X, Loader2,
    ChevronDown, ChevronRight, GripVertical, Play, Pause,
    Shield, Brain, Scan, Cog, GitBranch, AlertTriangle, HelpCircle, Info,
    LayoutGrid, List,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import BlockFormEditor from "@/components/block-form-editor";
import FormModal, { FormField } from "@/components/form-modal";

// Dynamic import for React Flow (SSR incompatible)
const PipelineEditor = dynamic(() => import("@/components/pipeline-editor/PipelineEditor"), { ssr: false });

type StepType = "BUILT_IN" | "PATTERN" | "LLM_PROMPT" | "CONDITIONAL" | "ACCELERATOR" | "SYNC_LLM";

type PipelineStep = {
    order: number;
    name: string;
    description: string;
    type: StepType;
    enabled: boolean;
    blockId?: string;
    blockVersion?: number;
    systemPrompt?: string;
    userPromptTemplate?: string;
    condition?: string;
    config: Record<string, string>;
};

type Block = {
    id: string;
    name: string;
    description: string;
    type: string;
    active: boolean;
    activeVersion: number;
    versions: { version: number; content: Record<string, unknown>; changelog: string; publishedBy: string; publishedAt: string }[];
    draftContent?: Record<string, unknown>;
    feedbackCount: number;
};

type Pipeline = {
    id?: string;
    name: string;
    description: string;
    active: boolean;
    isDefault?: boolean;
    applicableCategoryIds?: string[];
    includeSubCategories?: boolean;
    applicableMimeTypes?: string[];
    steps: PipelineStep[];
    createdAt?: string;
    updatedAt?: string;
};

type Category = { id: string; name: string; parentId?: string };
type OverlapMap = Record<string, { pipelineId: string; pipelineName: string; directMatch: boolean }[]>;

const STEP_ICONS: Record<StepType, React.ElementType> = {
    BUILT_IN: Cog,
    PATTERN: Scan,
    LLM_PROMPT: Brain,
    CONDITIONAL: GitBranch,
    ACCELERATOR: Cog,
    SYNC_LLM: Brain,
};

const STEP_COLORS: Record<StepType, string> = {
    BUILT_IN: "bg-gray-100 text-gray-700 border-gray-200",
    PATTERN: "bg-blue-50 text-blue-700 border-blue-200",
    LLM_PROMPT: "bg-purple-50 text-purple-700 border-purple-200",
    CONDITIONAL: "bg-amber-50 text-amber-700 border-amber-200",
    ACCELERATOR: "bg-violet-50 text-violet-700 border-violet-200",
    SYNC_LLM: "bg-indigo-50 text-indigo-700 border-indigo-200",
};

export default function PipelinesPage() {
    const [pipelines, setPipelines] = useState<Pipeline[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);
    const [overlaps, setOverlaps] = useState<OverlapMap>({});
    const [blocks, setBlocks] = useState<Block[]>([]);
    const [editing, setEditing] = useState<Pipeline | null>(null);
    const [expanded, setExpanded] = useState<string | null>(null);
    const [visualEditorPipeline, setVisualEditorPipeline] = useState<Pipeline | null>(null);
    const [saving, setSaving] = useState(false);
    const [scanning, setScanning] = useState(false);
    const [showTemplates, setShowTemplates] = useState(false);
    const [newName, setNewName] = useState("");
    const [newDesc, setNewDesc] = useState("");

    const load = useCallback(async () => {
        try {
            const [p, c, o, b] = await Promise.all([
                api.get("/admin/pipelines"),
                api.get("/admin/governance/taxonomy"),
                api.get("/admin/pipelines/overlap-check"),
                api.get("/admin/blocks/all"),
            ]);
            setPipelines(p.data);
            setCategories(c.data);
            setOverlaps(o.data);
            setBlocks(b.data.filter((bl: Block) => bl.active));
        } catch { toast.error("Failed to load pipelines"); }
    }, []);
    useEffect(() => { load(); }, [load]);

    const handleSave = async () => {
        if (!editing || !editing.name.trim()) return;
        setSaving(true);
        try {
            if (editing.id) { await api.put(`/admin/pipelines/${editing.id}`, editing); }
            else { await api.post("/admin/pipelines", editing); }
            toast.success(`Pipeline ${editing.id ? "updated" : "created"}`);
            setEditing(null); load();
        } catch { toast.error("Save failed"); }
        finally { setSaving(false); }
    };

    const handleBatchPiiScan = async () => {
        setScanning(true);
        try {
            const { data } = await api.post("/admin/pipelines/pii-scan/batch");
            toast.success(`Scanned ${data.scanned} documents, found ${data.piiEntitiesFound} PII entities`);
        } catch { toast.error("Batch scan failed"); }
        finally { setScanning(false); }
    };

    const newPipeline = (): Pipeline => ({
        name: "", description: "", active: true, isDefault: false,
        applicableCategoryIds: [], includeSubCategories: true,
        applicableMimeTypes: [], steps: [],
    });

    const newStep = (order: number): PipelineStep => ({
        order, name: "", description: "", type: "BUILT_IN", enabled: true, config: {},
    });

    // ── Pipeline templates ──────────────────────────────────────────
    type PipelineTemplate = { name: string; description: string; icon: React.ElementType; nodes: { id: string; type: string; label: string; x: number; y: number }[]; edges: { id: string; source: string; target: string; sourceHandle?: string }[] };

    const TEMPLATES: PipelineTemplate[] = [
        {
            name: "Blank", description: "Empty canvas — build from scratch", icon: Plus,
            nodes: [{ id: "n1", type: "trigger", label: "Trigger", x: 250, y: 0 }],
            edges: [],
        },
        {
            name: "Standard Classification", description: "Full pipeline: extract, PII scan, classify, condition routing, governance + review", icon: Brain,
            nodes: [
                { id: "n1", type: "trigger", label: "Trigger", x: 250, y: 0 },
                { id: "n2", type: "textExtraction", label: "Text Extraction", x: 250, y: 120 },
                { id: "n3", type: "piiScanner", label: "PII Scanner", x: 250, y: 240 },
                { id: "n4", type: "aiClassification", label: "AI Classification", x: 250, y: 360 },
                { id: "n5", type: "condition", label: "Confidence Check", x: 250, y: 480 },
                { id: "n6", type: "governance", label: "Governance", x: 100, y: 600 },
                { id: "n7", type: "humanReview", label: "Human Review", x: 400, y: 600 },
            ],
            edges: [
                { id: "e1", source: "n1", target: "n2" },
                { id: "e2", source: "n2", target: "n3" },
                { id: "e3", source: "n3", target: "n4" },
                { id: "e4", source: "n4", target: "n5" },
                { id: "e5", source: "n5", target: "n6", sourceHandle: "true" },
                { id: "e6", source: "n5", target: "n7", sourceHandle: "false" },
            ],
        },
        {
            name: "Quick Classify", description: "Fast pipeline: extract, classify, enforce", icon: Workflow,
            nodes: [
                { id: "n1", type: "trigger", label: "Trigger", x: 250, y: 0 },
                { id: "n2", type: "textExtraction", label: "Text Extraction", x: 250, y: 120 },
                { id: "n3", type: "aiClassification", label: "AI Classification", x: 250, y: 240 },
                { id: "n4", type: "governance", label: "Governance", x: 250, y: 360 },
            ],
            edges: [
                { id: "e1", source: "n1", target: "n2" },
                { id: "e2", source: "n2", target: "n3" },
                { id: "e3", source: "n3", target: "n4" },
            ],
        },
        {
            name: "Accelerated", description: "BERT + fingerprint before LLM — saves cost on repeat documents", icon: Cog,
            nodes: [
                { id: "n1", type: "trigger", label: "Trigger", x: 250, y: 0 },
                { id: "n2", type: "textExtraction", label: "Text Extraction", x: 250, y: 100 },
                { id: "n3", type: "piiScanner", label: "PII Scanner", x: 250, y: 200 },
                { id: "n4", type: "templateFingerprint", label: "Template Fingerprint", x: 250, y: 300 },
                { id: "n5", type: "bertClassifier", label: "BERT Classifier", x: 250, y: 400 },
                { id: "n6", type: "aiClassification", label: "AI Classification", x: 250, y: 500 },
                { id: "n7", type: "condition", label: "Confidence Check", x: 250, y: 620 },
                { id: "n8", type: "governance", label: "Governance", x: 100, y: 740 },
                { id: "n9", type: "humanReview", label: "Human Review", x: 400, y: 740 },
            ],
            edges: [
                { id: "e1", source: "n1", target: "n2" },
                { id: "e2", source: "n2", target: "n3" },
                { id: "e3", source: "n3", target: "n4" },
                { id: "e4", source: "n4", target: "n5" },
                { id: "e5", source: "n5", target: "n6" },
                { id: "e6", source: "n6", target: "n7" },
                { id: "e7", source: "n7", target: "n8", sourceHandle: "true" },
                { id: "e8", source: "n7", target: "n9", sourceHandle: "false" },
            ],
        },
    ];

    const handleCreateFromTemplate = async (template: PipelineTemplate) => {
        if (!newName.trim()) { toast.error("Pipeline name is required"); return; }
        setSaving(true);
        try {
            const payload = {
                name: newName.trim(),
                description: newDesc.trim(),
                active: true,
                isDefault: false,
                applicableCategoryIds: [],
                includeSubCategories: true,
                steps: [],
                visualNodes: template.nodes.map(n => ({ ...n, data: {} })),
                visualEdges: template.edges,
            };
            const { data } = await api.post("/admin/pipelines", payload);
            toast.success("Pipeline created");
            setShowTemplates(false);
            setNewName("");
            setNewDesc("");
            await load();
            // Open visual editor immediately
            setVisualEditorPipeline(data);
        } catch { toast.error("Failed to create pipeline"); }
        finally { setSaving(false); }
    };

    // Full-screen visual editor mode
    if (visualEditorPipeline) {
        return (
            <div className="h-[calc(100vh-120px)] -m-6 flex flex-col">
                <div className="flex items-center justify-between px-6 py-3 bg-white border-b border-gray-200 shrink-0">
                    <button onClick={() => setVisualEditorPipeline(null)}
                        className="text-sm text-blue-600 hover:text-blue-800">&larr; Back to pipelines</button>
                    <span className="text-sm text-gray-500">Visual Editor: <strong>{visualEditorPipeline.name}</strong></span>
                </div>
                <div className="flex-1">
                    <PipelineEditor pipeline={{ ...visualEditorPipeline, id: visualEditorPipeline.id! }} onSaved={() => { load(); }} />
                </div>
            </div>
        );
    }

    return (
        <>
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">AI Pipelines</h2>
                    <p className="text-sm text-gray-500 mt-1">Manage document processing workflows and AI steps</p>
                </div>
                <div className="flex gap-3">
                    <button onClick={handleBatchPiiScan} disabled={scanning}
                        className="inline-flex items-center gap-2 px-3 py-2 border border-gray-300 text-sm text-gray-700 rounded-lg hover:bg-gray-50 disabled:opacity-50">
                        {scanning ? <Loader2 className="size-4 animate-spin" /> : <Scan className="size-4" />}
                        Batch PII Scan
                    </button>
                    <button onClick={() => { setShowTemplates(true); setNewName(""); setNewDesc(""); }}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700">
                        <Plus className="size-4" /> New Pipeline
                    </button>
                </div>
            </div>

            {/* Pipeline Editor Modal */}
            <FormModal title={editing?.id ? "Edit Pipeline" : "New Pipeline"} open={!!editing} onClose={() => setEditing(null)} width="2xl"
                footer={editing ? <>
                    <button onClick={handleSave} disabled={saving}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-md hover:bg-blue-700">
                        {saving ? <Loader2 className="size-3.5 animate-spin" /> : <Save className="size-3.5" />} Save
                    </button>
                    <button onClick={() => setEditing(null)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-gray-300 text-gray-700 text-xs font-medium rounded-md hover:bg-gray-50">
                        Cancel
                    </button>
                </> : undefined}>
                {editing && (<>
                    <div className="grid grid-cols-2 gap-4">
                        <FormField label="Name" id="pipe-name" required>
                            <input id="pipe-name" value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })}
                                className="w-full text-sm border border-gray-300 rounded-md px-3 py-1.5" />
                        </FormField>
                        <FormField label="Active" id="pipe-active">
                            <select id="pipe-active" value={String(editing.active)} onChange={(e) => setEditing({ ...editing, active: e.target.value === "true" })}
                                className="w-full text-sm border border-gray-300 rounded-md px-3 py-1.5">
                                <option value="true">Active</option><option value="false">Inactive</option>
                            </select>
                        </FormField>
                    </div>
                    <FormField label="Description" id="pipe-desc">
                        <input id="pipe-desc" value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })}
                            className="w-full text-sm border border-gray-300 rounded-md px-3 py-1.5" />
                    </FormField>

                    {/* Taxonomy Binding */}
                    <div className="space-y-3">
                        <label className="text-xs font-semibold text-gray-700 uppercase block">Applicable Categories</label>
                        <p className="text-xs text-gray-500 -mt-2">Select which taxonomy categories this pipeline handles.</p>

                        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Category selection">
                            {categories.map(cat => {
                                const selected = editing.applicableCategoryIds?.includes(cat.id);
                                return (
                                    <button key={cat.id}
                                        onClick={() => {
                                            const ids = editing.applicableCategoryIds ?? [];
                                            const next = selected ? ids.filter(id => id !== cat.id) : [...ids, cat.id];
                                            setEditing({ ...editing, applicableCategoryIds: next });
                                        }}
                                        role="checkbox" aria-checked={selected}
                                        className={`px-2 py-1 text-xs rounded-md border transition-colors ${
                                            selected ? "bg-blue-100 text-blue-700 border-blue-200" : "bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100"
                                        }`}>
                                        {cat.name}
                                    </button>
                                );
                            })}
                        </div>

                        <div className="flex items-center gap-4">
                            <label htmlFor="pipe-include-subs" className="flex items-center gap-2 text-xs text-gray-700">
                                <input id="pipe-include-subs" type="checkbox" checked={editing.includeSubCategories ?? true}
                                    onChange={(e) => setEditing({ ...editing, includeSubCategories: e.target.checked })}
                                    className="rounded border-gray-300 text-blue-600" />
                                Include sub-categories
                            </label>
                            <label htmlFor="pipe-is-default" className="flex items-center gap-2 text-xs text-gray-700">
                                <input id="pipe-is-default" type="checkbox" checked={editing.isDefault ?? false}
                                    onChange={(e) => setEditing({ ...editing, isDefault: e.target.checked })}
                                    className="rounded border-gray-300 text-blue-600" />
                                Default pipeline (fallback when no category matches)
                            </label>
                        </div>

                        {/* Overlap warning */}
                        {editing.applicableCategoryIds && editing.applicableCategoryIds.length > 0 && (() => {
                            const myOverlaps = editing.applicableCategoryIds
                                .filter(catId => overlaps[catId] && overlaps[catId].some(o => o.pipelineId !== editing.id))
                                .map(catId => ({
                                    category: categories.find(c => c.id === catId)?.name ?? catId,
                                    pipelines: overlaps[catId].filter(o => o.pipelineId !== editing.id).map(o => o.pipelineName),
                                }));
                            if (myOverlaps.length === 0) return null;
                            return (
                                <div className="bg-amber-50 border border-amber-200 rounded-md p-3" role="alert">
                                    <div className="flex items-center gap-1.5 text-xs font-semibold text-amber-800 mb-1">
                                        <AlertTriangle className="size-3.5" /> Overlap Detected
                                    </div>
                                    <div className="text-xs text-amber-700 space-y-0.5">
                                        {myOverlaps.map(o => (
                                            <div key={o.category}>
                                                <strong>{o.category}</strong> is also covered by: {o.pipelines.join(", ")}
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            );
                        })()}
                    </div>

                    {/* Steps */}
                    <div>
                        <div className="flex items-center justify-between mb-2">
                            <label className="text-xs font-semibold text-gray-700 uppercase">Pipeline Steps</label>
                            <button onClick={() => setEditing({ ...editing, steps: [...editing.steps, newStep(editing.steps.length + 1)] })}
                                className="text-xs text-blue-600 hover:text-blue-800 flex items-center gap-1">
                                <Plus className="size-3" /> Add Step
                            </button>
                        </div>
                        <div className="space-y-2">
                            {editing.steps.sort((a, b) => a.order - b.order).map((step, i) => (
                                <StepEditor key={i} step={step} index={i} blocks={blocks}
                                    onChange={(updated) => {
                                        const steps = [...editing.steps];
                                        steps[i] = updated;
                                        setEditing({ ...editing, steps });
                                    }}
                                    onRemove={() => setEditing({ ...editing, steps: editing.steps.filter((_, j) => j !== i).map((s, j) => ({ ...s, order: j + 1 })) })}
                                />
                            ))}
                        </div>
                    </div>
                </>)}
            </FormModal>

            {/* Template Picker Modal */}
            <FormModal title="Create New Pipeline" open={showTemplates} onClose={() => setShowTemplates(false)} width="2xl"
                footer={<button onClick={() => setShowTemplates(false)}
                    className="px-3 py-1.5 border border-gray-300 text-gray-700 text-xs font-medium rounded-md hover:bg-gray-50">Cancel</button>}>
                <div className="space-y-4">
                    <div className="grid grid-cols-2 gap-3">
                        <FormField label="Pipeline Name" id="tpl-name" required>
                            <input id="tpl-name" value={newName} onChange={e => setNewName(e.target.value)}
                                placeholder="e.g. HR Document Pipeline"
                                className="w-full text-sm border border-gray-300 rounded-md px-3 py-1.5" />
                        </FormField>
                        <FormField label="Description" id="tpl-desc">
                            <input id="tpl-desc" value={newDesc} onChange={e => setNewDesc(e.target.value)}
                                placeholder="Optional description"
                                className="w-full text-sm border border-gray-300 rounded-md px-3 py-1.5" />
                        </FormField>
                    </div>
                    <p className="text-xs font-semibold text-gray-700 uppercase">Choose a Template</p>
                    <div className="grid grid-cols-2 gap-3">
                        {TEMPLATES.map((tpl) => {
                            const TplIcon = tpl.icon;
                            return (
                                <button key={tpl.name} onClick={() => handleCreateFromTemplate(tpl)} disabled={saving || !newName.trim()}
                                    className="flex items-start gap-3 p-4 border border-gray-200 rounded-lg hover:border-blue-400 hover:bg-blue-50/50 text-left transition disabled:opacity-50 disabled:cursor-not-allowed">
                                    <div className="size-10 rounded-lg bg-blue-100 flex items-center justify-center shrink-0">
                                        <TplIcon className="size-5 text-blue-600" />
                                    </div>
                                    <div>
                                        <p className="text-sm font-medium text-gray-900">{tpl.name}</p>
                                        <p className="text-xs text-gray-500 mt-0.5">{tpl.description}</p>
                                        <p className="text-[10px] text-gray-400 mt-1">{tpl.nodes.length} nodes</p>
                                    </div>
                                </button>
                            );
                        })}
                    </div>
                </div>
            </FormModal>

            {/* Pipeline List */}
            <div className="space-y-4">
                {pipelines.map((p) => (
                    <div key={p.id} className="bg-white rounded-lg shadow-sm border border-gray-200">
                        <div className="flex items-center justify-between px-6 py-4 cursor-pointer hover:bg-gray-50"
                            onClick={() => setExpanded(expanded === p.id ? null : p.id!)}>
                            <div className="flex items-center gap-3">
                                <Workflow className="size-5 text-blue-600" />
                                <div>
                                    <div className="flex items-center gap-2 flex-wrap">
                                        <h4 className="font-medium text-gray-900">{p.name}</h4>
                                        <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${p.active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>
                                            {p.active ? "Active" : "Inactive"}
                                        </span>
                                        {p.isDefault && <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-blue-100 text-blue-700">Default</span>}
                                        <span className="text-xs text-gray-400">{p.steps.length} steps</span>
                                        {p.applicableCategoryIds && p.applicableCategoryIds.length > 0 && (
                                            <span className="text-xs text-gray-400">
                                                {p.applicableCategoryIds.length} categor{p.applicableCategoryIds.length === 1 ? "y" : "ies"}
                                                {p.includeSubCategories ? " + subs" : ""}
                                            </span>
                                        )}
                                    </div>
                                    <div className="flex items-center gap-2 mt-0.5">
                                        <p className="text-sm text-gray-500">{p.description}</p>
                                    </div>
                                    {p.applicableCategoryIds && p.applicableCategoryIds.length > 0 && (
                                        <div className="flex flex-wrap gap-1 mt-1">
                                            {p.applicableCategoryIds.map(catId => {
                                                const cat = categories.find(c => c.id === catId);
                                                const hasOverlap = overlaps[catId] && overlaps[catId].length > 1;
                                                return (
                                                    <span key={catId} className={`px-1.5 py-0.5 text-[10px] rounded ${
                                                        hasOverlap ? "bg-amber-100 text-amber-700" : "bg-blue-50 text-blue-600"
                                                    }`}>
                                                        {cat?.name ?? catId}{hasOverlap ? " ⚠" : ""}
                                                    </span>
                                                );
                                            })}
                                        </div>
                                    )}
                                </div>
                            </div>
                            <div className="flex items-center gap-2">
                                <button onClick={(e) => { e.stopPropagation(); setVisualEditorPipeline(p); }}
                                    className="p-1.5 text-gray-400 hover:text-blue-600 rounded" title="Visual Editor">
                                    <LayoutGrid className="size-4" /></button>
                                <button onClick={(e) => { e.stopPropagation(); setEditing({ ...p }); }}
                                    className="p-1.5 text-gray-400 hover:text-gray-600 rounded" title="Edit Steps">
                                    <Pencil className="size-4" /></button>
                                {expanded === p.id ? <ChevronDown className="size-5 text-gray-400" /> : <ChevronRight className="size-5 text-gray-400" />}
                            </div>
                        </div>

                        {expanded === p.id && (
                            <div className="px-6 pb-4 border-t border-gray-100 pt-4">
                                <div className="relative">
                                    {p.steps.sort((a, b) => a.order - b.order).map((step, i) => {
                                        const Icon = STEP_ICONS[step.type];
                                        return (
                                            <div key={i} className="flex items-start gap-3 mb-4 last:mb-0">
                                                {/* Connector line */}
                                                <div className="flex flex-col items-center">
                                                    <div className={`size-8 rounded-full flex items-center justify-center border-2 ${step.enabled ? STEP_COLORS[step.type] : "bg-gray-50 text-gray-400 border-gray-200"}`}>
                                                        <Icon className="size-4" />
                                                    </div>
                                                    {i < p.steps.length - 1 && <div className="w-0.5 h-6 bg-gray-200 mt-1" />}
                                                </div>
                                                <div className={`flex-1 p-3 rounded-lg border ${step.enabled ? "bg-white border-gray-200" : "bg-gray-50 border-gray-100 opacity-60"}`}>
                                                    <div className="flex items-center gap-2">
                                                        <span className="text-sm font-medium text-gray-900">{step.name}</span>
                                                        <span className={`px-1.5 py-0.5 text-[10px] font-medium rounded ${STEP_COLORS[step.type]}`}>{step.type.replace(/_/g, " ")}</span>
                                                        {!step.enabled && <span className="px-1.5 py-0.5 text-[10px] bg-gray-200 text-gray-500 rounded">Disabled</span>}
                                                    </div>
                                                    <p className="text-xs text-gray-500 mt-1">{step.description}</p>
                                                    {step.blockId && (() => {
                                                        const bl = blocks.find(b => b.id === step.blockId);
                                                        if (!bl) return null;
                                                        const bi = BLOCK_TYPE_ICONS[bl.type];
                                                        const BIcon = bi?.icon ?? Cog;
                                                        return (
                                                            <div className="flex items-center gap-1.5 mt-1">
                                                                <BIcon className={`size-3 ${bi?.color ?? "text-gray-500"}`} />
                                                                <a href="/ai/blocks" className="text-[10px] text-blue-600 hover:text-blue-800 underline">
                                                                    {bl.name} v{step.blockVersion ?? bl.activeVersion}
                                                                </a>
                                                                {bl.feedbackCount > 0 && (
                                                                    <span className="px-1 py-0.5 text-[9px] bg-amber-100 text-amber-700 rounded-full">{bl.feedbackCount}</span>
                                                                )}
                                                            </div>
                                                        );
                                                    })()}
                                                    {step.condition && <p className="text-xs text-amber-600 mt-1">Condition: {step.condition}</p>}
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        )}
                    </div>
                ))}
            </div>
        </>
    );
}

// Map step type → compatible block types
const STEP_BLOCK_TYPES: Record<StepType, string[]> = {
    LLM_PROMPT: ["PROMPT"],
    PATTERN: ["REGEX_SET"],
    BUILT_IN: ["EXTRACTOR", "ENFORCER"],
    CONDITIONAL: ["ROUTER"],
    ACCELERATOR: ["BERT_CLASSIFIER"],
    SYNC_LLM: ["PROMPT"],
};

const BLOCK_TYPE_ICONS: Record<string, { icon: React.ElementType; color: string }> = {
    PROMPT: { icon: Brain, color: "text-purple-600" },
    REGEX_SET: { icon: Scan, color: "text-blue-600" },
    EXTRACTOR: { icon: Cog, color: "text-gray-600" },
    ROUTER: { icon: GitBranch, color: "text-amber-600" },
    ENFORCER: { icon: Shield, color: "text-green-600" },
};

function StepEditor({ step, index, onChange, onRemove, blocks }: {
    step: PipelineStep; index: number;
    onChange: (s: PipelineStep) => void; onRemove: () => void;
    blocks: Block[];
}) {
    const Icon = STEP_ICONS[step.type];
    const [showBlockConfig, setShowBlockConfig] = useState(!!step.blockId);

    // Filter blocks compatible with this step type
    const compatibleBlocks = blocks.filter(b => STEP_BLOCK_TYPES[step.type]?.includes(b.type));
    const linkedBlock = step.blockId ? blocks.find(b => b.id === step.blockId) : null;
    const linkedContent = linkedBlock
        ? (linkedBlock.versions.find(v => v.version === (step.blockVersion ?? linkedBlock.activeVersion))?.content ?? {})
        : null;

    const handleBlockChange = (blockId: string) => {
        if (!blockId) {
            onChange({ ...step, blockId: undefined, blockVersion: undefined });
            setShowBlockConfig(false);
        } else {
            const block = blocks.find(b => b.id === blockId);
            onChange({ ...step, blockId, blockVersion: block?.activeVersion });
            setShowBlockConfig(true);
        }
    };

    return (
        <div className={`rounded-lg border ${STEP_COLORS[step.type]} overflow-hidden`}>
            {/* Step header */}
            <div className="p-3 space-y-2">
                <div className="flex items-center gap-2">
                    <Icon className="size-4 shrink-0" />
                    <span className="text-xs font-medium text-gray-500">Step {step.order}</span>
                    <div className="flex-1" />
                    <button onClick={onRemove} className="text-red-400 hover:text-red-600"><Trash2 className="size-3.5" /></button>
                </div>
                <div className="grid grid-cols-3 gap-2">
                    <input id={`step-${index}-name`} aria-label="Step name" value={step.name} onChange={(e) => onChange({ ...step, name: e.target.value })}
                        placeholder="Step name" className="text-xs border border-gray-300 rounded px-2 py-1 bg-white" />
                    <select id={`step-${index}-type`} aria-label="Step type" value={step.type} onChange={(e) => {
                        const newType = e.target.value as StepType;
                        onChange({ ...step, type: newType, blockId: undefined, blockVersion: undefined });
                        setShowBlockConfig(false);
                    }}
                        className="text-xs border border-gray-300 rounded px-2 py-1 bg-white">
                        <option value="BUILT_IN">Built-in</option>
                        <option value="PATTERN">Pattern</option>
                        <option value="LLM_PROMPT">LLM Prompt</option>
                        <option value="CONDITIONAL">Conditional</option>
                    </select>
                    <select id={`step-${index}-enabled`} aria-label="Step enabled" value={String(step.enabled)} onChange={(e) => onChange({ ...step, enabled: e.target.value === "true" })}
                        className="text-xs border border-gray-300 rounded px-2 py-1 bg-white">
                        <option value="true">Enabled</option><option value="false">Disabled</option>
                    </select>
                </div>
                <input id={`step-${index}-desc`} aria-label="Step description" value={step.description} onChange={(e) => onChange({ ...step, description: e.target.value })}
                    placeholder="Description" className="w-full text-xs border border-gray-300 rounded px-2 py-1 bg-white" />

                {/* Block selector */}
                {compatibleBlocks.length > 0 && (
                    <div className="flex items-center gap-2">
                        <label className="text-[10px] font-medium text-gray-500 uppercase shrink-0">Block</label>
                        <select value={step.blockId ?? ""} onChange={(e) => handleBlockChange(e.target.value)}
                            className="flex-1 text-xs border border-gray-300 rounded px-2 py-1 bg-white">
                            <option value="">— No block (inline config) —</option>
                            {compatibleBlocks.map(b => (
                                <option key={b.id} value={b.id}>
                                    {b.name} (v{b.activeVersion})
                                </option>
                            ))}
                        </select>
                        {linkedBlock && (
                            <select value={step.blockVersion ?? linkedBlock.activeVersion}
                                onChange={(e) => onChange({ ...step, blockVersion: parseInt(e.target.value) })}
                                className="text-xs border border-gray-300 rounded px-2 py-1 bg-white w-20">
                                {linkedBlock.versions.map(v => (
                                    <option key={v.version} value={v.version}>
                                        v{v.version}{v.version === linkedBlock.activeVersion ? " ✓" : ""}
                                    </option>
                                ))}
                            </select>
                        )}
                    </div>
                )}

                {/* Inline prompts when no block linked */}
                {!step.blockId && (step.type === "LLM_PROMPT" || step.type === "CONDITIONAL") && (
                    <InlinePromptEditor step={step} onChange={onChange} />
                )}

                {step.type === "CONDITIONAL" && !step.blockId && (
                    <input value={step.condition ?? ""} onChange={(e) => onChange({ ...step, condition: e.target.value })}
                        placeholder="Condition (e.g. piiFindings.size > 0)" className="w-full text-xs border border-gray-300 rounded px-2 py-1 bg-white" />
                )}
            </div>

            {/* Linked block config — expandable */}
            {linkedBlock && linkedContent && (
                <div className="border-t border-gray-200">
                    <button onClick={() => setShowBlockConfig(!showBlockConfig)}
                        className="w-full flex items-center gap-2 px-3 py-2 bg-white/60 hover:bg-white/80 text-left">
                        {(() => {
                            const bi = BLOCK_TYPE_ICONS[linkedBlock.type];
                            const BIcon = bi?.icon ?? Cog;
                            return <BIcon className={`size-3.5 ${bi?.color ?? "text-gray-500"}`} />;
                        })()}
                        <span className="text-xs font-medium text-gray-700 flex-1">{linkedBlock.name}</span>
                        <span className="text-[10px] text-gray-400">v{step.blockVersion ?? linkedBlock.activeVersion}</span>
                        {linkedBlock.feedbackCount > 0 && (
                            <span className="px-1.5 py-0.5 text-[10px] bg-amber-100 text-amber-700 rounded-full">{linkedBlock.feedbackCount} feedback</span>
                        )}
                        {showBlockConfig ? <ChevronDown className="size-3.5 text-gray-400" /> : <ChevronRight className="size-3.5 text-gray-400" />}
                    </button>
                    {showBlockConfig && (
                        <div className="px-3 py-3 bg-white/40">
                            <BlockFormEditor type={linkedBlock.type} content={linkedContent} onChange={() => {}} readOnly />
                            <p className="text-[10px] text-gray-400 mt-2 italic">
                                Block content is read-only here. Edit in the <a href="/ai/blocks" className="text-blue-500 underline">Block Library</a>.
                            </p>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

function InlinePromptEditor({ step, onChange }: { step: PipelineStep; onChange: (s: PipelineStep) => void }) {
    const [showGuide, setShowGuide] = useState(false);

    return (
        <div className="space-y-2">
            <button onClick={() => setShowGuide(!showGuide)}
                className="flex items-center gap-1.5 text-[11px] text-blue-600 hover:text-blue-800">
                <HelpCircle className="size-3.5" />
                {showGuide ? "Hide prompt writing guide" : "How do prompts work?"}
            </button>

            {showGuide && (
                <div className="bg-white border border-blue-200 rounded-lg p-4 space-y-3 text-xs text-gray-600">
                    <div>
                        <h5 className="font-semibold text-gray-900 mb-1 flex items-center gap-1.5">
                            <Info className="size-3.5 text-blue-500" /> How prompts work
                        </h5>
                        <p>Each LLM step sends two prompts to Claude. Together they instruct the AI on what to do with each document.</p>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                        <div className="bg-purple-50 rounded-md p-3 border border-purple-100">
                            <h6 className="font-semibold text-purple-800 mb-1">System Prompt</h6>
                            <p className="text-purple-700">Defines the AI&apos;s <strong>role and rules</strong>. Sent once to set context.</p>
                        </div>
                        <div className="bg-blue-50 rounded-md p-3 border border-blue-100">
                            <h6 className="font-semibold text-blue-800 mb-1">User Prompt Template</h6>
                            <p className="text-blue-700">The <strong>per-document instruction</strong> with {"{placeholders}"} replaced.</p>
                        </div>
                    </div>
                    <div>
                        <h5 className="font-semibold text-gray-900 mb-1">Available placeholders</h5>
                        <div className="grid grid-cols-2 gap-x-4 gap-y-1 bg-gray-50 rounded-md p-2 font-mono">
                            <div><code className="text-blue-700">{"{documentId}"}</code> <span className="text-gray-400">— unique ID</span></div>
                            <div><code className="text-blue-700">{"{fileName}"}</code> <span className="text-gray-400">— file name</span></div>
                            <div><code className="text-blue-700">{"{mimeType}"}</code> <span className="text-gray-400">— file type</span></div>
                            <div><code className="text-blue-700">{"{extractedText}"}</code> <span className="text-gray-400">— full text</span></div>
                            <div><code className="text-blue-700">{"{piiFindings}"}</code> <span className="text-gray-400">— PII entities</span></div>
                            <div><code className="text-blue-700">{"{uploadedBy}"}</code> <span className="text-gray-400">— uploader</span></div>
                        </div>
                    </div>
                </div>
            )}

            <div>
                <label className="text-[10px] font-medium text-gray-500 uppercase block mb-0.5">
                    System Prompt
                    <span className="text-gray-400 normal-case ml-1">— the AI&apos;s role and instructions</span>
                </label>
                <textarea value={step.systemPrompt ?? ""} onChange={(e) => onChange({ ...step, systemPrompt: e.target.value })}
                    placeholder="You are a document classification specialist..."
                    rows={6}
                    className="w-full text-xs font-mono border border-gray-300 rounded px-2 py-1.5 bg-white leading-relaxed" />
            </div>

            <div>
                <label className="text-[10px] font-medium text-gray-500 uppercase block mb-0.5">
                    User Prompt Template
                    <span className="text-gray-400 normal-case ml-1">— sent per document with {"{placeholders}"} replaced</span>
                </label>
                <textarea value={step.userPromptTemplate ?? ""} onChange={(e) => onChange({ ...step, userPromptTemplate: e.target.value })}
                    placeholder={"Please classify the following document.\n\n**Document ID:** {documentId}\n**File name:** {fileName}\n..."}
                    rows={6}
                    className="w-full text-xs font-mono border border-gray-300 rounded px-2 py-1.5 bg-white leading-relaxed" />
            </div>
        </div>
    );
}
