"use client";

import { useCallback, useEffect, useState } from "react";
import {
    ScrollText, Search, Filter, CheckCircle, XCircle,
    ChevronDown, ChevronRight, RefreshCw, Loader2, AlertTriangle,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

type AuditEvent = {
    id: string;
    timestamp: string;
    userId?: string;
    userEmail?: string;
    action: string;
    resourceType?: string;
    resourceId?: string;
    httpMethod?: string;
    endpoint?: string;
    requestSummary?: string;
    responseStatus: number;
    success: boolean;
    errorMessage?: string;
    ipAddress?: string;
    userAgent?: string;
};

type PageResponse = {
    content: AuditEvent[];
    totalElements: number;
    totalPages: number;
    number: number;
};

export default function AuditLogPage() {
    const [events, setEvents] = useState<PageResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [expandedId, setExpandedId] = useState<string | null>(null);

    // Filters
    const [userFilter, setUserFilter] = useState("");
    const [actionFilter, setActionFilter] = useState("");
    const [successFilter, setSuccessFilter] = useState<string>("");
    const [stats, setStats] = useState<{ totalEvents: number; errorsLast24h: number } | null>(null);

    const loadEvents = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            params.set("page", String(page));
            params.set("size", "30");
            if (userFilter) params.set("user", userFilter);
            if (actionFilter) params.set("action", actionFilter);
            if (successFilter !== "") params.set("success", successFilter);

            const { data } = await api.get(`/admin/audit?${params}`);
            setEvents(data);
        } catch { toast.error("Failed to load audit log"); }
        finally { setLoading(false); }
    }, [page, userFilter, actionFilter, successFilter]);

    useEffect(() => { loadEvents(); }, [loadEvents]);

    useEffect(() => {
        api.get("/admin/audit/stats").then(({ data }) => setStats(data)).catch(() => {});
    }, []);

    return (
        <>
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">Audit Log</h2>
                    <p className="text-sm text-gray-500 mt-1">
                        {stats ? `${stats.totalEvents.toLocaleString()} events recorded` : "Loading..."}
                        {stats && stats.errorsLast24h > 0 && (
                            <span className="ml-2 text-red-600 font-medium">
                                {stats.errorsLast24h} error{stats.errorsLast24h !== 1 ? "s" : ""} in last 24h
                            </span>
                        )}
                    </p>
                </div>
                <button onClick={loadEvents} disabled={loading}
                    className="inline-flex items-center gap-2 px-3 py-2 border border-gray-300 text-sm text-gray-700 rounded-lg hover:bg-gray-50 disabled:opacity-50">
                    <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} /> Refresh
                </button>
            </div>

            {/* Filters */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 mb-4">
                <div className="flex items-center gap-3 flex-wrap">
                    <div className="relative flex-1 min-w-48">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-gray-400" />
                        <input type="text" value={userFilter} onChange={e => { setUserFilter(e.target.value); setPage(0); }}
                            placeholder="Filter by user email..."
                            className="w-full pl-9 pr-3 py-2 text-sm border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500" />
                    </div>
                    <input type="text" value={actionFilter} onChange={e => { setActionFilter(e.target.value); setPage(0); }}
                        placeholder="Filter by action..."
                        className="text-sm border border-gray-300 rounded-md px-3 py-2 w-48" />
                    <select value={successFilter} onChange={e => { setSuccessFilter(e.target.value); setPage(0); }}
                        className="text-sm border border-gray-300 rounded-md px-3 py-2">
                        <option value="">All results</option>
                        <option value="true">Success only</option>
                        <option value="false">Errors only</option>
                    </select>
                </div>
            </div>

            {/* Event list */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                {loading && !events ? (
                    <div className="p-8 text-center"><Loader2 className="size-6 animate-spin text-gray-300 mx-auto" /></div>
                ) : events?.content.length === 0 ? (
                    <div className="p-8 text-center text-gray-400">
                        <ScrollText className="size-8 mx-auto mb-3 text-gray-200" />
                        No audit events found
                    </div>
                ) : (
                    <div className="divide-y divide-gray-100">
                        {events?.content.map(event => (
                            <div key={event.id}>
                                <button onClick={() => setExpandedId(expandedId === event.id ? null : event.id)}
                                    className={`w-full text-left px-4 py-3 hover:bg-gray-50 ${!event.success ? "bg-red-50/50" : ""}`}>
                                    <div className="flex items-center gap-3">
                                        {event.success ? (
                                            <CheckCircle className="size-4 text-green-500 shrink-0" />
                                        ) : (
                                            <XCircle className="size-4 text-red-500 shrink-0" />
                                        )}
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2 flex-wrap">
                                                <span className="text-xs font-mono font-medium text-gray-900">{event.action}</span>
                                                {event.resourceType && (
                                                    <span className="px-1.5 py-0.5 text-[10px] bg-blue-50 text-blue-700 rounded">{event.resourceType}</span>
                                                )}
                                                {event.httpMethod && (
                                                    <span className={`px-1.5 py-0.5 text-[10px] font-medium rounded ${
                                                        event.httpMethod === "DELETE" ? "bg-red-100 text-red-700" :
                                                        event.httpMethod === "POST" ? "bg-green-100 text-green-700" :
                                                        event.httpMethod === "PUT" ? "bg-amber-100 text-amber-700" :
                                                        "bg-gray-100 text-gray-600"
                                                    }`}>{event.httpMethod}</span>
                                                )}
                                                {!event.success && event.errorMessage && (
                                                    <span className="text-[10px] text-red-600 truncate max-w-64">{event.errorMessage}</span>
                                                )}
                                            </div>
                                            <div className="text-[10px] text-gray-400 mt-0.5">
                                                {event.userEmail ?? "system"} &middot; {new Date(event.timestamp).toLocaleString()}
                                                {event.endpoint && <span className="ml-2 font-mono">{event.endpoint}</span>}
                                            </div>
                                        </div>
                                        {expandedId === event.id ? <ChevronDown className="size-4 text-gray-400" /> : <ChevronRight className="size-4 text-gray-400" />}
                                    </div>
                                </button>

                                {expandedId === event.id && (
                                    <div className="px-4 pb-3 pt-1 bg-gray-50 border-t border-gray-100 space-y-2 text-xs">
                                        <div className="grid grid-cols-2 gap-x-4 gap-y-1">
                                            <Detail label="User" value={event.userEmail} />
                                            <Detail label="User ID" value={event.userId} />
                                            <Detail label="Action" value={event.action} />
                                            <Detail label="Resource Type" value={event.resourceType} />
                                            <Detail label="Resource ID" value={event.resourceId} />
                                            <Detail label="HTTP Method" value={event.httpMethod} />
                                            <Detail label="Endpoint" value={event.endpoint} />
                                            <Detail label="Status Code" value={String(event.responseStatus)} />
                                            <Detail label="IP Address" value={event.ipAddress} />
                                            <Detail label="Timestamp" value={new Date(event.timestamp).toISOString()} />
                                        </div>
                                        {event.requestSummary && (
                                            <div>
                                                <span className="text-gray-500 font-medium">Request:</span>
                                                <pre className="mt-0.5 text-[10px] text-gray-600 bg-white rounded p-2 border overflow-x-auto">{event.requestSummary}</pre>
                                            </div>
                                        )}
                                        {event.errorMessage && (
                                            <div>
                                                <span className="text-red-600 font-medium">Error:</span>
                                                <pre className="mt-0.5 text-[10px] text-red-700 bg-red-50 rounded p-2 border border-red-100 overflow-x-auto">{event.errorMessage}</pre>
                                            </div>
                                        )}
                                        {event.userAgent && (
                                            <div className="text-[10px] text-gray-400 truncate">UA: {event.userAgent}</div>
                                        )}
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                )}

                {/* Pagination */}
                {events && events.totalPages > 1 && (
                    <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200 bg-gray-50">
                        <span className="text-xs text-gray-500">
                            Page {events.number + 1} of {events.totalPages} ({events.totalElements} total)
                        </span>
                        <div className="flex gap-1">
                            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={events.number === 0}
                                className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50">Prev</button>
                            <button onClick={() => setPage(p => p + 1)} disabled={events.number >= events.totalPages - 1}
                                className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50">Next</button>
                        </div>
                    </div>
                )}
            </div>
        </>
    );
}

function Detail({ label, value }: { label: string; value?: string }) {
    if (!value) return null;
    return (
        <div>
            <span className="text-gray-500">{label}:</span>{" "}
            <span className="text-gray-900 font-mono">{value}</span>
        </div>
    );
}
