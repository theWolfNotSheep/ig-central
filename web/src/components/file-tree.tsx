"use client";

import { useCallback, useEffect, useState } from "react";
import {
    Folder,
    FolderOpen,
    ChevronRight,
    ChevronDown,
    FileText,
    FileImage,
    FileSpreadsheet,
    File,
    Loader2,
    Clock,
    Inbox,
} from "lucide-react";
import api from "@/lib/axios/axios.client";
import { toast } from "sonner";

export type TreeDoc = {
    id: string;
    slug?: string;
    originalFileName: string;
    fileName: string;
    mimeType: string;
    fileSizeBytes: number;
    status: string;
    sensitivityLabel?: string;
    categoryName?: string;
    lastError?: string;
    lastErrorStage?: string;
};

type Category = {
    id: string;
    name: string;
    description: string;
    parentId?: string;
    defaultSensitivity: string;
    active: boolean;
};

function mimeIcon(mime: string) {
    if (mime?.startsWith("image/")) return FileImage;
    if (mime?.includes("spreadsheet") || mime?.includes("csv") || mime?.includes("excel")) return FileSpreadsheet;
    if (mime?.includes("pdf") || mime?.includes("word") || mime?.includes("text")) return FileText;
    return File;
}

const SENSITIVITY_DOT: Record<string, string> = {
    PUBLIC: "bg-green-500",
    INTERNAL: "bg-blue-500",
    CONFIDENTIAL: "bg-amber-500",
    RESTRICTED: "bg-red-500",
};

export default function FileTree({
    selectedDocId,
    onSelectDoc,
}: {
    selectedDocId?: string;
    onSelectDoc: (doc: TreeDoc) => void;
}) {
    const [categories, setCategories] = useState<Category[]>([]);
    const [unclassifiedDocs, setUnclassifiedDocs] = useState<TreeDoc[]>([]);
    const [expandedCats, setExpandedCats] = useState<Set<string>>(new Set());
    const [catDocs, setCatDocs] = useState<Map<string, TreeDoc[]>>(new Map());
    const [loadingCats, setLoadingCats] = useState<Set<string>>(new Set());
    const [showUnclassified, setShowUnclassified] = useState(true);

    const loadTree = useCallback(async () => {
        try {
            const [catRes, unclRes] = await Promise.all([
                api.get("/admin/governance/taxonomy"),
                api.get("/documents/unclassified"),
            ]);
            setCategories(catRes.data);
            setUnclassifiedDocs(unclRes.data);
        } catch {
            toast.error("Failed to load file tree");
        }
    }, []);

    useEffect(() => { loadTree(); }, [loadTree]);

    const roots = categories.filter((c) => !c.parentId && c.active);
    const childrenOf = (parentId: string) => categories.filter((c) => c.parentId === parentId && c.active);

    const toggleCategory = async (catId: string) => {
        if (expandedCats.has(catId)) {
            setExpandedCats((prev) => { const n = new Set(prev); n.delete(catId); return n; });
            return;
        }

        setExpandedCats((prev) => new Set(prev).add(catId));

        // Lazy load documents for this category
        if (!catDocs.has(catId)) {
            setLoadingCats((prev) => new Set(prev).add(catId));
            try {
                const cat = categories.find((c) => c.id === catId);
                const { data } = await api.get(`/documents/by-category/${catId}`, {
                    params: cat ? { name: cat.name } : undefined,
                });
                setCatDocs((prev) => new Map(prev).set(catId, data));
            } catch { /* ignore */ }
            setLoadingCats((prev) => { const n = new Set(prev); n.delete(catId); return n; });
        }
    };

    return (
        <div className="text-sm select-none py-1">
            {/* Unclassified / Processing */}
            {unclassifiedDocs.length > 0 && (
                <div className="mb-1">
                    <div
                        className="flex items-center gap-1.5 px-3 py-1.5 cursor-pointer hover:bg-gray-100 rounded-md mx-1"
                        onClick={() => setShowUnclassified(!showUnclassified)}
                    >
                        {showUnclassified ? <ChevronDown className="size-3.5 text-gray-400" /> : <ChevronRight className="size-3.5 text-gray-400" />}
                        <Inbox className="size-4 text-gray-500" />
                        <span className="text-gray-600 flex-1">Processing</span>
                        <span className="text-xs text-gray-400 bg-gray-100 px-1.5 rounded-full">{unclassifiedDocs.length}</span>
                    </div>
                    {showUnclassified && (
                        <div className="ml-1">
                            {unclassifiedDocs.map((doc) => (
                                <DocNode key={doc.id} doc={doc} depth={1} selected={selectedDocId === doc.id} onSelect={onSelectDoc} />
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* Divider */}
            {unclassifiedDocs.length > 0 && roots.length > 0 && (
                <div className="mx-3 my-1 border-t border-gray-100" />
            )}

            {/* Taxonomy categories */}
            {roots.map((cat) => (
                <CategoryNode
                    key={cat.id}
                    category={cat}
                    depth={0}
                    childrenOf={childrenOf}
                    expandedCats={expandedCats}
                    loadingCats={loadingCats}
                    catDocs={catDocs}
                    selectedDocId={selectedDocId}
                    onToggle={toggleCategory}
                    onSelectDoc={onSelectDoc}
                />
            ))}

            {roots.length === 0 && unclassifiedDocs.length === 0 && (
                <div className="px-3 py-6 text-xs text-gray-400 text-center">
                    No documents yet
                </div>
            )}
        </div>
    );
}

function CategoryNode({
    category, depth, childrenOf, expandedCats, loadingCats, catDocs,
    selectedDocId, onToggle, onSelectDoc,
}: {
    category: Category;
    depth: number;
    childrenOf: (parentId: string) => Category[];
    expandedCats: Set<string>;
    loadingCats: Set<string>;
    catDocs: Map<string, TreeDoc[]>;
    selectedDocId?: string;
    onToggle: (id: string) => void;
    onSelectDoc: (doc: TreeDoc) => void;
}) {
    const expanded = expandedCats.has(category.id);
    const loading = loadingCats.has(category.id);
    const children = childrenOf(category.id);
    const docs = catDocs.get(category.id) ?? [];
    const hasContent = children.length > 0 || docs.length > 0 || !catDocs.has(category.id);

    return (
        <div>
            <div
                className="flex items-center gap-1.5 px-3 py-1.5 cursor-pointer hover:bg-gray-100 rounded-md mx-1"
                style={{ paddingLeft: `${depth * 14 + 12}px` }}
                onClick={() => onToggle(category.id)}
            >
                {loading ? (
                    <Loader2 className="size-3.5 text-gray-400 animate-spin shrink-0" />
                ) : expanded ? (
                    <ChevronDown className="size-3.5 text-gray-400 shrink-0" />
                ) : (
                    <ChevronRight className="size-3.5 text-gray-400 shrink-0" />
                )}
                {expanded ? (
                    <FolderOpen className="size-4 text-blue-500 shrink-0" />
                ) : (
                    <Folder className="size-4 text-blue-500 shrink-0" />
                )}
                <span className="truncate text-gray-700 flex-1 text-xs font-medium">{category.name}</span>
                <span className={`size-2 rounded-full shrink-0 ${SENSITIVITY_DOT[category.defaultSensitivity] ?? "bg-gray-300"}`} />
            </div>

            {expanded && (
                <div>
                    {children.map((child) => (
                        <CategoryNode
                            key={child.id}
                            category={child}
                            depth={depth + 1}
                            childrenOf={childrenOf}
                            expandedCats={expandedCats}
                            loadingCats={loadingCats}
                            catDocs={catDocs}
                            selectedDocId={selectedDocId}
                            onToggle={onToggle}
                            onSelectDoc={onSelectDoc}
                        />
                    ))}
                    {docs.map((doc) => (
                        <DocNode key={doc.id} doc={doc} depth={depth + 1} selected={selectedDocId === doc.id} onSelect={onSelectDoc} />
                    ))}
                    {!loading && docs.length === 0 && children.length === 0 && (
                        <div className="text-xs text-gray-400 py-1" style={{ paddingLeft: `${(depth + 1) * 14 + 28}px` }}>
                            No documents
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

function DocNode({ doc, depth, selected, onSelect }: {
    doc: TreeDoc; depth: number; selected: boolean; onSelect: (doc: TreeDoc) => void;
}) {
    const Icon = mimeIcon(doc.mimeType);
    const isProcessing = ["UPLOADING", "UPLOADED", "PROCESSING", "PROCESSED", "CLASSIFYING"].includes(doc.status);

    return (
        <div
            className={`flex items-center gap-1.5 px-3 py-1 rounded-md cursor-pointer transition-colors mx-1 ${
                selected ? "bg-blue-50 text-blue-700" : "hover:bg-gray-50 text-gray-600"
            }`}
            style={{ paddingLeft: `${depth * 14 + 28}px` }}
            onClick={() => onSelect(doc)}
        >
            {isProcessing ? (
                <Loader2 className="size-3.5 shrink-0 text-amber-500 animate-spin" />
            ) : (
                <Icon className="size-3.5 shrink-0 text-gray-400" />
            )}
            <span className="truncate text-xs flex-1">{doc.originalFileName || doc.fileName}</span>
            {doc.sensitivityLabel && (
                <span className={`size-1.5 rounded-full shrink-0 ${SENSITIVITY_DOT[doc.sensitivityLabel] ?? "bg-gray-300"}`} />
            )}
        </div>
    );
}
