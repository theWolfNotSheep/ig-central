"use client";

import { memo, useMemo } from "react";
import { Handle, Position, type NodeProps } from "@xyflow/react";
import { Clock, type LucideIcon } from "lucide-react";
import { COLOR_THEMES, DEFAULT_THEME, type ColorTheme } from "./colorThemes";
import { resolveIcon, DEFAULT_ICON } from "./iconMap";
import type { NodeTypeDefinition, HandleDefinition, BranchLabel } from "@/hooks/use-node-type-definitions";

/* ------------------------------------------------------------------ */
/*  Shared types                                                       */
/* ------------------------------------------------------------------ */

export type WorkflowNodeData = {
    label: string;
    nodeType: string;
    summary?: string;
    configured?: boolean;
    // Common config
    blockId?: string;
    blockName?: string;
    blockVersion?: number;
    description?: string;
    // Type-specific config stored flat
    config?: Record<string, string>;

    // Legacy compat — old pipelines used blockType
    blockType?: string;
};

/* ------------------------------------------------------------------ */
/*  Handle definitions per node type                                   */
/* ------------------------------------------------------------------ */

type HandleDef = {
    type: "source" | "target";
    position: Position;
    id?: string;
    color?: string;
    label?: string;
    style?: React.CSSProperties;
};

export type NodeTypeDef = {
    icon: LucideIcon;
    bg: string;
    border: string;
    text: string;
    iconBg: string;
    handles: HandleDef[];
    branchLabels?: BranchLabel[];
    performanceImpact?: string;
};

/* ------------------------------------------------------------------ */
/*  Build node type defs from API definitions                          */
/* ------------------------------------------------------------------ */

const POSITION_MAP: Record<string, Position> = {
    Top: Position.Top,
    Bottom: Position.Bottom,
    Left: Position.Left,
    Right: Position.Right,
};

function toHandleDef(h: HandleDefinition): HandleDef {
    return {
        type: h.type,
        position: POSITION_MAP[h.position] ?? Position.Bottom,
        id: h.id ?? undefined,
        color: h.color ?? undefined,
        style: h.style as React.CSSProperties | undefined,
    };
}

export function buildNodeTypeDefs(definitions: NodeTypeDefinition[]): Record<string, NodeTypeDef> {
    const result: Record<string, NodeTypeDef> = {};
    for (const def of definitions) {
        const theme: ColorTheme = COLOR_THEMES[def.colorTheme] ?? DEFAULT_THEME;
        result[def.key] = {
            icon: resolveIcon(def.iconName),
            bg: theme.bg,
            border: theme.border,
            text: theme.text,
            iconBg: theme.iconBg,
            handles: (def.handles ?? []).map(toHandleDef),
            branchLabels: def.branchLabels,
            performanceImpact: def.performanceImpact,
        };
    }
    return result;
}

/**
 * Build the React Flow nodeTypes map dynamically.
 * Every known key points to the same MemoWorkflowNode component.
 */
export function buildReactFlowNodeTypes(
    keys: string[],
    nodeComponent: React.ComponentType<NodeProps>
): Record<string, React.ComponentType<NodeProps>> {
    const result: Record<string, React.ComponentType<NodeProps>> = {};
    for (const key of keys) {
        result[key] = nodeComponent;
    }
    // Legacy compat
    result.blockNode = nodeComponent;
    return result;
}

/* ------------------------------------------------------------------ */
/*  WorkflowNode — shared component for all node types                 */
/* ------------------------------------------------------------------ */

// Module-level mutable ref for current defs — set by the editor
let _currentDefs: Record<string, NodeTypeDef> = {};

export function setCurrentNodeTypeDefs(defs: Record<string, NodeTypeDef>) {
    _currentDefs = defs;
}

function WorkflowNode({ data, selected }: NodeProps & { data: WorkflowNodeData }) {
    const nodeType = data.nodeType ?? data.blockType ?? "textExtraction";
    const def = _currentDefs[nodeType] ?? {
        icon: DEFAULT_ICON, ...DEFAULT_THEME, handles: [],
    };
    const Icon = def.icon;
    const isConfigured = data.configured !== false;

    return (
        <div
            className={[
                "relative rounded-lg border-2 shadow-sm min-w-[200px] max-w-[240px] px-4 py-3 transition-all",
                def.bg,
                selected ? "border-blue-500 ring-2 ring-blue-200" : def.border,
            ].join(" ")}
        >
            {/* Performance impact badge */}
            {def.performanceImpact && (
                <div
                    className={`absolute -top-1.5 -right-1.5 size-5 rounded-full flex items-center justify-center border ${
                        def.performanceImpact === "high"
                            ? "bg-amber-100 border-amber-300"
                            : "bg-yellow-50 border-yellow-200"
                    }`}
                    title={def.performanceImpact === "high" ? "Slow: 10-30s per call" : "External call: 1-5s"}
                >
                    <Clock className={`size-3 ${def.performanceImpact === "high" ? "text-amber-600" : "text-yellow-500"}`} />
                </div>
            )}
            {/* Handles */}
            {def.handles.map((h, i) => (
                <Handle
                    key={h.id ?? `${h.type}-${h.position}-${i}`}
                    type={h.type}
                    position={h.position}
                    id={h.id}
                    className={`!w-3 !h-3 !border-2 !border-white ${h.color ?? "!bg-gray-400"}`}
                    style={h.style}
                />
            ))}

            {/* Header: icon + label + status */}
            <div className="flex items-center gap-2.5">
                <div className={`size-8 rounded-md flex items-center justify-center shrink-0 ${def.iconBg} ${def.text}`}>
                    <Icon className="size-4" />
                </div>
                <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                        <span className="text-xs font-bold text-gray-900 truncate">{data.label}</span>
                        <span
                            className={`size-2 rounded-full shrink-0 ${isConfigured ? "bg-green-500" : "bg-amber-400"}`}
                            title={isConfigured ? "Configured" : "Incomplete"}
                        />
                    </div>
                    {data.summary && (
                        <div className="text-[10px] text-gray-500 truncate mt-0.5">{data.summary}</div>
                    )}
                </div>
            </div>

            {/* Bottom labels for branching nodes — data-driven */}
            {def.branchLabels && def.branchLabels.length > 0 && (
                <div className="flex justify-between mt-2 px-1 text-[9px] font-medium">
                    {def.branchLabels.map(bl => (
                        <span key={bl.handleId} className={bl.color}>{bl.label}</span>
                    ))}
                </div>
            )}
        </div>
    );
}

/* ------------------------------------------------------------------ */
/*  Export                                                             */
/* ------------------------------------------------------------------ */

export const MemoWorkflowNode = memo(WorkflowNode);

// Default export for backward compat — these will be overridden by the editor
export const NODE_TYPE_DEFS = _currentDefs;
