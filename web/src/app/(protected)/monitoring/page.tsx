"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
    Activity, Server, Database, HardDrive, FileText,
    CheckCircle, XCircle, AlertTriangle, Loader2, Clock,
    ArrowUpRight, ArrowDownRight, Inbox, RefreshCw, Trash2, Wifi,
    RotateCcw, OctagonX, Radio,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import { usePipelineSSE } from "@/hooks/use-pipeline-sse";
import PipelineRunsTab from "@/components/monitoring/pipeline-runs-tab";
import DlqReplayTab from "@/components/monitoring/dlq-replay-tab";
import SchedulerLocksPanel from "@/components/monitoring/scheduler-locks-panel";
import PerformanceDashboard from "@/components/monitoring/performance-dashboard";

type ServiceStatus = {
    name: string;
    status: "UP" | "DOWN" | "DEGRADED" | "TIMEOUT";
    responseTimeMs?: number;
    error?: string;
};

type HealthData = {
    services: ServiceStatus[];
    timestamp: string;
};

type PipelineData = {
    statusCounts: Record<string, number>;
    totalDocuments: number;
    throughput: { last24h: number; last7d: number };
    avgClassificationTimeMs: number;
    staleDocuments: number;
    queueDepths: Record<string, number>;
    circuitBreaker?: { state: string; consecutiveFailures: number; threshold?: number; cooldownSeconds?: number; openedAt?: string };
};

type InfraData = {
    mongodb: { databaseSizeBytes?: number; storageSizeBytes?: number; collections?: number; totalObjects?: number };
    storage: { totalFiles: number; totalSizeBytes: number };
    rabbitmq: { status: string };
};

const SERVICE_LABELS: Record<string, string> = {
    api: "API Server",
    "mcp-server": "MCP Server",
    "llm-worker": "LLM Worker",
    "bert-classifier": "BERT Classifier",
    minio: "MinIO Storage",
    ollama: "Ollama (Local LLM)",
};

type ActivityItem = {
    documentId: string;
    status: string;
    fileName: string;
    timestamp: number;
};

type PipelineLog = {
    documentId: string;
    fileName: string;
    stage: string;
    level: string;
    message: string;
    durationMs: number;
    timestamp: number;
};

export default function MonitoringPage() {
    const [health, setHealth] = useState<HealthData | null>(null);
    const [pipeline, setPipeline] = useState<PipelineData | null>(null);
    const [infra, setInfra] = useState<InfraData | null>(null);
    const [refreshing, setRefreshing] = useState(false);
    const [activity, setActivity] = useState<ActivityItem[]>([]);
    const [logs, setLogs] = useState<PipelineLog[]>([]);

    const fetchHealth = useCallback(async () => {
        try { const { data } = await api.get("/admin/monitoring/health"); setHealth(data); } catch {}
    }, []);

    const fetchInfra = useCallback(async () => {
        try { const { data } = await api.get("/admin/monitoring/infrastructure"); setInfra(data); } catch {}
    }, []);

    const fetchAll = useCallback(async () => {
        const [h, i, es] = await Promise.allSettled([
            api.get("/admin/monitoring/health"),
            api.get("/admin/monitoring/infrastructure"),
            api.get("/admin/monitoring/errors/summary"),
        ]);
        if (h.status === "fulfilled") setHealth(h.value.data);
        if (i.status === "fulfilled") setInfra(i.value.data);
        if (es.status === "fulfilled") setErrorSummary(es.value.data);
    }, []);

    // SSE for real-time pipeline metrics + document status + pipeline logs
    const { connected } = usePipelineSSE({
        onPipelineMetrics: (data) => setPipeline(data),
        onDocumentStatus: (event) => {
            setActivity((prev) => [
                { ...event, timestamp: Date.now() },
                ...prev.slice(0, 49),
            ]);
        },
        onPipelineLog: (event) => {
            setLogs((prev) => [event, ...prev.slice(0, 199)]); // keep last 200
        },
    });

    // Initial fetch for health + infra (pipeline comes from SSE)
    useEffect(() => { fetchAll(); }, [fetchAll]);

    // Health polling every 15s (not SSE — separate microservice pings)
    useEffect(() => {
        const interval = setInterval(fetchHealth, 15000);
        return () => clearInterval(interval);
    }, [fetchHealth]);

    const handleRefresh = async () => {
        setRefreshing(true);
        await fetchAll();
        setRefreshing(false);
    };

    const [monTab, setMonTab] = useState<"overview" | "pipeline" | "runs" | "errors" | "ops">("overview");
    const [pipelineDocs, setPipelineDocs] = useState<any[]>([]);
    const [pipelineDocsLoading, setPipelineDocsLoading] = useState(false);
    const [pipelineFilter, setPipelineFilter] = useState<string | null>(null);

    // Error state
    const [errorSummary, setErrorSummary] = useState<{ unresolvedCount: number; criticalCount: number; last24hCount: number } | null>(null);
    const [errors, setErrors] = useState<any[]>([]);
    const [errorsLoading, setErrorsLoading] = useState(false);
    const [errorFilter, setErrorFilter] = useState<"all" | "unresolved">("unresolved");

    const loadPipelineDocs = useCallback(async () => {
        setPipelineDocsLoading(true);
        try { const { data } = await api.get("/admin/monitoring/pipeline/documents"); setPipelineDocs(data); }
        catch { }
        finally { setPipelineDocsLoading(false); }
    }, []);

    useEffect(() => { if (monTab === "pipeline") loadPipelineDocs(); }, [monTab, loadPipelineDocs]);

    // Auto-refresh pipeline docs every 5s when on that tab
    useEffect(() => {
        if (monTab !== "pipeline") return;
        const interval = setInterval(loadPipelineDocs, 5000);
        return () => clearInterval(interval);
    }, [monTab, loadPipelineDocs]);

    const loadErrors = useCallback(async () => {
        setErrorsLoading(true);
        try {
            const params = errorFilter === "unresolved" ? "?unresolved=true&size=50" : "?size=50";
            const { data } = await api.get(`/admin/monitoring/errors${params}`);
            setErrors(data.content ?? []);
        } catch {} finally { setErrorsLoading(false); }
    }, [errorFilter]);

    useEffect(() => { if (monTab === "errors") loadErrors(); }, [monTab, loadErrors]);

    const allUp = health?.services.every((s) => s.status === "UP") ?? false;
    const downCount = health?.services.filter((s) => s.status !== "UP").length ?? 0;

    return (
        <>
            {/* Header */}
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">System Monitoring</h2>
                    <div className="flex items-center gap-3 mt-1">
                        <p className="text-sm text-gray-500">
                            {health ? (
                                allUp ? (
                                    <span className="text-green-600 font-medium">All systems operational</span>
                                ) : (
                                    <span className="text-red-600 font-medium">{downCount} service{downCount > 1 ? "s" : ""} degraded</span>
                                )
                            ) : "Loading..."}
                        </p>
                        <span className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full ${
                            connected ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"
                        }`}>
                            <Radio className="size-3" />
                            {connected ? "Live" : "Connecting..."}
                        </span>
                    </div>
                </div>
                <div className="flex gap-2">
                    <button onClick={async () => {
                        try { const { data } = await api.post("/admin/monitoring/search/reindex"); toast.success(`Reindexed ${data.indexed} documents`); }
                        catch { toast.error("Reindex failed"); }
                    }} className="inline-flex items-center gap-2 px-3 py-2 border border-gray-300 text-sm text-gray-700 rounded-lg hover:bg-gray-50">
                        ES Reindex
                    </button>
                    <button onClick={handleRefresh} disabled={refreshing}
                        className="inline-flex items-center gap-2 px-3 py-2 border border-gray-300 text-sm text-gray-700 rounded-lg hover:bg-gray-50 disabled:opacity-50">
                        <RefreshCw className={`size-4 ${refreshing ? "animate-spin" : ""}`} />
                        Refresh
                    </button>
                </div>
            </div>

            {/* Tabs */}
            <div className="flex gap-1 mb-6 bg-gray-100 rounded-lg p-1 w-fit">
                <button onClick={() => setMonTab("overview")}
                    className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${monTab === "overview" ? "bg-white text-gray-900 shadow-sm" : "text-gray-600 hover:text-gray-900"}`}>
                    Overview
                </button>
                <button onClick={() => setMonTab("pipeline")}
                    className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${monTab === "pipeline" ? "bg-white text-gray-900 shadow-sm" : "text-gray-600 hover:text-gray-900"}`}>
                    Pipeline
                    {pipeline && (pipeline.statusCounts?.PROCESSING > 0 || pipeline.statusCounts?.CLASSIFYING > 0) && (
                        <span className="ml-1.5 size-2 rounded-full bg-blue-500 animate-pulse inline-block" />
                    )}
                </button>
                <button onClick={() => setMonTab("runs")}
                    className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${monTab === "runs" ? "bg-white text-gray-900 shadow-sm" : "text-gray-600 hover:text-gray-900"}`}>
                    Runs
                </button>
                <button onClick={() => setMonTab("errors")}
                    className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${monTab === "errors" ? "bg-white text-gray-900 shadow-sm" : "text-gray-600 hover:text-gray-900"}`}>
                    Errors
                    {errorSummary && errorSummary.unresolvedCount > 0 && (
                        <span className="ml-1.5 inline-flex items-center justify-center min-w-5 h-5 text-[10px] font-bold bg-red-500 text-white rounded-full px-1">
                            {errorSummary.unresolvedCount}
                        </span>
                    )}
                </button>
                <button onClick={() => setMonTab("ops")}
                    className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${monTab === "ops" ? "bg-white text-gray-900 shadow-sm" : "text-gray-600 hover:text-gray-900"}`}>
                    Ops
                </button>
            </div>

            {/* ── Ops Tab ───────────────────────────────── */}
            {monTab === "ops" && (
                <div className="space-y-6">
                    <section>
                        <SectionHeader icon={Activity} title="Performance" />
                        <PerformanceDashboard />
                    </section>
                    <section>
                        <SectionHeader icon={Inbox} title="Dead-letter queues" />
                        <DlqReplayTab />
                    </section>
                    <section>
                        <SectionHeader icon={Activity} title="Leader election" />
                        <SchedulerLocksPanel />
                    </section>
                </div>
            )}

            {/* ── Pipeline Tab ──────────────────────────── */}
            {monTab === "pipeline" && (
                <div className="space-y-6">
                    {/* Pipeline Funnel */}
                    {pipeline && (
                        <section>
                            <SectionHeader icon={Activity} title="Pipeline Status" />
                            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
                                <div className="space-y-2">
                                    {[
                                        { label: "Uploaded", key: "UPLOADED", color: "bg-gray-300" },
                                        { label: "Processing", key: "PROCESSING", color: "bg-yellow-400" },
                                        { label: "Processed", key: "PROCESSED", color: "bg-yellow-300" },
                                        { label: "Classifying", key: "CLASSIFYING", color: "bg-blue-400" },
                                        { label: "Classified", key: "CLASSIFIED", color: "bg-blue-500" },
                                        { label: "Review Required", key: "REVIEW_REQUIRED", color: "bg-amber-400" },
                                        { label: "Governed", key: "GOVERNANCE_APPLIED", color: "bg-green-500" },
                                    ].map(s => {
                                        const v = pipeline.statusCounts?.[s.key] ?? 0;
                                        const pct = pipeline.totalDocuments > 0 ? (v / pipeline.totalDocuments) * 100 : 0;
                                        const active = pipelineFilter === s.key;
                                        return (
                                            <button key={s.key} onClick={() => setPipelineFilter(active ? null : s.key)}
                                                className={`flex items-center gap-3 w-full rounded-md px-1 py-0.5 transition-colors ${active ? "bg-blue-50 ring-1 ring-blue-200" : "hover:bg-gray-50"}`}>
                                                <span className="text-[10px] text-gray-500 w-24 text-right shrink-0">{s.label}</span>
                                                <div className="flex-1 bg-gray-100 rounded-full h-5 overflow-hidden">
                                                    <div className={`${s.color} h-full rounded-full transition-all duration-700`} style={{ width: `${Math.max(pct, v > 0 ? 2 : 0)}%` }} />
                                                </div>
                                                <span className="text-xs font-medium text-gray-700 w-8 text-right">{v}</span>
                                            </button>
                                        );
                                    })}
                                    {/* Failed stages */}
                                    {["PROCESSING_FAILED", "CLASSIFICATION_FAILED", "ENFORCEMENT_FAILED"].some(k => (pipeline.statusCounts?.[k] ?? 0) > 0) && (
                                        <div className="pt-1 border-t border-gray-100 mt-1">
                                            {["PROCESSING_FAILED", "CLASSIFICATION_FAILED", "ENFORCEMENT_FAILED"].filter(k => (pipeline.statusCounts?.[k] ?? 0) > 0).map(k => {
                                                const active = pipelineFilter === k;
                                                return (
                                                    <button key={k} onClick={() => setPipelineFilter(active ? null : k)}
                                                        className={`flex items-center gap-3 w-full rounded-md px-1 py-0.5 transition-colors ${active ? "bg-red-50 ring-1 ring-red-200" : "hover:bg-gray-50"}`}>
                                                        <span className="text-[10px] text-red-500 w-24 text-right shrink-0">{k.replace(/_/g, " ")}</span>
                                                        <div className="flex-1 bg-gray-100 rounded-full h-5 overflow-hidden">
                                                            <div className="bg-red-500 h-full rounded-full" style={{ width: `${Math.max(((pipeline.statusCounts?.[k] ?? 0) / Math.max(pipeline.totalDocuments, 1)) * 100, 2)}%` }} />
                                                        </div>
                                                        <span className="text-xs font-medium text-red-600 w-8 text-right">{pipeline.statusCounts?.[k] ?? 0}</span>
                                                    </button>
                                                );
                                            })}
                                        </div>
                                    )}
                                </div>
                            </div>
                        </section>
                    )}

                    {/* Documents in Pipeline */}
                    <section>
                        <div className="flex items-center justify-between mb-3">
                            <div className="flex items-center gap-3">
                                <SectionHeader icon={FileText} title="Documents in Pipeline" />
                                {pipelineFilter && (
                                    <button onClick={() => setPipelineFilter(null)}
                                        className="inline-flex items-center gap-1 px-2 py-0.5 text-[10px] font-medium bg-blue-100 text-blue-700 rounded-full hover:bg-blue-200 transition-colors">
                                        {pipelineFilter.replace(/_/g, " ")}
                                        <span className="ml-0.5">&times;</span>
                                    </button>
                                )}
                            </div>
                            <button onClick={loadPipelineDocs} disabled={pipelineDocsLoading} className="text-xs text-gray-400 hover:text-gray-600">
                                <RefreshCw className={`size-3.5 ${pipelineDocsLoading ? "animate-spin" : ""}`} />
                            </button>
                        </div>
                        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                            {pipelineDocs.length === 0 ? (
                                <div className="p-8 text-center text-sm text-gray-400">
                                    <CheckCircle className="size-8 text-green-200 mx-auto mb-2" />
                                    No documents in the pipeline — all processed.
                                </div>
                            ) : (
                                <div className="overflow-x-auto">
                                    <table className="w-full text-sm">
                                        <thead className="bg-gray-50 border-b border-gray-200">
                                            <tr>
                                                <th className="text-left px-4 py-2 font-medium text-gray-600">Document</th>
                                                <th className="text-left px-3 py-2 font-medium text-gray-600 w-36">Status</th>
                                                <th className="text-left px-3 py-2 font-medium text-gray-600 w-28">Duration</th>
                                                <th className="text-left px-3 py-2 font-medium text-gray-600 w-28">Uploaded By</th>
                                                <th className="text-left px-3 py-2 font-medium text-gray-600 w-20">Retries</th>
                                                <th className="text-left px-3 py-2 font-medium text-gray-600 w-64">Error</th>
                                                <th className="text-left px-3 py-2 font-medium text-gray-600 w-16">Actions</th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-gray-100">
                                            {pipelineDocs.filter((doc: any) => !pipelineFilter || doc.status === pipelineFilter).map((doc: any) => {
                                                const isFailed = doc.status?.includes("FAILED");
                                                const isActive = doc.status === "PROCESSING" || doc.status === "CLASSIFYING";
                                                const dur = doc.durationMs ?? 0;
                                                const durStr = dur > 86400000 ? `${Math.floor(dur / 86400000)}d` :
                                                    dur > 3600000 ? `${Math.floor(dur / 3600000)}h` :
                                                    dur > 60000 ? `${Math.floor(dur / 60000)}m` : `${Math.floor(dur / 1000)}s`;
                                                return (
                                                    <tr key={doc.id} className={`hover:bg-gray-50 ${isFailed ? "bg-red-50/50" : ""}`}>
                                                        <td className="px-4 py-2">
                                                            <a href={`/documents?doc=${doc.slug ?? doc.id}`}
                                                                className="text-sm text-gray-900 hover:text-blue-700 truncate block max-w-64">
                                                                {doc.fileName}
                                                            </a>
                                                        </td>
                                                        <td className="px-3 py-2">
                                                            <span className={`inline-flex items-center gap-1 px-2 py-0.5 text-[10px] font-medium rounded-full ${
                                                                isFailed ? "bg-red-100 text-red-700" :
                                                                isActive ? "bg-blue-100 text-blue-700" :
                                                                doc.status === "REVIEW_REQUIRED" ? "bg-amber-100 text-amber-700" :
                                                                doc.status === "GOVERNANCE_APPLIED" || doc.status === "CLASSIFIED" ? "bg-green-100 text-green-700" :
                                                                "bg-gray-100 text-gray-600"
                                                            }`}>
                                                                {isActive && <Loader2 className="size-3 animate-spin" />}
                                                                {doc.status?.replace(/_/g, " ")}
                                                            </span>
                                                        </td>
                                                        <td className="px-3 py-2 text-xs text-gray-500 tabular-nums">{durStr}</td>
                                                        <td className="px-3 py-2 text-xs text-gray-500 truncate">{doc.uploadedBy}</td>
                                                        <td className="px-3 py-2 text-xs text-gray-500">{doc.retryCount > 0 ? doc.retryCount : "—"}</td>
                                                        <td className="px-3 py-2 text-xs text-red-500 truncate max-w-64" title={doc.lastError}>
                                                            {doc.lastError ? (
                                                                <>{doc.lastErrorStage && <span className="font-semibold">[{doc.lastErrorStage}] </span>}{doc.lastError.split("\n")[0]}</>
                                                            ) : "—"}
                                                        </td>
                                                        <td className="px-3 py-2">
                                                            <DeleteDocButton docId={doc.id} fileName={doc.fileName} onDeleted={loadPipelineDocs} />
                                                        </td>
                                                    </tr>
                                                );
                                            })}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>
                    </section>
                </div>
            )}

            {/* ── Runs Tab ──────────────────────────────── */}
            {monTab === "runs" && <PipelineRunsTab />}

            {/* ── Overview Tab ──────────────────────────── */}
            {monTab === "overview" && <>

            {/* Service Health */}
            <section className="mb-8">
                <SectionHeader icon={Server} title="Service Health" />
                <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 gap-4">
                    {health?.services.map((svc) => (
                        <ServiceCard key={svc.name} service={svc} onRefreshHealth={fetchAll} />
                    )) ?? Array.from({ length: 5 }).map((_, i) => <SkeletonCard key={i} />)}
                </div>
            </section>

            {/* Pipeline Metrics */}
            <section className="mb-8">
                <SectionHeader icon={Activity} title="Document Pipeline" />
                {pipeline ? (
                    <>
                        {/* Status pipeline visualization */}
                        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-4">
                            <div className="flex items-center gap-2 mb-4">
                                <span className="text-sm font-medium text-gray-700">Pipeline Status</span>
                                <span className="text-xs text-gray-400">({pipeline.totalDocuments} total documents)</span>
                            </div>
                            <div className="flex gap-2">
                                {Object.entries(pipeline.statusCounts).filter(([, v]) => v > 0).map(([status, count]) => (
                                    <PipelineStage key={status} status={status} count={count} total={pipeline.totalDocuments} />
                                ))}
                                {pipeline.totalDocuments === 0 && <span className="text-sm text-gray-400">No documents processed yet</span>}
                            </div>
                        </div>

                        {/* Metrics cards */}
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
                            <MetricCard icon={ArrowUpRight} label="Last 24h" value={String(pipeline.throughput.last24h)} sub="documents classified" colour="blue" />
                            <MetricCard icon={FileText} label="Last 7 days" value={String(pipeline.throughput.last7d)} sub="documents classified" colour="green" />
                            <MetricCard icon={Clock} label="Avg Classification" value={formatMs(pipeline.avgClassificationTimeMs)} sub="upload to classified" colour="purple" />
                            <MetricCard icon={AlertTriangle} label="Stale Documents"
                                value={String(pipeline.staleDocuments)}
                                sub="stuck > 10 min"
                                colour={pipeline.staleDocuments > 0 ? "red" : "green"} />
                        </div>

                        {/* Pipeline Controls */}
                        <PipelineControls
                            staleCount={pipeline.staleDocuments}
                            inFlightCount={
                                (pipeline.statusCounts["PROCESSING"] ?? 0) +
                                (pipeline.statusCounts["CLASSIFYING"] ?? 0) +
                                (pipeline.statusCounts["UPLOADING"] ?? 0)
                            }
                            failedCount={
                                (pipeline.statusCounts["PROCESSING_FAILED"] ?? 0) +
                                (pipeline.statusCounts["CLASSIFICATION_FAILED"] ?? 0) +
                                (pipeline.statusCounts["ENFORCEMENT_FAILED"] ?? 0)
                            }
                            onAction={fetchAll}
                        />

                        {/* Circuit breaker + Queue depths */}
                        {pipeline.circuitBreaker && pipeline.circuitBreaker.state !== "UNKNOWN" && (
                            <div className={`rounded-lg shadow-sm border p-4 mb-4 flex items-center justify-between ${
                                pipeline.circuitBreaker.state === "OPEN"
                                    ? "bg-red-50 border-red-300"
                                    : "bg-green-50 border-green-200"
                            }`}>
                                <div className="flex items-center gap-3">
                                    <div className={`size-3 rounded-full ${
                                        pipeline.circuitBreaker.state === "OPEN" ? "bg-red-500 animate-pulse" : "bg-green-500"
                                    }`} />
                                    <div>
                                        <span className="text-sm font-medium text-gray-900">
                                            LLM Circuit Breaker: {pipeline.circuitBreaker.state}
                                        </span>
                                        {pipeline.circuitBreaker.state === "OPEN" && (
                                            <p className="text-xs text-red-600 mt-0.5">
                                                Classification paused — {pipeline.circuitBreaker.consecutiveFailures} consecutive failures.
                                                Auto-retries in {pipeline.circuitBreaker.cooldownSeconds}s.
                                            </p>
                                        )}
                                        {pipeline.circuitBreaker.state === "CLOSED" && pipeline.circuitBreaker.consecutiveFailures > 0 && (
                                            <p className="text-xs text-amber-600 mt-0.5">
                                                {pipeline.circuitBreaker.consecutiveFailures}/{pipeline.circuitBreaker.threshold} failures before circuit opens
                                            </p>
                                        )}
                                    </div>
                                </div>
                            </div>
                        )}
                        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
                            <h4 className="text-sm font-medium text-gray-700 mb-3">RabbitMQ Queue Depths</h4>
                            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                                {Object.entries(pipeline.queueDepths).map(([queue, depth]) => (
                                    <QueueCard key={queue} queue={queue} depth={depth as number} onPurged={fetchAll} />
                                ))}
                            </div>
                        </div>
                    </>
                ) : <SkeletonBlock />}
            </section>

            {/* Pipeline Activity Feed */}
            <section className="mb-8">
                <SectionHeader icon={Radio} title="Pipeline Activity" />
                <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                    {activity.length === 0 ? (
                        <div className="p-6 text-center text-sm text-gray-400">
                            {connected ? "Waiting for pipeline activity..." : "Connecting to live feed..."}
                        </div>
                    ) : (
                        <div className="divide-y divide-gray-100 max-h-64 overflow-y-auto">
                            {activity.map((item, i) => (
                                <div key={`${item.documentId}-${item.timestamp}-${i}`}
                                    className="flex items-center justify-between px-4 py-2 text-sm hover:bg-gray-50">
                                    <div className="flex items-center gap-3 min-w-0">
                                        <FileText className="size-4 text-gray-400 shrink-0" />
                                        <span className="truncate text-gray-700 max-w-48" title={item.fileName}>
                                            {item.fileName || item.documentId}
                                        </span>
                                        <ActivityStatusBadge status={item.status} />
                                    </div>
                                    <span className="text-xs text-gray-400 shrink-0 ml-2">
                                        {formatTimeAgo(item.timestamp)}
                                    </span>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </section>

            {/* Pipeline Processing Log */}
            <section className="mb-8">
                <div className="flex items-center justify-between mb-3">
                    <SectionHeader icon={Activity} title="Processing Log" />
                    {logs.length > 0 && (
                        <button onClick={() => setLogs([])} className="text-xs text-gray-400 hover:text-gray-600">Clear</button>
                    )}
                </div>
                <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                    {logs.length === 0 ? (
                        <div className="p-6 text-center text-sm text-gray-400">
                            {connected ? "Processing logs will appear here as documents move through the pipeline..." : "Connecting..."}
                        </div>
                    ) : (
                        <div className="divide-y divide-gray-50 max-h-96 overflow-y-auto font-mono text-xs">
                            {logs.map((entry, i) => {
                                const levelColor = entry.level === "ERROR" ? "text-red-600 bg-red-50" :
                                    entry.level === "WARN" ? "text-amber-600 bg-amber-50" : "text-gray-600";
                                const stageColor: Record<string, string> = {
                                    EXTRACTION: "text-blue-600",
                                    LLM_TOOL: "text-indigo-600",
                                    PII_SCAN: "text-purple-600",
                                    CLASSIFICATION: "text-amber-600",
                                    ENFORCEMENT: "text-green-600",
                                };
                                return (
                                    <div key={`${entry.documentId}-${entry.timestamp}-${i}`}
                                        className={`flex items-start gap-2 px-3 py-1.5 hover:bg-gray-50 ${entry.level === "ERROR" ? "bg-red-50/50" : ""}`}>
                                        <span className="text-gray-400 shrink-0 w-16 text-right tabular-nums">
                                            {new Date(entry.timestamp).toLocaleTimeString()}
                                        </span>
                                        <span className={`shrink-0 w-20 font-semibold ${stageColor[entry.stage] ?? "text-gray-500"}`}>
                                            {entry.stage.replace(/_/g, " ")}
                                        </span>
                                        <span className={`shrink-0 w-10 ${levelColor}`}>
                                            {entry.level}
                                        </span>
                                        <span className="text-gray-700 truncate flex-1" title={entry.message}>
                                            {entry.message}
                                        </span>
                                        <span className="text-gray-300 shrink-0 truncate max-w-32" title={entry.fileName}>
                                            {entry.fileName}
                                        </span>
                                        {entry.durationMs > 0 && (
                                            <span className="text-gray-400 shrink-0 w-14 text-right tabular-nums">
                                                {entry.durationMs >= 1000
                                                    ? `${(entry.durationMs / 1000).toFixed(1)}s`
                                                    : `${entry.durationMs}ms`}
                                            </span>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </section>

            {/* Infrastructure */}
            <section>
                <SectionHeader icon={Database} title="Infrastructure" />
                {infra ? (
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <InfraCard title="MongoDB" icon={Database}
                            rows={[
                                { label: "Database Size", value: formatBytes(infra.mongodb.databaseSizeBytes ?? 0) },
                                { label: "Storage Size", value: formatBytes(infra.mongodb.storageSizeBytes ?? 0) },
                                { label: "Collections", value: String(infra.mongodb.collections ?? 0) },
                                { label: "Total Objects", value: String(infra.mongodb.totalObjects ?? 0) },
                            ]} />
                        <InfraCard title="Object Storage" icon={HardDrive}
                            rows={[
                                { label: "Total Files", value: String(infra.storage.totalFiles) },
                                { label: "Total Size", value: formatBytes(infra.storage.totalSizeBytes) },
                            ]} />
                        <InfraCard title="RabbitMQ" icon={Inbox}
                            rows={[
                                { label: "Status", value: infra.rabbitmq.status },
                            ]} />
                    </div>
                ) : <SkeletonBlock />}
            </section>
            </>}

            {/* ── Errors Tab ──────────────────────────── */}
            {monTab === "errors" && (
                <div className="space-y-6">
                    {/* Error Summary Cards */}
                    {errorSummary && (
                        <div className="grid grid-cols-3 gap-4">
                            <MetricCard icon={XCircle} label="Unresolved Errors" value={String(errorSummary.unresolvedCount)} sub="Total active" colour="red" />
                            <MetricCard icon={AlertTriangle} label="Critical" value={String(errorSummary.criticalCount)} sub="Require attention" colour="red" />
                            <MetricCard icon={Clock} label="Last 24 Hours" value={String(errorSummary.last24hCount)} sub="Recent errors" colour="purple" />
                        </div>
                    )}

                    {/* Filter + Refresh */}
                    <div className="flex items-center gap-3">
                        <div className="flex gap-1 bg-gray-100 rounded-lg p-0.5">
                            <button onClick={() => setErrorFilter("unresolved")}
                                className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${errorFilter === "unresolved" ? "bg-white text-gray-900 shadow-sm" : "text-gray-600 hover:text-gray-900"}`}>
                                Unresolved
                            </button>
                            <button onClick={() => setErrorFilter("all")}
                                className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${errorFilter === "all" ? "bg-white text-gray-900 shadow-sm" : "text-gray-600 hover:text-gray-900"}`}>
                                All
                            </button>
                        </div>
                        <button onClick={loadErrors} disabled={errorsLoading} className="text-xs text-gray-400 hover:text-gray-600">
                            <RefreshCw className={`size-3.5 ${errorsLoading ? "animate-spin" : ""}`} />
                        </button>
                    </div>

                    {/* Error Table */}
                    <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                        {errorsLoading && errors.length === 0 ? (
                            <div className="p-8 text-center text-sm text-gray-400">
                                <Loader2 className="size-6 animate-spin mx-auto mb-2 text-gray-300" />
                                Loading errors...
                            </div>
                        ) : errors.length === 0 ? (
                            <div className="p-8 text-center text-sm text-gray-400">
                                <CheckCircle className="size-8 text-green-200 mx-auto mb-2" />
                                No {errorFilter === "unresolved" ? "unresolved " : ""}errors found.
                            </div>
                        ) : (
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead className="bg-gray-50 border-b border-gray-200">
                                        <tr>
                                            <th className="text-left px-4 py-2 font-medium text-gray-600 w-36">Timestamp</th>
                                            <th className="text-left px-3 py-2 font-medium text-gray-600 w-24">Severity</th>
                                            <th className="text-left px-3 py-2 font-medium text-gray-600 w-28">Category</th>
                                            <th className="text-left px-3 py-2 font-medium text-gray-600 w-28">Service</th>
                                            <th className="text-left px-3 py-2 font-medium text-gray-600">Message</th>
                                            <th className="text-left px-3 py-2 font-medium text-gray-600 w-24">Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-gray-100">
                                        {errors.map((err: any) => (
                                            <ErrorRow key={err.id} error={err} onResolved={() => { loadErrors(); fetchAll(); }} />
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </>
    );
}

/* ── Components ────────────────────────────────────── */

function SectionHeader({ icon: Icon, title }: { icon: React.ElementType; title: string }) {
    return (
        <div className="flex items-center gap-2 mb-4">
            <Icon className="size-5 text-gray-500" />
            <h3 className="text-base font-semibold text-gray-900">{title}</h3>
        </div>
    );
}

function ServiceCard({ service, onRefreshHealth }: { service: ServiceStatus; onRefreshHealth: () => void }) {
    const [pinging, setPinging] = useState(false);
    const isUp = service.status === "UP";
    const isDown = service.status === "DOWN";

    const handlePing = async () => {
        setPinging(true);
        try {
            const { data } = await api.post(`/admin/monitoring/services/${service.name}/ping`);
            toast.success(`${SERVICE_LABELS[service.name]}: ${data.status} (${data.responseTimeMs}ms)`);
            onRefreshHealth();
        } catch {
            toast.error(`Failed to ping ${service.name}`);
        } finally {
            setPinging(false);
        }
    };

    return (
        <div className={`bg-white rounded-lg shadow-sm border p-4 ${isDown ? "border-red-200" : "border-gray-200"}`}>
            <div className="flex items-center justify-between mb-2">
                <span className="text-sm font-medium text-gray-900">{SERVICE_LABELS[service.name] ?? service.name}</span>
                {isUp ? <CheckCircle className="size-5 text-green-500" /> :
                 isDown ? <XCircle className="size-5 text-red-500" /> :
                 <AlertTriangle className="size-5 text-amber-500" />}
            </div>
            <div className="flex items-center justify-between">
                <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${
                    isUp ? "bg-green-100 text-green-700" : isDown ? "bg-red-100 text-red-700" : "bg-amber-100 text-amber-700"
                }`}>{service.status}</span>
                {service.responseTimeMs != null && (
                    <span className="text-xs text-gray-400">{service.responseTimeMs}ms</span>
                )}
            </div>
            {service.error && <p className="text-xs text-red-500 mt-2 truncate">{service.error}</p>}
            <button onClick={handlePing} disabled={pinging}
                className="mt-2 w-full inline-flex items-center justify-center gap-1.5 px-2 py-1 text-xs border border-gray-200 rounded-md text-gray-500 hover:bg-gray-50 hover:text-gray-700 disabled:opacity-50 transition-colors">
                {pinging ? <Loader2 className="size-3 animate-spin" /> : <Wifi className="size-3" />}
                Ping
            </button>
        </div>
    );
}

function PipelineStage({ status, count, total }: { status: string; count: number; total: number }) {
    const pct = total > 0 ? Math.max(2, (count / total) * 100) : 0;
    const colours: Record<string, string> = {
        UPLOADED: "bg-gray-200", PROCESSING: "bg-yellow-400", PROCESSING_FAILED: "bg-red-500",
        PROCESSED: "bg-yellow-300", CLASSIFYING: "bg-blue-400", CLASSIFICATION_FAILED: "bg-red-500",
        CLASSIFIED: "bg-green-400", ENFORCEMENT_FAILED: "bg-red-500",
        REVIEW_REQUIRED: "bg-amber-400", GOVERNANCE_APPLIED: "bg-emerald-500",
        INBOX: "bg-blue-400", FILED: "bg-emerald-600",
        ARCHIVED: "bg-gray-400", DISPOSED: "bg-red-300",
    };
    return (
        <div className="flex-1 min-w-0">
            <div className={`h-8 rounded-md flex items-center justify-center ${colours[status] ?? "bg-gray-200"}`}
                style={{ minWidth: `${pct}%` }}>
                <span className="text-xs font-bold text-white drop-shadow-sm">{count}</span>
            </div>
            <div className="text-xs text-gray-500 mt-1 truncate text-center">{status.replace(/_/g, " ")}</div>
        </div>
    );
}

function MetricCard({ icon: Icon, label, value, sub, colour }: {
    icon: React.ElementType; label: string; value: string; sub: string;
    colour: "blue" | "green" | "purple" | "red";
}) {
    const bg = { blue: "bg-blue-50", green: "bg-green-50", purple: "bg-purple-50", red: "bg-red-50" };
    const text = { blue: "text-blue-700", green: "text-green-700", purple: "text-purple-700", red: "text-red-700" };
    const iconCol = { blue: "text-blue-500", green: "text-green-500", purple: "text-purple-500", red: "text-red-500" };
    return (
        <div className={`rounded-lg p-4 ${bg[colour]}`}>
            <div className="flex items-center gap-2 mb-2">
                <Icon className={`size-4 ${iconCol[colour]}`} />
                <span className="text-xs font-medium text-gray-600">{label}</span>
            </div>
            <div className={`text-2xl font-bold ${text[colour]}`}>{value}</div>
            <div className="text-xs text-gray-500 mt-0.5">{sub}</div>
        </div>
    );
}

function InfraCard({ title, icon: Icon, rows }: {
    title: string; icon: React.ElementType; rows: { label: string; value: string }[];
}) {
    return (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
            <div className="flex items-center gap-2 mb-4">
                <Icon className="size-5 text-gray-500" />
                <h4 className="font-semibold text-gray-900">{title}</h4>
            </div>
            <div className="space-y-2">
                {rows.map((r) => (
                    <div key={r.label} className="flex justify-between">
                        <span className="text-sm text-gray-500">{r.label}</span>
                        <span className="text-sm font-medium text-gray-900">{r.value}</span>
                    </div>
                ))}
            </div>
        </div>
    );
}

function PipelineControls({ staleCount, inFlightCount, failedCount, onAction }: {
    staleCount: number; inFlightCount: number; failedCount: number; onAction: () => void;
}) {
    const [resetting, setResetting] = useState(false);
    const [cancelling, setCancelling] = useState(false);
    const [retrying, setRetrying] = useState(false);

    const handleResetStale = async () => {
        if (!confirm(`Reset and re-queue ${staleCount} stale document(s)? They will be sent back through the pipeline from the start.`)) return;
        setResetting(true);
        try {
            const { data } = await api.post("/admin/monitoring/pipeline/reset-stale");
            toast.success(`Reset and re-queued ${data.requeued} stale document(s)`);
            onAction();
        } catch {
            toast.error("Failed to reset stale documents");
        } finally {
            setResetting(false);
        }
    };

    const handleCancelAll = async () => {
        if (!confirm("Cancel ALL in-progress pipeline work? This will purge all queues and reset in-flight documents. They will NOT be re-queued automatically.")) return;
        setCancelling(true);
        try {
            const { data } = await api.post("/admin/monitoring/pipeline/cancel-all");
            toast.success(`Pipeline stopped: ${data.documentsReset} document(s) reset, ${data.queuesPurged} queue(s) purged`);
            onAction();
        } catch {
            toast.error("Failed to cancel pipeline");
        } finally {
            setCancelling(false);
        }
    };

    const handleRetryFailed = async () => {
        if (!confirm(`Retry ${failedCount} failed document(s)? They will be sent back through the pipeline from the start.`)) return;
        setRetrying(true);
        try {
            const { data } = await api.post("/admin/monitoring/pipeline/retry-failed");
            toast.success(`Retried ${data.retried} failed document(s)`);
            onAction();
        } catch {
            toast.error("Failed to retry documents");
        } finally {
            setRetrying(false);
        }
    };

    return (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-4">
            <h4 className="text-sm font-medium text-gray-700 mb-3">Pipeline Controls</h4>
            <div className="flex items-center gap-3 flex-wrap">
                <button onClick={handleResetStale} disabled={resetting || staleCount === 0}
                    className="inline-flex items-center gap-2 px-3 py-2 text-sm font-medium text-amber-700 bg-amber-50 border border-amber-200 rounded-lg hover:bg-amber-100 disabled:opacity-50 transition-colors">
                    {resetting ? <Loader2 className="size-4 animate-spin" /> : <RotateCcw className="size-4" />}
                    Reset Stale ({staleCount})
                </button>
                <button onClick={handleRetryFailed} disabled={retrying || failedCount === 0}
                    className="inline-flex items-center gap-2 px-3 py-2 text-sm font-medium text-orange-700 bg-orange-50 border border-orange-200 rounded-lg hover:bg-orange-100 disabled:opacity-50 transition-colors">
                    {retrying ? <Loader2 className="size-4 animate-spin" /> : <RefreshCw className="size-4" />}
                    Retry Failed ({failedCount})
                </button>
                <button onClick={handleCancelAll} disabled={cancelling || inFlightCount === 0}
                    className="inline-flex items-center gap-2 px-3 py-2 text-sm font-medium text-red-700 bg-red-50 border border-red-200 rounded-lg hover:bg-red-100 disabled:opacity-50 transition-colors">
                    {cancelling ? <Loader2 className="size-4 animate-spin" /> : <OctagonX className="size-4" />}
                    Cancel All In-Flight ({inFlightCount})
                </button>
                {staleCount === 0 && inFlightCount === 0 && failedCount === 0 && (
                    <span className="text-xs text-green-600 font-medium">Pipeline clear</span>
                )}
            </div>
        </div>
    );
}

function QueueCard({ queue, depth, onPurged }: { queue: string; depth: number; onPurged: () => void }) {
    const [purging, setPurging] = useState(false);

    const handlePurge = async () => {
        if (!confirm(`Purge all messages from ${queue}? This cannot be undone.`)) return;
        setPurging(true);
        try {
            await api.post(`/admin/monitoring/queues/${encodeURIComponent(queue)}/purge`);
            toast.success(`Purged queue: ${queue}`);
            onPurged();
        } catch {
            toast.error("Purge failed");
        } finally {
            setPurging(false);
        }
    };

    return (
        <div className="p-3 bg-gray-50 rounded-lg">
            <div className="flex items-center justify-between mb-1">
                <div className="text-xs text-gray-500 truncate">{queue.replace("gls.documents.", "")}</div>
                <Inbox className={`size-4 ${depth > 0 ? "text-amber-400" : "text-gray-300"}`} />
            </div>
            <div className={`text-lg font-bold ${depth > 0 ? "text-amber-600" : "text-gray-400"}`}>
                {depth >= 0 ? depth : "?"}
            </div>
            {depth > 0 && (
                <button onClick={handlePurge} disabled={purging}
                    className="mt-2 w-full inline-flex items-center justify-center gap-1.5 px-2 py-1 text-xs border border-red-200 rounded-md text-red-500 hover:bg-red-50 hover:text-red-700 disabled:opacity-50 transition-colors">
                    {purging ? <Loader2 className="size-3 animate-spin" /> : <Trash2 className="size-3" />}
                    Purge Queue
                </button>
            )}
        </div>
    );
}

function ErrorRow({ error, onResolved }: { error: any; onResolved: () => void }) {
    const [resolving, setResolving] = useState(false);

    const severityColours: Record<string, string> = {
        CRITICAL: "bg-red-100 text-red-700",
        ERROR: "bg-orange-100 text-orange-700",
        WARNING: "bg-amber-100 text-amber-700",
    };
    const categoryColours: Record<string, string> = {
        PIPELINE: "bg-blue-100 text-blue-700",
        STORAGE: "bg-purple-100 text-purple-700",
        QUEUE: "bg-yellow-100 text-yellow-700",
        AUTH: "bg-pink-100 text-pink-700",
        EXTERNAL_API: "bg-cyan-100 text-cyan-700",
        INTERNAL: "bg-gray-100 text-gray-600",
    };

    const handleResolve = async () => {
        const notes = prompt("Resolution notes (optional):");
        if (notes === null) return;
        setResolving(true);
        try {
            await api.post(`/admin/monitoring/errors/${error.id}/resolve`, { resolution: notes || "Resolved" });
            toast.success("Error marked as resolved");
            onResolved();
        } catch {
            toast.error("Failed to resolve error");
        } finally {
            setResolving(false);
        }
    };

    const ts = error.timestamp ? new Date(error.timestamp).toLocaleString() : "—";

    return (
        <tr className={`hover:bg-gray-50 ${error.resolved ? "opacity-50" : ""}`}>
            <td className="px-4 py-2 text-xs text-gray-500 whitespace-nowrap">{ts}</td>
            <td className="px-3 py-2">
                <span className={`inline-flex px-2 py-0.5 text-[10px] font-medium rounded-full ${severityColours[error.severity] ?? "bg-gray-100 text-gray-600"}`}>
                    {error.severity}
                </span>
            </td>
            <td className="px-3 py-2">
                <span className={`inline-flex px-2 py-0.5 text-[10px] font-medium rounded-full ${categoryColours[error.category] ?? "bg-gray-100 text-gray-600"}`}>
                    {error.category}
                </span>
            </td>
            <td className="px-3 py-2 text-xs text-gray-500">{error.service || "—"}</td>
            <td className="px-3 py-2 text-xs text-gray-700 max-w-md">
                <div className="truncate" title={error.message}>{error.message}</div>
                {error.endpoint && <div className="text-gray-400 truncate">{error.httpMethod} {error.endpoint}</div>}
                {error.resolution && <div className="text-green-600 truncate mt-0.5">Resolved: {error.resolution}</div>}
            </td>
            <td className="px-3 py-2">
                {!error.resolved && (
                    <button onClick={handleResolve} disabled={resolving} title="Mark as resolved"
                        className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium text-green-700 bg-green-50 border border-green-200 rounded hover:bg-green-100 disabled:opacity-50 transition-colors">
                        {resolving ? <Loader2 className="size-3 animate-spin" /> : <CheckCircle className="size-3" />}
                        Resolve
                    </button>
                )}
            </td>
        </tr>
    );
}

function DeleteDocButton({ docId, fileName, onDeleted }: { docId: string; fileName: string; onDeleted: () => void }) {
    const [deleting, setDeleting] = useState(false);
    const handleDelete = async () => {
        if (!confirm(`Delete "${fileName}" permanently? This removes the database record and stored file.`)) return;
        setDeleting(true);
        try {
            await api.delete(`/admin/monitoring/documents/${docId}`);
            toast.success(`Deleted: ${fileName}`);
            onDeleted();
        } catch {
            toast.error("Failed to delete document");
        } finally {
            setDeleting(false);
        }
    };
    return (
        <button onClick={handleDelete} disabled={deleting} title="Delete document"
            className="inline-flex items-center justify-center p-1 rounded text-gray-400 hover:text-red-600 hover:bg-red-50 disabled:opacity-50 transition-colors">
            {deleting ? <Loader2 className="size-3.5 animate-spin" /> : <Trash2 className="size-3.5" />}
        </button>
    );
}

function SkeletonCard() {
    return <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 h-24 animate-pulse" />;
}

function SkeletonBlock() {
    return <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 h-48 animate-pulse" />;
}

function formatBytes(bytes: number) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function formatMs(ms: number) {
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    return `${(ms / 60000).toFixed(1)}m`;
}

function formatTimeAgo(ts: number) {
    const diff = Math.floor((Date.now() - ts) / 1000);
    if (diff < 5) return "just now";
    if (diff < 60) return `${diff}s ago`;
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    return `${Math.floor(diff / 3600)}h ago`;
}

function ActivityStatusBadge({ status }: { status: string }) {
    const colours: Record<string, string> = {
        UPLOADED: "bg-gray-100 text-gray-600",
        PROCESSING: "bg-yellow-100 text-yellow-700",
        PROCESSING_FAILED: "bg-red-100 text-red-700",
        PROCESSED: "bg-yellow-50 text-yellow-600",
        CLASSIFYING: "bg-blue-100 text-blue-700",
        CLASSIFICATION_FAILED: "bg-red-100 text-red-700",
        CLASSIFIED: "bg-green-100 text-green-700",
        ENFORCEMENT_FAILED: "bg-red-100 text-red-700",
        REVIEW_REQUIRED: "bg-amber-100 text-amber-700",
        GOVERNANCE_APPLIED: "bg-emerald-100 text-emerald-700",
        INBOX: "bg-blue-100 text-blue-700",
        FILED: "bg-emerald-100 text-emerald-700",
    };
    return (
        <span className={`inline-flex text-xs font-medium px-1.5 py-0.5 rounded ${colours[status] ?? "bg-gray-100 text-gray-600"}`}>
            {status.replace(/_/g, " ")}
        </span>
    );
}
