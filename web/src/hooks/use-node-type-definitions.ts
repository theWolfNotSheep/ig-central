"use client";

import { useCallback, useEffect, useState } from "react";
import api from "@/lib/axios/axios.client";

/**
 * Shape of a NodeTypeDefinition as returned by the backend API.
 */
export type NodeTypeDefinition = {
    id: string;
    key: string;
    displayName: string;
    description: string;
    category: string;              // TRIGGER, PROCESSING, ACCELERATOR, LOGIC, ACTION, ERROR_HANDLING
    sortOrder: number;
    executionCategory: string;     // NOOP, BUILT_IN, ACCELERATOR, GENERIC_HTTP, ASYNC_BOUNDARY
    handlerBeanName?: string;
    requiresDocReload: boolean;
    pipelinePhase: string;         // PRE_CLASSIFICATION, POST_CLASSIFICATION, BOTH
    iconName: string;
    colorTheme: string;
    handles: HandleDefinition[];
    branchLabels?: BranchLabel[];
    configSchema?: Record<string, unknown>;
    configDefaults?: Record<string, string>;
    compatibleBlockType?: string;
    summaryTemplate?: string;
    validationExpression?: string;
    httpConfig?: Record<string, unknown>;
    performanceImpact?: string;    // "high", "medium", null
    performanceWarning?: string;
    active: boolean;
    builtIn: boolean;
};

export type HandleDefinition = {
    type: "source" | "target";
    position: string;           // "Top", "Bottom", "Left", "Right"
    id?: string;
    color?: string;
    style?: Record<string, unknown>;
};

export type BranchLabel = {
    handleId: string;
    label: string;
    color: string;
};

/**
 * Fetches active node type definitions from the backend.
 * Returns definitions + a loading flag + a refresh function.
 */
export function useNodeTypeDefinitions() {
    const [definitions, setDefinitions] = useState<NodeTypeDefinition[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async () => {
        setError(null);
        try {
            const { data } = await api.get<NodeTypeDefinition[]>("/admin/node-types");
            setDefinitions(data);
        } catch {
            setError("Failed to load node types — check that the API server is running");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { load(); }, [load]);

    return { definitions, loading, error, refresh: load };
}
