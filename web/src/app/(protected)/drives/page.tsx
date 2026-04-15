"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
    HardDrive, FolderOpen, FolderClosed, FileText, ChevronRight, ChevronDown,
    Plus, Check, Loader2, X, RefreshCw, CheckSquare, Square,
    FileImage, FileSpreadsheet, Users, AlertTriangle, Settings,
    CheckCircle, Upload, Cloud, Database, Server, Wifi, ExternalLink, Eye,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import EmptyState from "@/components/empty-state";
import { SkeletonTable } from "@/components/skeleton";
import ResizableTh from "@/components/resizable-th";

type ConnectedDrive = {
    id: string; provider: string; providerType: string; displayName: string;
    providerAccountEmail?: string; providerAccountName?: string;
    active: boolean; systemDrive: boolean;
    hasWriteAccess?: boolean; needsReconnect?: boolean;
    monitoredFolderIds?: string[];
};

type DriveFile = {
    id: string; name: string; mimeType: string; size: number;
    modifiedTime?: string; ownerEmail?: string; webViewLink?: string;
    folder: boolean; igStatus?: string; igCategory?: string; igSensitivity?: string;
    tracked?: boolean; trackedStatus?: string; trackedCategory?: string;
    trackedSensitivity?: string; trackedDocId?: string; trackedSlug?: string;
    // Local storage extras
    metadata?: Record<string, string>;
};

type SharedDrive = { id: string; name: string };
type FolderNode = { id: string; name: string; children?: FolderNode[]; loaded: boolean };

const SENSITIVITY_COLORS: Record<string, string> = {
    PUBLIC: "bg-green-100 text-green-700", INTERNAL: "bg-blue-100 text-blue-700",
    CONFIDENTIAL: "bg-amber-100 text-amber-700", RESTRICTED: "bg-red-100 text-red-700",
};

const PROVIDER_ICONS: Record<string, { icon: typeof HardDrive; color: string }> = {
    LOCAL: { icon: HardDrive, color: "text-gray-500" },
    GOOGLE_DRIVE: { icon: Cloud, color: "text-blue-500" },
    S3: { icon: Database, color: "text-amber-500" },
    SHAREPOINT: { icon: Server, color: "text-blue-600" },
    BOX: { icon: Cloud, color: "text-blue-400" },
    SMB: { icon: Wifi, color: "text-green-500" },
};

function fileIcon(mimeType: string) {
    if (mimeType?.includes("spreadsheet") || mimeType?.includes("csv")) return FileSpreadsheet;
    if (mimeType?.includes("image")) return FileImage;
    return FileText;
}

function formatSize(bytes: number) {
    if (!bytes) return "";
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function DrivesPage() {
    const fileInput = useRef<HTMLInputElement>(null);
    const [drives, setDrives] = useState<ConnectedDrive[]>([]);
    const [activeDrive, setActiveDrive] = useState<ConnectedDrive | null>(null);
    const [sharedDrives, setSharedDrives] = useState<SharedDrive[]>([]);
    const [files, setFiles] = useState<DriveFile[]>([]);
    const [loading, setLoading] = useState(false);
    const [currentFolder, setCurrentFolder] = useState<{ id: string; name: string }>({ id: "root", name: "Root" });
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [registering, setRegistering] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [folderTree, setFolderTree] = useState<FolderNode[]>([]);
    const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set(["root"]));
    const [dragOver, setDragOver] = useState(false);

    // Load drives
    const loadDrives = useCallback(async () => {
        try {
            const { data } = await api.get("/drives");
            setDrives(data);
            // Auto-select first drive if none selected
            if (!activeDrive && data.length > 0) {
                // Prefer system drive (Local Storage) as default
                const sys = data.find((d: ConnectedDrive) => d.systemDrive);
                switchToDrive(sys ?? data[0]);
            }
        } catch { /* ignore */ }
    }, [activeDrive]);

    useEffect(() => { loadDrives(); }, [loadDrives]);

    // Listen for OAuth popup completion
    useEffect(() => {
        const handler = (event: MessageEvent) => {
            if (event.data?.type === "google-drive-connected") { toast.success("Google Drive connected"); loadDrives(); }
        };
        window.addEventListener("message", handler);
        return () => window.removeEventListener("message", handler);
    }, [loadDrives]);

    // Load folders when drive changes
    useEffect(() => {
        if (!activeDrive) return;
        api.get(`/drives/${activeDrive.id}/folders?parentId=root`)
            .then(({ data }) => setFolderTree(data.map((f: DriveFile) => ({ id: f.id, name: f.name, loaded: false }))))
            .catch(() => setFolderTree([]));
    }, [activeDrive]);

    // Load files when drive or folder changes
    const loadFiles = useCallback(async () => {
        if (!activeDrive) return;
        setLoading(true); setSelected(new Set());
        try {
            const { data } = await api.get(`/drives/${activeDrive.id}/files?folderId=${currentFolder.id}`);
            setFiles(data);
        } catch { toast.error("Failed to load files"); }
        finally { setLoading(false); }
    }, [activeDrive, currentFolder.id]);

    useEffect(() => { if (activeDrive) loadFiles(); }, [activeDrive, loadFiles]);

    const switchToDrive = async (drive: ConnectedDrive) => {
        setActiveDrive(drive);
        setCurrentFolder({ id: "root", name: drive.displayName || "Root" });
        setFolderTree([]); setFiles([]); setSharedDrives([]);
        if (drive.providerType === "GOOGLE_DRIVE") {
            try { const { data } = await api.get(`/drives/${drive.id}/shared-drives`); setSharedDrives(data); } catch { }
        }
    };

    const handleConnect = async () => {
        try {
            const { data } = await api.get("/drives/google/auth-url");
            if (data.error) { toast.error(data.error); return; }
            const w = 600, h = 700, left = (screen.width - w) / 2, top = (screen.height - h) / 2;
            window.open(data.url, "google-drive-auth", `width=${w},height=${h},left=${left},top=${top}`);
        } catch { toast.error("Failed to start Google Drive connection"); }
    };

    // Upload to active drive (local storage)
    const handleUpload = async (fileList: File[]) => {
        if (!activeDrive || !fileList.length) return;
        setUploading(true);
        let uploaded = 0;
        for (const file of fileList) {
            try {
                const form = new FormData(); form.append("file", file);
                await api.post("/documents", form, { headers: { "Content-Type": "multipart/form-data" } });
                uploaded++;
            } catch { toast.error(`Failed to upload ${file.name}`); }
        }
        if (uploaded > 0) { toast.success(`Uploaded ${uploaded} file(s)`); loadFiles(); }
        setUploading(false);
        if (fileInput.current) fileInput.current.value = "";
    };

    const handleDrop = (e: React.DragEvent) => {
        e.preventDefault(); setDragOver(false);
        handleUpload(Array.from(e.dataTransfer.files));
    };

    // Classify selected files (Google Drive)
    const handleClassify = async () => {
        if (!selected.size || !activeDrive) return;
        setRegistering(true);
        try {
            const { data } = await api.post(`/drives/${activeDrive.id}/register`, { fileIds: [...selected] });
            toast.success(`${data.registered} file(s) queued for classification`);
            setSelected(new Set());
            setTimeout(loadFiles, 3000);
        } catch { toast.error("Failed"); }
        finally { setRegistering(false); }
    };

    // Folder tree
    const expandFolder = async (folderId: string, nodes: FolderNode[]): Promise<FolderNode[]> => {
        if (!activeDrive) return nodes;
        return Promise.all(nodes.map(async (node) => {
            if (node.id === folderId && !node.loaded) {
                try {
                    const { data } = await api.get(`/drives/${activeDrive.id}/folders?parentId=${folderId}`);
                    return { ...node, children: data.map((f: DriveFile) => ({ id: f.id, name: f.name, loaded: false })), loaded: true };
                } catch { return node; }
            }
            if (node.children) return { ...node, children: await expandFolder(folderId, node.children) };
            return node;
        }));
    };

    const handleFolderClick = async (folderId: string, name: string) => {
        setCurrentFolder({ id: folderId, name });
        if (!expandedFolders.has(folderId)) {
            setExpandedFolders(prev => new Set(prev).add(folderId));
            setFolderTree(await expandFolder(folderId, folderTree));
        }
    };

    const toggleExpand = async (folderId: string, e: React.MouseEvent) => {
        e.stopPropagation();
        const next = new Set(expandedFolders);
        if (next.has(folderId)) next.delete(folderId); else {
            next.add(folderId);
            setFolderTree(await expandFolder(folderId, folderTree));
        }
        setExpandedFolders(next);
    };

    const toggleSelect = (id: string) => setSelected(prev => { const n = new Set(prev); if (n.has(id)) n.delete(id); else n.add(id); return n; });
    const nonFolders = files.filter(f => !f.folder);
    const allSelected = nonFolders.length > 0 && selected.size === nonFolders.length;
    const selectAll = () => setSelected(allSelected ? new Set() : new Set(nonFolders.map(f => f.id)));
    const isLocal = activeDrive?.providerType === "LOCAL";
    const canUpload = isLocal || (activeDrive?.hasWriteAccess ?? false);

    return (
        <div className="flex h-[calc(100vh-120px)] -m-6 relative"
            onDragOver={e => { e.preventDefault(); if (canUpload) setDragOver(true); }}
            onDragLeave={e => { if (e.currentTarget === e.target || !e.currentTarget.contains(e.relatedTarget as Node)) setDragOver(false); }}
            onDrop={canUpload ? handleDrop : undefined}>

            {/* Drop overlay */}
            {dragOver && (
                <div className="absolute inset-0 z-50 bg-blue-500/10 border-2 border-dashed border-blue-400 rounded-lg flex items-center justify-center pointer-events-none">
                    <div className="bg-white rounded-xl shadow-lg px-8 py-6 text-center">
                        <Upload className="size-10 text-blue-500 mx-auto mb-2" />
                        <p className="text-sm font-semibold text-gray-800">Drop files to upload</p>
                        <p className="text-xs text-gray-400 mt-1">to {activeDrive?.displayName ?? "Local Storage"}</p>
                    </div>
                </div>
            )}

            {/* Drive selector sidebar */}
            <div className="w-52 shrink-0 bg-white border-r border-gray-200 flex flex-col">
                <div className="px-3 py-3 border-b border-gray-100 shrink-0">
                    <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Drives</span>
                </div>
                <div className="flex-1 overflow-y-auto py-1">
                    {drives.map(d => {
                        const pi = PROVIDER_ICONS[d.providerType] ?? PROVIDER_ICONS.LOCAL;
                        const Icon = pi.icon;
                        const isActive = activeDrive?.id === d.id;
                        return (
                            <button key={d.id} onClick={() => switchToDrive(d)}
                                className={`w-full text-left flex items-center gap-2 px-3 py-2 text-sm transition-colors ${
                                    isActive ? "bg-blue-50 text-blue-700" : "text-gray-700 hover:bg-gray-50"
                                }`}>
                                <Icon className={`size-4 shrink-0 ${isActive ? "text-blue-500" : pi.color}`} />
                                <div className="min-w-0 flex-1">
                                    <div className="text-xs font-medium truncate">{d.displayName || d.providerAccountEmail}</div>
                                    {d.providerAccountEmail && !d.systemDrive && (
                                        <div className="text-[10px] text-gray-400 truncate">{d.providerAccountEmail}</div>
                                    )}
                                </div>
                                {d.needsReconnect && <span className="size-2 rounded-full bg-amber-400 shrink-0" title="Read-only" />}
                            </button>
                        );
                    })}

                    {/* Shared drives (Google) */}
                    {sharedDrives.map(sd => (
                        <button key={sd.id} onClick={() => setCurrentFolder({ id: sd.id, name: sd.name })}
                            className="w-full text-left flex items-center gap-2 px-3 py-2 text-sm text-gray-600 hover:bg-gray-50">
                            <Users className="size-4 text-gray-400 shrink-0" />
                            <span className="text-xs truncate">{sd.name}</span>
                        </button>
                    ))}
                </div>

                {/* Connect button */}
                <div className="px-3 py-3 border-t border-gray-100 space-y-1.5 shrink-0">
                    <button onClick={handleConnect}
                        className="w-full flex items-center gap-2 px-3 py-2 text-xs font-medium text-blue-600 hover:bg-blue-50 rounded-lg transition-colors">
                        <Plus className="size-3.5" /> Connect Google Drive
                    </button>
                </div>
            </div>

            {/* Folder tree */}
            {activeDrive && (
                <div className="w-48 shrink-0 bg-white border-r border-gray-200 flex flex-col overflow-hidden">
                    <div className="px-3 py-2.5 border-b border-gray-100 shrink-0">
                        <button onClick={() => setCurrentFolder({ id: "root", name: activeDrive.displayName || "Root" })}
                            className={`w-full text-left text-xs font-medium px-2 py-1 rounded ${currentFolder.id === "root" ? "bg-blue-50 text-blue-700" : "text-gray-700 hover:bg-gray-50"}`}>
                            {isLocal ? "All Files" : "My Drive"}
                        </button>
                    </div>
                    <div className="flex-1 overflow-y-auto py-1">
                        {folderTree.map(n => (
                            <TreeNode key={n.id} node={n} depth={0} current={currentFolder.id}
                                expanded={expandedFolders} onSelect={handleFolderClick} onToggle={toggleExpand} />
                        ))}
                    </div>
                </div>
            )}

            {/* File browser */}
            <div className="flex-1 bg-white flex flex-col overflow-hidden">
                <div className="flex items-center justify-between px-4 py-2.5 border-b border-gray-200 bg-gray-50 shrink-0">
                    <span className="text-sm font-medium text-gray-700">{currentFolder.name}</span>
                    <div className="flex items-center gap-2">
                        {/* Classify button (external drives) */}
                        {selected.size > 0 && !isLocal && (
                            <button onClick={handleClassify} disabled={registering}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-green-600 text-white text-xs font-medium rounded-md hover:bg-green-700 disabled:opacity-50">
                                {registering ? <Loader2 className="size-3 animate-spin" /> : <Check className="size-3" />}
                                Classify {selected.size}
                            </button>
                        )}
                        {/* Watch folder (external drives) */}
                        {currentFolder.id !== "root" && !isLocal && activeDrive && (
                            <button onClick={async () => {
                                try {
                                    await api.post(`/drives/${activeDrive.id}/monitor/${currentFolder.id}`);
                                    toast.success(`Monitoring "${currentFolder.name}" for new files`);
                                } catch { toast.error("Failed"); }
                            }}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-blue-300 text-blue-700 text-xs font-medium rounded-md hover:bg-blue-50">
                                Watch Folder
                            </button>
                        )}
                        {/* Upload button (writable drives) */}
                        {canUpload && (
                            <button onClick={() => fileInput.current?.click()} disabled={uploading}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-md hover:bg-blue-700 disabled:opacity-50">
                                {uploading ? <Loader2 className="size-3 animate-spin" /> : <Upload className="size-3" />} Upload
                            </button>
                        )}
                        <button onClick={loadFiles} disabled={loading} className="p-1.5 text-gray-400 hover:text-gray-600" aria-label="Refresh">
                            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
                        </button>
                    </div>
                </div>

                {/* Readonly warning */}
                {activeDrive?.needsReconnect && (
                    <div className="px-4 py-2 bg-amber-50 border-b border-amber-200 flex items-center gap-2">
                        <AlertTriangle className="size-3.5 text-amber-500 shrink-0" />
                        <span className="text-xs text-amber-700">This drive has read-only access. Disconnect and reconnect to enable write-back.</span>
                    </div>
                )}

                <div className="flex-1 overflow-auto">
                    {loading ? (
                        <SkeletonTable rows={8} cols={5} />
                    ) : nonFolders.length === 0 && !files.some(f => f.folder) ? (
                        <EmptyState
                            icon={canUpload ? Upload : FolderOpen}
                            title={canUpload ? "No files here yet" : "Empty folder"}
                            description={canUpload ? "Upload files or drag and drop to get started." : "This folder is empty."}
                            action={canUpload ? "Upload Files" : undefined}
                            onAction={canUpload ? () => fileInput.current?.click() : undefined}
                        />
                    ) : (
                        <table className="w-full text-sm">
                            <thead className="sticky top-0 bg-gray-50 z-10">
                                <tr className="border-b border-gray-200">
                                    {!isLocal && (
                                        <th className="w-10 px-3 py-2">
                                            <button onClick={selectAll} aria-label="Select all">
                                                {allSelected ? <CheckSquare className="size-4 text-blue-600" /> : <Square className="size-4 text-gray-400" />}
                                            </button>
                                        </th>
                                    )}
                                    <ResizableTh className="text-left px-3 py-2 font-medium text-gray-600">Name</ResizableTh>
                                    <ResizableTh className="text-left px-3 py-2 font-medium text-gray-600" initialWidth={96}>Size</ResizableTh>
                                    <ResizableTh className="text-left px-3 py-2 font-medium text-gray-600" initialWidth={112}>Modified</ResizableTh>
                                    {!isLocal && <ResizableTh className="text-left px-3 py-2 font-medium text-gray-600" initialWidth={144}>Owner</ResizableTh>}
                                    <ResizableTh className="text-left px-3 py-2 font-medium text-gray-600" initialWidth={176}>Classification</ResizableTh>
                                    <th className="w-10"></th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                                {/* Folders first */}
                                {files.filter(f => f.folder).map(folder => (
                                    <tr key={folder.id} onClick={() => handleFolderClick(folder.id, folder.name)}
                                        className="cursor-pointer hover:bg-gray-50">
                                        {!isLocal && <td className="px-3 py-2" />}
                                        <td className="px-3 py-2" colSpan={isLocal ? 4 : 4}>
                                            <div className="flex items-center gap-2">
                                                <FolderClosed className="size-4 text-amber-400 shrink-0" />
                                                <span className="text-gray-900 font-medium">{folder.name}</span>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                                {/* Files */}
                                {nonFolders.map(file => {
                                    const Icon = fileIcon(file.mimeType);
                                    const sel = selected.has(file.id);
                                    const status = file.tracked ? file.trackedStatus : (file.metadata?.status || null);
                                    const category = file.tracked ? file.trackedCategory : (file.metadata?.categoryName || null);
                                    const sensitivity = file.tracked ? file.trackedSensitivity : (file.metadata?.sensitivityLabel || null);
                                    const isClassified = status === "GOVERNANCE_APPLIED" || status === "CLASSIFIED" || status === "INBOX" || status === "FILED" || status === "REVIEW_REQUIRED";
                                    const isFailed = status?.includes("FAILED");
                                    const isProcessing = status && !isClassified && !isFailed && status !== "UPLOADED";

                                    // Determine the document link — local files use id directly, tracked external files use slug/docId
                                    const docLink = isLocal ? `/documents?doc=${file.id}`
                                        : file.trackedSlug ? `/documents?doc=${file.trackedSlug}`
                                        : file.trackedDocId ? `/documents?doc=${file.trackedDocId}`
                                        : null;

                                    return (
                                        <tr key={file.id} className={`hover:bg-gray-50 ${sel ? "bg-blue-50" : ""} cursor-pointer`}
                                            onClick={() => {
                                                if (docLink) {
                                                    // Tracked file — navigate to document viewer
                                                    window.location.href = docLink;
                                                } else if (!isLocal) {
                                                    // Untracked external file — toggle selection for classification
                                                    toggleSelect(file.id);
                                                }
                                            }}>
                                            {!isLocal && (
                                                <td className="px-3 py-2" onClick={e => e.stopPropagation()}>
                                                    <button onClick={() => toggleSelect(file.id)} aria-label={`Select ${file.name}`}>
                                                        {sel ? <CheckSquare className="size-4 text-blue-600" /> : <Square className="size-4 text-gray-300" />}
                                                    </button>
                                                </td>
                                            )}
                                            <td className="px-3 py-2">
                                                <div className="flex items-center gap-2">
                                                    <Icon className="size-4 text-gray-400 shrink-0" />
                                                    <span className="text-gray-900 truncate">{file.name}</span>
                                                    {file.tracked && (
                                                        <span className={`px-1 py-0.5 text-[9px] font-medium rounded shrink-0 ${
                                                            isClassified ? "bg-green-100 text-green-700" :
                                                            isFailed ? "bg-red-100 text-red-700" :
                                                            "bg-blue-100 text-blue-700"
                                                        }`}>
                                                            {status === "REVIEW_REQUIRED" ? "Review" : isClassified ? "Classified" : isFailed ? "Failed" : "Processing"}
                                                        </span>
                                                    )}
                                                </div>
                                            </td>
                                            <td className="px-3 py-2 text-gray-500 text-xs">{formatSize(file.size)}</td>
                                            <td className="px-3 py-2 text-gray-500 text-xs">
                                                {file.modifiedTime ? new Date(file.modifiedTime).toLocaleDateString() : ""}
                                            </td>
                                            {!isLocal && <td className="px-3 py-2 text-gray-500 text-xs truncate">{file.ownerEmail}</td>}
                                            <td className="px-3 py-2">
                                                {(status && status !== "UPLOADED") ? (
                                                    <div className="flex items-center gap-1.5 flex-wrap">
                                                        {isClassified ? <CheckCircle className="size-3.5 text-green-500 shrink-0" /> :
                                                         isFailed ? <AlertTriangle className="size-3.5 text-red-500 shrink-0" /> :
                                                         isProcessing ? <Loader2 className="size-3.5 text-blue-500 animate-spin shrink-0" /> : null}
                                                        <span className="text-[10px] text-gray-600 truncate">{category ?? status?.replace(/_/g, " ")}</span>
                                                        {sensitivity && (
                                                            <span className={`px-1 py-0.5 text-[9px] font-medium rounded ${SENSITIVITY_COLORS[sensitivity] ?? "bg-gray-100 text-gray-500"}`}>
                                                                {sensitivity}
                                                            </span>
                                                        )}
                                                    </div>
                                                ) : (
                                                    <span className="text-[10px] text-gray-400">{isLocal ? "—" : "Not tracked"}</span>
                                                )}
                                            </td>
                                            <td className="px-2 py-2 w-10" onClick={e => e.stopPropagation()}>
                                                {file.webViewLink ? (
                                                    <a href={file.webViewLink} target="_blank" rel="noopener noreferrer"
                                                        className="p-1 text-gray-400 hover:text-blue-600 rounded" title="Open in Google Drive">
                                                        <ExternalLink className="size-3.5" />
                                                    </a>
                                                ) : docLink ? (
                                                    <a href={docLink} className="p-1 text-gray-400 hover:text-blue-600 rounded" title="View document">
                                                        <Eye className="size-3.5" />
                                                    </a>
                                                ) : null}
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    )}
                </div>
            </div>

            <input ref={fileInput} type="file" multiple className="hidden" onChange={e => handleUpload(Array.from(e.target.files ?? []))} />
        </div>
    );
}

function TreeNode({ node, depth, current, expanded, onSelect, onToggle }: {
    node: FolderNode; depth: number; current: string; expanded: Set<string>;
    onSelect: (id: string, name: string) => void; onToggle: (id: string, e: React.MouseEvent) => void;
}) {
    const isOpen = expanded.has(node.id);
    const isActive = current === node.id;
    return (
        <>
            <button onClick={() => onSelect(node.id, node.name)}
                className={`w-full flex items-center gap-1 text-left text-xs py-1.5 hover:bg-gray-50 ${isActive ? "bg-blue-50 text-blue-700" : "text-gray-700"}`}
                style={{ paddingLeft: `${depth * 16 + 12}px` }}>
                <span onClick={(e) => onToggle(node.id, e)} className="p-0.5 shrink-0">
                    {isOpen ? <ChevronDown className="size-3 text-gray-400" /> : <ChevronRight className="size-3 text-gray-400" />}
                </span>
                {isOpen ? <FolderOpen className="size-3.5 text-amber-500 shrink-0" /> : <FolderClosed className="size-3.5 text-amber-400 shrink-0" />}
                <span className="truncate">{node.name}</span>
            </button>
            {isOpen && node.children?.map(c => <TreeNode key={c.id} node={c} depth={depth + 1} current={current} expanded={expanded} onSelect={onSelect} onToggle={onToggle} />)}
        </>
    );
}
