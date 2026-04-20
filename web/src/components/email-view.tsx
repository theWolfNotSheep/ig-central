"use client";

import { useCallback, useEffect, useState } from "react";
import { Mail, Paperclip, ExternalLink, FileText, Loader2, ArrowLeft } from "lucide-react";
import api from "@/lib/axios/axios.client";
import Link from "next/link";

type EmailViewProps = {
    document: {
        id: string;
        mimeType: string;
        extractedText?: string;
        externalStorageRef?: Record<string, string>;
        parentDocumentId?: string;
        childDocumentIds?: string[];
    };
};

type ChildDoc = {
    id: string;
    slug: string;
    originalFileName: string;
    mimeType: string;
    status: string;
    categoryName?: string;
    sensitivityLabel?: string;
};

const SENSITIVITY_COLORS: Record<string, string> = {
    PUBLIC: "bg-green-100 text-green-700",
    INTERNAL: "bg-blue-100 text-blue-700",
    CONFIDENTIAL: "bg-amber-100 text-amber-700",
    RESTRICTED: "bg-red-100 text-red-700",
};

const STATUS_COLORS: Record<string, string> = {
    UPLOADED: "bg-gray-100 text-gray-600",
    PROCESSING: "bg-blue-100 text-blue-600",
    CLASSIFIED: "bg-green-100 text-green-700",
    GOVERNANCE_APPLIED: "bg-green-100 text-green-700",
    PROCESSING_FAILED: "bg-red-100 text-red-600",
    CLASSIFICATION_FAILED: "bg-red-100 text-red-600",
};

export default function EmailView({ document: doc }: EmailViewProps) {
    const [children, setChildren] = useState<ChildDoc[]>([]);
    const [parentDoc, setParentDoc] = useState<ChildDoc | null>(null);
    const [loading, setLoading] = useState(false);

    const isEmail = doc.mimeType === "message/rfc822";
    const isAttachment = !!doc.parentDocumentId;
    const ref = doc.externalStorageRef || {};

    // Load child documents (attachments)
    const loadChildren = useCallback(async () => {
        if (!doc.childDocumentIds || doc.childDocumentIds.length === 0) return;
        setLoading(true);
        try {
            const results: ChildDoc[] = [];
            for (const childId of doc.childDocumentIds) {
                try {
                    const { data } = await api.get(`/documents/${childId}`);
                    results.push({
                        id: data.id,
                        slug: data.slug,
                        originalFileName: data.originalFileName,
                        mimeType: data.mimeType,
                        status: data.status,
                        categoryName: data.categoryName,
                        sensitivityLabel: data.sensitivityLabel,
                    });
                } catch { /* skip unavailable */ }
            }
            setChildren(results);
        } finally {
            setLoading(false);
        }
    }, [doc.childDocumentIds]);

    // Load parent document info
    const loadParent = useCallback(async () => {
        if (!doc.parentDocumentId) return;
        try {
            const { data } = await api.get(`/documents/${doc.parentDocumentId}`);
            setParentDoc({
                id: data.id,
                slug: data.slug,
                originalFileName: data.originalFileName,
                mimeType: data.mimeType,
                status: data.status,
                categoryName: data.categoryName,
                sensitivityLabel: data.sensitivityLabel,
            });
        } catch { /* parent may be deleted */ }
    }, [doc.parentDocumentId]);

    useEffect(() => {
        if (isEmail) loadChildren();
        if (isAttachment) loadParent();
    }, [isEmail, isAttachment, loadChildren, loadParent]);

    if (!isEmail && !isAttachment) return null;

    return (
        <div className="border-b border-gray-200">
            {/* Attachment backlink */}
            {isAttachment && parentDoc && (
                <div className="px-4 py-2 bg-blue-50 border-b border-blue-200 flex items-center gap-2">
                    <ArrowLeft className="size-3.5 text-blue-500 shrink-0" />
                    <span className="text-xs text-blue-700">
                        Attached to:{" "}
                        <Link href={`/documents?doc=${parentDoc.slug}`}
                            className="font-medium underline hover:text-blue-800">
                            {parentDoc.originalFileName}
                        </Link>
                    </span>
                </div>
            )}

            {/* Email header */}
            {isEmail && (
                <div className="px-4 py-3 bg-gray-50">
                    <div className="flex items-start gap-3">
                        <div className="p-2 bg-blue-100 rounded-lg shrink-0">
                            <Mail className="size-5 text-blue-600" />
                        </div>
                        <div className="flex-1 min-w-0">
                            <div className="grid grid-cols-[auto,1fr] gap-x-3 gap-y-1 text-sm">
                                {ref.from && (
                                    <>
                                        <span className="text-gray-500 font-medium">From</span>
                                        <span className="text-gray-900 truncate">{ref.from}</span>
                                    </>
                                )}
                                {ref.to && (
                                    <>
                                        <span className="text-gray-500 font-medium">To</span>
                                        <span className="text-gray-900 truncate">{ref.to}</span>
                                    </>
                                )}
                                {ref.subject && (
                                    <>
                                        <span className="text-gray-500 font-medium">Subject</span>
                                        <span className="text-gray-900 font-medium">{ref.subject}</span>
                                    </>
                                )}
                                {ref.date && (
                                    <>
                                        <span className="text-gray-500 font-medium">Date</span>
                                        <span className="text-gray-900">{ref.date}</span>
                                    </>
                                )}
                            </div>
                            {ref.gmailUrl && (
                                <a href={ref.gmailUrl} target="_blank" rel="noopener noreferrer"
                                    className="inline-flex items-center gap-1 mt-2 text-xs text-blue-600 hover:text-blue-700">
                                    <ExternalLink className="size-3" /> Open in Gmail
                                </a>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* Attachments list */}
            {isEmail && (doc.childDocumentIds?.length ?? 0) > 0 && (
                <div className="px-4 py-2 border-t border-gray-200">
                    <div className="flex items-center gap-1.5 mb-2">
                        <Paperclip className="size-3.5 text-gray-400" />
                        <span className="text-xs font-medium text-gray-600">
                            {doc.childDocumentIds?.length} Attachment{(doc.childDocumentIds?.length ?? 0) > 1 ? "s" : ""}
                        </span>
                    </div>
                    {loading ? (
                        <div className="flex items-center gap-2 py-2">
                            <Loader2 className="size-4 animate-spin text-gray-300" />
                            <span className="text-xs text-gray-400">Loading attachments...</span>
                        </div>
                    ) : (
                        <div className="space-y-1">
                            {children.map(child => (
                                <Link key={child.id} href={`/documents?doc=${child.slug}`}
                                    className="flex items-center gap-2 px-2 py-1.5 rounded hover:bg-gray-50 transition-colors group">
                                    <FileText className="size-4 text-gray-400 group-hover:text-blue-500 shrink-0" />
                                    <span className="text-sm text-gray-700 group-hover:text-blue-600 truncate flex-1">
                                        {child.originalFileName}
                                    </span>
                                    {child.categoryName && (
                                        <span className="text-[10px] text-gray-500 shrink-0">{child.categoryName}</span>
                                    )}
                                    {child.sensitivityLabel && (
                                        <span className={`inline-flex px-1.5 py-0.5 text-[9px] font-medium rounded-full shrink-0 ${SENSITIVITY_COLORS[child.sensitivityLabel] || ""}`}>
                                            {child.sensitivityLabel}
                                        </span>
                                    )}
                                    <span className={`inline-flex px-1.5 py-0.5 text-[9px] font-medium rounded-full shrink-0 ${STATUS_COLORS[child.status] || "bg-gray-100 text-gray-600"}`}>
                                        {child.status?.replace(/_/g, " ")}
                                    </span>
                                </Link>
                            ))}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
