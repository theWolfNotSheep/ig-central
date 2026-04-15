"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
    Shield, Search, FileText, Plus, Clock, CheckCircle, AlertTriangle,
    Loader2, X, Users, ChevronDown, ChevronRight,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

type PiiSearchResult = {
    documentId: string; slug?: string; fileName: string;
    categoryName?: string; sensitivityLabel?: string;
    piiStatus?: string; documentStatus?: string;
    piiMatches: { type: string; redactedText: string; confidence: number; dismissed: boolean }[];
    metadataMatches: string[]; textOccurrences: number;
};

type Sar = {
    id: string; reference: string; dataSubjectName: string; dataSubjectEmail?: string;
    searchTerms: string[]; status: string; jurisdiction: string;
    deadline: string; matchedDocumentIds: string[]; totalMatches: number;
    assignedTo?: string; requestDate: string; completedAt?: string;
    notes: { text: string; author: string; timestamp: string }[];
};

type PiiSummary = {
    totalDocuments: number; documentsWithPii: number; totalFindings: number;
    byType: Record<string, number>; byStatus: Record<string, number>;
};

const SENSITIVITY_COLORS: Record<string, string> = {
    PUBLIC: "bg-green-100 text-green-700", INTERNAL: "bg-blue-100 text-blue-700",
    CONFIDENTIAL: "bg-amber-100 text-amber-700", RESTRICTED: "bg-red-100 text-red-700",
};

const SAR_STATUS_COLORS: Record<string, string> = {
    RECEIVED: "bg-gray-100 text-gray-700", SEARCHING: "bg-blue-100 text-blue-700",
    REVIEWING: "bg-amber-100 text-amber-700", COMPILING: "bg-purple-100 text-purple-700",
    COMPLETED: "bg-green-100 text-green-700", OVERDUE: "bg-red-100 text-red-700",
};

const JURISDICTIONS = [
    { value: "UK_GDPR", label: "UK GDPR (30 days)" },
    { value: "CCPA", label: "CCPA / CPRA (45 days)" },
    { value: "HIPAA", label: "HIPAA (30 days)" },
];

export default function PiiPage() {
    const router = useRouter();
    const [tab, setTab] = useState<"search" | "sars" | "summary">("search");
    const [searchTerms, setSearchTerms] = useState("");
    const [searchResults, setSearchResults] = useState<PiiSearchResult[] | null>(null);
    const [searching, setSearching] = useState(false);
    const [sars, setSars] = useState<Sar[]>([]);
    const [summary, setSummary] = useState<PiiSummary | null>(null);
    const [showCreateSar, setShowCreateSar] = useState(false);
    const [expandedSar, setExpandedSar] = useState<string | null>(null);

    const loadSars = useCallback(async () => {
        try { const { data } = await api.get("/pii/sar/all"); setSars(data); } catch {}
    }, []);

    useEffect(() => { loadSars(); }, [loadSars]);
    useEffect(() => { api.get("/pii/summary").then(({ data }) => setSummary(data)).catch(() => {}); }, []);

    const handleSearch = async () => {
        if (!searchTerms.trim()) return;
        setSearching(true);
        try {
            const terms = searchTerms.split(",").map(t => t.trim()).filter(Boolean);
            const { data } = await api.post("/pii/search", { searchTerms: terms });
            setSearchResults(data);
        } catch { toast.error("Search failed"); }
        finally { setSearching(false); }
    };

    return (
        <>
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">PII & Subject Access Requests</h2>
                    <p className="text-sm text-gray-500 mt-1">
                        {summary ? `${summary.documentsWithPii} documents with PII, ${summary.totalFindings} active findings` : "Loading..."}
                    </p>
                </div>
                <button onClick={() => setShowCreateSar(true)}
                    className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700">
                    <Plus className="size-4" /> New SAR
                </button>
            </div>

            {/* Tabs */}
            <div className="flex gap-1 mb-6 bg-gray-100 rounded-lg p-1 w-fit">
                {([
                    { key: "search", label: "PII Search", icon: Search },
                    { key: "sars", label: `SARs (${sars.filter(s => s.status !== "COMPLETED").length})`, icon: Users },
                    { key: "summary", label: "Summary", icon: Shield },
                ] as const).map(t => (
                    <button key={t.key} onClick={() => setTab(t.key)}
                        className={`inline-flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-md transition-colors ${
                            tab === t.key ? "bg-white text-gray-900 shadow-sm" : "text-gray-600 hover:text-gray-900"
                        }`}><t.icon className="size-4" />{t.label}</button>
                ))}
            </div>

            {/* PII Search tab */}
            {tab === "search" && (
                <div className="space-y-4">
                    <div className="flex gap-2">
                        <div className="relative flex-1">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-gray-400" />
                            <input value={searchTerms} onChange={e => setSearchTerms(e.target.value)}
                                onKeyDown={e => e.key === "Enter" && handleSearch()}
                                placeholder="Search for PII: email, name, phone, NI number... (comma-separated for multiple)"
                                className="w-full pl-10 pr-4 py-2.5 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500" />
                        </div>
                        <button onClick={handleSearch} disabled={searching}
                            className="px-4 py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50">
                            {searching ? <Loader2 className="size-4 animate-spin" /> : "Search"}
                        </button>
                    </div>

                    {searchResults !== null && (
                        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                            <div className="px-4 py-3 border-b border-gray-200 bg-gray-50">
                                <span className="text-sm font-medium text-gray-700">{searchResults.length} document(s) found</span>
                            </div>
                            {searchResults.length === 0 ? (
                                <div className="p-8 text-center text-sm text-gray-400">No documents match the search criteria</div>
                            ) : (
                                <div className="divide-y divide-gray-100">
                                    {searchResults.map(r => (
                                        <button key={r.documentId}
                                            onClick={() => router.push(`/documents?doc=${r.slug ?? r.documentId}`)}
                                            className="w-full text-left px-4 py-3 hover:bg-gray-50">
                                            <div className="flex items-start justify-between gap-4">
                                                <div className="min-w-0 flex-1">
                                                    <div className="flex items-center gap-2 mb-1">
                                                        <FileText className="size-4 text-gray-400 shrink-0" />
                                                        <span className="text-sm font-medium text-gray-900 truncate">{r.fileName}</span>
                                                        {r.piiStatus === "DETECTED" && <span className="px-1.5 py-0.5 text-[9px] bg-red-100 text-red-700 rounded font-medium">PII</span>}
                                                    </div>
                                                    {r.piiMatches.length > 0 && (
                                                        <div className="flex flex-wrap gap-1 mb-1">
                                                            {r.piiMatches.filter(m => !m.dismissed).map((m, i) => (
                                                                <span key={i} className="px-1.5 py-0.5 text-[10px] bg-red-50 text-red-600 rounded border border-red-100">
                                                                    {m.type}: {m.redactedText}
                                                                </span>
                                                            ))}
                                                        </div>
                                                    )}
                                                    {r.metadataMatches.length > 0 && (
                                                        <div className="flex flex-wrap gap-1">
                                                            {r.metadataMatches.map((m, i) => (
                                                                <span key={i} className="px-1.5 py-0.5 text-[10px] bg-blue-50 text-blue-600 rounded">{m}</span>
                                                            ))}
                                                        </div>
                                                    )}
                                                </div>
                                                <div className="text-right shrink-0">
                                                    {r.sensitivityLabel && (
                                                        <span className={`px-1.5 py-0.5 text-[10px] font-medium rounded-full ${SENSITIVITY_COLORS[r.sensitivityLabel] ?? ""}`}>
                                                            {r.sensitivityLabel}
                                                        </span>
                                                    )}
                                                    {r.textOccurrences > 0 && (
                                                        <div className="text-[10px] text-gray-400 mt-1">{r.textOccurrences} occurrence(s) in text</div>
                                                    )}
                                                </div>
                                            </div>
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}
                </div>
            )}

            {/* SARs tab */}
            {tab === "sars" && (
                <div className="space-y-3">
                    {sars.length === 0 ? (
                        <div className="text-center py-8 text-gray-400 text-sm">No subject access requests</div>
                    ) : sars.map(sar => {
                        const daysLeft = Math.ceil((new Date(sar.deadline).getTime() - Date.now()) / 86400000);
                        const isOverdue = daysLeft < 0 && sar.status !== "COMPLETED";
                        return (
                            <div key={sar.id} className={`bg-white rounded-lg shadow-sm border ${isOverdue ? "border-red-300" : "border-gray-200"}`}>
                                <button onClick={() => setExpandedSar(expandedSar === sar.id ? null : sar.id)}
                                    className="w-full text-left px-5 py-4">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <div className="flex items-center gap-2">
                                                <span className="text-sm font-semibold text-gray-900">{sar.reference}</span>
                                                <span className={`px-2 py-0.5 text-[10px] font-medium rounded-full ${SAR_STATUS_COLORS[isOverdue ? "OVERDUE" : sar.status] ?? ""}`}>
                                                    {isOverdue ? "OVERDUE" : sar.status}
                                                </span>
                                                <span className="text-xs text-gray-400">{sar.jurisdiction}</span>
                                            </div>
                                            <div className="text-sm text-gray-700 mt-0.5">{sar.dataSubjectName}</div>
                                        </div>
                                        <div className="text-right">
                                            <div className={`text-xs font-medium ${isOverdue ? "text-red-600" : daysLeft <= 7 ? "text-amber-600" : "text-gray-500"}`}>
                                                {sar.status === "COMPLETED" ? "Completed" : isOverdue ? `${Math.abs(daysLeft)} days overdue` : `${daysLeft} days left`}
                                            </div>
                                            <div className="text-[10px] text-gray-400">{sar.totalMatches} document(s)</div>
                                        </div>
                                    </div>
                                </button>

                                {expandedSar === sar.id && (
                                    <div className="px-5 pb-4 border-t border-gray-100 pt-3 space-y-3">
                                        <div className="grid grid-cols-2 gap-2 text-xs">
                                            <div><span className="text-gray-500">Email:</span> <span className="text-gray-900">{sar.dataSubjectEmail ?? "—"}</span></div>
                                            <div><span className="text-gray-500">Requested:</span> <span className="text-gray-900">{new Date(sar.requestDate).toLocaleDateString()}</span></div>
                                            <div><span className="text-gray-500">Deadline:</span> <span className="text-gray-900">{new Date(sar.deadline).toLocaleDateString()}</span></div>
                                            <div><span className="text-gray-500">Assigned:</span> <span className="text-gray-900">{sar.assignedTo ?? "Unassigned"}</span></div>
                                        </div>
                                        <div className="text-xs text-gray-500">
                                            Search terms: {sar.searchTerms?.join(", ")}
                                        </div>
                                        {sar.notes?.length > 0 && (
                                            <div className="space-y-1">
                                                {sar.notes.map((n, i) => (
                                                    <div key={i} className="text-[10px] text-gray-500">
                                                        <span className="font-medium">{n.author}</span> — {n.text}
                                                        <span className="text-gray-400 ml-1">{new Date(n.timestamp).toLocaleString()}</span>
                                                    </div>
                                                ))}
                                            </div>
                                        )}
                                        <div className="flex gap-2">
                                            {sar.status !== "COMPLETED" && (
                                                <button onClick={async () => {
                                                    await api.put(`/pii/sar/${sar.id}/status`, { status: "COMPLETED", note: "Marked as completed" });
                                                    toast.success("SAR completed"); loadSars();
                                                }} className="text-xs text-green-600 hover:text-green-800 font-medium">Complete</button>
                                            )}
                                        </div>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}

            {/* Summary tab */}
            {tab === "summary" && summary && (
                <div className="space-y-4">
                    <div className="grid grid-cols-3 gap-4">
                        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
                            <div className="text-2xl font-bold text-gray-900">{summary.documentsWithPii}</div>
                            <div className="text-xs text-gray-500">Documents with PII</div>
                        </div>
                        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
                            <div className="text-2xl font-bold text-red-600">{summary.totalFindings}</div>
                            <div className="text-xs text-gray-500">Active PII findings</div>
                        </div>
                        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
                            <div className="text-2xl font-bold text-blue-600">{Object.keys(summary.byType).length}</div>
                            <div className="text-xs text-gray-500">PII types detected</div>
                        </div>
                    </div>
                    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
                        <h4 className="text-sm font-semibold text-gray-900 mb-3">Findings by Type</h4>
                        <div className="space-y-2">
                            {Object.entries(summary.byType).sort(([,a], [,b]) => b - a).map(([type, count]) => (
                                <div key={type} className="flex items-center justify-between">
                                    <span className="text-xs text-gray-700">{type.replace(/_/g, " ")}</span>
                                    <div className="flex items-center gap-2">
                                        <div className="w-32 bg-gray-100 rounded-full h-2">
                                            <div className="bg-red-500 h-2 rounded-full"
                                                style={{ width: `${Math.min(100, (count / summary.totalFindings) * 100)}%` }} />
                                        </div>
                                        <span className="text-xs font-medium text-gray-900 w-8 text-right">{count}</span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
                        <h4 className="text-sm font-semibold text-gray-900 mb-3">Documents by PII Status</h4>
                        <div className="flex gap-3">
                            {Object.entries(summary.byStatus).map(([status, count]) => (
                                <div key={status} className="text-center">
                                    <div className="text-lg font-bold text-gray-900">{count}</div>
                                    <div className="text-[10px] text-gray-500">{status}</div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            )}

            {/* Create SAR Modal */}
            {showCreateSar && (
                <CreateSarModal onClose={() => setShowCreateSar(false)} onCreated={() => { setShowCreateSar(false); loadSars(); }} />
            )}
        </>
    );
}

function CreateSarModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
    const [name, setName] = useState("");
    const [email, setEmail] = useState("");
    const [terms, setTerms] = useState("");
    const [jurisdiction, setJurisdiction] = useState("UK_GDPR");
    const [saving, setSaving] = useState(false);

    const handleCreate = async () => {
        if (!name.trim()) { toast.error("Data subject name is required"); return; }
        const searchTerms = [name.trim(), email.trim(), ...terms.split(",").map(t => t.trim())].filter(Boolean);
        setSaving(true);
        try {
            const { data } = await api.post("/pii/sar", { dataSubjectName: name, dataSubjectEmail: email, searchTerms, jurisdiction });
            toast.success(`SAR ${data.reference} created — ${data.totalMatches} document(s) found`);
            onCreated();
        } catch { toast.error("Failed to create SAR"); }
        finally { setSaving(false); }
    };

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onClose}>
            <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6 space-y-4" onClick={e => e.stopPropagation()}>
                <div className="flex items-center justify-between">
                    <h3 className="text-lg font-semibold text-gray-900">New Subject Access Request</h3>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X className="size-5" /></button>
                </div>

                <div className="bg-blue-50 rounded-md p-3 border border-blue-100 text-xs text-blue-800">
                    <p className="font-medium mb-1">What is a SAR?</p>
                    <p>Under UK GDPR, individuals have the right to request a copy of their personal data. You have 30 days to respond. The system will automatically search all documents for the data subject&apos;s information.</p>
                </div>

                <div className="space-y-3">
                    <div>
                        <label className="text-xs font-medium text-gray-700 block mb-1">Data Subject Name *</label>
                        <input value={name} onChange={e => setName(e.target.value)} placeholder="e.g. John Smith"
                            className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                    </div>
                    <div>
                        <label className="text-xs font-medium text-gray-700 block mb-1">Email Address</label>
                        <input value={email} onChange={e => setEmail(e.target.value)} placeholder="e.g. john@example.com"
                            className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                    </div>
                    <div>
                        <label className="text-xs font-medium text-gray-700 block mb-1">Additional Search Terms (comma-separated)</label>
                        <input value={terms} onChange={e => setTerms(e.target.value)}
                            placeholder="e.g. NI number, phone number, employee ID"
                            className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                    </div>
                    <div>
                        <label className="text-xs font-medium text-gray-700 block mb-1">Jurisdiction</label>
                        <select value={jurisdiction} onChange={e => setJurisdiction(e.target.value)}
                            className="w-full text-sm border border-gray-300 rounded-md px-3 py-2">
                            {JURISDICTIONS.map(j => <option key={j.value} value={j.value}>{j.label}</option>)}
                        </select>
                    </div>
                </div>

                <div className="flex gap-3 justify-end pt-2 border-t border-gray-200">
                    <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                    <button onClick={handleCreate} disabled={saving}
                        className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50">
                        {saving ? "Creating..." : "Create SAR & Search"}
                    </button>
                </div>
            </div>
        </div>
    );
}
