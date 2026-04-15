"use client";

import { useCallback, useEffect, useState } from "react";
import {
    Inbox, CheckCircle, Loader2, RefreshCw, FolderInput,
    FileText, ArrowRight,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import FilingDialog from "@/components/filing-dialog";

type InboxDoc = {
    id: string;
    originalFileName: string;
    categoryName?: string;
    sensitivityLabel?: string;
    mimeType?: string;
    fileSizeBytes?: number;
    governanceAppliedAt?: string;
    updatedAt?: string;
    slug?: string;
};

const SENSITIVITY_COLORS: Record<string, string> = {
    PUBLIC: "bg-green-100 text-green-700",
    INTERNAL: "bg-blue-100 text-blue-700",
    CONFIDENTIAL: "bg-amber-100 text-amber-700",
    RESTRICTED: "bg-red-100 text-red-700",
};

export default function InboxPage() {
    const [docs, setDocs] = useState<InboxDoc[]>([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [filingDoc, setFilingDoc] = useState<InboxDoc | null>(null);

    const loadInbox = useCallback(async () => {
        setLoading(true);
        try {
            const { data } = await api.get(`/filing/inbox?page=${page}&size=20&sort=updatedAt,desc`);
            setDocs(data.content ?? []);
            setTotalPages(data.totalPages ?? 0);
            setTotalElements(data.totalElements ?? 0);
        } catch {
            toast.error("Failed to load inbox");
        } finally {
            setLoading(false);
        }
    }, [page]);

    useEffect(() => { loadInbox(); }, [loadInbox]);

    const handleFiled = () => {
        setFilingDoc(null);
        loadInbox();
    };

    const formatSize = (bytes?: number) => {
        if (!bytes) return "—";
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    };

    const formatDate = (date?: string) => {
        if (!date) return "—";
        return new Date(date).toLocaleDateString("en-GB", {
            day: "numeric", month: "short", year: "numeric",
            hour: "2-digit", minute: "2-digit",
        });
    };

    return (
        <>
            {/* Header */}
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2">
                        <Inbox className="size-5 text-blue-500" />
                        Inbox
                        {totalElements > 0 && (
                            <span className="inline-flex items-center justify-center min-w-6 h-6 text-xs font-bold bg-blue-500 text-white rounded-full px-1.5">
                                {totalElements}
                            </span>
                        )}
                    </h2>
                    <p className="text-sm text-gray-500 mt-1">
                        Documents processed and waiting to be filed to a permanent location.
                    </p>
                </div>
                <button onClick={loadInbox} disabled={loading}
                    className="inline-flex items-center gap-2 px-3 py-2 border border-gray-300 text-sm text-gray-700 rounded-lg hover:bg-gray-50 disabled:opacity-50">
                    <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
                    Refresh
                </button>
            </div>

            {/* Content */}
            {loading && docs.length === 0 ? (
                <div className="flex items-center justify-center py-16">
                    <Loader2 className="size-8 animate-spin text-gray-300" />
                </div>
            ) : docs.length === 0 ? (
                <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-12 text-center">
                    <CheckCircle className="size-12 text-green-200 mx-auto mb-3" />
                    <h3 className="text-base font-medium text-gray-700 mb-1">All caught up</h3>
                    <p className="text-sm text-gray-500">No documents waiting to be filed.</p>
                </div>
            ) : (
                <>
                    <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                        <table className="w-full text-sm">
                            <thead className="bg-gray-50 border-b border-gray-200">
                                <tr>
                                    <th className="text-left px-4 py-3 font-medium text-gray-600">Document</th>
                                    <th className="text-left px-3 py-3 font-medium text-gray-600 w-40">Category</th>
                                    <th className="text-left px-3 py-3 font-medium text-gray-600 w-32">Sensitivity</th>
                                    <th className="text-left px-3 py-3 font-medium text-gray-600 w-24">Size</th>
                                    <th className="text-left px-3 py-3 font-medium text-gray-600 w-40">Processed</th>
                                    <th className="text-left px-3 py-3 font-medium text-gray-600 w-28">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                                {docs.map(doc => (
                                    <tr key={doc.id} className="hover:bg-gray-50">
                                        <td className="px-4 py-3">
                                            <a href={`/documents?doc=${doc.slug || doc.id}`}
                                                className="flex items-center gap-2 text-sm text-gray-900 hover:text-blue-700">
                                                <FileText className="size-4 text-gray-400 shrink-0" />
                                                <span className="truncate max-w-64">{doc.originalFileName}</span>
                                            </a>
                                        </td>
                                        <td className="px-3 py-3">
                                            {doc.categoryName ? (
                                                <span className="text-xs text-gray-700">{doc.categoryName}</span>
                                            ) : <span className="text-xs text-gray-400">—</span>}
                                        </td>
                                        <td className="px-3 py-3">
                                            {doc.sensitivityLabel ? (
                                                <span className={`inline-flex px-2 py-0.5 text-[10px] font-medium rounded-full ${SENSITIVITY_COLORS[doc.sensitivityLabel] ?? "bg-gray-100 text-gray-600"}`}>
                                                    {doc.sensitivityLabel}
                                                </span>
                                            ) : <span className="text-xs text-gray-400">—</span>}
                                        </td>
                                        <td className="px-3 py-3 text-xs text-gray-500 tabular-nums">
                                            {formatSize(doc.fileSizeBytes)}
                                        </td>
                                        <td className="px-3 py-3 text-xs text-gray-500">
                                            {formatDate(doc.governanceAppliedAt || doc.updatedAt)}
                                        </td>
                                        <td className="px-3 py-3">
                                            <button onClick={() => setFilingDoc(doc)}
                                                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-blue-700 bg-blue-50 border border-blue-200 rounded-lg hover:bg-blue-100 transition-colors">
                                                <FolderInput className="size-3.5" />
                                                File
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {/* Pagination */}
                    {totalPages > 1 && (
                        <div className="flex items-center justify-between mt-4">
                            <span className="text-xs text-gray-500">
                                Page {page + 1} of {totalPages} ({totalElements} documents)
                            </span>
                            <div className="flex gap-2">
                                <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
                                    className="px-3 py-1.5 text-xs border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-50">
                                    Previous
                                </button>
                                <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
                                    className="px-3 py-1.5 text-xs border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-50">
                                    Next
                                </button>
                            </div>
                        </div>
                    )}
                </>
            )}

            {/* Filing Dialog */}
            {filingDoc && (
                <FilingDialog
                    document={filingDoc}
                    onFiled={handleFiled}
                    onClose={() => setFilingDoc(null)}
                />
            )}
        </>
    );
}
