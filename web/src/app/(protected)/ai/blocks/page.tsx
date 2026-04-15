"use client";

import { useCallback, useEffect, useState } from "react";
import {
    Brain, Scan, Cog, GitBranch, Shield, Plus, Pencil, Save, X,
    Loader2, ChevronDown, ChevronRight, Clock, MessageSquare,
    CheckCircle, AlertTriangle, History, RefreshCw,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import TextDiff from "@/components/text-diff";
import BlockFormEditor from "@/components/block-form-editor";

type BlockVersion = {
    version: number;
    content: Record<string, unknown>;
    changelog: string;
    publishedBy: string;
    publishedAt: string;
};

type Block = {
    id?: string;
    name: string;
    description: string;
    type: string;
    active: boolean;
    activeVersion: number;
    versions: BlockVersion[];
    draftContent?: Record<string, unknown>;
    draftChangelog?: string;
    feedbackCount: number;
    documentsProcessed: number;
    correctionsReceived: number;
    createdAt?: string;
    createdBy?: string;
};

type FeedbackItem = {
    id: string;
    blockVersion: number;
    type: string;
    details: string;
    originalValue?: string;
    correctedValue?: string;
    userEmail?: string;
    timestamp: string;
};

const TYPE_ICONS: Record<string, { icon: typeof Brain; color: string; label: string }> = {
    PROMPT: { icon: Brain, color: "bg-purple-50 text-purple-700 border-purple-200", label: "Prompt" },
    REGEX_SET: { icon: Scan, color: "bg-blue-50 text-blue-700 border-blue-200", label: "Regex Set" },
    EXTRACTOR: { icon: Cog, color: "bg-gray-50 text-gray-700 border-gray-200", label: "Extractor" },
    ROUTER: { icon: GitBranch, color: "bg-amber-50 text-amber-700 border-amber-200", label: "Router" },
    ENFORCER: { icon: Shield, color: "bg-green-50 text-green-700 border-green-200", label: "Enforcer" },
};

const BLOCK_TYPES = ["PROMPT", "REGEX_SET", "EXTRACTOR", "ROUTER", "ENFORCER"];

export default function BlockLibraryPage() {
    const [blocks, setBlocks] = useState<Block[]>([]);
    const [selectedBlock, setSelectedBlock] = useState<Block | null>(null);
    const [feedback, setFeedback] = useState<FeedbackItem[]>([]);
    const [showCreate, setShowCreate] = useState(false);
    const [tab, setTab] = useState<"config" | "versions" | "feedback">("config");
    const [filterType, setFilterType] = useState("");

    const load = useCallback(async () => {
        try {
            const { data } = await api.get("/admin/blocks/all");
            setBlocks(data);
        } catch { toast.error("Failed to load blocks"); }
    }, []);

    useEffect(() => { load(); }, [load]);

    const selectBlock = async (block: Block) => {
        setSelectedBlock(block);
        setTab("config");
        try {
            const { data } = await api.get(`/admin/blocks/${block.id}/feedback?size=20`);
            setFeedback(data.content ?? []);
        } catch { setFeedback([]); }
    };

    const filtered = filterType ? blocks.filter(b => b.type === filterType) : blocks;
    const active = filtered.filter(b => b.active);

    return (
        <>
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">Block Library</h2>
                    <p className="text-sm text-gray-500 mt-1">Versioned processing blocks — prompts, patterns, extractors, routers</p>
                </div>
                <button onClick={() => setShowCreate(true)}
                    className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700">
                    <Plus className="size-4" /> New Block
                </button>
            </div>

            {/* Type filter */}
            <div className="flex gap-2 mb-4">
                <button onClick={() => setFilterType("")}
                    className={`px-3 py-1.5 text-xs font-medium rounded-md border ${!filterType ? "bg-blue-50 text-blue-700 border-blue-200" : "text-gray-600 border-gray-300 hover:bg-gray-50"}`}>
                    All ({blocks.filter(b => b.active).length})
                </button>
                {BLOCK_TYPES.map(t => {
                    const info = TYPE_ICONS[t];
                    const count = blocks.filter(b => b.type === t && b.active).length;
                    return (
                        <button key={t} onClick={() => setFilterType(filterType === t ? "" : t)}
                            className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-md border ${
                                filterType === t ? info.color : "text-gray-600 border-gray-300 hover:bg-gray-50"
                            }`}>
                            <info.icon className="size-3.5" /> {info.label} ({count})
                        </button>
                    );
                })}
            </div>

            <div className="flex gap-6">
                {/* Block list */}
                <div className="flex-1 space-y-3">
                    {active.map(block => {
                        const typeInfo = TYPE_ICONS[block.type] ?? TYPE_ICONS.EXTRACTOR;
                        const TypeIcon = typeInfo.icon;
                        const isSelected = selectedBlock?.id === block.id;
                        return (
                            <button key={block.id} onClick={() => selectBlock(block)}
                                className={`w-full text-left bg-white rounded-lg shadow-sm border p-4 hover:bg-gray-50 transition-colors ${
                                    isSelected ? "border-blue-300 ring-1 ring-blue-200" : "border-gray-200"
                                }`}>
                                <div className="flex items-start justify-between">
                                    <div className="flex items-center gap-3">
                                        <div className={`size-10 rounded-lg flex items-center justify-center border ${typeInfo.color}`}>
                                            <TypeIcon className="size-5" />
                                        </div>
                                        <div>
                                            <div className="flex items-center gap-2">
                                                <span className="text-sm font-semibold text-gray-900">{block.name}</span>
                                                <span className="text-[10px] text-gray-400">v{block.activeVersion}</span>
                                            </div>
                                            <p className="text-xs text-gray-500 mt-0.5">{block.description}</p>
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        {block.feedbackCount > 0 && (
                                            <span className="inline-flex items-center gap-1 px-2 py-0.5 text-[10px] font-medium bg-amber-100 text-amber-700 rounded-full">
                                                <MessageSquare className="size-3" /> {block.feedbackCount}
                                            </span>
                                        )}
                                        {block.draftContent && (
                                            <span className="px-2 py-0.5 text-[10px] font-medium bg-blue-100 text-blue-700 rounded-full">Draft</span>
                                        )}
                                        <span className="text-xs text-gray-400">{block.versions.length} version{block.versions.length !== 1 ? "s" : ""}</span>
                                    </div>
                                </div>
                            </button>
                        );
                    })}
                    {active.length === 0 && (
                        <div className="flex flex-col items-center justify-center py-12 text-center">
                            <div className="size-16 rounded-full bg-gray-100 flex items-center justify-center mb-4">
                                <Brain className="size-8 text-gray-300" />
                            </div>
                            <h3 className="text-sm font-semibold text-gray-700 mb-1">No blocks yet</h3>
                            <p className="text-xs text-gray-400 max-w-xs mb-4">Blocks are reusable processing units — prompts, PII patterns, extractors, routers, and enforcers.</p>
                            <button onClick={() => setShowCreate(true)}
                                className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">
                                <Plus className="size-3.5" /> Create First Block
                            </button>
                        </div>
                    )}
                </div>

                {/* Detail panel */}
                {selectedBlock && (
                    <BlockDetail
                        block={selectedBlock}
                        feedback={feedback}
                        tab={tab}
                        onTabChange={setTab}
                        onUpdated={() => { load(); selectBlock(selectedBlock); }}
                    />
                )}
            </div>

            {/* Create modal */}
            {showCreate && (
                <CreateBlockModal onClose={() => setShowCreate(false)} onCreated={() => { setShowCreate(false); load(); }} />
            )}
        </>
    );
}

/* ── Block Detail Panel ───────────────────────────── */

function BlockDetail({ block, feedback, tab, onTabChange, onUpdated }: {
    block: Block; feedback: FeedbackItem[];
    tab: "config" | "versions" | "feedback";
    onTabChange: (t: "config" | "versions" | "feedback") => void;
    onUpdated: () => void;
}) {
    const [draftContent, setDraftContent] = useState<Record<string, unknown>>(
        block.draftContent ?? block.versions.find(v => v.version === block.activeVersion)?.content ?? {}
    );
    const [changelog, setChangelog] = useState(block.draftChangelog ?? "");
    const [saving, setSaving] = useState(false);
    const [publishing, setPublishing] = useState(false);
    const [improving, setImproving] = useState(false);
    const [comparing, setComparing] = useState<[number, number] | null>(null);
    const [compareData, setCompareData] = useState<{ content1: Record<string, unknown>; content2: Record<string, unknown> } | null>(null);

    // Reset when block changes
    useEffect(() => {
        const content = block.draftContent ?? block.versions.find(v => v.version === block.activeVersion)?.content ?? {};
        setDraftContent(content);
        setChangelog(block.draftChangelog ?? "");
    }, [block.id, block.activeVersion, block.draftContent, block.draftChangelog, block.versions]);

    const handleSaveDraft = async () => {
        setSaving(true);
        try {
            await api.put(`/admin/blocks/${block.id}/draft`, { content: draftContent, changelog });
            toast.success("Draft saved");
            onUpdated();
        } catch {
            toast.error("Save failed");
        } finally { setSaving(false); }
    };

    const handlePublish = async () => {
        if (!confirm("Publish this draft as a new version?")) return;
        setPublishing(true);
        try {
            await api.put(`/admin/blocks/${block.id}/draft`, { content: draftContent, changelog });
            await api.post(`/admin/blocks/${block.id}/publish`);
            toast.success("Published as v" + (block.versions.length + 1));
            onUpdated();
        } catch { toast.error("Publish failed"); }
        finally { setPublishing(false); }
    };

    const handleRollback = async (version: number) => {
        if (!confirm(`Rollback to v${version}? This changes the active version.`)) return;
        try {
            await api.post(`/admin/blocks/${block.id}/rollback/${version}`);
            toast.success(`Rolled back to v${version}`);
            onUpdated();
        } catch { toast.error("Rollback failed"); }
    };

    const handleImprove = async () => {
        setImproving(true);
        try {
            const { data } = await api.post(`/admin/blocks/${block.id}/improve`);
            if (data.status === "no_feedback") {
                toast.info(data.message);
            } else {
                toast.success(data.message);
                onUpdated();
            }
        } catch { toast.error("AI improvement failed"); }
        finally { setImproving(false); }
    };

    const handleCompare = async (v1: number, v2: number) => {
        try {
            const { data } = await api.get(`/admin/blocks/${block.id}/compare/${v1}/${v2}`);
            setComparing([v1, v2]);
            setCompareData({ content1: data.content1, content2: data.content2 });
        } catch { toast.error("Failed to load comparison"); }
    };

    return (
        <div className="w-[540px] shrink-0 bg-white rounded-lg shadow-sm border border-gray-200 flex flex-col max-h-[calc(100vh-200px)]">
            {/* Header */}
            <div className="px-5 py-4 border-b border-gray-200">
                <div className="flex items-center justify-between">
                    <h3 className="font-semibold text-gray-900">{block.name}</h3>
                    <span className="text-xs text-gray-400">Active: v{block.activeVersion}</span>
                </div>
                <p className="text-xs text-gray-500 mt-0.5">{block.description}</p>
            </div>

            {/* Tabs */}
            <div className="flex border-b border-gray-200">
                {(["config", "versions", "feedback"] as const).map(t => (
                    <button key={t} onClick={() => onTabChange(t)}
                        className={`flex-1 px-3 py-2 text-xs font-medium ${
                            tab === t ? "text-blue-700 border-b-2 border-blue-600" : "text-gray-500 hover:text-gray-700"
                        }`}>
                        {t === "config" ? "Configuration" : t === "versions" ? `Versions (${block.versions.length})` : `Feedback (${feedback.length})`}
                    </button>
                ))}
            </div>

            {/* Tab content */}
            <div className="flex-1 overflow-y-auto">
                {tab === "config" && (
                    <div className="p-4 space-y-3">
                        <BlockFormEditor type={block.type} content={draftContent} onChange={setDraftContent} />
                        <div>
                            <label htmlFor="block-changelog" className="text-xs font-medium text-gray-700 block mb-1">Changelog</label>
                            <input id="block-changelog" value={changelog} onChange={e => setChangelog(e.target.value)}
                                placeholder="What changed in this version?"
                                className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                        </div>
                        <div className="flex gap-2 flex-wrap">
                            <button onClick={handleSaveDraft} disabled={saving}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-gray-300 text-gray-700 text-xs font-medium rounded-md hover:bg-gray-50 disabled:opacity-50">
                                {saving ? <Loader2 className="size-3 animate-spin" /> : <Save className="size-3" />} Save Draft
                            </button>
                            <button onClick={handlePublish} disabled={publishing}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-green-600 text-white text-xs font-medium rounded-md hover:bg-green-700 disabled:opacity-50">
                                {publishing ? <Loader2 className="size-3 animate-spin" /> : <CheckCircle className="size-3" />} Publish
                            </button>
                            <button onClick={handleImprove} disabled={improving || block.feedbackCount === 0}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-purple-600 text-white text-xs font-medium rounded-md hover:bg-purple-700 disabled:opacity-50"
                                title={block.feedbackCount === 0 ? "No feedback to improve from" : `Improve using ${block.feedbackCount} feedback items`}>
                                {improving ? <Loader2 className="size-3 animate-spin" /> : <Brain className="size-3" />}
                                {improving ? "Thinking..." : "Improve with AI"}
                                {block.feedbackCount > 0 && !improving && (
                                    <span className="size-4 rounded-full bg-white/20 text-[9px] flex items-center justify-center">{block.feedbackCount}</span>
                                )}
                            </button>
                        </div>
                    </div>
                )}

                {tab === "versions" && (
                    <div className="p-4 space-y-2">
                        {/* Version comparison */}
                        {comparing && compareData && (
                            <div className="bg-blue-50 border border-blue-200 rounded-lg p-3 mb-3">
                                <div className="flex items-center justify-between mb-2">
                                    <span className="text-xs font-semibold text-blue-800">v{comparing[0]} → v{comparing[1]}</span>
                                    <button onClick={() => { setComparing(null); setCompareData(null); }}
                                        aria-label="Close version comparison" className="text-[10px] text-blue-600 hover:text-blue-800">Close</button>
                                </div>
                                <TextDiff
                                    oldText={JSON.stringify(compareData.content1, null, 2)}
                                    newText={JSON.stringify(compareData.content2, null, 2)}
                                    oldLabel={`v${comparing[0]}`}
                                    newLabel={`v${comparing[1]}`}
                                />
                            </div>
                        )}

                        {[...block.versions].reverse().map((v, i, arr) => (
                            <div key={v.version} className={`p-3 rounded-lg border ${v.version === block.activeVersion ? "border-green-200 bg-green-50" : "border-gray-200"}`}>
                                <div className="flex items-center justify-between mb-1">
                                    <div className="flex items-center gap-2">
                                        <span className="text-sm font-semibold text-gray-900">v{v.version}</span>
                                        {v.version === block.activeVersion && (
                                            <span className="px-1.5 py-0.5 text-[10px] bg-green-100 text-green-700 rounded">Active</span>
                                        )}
                                    </div>
                                    <div className="flex items-center gap-2">
                                        {i < arr.length - 1 && (
                                            <button onClick={() => handleCompare(arr[i + 1].version, v.version)}
                                                className="text-[10px] text-blue-600 hover:text-blue-800 font-medium">Compare</button>
                                        )}
                                        {v.version !== block.activeVersion && (
                                            <button onClick={() => handleRollback(v.version)}
                                                className="text-[10px] text-amber-600 hover:text-amber-800 font-medium">Rollback</button>
                                        )}
                                    </div>
                                </div>
                                <p className="text-xs text-gray-600">{v.changelog}</p>
                                <div className="text-[10px] text-gray-400 mt-1">
                                    {v.publishedBy} &middot; {new Date(v.publishedAt).toLocaleString()}
                                </div>
                            </div>
                        ))}
                        {block.versions.length === 0 && (
                            <p className="text-xs text-gray-400 text-center py-4">No versions published yet</p>
                        )}
                    </div>
                )}

                {tab === "feedback" && (
                    <div className="p-4 space-y-2">
                        {feedback.map(f => (
                            <div key={f.id} className="p-3 rounded-lg border border-gray-200 text-xs">
                                <div className="flex items-center gap-2 mb-1">
                                    <span className={`px-1.5 py-0.5 text-[10px] font-medium rounded ${
                                        f.type === "CORRECTION" ? "bg-amber-100 text-amber-700" :
                                        f.type === "FALSE_POSITIVE" ? "bg-red-100 text-red-700" :
                                        f.type === "MISSED" ? "bg-purple-100 text-purple-700" :
                                        "bg-blue-100 text-blue-700"
                                    }`}>{f.type.replace(/_/g, " ")}</span>
                                    <span className="text-gray-400">v{f.blockVersion}</span>
                                </div>
                                <p className="text-gray-700">{f.details}</p>
                                {f.originalValue && <p className="text-gray-400 mt-0.5">Was: {f.originalValue}</p>}
                                {f.correctedValue && <p className="text-green-700 mt-0.5">Should be: {f.correctedValue}</p>}
                                <div className="text-[10px] text-gray-400 mt-1">
                                    {f.userEmail} &middot; {new Date(f.timestamp).toLocaleString()}
                                </div>
                            </div>
                        ))}
                        {feedback.length === 0 && (
                            <p className="text-xs text-gray-400 text-center py-4">No feedback yet</p>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}

/* ── Create Block Modal ───────────────────────────── */

function CreateBlockModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [type, setType] = useState("PROMPT");
    const [content, setContent] = useState<Record<string, unknown>>({});
    const [saving, setSaving] = useState(false);

    const handleCreate = async () => {
        if (!name.trim()) { toast.error("Name is required"); return; }
        setSaving(true);
        try {
            await api.post("/admin/blocks", { name, description, type, content });
            toast.success("Block created");
            onCreated();
        } catch { toast.error("Failed to create block"); }
        finally { setSaving(false); }
    };

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onClose}>
            <div className="bg-white rounded-lg shadow-xl w-full max-w-2xl max-h-[85vh] overflow-y-auto p-6 space-y-4" onClick={e => e.stopPropagation()}>
                <div className="flex items-center justify-between">
                    <h3 className="text-lg font-semibold text-gray-900">New Block</h3>
                    <button onClick={onClose} aria-label="Close modal" className="text-gray-400 hover:text-gray-600"><X className="size-5" /></button>
                </div>

                <div className="grid grid-cols-2 gap-3">
                    <div className="col-span-2">
                        <label htmlFor="create-block-name" className="text-xs font-medium text-gray-700 block mb-1">Name</label>
                        <input id="create-block-name" value={name} onChange={e => setName(e.target.value)}
                            placeholder="e.g. Classification Prompt" className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                    </div>
                    <div className="col-span-2">
                        <label htmlFor="create-block-description" className="text-xs font-medium text-gray-700 block mb-1">Description</label>
                        <input id="create-block-description" value={description} onChange={e => setDescription(e.target.value)}
                            placeholder="What does this block do?" className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                    </div>
                    <div>
                        <label htmlFor="create-block-type" className="text-xs font-medium text-gray-700 block mb-1">Type</label>
                        <select id="create-block-type" value={type} onChange={e => setType(e.target.value)}
                            className="w-full text-sm border border-gray-300 rounded-md px-3 py-2">
                            {BLOCK_TYPES.map(t => <option key={t} value={t}>{TYPE_ICONS[t].label}</option>)}
                        </select>
                    </div>
                </div>

                <BlockFormEditor type={type} content={content} onChange={setContent} />

                <div className="flex gap-3 justify-end pt-2 border-t border-gray-200">
                    <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                    <button onClick={handleCreate} disabled={saving}
                        className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50">
                        {saving ? "Creating..." : "Create Block"}
                    </button>
                </div>
            </div>
        </div>
    );
}
