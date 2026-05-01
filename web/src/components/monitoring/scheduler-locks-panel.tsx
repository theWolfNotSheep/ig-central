"use client";

import { useCallback, useEffect, useState } from "react";
import { CheckCircle2, Lock, RefreshCw, Unlock, Loader2 } from "lucide-react";
import api from "@/lib/axios/axios.client";

/**
 * Phase 3 — `@SchedulerLock` leader-status panel.
 *
 * Reads `/api/admin/scheduler/locks` (Phase 2.4 PR3 #117) and shows
 * the current state of every named ShedLock row: which replica holds
 * the lock, until when, plus a derived `active` flag (lockUntil > now).
 *
 * Useful for operators to see at a glance which replica is currently
 * leader for `audit-tier1-leader`, `igc-audit-outbox-relay`,
 * `stale-pipeline-recovery`, `index-reconciliation`, etc.
 */

type LockRow = {
    name: string;
    lockUntil: string | null;
    lockedAt: string | null;
    lockedBy: string | null;
    active: boolean;
};

type LocksResponse = {
    collection: string;
    queriedAt: string;
    locks: LockRow[];
};

export default function SchedulerLocksPanel() {
    const [data, setData] = useState<LocksResponse | null>(null);
    const [loading, setLoading] = useState(false);

    const fetchLocks = useCallback(async () => {
        setLoading(true);
        try {
            const { data } = await api.get<LocksResponse>("/admin/scheduler/locks");
            setData(data);
        } catch {
            console.error("Failed to fetch scheduler locks");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { fetchLocks(); }, [fetchLocks]);

    const sortedLocks = (data?.locks ?? [])
        .slice()
        .sort((a, b) => a.name.localeCompare(b.name));

    return (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200">
            <div className="flex items-center justify-between p-4 border-b border-gray-100">
                <div className="flex items-center gap-2">
                    <Lock className="size-4 text-gray-600" />
                    <h3 className="font-medium text-gray-900">Scheduler locks</h3>
                    {data && (
                        <span className="text-xs text-gray-500">
                            {sortedLocks.filter(l => l.active).length} active
                        </span>
                    )}
                </div>
                <button
                    onClick={fetchLocks}
                    className="flex items-center gap-1 px-2 py-1 text-xs text-gray-600 hover:text-gray-900">
                    {loading ? <Loader2 className="size-3 animate-spin" /> : <RefreshCw className="size-3" />}
                    Refresh
                </button>
            </div>

            {sortedLocks.length === 0 ? (
                <div className="p-4 text-sm text-gray-500">
                    {loading ? "Loading..." : "No scheduler locks found."}
                </div>
            ) : (
                <div className="overflow-auto">
                    <table className="w-full text-sm">
                        <thead className="border-b border-gray-100 bg-gray-50">
                            <tr>
                                <th className="text-left px-3 py-2 font-medium text-gray-600">Status</th>
                                <th className="text-left px-3 py-2 font-medium text-gray-600">Name</th>
                                <th className="text-left px-3 py-2 font-medium text-gray-600">Holder</th>
                                <th className="text-left px-3 py-2 font-medium text-gray-600">Acquired</th>
                                <th className="text-left px-3 py-2 font-medium text-gray-600">Lock until</th>
                            </tr>
                        </thead>
                        <tbody>
                            {sortedLocks.map(lock => (
                                <tr key={lock.name} className="border-b border-gray-50 last:border-0 hover:bg-gray-50">
                                    <td className="px-3 py-2">
                                        {lock.active ? (
                                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-800">
                                                <CheckCircle2 className="size-3" />
                                                ACTIVE
                                            </span>
                                        ) : (
                                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-700">
                                                <Unlock className="size-3" />
                                                EXPIRED
                                            </span>
                                        )}
                                    </td>
                                    <td className="px-3 py-2 font-mono text-xs text-gray-900">{lock.name}</td>
                                    <td className="px-3 py-2 font-mono text-xs text-gray-700">{lock.lockedBy ?? "—"}</td>
                                    <td className="px-3 py-2 text-xs text-gray-500" title={lock.lockedAt ?? ""}>
                                        {formatRelative(lock.lockedAt)}
                                    </td>
                                    <td className="px-3 py-2 text-xs text-gray-500" title={lock.lockUntil ?? ""}>
                                        {formatRelative(lock.lockUntil)}
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

function formatRelative(iso: string | null): string {
    if (!iso) return "—";
    const t = new Date(iso).getTime();
    if (Number.isNaN(t)) return "—";
    const delta = (t - Date.now()) / 1000;
    const abs = Math.abs(delta);
    let unit: string;
    let value: number;
    if (abs < 60) { value = Math.round(abs); unit = "s"; }
    else if (abs < 3600) { value = Math.round(abs / 60); unit = "m"; }
    else if (abs < 86400) { value = Math.round(abs / 3600); unit = "h"; }
    else { value = Math.round(abs / 86400); unit = "d"; }
    return delta < 0 ? `${value}${unit} ago` : `in ${value}${unit}`;
}
