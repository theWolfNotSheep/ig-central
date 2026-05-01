"use client";

import { useState } from "react";
import {
    AlertTriangle, ChevronDown, ChevronRight, DollarSign, Eye, Loader2, Play, RefreshCw,
    Sparkles,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

/**
 * Phase 3 — Bulk reclassify trigger.
 *
 * Wraps `POST /api/admin/monitoring/pipeline/bulk-reclassify`. The
 * backend supports two filter modes — by status (multi-select) or by
 * explicit ID list — plus a hard cap. Dry-run is the default first
 * step: it returns the matched count + cost estimate (mean of recent
 * CLASSIFY usage × document count) without queuing anything. Real
 * mode requeues each matched document via the same path as the
 * per-document reclassify endpoint.
 *
 * Don't confuse with `/pipeline/retry-failed` — that's for failed
 * documents specifically. This is for already-classified documents
 * an operator wants to re-run after a prompt or model change.
 */

const STATUS_PRESETS: { value: string; label: string; description: string }[] = [
    { value: "CLASSIFIED", label: "Classified", description: "Auto-classified, awaiting governance." },
    { value: "GOVERNANCE_APPLIED", label: "Governed", description: "Fully processed; reclassify after rule changes." },
    { value: "REVIEW_REQUIRED", label: "Review required", description: "Low-confidence, queued for human review." },
];

const DEFAULT_HARD_CAP = 1000;

type Estimate = {
    documentCount: number;
    sampleSize: number;
    averageCostPerDocumentUsd: number;
    estimatedTotalCostUsd: number;
    averageInputTokens: number;
    averageOutputTokens: number;
    estimatedTotalInputTokens: number;
    estimatedTotalOutputTokens: number;
};

type ReclassifyResult = {
    matched: number;
    queued: number;
    skipped: number;
    errors: string[];
    dryRun: boolean;
    estimate: Estimate;
};

export default function BulkReclassifyPanel() {
    const [open, setOpen] = useState(false);
    const [statuses, setStatuses] = useState<string[]>(["CLASSIFIED"]);
    const [documentIdsRaw, setDocumentIdsRaw] = useState("");
    const [hardCap, setHardCap] = useState(DEFAULT_HARD_CAP);
    const [result, setResult] = useState<ReclassifyResult | null>(null);
    const [loading, setLoading] = useState<"preview" | "execute" | null>(null);

    const toggleStatus = (s: string) => {
        setStatuses(prev => prev.includes(s) ? prev.filter(x => x !== s) : [...prev, s]);
    };

    const callApi = async (dryRun: boolean) => {
        setLoading(dryRun ? "preview" : "execute");
        try {
            const documentIds = documentIdsRaw
                .split(/[\s,]+/)
                .map(s => s.trim())
                .filter(Boolean);
            const body = {
                statuses: documentIds.length > 0 ? null : statuses,
                documentIds: documentIds.length > 0 ? documentIds : null,
                dryRun,
                hardCap,
            };
            const { data } = await api.post<ReclassifyResult>(
                "/admin/monitoring/pipeline/bulk-reclassify", body);
            setResult(data);
            const verb = dryRun ? "Previewed" : "Queued";
            const count = dryRun ? data.matched : data.queued;
            toast.success(`${verb} ${count} document(s)`);
        } catch {
            toast.error(`Bulk reclassify ${dryRun ? "preview" : "execution"} failed`);
        } finally {
            setLoading(null);
        }
    };

    return (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200">
            <button
                onClick={() => setOpen(o => !o)}
                className="w-full p-4 flex items-center gap-3 text-left hover:bg-gray-50 rounded-t-lg">
                {open ? <ChevronDown className="size-4 text-gray-500" />
                      : <ChevronRight className="size-4 text-gray-500" />}
                <Sparkles className="size-5 text-purple-600" />
                <div className="flex-1 min-w-0">
                    <div className="font-medium text-gray-900">Bulk reclassify</div>
                    <div className="text-xs text-gray-500 mt-0.5">
                        Re-run classification on existing documents — preview cost first.
                    </div>
                </div>
                {result && (
                    <div className="text-xs text-gray-500 shrink-0">
                        Last: {result.dryRun ? "preview" : "execute"} — {result.matched} matched,
                        {" "}{result.queued} queued
                    </div>
                )}
            </button>

            {open && (
                <div className="border-t border-gray-100 p-4 space-y-4">
                    <div className="bg-amber-50 border border-amber-200 rounded-md p-3 text-sm flex items-start gap-2">
                        <AlertTriangle className="size-4 text-amber-600 shrink-0 mt-0.5" />
                        <div className="text-amber-900">
                            Always <span className="font-medium">Preview</span> first to see the matched
                            count + cost estimate. Real execution re-queues each document and incurs
                            classification cost.
                        </div>
                    </div>

                    {/* Status filter */}
                    <div>
                        <div className="text-xs font-medium text-gray-700 mb-2">Status filter</div>
                        <div className="space-y-1.5">
                            {STATUS_PRESETS.map(s => (
                                <label key={s.value} className="flex items-start gap-2 cursor-pointer">
                                    <input type="checkbox" checked={statuses.includes(s.value)}
                                        onChange={() => toggleStatus(s.value)}
                                        disabled={documentIdsRaw.trim().length > 0}
                                        className="mt-0.5" />
                                    <div className="flex-1">
                                        <div className="text-sm font-medium text-gray-900">{s.label}
                                            <span className="ml-2 text-[10px] font-mono text-gray-400">{s.value}</span>
                                        </div>
                                        <div className="text-xs text-gray-500">{s.description}</div>
                                    </div>
                                </label>
                            ))}
                        </div>
                        {documentIdsRaw.trim().length > 0 && (
                            <p className="mt-2 text-xs text-gray-400">
                                Status filter is ignored when explicit document IDs are provided.
                            </p>
                        )}
                    </div>

                    {/* Explicit IDs override */}
                    <div>
                        <div className="text-xs font-medium text-gray-700 mb-1">
                            Explicit document IDs <span className="text-gray-400 font-normal">(optional, overrides status filter)</span>
                        </div>
                        <textarea
                            value={documentIdsRaw}
                            onChange={e => setDocumentIdsRaw(e.target.value)}
                            placeholder="Comma- or newline-separated document IDs"
                            rows={3}
                            className="w-full px-3 py-2 text-sm font-mono border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500" />
                    </div>

                    {/* Hard cap */}
                    <div className="flex items-center gap-2">
                        <label className="text-sm text-gray-700">
                            Hard cap:
                            <input type="number" min={1} max={5000} value={hardCap}
                                onChange={e => setHardCap(Math.max(1, Math.min(5000, parseInt(e.target.value, 10) || DEFAULT_HARD_CAP)))}
                                className="ml-2 w-24 px-2 py-1 border border-gray-300 rounded text-sm" />
                        </label>
                        <span className="text-xs text-gray-400">Max documents to touch (1–5000).</span>
                    </div>

                    {/* Actions */}
                    <div className="flex items-center gap-2">
                        <button onClick={() => callApi(true)} disabled={loading != null}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-gray-100 hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed rounded text-sm font-medium text-gray-800 transition-colors">
                            {loading === "preview"
                                ? <Loader2 className="size-4 animate-spin" />
                                : <Eye className="size-4" />}
                            Preview
                        </button>
                        <button
                            onClick={() => {
                                if (!result) {
                                    toast.warning("Preview first to see matched count + cost estimate");
                                    return;
                                }
                                if (!confirm(`Reclassify ${result.matched} documents? Estimated cost: $${result.estimate.estimatedTotalCostUsd.toFixed(4)}`)) return;
                                callApi(false);
                            }}
                            disabled={loading != null}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed rounded text-sm font-medium text-white transition-colors">
                            {loading === "execute"
                                ? <Loader2 className="size-4 animate-spin" />
                                : <Play className="size-4" />}
                            Execute
                        </button>
                        {result && (
                            <button onClick={() => setResult(null)}
                                className="flex items-center gap-1 px-2 py-1 text-xs text-gray-500 hover:text-gray-700">
                                <RefreshCw className="size-3" /> Clear
                            </button>
                        )}
                    </div>

                    {result && <ResultPanel result={result} />}
                </div>
            )}
        </div>
    );
}

function ResultPanel({ result }: { result: ReclassifyResult }) {
    const e = result.estimate;
    const hasCostSample = e.sampleSize > 0;
    return (
        <div className="bg-gray-50 rounded border border-gray-200 p-3 space-y-3">
            <div className="flex items-center gap-3 flex-wrap text-sm">
                <span className={`px-2 py-0.5 rounded text-xs font-medium ${result.dryRun ? "bg-blue-100 text-blue-800" : "bg-green-100 text-green-800"}`}>
                    {result.dryRun ? "DRY RUN" : "EXECUTED"}
                </span>
                <span className="text-gray-700">{result.matched} matched</span>
                {!result.dryRun && (
                    <>
                        <span className="text-gray-500">·</span>
                        <span className="text-gray-700">{result.queued} queued</span>
                        {result.skipped > 0 && (
                            <>
                                <span className="text-gray-500">·</span>
                                <span className="text-amber-700">{result.skipped} skipped</span>
                            </>
                        )}
                    </>
                )}
            </div>

            {/* Cost estimate */}
            <div className="bg-white border border-gray-200 rounded p-3 space-y-1.5">
                <div className="flex items-center gap-2 mb-1">
                    <DollarSign className="size-4 text-green-600" />
                    <span className="text-xs font-medium text-gray-700">Cost estimate</span>
                    {!hasCostSample && (
                        <span className="ml-2 text-[10px] text-gray-400">
                            (no recent classification samples — estimate unavailable)
                        </span>
                    )}
                </div>
                {hasCostSample && (
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs">
                        <Stat label="Total estimated cost" value={`$${e.estimatedTotalCostUsd.toFixed(4)}`} highlight />
                        <Stat label="Per document" value={`$${e.averageCostPerDocumentUsd.toFixed(4)}`} />
                        <Stat label="Total input tokens" value={Math.round(e.estimatedTotalInputTokens).toLocaleString()} />
                        <Stat label="Total output tokens" value={Math.round(e.estimatedTotalOutputTokens).toLocaleString()} />
                    </div>
                )}
                {hasCostSample && (
                    <div className="text-[10px] text-gray-400 pt-1">
                        Based on the mean of the {e.sampleSize} most-recent CLASSIFY usage logs.
                    </div>
                )}
            </div>

            {result.errors.length > 0 && (
                <details className="text-xs">
                    <summary className="cursor-pointer text-red-700 font-medium">
                        Errors ({result.errors.length})
                    </summary>
                    <ul className="mt-2 space-y-1 ml-4 list-disc text-red-600 max-h-40 overflow-auto">
                        {result.errors.map((err, i) => <li key={i} className="font-mono">{err}</li>)}
                    </ul>
                </details>
            )}
        </div>
    );
}

function Stat({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
    return (
        <div>
            <div className="text-[10px] uppercase tracking-wide text-gray-500">{label}</div>
            <div className={`font-semibold tabular-nums ${highlight ? "text-green-700 text-base" : "text-gray-900"}`}>
                {value}
            </div>
        </div>
    );
}
