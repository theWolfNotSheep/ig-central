"use client";

import { useCallback, useEffect, useState } from "react";
import { Clock, History, Loader2, RotateCcw, X } from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

/**
 * Phase 3 PR11 — pipeline version history & rollback drawer.
 *
 * Reads `GET /api/admin/pipelines/{id}/versions` and renders the
 * archive of past workflow snapshots. Each row exposes a "Rollback"
 * button that calls `POST /api/admin/pipelines/{id}/rollback/{version}`,
 * after which the parent reloads the pipeline so the visual editor
 * picks up the restored steps + nodes + edges.
 *
 * The panel slides in from the right; clicking the backdrop or the
 * close button hides it.
 */

type PipelineSnapshot = {
    version: number;
    savedAt: string;
    savedBy: string;
    changelog: string;
    steps?: unknown[];
    visualNodes?: unknown[];
    visualEdges?: unknown[];
};

type VersionsResponse = {
    currentVersion: number;
    versions: PipelineSnapshot[];
};

type Props = {
    pipelineId: string;
    open: boolean;
    onClose: () => void;
    onRolledBack: () => void;
};

export default function PipelineVersionHistoryPanel({
    pipelineId, open, onClose, onRolledBack,
}: Props) {
    const [data, setData] = useState<VersionsResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const [rollingBack, setRollingBack] = useState<number | null>(null);

    const load = useCallback(async () => {
        if (!open) return;
        setLoading(true);
        try {
            const { data } = await api.get<VersionsResponse>(
                `/admin/pipelines/${pipelineId}/versions`);
            setData(data);
        } catch {
            toast.error("Failed to load version history");
            setData(null);
        } finally {
            setLoading(false);
        }
    }, [open, pipelineId]);

    useEffect(() => { load(); }, [load]);

    const onRollback = async (version: number) => {
        if (!confirm(`Roll back the workflow to v${version}? The current state will be archived as a new version (the rollback itself is reversible).`)) return;
        setRollingBack(version);
        try {
            await api.post(`/admin/pipelines/${pipelineId}/rollback/${version}`);
            toast.success(`Rolled back to v${version}`);
            await load();
            onRolledBack();
        } catch (err: unknown) {
            const e = err as { response?: { status?: number; data?: { error?: string } } };
            const msg = e?.response?.data?.error;
            toast.error(`Rollback failed${msg ? ": " + msg : ""}`);
        } finally {
            setRollingBack(null);
        }
    };

    if (!open) return null;

    const versions = (data?.versions ?? []).slice().sort((a, b) => b.version - a.version);

    return (
        <>
            <div className="fixed inset-0 z-30 bg-black/30" onClick={onClose} />
            <div className="fixed top-0 right-0 z-40 h-full w-96 bg-white shadow-2xl flex flex-col">
                <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200">
                    <div className="flex items-center gap-2">
                        <History className="size-4 text-gray-600" />
                        <h3 className="font-medium text-gray-900">Version history</h3>
                        {data && (
                            <span className="text-xs text-gray-500">
                                · current: v{data.currentVersion}
                            </span>
                        )}
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
                        <X className="size-4" />
                    </button>
                </div>

                <div className="flex-1 overflow-y-auto">
                    {loading ? (
                        <div className="p-8 text-center"><Loader2 className="size-5 animate-spin text-gray-300 mx-auto" /></div>
                    ) : versions.length === 0 ? (
                        <div className="p-6 text-center text-sm text-gray-400">
                            <Clock className="size-6 text-gray-200 mx-auto mb-2" />
                            No saved versions yet. Edit and save the pipeline to start the history.
                        </div>
                    ) : (
                        <div className="divide-y divide-gray-100">
                            {versions.map(snap => (
                                <div key={snap.version} className="p-4 hover:bg-gray-50">
                                    <div className="flex items-center justify-between mb-1">
                                        <span className="font-mono text-sm font-semibold text-gray-900">v{snap.version}</span>
                                        <button onClick={() => onRollback(snap.version)}
                                            disabled={rollingBack != null}
                                            className="inline-flex items-center gap-1 px-2 py-1 text-xs border border-gray-300 rounded text-gray-700 hover:bg-gray-50 disabled:opacity-50">
                                            {rollingBack === snap.version
                                                ? <Loader2 className="size-3 animate-spin" />
                                                : <RotateCcw className="size-3" />}
                                            Rollback
                                        </button>
                                    </div>
                                    <div className="text-xs text-gray-700 mb-1">{snap.changelog}</div>
                                    <div className="text-[10px] text-gray-500">
                                        {snap.savedBy} · {formatRelative(snap.savedAt)}
                                    </div>
                                    <div className="text-[10px] text-gray-400 mt-1">
                                        {snap.steps?.length ?? 0} steps · {snap.visualNodes?.length ?? 0} nodes ·{" "}
                                        {snap.visualEdges?.length ?? 0} edges
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </>
    );
}

function formatRelative(iso: string): string {
    const t = new Date(iso).getTime();
    if (Number.isNaN(t)) return iso;
    const delta = (Date.now() - t) / 1000;
    if (delta < 60) return `${Math.round(delta)}s ago`;
    if (delta < 3600) return `${Math.round(delta / 60)}m ago`;
    if (delta < 86400) return `${Math.round(delta / 3600)}h ago`;
    return `${Math.round(delta / 86400)}d ago`;
}
