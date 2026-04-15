"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
    Upload, Loader2, Search, FolderOpen, FolderClosed, FileText,
    FileImage, FileSpreadsheet, ChevronRight, ChevronDown,
    CheckSquare, Square, RefreshCw, CheckCircle, Clock, AlertTriangle,
    XCircle, Inbox,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import EmptyState from "@/components/empty-state";
import { SkeletonTable } from "@/components/skeleton";
import DocumentViewer from "@/components/document-viewer";
import ResizableTh from "@/components/resizable-th";
import ClassificationPanel from "@/components/classification-panel";
import DocumentAuditTrail from "@/components/document-audit-trail";
import { usePiiTypes } from "@/hooks/use-pii-types";

type Doc = {
    id: string; slug?: string; fileName: string; originalFileName: string;
    mimeType: string; fileSizeBytes: number; status: string;
    categoryName?: string; categoryId?: string; sensitivityLabel?: string;
    tags?: string[]; pageCount?: number; extractedText?: string;
    extractedMetadata?: Record<string, string>; dublinCore?: Record<string, string>;
    retentionScheduleId?: string; retentionExpiresAt?: string;
    legalHold?: boolean; legalHoldReason?: string; appliedPolicyIds?: string[];
    classificationResultId?: string; lastError?: string; lastErrorStage?: string;
    failedAt?: string; retryCount?: number; storageProvider?: string;
    externalStorageRef?: Record<string, string>;
    createdAt: string; classifiedAt?: string; governanceAppliedAt?: string;
};

type Category = { id: string; name: string; parentId?: string; active: boolean };

const SENSITIVITY_COLORS: Record<string, string> = {
    PUBLIC: "bg-green-100 text-green-700", INTERNAL: "bg-blue-100 text-blue-700",
    CONFIDENTIAL: "bg-amber-100 text-amber-700", RESTRICTED: "bg-red-100 text-red-700",
};

const STATUS_ICONS: Record<string, { icon: typeof CheckCircle; color: string }> = {
    UPLOADED: { icon: Clock, color: "text-gray-400" },
    PROCESSING: { icon: Loader2, color: "text-yellow-500" },
    PROCESSING_FAILED: { icon: XCircle, color: "text-red-500" },
    PROCESSED: { icon: Clock, color: "text-yellow-400" },
    CLASSIFYING: { icon: Loader2, color: "text-blue-500" },
    CLASSIFICATION_FAILED: { icon: XCircle, color: "text-red-500" },
    CLASSIFIED: { icon: CheckCircle, color: "text-green-400" },
    ENFORCEMENT_FAILED: { icon: XCircle, color: "text-red-500" },
    REVIEW_REQUIRED: { icon: AlertTriangle, color: "text-amber-500" },
    GOVERNANCE_APPLIED: { icon: CheckCircle, color: "text-green-600" },
    INBOX: { icon: Inbox, color: "text-blue-500" },
    FILED: { icon: CheckCircle, color: "text-green-700" },
};

function fileIcon(mime: string) {
    if (mime?.includes("image")) return FileImage;
    if (mime?.includes("spreadsheet") || mime?.includes("csv")) return FileSpreadsheet;
    return FileText;
}

function formatSize(bytes: number) {
    if (!bytes) return "";
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function DocumentsPage() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const piiTypes = usePiiTypes();

    const [categories, setCategories] = useState<Category[]>([]);
    const [docs, setDocs] = useState<Doc[]>([]);
    const [selectedDoc, setSelectedDoc] = useState<Doc | null>(null);
    const [viewingDoc, setViewingDoc] = useState<Doc | null>(null);
    const [loading, setLoading] = useState(false);
    const [currentCategory, setCurrentCategory] = useState<{ id: string; name: string } | null>(null);
    const [expandedCats, setExpandedCats] = useState<Set<string>>(new Set());
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [piiSelectedText, setPiiSelectedText] = useState<string | null>(null);
    const [treeKey, setTreeKey] = useState(0);
    const [sortField, setSortField] = useState<string>("createdAt");
    const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
    const [searchQuery, setSearchQuery] = useState("");

    // Load categories
    useEffect(() => {
        api.get("/admin/governance/taxonomy").then(({ data }) => setCategories(data)).catch(() => {});
    }, [treeKey]);

    const IN_PROGRESS_STATUSES = new Set([
        "UPLOADING", "UPLOADED", "PROCESSING", "PROCESSED", "CLASSIFYING", "CLASSIFIED",
    ]);

    // Load docs for current category, search query, or all
    // When silent=true, skip the loading spinner (used by polling/SSE refreshes)
    const loadDocs = useCallback(async (silent = false) => {
        if (!silent) { setLoading(true); setSelected(new Set()); }
        try {
            let fetched: Doc[];
            if (searchQuery.trim()) {
                const { data } = await api.get("/documents", {
                    params: { q: searchQuery.trim(), size: 100, sort: "createdAt,desc",
                        ...(currentCategory ? { category: currentCategory.name } : {}) }
                });
                fetched = data.content ?? data;
            } else if (currentCategory) {
                const { data } = await api.get(`/documents/by-category/${currentCategory.id}?name=${encodeURIComponent(currentCategory.name)}`);
                fetched = data;
            } else {
                const { data } = await api.get("/documents?size=100&sort=createdAt,desc");
                fetched = data.content ?? data;
            }
            setDocs(fetched);
        } catch { if (!silent) toast.error("Failed to load documents"); }
        finally { if (!silent) setLoading(false); }
    }, [currentCategory, searchQuery]);

    useEffect(() => { loadDocs(); }, [loadDocs]);

    // Poll every 3s when any document is in an in-progress state
    const hasInProgress = docs.some(d => IN_PROGRESS_STATUSES.has(d.status));
    useEffect(() => {
        if (!hasInProgress) return;
        const timer = setInterval(() => loadDocs(true), 3000);
        return () => clearInterval(timer);
    }, [hasInProgress, loadDocs]);

    // Poll the viewed document individually when it's in-progress
    useEffect(() => {
        if (!viewingDoc || !IN_PROGRESS_STATUSES.has(viewingDoc.status)) return;
        const timer = setInterval(async () => {
            try {
                const { data } = await api.get(`/documents/${viewingDoc.id}`);
                setViewingDoc(data);
                setSelectedDoc(data);
            } catch { /* ignore */ }
        }, 2000);
        return () => clearInterval(timer);
    }, [viewingDoc?.id, viewingDoc?.status]);

    // Track whether user explicitly closed the viewer (to prevent URL param re-opening it)
    const closedByUser = useRef(false);

    // Load doc from slug or ID query param
    useEffect(() => {
        const docParam = searchParams.get("doc");
        if (docParam && !viewingDoc && !closedByUser.current) {
            // Try slug first, then fall back to direct ID lookup
            api.get(`/documents/by-slug/${docParam}`).then(({ data }) => {
                setSelectedDoc(data); setViewingDoc(data);
            }).catch(() => {
                api.get(`/documents/${docParam}`).then(({ data }) => {
                    setSelectedDoc(data); setViewingDoc(data);
                }).catch(() => {});
            });
        }
        // Reset the flag once the URL no longer has the doc param
        if (!docParam) {
            closedByUser.current = false;
        }
    }, [searchParams, viewingDoc]);

    // SSE for instant updates (supplements polling)
    useEffect(() => {
        let es: EventSource | null = null;
        let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

        const connect = () => {
            es = new EventSource("/api/admin/monitoring/events", { withCredentials: true });
            es.addEventListener("document-status", (e) => {
                try {
                    const event = JSON.parse(e.data);
                    const { documentId, status, fileName } = event;

                    // Update the document in-place in the list
                    setDocs(prev => {
                        const idx = prev.findIndex(d => d.id === documentId);
                        if (idx === -1) {
                            // New doc not in list — do a full silent reload
                            loadDocs(true);
                            return prev;
                        }
                        const updated = [...prev];
                        updated[idx] = { ...updated[idx], status, ...(fileName ? { fileName } : {}) };
                        return updated;
                    });

                    // If this is a terminal status, do a full silent reload to get all fields
                    if (!IN_PROGRESS_STATUSES.has(status)) {
                        loadDocs(true);
                        setTreeKey(k => k + 1);
                    }

                    // Refresh the viewed document if it's the one that changed
                    setViewingDoc(prev => {
                        if (prev && prev.id === documentId) {
                            api.get(`/documents/${prev.id}`).then(({ data }) => {
                                setViewingDoc(data);
                                setSelectedDoc(data);
                            }).catch(() => {});
                        }
                        return prev;
                    });
                } catch {
                    // Parse error — fall back to full reload
                    loadDocs(true);
                }
            });
            es.onerror = () => {
                es?.close();
                reconnectTimer = setTimeout(connect, 3000);
            };
        };
        connect();

        return () => {
            es?.close();
            if (reconnectTimer) clearTimeout(reconnectTimer);
        };
    }, [loadDocs]);

    const handleDocClick = (doc: Doc) => {
        setSelectedDoc(doc); setViewingDoc(doc);
        if (doc.slug) router.replace(`/documents?doc=${doc.slug}`, { scroll: false });
    };

    const handleReprocess = async (doc: Doc) => {
        try {
            await api.post(`/documents/${doc.id}/reprocess`);
            toast.success("Queued for reprocessing");
            const { data } = await api.get(`/documents/${doc.id}`);
            setSelectedDoc(data); setViewingDoc(data); loadDocs();
        } catch { toast.error("Failed"); }
    };

    const handleDownload = async (doc: Doc) => {
        try {
            const { data } = await api.get(`/documents/${doc.id}/download`, { responseType: "blob" });
            const url = URL.createObjectURL(data); const a = document.createElement("a");
            a.href = url; a.download = doc.originalFileName || doc.fileName; a.click(); URL.revokeObjectURL(url);
        } catch { toast.error("Download failed"); }
    };

    const toggleSort = (field: string) => {
        if (sortField === field) setSortDir(d => d === "asc" ? "desc" : "asc");
        else { setSortField(field); setSortDir(field === "createdAt" ? "desc" : "asc"); }
    };

    const sortedDocs = [...docs].sort((a, b) => {
        let av: string | number = "", bv: string | number = "";
        switch (sortField) {
            case "name": av = a.originalFileName?.toLowerCase() ?? ""; bv = b.originalFileName?.toLowerCase() ?? ""; break;
            case "status": av = a.status; bv = b.status; break;
            case "category": av = a.categoryName ?? ""; bv = b.categoryName ?? ""; break;
            case "sensitivity": av = a.sensitivityLabel ?? ""; bv = b.sensitivityLabel ?? ""; break;
            case "size": av = a.fileSizeBytes ?? 0; bv = b.fileSizeBytes ?? 0; break;
            case "createdAt": av = a.createdAt; bv = b.createdAt; break;
        }
        const cmp = av < bv ? -1 : av > bv ? 1 : 0;
        return sortDir === "asc" ? cmp : -cmp;
    });

    const roots = categories.filter(c => !c.parentId && c.active);
    const childrenOf = (pid: string) => categories.filter(c => c.parentId === pid && c.active);
    const toggleSelect = (id: string) => setSelected(prev => { const n = new Set(prev); if (n.has(id)) n.delete(id); else n.add(id); return n; });
    const allSelected = docs.length > 0 && selected.size === docs.length;
    const selectAll = () => setSelected(allSelected ? new Set() : new Set(docs.map(d => d.id)));

    const [viewerTab, setViewerTab] = useState<"viewer" | "audit">("viewer");

    // If viewing a document, show the viewer + classification panel
    if (viewingDoc) {
        return (
            <div className="flex h-[calc(100vh-120px)] -m-6">
                <div className="flex-1 min-w-0 flex flex-col bg-gray-50">
                    <div className="flex items-center gap-3 px-4 py-2 bg-white border-b border-gray-200 shrink-0">
                        <button onClick={() => { closedByUser.current = true; setViewingDoc(null); router.replace("/documents", { scroll: false }); }}
                            className="text-sm text-blue-600 hover:text-blue-800">&larr; Back to list</button>
                        <span className="text-sm font-medium text-gray-900 truncate flex-1">
                            {viewingDoc.originalFileName || viewingDoc.fileName}
                        </span>
                        <div className="flex border border-gray-200 rounded-md overflow-hidden">
                            <button onClick={() => setViewerTab("viewer")}
                                className={`px-3 py-1 text-xs font-medium ${viewerTab === "viewer" ? "bg-blue-600 text-white" : "bg-white text-gray-600 hover:bg-gray-50"}`}>
                                Document
                            </button>
                            <button onClick={() => setViewerTab("audit")}
                                className={`px-3 py-1 text-xs font-medium border-l border-gray-200 ${viewerTab === "audit" ? "bg-blue-600 text-white" : "bg-white text-gray-600 hover:bg-gray-50"}`}>
                                Audit Trail
                            </button>
                        </div>
                    </div>
                    <div className="flex-1 overflow-hidden">
                        {viewerTab === "viewer" ? (
                            <DocumentViewer
                                documentId={viewingDoc.id} mimeType={viewingDoc.mimeType}
                                fileName={viewingDoc.originalFileName || viewingDoc.fileName}
                                extractedText={viewingDoc.extractedText}
                                storageProvider={viewingDoc.storageProvider}
                                externalStorageRef={viewingDoc.externalStorageRef}
                                onFlagPii={(text) => setPiiSelectedText(text)}
                            />
                        ) : (
                            <DocumentAuditTrail documentId={viewingDoc.id} />
                        )}
                    </div>
                </div>
                <ClassificationPanel
                    doc={viewingDoc} onClose={() => { closedByUser.current = true; setViewingDoc(null); router.replace("/documents", { scroll: false }); }}
                    onDownload={handleDownload} onReprocess={handleReprocess}
                    onDocUpdated={(updated) => { setSelectedDoc(updated); setViewingDoc(updated); loadDocs(); }}
                />
                {piiSelectedText && (
                    <PiiQuickFlag documentId={viewingDoc.id} selectedText={piiSelectedText}
                        piiTypes={piiTypes} onClose={() => setPiiSelectedText(null)} />
                )}
            </div>
        );
    }

    return (
        <div className="flex h-[calc(100vh-120px)] -m-6">
            {/* Category tree sidebar */}
            <div className="w-56 shrink-0 bg-white border-r border-gray-200 flex flex-col">
                <div className="px-3 py-2 border-b border-gray-100 shrink-0">
                    <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Categories</span>
                </div>
                <div className="flex-1 overflow-y-auto py-1">
                    <button onClick={() => setCurrentCategory(null)}
                        className={`w-full text-left text-sm font-medium px-4 py-1.5 ${!currentCategory ? "bg-blue-50 text-blue-700" : "text-gray-700 hover:bg-gray-50"}`}>
                        <div className="flex items-center gap-2"><Inbox className="size-3.5" /> All Documents</div>
                    </button>
                    {roots.map(cat => (
                        <CatNode key={cat.id} cat={cat} depth={0} childrenOf={childrenOf}
                            currentId={currentCategory?.id} expanded={expandedCats}
                            onSelect={(c) => setCurrentCategory({ id: c.id, name: c.name })}
                            onToggle={(id) => setExpandedCats(prev => { const n = new Set(prev); if (n.has(id)) n.delete(id); else n.add(id); return n; })} />
                    ))}
                </div>
            </div>

            {/* Document table */}
            <div className="flex-1 bg-white flex flex-col overflow-hidden">
                <div className="flex items-center gap-3 px-4 py-2.5 border-b border-gray-200 bg-gray-50 shrink-0">
                    <div className="flex-1 relative">
                        <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 size-3.5 text-gray-400" />
                        <input
                            id="doc-search"
                            type="search"
                            value={searchQuery}
                            onChange={e => setSearchQuery(e.target.value)}
                            onKeyDown={e => { if (e.key === "Enter") loadDocs(); }}
                            placeholder="Search documents by name, content, or metadata..."
                            className="w-full text-sm border border-gray-300 rounded-md pl-8 pr-3 py-1.5 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                            aria-label="Search documents"
                        />
                    </div>
                    <span className="text-xs text-gray-400 shrink-0">
                        {currentCategory ? currentCategory.name : "All"} ({docs.length})
                    </span>
                    <Link href="/search" className="text-xs text-blue-600 hover:text-blue-800 shrink-0">Advanced</Link>
                    <button onClick={() => loadDocs()} disabled={loading} className="p-1.5 text-gray-400 hover:text-gray-600" aria-label="Refresh">
                        <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
                    </button>
                </div>

                <div className="flex-1 overflow-auto">
                    {loading ? (
                        <SkeletonTable rows={8} cols={5} />
                    ) : docs.length === 0 ? (
                        <EmptyState
                            icon={searchQuery ? Search : (currentCategory ? FolderOpen : FileText)}
                            title={searchQuery ? "No results found" : (currentCategory ? "No documents in this category" : "No documents yet")}
                            description={searchQuery
                                ? "Try different keywords or clear the search."
                                : currentCategory
                                    ? "Documents classified into this category will appear here."
                                    : "Upload files through Drives to get started with AI classification."}
                            action={!searchQuery && !currentCategory ? "Go to Drives" : undefined}
                            href={!searchQuery && !currentCategory ? "/drives" : undefined}
                        />
                    ) : (
                        <table className="w-full text-sm">
                            <thead className="sticky top-0 bg-gray-50 z-10">
                                <tr className="border-b border-gray-200">
                                    {[
                                        { key: "name", label: "Name", width: undefined },
                                        { key: "status", label: "Status", width: 128 },
                                        { key: "category", label: "Category", width: 144 },
                                        { key: "sensitivity", label: "Sensitivity", width: 112 },
                                        { key: "size", label: "Size", width: 96 },
                                        { key: "createdAt", label: "Date", width: 112 },
                                    ].map(col => (
                                        <ResizableTh key={col.key}
                                            onClick={() => toggleSort(col.key)}
                                            initialWidth={col.width}
                                            className={`text-left px-3 py-2 font-medium text-gray-600 cursor-pointer hover:text-gray-900 ${col.key === "name" ? "pl-4" : ""}`}>
                                            <span className="inline-flex items-center gap-1">
                                                {col.label}
                                                {sortField === col.key && (
                                                    <span className="text-blue-500 text-[10px]">{sortDir === "asc" ? "\u25B2" : "\u25BC"}</span>
                                                )}
                                            </span>
                                        </ResizableTh>
                                    ))}
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                                {sortedDocs.map(doc => {
                                    const Icon = fileIcon(doc.mimeType);
                                    const statusInfo = STATUS_ICONS[doc.status] ?? { icon: Clock, color: "text-gray-400" };
                                    const StatusIcon = statusInfo.icon;
                                    const isActive = selectedDoc?.id === doc.id;
                                    return (
                                        <tr key={doc.id} onClick={() => handleDocClick(doc)}
                                            className={`cursor-pointer hover:bg-gray-50 ${isActive ? "bg-blue-50" : ""}`}>
                                            <td className="px-4 py-2.5">
                                                <div className="flex items-center gap-2">
                                                    <Icon className="size-4 text-gray-400 shrink-0" />
                                                    <span className="text-gray-900 truncate">{doc.originalFileName || doc.fileName}</span>
                                                    {doc.storageProvider === "GOOGLE_DRIVE" && (
                                                        <span className="px-1 py-0.5 text-[9px] bg-blue-50 text-blue-600 rounded">Drive</span>
                                                    )}
                                                </div>
                                            </td>
                                            <td className="px-3 py-2.5">
                                                <div className="flex items-center gap-1.5">
                                                    <StatusIcon className={`size-3.5 ${statusInfo.color} ${doc.status.includes("ING") && !doc.status.includes("FAIL") ? "animate-spin" : ""}`} />
                                                    <span className="text-[10px] text-gray-600">{doc.status.replace(/_/g, " ")}</span>
                                                </div>
                                                {doc.status.includes("FAILED") && doc.lastError && (
                                                    <div className="mt-0.5 text-[9px] text-red-600 truncate max-w-[200px]" title={doc.lastError}>
                                                        {doc.lastErrorStage ? `[${doc.lastErrorStage}] ` : ""}{doc.lastError.split("\n")[0]}
                                                    </div>
                                                )}
                                            </td>
                                            <td className="px-3 py-2.5 text-xs text-gray-600 truncate">{doc.categoryName || "—"}</td>
                                            <td className="px-3 py-2.5">
                                                {doc.sensitivityLabel ? (
                                                    <span className={`px-1.5 py-0.5 text-[10px] font-medium rounded-full ${SENSITIVITY_COLORS[doc.sensitivityLabel] ?? ""}`}>
                                                        {doc.sensitivityLabel}
                                                    </span>
                                                ) : <span className="text-[10px] text-gray-400">—</span>}
                                            </td>
                                            <td className="px-3 py-2.5 text-xs text-gray-500">{formatSize(doc.fileSizeBytes)}</td>
                                            <td className="px-3 py-2.5 text-xs text-gray-500">{new Date(doc.createdAt).toLocaleDateString()}</td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    )}
                </div>
            </div>
        </div>
    );
}

/* ── Category Tree Node ───────────────────────────── */

function CatNode({ cat, depth, childrenOf, currentId, expanded, onSelect, onToggle }: {
    cat: Category; depth: number; childrenOf: (pid: string) => Category[];
    currentId?: string; expanded: Set<string>;
    onSelect: (cat: Category) => void; onToggle: (id: string) => void;
}) {
    const children = childrenOf(cat.id);
    const hasChildren = children.length > 0;
    const isOpen = expanded.has(cat.id);
    const isActive = currentId === cat.id;

    return (
        <>
            <button onClick={() => onSelect(cat)}
                className={`w-full flex items-center gap-1 text-left text-xs py-1.5 hover:bg-gray-50 ${isActive ? "bg-blue-50 text-blue-700" : "text-gray-700"}`}
                style={{ paddingLeft: `${depth * 16 + 16}px` }}>
                {hasChildren ? (
                    <span onClick={(e) => { e.stopPropagation(); onToggle(cat.id); }} className="p-0.5 shrink-0">
                        {isOpen ? <ChevronDown className="size-3 text-gray-400" /> : <ChevronRight className="size-3 text-gray-400" />}
                    </span>
                ) : <span className="w-4" />}
                {isOpen ? <FolderOpen className="size-3.5 text-amber-500 shrink-0" /> : <FolderClosed className="size-3.5 text-amber-400 shrink-0" />}
                <span className="truncate">{cat.name}</span>
            </button>
            {isOpen && children.map(c => (
                <CatNode key={c.id} cat={c} depth={depth + 1} childrenOf={childrenOf}
                    currentId={currentId} expanded={expanded} onSelect={onSelect} onToggle={onToggle} />
            ))}
        </>
    );
}

/* ── PII Quick Flag ───────────────────────────────── */

function PiiQuickFlag({ documentId, selectedText, piiTypes, onClose }: {
    documentId: string; selectedText: string; piiTypes: { key: string; displayName: string; category: string }[]; onClose: () => void;
}) {
    const [type, setType] = useState("");
    const [customType, setCustomType] = useState("");
    const [context, setContext] = useState("");
    const [saving, setSaving] = useState(false);
    const isCustom = type === "__custom__";
    const resolvedType = isCustom ? customType.toUpperCase().replace(/[^A-Z0-9_]/g, "_") : type;

    const handleSave = async () => {
        if (!resolvedType) { toast.error("Select a PII type"); return; }
        if (isCustom && customType.trim()) {
            try { await api.post("/documents/pii-types/suggest", { key: resolvedType, displayName: customType.trim(), description: "User-submitted", category: "other", examples: [selectedText.slice(0, 100)] }); } catch {}
        }
        setSaving(true);
        try {
            await api.post(`/review/${documentId}/report-pii`, { piiItems: [{ type: resolvedType, description: selectedText, context: context || "Selected from document text" }] });
            toast.success(isCustom ? "PII flagged — custom type submitted for approval" : "PII flagged");
            onClose();
        } catch { toast.error("Failed"); }
        finally { setSaving(false); }
    };

    const grouped = piiTypes.reduce((acc, t) => { (acc[t.category] ??= []).push(t); return acc; }, {} as Record<string, typeof piiTypes>);

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onClose}>
            <div className="bg-white rounded-lg shadow-xl w-full max-w-sm p-5 space-y-4" onClick={e => e.stopPropagation()}>
                <h3 className="text-sm font-semibold text-gray-900">Flag Selected Text as PII</h3>
                <div className="bg-purple-50 rounded-md p-3 border border-purple-100">
                    <p className="text-sm font-mono text-purple-800 break-all">{selectedText.length > 200 ? selectedText.slice(0, 200) + "..." : selectedText}</p>
                </div>
                <select value={type} onChange={e => setType(e.target.value)} className="w-full text-sm border border-gray-300 rounded-md px-3 py-2">
                    <option value="">Select type...</option>
                    {Object.entries(grouped).map(([cat, types]) => (
                        <optgroup key={cat} label={cat.charAt(0).toUpperCase() + cat.slice(1)}>
                            {types.map(t => <option key={t.key} value={t.key}>{t.displayName}</option>)}
                        </optgroup>
                    ))}
                    <option value="__custom__">Other...</option>
                </select>
                {isCustom && <input type="text" value={customType} onChange={e => setCustomType(e.target.value)} placeholder="Custom type name" className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />}
                <input type="text" value={context} onChange={e => setContext(e.target.value)} placeholder="Context (optional)" className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                <div className="flex gap-2 justify-end">
                    <button onClick={onClose} className="px-3 py-2 text-sm text-gray-600 border rounded-md hover:bg-gray-50">Cancel</button>
                    <button onClick={handleSave} disabled={saving} className="px-3 py-2 text-sm font-medium text-white bg-purple-600 rounded-md hover:bg-purple-700 disabled:opacity-50">{saving ? "..." : "Flag PII"}</button>
                </div>
            </div>
        </div>
    );
}
