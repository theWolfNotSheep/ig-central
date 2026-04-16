"use client";

import { useCallback, useEffect, useState } from "react";
import {
    Activity, CheckCircle, XCircle, Clock, Loader2,
    RefreshCw, ChevronDown, ChevronRight, Minus,
    Zap, FileSearch, Shield, GitBranch, MessageSquare,
    Play,
} from "lucide-react";
import api from "@/lib/axios/axios.client";

/* ── Types ──────────────────────────────────────────── */

type RunSummary = {
    running: number;
    waiting: number;
    completed: number;
    failed: number;
};

type PipelineRun = {
    id: string;
    documentId: string;
    pipelineId: string;
    pipelineName: string;
    status: "RUNNING" | "WAITING" | "COMPLETED" | "FAILED";
    currentNodeKey: string | null;
    startedAt: string;
    completedAt: string | null;
    durationMs: number | null;
    error: string | null;
};

type NodeRun = {
    nodeKey: string;
    nodeType: string;
    status: "RUNNING" | "WAITING" | "SUCCEEDED" | "FAILED" | "SKIPPED";
    startedAt: string | null;
    completedAt: string | null;
    durationMs: number | null;
    error: string | null;
};

type RunDetail = PipelineRun & {
    nodeRuns: NodeRun[];
};

type RunFilter = "ALL" | "RUNNING" | "WAITING" | "FAILED" | "COMPLETED";

/* ── Main Component ─────────────────────────────────── */

export default function PipelineRunsTab() {
    const [summary, setSummary] = useState<RunSummary | null>(null);
    const [runs, setRuns] = useState<PipelineRun[]>([]);
    const [runsLoading, setRunsLoading] = useState(false);
    const [filter, setFilter] = useState<RunFilter>("ALL");
    const [expandedRunId, setExpandedRunId] = useState<string | null>(null);
    const [runDetail, setRunDetail] = useState<RunDetail | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);

    const fetchSummary = useCallback(async () => {
        try {
            const { data } = await api.get("/admin/monitoring/pipeline/runs/summary");
            setSummary(data);
        } catch {
            console.error("Failed to fetch pipeline runs summary");
        }
    }, []);

    const fetchRuns = useCallback(async () => {
        setRunsLoading(true);
        try {
            const params = filter === "ALL" ? "?page=0&size=50" : `?status=${filter}&page=0&size=50`;
            const { data } = await api.get(`/admin/monitoring/pipeline/runs${params}`);
            setRuns(data.content ?? data ?? []);
        } catch {
            console.error("Failed to fetch pipeline runs");
        } finally {
            setRunsLoading(false);
        }
    }, [filter]);

    const fetchRunDetail = useCallback(async (runId: string) => {
        setDetailLoading(true);
        try {
            const { data } = await api.get(`/admin/monitoring/pipeline/runs/${runId}`);
            setRunDetail(data);
        } catch {
            console.error("Failed to fetch run detail");
        } finally {
            setDetailLoading(false);
        }
    }, []);

    // Initial load
    useEffect(() => { fetchSummary(); fetchRuns(); }, [fetchSummary, fetchRuns]);

    // Auto-refresh every 10s
    useEffect(() => {
        const interval = setInterval(() => { fetchSummary(); fetchRuns(); }, 10000);
        return () => clearInterval(interval);
    }, [fetchSummary, fetchRuns]);

    // Fetch detail when expanding a row
    const handleRowClick = (runId: string) => {
        if (expandedRunId === runId) {
            setExpandedRunId(null);
            setRunDetail(null);
        } else {
            setExpandedRunId(runId);
            fetchRunDetail(runId);
        }
    };

    const filters: { key: RunFilter; label: string }[] = [
        { key: "ALL", label: "All" },
        { key: "RUNNING", label: "Running" },
        { key: "WAITING", label: "Waiting" },
        { key: "FAILED", label: "Failed" },
        { key: "COMPLETED", label: "Completed" },
    ];

    return (
        <div className="space-y-6">
            {/* Summary Cards */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                <SummaryCard
                    icon={Loader2}
                    label="Running"
                    value={summary?.running ?? 0}
                    colour="blue"
                    spinning
                />
                <SummaryCard
                    icon={Clock}
                    label="Waiting"
                    value={summary?.waiting ?? 0}
                    colour="amber"
                />
                <SummaryCard
                    icon={CheckCircle}
                    label="Completed"
                    value={summary?.completed ?? 0}
                    colour="green"
                />
                <SummaryCard
                    icon={XCircle}
                    label="Failed"
                    value={summary?.failed ?? 0}
                    colour="red"
                />
            </div>

            {/* Filter tabs + Refresh */}
            <div className="flex items-center gap-3">
                <div className="flex gap-1 bg-gray-100 rounded-lg p-0.5">
                    {filters.map((f) => (
                        <button
                            key={f.key}
                            onClick={() => setFilter(f.key)}
                            className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                                filter === f.key
                                    ? "bg-white text-gray-900 shadow-sm"
                                    : "text-gray-600 hover:text-gray-900"
                            }`}
                        >
                            {f.label}
                        </button>
                    ))}
                </div>
                <button onClick={() => { fetchSummary(); fetchRuns(); }} disabled={runsLoading} className="text-xs text-gray-400 hover:text-gray-600">
                    <RefreshCw className={`size-3.5 ${runsLoading ? "animate-spin" : ""}`} />
                </button>
            </div>

            {/* Runs Table */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                {runsLoading && runs.length === 0 ? (
                    <div className="p-8 text-center text-sm text-gray-400">
                        <Loader2 className="size-6 animate-spin mx-auto mb-2 text-gray-300" />
                        Loading pipeline runs...
                    </div>
                ) : runs.length === 0 ? (
                    <div className="p-8 text-center text-sm text-gray-400">
                        <CheckCircle className="size-8 text-green-200 mx-auto mb-2" />
                        No pipeline runs found{filter !== "ALL" ? ` with status "${filter}"` : ""}.
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead className="bg-gray-50 border-b border-gray-200">
                                <tr>
                                    <th className="w-8 px-2 py-2" />
                                    <th className="text-left px-3 py-2 font-medium text-gray-600">Document</th>
                                    <th className="text-left px-3 py-2 font-medium text-gray-600">Pipeline</th>
                                    <th className="text-left px-3 py-2 font-medium text-gray-600 w-28">Status</th>
                                    <th className="text-left px-3 py-2 font-medium text-gray-600">Current Node</th>
                                    <th className="text-left px-3 py-2 font-medium text-gray-600 w-36">Started</th>
                                    <th className="text-left px-3 py-2 font-medium text-gray-600 w-24">Duration</th>
                                    <th className="text-left px-3 py-2 font-medium text-gray-600">Error</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                                {runs.map((run) => (
                                    <RunRow
                                        key={run.id}
                                        run={run}
                                        expanded={expandedRunId === run.id}
                                        detail={expandedRunId === run.id ? runDetail : null}
                                        detailLoading={expandedRunId === run.id && detailLoading}
                                        onClick={() => handleRowClick(run.id)}
                                    />
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
}

/* ── Summary Card ───────────────────────────────────── */

function SummaryCard({ icon: Icon, label, value, colour, spinning }: {
    icon: React.ElementType;
    label: string;
    value: number;
    colour: "blue" | "amber" | "green" | "red";
    spinning?: boolean;
}) {
    const bg = { blue: "bg-blue-50", amber: "bg-amber-50", green: "bg-green-50", red: "bg-red-50" };
    const text = { blue: "text-blue-700", amber: "text-amber-700", green: "text-green-700", red: "text-red-700" };
    const iconCol = { blue: "text-blue-500", amber: "text-amber-500", green: "text-green-500", red: "text-red-500" };

    return (
        <div className={`rounded-lg p-4 ${bg[colour]}`}>
            <div className="flex items-center gap-2 mb-2">
                <Icon className={`size-4 ${iconCol[colour]} ${spinning && value > 0 ? "animate-spin" : ""}`} />
                <span className="text-xs font-medium text-gray-600">{label}</span>
            </div>
            <div className={`text-2xl font-bold ${text[colour]}`}>{value}</div>
        </div>
    );
}

/* ── Run Row (with expandable detail) ───────────────── */

function RunRow({ run, expanded, detail, detailLoading, onClick }: {
    run: PipelineRun;
    expanded: boolean;
    detail: RunDetail | null;
    detailLoading: boolean;
    onClick: () => void;
}) {
    const isFailed = run.status === "FAILED";

    return (
        <>
            <tr
                onClick={onClick}
                className={`cursor-pointer hover:bg-gray-50 ${isFailed ? "bg-red-50/50" : ""}`}
            >
                <td className="px-2 py-2 text-gray-400">
                    {expanded ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
                </td>
                <td className="px-3 py-2 text-xs text-gray-500 font-mono" title={run.documentId}>
                    {run.documentId.slice(0, 8)}...{run.documentId.slice(-4)}
                </td>
                <td className="px-3 py-2 text-xs text-gray-700 truncate max-w-40">
                    {run.pipelineName || run.pipelineId || "—"}
                </td>
                <td className="px-3 py-2">
                    <RunStatusBadge status={run.status} />
                </td>
                <td className="px-3 py-2 text-xs text-gray-500 truncate max-w-32">
                    {run.currentNodeKey ?? "—"}
                </td>
                <td className="px-3 py-2 text-xs text-gray-500 whitespace-nowrap">
                    {run.startedAt ? new Date(run.startedAt).toLocaleString() : "—"}
                </td>
                <td className="px-3 py-2 text-xs text-gray-500 tabular-nums">
                    {formatDuration(run.durationMs)}
                </td>
                <td className="px-3 py-2 text-xs text-red-500 truncate max-w-48" title={run.error ?? undefined}>
                    {run.error ? run.error.split("\n")[0] : "—"}
                </td>
            </tr>
            {expanded && (
                <tr>
                    <td colSpan={8} className="bg-gray-50/50 px-6 py-4 border-t border-gray-100">
                        {detailLoading ? (
                            <div className="flex items-center gap-2 text-sm text-gray-400 py-4">
                                <Loader2 className="size-4 animate-spin" />
                                Loading node execution timeline...
                            </div>
                        ) : detail?.nodeRuns && detail.nodeRuns.length > 0 ? (
                            <NodeTimeline nodes={detail.nodeRuns} />
                        ) : (
                            <div className="text-sm text-gray-400 py-2">No node execution data available.</div>
                        )}
                    </td>
                </tr>
            )}
        </>
    );
}

/* ── Node Execution Timeline ────────────────────────── */

const NODE_TYPE_ICONS: Record<string, React.ElementType> = {
    PROMPT: MessageSquare,
    EXTRACTOR: FileSearch,
    REGEX_SET: Zap,
    ROUTER: GitBranch,
    ENFORCER: Shield,
    LLM: MessageSquare,
    CLASSIFICATION: Activity,
    PII_SCAN: Zap,
    EXTRACTION: FileSearch,
    GOVERNANCE: Shield,
};

function NodeTimeline({ nodes }: { nodes: NodeRun[] }) {
    return (
        <div className="relative pl-6">
            {/* Vertical connecting line */}
            <div className="absolute left-[11px] top-3 bottom-3 w-px bg-gray-200" />

            <div className="space-y-0">
                {nodes.map((node, i) => {
                    const Icon = NODE_TYPE_ICONS[node.nodeType] ?? Play;
                    const isLast = i === nodes.length - 1;

                    return (
                        <div key={`${node.nodeKey}-${i}`} className="relative flex items-start gap-3 py-2">
                            {/* Status dot on the line */}
                            <div className="absolute left-[-13px] top-3">
                                <NodeStatusDot status={node.status} />
                            </div>

                            {/* Node content */}
                            <div className={`flex-1 flex items-center gap-3 rounded-md px-3 py-2 ${
                                node.status === "FAILED" ? "bg-red-50" :
                                node.status === "RUNNING" ? "bg-blue-50/50" :
                                node.status === "WAITING" ? "bg-amber-50/50" :
                                "bg-white"
                            }`}>
                                <Icon className={`size-4 shrink-0 ${
                                    node.status === "SUCCEEDED" ? "text-green-500" :
                                    node.status === "FAILED" ? "text-red-500" :
                                    node.status === "RUNNING" ? "text-blue-500" :
                                    node.status === "WAITING" ? "text-amber-500" :
                                    "text-gray-300"
                                }`} />

                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2">
                                        <span className="text-sm font-medium text-gray-900">{node.nodeKey}</span>
                                        <span className="text-[10px] text-gray-400 uppercase">{node.nodeType}</span>
                                    </div>
                                    {node.error && (
                                        <p className="text-xs text-red-600 mt-0.5 truncate" title={node.error}>
                                            {node.error}
                                        </p>
                                    )}
                                </div>

                                <NodeStatusBadge status={node.status} />

                                <span className="text-xs text-gray-400 tabular-nums shrink-0 w-16 text-right">
                                    {formatDuration(node.durationMs)}
                                </span>

                                <span className="text-xs text-gray-300 shrink-0 w-20 text-right">
                                    {node.startedAt ? new Date(node.startedAt).toLocaleTimeString() : "—"}
                                </span>
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

/* ── Status Components ──────────────────────────────── */

function NodeStatusDot({ status }: { status: NodeRun["status"] }) {
    if (status === "SUCCEEDED") {
        return <div className="size-3 rounded-full bg-green-500 ring-2 ring-white" />;
    }
    if (status === "FAILED") {
        return <div className="size-3 rounded-full bg-red-500 ring-2 ring-white" />;
    }
    if (status === "RUNNING") {
        return <div className="size-3 rounded-full bg-blue-500 ring-2 ring-white animate-pulse" />;
    }
    if (status === "WAITING") {
        return <div className="size-3 rounded-full bg-amber-400 ring-2 ring-white animate-pulse" />;
    }
    // SKIPPED
    return <div className="size-3 rounded-full bg-gray-300 ring-2 ring-white" />;
}

function RunStatusBadge({ status }: { status: PipelineRun["status"] }) {
    const styles: Record<string, string> = {
        RUNNING: "bg-blue-100 text-blue-700",
        WAITING: "bg-amber-100 text-amber-700",
        COMPLETED: "bg-green-100 text-green-700",
        FAILED: "bg-red-100 text-red-700",
    };

    return (
        <span className={`inline-flex items-center gap-1 px-2 py-0.5 text-[10px] font-medium rounded-full ${styles[status] ?? "bg-gray-100 text-gray-600"}`}>
            {status === "RUNNING" && <Loader2 className="size-3 animate-spin" />}
            {status === "WAITING" && <Clock className="size-3" />}
            {status}
        </span>
    );
}

function NodeStatusBadge({ status }: { status: NodeRun["status"] }) {
    const styles: Record<string, string> = {
        SUCCEEDED: "bg-green-100 text-green-700",
        FAILED: "bg-red-100 text-red-700",
        RUNNING: "bg-blue-100 text-blue-700",
        WAITING: "bg-amber-100 text-amber-700",
        SKIPPED: "bg-gray-100 text-gray-500",
    };

    const icons: Record<string, React.ReactNode> = {
        SUCCEEDED: <CheckCircle className="size-3" />,
        FAILED: <XCircle className="size-3" />,
        RUNNING: <Loader2 className="size-3 animate-spin" />,
        WAITING: <Clock className="size-3" />,
        SKIPPED: <Minus className="size-3" />,
    };

    return (
        <span className={`inline-flex items-center gap-1 px-2 py-0.5 text-[10px] font-medium rounded-full shrink-0 ${styles[status] ?? "bg-gray-100 text-gray-600"}`}>
            {icons[status]}
            {status}
        </span>
    );
}

/* ── Helpers ────────────────────────────────────────── */

function formatDuration(ms: number | null | undefined): string {
    if (ms == null || ms < 0) return "—";
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    if (ms < 3600000) return `${(ms / 60000).toFixed(1)}m`;
    return `${(ms / 3600000).toFixed(1)}h`;
}
