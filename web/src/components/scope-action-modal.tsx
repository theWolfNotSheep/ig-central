"use client";

import { useState } from "react";
import { X, FileText, FolderOpen, Layers } from "lucide-react";

export type ActionScope = "document" | "type" | "category";

type ScopeActionModalProps = {
    title: string;
    description: string;
    fieldName: string;
    categoryName?: string;
    mimeType?: string;
    onConfirm: (scope: ActionScope, reason: string) => void;
    onClose: () => void;
};

export default function ScopeActionModal({
    title, description, fieldName, categoryName, mimeType, onConfirm, onClose,
}: ScopeActionModalProps) {
    const [scope, setScope] = useState<ActionScope>("document");
    const [reason, setReason] = useState("");

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-[60]" onClick={onClose}>
            <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6 space-y-4" onClick={e => e.stopPropagation()}>
                <div className="flex items-start justify-between">
                    <div>
                        <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
                        <p className="text-sm text-gray-500 mt-0.5">{description}</p>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X className="size-5" /></button>
                </div>

                <div className="bg-gray-50 rounded-md p-3 border border-gray-200">
                    <span className="text-xs text-gray-500">Field: </span>
                    <span className="text-sm font-medium text-gray-900">{fieldName.replace(/_/g, " ")}</span>
                </div>

                <div>
                    <label className="text-xs font-medium text-gray-700 block mb-2">Apply to:</label>
                    <div className="space-y-2">
                        <label className={`flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-colors ${
                            scope === "document" ? "border-blue-300 bg-blue-50" : "border-gray-200 hover:bg-gray-50"
                        }`}>
                            <input type="radio" name="scope" checked={scope === "document"}
                                onChange={() => setScope("document")} className="mt-0.5" />
                            <div>
                                <div className="flex items-center gap-1.5">
                                    <FileText className="size-3.5 text-gray-500" />
                                    <span className="text-sm font-medium text-gray-900">This document only</span>
                                </div>
                                <p className="text-xs text-gray-500 mt-0.5">Only affects this specific document. Other documents are unchanged.</p>
                            </div>
                        </label>

                        <label className={`flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-colors ${
                            scope === "type" ? "border-blue-300 bg-blue-50" : "border-gray-200 hover:bg-gray-50"
                        }`}>
                            <input type="radio" name="scope" checked={scope === "type"}
                                onChange={() => setScope("type")} className="mt-0.5" />
                            <div>
                                <div className="flex items-center gap-1.5">
                                    <Layers className="size-3.5 text-gray-500" />
                                    <span className="text-sm font-medium text-gray-900">All documents of this type</span>
                                </div>
                                <p className="text-xs text-gray-500 mt-0.5">
                                    Applies to all <strong>{mimeType ?? "this file type"}</strong> documents.
                                    The AI will be instructed not to extract this field for this file type.
                                </p>
                            </div>
                        </label>

                        <label className={`flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-colors ${
                            scope === "category" ? "border-blue-300 bg-blue-50" : "border-gray-200 hover:bg-gray-50"
                        }`}>
                            <input type="radio" name="scope" checked={scope === "category"}
                                onChange={() => setScope("category")} className="mt-0.5" />
                            <div>
                                <div className="flex items-center gap-1.5">
                                    <FolderOpen className="size-3.5 text-gray-500" />
                                    <span className="text-sm font-medium text-gray-900">All documents in this category</span>
                                </div>
                                <p className="text-xs text-gray-500 mt-0.5">
                                    Applies to all <strong>{categoryName ?? "this category"}</strong> documents.
                                    The field will be removed from the category&apos;s metadata schema.
                                </p>
                            </div>
                        </label>
                    </div>
                </div>

                <div>
                    <label className="text-xs font-medium text-gray-700 block mb-1">Reason (helps train the AI)</label>
                    <input type="text" value={reason} onChange={e => setReason(e.target.value)}
                        placeholder="e.g. This field is not relevant for this document type"
                        className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                </div>

                <div className="flex gap-3 justify-end pt-2 border-t border-gray-200">
                    <button onClick={onClose}
                        className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                    <button onClick={() => onConfirm(scope, reason)}
                        className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700">
                        Confirm
                    </button>
                </div>
            </div>
        </div>
    );
}
