"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ZoomIn, ZoomOut, RotateCw, FileText, FileImage, Eye, ShieldAlert } from "lucide-react";

type ViewerProps = {
    documentId: string;
    mimeType: string;
    fileName: string;
    extractedText?: string;
    storageProvider?: string;
    externalStorageRef?: Record<string, string>;
    onFlagPii?: (selectedText: string) => void;
};

const OFFICE_TYPES = [
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/msword",
    "application/vnd.ms-excel",
    "application/vnd.ms-powerpoint",
    "application/rtf",
];

function isOfficeDoc(mimeType: string): boolean {
    return OFFICE_TYPES.some((t) => mimeType?.includes(t));
}

function canPreview(mimeType: string): boolean {
    if (!mimeType) return false;
    if (mimeType === "application/pdf") return true;
    if (mimeType.startsWith("image/")) return true;
    if (mimeType.startsWith("text/")) return true;
    if (mimeType.includes("json") || mimeType.includes("xml")) return true;
    if (isOfficeDoc(mimeType)) return true;
    return false;
}

export default function DocumentViewer({ documentId, mimeType, fileName, extractedText, storageProvider, externalStorageRef, onFlagPii }: ViewerProps) {
    const [zoom, setZoom] = useState(100);
    const [tab, setTab] = useState<"preview" | "text">(canPreview(mimeType) ? "preview" : "text");
    const [selection, setSelection] = useState<{ text: string; x: number; y: number } | null>(null);
    const textRef = useRef<HTMLPreElement>(null);

    const handleTextMouseUp = useCallback(() => {
        const sel = window.getSelection();
        const text = sel?.toString().trim();
        if (!text || text.length < 2) {
            setSelection(null);
            return;
        }
        const range = sel?.getRangeAt(0);
        if (!range) return;
        const rect = range.getBoundingClientRect();
        setSelection({ text, x: rect.left + rect.width / 2, y: rect.top - 8 });
    }, []);

    // Dismiss popup on click outside
    useEffect(() => {
        const dismiss = (e: MouseEvent) => {
            const target = e.target as HTMLElement;
            if (!target.closest("[data-pii-popup]")) {
                // Small delay to let the flag button click register
                setTimeout(() => setSelection(null), 150);
            }
        };
        document.addEventListener("mousedown", dismiss);
        return () => document.removeEventListener("mousedown", dismiss);
    }, []);

    const isGoogleDrive = storageProvider === "GOOGLE_DRIVE";
    const driveId = externalStorageRef?.driveId;
    const fileId = externalStorageRef?.fileId;

    const previewUrl = isGoogleDrive && driveId && fileId
        ? `/api/proxy/drives/${driveId}/content/${fileId}`
        : isOfficeDoc(mimeType)
            ? `/api/proxy/documents/${documentId}/preview-html`
            : `/api/proxy/documents/${documentId}/preview`;

    return (
        <div className="flex flex-col h-full bg-white rounded-lg border border-gray-200 overflow-hidden">
            {/* Toolbar */}
            <div className="flex items-center justify-between px-4 py-2 border-b border-gray-200 bg-gray-50 shrink-0">
                <div className="flex items-center gap-1">
                    <button
                        onClick={() => setTab("preview")}
                        disabled={!canPreview(mimeType)}
                        className={`inline-flex items-center gap-1.5 px-3 py-1 text-xs font-medium rounded-md transition-colors ${
                            tab === "preview" ? "bg-white shadow-sm text-gray-900" : "text-gray-500 hover:text-gray-700"
                        } disabled:opacity-30`}
                    >
                        <Eye className="size-3.5" />
                        Preview
                    </button>
                    <button
                        onClick={() => setTab("text")}
                        disabled={!extractedText}
                        className={`inline-flex items-center gap-1.5 px-3 py-1 text-xs font-medium rounded-md transition-colors ${
                            tab === "text" ? "bg-white shadow-sm text-gray-900" : "text-gray-500 hover:text-gray-700"
                        } disabled:opacity-30`}
                    >
                        <FileText className="size-3.5" />
                        Text
                    </button>
                </div>

                {tab === "preview" && canPreview(mimeType) && (
                    <div className="flex items-center gap-2">
                        <button onClick={() => setZoom(Math.max(25, zoom - 25))} className="p-1 text-gray-400 hover:text-gray-600 rounded">
                            <ZoomOut className="size-4" />
                        </button>
                        <span className="text-xs text-gray-500 w-10 text-center">{zoom}%</span>
                        <button onClick={() => setZoom(Math.min(200, zoom + 25))} className="p-1 text-gray-400 hover:text-gray-600 rounded">
                            <ZoomIn className="size-4" />
                        </button>
                        <button onClick={() => setZoom(100)} className="p-1 text-gray-400 hover:text-gray-600 rounded" title="Reset zoom">
                            <RotateCw className="size-4" />
                        </button>
                    </div>
                )}
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto bg-gray-100">
                {tab === "preview" && canPreview(mimeType) && (
                    <PreviewContent url={previewUrl} mimeType={mimeType} zoom={zoom} fileName={fileName} />
                )}
                {tab === "text" && extractedText && (
                    <div className="p-6 max-w-3xl mx-auto relative">
                        <pre ref={textRef} onMouseUp={handleTextMouseUp}
                            className="whitespace-pre-wrap text-sm text-gray-700 font-mono leading-relaxed bg-white rounded-lg p-6 border border-gray-200 shadow-sm select-text cursor-text">
                            {extractedText}
                        </pre>
                        {selection && onFlagPii && (
                            <div data-pii-popup
                                className="fixed z-50 -translate-x-1/2 -translate-y-full"
                                style={{ left: selection.x, top: selection.y }}>
                                <button
                                    onClick={() => { onFlagPii(selection.text); setSelection(null); }}
                                    className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-purple-600 text-white text-xs font-medium rounded-lg shadow-lg hover:bg-purple-700 transition-colors"
                                >
                                    <ShieldAlert className="size-3.5" />
                                    Flag as PII
                                </button>
                                <div className="w-3 h-3 bg-purple-600 rotate-45 mx-auto -mt-1.5" />
                            </div>
                        )}
                    </div>
                )}
                {tab === "preview" && !canPreview(mimeType) && (
                    <NoPreview fileName={fileName} />
                )}
                {tab === "text" && !extractedText && (
                    <div className="flex items-center justify-center h-full text-gray-400 text-sm">
                        No extracted text available
                    </div>
                )}
            </div>
        </div>
    );
}

function PreviewContent({ url, mimeType, zoom, fileName }: { url: string; mimeType: string; zoom: number; fileName: string }) {
    if (mimeType === "application/pdf") {
        return (
            <div className="h-full flex items-center justify-center p-4">
                <iframe
                    src={url}
                    className="bg-white shadow-lg rounded"
                    style={{ width: `${zoom}%`, height: "100%", minHeight: "600px" }}
                    title={fileName}
                />
            </div>
        );
    }

    if (mimeType.startsWith("image/")) {
        return (
            <div className="flex items-center justify-center p-4 h-full">
                <img
                    src={url}
                    alt={fileName}
                    className="max-h-full rounded shadow-lg object-contain"
                    style={{ width: `${zoom}%` }}
                />
            </div>
        );
    }

    // Office documents rendered as HTML, or text/json/xml
    if (isOfficeDoc(mimeType) || mimeType.startsWith("text/") || mimeType.includes("json") || mimeType.includes("xml")) {
        return (
            <div className="h-full p-4">
                <iframe
                    src={url}
                    className="w-full h-full bg-white rounded-lg border border-gray-200 shadow-sm"
                    style={{ minHeight: "600px" }}
                    title={fileName}
                />
            </div>
        );
    }

    return <NoPreview fileName={fileName} />;
}

function NoPreview({ fileName }: { fileName: string }) {
    return (
        <div className="flex flex-col items-center justify-center h-full text-gray-400 gap-3">
            <FileImage className="size-16 text-gray-200" />
            <p className="text-sm">Preview not available for this file type</p>
            <p className="text-xs text-gray-300">{fileName}</p>
        </div>
    );
}
