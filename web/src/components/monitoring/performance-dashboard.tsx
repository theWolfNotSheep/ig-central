"use client";

import { useCallback, useEffect, useState } from "react";
import {
    Activity, Gauge, Loader2, RefreshCw, Inbox, Lock, Network,
    CheckCircle2, XCircle,
} from "lucide-react";
import {
    Bar, BarChart, CartesianGrid, Cell, Legend, ResponsiveContainer, Tooltip,
    XAxis, YAxis,
} from "recharts";
import api from "@/lib/axios/axios.client";

/**
 * Phase 3 PR2 — Performance dashboards.
 *
 * Reads `GET /api/admin/metrics/dashboard` (backed by the shipped
 * Phase 2 Micrometer metrics on gls-app-assembly) and renders four
 * sections: stale-pipeline detection age, connector lock activity,
 * scheduler lock outcomes, and DLQ replay activity.
 *
 * Cross-service metrics (router classify counters, LLM circuit-breaker
 * state, fallback invocations) live on the router/worker registries
 * and aren't surfaced here yet — they'll need an HTTP probe layer.
 */

type SummaryStats = {
    count: number;
    mean: number;
    max: number;
    p50: number;
    p95: number;
    p99: number;
};

type TaggedCounter = {
    tagKey: string;
    tagValue: string;
    values: Record<string, number>;
};

type DlqReplayActivity = {
    queue: string;
    byMode: Record<string, Record<string, number>>;
};

type DashboardResponse = {
    timestamp: string;
    stalePipelineDetectionAge: SummaryStats;
    connectorLocks: TaggedCounter[];
    schedulerLocks: TaggedCounter[];
    dlqReplay: DlqReplayActivity[];
};

type PromSample = {
    metricName: string;
    labels: Record<string, string>;
    value: number;
};

type ServiceProbeResult = {
    service: string;
    url: string;
    reachable: boolean;
    error?: string | null;
    elapsedMs: number;
    samples: PromSample[];
};

type CrossServiceResponse = {
    timestamp: string;
    services: ServiceProbeResult[];
};

const COLOURS = {
    acquired: "#22c55e",
    skipped: "#f59e0b",
    duration: "#3b82f6",
    replayed: "#10b981",
    skippedReplay: "#94a3b8",
    real: "#6366f1",
    dryRun: "#0ea5e9",
};

/** Polling intervals offered in the toolbar. 0 means "off — manual refresh only". */
const REFRESH_INTERVALS_MS = [
    { label: "Off", value: 0 },
    { label: "30s", value: 30_000 },
    { label: "1m", value: 60_000 },
    { label: "5m", value: 300_000 },
] as const;

export default function PerformanceDashboard() {
    const [data, setData] = useState<DashboardResponse | null>(null);
    const [crossService, setCrossService] = useState<CrossServiceResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const [refreshIntervalMs, setRefreshIntervalMs] = useState<number>(0);

    const fetchData = useCallback(async () => {
        setLoading(true);
        try {
            const [local, cross] = await Promise.allSettled([
                api.get<DashboardResponse>("/admin/metrics/dashboard"),
                api.get<CrossServiceResponse>("/admin/metrics/dashboard/cross-service"),
            ]);
            if (local.status === "fulfilled") setData(local.value.data);
            if (cross.status === "fulfilled") setCrossService(cross.value.data);
        } catch {
            console.error("Failed to fetch metrics dashboard");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { fetchData(); }, [fetchData]);

    // Auto-refresh poll. 0 = off (default). The interval is paused while a
    // request is in-flight to avoid stacking calls on a slow probe.
    useEffect(() => {
        if (refreshIntervalMs === 0) return;
        const handle = setInterval(() => {
            if (!loading) fetchData();
        }, refreshIntervalMs);
        return () => clearInterval(handle);
    }, [refreshIntervalMs, loading, fetchData]);

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-end gap-2">
                <label className="inline-flex items-center gap-1.5 text-xs text-gray-600">
                    Auto-refresh
                    <select value={refreshIntervalMs}
                        onChange={e => setRefreshIntervalMs(Number(e.target.value))}
                        className="text-xs border border-gray-200 rounded-md px-2 py-1 bg-white">
                        {REFRESH_INTERVALS_MS.map(opt => (
                            <option key={opt.value} value={opt.value}>{opt.label}</option>
                        ))}
                    </select>
                </label>
                <button
                    onClick={fetchData}
                    disabled={loading}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs text-gray-600 hover:text-gray-900 border border-gray-200 rounded-md hover:bg-gray-50 disabled:opacity-50">
                    {loading ? <Loader2 className="size-3 animate-spin" /> : <RefreshCw className="size-3" />}
                    Refresh
                    {data && (
                        <span className="text-gray-400 ml-1">
                            · {new Date(data.timestamp).toLocaleTimeString()}
                        </span>
                    )}
                </button>
            </div>

            <PanelHeader icon={Gauge} title="Stale-pipeline detection age" subtitle="Age (ms) of runs caught by the stale-recovery sweep — how late the recovery is firing." />
            <StaleAgePanel stats={data?.stalePipelineDetectionAge} loading={loading && !data} />

            <PanelHeader icon={Inbox} title="Connector lock activity" subtitle="Per-source ShedLock acquisitions vs skips, with mean action duration." />
            <ConnectorLocksPanel rows={data?.connectorLocks ?? []} loading={loading && !data} />

            <PanelHeader icon={Lock} title="Scheduler lock outcomes" subtitle="Acquired vs skipped per named lock — visualises leader election." />
            <SchedulerLocksChart rows={data?.schedulerLocks ?? []} loading={loading && !data} />

            <PanelHeader icon={Activity} title="DLQ replay activity" subtitle="Replayed vs skipped messages per queue and mode (preview vs real)." />
            <DlqReplayPanel rows={data?.dlqReplay ?? []} loading={loading && !data} />

            <PanelHeader icon={Network} title="Cross-service metrics"
                subtitle="Live scrape of /actuator/prometheus on the router and llm-worker — surfaces metrics that don't live on this app's registry." />
            <CrossServicePanel data={crossService} loading={loading && !crossService} />
        </div>
    );
}

function PanelHeader({ icon: Icon, title, subtitle }: { icon: React.ElementType; title: string; subtitle: string }) {
    return (
        <div className="flex items-start gap-2 pt-2">
            <Icon className="size-4 text-gray-500 mt-0.5" />
            <div>
                <h4 className="text-sm font-semibold text-gray-900">{title}</h4>
                <p className="text-xs text-gray-500">{subtitle}</p>
            </div>
        </div>
    );
}

function StaleAgePanel({ stats, loading }: { stats: SummaryStats | undefined; loading: boolean }) {
    if (loading) return <SkeletonPanel />;
    const empty = !stats || stats.count === 0;
    return (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            {empty ? (
                <div className="text-sm text-gray-500 py-4 text-center">
                    No stale runs detected since startup.
                </div>
            ) : (
                <div className="grid grid-cols-2 md:grid-cols-6 gap-3">
                    <Stat label="Detected" value={stats!.count.toLocaleString()} />
                    <Stat label="Mean" value={formatMs(stats!.mean)} />
                    <Stat label="Max" value={formatMs(stats!.max)} />
                    <Stat label="p50" value={formatMs(stats!.p50)} />
                    <Stat label="p95" value={formatMs(stats!.p95)} />
                    <Stat label="p99" value={formatMs(stats!.p99)} />
                </div>
            )}
        </div>
    );
}

function ConnectorLocksPanel({ rows, loading }: { rows: TaggedCounter[]; loading: boolean }) {
    if (loading) return <SkeletonPanel />;
    if (rows.length === 0) {
        return (
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
                <p className="text-sm text-gray-500 py-4 text-center">
                    No connector lock activity recorded yet.
                </p>
            </div>
        );
    }
    const chart = rows.map(r => ({
        source: r.tagValue,
        acquired: r.values.acquired ?? 0,
        skipped: r.values.skipped ?? 0,
        meanMs: r.values.meanDurationMs ?? 0,
    }));
    return (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 space-y-4">
            <div style={{ width: "100%", height: 220 }}>
                <ResponsiveContainer>
                    <BarChart data={chart} margin={{ top: 10, right: 16, left: 0, bottom: 8 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
                        <XAxis dataKey="source" tick={{ fontSize: 11 }} />
                        <YAxis tick={{ fontSize: 11 }} />
                        <Tooltip />
                        <Legend wrapperStyle={{ fontSize: 12 }} />
                        <Bar dataKey="acquired" name="Acquired" fill={COLOURS.acquired} />
                        <Bar dataKey="skipped" name="Skipped (peer held)" fill={COLOURS.skipped} />
                    </BarChart>
                </ResponsiveContainer>
            </div>
            <div className="overflow-auto">
                <table className="w-full text-xs">
                    <thead className="bg-gray-50 border-b border-gray-100">
                        <tr>
                            <th className="text-left px-3 py-1.5 font-medium text-gray-600">Source</th>
                            <th className="text-right px-3 py-1.5 font-medium text-gray-600">Acquired</th>
                            <th className="text-right px-3 py-1.5 font-medium text-gray-600">Skipped</th>
                            <th className="text-right px-3 py-1.5 font-medium text-gray-600">Mean duration</th>
                        </tr>
                    </thead>
                    <tbody>
                        {chart.map(r => (
                            <tr key={r.source} className="border-b border-gray-50 last:border-0">
                                <td className="px-3 py-1.5 font-mono text-gray-900">{r.source}</td>
                                <td className="px-3 py-1.5 text-right tabular-nums text-gray-700">{r.acquired.toLocaleString()}</td>
                                <td className="px-3 py-1.5 text-right tabular-nums text-gray-700">{r.skipped.toLocaleString()}</td>
                                <td className="px-3 py-1.5 text-right tabular-nums text-gray-500">{formatMs(r.meanMs)}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

function SchedulerLocksChart({ rows, loading }: { rows: TaggedCounter[]; loading: boolean }) {
    if (loading) return <SkeletonPanel />;
    if (rows.length === 0) {
        return (
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
                <p className="text-sm text-gray-500 py-4 text-center">
                    No scheduler lock outcomes recorded yet.
                </p>
            </div>
        );
    }
    const chart = rows.map(r => ({
        name: r.tagValue,
        acquired: r.values.acquired ?? 0,
        skipped: r.values.skipped ?? 0,
    }));
    return (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <div style={{ width: "100%", height: Math.max(220, chart.length * 36) }}>
                <ResponsiveContainer>
                    <BarChart layout="vertical" data={chart} margin={{ top: 10, right: 16, left: 16, bottom: 8 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
                        <XAxis type="number" tick={{ fontSize: 11 }} />
                        <YAxis type="category" dataKey="name" tick={{ fontSize: 11 }} width={180} />
                        <Tooltip />
                        <Legend wrapperStyle={{ fontSize: 12 }} />
                        <Bar dataKey="acquired" name="Acquired" fill={COLOURS.acquired} />
                        <Bar dataKey="skipped" name="Skipped" fill={COLOURS.skipped} />
                    </BarChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
}

function DlqReplayPanel({ rows, loading }: { rows: DlqReplayActivity[]; loading: boolean }) {
    if (loading) return <SkeletonPanel />;
    if (rows.length === 0) {
        return (
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
                <p className="text-sm text-gray-500 py-4 text-center">
                    No DLQ replay activity yet.
                </p>
            </div>
        );
    }
    return (
        <div className="space-y-3">
            {rows.map(r => (
                <DlqQueueCard key={r.queue} row={r} />
            ))}
        </div>
    );
}

function DlqQueueCard({ row }: { row: DlqReplayActivity }) {
    const modes = Object.keys(row.byMode).sort();
    const chart = modes.map(mode => ({
        mode,
        replayed: row.byMode[mode]?.replayed ?? 0,
        skipped: row.byMode[mode]?.skipped ?? 0,
    }));
    return (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <div className="text-sm font-mono text-gray-900 mb-2">{row.queue}</div>
            <div style={{ width: "100%", height: 180 }}>
                <ResponsiveContainer>
                    <BarChart data={chart} margin={{ top: 8, right: 16, left: 0, bottom: 8 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
                        <XAxis dataKey="mode" tick={{ fontSize: 11 }} />
                        <YAxis tick={{ fontSize: 11 }} />
                        <Tooltip />
                        <Legend wrapperStyle={{ fontSize: 12 }} />
                        <Bar dataKey="replayed" name="Replayed" fill={COLOURS.replayed}>
                            {chart.map((entry, i) => (
                                <Cell key={i} fill={entry.mode === "dry_run" ? COLOURS.dryRun : COLOURS.replayed} />
                            ))}
                        </Bar>
                        <Bar dataKey="skipped" name="Skipped" fill={COLOURS.skippedReplay} />
                    </BarChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
}

function Stat({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
    return (
        <div>
            <div className="text-[10px] uppercase tracking-wide text-gray-500">{label}</div>
            <div className={`text-lg font-semibold tabular-nums ${highlight ? "text-red-700" : "text-gray-900"}`}>{value}</div>
        </div>
    );
}

function SkeletonPanel() {
    return <div className="bg-white rounded-lg shadow-sm border border-gray-200 h-32 animate-pulse" />;
}

function formatMs(ms: number): string {
    if (!Number.isFinite(ms) || ms <= 0) return "—";
    if (ms < 1) return `${ms.toFixed(2)} ms`;
    if (ms < 1000) return `${ms.toFixed(0)} ms`;
    if (ms < 60_000) return `${(ms / 1000).toFixed(1)} s`;
    if (ms < 3_600_000) return `${(ms / 60_000).toFixed(1)} m`;
    return `${(ms / 3_600_000).toFixed(1)} h`;
}

/* ── Cross-service panel ───────────────────────────────────────── */

const CIRCUIT_STATE_LABEL: Record<number, string> = {
    0: "CLOSED",
    1: "HALF_OPEN",
    2: "OPEN",
};
const CIRCUIT_STATE_BADGE: Record<number, string> = {
    0: "bg-green-100 text-green-700",
    1: "bg-amber-100 text-amber-700",
    2: "bg-red-100 text-red-700",
};

function CrossServicePanel({ data, loading }: { data: CrossServiceResponse | null; loading: boolean }) {
    if (loading) return <SkeletonPanel />;
    if (!data || data.services.length === 0) {
        return (
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
                <p className="text-sm text-gray-500 py-4 text-center">No cross-service metrics configured.</p>
            </div>
        );
    }
    // Use the probe timestamp (deterministic per fetch) rather than Date.now()
    // so render is pure. The probe's "now" is what matters for budget-exhaustion
    // checks anyway — it's the wall-clock when the scrape ran.
    const probeTimeMs = new Date(data.timestamp).getTime();
    return (
        <div className="space-y-3">
            {data.services.map(svc => (
                <ServiceCard key={svc.service} svc={svc} probeTimeMs={probeTimeMs} />
            ))}
        </div>
    );
}

function ServiceCard({ svc, probeTimeMs }: { svc: ServiceProbeResult; probeTimeMs: number }) {
    return (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200">
            <div className="flex items-center gap-2 px-4 py-2 border-b border-gray-100 bg-gray-50">
                {svc.reachable
                    ? <CheckCircle2 className="size-3.5 text-green-600" />
                    : <XCircle className="size-3.5 text-red-600" />}
                <span className="text-sm font-mono font-medium text-gray-900">{svc.service}</span>
                <span className="text-[10px] text-gray-500">{svc.url}</span>
                <span className="ml-auto text-[10px] text-gray-400">{svc.elapsedMs} ms</span>
            </div>
            {!svc.reachable ? (
                <div className="px-4 py-3 text-xs text-red-600">
                    Unreachable: {svc.error ?? "unknown error"}
                </div>
            ) : svc.samples.length === 0 ? (
                <div className="px-4 py-3 text-xs text-gray-400">
                    No matching metrics emitted yet.
                </div>
            ) : svc.service === "router" ? (
                <RouterMetrics samples={svc.samples} probeTimeMs={probeTimeMs} />
            ) : svc.service === "llm-worker" ? (
                <LlmWorkerMetrics samples={svc.samples} />
            ) : (
                <GenericSamplesTable samples={svc.samples} />
            )}
        </div>
    );
}

function RouterMetrics({ samples, probeTimeMs }: { samples: PromSample[]; probeTimeMs: number }) {
    const byMetric = groupByMetric(samples);
    const tierCounts = byMetric["gls_router_classify_result_total"] ?? [];
    const tierByCategory = byMetric["gls_router_classify_tier_by_category_total"] ?? [];
    const cost = byMetric["gls_router_classify_cost_units_total"] ?? [];
    const budgetExhausted = byMetric["router_llm_budget_exhausted_until_epoch_s"] ?? [];
    const permitsAvailable = byMetric["router_rate_limit_permits_available"] ?? [];
    const permitsTotal = byMetric["router_rate_limit_permits_total"] ?? [];

    const tierTotals = tierCounts.reduce<Record<string, number>>((acc, s) => {
        const tier = s.labels.tier ?? "?";
        acc[tier] = (acc[tier] ?? 0) + s.value;
        return acc;
    }, {});
    const costPerTier = cost.reduce<Record<string, number>>((acc, s) => {
        const tier = s.labels.tier ?? "?";
        acc[tier] = (acc[tier] ?? 0) + s.value;
        return acc;
    }, {});
    const budgetExhaustedAt = budgetExhausted[0]?.value ?? 0;
    const budgetActive = budgetExhaustedAt * 1000 > probeTimeMs;
    const available = permitsAvailable[0]?.value;
    const total = permitsTotal[0]?.value;

    return (
        <div className="p-4 space-y-3">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                {Object.entries(tierTotals).sort().map(([tier, count]) => (
                    <Stat key={tier} label={`Tier ${tier}`} value={Math.round(count).toLocaleString()} />
                ))}
            </div>
            {Object.keys(costPerTier).length > 0 && (
                <div className="grid grid-cols-2 md:grid-cols-4 gap-3 pt-2 border-t border-gray-100">
                    {Object.entries(costPerTier).sort().map(([tier, units]) => (
                        <Stat key={tier} label={`${tier} cost units`} value={units.toFixed(1)} />
                    ))}
                </div>
            )}
            <div className="flex items-center gap-3 flex-wrap pt-2 border-t border-gray-100 text-xs">
                <span className={`px-2 py-0.5 rounded text-[10px] font-medium ${
                    budgetActive ? "bg-red-100 text-red-700" : "bg-green-100 text-green-700"}`}>
                    LLM budget {budgetActive ? "EXHAUSTED" : "OK"}
                </span>
                {budgetActive && (
                    <span className="text-gray-500">
                        until {new Date(budgetExhaustedAt * 1000).toLocaleTimeString()}
                    </span>
                )}
                {available != null && total != null && (
                    <>
                        <span className="text-gray-300">·</span>
                        <span className="text-gray-700">
                            Rate-limit permits: <span className="font-mono">{available}/{total}</span>
                        </span>
                    </>
                )}
            </div>
            {tierByCategory.length > 0 && (
                <details className="text-xs">
                    <summary className="cursor-pointer text-gray-500 hover:text-gray-700">
                        Per-category tier breakdown ({tierByCategory.length} entries)
                    </summary>
                    <div className="mt-2 max-h-48 overflow-auto">
                        <table className="w-full text-xs">
                            <thead className="bg-gray-50">
                                <tr>
                                    <th className="text-left px-2 py-1 font-medium text-gray-600">Category</th>
                                    <th className="text-left px-2 py-1 font-medium text-gray-600">Tier</th>
                                    <th className="text-right px-2 py-1 font-medium text-gray-600">Count</th>
                                </tr>
                            </thead>
                            <tbody>
                                {tierByCategory.map((s, i) => (
                                    <tr key={i} className="border-b border-gray-50 last:border-0">
                                        <td className="px-2 py-1 font-mono text-gray-700">{s.labels.category ?? "?"}</td>
                                        <td className="px-2 py-1 font-mono text-gray-700">{s.labels.tier ?? "?"}</td>
                                        <td className="px-2 py-1 text-right tabular-nums text-gray-500">
                                            {Math.round(s.value).toLocaleString()}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </details>
            )}
        </div>
    );
}

function LlmWorkerMetrics({ samples }: { samples: PromSample[] }) {
    const byMetric = groupByMetric(samples);
    const states = byMetric["llm_circuit_breaker_state"] ?? [];
    const failures = byMetric["llm_circuit_breaker_consecutive_failures"] ?? [];
    const fallbacks = byMetric["llm_fallback_invocations_total"] ?? [];

    return (
        <div className="p-4 space-y-3">
            <div className="text-xs font-medium text-gray-700 mb-1">Circuit breakers</div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                {states.map((s, i) => {
                    const backend = s.labels.backend ?? "?";
                    const state = Math.round(s.value);
                    const failureSample = failures.find(f => f.labels.backend === backend);
                    return (
                        <div key={i} className="flex items-center justify-between bg-gray-50 rounded p-2 text-sm">
                            <span className="font-mono text-gray-700">{backend}</span>
                            <div className="flex items-center gap-2">
                                {failureSample && failureSample.value > 0 && (
                                    <span className="text-[10px] text-amber-600">
                                        {failureSample.value} fail{failureSample.value === 1 ? "" : "s"}
                                    </span>
                                )}
                                <span className={`px-2 py-0.5 rounded text-[10px] font-medium ${CIRCUIT_STATE_BADGE[state] ?? "bg-gray-100 text-gray-600"}`}>
                                    {CIRCUIT_STATE_LABEL[state] ?? `STATE ${state}`}
                                </span>
                            </div>
                        </div>
                    );
                })}
            </div>
            {fallbacks.length > 0 && (
                <div className="pt-2 border-t border-gray-100">
                    <div className="text-xs font-medium text-gray-700 mb-1">Fallback invocations</div>
                    <table className="w-full text-xs">
                        <thead className="bg-gray-50">
                            <tr>
                                <th className="text-left px-2 py-1 font-medium text-gray-600">Primary</th>
                                <th className="text-left px-2 py-1 font-medium text-gray-600">Reason</th>
                                <th className="text-right px-2 py-1 font-medium text-gray-600">Count</th>
                            </tr>
                        </thead>
                        <tbody>
                            {fallbacks.map((s, i) => (
                                <tr key={i} className="border-b border-gray-50 last:border-0">
                                    <td className="px-2 py-1 font-mono text-gray-700">{s.labels.primary ?? "?"}</td>
                                    <td className="px-2 py-1 font-mono text-gray-700">{s.labels.reason ?? "?"}</td>
                                    <td className="px-2 py-1 text-right tabular-nums text-gray-500">
                                        {Math.round(s.value).toLocaleString()}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}

function GenericSamplesTable({ samples }: { samples: PromSample[] }) {
    const byMetric = groupByMetric(samples);
    const uptime = byMetric["process_uptime_seconds"]?.[0]?.value;
    const threads = byMetric["jvm_threads_live_threads"]?.[0]?.value;
    const requests = byMetric["http_server_requests_seconds_count"] ?? [];
    const requestsTotal = requests.reduce((sum, s) => sum + s.value, 0);
    const errorRequests = requests.filter(s => {
        const status = s.labels.status ?? "";
        return status.startsWith("4") || status.startsWith("5");
    });
    const errorTotal = errorRequests.reduce((sum, s) => sum + s.value, 0);

    return (
        <div className="p-4 space-y-3">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                {uptime != null && (
                    <Stat label="Uptime" value={formatDurationSeconds(uptime)} />
                )}
                {threads != null && (
                    <Stat label="Live threads" value={Math.round(threads).toLocaleString()} />
                )}
                {requests.length > 0 && (
                    <>
                        <Stat label="Requests served" value={Math.round(requestsTotal).toLocaleString()} />
                        <Stat label="4xx / 5xx"
                            value={errorTotal > 0 ? Math.round(errorTotal).toLocaleString() : "0"}
                            highlight={errorTotal > 0} />
                    </>
                )}
            </div>
            {requests.length > 0 && (
                <details className="text-xs">
                    <summary className="cursor-pointer text-gray-500 hover:text-gray-700">
                        Per-route request breakdown ({requests.length})
                    </summary>
                    <div className="mt-2 max-h-48 overflow-auto">
                        <table className="w-full text-xs">
                            <thead className="bg-gray-50">
                                <tr>
                                    <th className="text-left px-2 py-1 font-medium text-gray-600">Method</th>
                                    <th className="text-left px-2 py-1 font-medium text-gray-600">URI</th>
                                    <th className="text-left px-2 py-1 font-medium text-gray-600">Status</th>
                                    <th className="text-right px-2 py-1 font-medium text-gray-600">Count</th>
                                </tr>
                            </thead>
                            <tbody>
                                {requests
                                    .slice()
                                    .sort((a, b) => b.value - a.value)
                                    .slice(0, 50)
                                    .map((s, i) => {
                                        const status = s.labels.status ?? "?";
                                        const isError = status.startsWith("4") || status.startsWith("5");
                                        return (
                                            <tr key={i} className="border-b border-gray-50 last:border-0">
                                                <td className="px-2 py-1 font-mono text-gray-700">{s.labels.method ?? "?"}</td>
                                                <td className="px-2 py-1 font-mono text-gray-700 truncate max-w-64" title={s.labels.uri}>
                                                    {s.labels.uri ?? "?"}
                                                </td>
                                                <td className={`px-2 py-1 font-mono ${isError ? "text-red-600" : "text-gray-700"}`}>
                                                    {status}
                                                </td>
                                                <td className="px-2 py-1 text-right tabular-nums text-gray-500">
                                                    {Math.round(s.value).toLocaleString()}
                                                </td>
                                            </tr>
                                        );
                                    })}
                            </tbody>
                        </table>
                    </div>
                </details>
            )}
        </div>
    );
}

function formatDurationSeconds(seconds: number): string {
    if (seconds < 60) return `${Math.round(seconds)}s`;
    if (seconds < 3600) return `${Math.round(seconds / 60)}m`;
    if (seconds < 86400) return `${(seconds / 3600).toFixed(1)}h`;
    return `${(seconds / 86400).toFixed(1)}d`;
}

function groupByMetric(samples: PromSample[]): Record<string, PromSample[]> {
    return samples.reduce<Record<string, PromSample[]>>((acc, s) => {
        (acc[s.metricName] ??= []).push(s);
        return acc;
    }, {});
}
