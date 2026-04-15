"use client";

import { useEffect, useState } from "react";
import { Eye, Download, ShieldAlert, Clock, User, Bot, RefreshCw } from "lucide-react";
import api from "@/lib/axios/axios.client";

type AuditEvent = {
    id: string;
    documentId: string;
    action: string;
    performedBy: string;
    performedByType: string;
    details: Record<string, string> | null;
    timestamp: string;
};

const ACTION_CONFIG: Record<string, { label: string; icon: typeof Eye; color: string; bgColor: string }> = {
    DOCUMENT_VIEWED: { label: "Viewed", icon: Eye, color: "text-blue-600", bgColor: "bg-blue-50" },
    DOCUMENT_DOWNLOADED: { label: "Downloaded", icon: Download, color: "text-green-600", bgColor: "bg-green-50" },
    ACCESS_DENIED: { label: "Access Denied", icon: ShieldAlert, color: "text-red-600", bgColor: "bg-red-50" },
    DOCUMENT_UPLOADED: { label: "Uploaded", icon: Clock, color: "text-gray-600", bgColor: "bg-gray-50" },
    STATUS_CHANGED: { label: "Status Changed", icon: RefreshCw, color: "text-indigo-600", bgColor: "bg-indigo-50" },
    CLASSIFICATION_APPROVED: { label: "Classification Approved", icon: Eye, color: "text-green-600", bgColor: "bg-green-50" },
    CLASSIFICATION_OVERRIDDEN: { label: "Classification Overridden", icon: ShieldAlert, color: "text-amber-600", bgColor: "bg-amber-50" },
    GOVERNANCE_APPLIED: { label: "Governance Applied", icon: RefreshCw, color: "text-purple-600", bgColor: "bg-purple-50" },
    STORAGE_TIER_MIGRATED: { label: "Storage Migrated", icon: RefreshCw, color: "text-cyan-600", bgColor: "bg-cyan-50" },
    PII_SENSITIVITY_ESCALATED: { label: "PII Escalation", icon: ShieldAlert, color: "text-red-600", bgColor: "bg-red-50" },
    PII_REPORTED: { label: "PII Reported", icon: ShieldAlert, color: "text-orange-600", bgColor: "bg-orange-50" },
    PROCESSING_FAILED: { label: "Processing Failed", icon: ShieldAlert, color: "text-red-600", bgColor: "bg-red-50" },
    DOCUMENT_FILED: { label: "Filed", icon: Clock, color: "text-gray-600", bgColor: "bg-gray-50" },
    DOCUMENT_DELETED: { label: "Deleted", icon: ShieldAlert, color: "text-red-600", bgColor: "bg-red-50" },
};

// Events to show — views, downloads, access denials, and security-relevant actions
const VISIBLE_ACTIONS = new Set([
    "DOCUMENT_VIEWED",
    "DOCUMENT_DOWNLOADED",
    "ACCESS_DENIED",
    "CLASSIFICATION_OVERRIDDEN",
    "PII_SENSITIVITY_ESCALATED",
    "PII_REPORTED",
    "DOCUMENT_DELETED",
]);

function formatTimestamp(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleDateString(undefined, { day: "2-digit", month: "short", year: "numeric" })
        + " " + d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

export default function DocumentAuditTrail({ documentId }: { documentId: string }) {
    const [events, setEvents] = useState<AuditEvent[]>([]);
    const [loading, setLoading] = useState(true);
    const [filter, setFilter] = useState<"relevant" | "all">("relevant");

    useEffect(() => {
        setLoading(true);
        api.get(`/documents/${documentId}/audit`, { params: { size: 200, sort: "timestamp,desc" } })
            .then(({ data }) => {
                const content = data.content ?? data;
                setEvents(Array.isArray(content) ? content : []);
            })
            .catch(() => setEvents([]))
            .finally(() => setLoading(false));
    }, [documentId]);

    const filtered = filter === "relevant"
        ? events.filter(e => VISIBLE_ACTIONS.has(e.action))
        : events;

    if (loading) {
        return (
            <div className="flex items-center justify-center h-full text-gray-400">
                <RefreshCw className="size-5 animate-spin mr-2" /> Loading audit trail...
            </div>
        );
    }

    return (
        <div className="h-full flex flex-col">
            <div className="flex items-center justify-between px-4 py-2 border-b border-gray-200 bg-white shrink-0">
                <span className="text-xs text-gray-500">{filtered.length} event{filtered.length !== 1 ? "s" : ""}</span>
                <div className="flex border border-gray-200 rounded-md overflow-hidden">
                    <button onClick={() => setFilter("relevant")}
                        className={`px-2 py-0.5 text-[10px] font-medium ${filter === "relevant" ? "bg-gray-800 text-white" : "bg-white text-gray-500 hover:bg-gray-50"}`}>
                        Views &amp; Security
                    </button>
                    <button onClick={() => setFilter("all")}
                        className={`px-2 py-0.5 text-[10px] font-medium border-l border-gray-200 ${filter === "all" ? "bg-gray-800 text-white" : "bg-white text-gray-500 hover:bg-gray-50"}`}>
                        All Events
                    </button>
                </div>
            </div>

            <div className="flex-1 overflow-y-auto">
                {filtered.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-full text-gray-400">
                        <Eye className="size-8 mb-2 opacity-40" />
                        <p className="text-sm">No audit events yet</p>
                        <p className="text-xs mt-1">Views and security events will appear here</p>
                    </div>
                ) : (
                    <div className="relative">
                        {/* Timeline line */}
                        <div className="absolute left-6 top-0 bottom-0 w-px bg-gray-200" />

                        {filtered.map((event, i) => {
                            const config = ACTION_CONFIG[event.action] ?? {
                                label: event.action.replace(/_/g, " "),
                                icon: Clock,
                                color: "text-gray-500",
                                bgColor: "bg-gray-50",
                            };
                            const Icon = config.icon;
                            const isSecurityEvent = event.action === "ACCESS_DENIED";

                            return (
                                <div key={event.id ?? i}
                                    className={`relative flex gap-3 px-4 py-3 ${isSecurityEvent ? "bg-red-50/50" : i % 2 === 0 ? "bg-white" : "bg-gray-50/30"}`}>
                                    {/* Timeline dot */}
                                    <div className={`relative z-10 flex items-center justify-center size-5 rounded-full shrink-0 mt-0.5 ${config.bgColor}`}>
                                        <Icon className={`size-3 ${config.color}`} />
                                    </div>

                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-center gap-2">
                                            <span className={`text-xs font-semibold ${config.color}`}>
                                                {config.label}
                                            </span>
                                            {isSecurityEvent && (
                                                <span className="px-1.5 py-0.5 text-[9px] font-bold bg-red-100 text-red-700 rounded">
                                                    SECURITY
                                                </span>
                                            )}
                                        </div>

                                        <div className="flex items-center gap-1.5 mt-0.5">
                                            {event.performedByType === "USER" ? (
                                                <User className="size-3 text-gray-400" />
                                            ) : (
                                                <Bot className="size-3 text-gray-400" />
                                            )}
                                            <span className="text-[11px] text-gray-600">{event.performedBy}</span>
                                            <span className="text-[10px] text-gray-400 ml-auto shrink-0">{formatTimestamp(event.timestamp)}</span>
                                        </div>

                                        {event.details && Object.keys(event.details).length > 0 && (
                                            <div className="mt-1.5 flex flex-wrap gap-1">
                                                {Object.entries(event.details)
                                                    .filter(([, v]) => v && v.length > 0)
                                                    .map(([k, v]) => (
                                                        <span key={k} className="inline-flex items-center gap-1 px-1.5 py-0.5 text-[10px] bg-gray-100 text-gray-600 rounded">
                                                            <span className="font-medium text-gray-500">{k.replace(/([A-Z])/g, " $1").trim()}:</span>
                                                            {v}
                                                        </span>
                                                    ))}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
}
