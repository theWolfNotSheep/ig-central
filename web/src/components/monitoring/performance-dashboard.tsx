"use client";

import { useCallback, useEffect, useState } from "react";
import { Activity, Gauge, Loader2, RefreshCw, Inbox, Lock } from "lucide-react";
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

const COLOURS = {
    acquired: "#22c55e",
    skipped: "#f59e0b",
    duration: "#3b82f6",
    replayed: "#10b981",
    skippedReplay: "#94a3b8",
    real: "#6366f1",
    dryRun: "#0ea5e9",
};

export default function PerformanceDashboard() {
    const [data, setData] = useState<DashboardResponse | null>(null);
    const [loading, setLoading] = useState(false);

    const fetchData = useCallback(async () => {
        setLoading(true);
        try {
            const { data } = await api.get<DashboardResponse>("/admin/metrics/dashboard");
            setData(data);
        } catch {
            console.error("Failed to fetch metrics dashboard");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { fetchData(); }, [fetchData]);

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-end">
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

function Stat({ label, value }: { label: string; value: string }) {
    return (
        <div>
            <div className="text-[10px] uppercase tracking-wide text-gray-500">{label}</div>
            <div className="text-lg font-semibold text-gray-900 tabular-nums">{value}</div>
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
