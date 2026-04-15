"use client";

import { useCallback, useEffect, useState } from "react";
import {
    Plus, Save, Trash2, Loader2, X, RefreshCw, Cog,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import { resolveIcon } from "@/components/pipeline-editor/iconMap";
import { ICON_MAP } from "@/components/pipeline-editor/iconMap";
import { COLOR_THEMES } from "@/components/pipeline-editor/colorThemes";
import type { NodeTypeDefinition } from "@/hooks/use-node-type-definitions";

/* ------------------------------------------------------------------ */
/*  Constants                                                          */
/* ------------------------------------------------------------------ */

const CATEGORIES = ["TRIGGER", "PROCESSING", "ACCELERATOR", "LOGIC", "ACTION", "ERROR_HANDLING"];
const EXECUTION_CATEGORIES = ["NOOP", "BUILT_IN", "ACCELERATOR", "GENERIC_HTTP", "ASYNC_BOUNDARY"];
const PHASES = ["PRE_CLASSIFICATION", "POST_CLASSIFICATION", "BOTH"];

/* ------------------------------------------------------------------ */
/*  Page component                                                     */
/* ------------------------------------------------------------------ */

export default function NodeTypesAdminPage() {
    const [definitions, setDefinitions] = useState<NodeTypeDefinition[]>([]);
    const [selected, setSelected] = useState<NodeTypeDefinition | null>(null);
    const [creating, setCreating] = useState(false);
    const [saving, setSaving] = useState(false);

    // Draft state for editing
    const [draft, setDraft] = useState<Partial<NodeTypeDefinition>>({});
    const [schemaJson, setSchemaJson] = useState("");
    const [httpConfigJson, setHttpConfigJson] = useState("");

    const load = useCallback(async () => {
        try {
            const { data } = await api.get<NodeTypeDefinition[]>("/admin/node-types/all");
            setDefinitions(data);
        } catch { toast.error("Failed to load node types"); }
    }, []);

    useEffect(() => { load(); }, [load]);

    const selectType = (def: NodeTypeDefinition) => {
        setSelected(def);
        setCreating(false);
        setDraft({ ...def });
        setSchemaJson(def.configSchema ? JSON.stringify(def.configSchema, null, 2) : "{}");
        setHttpConfigJson(def.httpConfig ? JSON.stringify(def.httpConfig, null, 2) : "");
    };

    const startCreate = () => {
        setSelected(null);
        setCreating(true);
        setDraft({
            key: "",
            displayName: "",
            description: "",
            category: "ACCELERATOR",
            sortOrder: 0,
            executionCategory: "GENERIC_HTTP",
            pipelinePhase: "PRE_CLASSIFICATION",
            iconName: "Cog",
            colorTheme: "gray",
            requiresDocReload: false,
            active: true,
        });
        setSchemaJson("{\n  \"properties\": {}\n}");
        setHttpConfigJson("{\n  \"defaultUrl\": \"http://localhost:8000\",\n  \"path\": \"/classify\",\n  \"method\": \"POST\",\n  \"defaultTimeoutMs\": 5000\n}");
    };

    const handleSave = async () => {
        setSaving(true);
        try {
            let configSchema: Record<string, unknown> | undefined;
            try { configSchema = schemaJson ? JSON.parse(schemaJson) : undefined; } catch { toast.error("Invalid config schema JSON"); setSaving(false); return; }

            let httpConfig: Record<string, unknown> | undefined;
            try { httpConfig = httpConfigJson ? JSON.parse(httpConfigJson) : undefined; } catch { toast.error("Invalid HTTP config JSON"); setSaving(false); return; }

            const payload = { ...draft, configSchema, httpConfig };

            if (creating) {
                if (!draft.key) { toast.error("Key is required"); setSaving(false); return; }
                await api.post("/admin/node-types", payload);
                toast.success("Node type created");
            } else if (selected) {
                await api.put(`/admin/node-types/${selected.key}`, payload);
                toast.success("Node type updated");
            }

            await load();
            setCreating(false);
        } catch (e: unknown) {
            const msg = e instanceof Error ? e.message : "Save failed";
            toast.error(msg);
        } finally { setSaving(false); }
    };

    const handleDelete = async () => {
        if (!selected || selected.builtIn) return;
        if (!confirm(`Delete node type "${selected.displayName}"?`)) return;
        try {
            await api.delete(`/admin/node-types/${selected.key}`);
            toast.success("Deleted");
            setSelected(null);
            await load();
        } catch { toast.error("Delete failed"); }
    };

    const isEditing = creating || selected != null;
    const editTarget = creating ? draft : draft;

    return (
        <div className="h-full flex">
            {/* Left: List */}
            <div className="w-80 border-r border-gray-200 bg-white flex flex-col">
                <div className="p-4 border-b border-gray-200 flex items-center justify-between">
                    <h2 className="text-sm font-bold text-gray-900">Node Types</h2>
                    <div className="flex items-center gap-1">
                        <button onClick={load} className="p-1.5 text-gray-400 hover:text-gray-600" title="Refresh">
                            <RefreshCw className="size-3.5" />
                        </button>
                        <button onClick={startCreate}
                            className="inline-flex items-center gap-1 px-2.5 py-1 bg-blue-600 text-white text-xs font-medium rounded-md hover:bg-blue-700">
                            <Plus className="size-3" /> New
                        </button>
                    </div>
                </div>
                <div className="flex-1 overflow-y-auto">
                    {CATEGORIES.map(cat => {
                        const items = definitions.filter(d => d.category === cat);
                        if (items.length === 0) return null;
                        return (
                            <div key={cat}>
                                <div className="px-4 py-2 bg-gray-50 border-b border-gray-100">
                                    <span className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">{cat.replace(/_/g, " ")}</span>
                                </div>
                                {items.map(d => {
                                    const Icon = resolveIcon(d.iconName);
                                    const theme = COLOR_THEMES[d.colorTheme];
                                    const isSelected = selected?.key === d.key;
                                    return (
                                        <button key={d.key} onClick={() => selectType(d)}
                                            className={`w-full text-left px-4 py-2.5 border-b border-gray-100 flex items-center gap-3 hover:bg-gray-50 transition-colors ${isSelected ? "bg-blue-50 border-l-2 border-l-blue-500" : ""}`}>
                                            <div className={`size-7 rounded flex items-center justify-center shrink-0 ${theme?.iconBg ?? "bg-gray-100"} ${theme?.text ?? "text-gray-500"}`}>
                                                <Icon className="size-3.5" />
                                            </div>
                                            <div className="min-w-0 flex-1">
                                                <div className="text-xs font-semibold text-gray-900 truncate">{d.displayName}</div>
                                                <div className="text-[10px] text-gray-400 truncate">{d.key} — {d.executionCategory}</div>
                                            </div>
                                            {d.builtIn && (
                                                <span className="text-[9px] font-medium text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded">Built-in</span>
                                            )}
                                        </button>
                                    );
                                })}
                            </div>
                        );
                    })}
                </div>
            </div>

            {/* Right: Detail */}
            <div className="flex-1 bg-gray-50 overflow-y-auto">
                {!isEditing ? (
                    <div className="flex items-center justify-center h-full text-gray-400 text-sm">
                        Select a node type or create a new one
                    </div>
                ) : (
                    <div className="max-w-2xl mx-auto p-6 space-y-6">
                        <div className="flex items-center justify-between">
                            <h3 className="text-lg font-bold text-gray-900">
                                {creating ? "New Node Type" : editTarget.displayName}
                            </h3>
                            <div className="flex items-center gap-2">
                                {selected && !selected.builtIn && (
                                    <button onClick={handleDelete}
                                        className="inline-flex items-center gap-1 px-3 py-1.5 border border-red-200 text-red-600 text-xs font-medium rounded-md hover:bg-red-50">
                                        <Trash2 className="size-3" /> Delete
                                    </button>
                                )}
                                <button onClick={() => { setSelected(null); setCreating(false); }}
                                    className="p-1.5 text-gray-400 hover:text-gray-600">
                                    <X className="size-4" />
                                </button>
                            </div>
                        </div>

                        {/* Basic fields */}
                        <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
                            <h4 className="text-xs font-bold text-gray-500 uppercase">Identity</h4>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="text-[10px] font-medium text-gray-500 block mb-1">Key</label>
                                    <input value={editTarget.key ?? ""} onChange={e => setDraft(d => ({ ...d, key: e.target.value }))}
                                        disabled={!creating} placeholder="myCustomNode"
                                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5 disabled:bg-gray-50 disabled:text-gray-500" />
                                </div>
                                <div>
                                    <label className="text-[10px] font-medium text-gray-500 block mb-1">Display Name</label>
                                    <input value={editTarget.displayName ?? ""} onChange={e => setDraft(d => ({ ...d, displayName: e.target.value }))}
                                        placeholder="My Custom Node"
                                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5" />
                                </div>
                            </div>
                            <div>
                                <label className="text-[10px] font-medium text-gray-500 block mb-1">Description</label>
                                <textarea value={editTarget.description ?? ""} onChange={e => setDraft(d => ({ ...d, description: e.target.value }))}
                                    rows={2} placeholder="What this node does..."
                                    className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5" />
                            </div>
                        </div>

                        {/* Execution */}
                        <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
                            <h4 className="text-xs font-bold text-gray-500 uppercase">Execution</h4>
                            <div className="grid grid-cols-3 gap-3">
                                <div>
                                    <label className="text-[10px] font-medium text-gray-500 block mb-1">Category</label>
                                    <select value={editTarget.category ?? ""} onChange={e => setDraft(d => ({ ...d, category: e.target.value }))}
                                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5">
                                        {CATEGORIES.map(c => <option key={c} value={c}>{c.replace(/_/g, " ")}</option>)}
                                    </select>
                                </div>
                                <div>
                                    <label className="text-[10px] font-medium text-gray-500 block mb-1">Execution Category</label>
                                    <select value={editTarget.executionCategory ?? ""} onChange={e => setDraft(d => ({ ...d, executionCategory: e.target.value }))}
                                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5">
                                        {EXECUTION_CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                                    </select>
                                </div>
                                <div>
                                    <label className="text-[10px] font-medium text-gray-500 block mb-1">Pipeline Phase</label>
                                    <select value={editTarget.pipelinePhase ?? ""} onChange={e => setDraft(d => ({ ...d, pipelinePhase: e.target.value }))}
                                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5">
                                        {PHASES.map(p => <option key={p} value={p}>{p.replace(/_/g, " ")}</option>)}
                                    </select>
                                </div>
                            </div>
                            <label className="flex items-center gap-2 text-sm text-gray-700">
                                <input type="checkbox" checked={editTarget.requiresDocReload ?? false}
                                    onChange={e => setDraft(d => ({ ...d, requiresDocReload: e.target.checked }))}
                                    className="rounded border-gray-300" />
                                Requires document reload after execution
                            </label>
                        </div>

                        {/* Visual */}
                        <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
                            <h4 className="text-xs font-bold text-gray-500 uppercase">Visual</h4>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="text-[10px] font-medium text-gray-500 block mb-1">Icon</label>
                                    <select value={editTarget.iconName ?? "Cog"} onChange={e => setDraft(d => ({ ...d, iconName: e.target.value }))}
                                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5">
                                        {Object.keys(ICON_MAP).map(name => <option key={name} value={name}>{name}</option>)}
                                    </select>
                                </div>
                                <div>
                                    <label className="text-[10px] font-medium text-gray-500 block mb-1">Color Theme</label>
                                    <select value={editTarget.colorTheme ?? "gray"} onChange={e => setDraft(d => ({ ...d, colorTheme: e.target.value }))}
                                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5">
                                        {Object.keys(COLOR_THEMES).map(name => <option key={name} value={name}>{name}</option>)}
                                    </select>
                                </div>
                            </div>
                            {/* Preview */}
                            {editTarget.iconName && editTarget.colorTheme && (() => {
                                const Icon = resolveIcon(editTarget.iconName);
                                const theme = COLOR_THEMES[editTarget.colorTheme ?? "gray"];
                                return theme ? (
                                    <div className={`inline-flex items-center gap-2 px-3 py-2 rounded-lg border-2 ${theme.bg} ${theme.border}`}>
                                        <div className={`size-8 rounded-md flex items-center justify-center ${theme.iconBg} ${theme.text}`}>
                                            <Icon className="size-4" />
                                        </div>
                                        <span className="text-xs font-bold text-gray-900">{editTarget.displayName || "Preview"}</span>
                                    </div>
                                ) : null;
                            })()}
                        </div>

                        {/* Config Schema */}
                        <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
                            <h4 className="text-xs font-bold text-gray-500 uppercase">Config Schema (JSON)</h4>
                            <p className="text-[10px] text-gray-400">Defines the configuration form shown in the pipeline editor inspector panel.</p>
                            <textarea value={schemaJson} onChange={e => setSchemaJson(e.target.value)}
                                rows={10} spellCheck={false}
                                className="w-full text-xs font-mono border border-gray-300 rounded-md px-3 py-2 bg-gray-50" />
                        </div>

                        {/* HTTP Config (for GENERIC_HTTP) */}
                        {editTarget.executionCategory === "GENERIC_HTTP" && (
                            <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
                                <h4 className="text-xs font-bold text-gray-500 uppercase">HTTP Config (JSON)</h4>
                                <p className="text-[10px] text-gray-400">Defines the external service call for GENERIC_HTTP nodes.</p>
                                <textarea value={httpConfigJson} onChange={e => setHttpConfigJson(e.target.value)}
                                    rows={10} spellCheck={false}
                                    className="w-full text-xs font-mono border border-gray-300 rounded-md px-3 py-2 bg-gray-50" />
                            </div>
                        )}

                        {/* Save */}
                        <div className="flex justify-end">
                            <button onClick={handleSave} disabled={saving}
                                className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-md hover:bg-blue-700 disabled:opacity-50">
                                {saving ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
                                {creating ? "Create" : "Save Changes"}
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
