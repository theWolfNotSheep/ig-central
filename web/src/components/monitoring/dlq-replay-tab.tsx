"use client";

import { useCallback, useState } from "react";
import {
    AlertTriangle, ChevronDown, ChevronRight, Eye, FileText, Loader2,
    Play, RefreshCw,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

/**
 * Phase 3 — DLQ inspection + replay UI.
 *
 * Wraps the admin endpoint at `POST /api/admin/dlq/{queueName}/replay`
 * shipped in Phase 2.3 PR4 (#111) + dry-run support from PR5 (#116).
 *
 * The whitelist is fixed in the backend; this UI surfaces the two
 * configured DLQs (`gls.documents.dlq`, `gls.pipeline.dlq`) and lets
 * an admin preview (dry-run) or replay messages from either.
 */

const ALLOWED_DLQS: { name: string; label: string; description: string }[] = [
    {
        name: "gls.documents.dlq",
        label: "Document pipeline DLQ",
        description: "Failed documents.ingested / processed / classified messages.",
    },
    {
        name: "gls.pipeline.dlq",
        label: "Pipeline-execution DLQ",
        description: "Failed pipeline node-execution messages (LLM jobs, resume events).",
    },
];

const DEFAULT_MAX = 100;
const HARD_CAP = 1000;

type ReplayPreview = {
    originExchange: string;
    originRoutingKey: string;
    reason: string | null;
    bodyBytes: number;
};

type ReplayResult = {
    queue: string;
    dryRun: boolean;
    replayed: number;
    skipped: number;
    errors: string[];
    preview: ReplayPreview[];
};

export default function DlqReplayTab() {
    const [expanded, setExpanded] = useState<string | null>(null);
    const [maxByQueue, setMaxByQueue] = useState<Record<string, number>>({});
    const [previewByQueue, setPreviewByQueue] = useState<Record<string, ReplayResult | null>>({});
    const [loadingByQueue, setLoadingByQueue] = useState<Record<string, "preview" | "replay" | null>>({});

    const maxFor = (q: string) => maxByQueue[q] ?? DEFAULT_MAX;

    const setMax = (q: string, v: number) => {
        setMaxByQueue(prev => ({ ...prev, [q]: Math.max(1, Math.min(v, HARD_CAP)) }));
    };

    const callReplay = useCallback(async (queueName: string, dryRun: boolean) => {
        setLoadingByQueue(prev => ({ ...prev, [queueName]: dryRun ? "preview" : "replay" }));
        try {
            const max = maxByQueue[queueName] ?? DEFAULT_MAX;
            const { data } = await api.post<ReplayResult>(
                `/admin/dlq/${encodeURIComponent(queueName)}/replay`,
                null,
                { params: { max, dryRun } });
            setPreviewByQueue(prev => ({ ...prev, [queueName]: data }));
            const verb = dryRun ? "Previewed" : "Replayed";
            toast.success(`${verb} ${data.replayed} message(s) from ${queueName}`);
        } catch (err: unknown) {
            const e = err as { response?: { status?: number } };
            const status = e?.response?.status;
            if (status === 409) {
                toast.error(`Another admin is already draining ${queueName} — try again in a moment`);
            } else if (status === 400) {
                toast.error(`Invalid request — queue not whitelisted or max out of range`);
            } else {
                toast.error(`DLQ ${dryRun ? "preview" : "replay"} failed`);
            }
        } finally {
            setLoadingByQueue(prev => ({ ...prev, [queueName]: null }));
        }
    }, [maxByQueue]);

    return (
        <div className="space-y-4">
            <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-sm flex items-start gap-2">
                <AlertTriangle className="size-4 text-amber-600 shrink-0 mt-0.5" />
                <div className="text-amber-900">
                    <span className="font-medium">Replay re-publishes messages to their original exchange.</span> Use{" "}
                    <span className="font-medium">Preview</span> first to see what would be replayed without consuming.
                </div>
            </div>

            {ALLOWED_DLQS.map(q => {
                const isOpen = expanded === q.name;
                const result = previewByQueue[q.name];
                const loading = loadingByQueue[q.name];
                return (
                    <div key={q.name} className="bg-white rounded-lg shadow-sm border border-gray-200">
                        <button
                            onClick={() => setExpanded(isOpen ? null : q.name)}
                            className="w-full p-4 flex items-center gap-3 text-left hover:bg-gray-50 rounded-t-lg">
                            {isOpen ? <ChevronDown className="size-4 text-gray-500" />
                                    : <ChevronRight className="size-4 text-gray-500" />}
                            <FileText className="size-5 text-gray-600" />
                            <div className="flex-1 min-w-0">
                                <div className="font-medium text-gray-900 font-mono text-sm">{q.name}</div>
                                <div className="text-xs text-gray-500 mt-0.5">{q.description}</div>
                            </div>
                            {result && (
                                <div className="text-xs text-gray-500 shrink-0">
                                    Last: {result.dryRun ? "preview" : "replay"} — {result.replayed} replayed,{" "}
                                    {result.skipped} skipped
                                </div>
                            )}
                        </button>

                        {isOpen && (
                            <div className="border-t border-gray-100 p-4 space-y-4">
                                <div className="flex items-center gap-3 flex-wrap">
                                    <label className="text-sm text-gray-600">
                                        Max messages:
                                        <input
                                            type="number"
                                            min={1}
                                            max={HARD_CAP}
                                            value={maxFor(q.name)}
                                            onChange={(e) => setMax(q.name, parseInt(e.target.value, 10) || DEFAULT_MAX)}
                                            className="ml-2 w-24 px-2 py-1 border border-gray-300 rounded text-sm" />
                                    </label>
                                    <button
                                        onClick={() => callReplay(q.name, /* dryRun */ true)}
                                        disabled={loading != null}
                                        className="flex items-center gap-1.5 px-3 py-1.5 bg-gray-100 hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed rounded text-sm font-medium text-gray-800 transition-colors">
                                        {loading === "preview"
                                            ? <Loader2 className="size-4 animate-spin" />
                                            : <Eye className="size-4" />}
                                        Preview
                                    </button>
                                    <button
                                        onClick={() => callReplay(q.name, /* dryRun */ false)}
                                        disabled={loading != null}
                                        className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed rounded text-sm font-medium text-white transition-colors">
                                        {loading === "replay"
                                            ? <Loader2 className="size-4 animate-spin" />
                                            : <Play className="size-4" />}
                                        Replay
                                    </button>
                                    {result && (
                                        <button
                                            onClick={() => setPreviewByQueue(prev => ({ ...prev, [q.name]: null }))}
                                            className="flex items-center gap-1 px-2 py-1 text-xs text-gray-500 hover:text-gray-700">
                                            <RefreshCw className="size-3" /> Clear
                                        </button>
                                    )}
                                </div>

                                {result && (
                                    <ReplayResultPanel result={result} />
                                )}
                            </div>
                        )}
                    </div>
                );
            })}
        </div>
    );
}

function ReplayResultPanel({ result }: { result: ReplayResult }) {
    return (
        <div className="bg-gray-50 rounded border border-gray-200 p-3 space-y-3">
            <div className="flex items-center gap-3 flex-wrap text-sm">
                <span className={`px-2 py-0.5 rounded text-xs font-medium ${result.dryRun ? "bg-blue-100 text-blue-800" : "bg-green-100 text-green-800"}`}>
                    {result.dryRun ? "DRY RUN" : "REAL"}
                </span>
                <span className="text-gray-700">{result.replayed} {result.dryRun ? "previewed" : "replayed"}</span>
                <span className="text-gray-500">·</span>
                <span className="text-gray-700">{result.skipped} skipped</span>
                {result.errors.length > 0 && (
                    <>
                        <span className="text-gray-500">·</span>
                        <span className="text-red-700">{result.errors.length} errors</span>
                    </>
                )}
            </div>

            {result.errors.length > 0 && (
                <details className="text-xs">
                    <summary className="cursor-pointer text-red-700 font-medium">Errors</summary>
                    <ul className="mt-2 space-y-1 ml-4 list-disc text-red-600">
                        {result.errors.map((e, i) => <li key={i} className="font-mono">{e}</li>)}
                    </ul>
                </details>
            )}

            {result.preview && result.preview.length > 0 && (
                <div>
                    <div className="text-xs font-medium text-gray-700 mb-1.5">
                        {result.dryRun ? "Would replay to:" : "Replayed to:"}
                    </div>
                    <div className="overflow-auto max-h-72 border border-gray-200 rounded">
                        <table className="w-full text-xs">
                            <thead className="bg-white sticky top-0 border-b border-gray-200">
                                <tr>
                                    <th className="text-left px-2 py-1.5 font-medium text-gray-600">Exchange</th>
                                    <th className="text-left px-2 py-1.5 font-medium text-gray-600">Routing key</th>
                                    <th className="text-left px-2 py-1.5 font-medium text-gray-600">Reason</th>
                                    <th className="text-right px-2 py-1.5 font-medium text-gray-600">Body</th>
                                </tr>
                            </thead>
                            <tbody>
                                {result.preview.map((p, i) => (
                                    <tr key={i} className="border-b border-gray-100 last:border-0">
                                        <td className="px-2 py-1 font-mono text-gray-700">{p.originExchange}</td>
                                        <td className="px-2 py-1 font-mono text-gray-700">{p.originRoutingKey}</td>
                                        <td className="px-2 py-1 text-gray-500">{p.reason ?? "—"}</td>
                                        <td className="px-2 py-1 text-right text-gray-500 tabular-nums">
                                            {formatBytes(p.bodyBytes)}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </div>
    );
}

function formatBytes(b: number): string {
    if (b < 1024) return `${b} B`;
    if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)} KB`;
    return `${(b / 1024 / 1024).toFixed(1)} MB`;
}
