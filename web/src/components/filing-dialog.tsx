"use client";

import { useCallback, useEffect, useState } from "react";
import {
    X, Folder, ChevronRight, HardDrive, Loader2, Check,
    FolderOpen, Plus, Cloud, Database,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

type Drive = {
    id: string;
    displayName: string;
    providerType: string;
    systemDrive?: boolean;
    active?: boolean;
};

type FolderEntry = {
    id: string;
    name: string;
    folder: boolean;
};

type FilingDialogProps = {
    document: {
        id: string;
        originalFileName: string;
        categoryName?: string;
        sensitivityLabel?: string;
    };
    onFiled: () => void;
    onClose: () => void;
};

const PROVIDER_ICONS: Record<string, typeof Database> = {
    LOCAL: Database,
    GOOGLE_DRIVE: Cloud,
    S3: HardDrive,
};

const SUPPORTED_PROVIDERS = new Set(["LOCAL"]);

export default function FilingDialog({ document, onFiled, onClose }: FilingDialogProps) {
    const [drives, setDrives] = useState<Drive[]>([]);
    const [selectedDrive, setSelectedDrive] = useState<Drive | null>(null);
    const [folders, setFolders] = useState<FolderEntry[]>([]);
    const [folderPath, setFolderPath] = useState<{ id: string; name: string }[]>([]);
    const [currentFolderId, setCurrentFolderId] = useState<string | null>(null);
    const [loadingFolders, setLoadingFolders] = useState(false);
    const [filing, setFiling] = useState(false);
    const [newFolderName, setNewFolderName] = useState("");
    const [showNewFolder, setShowNewFolder] = useState(false);

    useEffect(() => {
        api.get("/drives").then(({ data }) => setDrives(data)).catch(() => {});
    }, []);

    const loadFolders = useCallback(async (drive: Drive, parentId?: string) => {
        setLoadingFolders(true);
        try {
            const pid = parentId || "root";
            const { data } = await api.get(`/drives/${drive.id}/folders?parentId=${pid}`);
            setFolders(data.filter((f: FolderEntry) => f.folder));
        } catch {
            setFolders([]);
        } finally {
            setLoadingFolders(false);
        }
    }, []);

    const handleSelectDrive = (drive: Drive) => {
        if (!SUPPORTED_PROVIDERS.has(drive.providerType || "LOCAL")) return;
        setSelectedDrive(drive);
        setFolderPath([]);
        setCurrentFolderId(null);
        loadFolders(drive);
    };

    const handleNavigateFolder = (folder: FolderEntry) => {
        if (!selectedDrive) return;
        setFolderPath(prev => [...prev, { id: folder.id, name: folder.name }]);
        setCurrentFolderId(folder.id);
        loadFolders(selectedDrive, folder.id);
    };

    const handleNavigateBack = (index: number) => {
        if (!selectedDrive) return;
        const newPath = folderPath.slice(0, index);
        setFolderPath(newPath);
        const parentId = newPath.length > 0 ? newPath[newPath.length - 1].id : null;
        setCurrentFolderId(parentId);
        loadFolders(selectedDrive, parentId || undefined);
    };

    const handleFile = async () => {
        if (!selectedDrive) return;
        setFiling(true);
        try {
            await api.post(`/filing/${document.id}`, {
                driveId: selectedDrive.id,
                folderId: currentFolderId || "root",
            });
            toast.success(`Filed "${document.originalFileName}"`);
            onFiled();
        } catch (e: any) {
            toast.error(e?.response?.data?.message || "Failed to file document");
        } finally {
            setFiling(false);
        }
    };

    const handleCreateFolder = async () => {
        if (!newFolderName.trim()) return;
        try {
            await api.post("/folders", {
                name: newFolderName.trim(),
                parentId: currentFolderId || null,
            });
            setNewFolderName("");
            setShowNewFolder(false);
            if (selectedDrive) loadFolders(selectedDrive, currentFolderId || undefined);
        } catch {
            toast.error("Failed to create folder");
        }
    };

    const locationLabel = selectedDrive
        ? folderPath.length > 0
            ? `${selectedDrive.displayName} / ${folderPath.map(f => f.name).join(" / ")}`
            : `${selectedDrive.displayName} (root)`
        : null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
            <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 max-h-[80vh] flex flex-col" onClick={e => e.stopPropagation()}>
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200">
                    <div>
                        <h3 className="text-base font-semibold text-gray-900">File Document</h3>
                        <p className="text-xs text-gray-500 mt-0.5 truncate max-w-80">{document.originalFileName}</p>
                    </div>
                    <button onClick={onClose} className="p-1 text-gray-400 hover:text-gray-600 rounded">
                        <X className="size-5" />
                    </button>
                </div>

                {/* Document Info */}
                <div className="px-5 py-3 bg-gray-50 border-b border-gray-200 flex gap-3">
                    {document.categoryName && (
                        <span className="inline-flex px-2 py-0.5 text-xs font-medium bg-blue-100 text-blue-700 rounded-full">
                            {document.categoryName}
                        </span>
                    )}
                    {document.sensitivityLabel && (
                        <span className="inline-flex px-2 py-0.5 text-xs font-medium bg-amber-100 text-amber-700 rounded-full">
                            {document.sensitivityLabel}
                        </span>
                    )}
                </div>

                {/* Drive Selection */}
                <div className="px-5 py-3 border-b border-gray-200">
                    <p className="text-xs font-medium text-gray-500 mb-2">Choose destination</p>
                    <div className="flex gap-2 flex-wrap">
                        {drives.map(drive => {
                            const Icon = PROVIDER_ICONS[drive.providerType || "LOCAL"] || Database;
                            const supported = SUPPORTED_PROVIDERS.has(drive.providerType || "LOCAL");
                            return (
                                <button key={drive.id}
                                    onClick={() => supported && handleSelectDrive(drive)}
                                    disabled={!supported}
                                    className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border transition-colors ${
                                        selectedDrive?.id === drive.id
                                            ? "border-blue-500 bg-blue-50 text-blue-700"
                                            : supported
                                                ? "border-gray-200 text-gray-700 hover:bg-gray-50"
                                                : "border-gray-100 text-gray-400 cursor-not-allowed"
                                    }`}>
                                    <Icon className="size-3.5" />
                                    {drive.displayName}
                                    {!supported && <span className="text-[10px] text-gray-400 ml-1">Soon</span>}
                                </button>
                            );
                        })}
                    </div>
                </div>

                {/* Folder Browser */}
                {selectedDrive && (
                    <div className="flex-1 overflow-y-auto px-5 py-3 min-h-48">
                        {/* Breadcrumb */}
                        <div className="flex items-center gap-1 text-xs text-gray-500 mb-3 flex-wrap">
                            <button onClick={() => handleNavigateBack(0)}
                                className="hover:text-blue-600 font-medium">
                                {selectedDrive.displayName}
                            </button>
                            {folderPath.map((f, i) => (
                                <span key={f.id} className="flex items-center gap-1">
                                    <ChevronRight className="size-3" />
                                    <button onClick={() => handleNavigateBack(i + 1)}
                                        className="hover:text-blue-600 font-medium">
                                        {f.name}
                                    </button>
                                </span>
                            ))}
                        </div>

                        {loadingFolders ? (
                            <div className="flex items-center justify-center py-8">
                                <Loader2 className="size-5 animate-spin text-gray-300" />
                            </div>
                        ) : (
                            <div className="space-y-1">
                                {folders.map(folder => (
                                    <button key={folder.id}
                                        onClick={() => handleNavigateFolder(folder)}
                                        className="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-gray-700 rounded-lg hover:bg-gray-50 transition-colors text-left">
                                        <FolderOpen className="size-4 text-amber-500 shrink-0" />
                                        <span className="truncate">{folder.name}</span>
                                        <ChevronRight className="size-3.5 text-gray-300 ml-auto shrink-0" />
                                    </button>
                                ))}
                                {folders.length === 0 && !loadingFolders && (
                                    <p className="text-xs text-gray-400 py-4 text-center">No subfolders. File here or create one.</p>
                                )}

                                {/* New Folder */}
                                {showNewFolder ? (
                                    <div className="flex items-center gap-2 px-3 py-2">
                                        <Folder className="size-4 text-gray-400 shrink-0" />
                                        <input
                                            type="text"
                                            value={newFolderName}
                                            onChange={e => setNewFolderName(e.target.value)}
                                            onKeyDown={e => e.key === "Enter" && handleCreateFolder()}
                                            placeholder="Folder name..."
                                            className="flex-1 text-sm border border-gray-200 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-blue-500"
                                            autoFocus
                                        />
                                        <button onClick={handleCreateFolder} className="text-blue-600 hover:text-blue-800">
                                            <Check className="size-4" />
                                        </button>
                                        <button onClick={() => { setShowNewFolder(false); setNewFolderName(""); }}
                                            className="text-gray-400 hover:text-gray-600">
                                            <X className="size-4" />
                                        </button>
                                    </div>
                                ) : (
                                    <button onClick={() => setShowNewFolder(true)}
                                        className="w-full flex items-center gap-2.5 px-3 py-2 text-xs text-gray-400 rounded-lg hover:bg-gray-50 hover:text-gray-600 transition-colors">
                                        <Plus className="size-3.5" />
                                        New folder
                                    </button>
                                )}
                            </div>
                        )}
                    </div>
                )}

                {/* Footer */}
                <div className="px-5 py-4 border-t border-gray-200 flex items-center justify-between">
                    <div className="text-xs text-gray-500 truncate max-w-60">
                        {locationLabel || "Select a destination"}
                    </div>
                    <div className="flex gap-2">
                        <button onClick={onClose}
                            className="px-3 py-1.5 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50">
                            Cancel
                        </button>
                        <button onClick={handleFile} disabled={!selectedDrive || filing}
                            className="inline-flex items-center gap-1.5 px-4 py-1.5 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors">
                            {filing ? <Loader2 className="size-3.5 animate-spin" /> : <Check className="size-3.5" />}
                            File Here
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}
