"use client";

import { useCallback, useEffect, useState } from "react";
import {
    ScrollText, Search, RefreshCw, Loader2, ChevronDown, ChevronRight,
    CheckCircle, XCircle, AlertTriangle, Filter, Link2, ShieldCheck, ShieldAlert,
    Download,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

/**
 * Phase 3 — Audit Explorer (Tier 2).
 *
 * Reads `GET /api/admin/audit-events/v2` (proxy in front of
 * `gls-audit-collector`'s `/v1/events`). Surfaces the v2 envelope
 * stream — pipeline events, classifier decisions, governance
 * actions — that the legacy `/admin/audit` page doesn't show
 * (that one queries the per-service request audit log).
 *
 * Single-event lookup at `/v2/{eventId}` covers Tier 1 too, so
 * pasting a hash-chain event id from elsewhere resolves regardless
 * of tier.
 */

type AuditEvent = {
    eventId: string;
    eventType: string;
    tier?: "DOMAIN" | "SYSTEM";
    schemaVersion?: string;
    timestamp?: string;
    actor?: Record<string, unknown>;
    action?: string;
    outcome?: "SUCCESS" | "FAILURE" | "PARTIAL";
    documentId?: string;
    pipelineRunId?: string;
    nodeRunId?: string;
    traceparent?: string;
    resource?: Record<string, unknown>;
    details?: Record<string, unknown>;
    retentionClass?: string;
    previousEventHash?: string;
};

type EventListResponse = {
    events: AuditEvent[];
    nextPageToken?: string | null;
};

type ResourceType =
    | "DOCUMENT" | "BLOCK" | "USER" | "PIPELINE_RUN"
    | "POLICY" | "CATEGORY" | "RETENTION_SCHEDULE";

type ChainVerifyResponse = {
    resourceType: ResourceType;
    resourceId: string;
    status: "OK" | "BROKEN";
    eventsTraversed: number;
    firstEventId?: string;
    lastEventId?: string;
    brokenAtEventId?: string;
    expectedPreviousHash?: string;
    computedPreviousHash?: string;
};

const RESOURCE_TYPES: ResourceType[] = [
    "DOCUMENT", "BLOCK", "USER", "PIPELINE_RUN", "POLICY", "CATEGORY", "RETENTION_SCHEDULE",
];

const DEFAULT_PAGE_SIZE = 50;

export default function AuditExplorerPage() {
    const [documentId, setDocumentId] = useState("");
    const [eventType, setEventType] = useState("");
    const [actorService, setActorService] = useState("");
    const [from, setFrom] = useState("");
    const [to, setTo] = useState("");
    const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

    const [data, setData] = useState<EventListResponse | null>(null);
    const [pageStack, setPageStack] = useState<(string | null)[]>([null]);
    const [loading, setLoading] = useState(false);
    const [expandedId, setExpandedId] = useState<string | null>(null);

    const [singleEventId, setSingleEventId] = useState("");
    const [singleEvent, setSingleEvent] = useState<AuditEvent | null>(null);
    const [singleLoading, setSingleLoading] = useState(false);

    const [verifyResourceType, setVerifyResourceType] = useState<ResourceType>("DOCUMENT");
    const [verifyResourceId, setVerifyResourceId] = useState("");
    const [verifyResult, setVerifyResult] = useState<ChainVerifyResponse | null>(null);
    const [verifyLoading, setVerifyLoading] = useState(false);

    const fetchPage = useCallback(async (token: string | null) => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            if (documentId.trim()) params.set("documentId", documentId.trim());
            if (eventType.trim()) params.set("eventType", eventType.trim());
            if (actorService.trim()) params.set("actorService", actorService.trim());
            if (from) params.set("from", new Date(from).toISOString());
            if (to) params.set("to", new Date(to).toISOString());
            if (token) params.set("pageToken", token);
            params.set("pageSize", String(pageSize));
            const { data } = await api.get<EventListResponse>(`/admin/audit-events/v2?${params}`);
            setData(data);
        } catch {
            toast.error("Failed to load audit events");
            setData({ events: [] });
        } finally {
            setLoading(false);
        }
    }, [documentId, eventType, actorService, from, to, pageSize]);

    useEffect(() => { fetchPage(null); /* initial load */ }, [fetchPage]);

    const onSearch = () => {
        setPageStack([null]);
        fetchPage(null);
    };

    /**
     * Trigger a CSV download via the proxy endpoint. We build the
     * same filter query string as the search, then let the browser
     * follow the URL — the backend sets Content-Disposition so the
     * download just works without us touching `Blob` here.
     */
    const onExportCsv = () => {
        const params = new URLSearchParams();
        if (documentId.trim()) params.set("documentId", documentId.trim());
        if (eventType.trim()) params.set("eventType", eventType.trim());
        if (actorService.trim()) params.set("actorService", actorService.trim());
        if (from) params.set("from", new Date(from).toISOString());
        if (to) params.set("to", new Date(to).toISOString());
        const url = `${api.defaults.baseURL ?? ""}/admin/audit-events/v2/export.csv?${params}`;
        window.location.href = url;
        toast.info("CSV download starting…");
    };

    const onNextPage = () => {
        if (!data?.nextPageToken) return;
        setPageStack(prev => [...prev, data.nextPageToken!]);
        fetchPage(data.nextPageToken);
    };

    const onPrevPage = () => {
        if (pageStack.length <= 1) return;
        const next = [...pageStack];
        next.pop();
        setPageStack(next);
        fetchPage(next[next.length - 1] ?? null);
    };

    const onLookup = async () => {
        const id = singleEventId.trim();
        if (!id) return;
        setSingleLoading(true);
        try {
            const { data } = await api.get<AuditEvent>(`/admin/audit-events/v2/${encodeURIComponent(id)}`);
            setSingleEvent(data);
        } catch (err: unknown) {
            const e = err as { response?: { status?: number } };
            if (e?.response?.status === 404) toast.error(`Event not found: ${id}`);
            else toast.error("Lookup failed");
            setSingleEvent(null);
        } finally {
            setSingleLoading(false);
        }
    };

    const onVerifyChain = async () => {
        const id = verifyResourceId.trim();
        if (!id) return;
        setVerifyLoading(true);
        try {
            const { data } = await api.get<ChainVerifyResponse>(
                `/admin/audit-events/v2/chains/${encodeURIComponent(verifyResourceType)}/${encodeURIComponent(id)}/verify`);
            setVerifyResult(data);
            if (data.status === "OK") {
                toast.success(`Chain OK — ${data.eventsTraversed} event(s) verified`);
            } else {
                toast.error(`Chain BROKEN at ${data.brokenAtEventId ?? "?"}`);
            }
        } catch (err: unknown) {
            const e = err as { response?: { status?: number } };
            if (e?.response?.status === 404) {
                toast.error(`No Tier 1 events for ${verifyResourceType}:${id}`);
            } else {
                toast.error("Chain verification failed");
            }
            setVerifyResult(null);
        } finally {
            setVerifyLoading(false);
        }
    };

    return (
        <>
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">Audit Explorer</h2>
                    <p className="text-sm text-gray-500 mt-1">
                        Query Tier 1 / Tier 2 envelope events from the audit collector.
                    </p>
                </div>
                <button
                    onClick={() => fetchPage(pageStack[pageStack.length - 1] ?? null)}
                    disabled={loading}
                    className="inline-flex items-center gap-2 px-3 py-2 border border-gray-300 text-sm text-gray-700 rounded-lg hover:bg-gray-50 disabled:opacity-50">
                    <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} /> Refresh
                </button>
            </div>

            {/* Single-event lookup */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 mb-4">
                <div className="text-xs font-medium text-gray-700 mb-2">Single event lookup (Tier 1 + Tier 2)</div>
                <div className="flex items-center gap-2">
                    <input type="text" value={singleEventId}
                        onChange={e => setSingleEventId(e.target.value)}
                        onKeyDown={e => { if (e.key === "Enter") onLookup(); }}
                        placeholder="ULID — e.g. 01HK3X8Y7M1ABCDE0123456789"
                        className="flex-1 px-3 py-2 text-sm border border-gray-300 rounded-md font-mono focus:ring-2 focus:ring-blue-500" />
                    <button onClick={onLookup} disabled={singleLoading || !singleEventId.trim()}
                        className="inline-flex items-center gap-2 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-md hover:bg-blue-700 disabled:opacity-50">
                        {singleLoading ? <Loader2 className="size-4 animate-spin" /> : <Search className="size-4" />}
                        Lookup
                    </button>
                </div>
                {singleEvent && (
                    <div className="mt-3 border border-gray-200 rounded-md bg-gray-50 p-3">
                        <EventSummary event={singleEvent} />
                        <pre className="mt-2 text-[10px] text-gray-700 bg-white border border-gray-200 rounded p-2 overflow-x-auto max-h-64">
                            {JSON.stringify(singleEvent, null, 2)}
                        </pre>
                    </div>
                )}
            </div>

            {/* Tier 1 hash-chain verification */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 mb-4">
                <div className="flex items-center gap-2 mb-2">
                    <Link2 className="size-3 text-gray-500" />
                    <span className="text-xs font-medium text-gray-700">Tier 1 hash-chain verification</span>
                    <span className="text-[10px] text-gray-400">
                        Walks the per-resource chain, recomputes hashes, reports OK or BROKEN.
                    </span>
                </div>
                <div className="flex items-center gap-2 flex-wrap">
                    <select value={verifyResourceType}
                        onChange={e => setVerifyResourceType(e.target.value as ResourceType)}
                        className="px-3 py-2 text-sm border border-gray-300 rounded-md font-mono">
                        {RESOURCE_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                    </select>
                    <input type="text" value={verifyResourceId}
                        onChange={e => setVerifyResourceId(e.target.value)}
                        onKeyDown={e => { if (e.key === "Enter") onVerifyChain(); }}
                        placeholder="Resource ID (e.g. document slug or _id)"
                        className="flex-1 min-w-48 px-3 py-2 text-sm border border-gray-300 rounded-md font-mono" />
                    <button onClick={onVerifyChain} disabled={verifyLoading || !verifyResourceId.trim()}
                        className="inline-flex items-center gap-2 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-md hover:bg-blue-700 disabled:opacity-50">
                        {verifyLoading ? <Loader2 className="size-4 animate-spin" /> : <ShieldCheck className="size-4" />}
                        Verify
                    </button>
                </div>
                {verifyResult && (
                    <div className={`mt-3 rounded-md p-3 border ${
                        verifyResult.status === "OK"
                            ? "bg-green-50 border-green-200"
                            : "bg-red-50 border-red-200"
                    }`}>
                        <div className="flex items-center gap-2 mb-2">
                            {verifyResult.status === "OK" ? (
                                <ShieldCheck className="size-4 text-green-600" />
                            ) : (
                                <ShieldAlert className="size-4 text-red-600" />
                            )}
                            <span className={`text-sm font-semibold ${
                                verifyResult.status === "OK" ? "text-green-800" : "text-red-800"
                            }`}>
                                Chain {verifyResult.status}
                            </span>
                            <span className="text-xs text-gray-600">
                                — {verifyResult.eventsTraversed} event(s) traversed
                            </span>
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-4 gap-y-1 text-xs">
                            <Detail label="Resource" value={`${verifyResult.resourceType} / ${verifyResult.resourceId}`} />
                            {verifyResult.firstEventId && (
                                <Detail label="First event" value={verifyResult.firstEventId} />
                            )}
                            {verifyResult.lastEventId && (
                                <Detail label="Last event" value={verifyResult.lastEventId} />
                            )}
                            {verifyResult.brokenAtEventId && (
                                <Detail label="Broken at" value={verifyResult.brokenAtEventId} highlight />
                            )}
                            {verifyResult.expectedPreviousHash && (
                                <Detail label="Expected prev hash" value={verifyResult.expectedPreviousHash} />
                            )}
                            {verifyResult.computedPreviousHash && (
                                <Detail label="Computed prev hash" value={verifyResult.computedPreviousHash} />
                            )}
                        </div>
                    </div>
                )}
            </div>

            {/* Filters */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 mb-4">
                <div className="flex items-center gap-2 mb-3 text-xs font-medium text-gray-700">
                    <Filter className="size-3" /> Tier 2 search filters
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                    <Field label="Document ID">
                        <input type="text" value={documentId} onChange={e => setDocumentId(e.target.value)}
                            placeholder="any" className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md font-mono" />
                    </Field>
                    <Field label="Event type">
                        <input type="text" value={eventType} onChange={e => setEventType(e.target.value)}
                            placeholder="e.g. DocumentClassifiedV1"
                            className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md font-mono" />
                    </Field>
                    <Field label="Actor service">
                        <input type="text" value={actorService} onChange={e => setActorService(e.target.value)}
                            placeholder="e.g. router"
                            className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md font-mono" />
                    </Field>
                    <Field label="From">
                        <input type="datetime-local" value={from} onChange={e => setFrom(e.target.value)}
                            className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md" />
                    </Field>
                    <Field label="To">
                        <input type="datetime-local" value={to} onChange={e => setTo(e.target.value)}
                            className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md" />
                    </Field>
                    <Field label="Page size">
                        <select value={pageSize} onChange={e => setPageSize(parseInt(e.target.value, 10))}
                            className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md">
                            <option value={25}>25</option>
                            <option value={50}>50</option>
                            <option value={100}>100</option>
                            <option value={250}>250</option>
                            <option value={500}>500</option>
                        </select>
                    </Field>
                </div>
                <div className="mt-3 flex items-center justify-end gap-2">
                    <button onClick={onExportCsv} disabled={loading}
                        title="Download CSV of all events matching the current filters"
                        className="inline-flex items-center gap-2 px-3 py-2 border border-gray-300 text-gray-700 text-sm font-medium rounded-md hover:bg-gray-50 disabled:opacity-50">
                        <Download className="size-4" />
                        Export CSV
                    </button>
                    <button onClick={onSearch} disabled={loading}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-md hover:bg-blue-700 disabled:opacity-50">
                        {loading ? <Loader2 className="size-4 animate-spin" /> : <Search className="size-4" />}
                        Search
                    </button>
                </div>
            </div>

            {/* Result list */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                {loading && !data ? (
                    <div className="p-8 text-center"><Loader2 className="size-6 animate-spin text-gray-300 mx-auto" /></div>
                ) : !data || data.events.length === 0 ? (
                    <div className="p-8 text-center text-gray-400">
                        <ScrollText className="size-8 mx-auto mb-3 text-gray-200" />
                        No matching events.
                    </div>
                ) : (
                    <div className="divide-y divide-gray-100">
                        {data.events.map(event => (
                            <div key={event.eventId}>
                                <button onClick={() => setExpandedId(expandedId === event.eventId ? null : event.eventId)}
                                    className="w-full text-left px-4 py-2.5 hover:bg-gray-50">
                                    <div className="flex items-center gap-3">
                                        <OutcomeIcon outcome={event.outcome} />
                                        <EventSummary event={event} compact />
                                        {expandedId === event.eventId
                                            ? <ChevronDown className="size-4 text-gray-400" />
                                            : <ChevronRight className="size-4 text-gray-400" />}
                                    </div>
                                </button>
                                {expandedId === event.eventId && (
                                    <div className="px-4 pb-3 pt-1 bg-gray-50 border-t border-gray-100">
                                        <pre className="text-[10px] text-gray-700 bg-white border border-gray-200 rounded p-2 overflow-x-auto max-h-96">
                                            {JSON.stringify(event, null, 2)}
                                        </pre>
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                )}

                {/* Pagination */}
                {data && (data.events.length > 0 || pageStack.length > 1) && (
                    <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200 bg-gray-50">
                        <span className="text-xs text-gray-500">
                            Page {pageStack.length} · {data.events.length} events shown
                        </span>
                        <div className="flex gap-1">
                            <button onClick={onPrevPage} disabled={pageStack.length <= 1 || loading}
                                className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50">
                                Prev
                            </button>
                            <button onClick={onNextPage} disabled={!data.nextPageToken || loading}
                                className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50">
                                Next
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </>
    );
}

function Detail({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
    return (
        <div className="min-w-0">
            <span className="text-gray-500">{label}: </span>
            <span className={`font-mono break-all ${highlight ? "text-red-700 font-semibold" : "text-gray-900"}`}>
                {value}
            </span>
        </div>
    );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
    return (
        <label className="block">
            <span className="text-[10px] uppercase tracking-wide text-gray-500">{label}</span>
            <div className="mt-1">{children}</div>
        </label>
    );
}

function OutcomeIcon({ outcome }: { outcome?: AuditEvent["outcome"] }) {
    if (outcome === "SUCCESS") return <CheckCircle className="size-4 text-green-500 shrink-0" />;
    if (outcome === "FAILURE") return <XCircle className="size-4 text-red-500 shrink-0" />;
    if (outcome === "PARTIAL") return <AlertTriangle className="size-4 text-amber-500 shrink-0" />;
    return <ScrollText className="size-4 text-gray-300 shrink-0" />;
}

function EventSummary({ event, compact = false }: { event: AuditEvent; compact?: boolean }) {
    const ts = event.timestamp ? new Date(event.timestamp).toLocaleString() : "—";
    const actorService = (event.actor as { service?: string } | undefined)?.service ?? "—";
    return (
        <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
                <span className="text-xs font-mono font-medium text-gray-900">{event.eventType}</span>
                {event.tier && (
                    <span className={`px-1.5 py-0.5 text-[10px] font-medium rounded ${
                        event.tier === "DOMAIN" ? "bg-purple-100 text-purple-700" : "bg-blue-100 text-blue-700"
                    }`}>{event.tier}</span>
                )}
                {event.action && (
                    <span className="text-[10px] text-gray-500 font-mono">{event.action}</span>
                )}
                {event.documentId && (
                    <span className="text-[10px] text-gray-400 font-mono truncate max-w-48" title={event.documentId}>
                        doc:{event.documentId}
                    </span>
                )}
            </div>
            <div className={`text-[10px] text-gray-400 mt-0.5 ${compact ? "" : "flex items-center gap-2 flex-wrap"}`}>
                <span>{ts}</span>
                <span className="ml-2">actor: <span className="font-mono">{actorService}</span></span>
                {event.pipelineRunId && (
                    <span className="ml-2 font-mono truncate max-w-32" title={event.pipelineRunId}>
                        run:{event.pipelineRunId.slice(0, 8)}…
                    </span>
                )}
                <span className="ml-2 font-mono text-gray-300 truncate max-w-32" title={event.eventId}>
                    {event.eventId.slice(0, 12)}…
                </span>
            </div>
        </div>
    );
}
