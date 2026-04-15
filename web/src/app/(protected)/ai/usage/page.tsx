"use client";

import { useCallback, useEffect, useState } from "react";
import {
    Brain, Clock, FileText, AlertTriangle, CheckCircle, XCircle,
    Loader2, ChevronDown, ChevronRight, Filter, User, Zap,
    MessageSquare, Eye, Copy,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

type UsageLog = {
    id: string;
    timestamp: string;
    usageType: string;
    triggeredBy: string;
    documentId?: string;
    documentName?: string;
    provider: string;
    model: string;
    pipelineId?: string;
    promptBlockId?: string;
    promptBlockVersion?: number;
    systemPrompt?: string;
    userPrompt?: string;
    toolCalls?: { toolName: string; input: string; output: string; durationMs: number }[];
    response?: string;
    reasoning?: string;
    result?: Record<string, unknown>;
    durationMs: number;
    inputTokens: number;
    outputTokens: number;
    estimatedCost: number;
    status: string;
    errorMessage?: string;
    outcome?: string;
    overriddenBy?: string;
    outcomeAt?: string;
};

type PageResult = { content: UsageLog[]; totalElements: number; totalPages: number; number: number };
type Stats = { total: number; last24h: number; last7d: number; classifications: number; schemaSuggestions: number; schemaTests: number; blockImprovements: number; failures: number };

const TYPE_LABELS: Record<string, { label: string; color: string; icon: typeof Brain }> = {
    CLASSIFY: { label: "Classification", color: "bg-indigo-100 text-indigo-700", icon: Brain },
    SUGGEST_SCHEMA: { label: "Schema Suggestion", color: "bg-purple-100 text-purple-700", icon: Zap },
    TEST_SCHEMA: { label: "Schema Test", color: "bg-blue-100 text-blue-700", icon: FileText },
    IMPROVE_BLOCK: { label: "Block Improvement", color: "bg-green-100 text-green-700", icon: MessageSquare },
    METADATA_EXTRACT: { label: "Metadata Extract", color: "bg-amber-100 text-amber-700", icon: FileText },
};

const STATUS_ICONS: Record<string, { icon: typeof CheckCircle; color: string }> = {
    SUCCESS: { icon: CheckCircle, color: "text-green-500" },
    FAILED: { icon: XCircle, color: "text-red-500" },
    NO_RESULT: { icon: AlertTriangle, color: "text-amber-500" },
};

export default function AiUsagePage() {
    const [results, setResults] = useState<PageResult | null>(null);
    const [stats, setStats] = useState<Stats | null>(null);
    const [loading, setLoading] = useState(false);
    const [page, setPage] = useState(0);
    const [typeFilter, setTypeFilter] = useState("");
    const [statusFilter, setStatusFilter] = useState("");
    const [expandedId, setExpandedId] = useState<string | null>(null);

    const loadStats = useCallback(async () => {
        try { const { data } = await api.get("/admin/ai/usage/stats"); setStats(data); } catch {}
    }, []);

    const loadUsage = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            params.set("page", String(page));
            params.set("size", "20");
            params.set("sort", "timestamp,desc");
            if (typeFilter) params.set("type", typeFilter);
            if (statusFilter) params.set("status", statusFilter);
            const { data } = await api.get(`/admin/ai/usage?${params}`);
            setResults(data);
        } catch { toast.error("Failed to load AI usage logs"); }
        finally { setLoading(false); }
    }, [page, typeFilter, statusFilter]);

    useEffect(() => { loadStats(); loadUsage(); }, [loadStats, loadUsage]);

    const copyToClipboard = (text: string) => {
        navigator.clipboard.writeText(text);
        toast.success("Copied to clipboard");
    };

    return (
        <>
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">AI Usage Log</h2>
                    <p className="text-sm text-gray-500 mt-1">Every AI interaction — prompts, reasoning, responses, and outcomes</p>
                </div>
            </div>

            {/* Stats */}
            {stats && (
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
                    <StatCard label="Total Calls" value={stats.total} />
                    <StatCard label="Last 24h" value={stats.last24h} />
                    <StatCard label="Classifications" value={stats.classifications} />
                    <StatCard label="Failures" value={stats.failures} warn={stats.failures > 0} />
                </div>
            )}

            {/* Filters */}
            <div className="flex gap-2 mb-4 flex-wrap">
                <button onClick={() => { setTypeFilter(""); setPage(0); }}
                    className={`px-3 py-1.5 text-xs font-medium rounded-md border ${!typeFilter ? "bg-blue-50 text-blue-700 border-blue-200" : "text-gray-600 border-gray-300 hover:bg-gray-50"}`}>
                    All Types
                </button>
                {Object.entries(TYPE_LABELS).map(([key, { label, color }]) => (
                    <button key={key} onClick={() => { setTypeFilter(typeFilter === key ? "" : key); setPage(0); }}
                        className={`px-3 py-1.5 text-xs font-medium rounded-md border ${typeFilter === key ? color + " border-current" : "text-gray-600 border-gray-300 hover:bg-gray-50"}`}>
                        {label}
                    </button>
                ))}
                <div className="border-l border-gray-200 mx-1" />
                <button onClick={() => { setStatusFilter(statusFilter === "FAILED" ? "" : "FAILED"); setPage(0); }}
                    className={`px-3 py-1.5 text-xs font-medium rounded-md border ${statusFilter === "FAILED" ? "bg-red-50 text-red-700 border-red-200" : "text-gray-600 border-gray-300 hover:bg-gray-50"}`}>
                    Failures Only
                </button>
            </div>

            {/* Results */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                {loading && !results ? (
                    <div className="p-12 text-center"><Loader2 className="size-8 animate-spin text-gray-300 mx-auto" /></div>
                ) : !results?.content.length ? (
                    <div className="p-12 text-center">
                        <Brain className="size-12 text-gray-200 mx-auto mb-4" />
                        <h3 className="text-sm font-semibold text-gray-700">No AI usage recorded yet</h3>
                        <p className="text-xs text-gray-400 mt-1">Usage logs appear when documents are classified or schemas are suggested.</p>
                    </div>
                ) : (
                    <div className="divide-y divide-gray-100">
                        {results.content.map(log => {
                            const typeDef = TYPE_LABELS[log.usageType] ?? { label: log.usageType, color: "bg-gray-100 text-gray-600", icon: Brain };
                            const statusDef = STATUS_ICONS[log.status] ?? STATUS_ICONS.SUCCESS;
                            const StatusIcon = statusDef.icon;
                            const isExpanded = expandedId === log.id;

                            return (
                                <div key={log.id}>
                                    <button onClick={() => setExpandedId(isExpanded ? null : log.id)}
                                        className="w-full text-left px-5 py-3 hover:bg-gray-50 transition-colors">
                                        <div className="flex items-center gap-3">
                                            {isExpanded ? <ChevronDown className="size-4 text-gray-400 shrink-0" /> : <ChevronRight className="size-4 text-gray-400 shrink-0" />}
                                            <StatusIcon className={`size-4 ${statusDef.color} shrink-0`} />
                                            <span className={`px-2 py-0.5 text-[10px] font-medium rounded ${typeDef.color} shrink-0`}>
                                                {typeDef.label}
                                            </span>
                                            <span className="text-sm text-gray-800 truncate flex-1">
                                                {log.documentName || log.documentId || "System"}
                                            </span>
                                            <span className="text-xs text-gray-400 shrink-0">{log.provider}/{log.model}</span>
                                            {log.durationMs > 0 && (
                                                <span className="text-xs text-gray-400 shrink-0 tabular-nums w-14 text-right">
                                                    {log.durationMs >= 1000 ? `${(log.durationMs / 1000).toFixed(1)}s` : `${log.durationMs}ms`}
                                                </span>
                                            )}
                                            <span className="text-[10px] text-gray-400 shrink-0 w-20 text-right">
                                                {new Date(log.timestamp).toLocaleTimeString()}
                                            </span>
                                        </div>
                                        {log.triggeredBy && (
                                            <div className="flex items-center gap-1 ml-8 mt-0.5">
                                                <User className="size-3 text-gray-300" />
                                                <span className="text-[10px] text-gray-400">{log.triggeredBy}</span>
                                                {log.errorMessage && <span className="text-[10px] text-red-500 ml-2">{log.errorMessage}</span>}
                                            </div>
                                        )}
                                    </button>

                                    {/* Expanded detail */}
                                    {isExpanded && (
                                        <div className="px-5 pb-4 bg-gray-50 border-t border-gray-100">
                                            <div className="grid grid-cols-2 gap-6 mt-3">
                                                {/* Left: Prompts */}
                                                <div className="space-y-3">
                                                    {log.systemPrompt && (
                                                        <PromptSection title="System Prompt" content={log.systemPrompt} onCopy={copyToClipboard} />
                                                    )}
                                                    {log.userPrompt && (
                                                        <PromptSection title="User Prompt" content={log.userPrompt} onCopy={copyToClipboard} />
                                                    )}
                                                    {log.toolCalls && log.toolCalls.length > 0 && (
                                                        <div>
                                                            <h4 className="text-xs font-semibold text-gray-700 mb-1">MCP Tool Calls ({log.toolCalls.length})</h4>
                                                            <div className="space-y-1">
                                                                {log.toolCalls.map((tc, i) => (
                                                                    <div key={i} className="bg-white rounded border border-gray-200 px-3 py-2 text-xs">
                                                                        <div className="flex items-center justify-between">
                                                                            <span className="font-semibold text-indigo-600">{tc.toolName}</span>
                                                                            {tc.durationMs > 0 && <span className="text-gray-400">{tc.durationMs}ms</span>}
                                                                        </div>
                                                                        {tc.input && <div className="text-gray-500 mt-0.5 truncate">In: {tc.input}</div>}
                                                                        {tc.output && <div className="text-gray-400 mt-0.5 truncate">Out: {tc.output.substring(0, 100)}</div>}
                                                                    </div>
                                                                ))}
                                                            </div>
                                                        </div>
                                                    )}
                                                </div>

                                                {/* Right: Response */}
                                                <div className="space-y-3">
                                                    {log.response && (
                                                        <PromptSection title="AI Response" content={log.response} onCopy={copyToClipboard} />
                                                    )}
                                                    {log.reasoning && (
                                                        <PromptSection title="Reasoning" content={log.reasoning} onCopy={copyToClipboard} />
                                                    )}
                                                    {log.result && Object.keys(log.result).length > 0 && (
                                                        <div>
                                                            <h4 className="text-xs font-semibold text-gray-700 mb-1">Structured Result</h4>
                                                            <pre className="bg-white rounded border border-gray-200 p-2 text-[10px] text-gray-600 overflow-x-auto max-h-40 overflow-y-auto">
                                                                {JSON.stringify(log.result, null, 2)}
                                                            </pre>
                                                        </div>
                                                    )}
                                                    {log.errorMessage && (
                                                        <div className="bg-red-50 rounded border border-red-200 p-3">
                                                            <h4 className="text-xs font-semibold text-red-700 mb-1">Error</h4>
                                                            <p className="text-xs text-red-600 font-mono">{log.errorMessage}</p>
                                                        </div>
                                                    )}
                                                    <div className="grid grid-cols-2 gap-2 text-[10px] text-gray-400">
                                                        <div>Provider: <span className="text-gray-600">{log.provider}</span></div>
                                                        <div>Model: <span className="text-gray-600">{log.model}</span></div>
                                                        <div>Duration: <span className="text-gray-600">{log.durationMs}ms</span></div>
                                                        <div>Tokens: <span className="text-gray-600">{log.inputTokens || 0} in / {log.outputTokens || 0} out</span></div>
                                                        {log.outcome && <div>Outcome: <span className="text-gray-600 font-medium">{log.outcome}</span></div>}
                                                        {log.overriddenBy && <div>Overridden by: <span className="text-gray-600">{log.overriddenBy}</span></div>}
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                )}

                {results && results.totalPages > 1 && (
                    <div className="flex items-center justify-between px-5 py-3 border-t border-gray-200 bg-gray-50">
                        <span className="text-xs text-gray-500">Page {results.number + 1} of {results.totalPages} ({results.totalElements} total)</span>
                        <div className="flex gap-1">
                            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
                                className="px-3 py-1 text-xs border rounded hover:bg-gray-100 disabled:opacity-50">Prev</button>
                            <button onClick={() => setPage(p => p + 1)} disabled={page >= results.totalPages - 1}
                                className="px-3 py-1 text-xs border rounded hover:bg-gray-100 disabled:opacity-50">Next</button>
                        </div>
                    </div>
                )}
            </div>
        </>
    );
}

function StatCard({ label, value, warn }: { label: string; value: number; warn?: boolean }) {
    return (
        <div className={`rounded-lg p-4 border ${warn ? "bg-red-50 border-red-200" : "bg-gray-50 border-gray-200"}`}>
            <div className="text-xs text-gray-500 mb-1">{label}</div>
            <div className={`text-2xl font-bold ${warn ? "text-red-600" : "text-gray-900"}`}>{value}</div>
        </div>
    );
}

function PromptSection({ title, content, onCopy }: { title: string; content: string; onCopy: (s: string) => void }) {
    const [expanded, setExpanded] = useState(false);
    const preview = content.length > 200 ? content.substring(0, 200) + "..." : content;

    return (
        <div>
            <div className="flex items-center justify-between mb-1">
                <h4 className="text-xs font-semibold text-gray-700">{title}</h4>
                <button onClick={() => onCopy(content)} className="text-[10px] text-gray-400 hover:text-gray-600 flex items-center gap-0.5">
                    <Copy className="size-3" /> Copy
                </button>
            </div>
            <pre className="bg-white rounded border border-gray-200 p-2 text-[10px] text-gray-600 whitespace-pre-wrap max-h-48 overflow-y-auto font-mono leading-relaxed">
                {expanded ? content : preview}
            </pre>
            {content.length > 200 && (
                <button onClick={() => setExpanded(!expanded)} className="text-[10px] text-blue-600 hover:text-blue-800 mt-0.5">
                    {expanded ? "Show less" : "Show full"}
                </button>
            )}
        </div>
    );
}
