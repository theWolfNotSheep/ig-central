"use client";

import { useCallback, useEffect, useState } from "react";
import {
    FileSearch,
    CheckCircle,
    XCircle,
    Edit3,
    ChevronLeft,
    ChevronRight,
    AlertTriangle,
    ShieldAlert,
    Pencil,
    X,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import EmptyState from "@/components/empty-state";
import { usePiiTypes } from "@/hooks/use-pii-types";
import ReasonModal from "@/components/reason-modal";

type ReviewDoc = {
    id: string;
    fileName: string;
    originalFileName: string;
    status: string;
    categoryName?: string;
    sensitivityLabel?: string;
    classificationResultId?: string;
    createdAt: string;
};

type ClassificationResult = {
    id: string;
    documentId: string;
    categoryId: string;
    categoryName: string;
    sensitivityLabel: string;
    confidence: number;
    reasoning: string;
    tags: string[];
    modelId?: string;
    classifiedAt: string;
};

const SENSITIVITY_COLORS: Record<string, string> = {
    PUBLIC: "bg-green-100 text-green-700",
    INTERNAL: "bg-blue-100 text-blue-700",
    CONFIDENTIAL: "bg-amber-100 text-amber-700",
    RESTRICTED: "bg-red-100 text-red-700",
};

export default function ReviewPage() {
    const [docs, setDocs] = useState<ReviewDoc[]>([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [selected, setSelected] = useState<ReviewDoc | null>(null);
    const [classification, setClassification] = useState<ClassificationResult | null>(null);
    const [actionLoading, setActionLoading] = useState(false);
    const [showOverride, setShowOverride] = useState(false);
    const [showPiiReport, setShowPiiReport] = useState(false);
    const [showReject, setShowReject] = useState(false);
    const piiTypes = usePiiTypes();

    const fetchQueue = useCallback(async () => {
        try {
            const { data } = await api.get("/review", { params: { page, size: 20 } });
            setDocs(data.content ?? []);
            setTotalPages(data.totalPages ?? 0);
        } catch {
            toast.error("Failed to load review queue");
        }
    }, [page]);

    useEffect(() => { fetchQueue(); }, [fetchQueue]);

    const selectDoc = async (doc: ReviewDoc) => {
        setSelected(doc);
        setClassification(null);
        try {
            const { data } = await api.get(`/review/${doc.id}/classification`);
            if (data.length > 0) setClassification(data[0]);
        } catch {
            toast.error("Failed to load classification");
        }
    };

    const handleApprove = async () => {
        if (!selected) return;
        setActionLoading(true);
        try {
            await api.post(`/review/${selected.id}/approve`);
            toast.success("Classification approved");
            setSelected(null);
            fetchQueue();
        } catch {
            toast.error("Approval failed");
        } finally {
            setActionLoading(false);
        }
    };

    const handleReject = async (reason: string) => {
        if (!selected || !reason) return;
        setActionLoading(true);
        try {
            await api.post(`/review/${selected.id}/reject`, { reason });
            toast.success("Classification rejected");
            setShowReject(false);
            setSelected(null);
            fetchQueue();
        } catch {
            toast.error("Rejection failed");
        } finally {
            setActionLoading(false);
        }
    };

    const confidenceColor = (c: number) => {
        if (c >= 0.9) return "text-green-600";
        if (c >= 0.7) return "text-blue-600";
        if (c >= 0.5) return "text-amber-600";
        return "text-red-600";
    };

    return (
        <>
            <h2 className="text-xl font-bold text-gray-900 mb-6">Review Queue</h2>

            <div className="flex gap-6">
                {/* Queue list */}
                <div className="flex-1">
                    <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                        <div className="divide-y divide-gray-200">
                            {docs.length === 0 ? (
                                <EmptyState
                                    icon={CheckCircle}
                                    title="All caught up!"
                                    description="No documents are awaiting human review right now."
                                    action="Upload Documents"
                                    href="/documents"
                                />
                            ) : (
                                docs.map((doc) => (
                                    <button
                                        key={doc.id}
                                        onClick={() => selectDoc(doc)}
                                        className={`w-full text-left px-6 py-4 hover:bg-gray-50 transition-colors ${
                                            selected?.id === doc.id ? "bg-blue-50 border-l-2 border-blue-600" : ""
                                        }`}
                                    >
                                        <div className="flex items-center justify-between">
                                            <div className="min-w-0">
                                                <span className="text-sm font-medium text-gray-900 truncate block">
                                                    {doc.originalFileName || doc.fileName}
                                                </span>
                                                <span className="text-xs text-gray-500">
                                                    {new Date(doc.createdAt).toLocaleDateString()}
                                                </span>
                                            </div>
                                            <AlertTriangle className="size-4 text-amber-500 shrink-0 ml-3" />
                                        </div>
                                    </button>
                                ))
                            )}
                        </div>

                        {totalPages > 1 && (
                            <div className="flex items-center justify-between px-6 py-3 border-t border-gray-200 bg-gray-50">
                                <span className="text-sm text-gray-600">Page {page + 1} of {totalPages}</span>
                                <div className="flex gap-2">
                                    <button onClick={() => setPage(Math.max(0, page - 1))} disabled={page === 0} className="p-1 rounded hover:bg-gray-200 disabled:opacity-30">
                                        <ChevronLeft className="size-5" />
                                    </button>
                                    <button onClick={() => setPage(Math.min(totalPages - 1, page + 1))} disabled={page >= totalPages - 1} className="p-1 rounded hover:bg-gray-200 disabled:opacity-30">
                                        <ChevronRight className="size-5" />
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>
                </div>

                {/* Detail panel */}
                <div className="w-96 shrink-0">
                    {selected && classification ? (
                        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 space-y-5 sticky top-6">
                            <div>
                                <h3 className="font-semibold text-gray-900 mb-1">
                                    {selected.originalFileName || selected.fileName}
                                </h3>
                                <p className="text-xs text-gray-500">ID: {selected.id}</p>
                            </div>

                            <div className="space-y-3">
                                <DetailRow label="Category" value={classification.categoryName} />
                                <DetailRow label="Sensitivity">
                                    <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${SENSITIVITY_COLORS[classification.sensitivityLabel] ?? ""}`}>
                                        {classification.sensitivityLabel}
                                    </span>
                                </DetailRow>
                                <DetailRow label="Confidence">
                                    <span className={`font-bold ${confidenceColor(classification.confidence)}`}>
                                        {(classification.confidence * 100).toFixed(0)}%
                                    </span>
                                </DetailRow>
                                <DetailRow label="Model" value={classification.modelId || "claude"} />
                            </div>

                            {classification.reasoning && (
                                <div>
                                    <h4 className="text-sm font-medium text-gray-700 mb-1">Reasoning</h4>
                                    <p className="text-sm text-gray-600 bg-gray-50 rounded p-3">
                                        {classification.reasoning}
                                    </p>
                                </div>
                            )}

                            {classification.tags?.length > 0 && (
                                <div>
                                    <h4 className="text-sm font-medium text-gray-700 mb-1">Tags</h4>
                                    <div className="flex flex-wrap gap-1">
                                        {classification.tags.map((tag) => (
                                            <span key={tag} className="px-2 py-0.5 text-xs bg-gray-100 text-gray-600 rounded">
                                                {tag}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {/* Actions */}
                            <div className="space-y-2 pt-2 border-t border-gray-200">
                                <div className="flex gap-2">
                                    <button
                                        onClick={handleApprove}
                                        disabled={actionLoading}
                                        className="flex-1 inline-flex items-center justify-center gap-2 px-4 py-2 bg-green-600 text-white text-sm font-medium rounded-md hover:bg-green-700 disabled:opacity-50 transition-colors"
                                    >
                                        <CheckCircle className="size-4" />
                                        Approve
                                    </button>
                                    <button
                                        onClick={() => setShowReject(true)}
                                        disabled={actionLoading}
                                        className="flex-1 inline-flex items-center justify-center gap-2 px-4 py-2 bg-red-600 text-white text-sm font-medium rounded-md hover:bg-red-700 disabled:opacity-50 transition-colors"
                                    >
                                        <XCircle className="size-4" />
                                        Reject
                                    </button>
                                </div>
                                <div className="flex gap-2">
                                    <button
                                        onClick={() => setShowOverride(true)}
                                        disabled={actionLoading}
                                        className="flex-1 inline-flex items-center justify-center gap-2 px-4 py-2 border border-amber-300 text-amber-700 bg-amber-50 text-sm font-medium rounded-md hover:bg-amber-100 disabled:opacity-50 transition-colors"
                                    >
                                        <Pencil className="size-4" />
                                        Reclassify
                                    </button>
                                    <button
                                        onClick={() => setShowPiiReport(true)}
                                        disabled={actionLoading}
                                        className="flex-1 inline-flex items-center justify-center gap-2 px-4 py-2 border border-purple-300 text-purple-700 bg-purple-50 text-sm font-medium rounded-md hover:bg-purple-100 disabled:opacity-50 transition-colors"
                                    >
                                        <ShieldAlert className="size-4" />
                                        Flag PII
                                    </button>
                                </div>
                            </div>
                        </div>
                    ) : selected ? (
                        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 text-center text-gray-500">
                            Loading classification...
                        </div>
                    ) : (
                        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 text-center text-gray-400">
                            <FileSearch className="size-8 mx-auto mb-3" />
                            Select a document to review its classification.
                        </div>
                    )}
                </div>
            </div>
            {/* Reject Modal */}
            {showReject && selected && (
                <ReasonModal
                    title="Reject Classification"
                    description="Explain why this classification is incorrect. The document will be flagged for further investigation."
                    placeholder="e.g. Category is wrong — this is a contract, not a policy document"
                    confirmLabel="Reject"
                    confirmClass="bg-red-600 hover:bg-red-700"
                    onConfirm={(reason) => handleReject(reason)}
                    onClose={() => setShowReject(false)}
                />
            )}

            {/* Override Modal */}
            {showOverride && selected && (
                <OverrideModal
                    documentId={selected.id}
                    currentCategory={classification?.categoryName}
                    currentSensitivity={classification?.sensitivityLabel}
                    currentTags={classification?.tags ?? []}
                    onClose={() => setShowOverride(false)}
                    onSaved={() => { setShowOverride(false); setSelected(null); fetchQueue(); }}
                />
            )}

            {/* PII Report Modal */}
            {showPiiReport && selected && (
                <PiiReportModal
                    documentId={selected.id}
                    piiTypes={piiTypes}
                    onClose={() => setShowPiiReport(false)}
                    onSaved={() => { setShowPiiReport(false); toast.success("PII report submitted — this will improve future detection"); }}
                />
            )}
        </>
    );
}

function DetailRow({ label, value, children }: { label: string; value?: string; children?: React.ReactNode }) {
    return (
        <div className="flex justify-between items-center">
            <span className="text-sm text-gray-500">{label}</span>
            {children ?? <span className="text-sm font-medium text-gray-900">{value}</span>}
        </div>
    );
}

/* ── Override Modal ────────────────────────────────── */

function OverrideModal({ documentId, currentCategory, currentSensitivity, currentTags, onClose, onSaved }: {
    documentId: string;
    currentCategory?: string;
    currentSensitivity?: string;
    currentTags: string[];
    onClose: () => void;
    onSaved: () => void;
}) {
    const [categories, setCategories] = useState<{ id: string; name: string; parentId?: string; defaultSensitivity: string }[]>([]);
    const [sensitivities, setSensitivities] = useState<{ key: string; displayName: string }[]>([]);
    const [categoryId, setCategoryId] = useState("");
    const [categoryName, setCategoryName] = useState("");
    const [sensitivity, setSensitivity] = useState(currentSensitivity ?? "");
    const [reason, setReason] = useState("");
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        api.get("/admin/governance/taxonomy").then(({ data }) => setCategories(data)).catch(() => {});
        api.get("/admin/governance/sensitivities").then(({ data }) => setSensitivities(data)).catch(() => {});
    }, []);

    const handleCategoryChange = (id: string) => {
        setCategoryId(id);
        const cat = categories.find((c) => c.id === id);
        if (cat) { setCategoryName(cat.name); setSensitivity(cat.defaultSensitivity); }
    };

    const handleSave = async () => {
        if (!categoryId || !sensitivity || !reason.trim()) {
            toast.error("Please fill in all fields including a reason");
            return;
        }
        setSaving(true);
        try {
            await api.post(`/review/${documentId}/override`, {
                categoryId, categoryName, sensitivityLabel: sensitivity,
                tags: currentTags, reason: reason.trim(),
            });
            toast.success("Document reclassified — correction recorded for future LLM improvements");
            onSaved();
        } catch {
            toast.error("Reclassification failed");
        } finally {
            setSaving(false);
        }
    };

    const roots = categories.filter((c) => !c.parentId);
    const childrenOf = (pid: string) => categories.filter((c) => c.parentId === pid);

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onClose}>
            <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6 space-y-4" onClick={(e) => e.stopPropagation()}>
                <div className="flex items-center justify-between">
                    <h3 className="text-lg font-semibold text-gray-900">Reclassify Document</h3>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X className="size-5" /></button>
                </div>

                {currentCategory && (
                    <div className="bg-gray-50 rounded-lg p-3 text-sm">
                        <span className="text-gray-500">Current: </span>
                        <span className="font-medium text-gray-900">{currentCategory}</span>
                        {currentSensitivity && (
                            <span className={`ml-2 px-2 py-0.5 text-xs font-medium rounded-full ${SENSITIVITY_COLORS[currentSensitivity] ?? ""}`}>
                                {currentSensitivity}
                            </span>
                        )}
                    </div>
                )}

                <div>
                    <label className="text-xs font-medium text-gray-700 block mb-1">New Category</label>
                    <select value={categoryId} onChange={(e) => handleCategoryChange(e.target.value)}
                        className="w-full text-sm border border-gray-300 rounded-md px-3 py-2">
                        <option value="">Select a category...</option>
                        {roots.map((root) => (
                            <optgroup key={root.id} label={root.name}>
                                <option value={root.id}>{root.name}</option>
                                {childrenOf(root.id).map((child) => (
                                    <option key={child.id} value={child.id}>&nbsp;&nbsp;{child.name}</option>
                                ))}
                            </optgroup>
                        ))}
                    </select>
                </div>

                <div>
                    <label className="text-xs font-medium text-gray-700 block mb-1">Sensitivity</label>
                    <select value={sensitivity} onChange={(e) => setSensitivity(e.target.value)}
                        className="w-full text-sm border border-gray-300 rounded-md px-3 py-2">
                        <option value="">Select...</option>
                        {sensitivities.map((s) => <option key={s.key} value={s.key}>{s.displayName}</option>)}
                    </select>
                </div>

                <div>
                    <label className="text-xs font-medium text-gray-700 block mb-1">Reason for change</label>
                    <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} placeholder="Why is this classification wrong?"
                        className="w-full text-sm border border-gray-300 rounded-md px-3 py-2 resize-none" />
                </div>

                <div className="flex gap-2 justify-end pt-2">
                    <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                    <button onClick={handleSave} disabled={saving}
                        className="px-4 py-2 text-sm font-medium text-white bg-amber-600 rounded-md hover:bg-amber-700 disabled:opacity-50">
                        {saving ? "Saving..." : "Reclassify"}
                    </button>
                </div>
            </div>
        </div>
    );
}

/* ── PII Report Modal ─────────────────────────────── */

function PiiReportModal({ documentId, piiTypes, onClose, onSaved }: {
    documentId: string; piiTypes: { key: string; displayName: string }[]; onClose: () => void; onSaved: () => void;
}) {
    const [items, setItems] = useState([{ type: "", description: "", context: "" }]);
    const [saving, setSaving] = useState(false);

    const updateItem = (idx: number, field: string, value: string) => {
        setItems((prev) => prev.map((item, i) => i === idx ? { ...item, [field]: value } : item));
    };

    const addItem = () => setItems((prev) => [...prev, { type: "", description: "", context: "" }]);

    const removeItem = (idx: number) => {
        if (items.length === 1) return;
        setItems((prev) => prev.filter((_, i) => i !== idx));
    };

    const handleSave = async () => {
        const valid = items.filter((i) => i.type.trim() && i.description.trim());
        if (valid.length === 0) {
            toast.error("Please add at least one PII item with type and description");
            return;
        }
        setSaving(true);
        try {
            await api.post(`/review/${documentId}/report-pii`, { piiItems: valid });
            onSaved();
        } catch {
            toast.error("Failed to submit PII report");
        } finally {
            setSaving(false);
        }
    };


    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onClose}>
            <div className="bg-white rounded-lg shadow-xl w-full max-w-lg p-6 space-y-4" onClick={(e) => e.stopPropagation()}>
                <div className="flex items-center justify-between">
                    <div>
                        <h3 className="text-lg font-semibold text-gray-900">Report Missed PII</h3>
                        <p className="text-xs text-gray-500 mt-0.5">Flag personal data the system didn&apos;t detect. This improves future scanning.</p>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X className="size-5" /></button>
                </div>

                <div className="space-y-3 max-h-64 overflow-y-auto">
                    {items.map((item, idx) => (
                        <div key={idx} className="bg-gray-50 rounded-lg p-3 space-y-2">
                            <div className="flex items-center gap-2">
                                <select value={item.type} onChange={(e) => updateItem(idx, "type", e.target.value)}
                                    className="flex-1 text-xs border border-gray-300 rounded-md px-2 py-1.5">
                                    <option value="">PII type...</option>
                                    {piiTypes.map((t) => <option key={t.key} value={t.key}>{t.displayName}</option>)}
                                </select>
                                {items.length > 1 && (
                                    <button onClick={() => removeItem(idx)} className="text-gray-400 hover:text-red-500">
                                        <X className="size-4" />
                                    </button>
                                )}
                            </div>
                            <input type="text" placeholder="What is the PII? (e.g. 'Employee reference numbers EMP-XXXX')"
                                value={item.description} onChange={(e) => updateItem(idx, "description", e.target.value)}
                                className="w-full text-xs border border-gray-300 rounded-md px-2 py-1.5" />
                            <input type="text" placeholder="Where in the document? (e.g. 'Table on page 2', 'Header section')"
                                value={item.context} onChange={(e) => updateItem(idx, "context", e.target.value)}
                                className="w-full text-xs border border-gray-300 rounded-md px-2 py-1.5" />
                        </div>
                    ))}
                </div>

                <button onClick={addItem} className="text-xs text-blue-600 hover:text-blue-800 font-medium">+ Add another PII item</button>

                <div className="flex gap-2 justify-end pt-2">
                    <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                    <button onClick={handleSave} disabled={saving}
                        className="px-4 py-2 text-sm font-medium text-white bg-purple-600 rounded-md hover:bg-purple-700 disabled:opacity-50">
                        {saving ? "Submitting..." : "Submit PII Report"}
                    </button>
                </div>
            </div>
        </div>
    );
}
