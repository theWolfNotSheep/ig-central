"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
    Shield, FolderTree, Clock, HardDrive, ChevronDown, ChevronRight, Plus,
    Pencil, Trash2, Save, X, Loader2, Tag, Fingerprint, Check, XCircle,
    Database, GripVertical, Globe,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import ReasonModal from "@/components/reason-modal";
import FormModal, { FormField } from "@/components/form-modal";

type Tab = "sensitivities" | "taxonomy" | "policies" | "retention" | "storage" | "pii-types" | "metadata-schemas";

type SensitivityDef = {
    id?: string;
    key: string;
    displayName: string;
    description: string;
    level: number;
    colour: string;
    active: boolean;
    guidelines: string[];
    examples: string[];
};

type Category = {
    id?: string;
    classificationCode?: string;
    name: string;
    description: string;
    parentId?: string;
    level?: "FUNCTION" | "ACTIVITY" | "TRANSACTION";
    keywords: string[];
    defaultSensitivity: string;
    retentionScheduleId?: string;
    metadataSchemaId?: string;
    scopeNotes?: string;
    retentionTrigger?: string;
    retentionTriggerDescription?: string;
    legalCitation?: string;
    jurisdiction?: string;
    typicalRecords?: string[];
    retentionPeriodText?: string;
    owner?: string;
    custodian?: string;
    reviewCycleDuration?: string;
    lastReviewedAt?: string;
    nextReviewAt?: string;
    personalDataFlag?: boolean;
    vitalRecordFlag?: boolean;
    status?: "ACTIVE" | "INACTIVE" | "DEPRECATED";
    path?: string[];
    version?: number;
    active: boolean;
};

type Policy = {
    id?: string;
    name: string;
    description: string;
    version: number;
    active: boolean;
    rules: string[];
    applicableSensitivities: string[];
    enforcementActions: Record<string, string>;
};

type RetentionSchedule = {
    id?: string;
    name: string;
    description: string;
    retentionDays: number;
    dispositionAction: string;
    legalHoldOverride: boolean;
    regulatoryBasis: string;
};

type StorageTier = {
    id?: string;
    name: string;
    description: string;
    encryptionType: string;
    immutable: boolean;
    geographicallyRestricted: boolean;
    region?: string;
    allowedSensitivities: string[];
    maxFileSizeBytes: number;
    costPerGbMonth: number;
};

const TABS: { key: Tab; label: string; icon: React.ElementType }[] = [
    { key: "sensitivities", label: "Sensitivity Labels", icon: Tag },
    { key: "taxonomy", label: "Taxonomy", icon: FolderTree },
    { key: "policies", label: "Policies", icon: Shield },
    { key: "retention", label: "Retention", icon: Clock },
    { key: "storage", label: "Storage Tiers", icon: HardDrive },
    { key: "pii-types", label: "PII Types", icon: Fingerprint },
    { key: "metadata-schemas", label: "Metadata Schemas", icon: Database },
];

const SENSITIVITIES = ["PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"];
const DISPOSITIONS = ["DELETE", "ARCHIVE", "REVIEW", "ANONYMISE"];

const SC: Record<string, string> = {
    PUBLIC: "bg-green-100 text-green-700",
    INTERNAL: "bg-blue-100 text-blue-700",
    CONFIDENTIAL: "bg-amber-100 text-amber-700",
    RESTRICTED: "bg-red-100 text-red-700",
};

export default function GovernancePage() {
    const [tab, setTab] = useState<Tab>("sensitivities");

    // Sync tab with URL hash
    useEffect(() => {
        const hash = window.location.hash.replace("#", "") as Tab;
        if (TABS.some((t) => t.key === hash)) setTab(hash);
    }, []);

    const changeTab = (key: Tab) => {
        setTab(key);
        window.history.replaceState(null, "", `/governance#${key}`);
    };

    return (
        <>
            <div className="flex items-center justify-between mb-6">
                <h2 className="text-xl font-bold text-gray-900">Governance</h2>
                <div className="flex items-center gap-2">
                    <Link href="/governance/schema-coverage"
                        className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
                        <Database className="size-4" /> Schema Coverage
                    </Link>
                    <Link href="/governance/hub"
                        className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50 transition-colors">
                        <Globe className="size-4" /> Governance Hub
                    </Link>
                </div>
            </div>
            <div className="flex gap-1 mb-6 bg-gray-100 rounded-lg p-1 w-fit">
                {TABS.map((t) => (
                    <button key={t.key} onClick={() => changeTab(t.key)}
                        className={`inline-flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-md transition-colors ${
                            tab === t.key ? "bg-white text-gray-900 shadow-sm" : "text-gray-600 hover:text-gray-900"
                        }`}>
                        <t.icon className="size-4" />{t.label}
                    </button>
                ))}
            </div>
            {tab === "sensitivities" && <SensitivityPanel />}
            {tab === "taxonomy" && <TaxonomyPanel />}
            {tab === "policies" && <PoliciesPanel />}
            {tab === "retention" && <RetentionPanel />}
            {tab === "storage" && <StoragePanel />}
            {tab === "pii-types" && <PiiTypesPanel />}
            {tab === "metadata-schemas" && <MetadataSchemasPanel />}
        </>
    );
}

/* ── Shared ──────────────────────────────────────────── */

function SensitivityCheckboxes({ value, onChange }: { value: string[]; onChange: (v: string[]) => void }) {
    return (
        <div className="flex gap-2">
            {SENSITIVITIES.map((s) => (
                <label key={s} className="flex items-center gap-1.5 text-xs">
                    <input type="checkbox" checked={value.includes(s)}
                        onChange={(e) => onChange(e.target.checked ? [...value, s] : value.filter((v) => v !== s))}
                        className="rounded border-gray-300 text-blue-600 size-3.5" />
                    <span className={`px-1.5 py-0.5 rounded-full font-medium ${SC[s]}`}>{s}</span>
                </label>
            ))}
        </div>
    );
}

function TagInput({ tags, onChange, placeholder }: { tags: string[]; onChange: (tags: string[]) => void; placeholder?: string }) {
    const [text, setText] = useState("");

    const addTag = () => {
        const trimmed = text.trim();
        if (trimmed && !tags.includes(trimmed)) {
            onChange([...tags, trimmed]);
        }
        setText("");
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === "Enter" || e.key === ",") {
            e.preventDefault();
            addTag();
        }
        if (e.key === "Backspace" && text === "" && tags.length > 0) {
            onChange(tags.slice(0, -1));
        }
    };

    return (
        <div className="flex flex-wrap items-center gap-1.5 p-1.5 border border-gray-300 rounded-md focus-within:ring-2 focus-within:ring-blue-500 focus-within:border-blue-500 bg-white min-h-[34px]">
            {tags.map((tag) => (
                <span key={tag} className="inline-flex items-center gap-1 px-2 py-0.5 text-xs bg-blue-50 text-blue-700 rounded border border-blue-200">
                    {tag}
                    <button type="button" onClick={() => onChange(tags.filter((t) => t !== tag))} className="text-blue-400 hover:text-blue-700">
                        <X className="size-3" />
                    </button>
                </span>
            ))}
            <input
                value={text}
                onChange={(e) => setText(e.target.value)}
                onKeyDown={handleKeyDown}
                onBlur={addTag}
                placeholder={tags.length === 0 ? (placeholder ?? "Add tag...") : ""}
                className="flex-1 min-w-[120px] text-sm outline-none border-none p-0 focus:ring-0"
            />
        </div>
    );
}

const input = "w-full text-sm border border-gray-300 rounded-md px-3 py-1.5 focus:ring-2 focus:ring-blue-500 focus:border-blue-500";
const btnPrimary = "inline-flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-md hover:bg-blue-700 transition-colors";
const btnSecondary = "inline-flex items-center gap-1.5 px-3 py-1.5 border border-gray-300 text-gray-700 text-xs font-medium rounded-md hover:bg-gray-50 transition-colors";
const btnDanger = "inline-flex items-center gap-1.5 px-2 py-1.5 text-red-600 hover:bg-red-50 rounded-md transition-colors text-xs";

/* ── Sensitivity Labels ────────────────────────────────── */

const COLOUR_OPTIONS = [
    { label: "Green", value: "green" },
    { label: "Blue", value: "blue" },
    { label: "Amber", value: "amber" },
    { label: "Red", value: "red" },
    { label: "Purple", value: "purple" },
    { label: "Gray", value: "gray" },
];

const COLOUR_MAP: Record<string, string> = {
    green: "bg-green-100 text-green-700 border-green-300",
    blue: "bg-blue-100 text-blue-700 border-blue-300",
    amber: "bg-amber-100 text-amber-700 border-amber-300",
    red: "bg-red-100 text-red-700 border-red-300",
    purple: "bg-purple-100 text-purple-700 border-purple-300",
    gray: "bg-gray-100 text-gray-700 border-gray-300",
};

function SensitivityPanel() {
    const [defs, setDefs] = useState<SensitivityDef[]>([]);
    const [editing, setEditing] = useState<SensitivityDef | null>(null);
    const [saving, setSaving] = useState(false);
    const [dragIdx, setDragIdx] = useState<number | null>(null);
    const [dropIdx, setDropIdx] = useState<number | null>(null);
    const [reordering, setReordering] = useState(false);

    const load = useCallback(async () => {
        try { const { data } = await api.get("/admin/governance/sensitivities/all"); setDefs(data); }
        catch { toast.error("Failed to load sensitivity labels"); }
    }, []);
    useEffect(() => { load(); }, [load]);

    const sorted = [...defs].sort((a, b) => a.level - b.level);

    const newDef = (): SensitivityDef => ({
        key: "", displayName: "", description: "", level: defs.length, colour: "gray", active: true, guidelines: [""], examples: [""],
    });

    const handleSave = async () => {
        if (!editing || !editing.key.trim() || !editing.displayName.trim()) return;
        setSaving(true);
        try {
            if (editing.id) { await api.put(`/admin/governance/sensitivities/${editing.id}`, editing); }
            else { await api.post("/admin/governance/sensitivities", editing); }
            toast.success(`Sensitivity label ${editing.id ? "updated" : "created"}`);
            setEditing(null); load();
        } catch { toast.error("Save failed"); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: string) => {
        if (!confirm("Deactivate this sensitivity label?")) return;
        try { await api.delete(`/admin/governance/sensitivities/${id}`); toast.success("Deactivated"); load(); }
        catch { toast.error("Failed"); }
    };

    // Drag and drop reorder
    const handleDragStart = (idx: number) => {
        setDragIdx(idx);
    };

    const handleDragOver = (e: React.DragEvent, idx: number) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = "move";
        setDropIdx(idx);
    };

    const handleDragLeave = () => {
        setDropIdx(null);
    };

    const handleDrop = async (targetIdx: number) => {
        if (dragIdx === null || dragIdx === targetIdx) {
            setDragIdx(null);
            setDropIdx(null);
            return;
        }

        // Reorder: remove dragged item, insert at target position
        const reordered = [...sorted];
        const [moved] = reordered.splice(dragIdx, 1);
        reordered.splice(targetIdx, 0, moved);

        // Assign new levels (0, 1, 2, ...)
        const updated = reordered.map((def, i) => ({ ...def, level: i }));

        // Optimistic update
        setDefs(updated);
        setDragIdx(null);
        setDropIdx(null);

        // Save all reordered levels
        setReordering(true);
        try {
            await Promise.all(
                updated
                    .filter((def, i) => def.level !== sorted.findIndex(s => s.id === def.id))
                    .map(def => api.put(`/admin/governance/sensitivities/${def.id}`, def))
            );
            toast.success("Sensitivity levels reordered");
            load();
        } catch {
            toast.error("Failed to save new order");
            load(); // revert
        } finally { setReordering(false); }
    };

    const handleDragEnd = () => {
        setDragIdx(null);
        setDropIdx(null);
    };

    return (
        <div className="space-y-4">
            <div className="flex justify-between items-center">
                <div>
                    <p className="text-sm text-gray-500">Define the sensitivity labels the LLM uses to classify document access levels</p>
                    <p className="text-[10px] text-gray-400 mt-0.5">Drag to reorder priority — level 0 is lowest sensitivity, highest number is most restricted</p>
                </div>
                <button onClick={() => setEditing(newDef())} className={btnPrimary}><Plus className="size-3.5" />Add Label</button>
            </div>

            <FormModal title={editing?.id ? "Edit Sensitivity Label" : "New Sensitivity Label"} open={!!editing} onClose={() => setEditing(null)} width="lg"
                footer={<>
                    <button onClick={handleSave} disabled={saving} className={btnPrimary}>{saving ? <Loader2 className="size-3.5 animate-spin" /> : <Save className="size-3.5" />}Save</button>
                    <button onClick={() => setEditing(null)} className={btnSecondary}><X className="size-3.5" />Cancel</button>
                </>}>
                {editing && (<>
                    <div className="grid grid-cols-3 gap-3">
                        <FormField label="Key (e.g. CONFIDENTIAL)" id="sens-key" required>
                            <input id="sens-key" value={editing.key} onChange={(e) => setEditing({ ...editing, key: e.target.value.toUpperCase().replace(/[^A-Z_]/g, "") })}
                                className={input} disabled={!!editing.id} placeholder="CONFIDENTIAL" />
                        </FormField>
                        <FormField label="Display Name" id="sens-display-name" required>
                            <input id="sens-display-name" value={editing.displayName} onChange={(e) => setEditing({ ...editing, displayName: e.target.value })} className={input} />
                        </FormField>
                        <FormField label="Colour" id="sens-colour">
                            <select id="sens-colour" value={editing.colour} onChange={(e) => setEditing({ ...editing, colour: e.target.value })} className={input}>
                                {COLOUR_OPTIONS.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
                            </select>
                        </FormField>
                    </div>
                    <FormField label="Description" id="sens-description">
                        <input id="sens-description" value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })} className={input}
                            placeholder="What does this sensitivity level mean?" />
                    </FormField>
                    <FormField label="Guidelines (when to assign this level)" id="guideline-0">
                        <div className="space-y-1">
                            {editing.guidelines.map((g, i) => (
                                <div key={i} className="flex gap-1">
                                    <input id={`guideline-${i}`} value={g} onChange={(e) => { const gd = [...editing.guidelines]; gd[i] = e.target.value; setEditing({ ...editing, guidelines: gd }); }}
                                        className={input} placeholder="Guideline..." />
                                    <button onClick={() => setEditing({ ...editing, guidelines: editing.guidelines.filter((_, j) => j !== i) })} className={btnDanger}><Trash2 className="size-3" /></button>
                                </div>
                            ))}
                            <button onClick={() => setEditing({ ...editing, guidelines: [...editing.guidelines, ""] })} className="text-xs text-blue-600 hover:text-blue-800">+ Add guideline</button>
                        </div>
                    </FormField>
                    <FormField label="Examples (document types at this level)" id="sens-example-0">
                        <div className="space-y-1">
                            {editing.examples.map((e, i) => (
                                <div key={i} className="flex gap-1">
                                    <input id={`sens-example-${i}`} value={e} onChange={(ev) => { const ex = [...editing.examples]; ex[i] = ev.target.value; setEditing({ ...editing, examples: ex }); }}
                                        className={input} placeholder="Example..." />
                                    <button onClick={() => setEditing({ ...editing, examples: editing.examples.filter((_, j) => j !== i) })} className={btnDanger}><Trash2 className="size-3" /></button>
                                </div>
                            ))}
                            <button onClick={() => setEditing({ ...editing, examples: [...editing.examples, ""] })} className="text-xs text-blue-600 hover:text-blue-800">+ Add example</button>
                        </div>
                    </FormField>
                </>)}
            </FormModal>

            <div className="space-y-1">
                {sorted.map((def, idx) => (
                    <div key={def.id}
                        draggable
                        onDragStart={() => handleDragStart(idx)}
                        onDragOver={(e) => handleDragOver(e, idx)}
                        onDragLeave={handleDragLeave}
                        onDrop={() => handleDrop(idx)}
                        onDragEnd={handleDragEnd}
                        className={`group bg-white rounded-lg shadow-sm border border-gray-200 transition-all duration-150 ${
                            dragIdx === idx ? "opacity-40 scale-[0.98]" : ""
                        } ${dropIdx === idx && dragIdx !== idx ? "ring-2 ring-blue-400 ring-offset-2" : ""
                        } ${reordering ? "pointer-events-none" : ""}`}
                    >
                        {/* Drop indicator line */}
                        {dropIdx === idx && dragIdx !== null && dragIdx > idx && (
                            <div className="h-0.5 bg-blue-500 rounded-full -mt-0.5 mx-4" />
                        )}
                        <div className="flex items-start gap-0 px-2 py-3">
                            {/* Drag handle */}
                            <div className="flex flex-col items-center justify-center px-2 py-2 cursor-grab active:cursor-grabbing shrink-0 self-center">
                                <GripVertical className="size-5 text-gray-300 group-hover:text-gray-500 transition-colors" />
                            </div>

                            {/* Level badge */}
                            <div className="flex flex-col items-center justify-center w-10 shrink-0 self-center mr-3">
                                <span className="text-lg font-bold text-gray-300">{def.level}</span>
                            </div>

                            {/* Content */}
                            <div className="flex-1 min-w-0 py-1">
                                <div className="flex items-center gap-3 mb-1">
                                    <span className={`inline-flex items-center px-3 py-1 text-sm font-semibold rounded-lg border ${COLOUR_MAP[def.colour] ?? COLOUR_MAP.gray}`}>
                                        {def.displayName}
                                    </span>
                                    <span className="text-xs text-gray-400 font-mono">{def.key}</span>
                                    {!def.active && <span className="px-2 py-0.5 text-xs bg-gray-100 text-gray-500 rounded-full">Inactive</span>}
                                </div>
                                <p className="text-sm text-gray-600">{def.description}</p>
                                {def.guidelines && def.guidelines.filter(Boolean).length > 0 && (
                                    <div className="mt-2">
                                        <ul className="space-y-0.5">
                                            {def.guidelines.filter(Boolean).map((g, i) => (
                                                <li key={i} className="text-xs text-gray-500 flex items-start gap-1.5">
                                                    <span className="text-blue-400 mt-0.5 shrink-0">•</span>{g}
                                                </li>
                                            ))}
                                        </ul>
                                    </div>
                                )}
                                {def.examples && def.examples.filter(Boolean).length > 0 && (
                                    <div className="flex flex-wrap gap-1 mt-2">
                                        {def.examples.filter(Boolean).map((e, i) => (
                                            <span key={i} className="px-2 py-0.5 text-[10px] bg-gray-100 text-gray-500 rounded">{e}</span>
                                        ))}
                                    </div>
                                )}
                            </div>

                            {/* Actions */}
                            <div className="flex gap-1 shrink-0 self-start pt-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                <button onClick={() => setEditing({ ...def })} className={btnSecondary}><Pencil className="size-3.5" /></button>
                                <button onClick={() => handleDelete(def.id!)} className={btnDanger}><Trash2 className="size-3.5" /></button>
                            </div>
                        </div>
                        {/* Drop indicator line below */}
                        {dropIdx === idx && dragIdx !== null && dragIdx < idx && (
                            <div className="h-0.5 bg-blue-500 rounded-full -mb-0.5 mx-4" />
                        )}
                    </div>
                ))}
            </div>

            {reordering && (
                <div className="flex items-center gap-2 text-xs text-blue-600">
                    <Loader2 className="size-3 animate-spin" /> Saving new order...
                </div>
            )}
        </div>
    );
}

/* ── Taxonomy ──────────────────────────────────────────── */

function TaxonomyPanel() {
    const [categories, setCategories] = useState<Category[]>([]);
    const [expanded, setExpanded] = useState<Set<string>>(new Set());
    const [editing, setEditing] = useState<Category | null>(null);
    const [saving, setSaving] = useState(false);
    const [draggingId, setDraggingId] = useState<string | null>(null);
    const [dragOverId, setDragOverId] = useState<string | null>(null);
    const [moving, setMoving] = useState(false);

    const load = useCallback(async () => {
        try { const { data } = await api.get("/admin/governance/taxonomy"); setCategories(data); }
        catch { toast.error("Failed to load taxonomy"); }
    }, []);
    useEffect(() => { load(); }, [load]);

    const roots = categories.filter((c) => !c.parentId);
    const childrenOf = (pid: string) => categories.filter((c) => c.parentId === pid);

    const toggle = (id: string) => setExpanded((p) => { const n = new Set(p); n.has(id) ? n.delete(id) : n.add(id); return n; });

    const moveCategory = async (sourceId: string, newParentId: string | null) => {
        if (sourceId === newParentId) return;
        setMoving(true);
        try {
            await api.post(`/admin/governance/taxonomy/${sourceId}/move`, { newParentId });
            toast.success(newParentId
                ? `Moved under ${categories.find(c => c.id === newParentId)?.name ?? "parent"}`
                : "Moved to root");
            await load();
        } catch (err: unknown) {
            const e = err as { response?: { status?: number; data?: { error?: string; detail?: string } } };
            const msg = e?.response?.data?.detail ?? e?.response?.data?.error;
            toast.error(`Move failed${msg ? ": " + msg : ""}`);
        } finally {
            setMoving(false);
            setDraggingId(null);
            setDragOverId(null);
        }
    };

    const newCategory = (parentId?: string): Category => {
        // Infer level from depth: no parent = FUNCTION, parent is root = ACTIVITY, else TRANSACTION
        let level: Category["level"] = "FUNCTION";
        if (parentId) {
            const parent = categories.find((c) => c.id === parentId);
            level = parent?.level === "FUNCTION" ? "ACTIVITY" : "TRANSACTION";
        }
        return {
            name: "", description: "", keywords: [], defaultSensitivity: "INTERNAL", active: true, parentId,
            level,
            status: "ACTIVE",
            personalDataFlag: false,
            vitalRecordFlag: false,
        };
    };

    const handleSave = async () => {
        if (!editing || !editing.name.trim()) return;
        setSaving(true);
        try {
            if (editing.id) {
                await api.put(`/admin/governance/taxonomy/${editing.id}`, editing);
            } else {
                await api.post("/admin/governance/taxonomy", editing);
            }
            toast.success(`Category ${editing.id ? "updated" : "created"}`);
            setEditing(null);
            load();
        } catch { toast.error("Save failed"); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: string) => {
        if (!confirm("Deactivate this category?")) return;
        try { await api.delete(`/admin/governance/taxonomy/${id}`); toast.success("Category deactivated"); load(); }
        catch { toast.error("Delete failed"); }
    };

    // Drop on the root drop-zone promotes a node to top-level (parentId = null).
    const onRootDragOver = (e: React.DragEvent) => {
        if (!draggingId) return;
        e.preventDefault();
        e.dataTransfer.dropEffect = "move";
    };
    const onRootDrop = (e: React.DragEvent) => {
        e.preventDefault();
        if (draggingId) moveCategory(draggingId, null);
    };

    return (
        <div className="space-y-4">
            <div className="flex justify-between items-center">
                <p className="text-sm text-gray-500">
                    Categories the LLM uses to classify documents.
                    {" "}<span className="text-gray-400">Drag a row onto another to reparent, or onto the root strip to promote.</span>
                </p>
                <button onClick={() => setEditing(newCategory())} className={btnPrimary}><Plus className="size-3.5" />Add Category</button>
            </div>

            <CategoryForm category={editing} categories={categories} onChange={setEditing}
                onSave={handleSave} onCancel={() => setEditing(null)} saving={saving} />

            {/* Root drop-zone — drop a node here to promote it to top-level */}
            <div
                onDragOver={onRootDragOver}
                onDrop={onRootDrop}
                className={`text-xs border-2 border-dashed rounded-md px-3 py-1.5 transition-colors ${
                    draggingId
                        ? "border-blue-400 bg-blue-50 text-blue-700"
                        : "border-transparent text-transparent select-none"
                }`}>
                {draggingId ? "↑ Drop here to promote to top-level" : "drop-zone"}
            </div>

            <div className="bg-white rounded-lg shadow-sm border border-gray-200 divide-y divide-gray-100 relative">
                {moving && (
                    <div className="absolute inset-0 bg-white/60 z-10 flex items-center justify-center">
                        <Loader2 className="size-5 animate-spin text-gray-400" />
                    </div>
                )}
                {roots.map((cat) => (
                    <TaxonomyNode key={cat.id} cat={cat} depth={0}
                        childrenOf={childrenOf} expanded={expanded} toggle={toggle}
                        onAdd={(parentId) => setEditing(newCategory(parentId))}
                        onEdit={(c) => setEditing({ ...c })}
                        onDelete={handleDelete}
                        draggingId={draggingId} dragOverId={dragOverId}
                        onDragStart={setDraggingId}
                        onDragEnd={() => { setDraggingId(null); setDragOverId(null); }}
                        onDragOverNode={setDragOverId}
                        onDropOnNode={(targetId) => {
                            if (draggingId) moveCategory(draggingId, targetId);
                        }} />
                ))}
            </div>
        </div>
    );
}

function TaxonomyNode({ cat, depth, childrenOf, expanded, toggle, onAdd, onEdit, onDelete,
                       draggingId, dragOverId, onDragStart, onDragEnd, onDragOverNode, onDropOnNode }: {
    cat: Category; depth: number;
    childrenOf: (pid: string) => Category[];
    expanded: Set<string>; toggle: (id: string) => void;
    onAdd: (parentId: string) => void;
    onEdit: (c: Category) => void;
    onDelete: (id: string) => void;
    draggingId: string | null;
    dragOverId: string | null;
    onDragStart: (id: string) => void;
    onDragEnd: () => void;
    onDragOverNode: (id: string | null) => void;
    onDropOnNode: (id: string) => void;
}) {
    const children = childrenOf(cat.id!);
    const hasChildren = children.length > 0;
    const isExpanded = expanded.has(cat.id!);
    const indent = depth === 0 ? "pl-6" : depth === 1 ? "pl-14" : "pl-22";
    const isDragging = draggingId === cat.id;
    const isDragOver = dragOverId === cat.id && draggingId !== cat.id;
    const bg = isDragOver
        ? "bg-blue-50"
        : isDragging
            ? "opacity-40"
            : depth === 0 ? "hover:bg-gray-50" : depth === 1 ? "bg-gray-50/50" : "bg-gray-100/40";
    const textSize = depth === 0 ? "font-medium text-gray-900" : depth === 1 ? "font-medium text-gray-800 text-sm" : "text-gray-700 text-sm";

    const handleDragStart = (e: React.DragEvent) => {
        if (!cat.id) return;
        e.dataTransfer.effectAllowed = "move";
        e.dataTransfer.setData("text/plain", cat.id);
        onDragStart(cat.id);
    };
    const handleDragOver = (e: React.DragEvent) => {
        if (!cat.id || !draggingId || draggingId === cat.id) return;
        e.preventDefault();
        e.dataTransfer.dropEffect = "move";
        if (dragOverId !== cat.id) onDragOverNode(cat.id);
    };
    const handleDragLeave = () => {
        if (dragOverId === cat.id) onDragOverNode(null);
    };
    const handleDrop = (e: React.DragEvent) => {
        e.preventDefault();
        e.stopPropagation();
        if (cat.id && draggingId && draggingId !== cat.id) onDropOnNode(cat.id);
    };

    return (
        <div>
            <div
                draggable={!!cat.id}
                onDragStart={handleDragStart}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDragEnd={onDragEnd}
                onDrop={handleDrop}
                className={`flex items-center gap-3 px-6 py-3 ${indent} ${bg} ${depth > 0 ? "border-t border-gray-100" : ""} cursor-move ${isDragOver ? "ring-1 ring-blue-300" : ""}`}>
                <button onClick={() => toggle(cat.id!)} className="shrink-0">
                    {hasChildren ? (isExpanded ? <ChevronDown className="size-4 text-gray-400" /> : <ChevronRight className="size-4 text-gray-400" />) : <div className="w-4" />}
                </button>
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                        {cat.classificationCode && <span className="font-mono text-xs text-blue-600 bg-blue-50 px-1.5 py-0.5 rounded">{cat.classificationCode}</span>}
                        <span className={textSize}>{cat.name}</span>
                        {cat.level && <span className="px-1.5 py-0.5 text-xs text-gray-500 bg-gray-50 rounded">{cat.level.toLowerCase()}</span>}
                        <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${SC[cat.defaultSensitivity] ?? ""}`}>{cat.defaultSensitivity}</span>
                        {cat.jurisdiction && <span className="px-1.5 py-0.5 text-xs bg-indigo-50 text-indigo-700 rounded">{cat.jurisdiction}</span>}
                        {cat.personalDataFlag && <span className="px-1.5 py-0.5 text-xs bg-purple-50 text-purple-700 rounded-full">PII</span>}
                        {cat.vitalRecordFlag && <span className="px-1.5 py-0.5 text-xs bg-red-50 text-red-700 rounded-full">Vital</span>}
                        {cat.status === "INACTIVE" && <span className="px-2 py-0.5 text-xs bg-gray-100 text-gray-500 rounded-full">Inactive</span>}
                        {cat.status === "DEPRECATED" && <span className="px-2 py-0.5 text-xs bg-amber-100 text-amber-700 rounded-full">Deprecated</span>}
                    </div>
                    <p className="text-xs text-gray-500 mt-0.5">{cat.description}</p>
                    {cat.retentionPeriodText && <p className="text-xs text-gray-500 mt-0.5">Retention: {cat.retentionPeriodText}{cat.legalCitation && <> — <span className="text-gray-400">{cat.legalCitation}</span></>}</p>}
                    {cat.typicalRecords && cat.typicalRecords.length > 0 && <p className="text-xs text-gray-400 mt-0.5">Typical: {cat.typicalRecords.join(", ")}</p>}
                    {cat.scopeNotes && <p className="text-xs text-gray-400 mt-0.5 italic">{cat.scopeNotes}</p>}
                    {cat.keywords.length > 0 && <div className="flex flex-wrap gap-1 mt-1">{cat.keywords.map((kw) => <span key={kw} className="px-2 py-0.5 text-xs bg-gray-100 text-gray-600 rounded">{kw}</span>)}</div>}
                </div>
                <div className="flex gap-1 shrink-0">
                    {cat.level !== "TRANSACTION" && <button onClick={() => onAdd(cat.id!)} className={btnSecondary} title="Add child"><Plus className="size-3.5" /></button>}
                    <button onClick={() => onEdit(cat)} className={btnSecondary} title="Edit"><Pencil className="size-3.5" /></button>
                    <button onClick={() => onDelete(cat.id!)} className={btnDanger} title="Deactivate"><Trash2 className="size-3.5" /></button>
                </div>
            </div>
            {isExpanded && children.map((child) => (
                <TaxonomyNode key={child.id} cat={child} depth={depth + 1}
                    childrenOf={childrenOf} expanded={expanded} toggle={toggle}
                    onAdd={onAdd} onEdit={onEdit} onDelete={onDelete}
                    draggingId={draggingId} dragOverId={dragOverId}
                    onDragStart={onDragStart} onDragEnd={onDragEnd}
                    onDragOverNode={onDragOverNode} onDropOnNode={onDropOnNode} />
            ))}
        </div>
    );
}

const TAXONOMY_LEVELS = ["FUNCTION", "ACTIVITY", "TRANSACTION"] as const;
const NODE_STATUSES = ["ACTIVE", "INACTIVE", "DEPRECATED"] as const;
const RETENTION_TRIGGERS = ["DATE_CREATED", "DATE_LAST_MODIFIED", "DATE_CLOSED", "EVENT_BASED", "END_OF_FINANCIAL_YEAR", "SUPERSEDED"] as const;

function CategoryForm({ category, categories, onChange, onSave, onCancel, saving }: {
    category: Category | null; categories: Category[]; onChange: (c: Category) => void;
    onSave: () => void; onCancel: () => void; saving: boolean;
}) {
    // Build parent options: all non-TRANSACTION categories, excluding self, with hierarchy indentation
    const parentOptions: { id: string; label: string; depth: number }[] = [];
    const buildParentOpts = (parentId: string | undefined, depth: number) => {
        categories
            .filter((c) => c.parentId === (parentId ?? undefined) && c.id !== category?.id && c.level !== "TRANSACTION")
            .forEach((c) => {
                const prefix = c.classificationCode ? `[${c.classificationCode}] ` : "";
                parentOptions.push({ id: c.id!, label: `${"  ".repeat(depth)}${prefix}${c.name}`, depth });
                buildParentOpts(c.id, depth + 1);
            });
    };
    if (category) {
        // roots have parentId undefined/null
        categories.filter((c) => !c.parentId && c.id !== category.id && c.level !== "TRANSACTION").forEach((c) => {
            const prefix = c.classificationCode ? `[${c.classificationCode}] ` : "";
            parentOptions.push({ id: c.id!, label: `${prefix}${c.name}`, depth: 0 });
            buildParentOpts(c.id, 1);
        });
    }

    return (
        <FormModal title={category?.id ? "Edit Category" : "New Category"} open={!!category} onClose={onCancel} width="lg"
            footer={<>
                <button onClick={onSave} disabled={saving} className={btnPrimary}>{saving ? <Loader2 className="size-3.5 animate-spin" /> : <Save className="size-3.5" />}Save</button>
                <button onClick={onCancel} className={btnSecondary}><X className="size-3.5" />Cancel</button>
            </>}>
            {category && (<>
                <div className="grid grid-cols-3 gap-3">
                    <FormField label="Name" id="cat-name" required>
                        <input id="cat-name" value={category.name} onChange={(e) => onChange({ ...category, name: e.target.value })} className={input} />
                    </FormField>
                    <FormField label="Classification Code" id="cat-code" hint="Auto-generated if blank">
                        <input id="cat-code" value={category.classificationCode ?? ""} onChange={(e) => onChange({ ...category, classificationCode: e.target.value })} className={input} placeholder="e.g. HR-EMP" />
                    </FormField>
                    <FormField label="Level" id="cat-level">
                        <select id="cat-level" value={category.level ?? ""} onChange={(e) => onChange({ ...category, level: e.target.value as Category["level"] })} className={input}>
                            {TAXONOMY_LEVELS.map((l) => <option key={l} value={l}>{l.charAt(0) + l.slice(1).toLowerCase()}</option>)}
                        </select>
                    </FormField>
                </div>
                <div className="grid grid-cols-2 gap-3">
                    <FormField label="Parent" id="cat-parent">
                        <select id="cat-parent" value={category.parentId ?? ""} onChange={(e) => onChange({ ...category, parentId: e.target.value || undefined })} className={input}>
                            <option value="">Root (no parent)</option>
                            {parentOptions.map((opt) => <option key={opt.id} value={opt.id}>{opt.label}</option>)}
                        </select>
                    </FormField>
                    <FormField label="Status" id="cat-status">
                        <select id="cat-status" value={category.status ?? "ACTIVE"} onChange={(e) => onChange({ ...category, status: e.target.value as Category["status"] })} className={input}>
                            {NODE_STATUSES.map((s) => <option key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</option>)}
                        </select>
                    </FormField>
                </div>
                <FormField label="Description" id="cat-description">
                    <input id="cat-description" value={category.description} onChange={(e) => onChange({ ...category, description: e.target.value })} className={input} />
                </FormField>
                <FormField label="Scope Notes" id="cat-scope" hint="Inclusion/exclusion guidance for classifiers">
                    <textarea id="cat-scope" value={category.scopeNotes ?? ""} onChange={(e) => onChange({ ...category, scopeNotes: e.target.value })} className={input} rows={2} />
                </FormField>
                <FormField label="Keywords" id="cat-keywords">
                    <TagInput tags={category.keywords} onChange={(kw) => onChange({ ...category, keywords: kw })} placeholder="Type keyword and press Enter..." />
                </FormField>
                <div className="grid grid-cols-2 gap-3">
                    <FormField label="Default Sensitivity" id="cat-sensitivity">
                        <select id="cat-sensitivity" value={category.defaultSensitivity} onChange={(e) => onChange({ ...category, defaultSensitivity: e.target.value })} className={input}>
                            {SENSITIVITIES.map((s) => <option key={s} value={s}>{s}</option>)}
                        </select>
                    </FormField>
                    <FormField label="Retention Trigger" id="cat-trigger">
                        <select id="cat-trigger" value={category.retentionTrigger ?? ""} onChange={(e) => onChange({ ...category, retentionTrigger: e.target.value || undefined })} className={input}>
                            <option value="">Inherit from parent</option>
                            {RETENTION_TRIGGERS.map((t) => <option key={t} value={t}>{t.replace(/_/g, " ").toLowerCase()}</option>)}
                        </select>
                    </FormField>
                </div>
                {category.retentionTrigger === "EVENT_BASED" && (
                    <FormField label="Trigger Description" id="cat-trigger-desc" hint="Describe the triggering event">
                        <input id="cat-trigger-desc" value={category.retentionTriggerDescription ?? ""} onChange={(e) => onChange({ ...category, retentionTriggerDescription: e.target.value })} className={input} />
                    </FormField>
                )}
                <div className="grid grid-cols-2 gap-3">
                    <FormField label="Jurisdiction" id="cat-jurisdiction">
                        <input id="cat-jurisdiction" value={category.jurisdiction ?? ""} onChange={(e) => onChange({ ...category, jurisdiction: e.target.value })} className={input} placeholder="e.g. US, UK, EU" />
                    </FormField>
                    <FormField label="Retention Period" id="cat-retention-text" hint="Human-readable">
                        <input id="cat-retention-text" value={category.retentionPeriodText ?? ""} onChange={(e) => onChange({ ...category, retentionPeriodText: e.target.value })} className={input} placeholder="e.g. 7 years after termination" />
                    </FormField>
                </div>
                <FormField label="Legal Citation" id="cat-legal" hint="Legal/regulatory reference">
                    <input id="cat-legal" value={category.legalCitation ?? ""} onChange={(e) => onChange({ ...category, legalCitation: e.target.value })} className={input} placeholder="e.g. IRS requirements (IRC §6001)" />
                </FormField>
                <div className="grid grid-cols-2 gap-3">
                    <FormField label="Owner" id="cat-owner" hint="Business owner">
                        <input id="cat-owner" value={category.owner ?? ""} onChange={(e) => onChange({ ...category, owner: e.target.value })} className={input} />
                    </FormField>
                    <FormField label="Custodian" id="cat-custodian" hint="Operational custodian">
                        <input id="cat-custodian" value={category.custodian ?? ""} onChange={(e) => onChange({ ...category, custodian: e.target.value })} className={input} />
                    </FormField>
                </div>
                <div className="grid grid-cols-3 gap-3">
                    <FormField label="Review Cycle" id="cat-review" hint="ISO 8601 duration">
                        <input id="cat-review" value={category.reviewCycleDuration ?? ""} onChange={(e) => onChange({ ...category, reviewCycleDuration: e.target.value })} className={input} placeholder="e.g. P1Y" />
                    </FormField>
                    <div className="flex items-center gap-4 pt-6">
                        <label className="flex items-center gap-2 text-sm">
                            <input type="checkbox" checked={category.personalDataFlag ?? false} onChange={(e) => onChange({ ...category, personalDataFlag: e.target.checked })} className="rounded border-gray-300" />
                            Personal data
                        </label>
                        <label className="flex items-center gap-2 text-sm">
                            <input type="checkbox" checked={category.vitalRecordFlag ?? false} onChange={(e) => onChange({ ...category, vitalRecordFlag: e.target.checked })} className="rounded border-gray-300" />
                            Vital record
                        </label>
                    </div>
                </div>
            </>)}
        </FormModal>
    );
}

/* ── Policies ──────────────────────────────────────────── */

function PoliciesPanel() {
    const [policies, setPolicies] = useState<Policy[]>([]);
    const [expanded, setExpanded] = useState<string | null>(null);
    const [editing, setEditing] = useState<Policy | null>(null);
    const [saving, setSaving] = useState(false);

    const load = useCallback(async () => {
        try { const { data } = await api.get("/admin/governance/policies"); setPolicies(data); }
        catch { toast.error("Failed to load policies"); }
    }, []);
    useEffect(() => { load(); }, [load]);

    const newPolicy = (): Policy => ({ name: "", description: "", version: 1, active: true, rules: [""], applicableSensitivities: [], enforcementActions: {} });

    const handleSave = async () => {
        if (!editing || !editing.name.trim()) return;
        setSaving(true);
        try {
            if (editing.id) { await api.put(`/admin/governance/policies/${editing.id}`, editing); }
            else { await api.post("/admin/governance/policies", editing); }
            toast.success(`Policy ${editing.id ? "updated" : "created"}`);
            setEditing(null); load();
        } catch { toast.error("Save failed"); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: string) => {
        if (!confirm("Deactivate this policy?")) return;
        try { await api.delete(`/admin/governance/policies/${id}`); toast.success("Policy deactivated"); load(); }
        catch { toast.error("Delete failed"); }
    };

    return (
        <div className="space-y-4">
            <div className="flex justify-between items-center">
                <p className="text-sm text-gray-500">Rules the LLM follows when classifying and enforcing governance</p>
                <button onClick={() => setEditing(newPolicy())} className={btnPrimary}><Plus className="size-3.5" />Add Policy</button>
            </div>

            <FormModal title={editing?.id ? "Edit Policy" : "New Policy"} open={!!editing} onClose={() => setEditing(null)} width="lg"
                footer={editing ? <>
                    <button onClick={handleSave} disabled={saving} className={btnPrimary}>{saving ? <Loader2 className="size-3.5 animate-spin" /> : <Save className="size-3.5" />}Save</button>
                    <button onClick={() => setEditing(null)} className={btnSecondary}><X className="size-3.5" />Cancel</button>
                </> : undefined}>
                {editing && (<>
                    <div className="grid grid-cols-2 gap-3">
                        <FormField label="Name" id="policy-name" required>
                            <input id="policy-name" value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} className={input} />
                        </FormField>
                        <FormField label="Active" id="policy-active">
                            <select id="policy-active" value={String(editing.active)} onChange={(e) => setEditing({ ...editing, active: e.target.value === "true" })} className={input}>
                                <option value="true">Active</option><option value="false">Inactive</option>
                            </select>
                        </FormField>
                    </div>
                    <FormField label="Description" id="policy-description">
                        <input id="policy-description" value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })} className={input} />
                    </FormField>
                    <FormField label="Rules" id="rule-0">
                        <div className="space-y-1">
                            {editing.rules.map((r, i) => (
                                <div key={i} className="flex gap-1">
                                    <input id={`rule-${i}`} value={r} onChange={(e) => { const rules = [...editing.rules]; rules[i] = e.target.value; setEditing({ ...editing, rules }); }} className={input} placeholder="Rule text..." />
                                    <button onClick={() => setEditing({ ...editing, rules: editing.rules.filter((_, j) => j !== i) })} className={btnDanger}><Trash2 className="size-3" /></button>
                                </div>
                            ))}
                            <button onClick={() => setEditing({ ...editing, rules: [...editing.rules, ""] })} className="text-xs text-blue-600 hover:text-blue-800">+ Add rule</button>
                        </div>
                    </FormField>
                    <FormField label="Applicable Sensitivities" id="policy-sensitivities">
                        <SensitivityCheckboxes value={editing.applicableSensitivities} onChange={(v) => setEditing({ ...editing, applicableSensitivities: v })} />
                    </FormField>
                    <FormField label="Enforcement Actions (key=value, one per line)" id="policy-enforcement">
                        <textarea id="policy-enforcement" value={Object.entries(editing.enforcementActions).map(([k, v]) => `${k}=${v}`).join("\n")}
                            onChange={(e) => {
                                const actions: Record<string, string> = {};
                                e.target.value.split("\n").forEach((line) => { const [k, ...v] = line.split("="); if (k?.trim()) actions[k.trim()] = v.join("=").trim(); });
                                setEditing({ ...editing, enforcementActions: actions });
                            }} rows={3} className={input + " font-mono text-xs"} />
                    </FormField>
                </>)}
            </FormModal>

            {policies.map((p) => (
                <div key={p.id} className="bg-white rounded-lg shadow-sm border border-gray-200">
                    <div className="flex items-center justify-between px-6 py-4 cursor-pointer hover:bg-gray-50" onClick={() => setExpanded(expanded === p.id ? null : p.id!)}>
                        <div className="flex-1">
                            <div className="flex items-center gap-2">
                                <h4 className="font-medium text-gray-900">{p.name}</h4>
                                <span className="text-xs text-gray-400">v{p.version}</span>
                                <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${p.active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>{p.active ? "Active" : "Inactive"}</span>
                            </div>
                            <p className="text-sm text-gray-500 mt-1">{p.description}</p>
                        </div>
                        <div className="flex items-center gap-2">
                            <button onClick={(e) => { e.stopPropagation(); setEditing({ ...p }); }} className={btnSecondary}><Pencil className="size-3.5" /></button>
                            <button onClick={(e) => { e.stopPropagation(); handleDelete(p.id!); }} className={btnDanger}><Trash2 className="size-3.5" /></button>
                            {expanded === p.id ? <ChevronDown className="size-5 text-gray-400" /> : <ChevronRight className="size-5 text-gray-400" />}
                        </div>
                    </div>
                    {expanded === p.id && (
                        <div className="px-6 pb-4 border-t border-gray-100 pt-4 space-y-3">
                            <div><h5 className="text-sm font-medium text-gray-700 mb-1">Rules</h5>
                                <ul className="space-y-1">{p.rules.map((r, i) => <li key={i} className="text-sm text-gray-600 flex items-start gap-2"><span className="text-blue-500 mt-0.5">•</span>{r}</li>)}</ul>
                            </div>
                            <div className="flex gap-6">
                                <div><h5 className="text-sm font-medium text-gray-700 mb-1">Sensitivities</h5><div className="flex gap-1">{p.applicableSensitivities.map((s) => <span key={s} className={`px-2 py-0.5 text-xs font-medium rounded-full ${SC[s] ?? ""}`}>{s}</span>)}</div></div>
                                <div><h5 className="text-sm font-medium text-gray-700 mb-1">Enforcement</h5><div className="flex flex-wrap gap-1">{Object.entries(p.enforcementActions).map(([k, v]) => <span key={k} className="px-2 py-0.5 text-xs bg-gray-100 text-gray-600 rounded">{k}: {v}</span>)}</div></div>
                            </div>
                        </div>
                    )}
                </div>
            ))}
        </div>
    );
}

/* ── Retention ─────────────────────────────────────────── */

function RetentionPanel() {
    const [schedules, setSchedules] = useState<RetentionSchedule[]>([]);
    const [editing, setEditing] = useState<RetentionSchedule | null>(null);
    const [saving, setSaving] = useState(false);

    const load = useCallback(async () => {
        try { const { data } = await api.get("/admin/governance/retention"); setSchedules(data); }
        catch { toast.error("Failed to load retention schedules"); }
    }, []);
    useEffect(() => { load(); }, [load]);

    const newSchedule = (): RetentionSchedule => ({ name: "", description: "", retentionDays: 365, dispositionAction: "ARCHIVE", legalHoldOverride: false, regulatoryBasis: "" });

    const handleSave = async () => {
        if (!editing || !editing.name.trim()) return;
        setSaving(true);
        try {
            if (editing.id) { await api.put(`/admin/governance/retention/${editing.id}`, editing); }
            else { await api.post("/admin/governance/retention", editing); }
            toast.success(`Schedule ${editing.id ? "updated" : "created"}`); setEditing(null); load();
        } catch { toast.error("Save failed"); }
        finally { setSaving(false); }
    };

    const fmtDays = (d: number) => d >= 365 ? `${Math.round(d / 365)} years` : `${d} days`;

    return (
        <div className="space-y-4">
            <div className="flex justify-between items-center">
                <p className="text-sm text-gray-500">Define how long documents are retained and what happens at expiry</p>
                <button onClick={() => setEditing(newSchedule())} className={btnPrimary}><Plus className="size-3.5" />Add Schedule</button>
            </div>

            <FormModal title={editing?.id ? "Edit Schedule" : "New Schedule"} open={!!editing} onClose={() => setEditing(null)} width="lg"
                footer={editing ? <>
                    <button onClick={handleSave} disabled={saving} className={btnPrimary}>{saving ? <Loader2 className="size-3.5 animate-spin" /> : <Save className="size-3.5" />}Save</button>
                    <button onClick={() => setEditing(null)} className={btnSecondary}><X className="size-3.5" />Cancel</button>
                </> : undefined}>
                {editing && (<>
                    <div className="grid grid-cols-2 gap-3">
                        <FormField label="Name" id="ret-name" required>
                            <input id="ret-name" value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} className={input} />
                        </FormField>
                        <FormField label="Retention (days)" id="ret-days">
                            <input id="ret-days" type="number" min={1} value={editing.retentionDays} onChange={(e) => setEditing({ ...editing, retentionDays: parseInt(e.target.value) || 1 })} className={input} />
                        </FormField>
                    </div>
                    <FormField label="Description" id="ret-description">
                        <input id="ret-description" value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })} className={input} />
                    </FormField>
                    <div className="grid grid-cols-3 gap-3">
                        <FormField label="Disposition" id="ret-disposition">
                            <select id="ret-disposition" value={editing.dispositionAction} onChange={(e) => setEditing({ ...editing, dispositionAction: e.target.value })} className={input}>
                                {DISPOSITIONS.map((d) => <option key={d} value={d}>{d}</option>)}
                            </select>
                        </FormField>
                        <FormField label="Legal Hold Override" id="ret-legal-hold">
                            <select id="ret-legal-hold" value={String(editing.legalHoldOverride)} onChange={(e) => setEditing({ ...editing, legalHoldOverride: e.target.value === "true" })} className={input}>
                                <option value="false">No</option><option value="true">Yes</option>
                            </select>
                        </FormField>
                        <FormField label="Regulatory Basis" id="ret-regulatory">
                            <input id="ret-regulatory" value={editing.regulatoryBasis} onChange={(e) => setEditing({ ...editing, regulatoryBasis: e.target.value })} className={input} />
                        </FormField>
                    </div>
                </>)}
            </FormModal>

            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                <table className="min-w-full divide-y divide-gray-200">
                    <thead className="bg-gray-50">
                        <tr>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Schedule</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Retention</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Disposition</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Legal Hold</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Regulatory Basis</th>
                            <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200">
                        {schedules.map((s) => (
                            <tr key={s.id} className="hover:bg-gray-50">
                                <td className="px-6 py-4"><div className="font-medium text-gray-900 text-sm">{s.name}</div><div className="text-xs text-gray-500 mt-0.5">{s.description}</div></td>
                                <td className="px-6 py-4 text-sm text-gray-700">{fmtDays(s.retentionDays)}</td>
                                <td className="px-6 py-4"><span className="px-2 py-0.5 text-xs font-medium rounded-full bg-gray-100 text-gray-700">{s.dispositionAction}</span></td>
                                <td className="px-6 py-4 text-sm">{s.legalHoldOverride ? <span className="text-amber-600 font-medium">Yes</span> : <span className="text-gray-400">No</span>}</td>
                                <td className="px-6 py-4 text-sm text-gray-600">{s.regulatoryBasis}</td>
                                <td className="px-6 py-4 text-right"><button onClick={() => setEditing({ ...s })} className={btnSecondary}><Pencil className="size-3.5" /></button></td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

/* ── Storage Tiers ─────────────────────────────────────── */

function StoragePanel() {
    const [tiers, setTiers] = useState<StorageTier[]>([]);
    const [editing, setEditing] = useState<StorageTier | null>(null);
    const [saving, setSaving] = useState(false);

    const load = useCallback(async () => {
        try { const { data } = await api.get("/admin/governance/storage-tiers"); setTiers(data); }
        catch { toast.error("Failed to load storage tiers"); }
    }, []);
    useEffect(() => { load(); }, [load]);

    const newTier = (): StorageTier => ({ name: "", description: "", encryptionType: "AES-256", immutable: false, geographicallyRestricted: false, region: "", allowedSensitivities: [], maxFileSizeBytes: 1_000_000_000, costPerGbMonth: 0.05 });

    const handleSave = async () => {
        if (!editing || !editing.name.trim()) return;
        setSaving(true);
        try {
            if (editing.id) { await api.put(`/admin/governance/storage-tiers/${editing.id}`, editing); }
            else { await api.post("/admin/governance/storage-tiers", editing); }
            toast.success(`Storage tier ${editing.id ? "updated" : "created"}`); setEditing(null); load();
        } catch { toast.error("Save failed"); }
        finally { setSaving(false); }
    };

    return (
        <div className="space-y-4">
            <div className="flex justify-between items-center">
                <p className="text-sm text-gray-500">Storage locations with security capabilities matched to sensitivity levels</p>
                <button onClick={() => setEditing(newTier())} className={btnPrimary}><Plus className="size-3.5" />Add Tier</button>
            </div>

            <FormModal title={editing?.id ? "Edit Storage Tier" : "New Storage Tier"} open={!!editing} onClose={() => setEditing(null)} width="lg"
                footer={editing ? <>
                    <button onClick={handleSave} disabled={saving} className={btnPrimary}>{saving ? <Loader2 className="size-3.5 animate-spin" /> : <Save className="size-3.5" />}Save</button>
                    <button onClick={() => setEditing(null)} className={btnSecondary}><X className="size-3.5" />Cancel</button>
                </> : undefined}>
                {editing && (<>
                    <div className="grid grid-cols-2 gap-3">
                        <FormField label="Name" id="stor-name" required>
                            <input id="stor-name" value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} className={input} />
                        </FormField>
                        <FormField label="Encryption Type" id="stor-encryption">
                            <input id="stor-encryption" value={editing.encryptionType} onChange={(e) => setEditing({ ...editing, encryptionType: e.target.value })} className={input} />
                        </FormField>
                    </div>
                    <FormField label="Description" id="stor-description">
                        <input id="stor-description" value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })} className={input} />
                    </FormField>
                    <div className="grid grid-cols-4 gap-3">
                        <FormField label="Immutable" id="stor-immutable">
                            <select id="stor-immutable" value={String(editing.immutable)} onChange={(e) => setEditing({ ...editing, immutable: e.target.value === "true" })} className={input}>
                                <option value="false">No</option><option value="true">Yes</option>
                            </select>
                        </FormField>
                        <FormField label="Geo-Restricted" id="stor-geo">
                            <select id="stor-geo" value={String(editing.geographicallyRestricted)} onChange={(e) => setEditing({ ...editing, geographicallyRestricted: e.target.value === "true" })} className={input}>
                                <option value="false">No</option><option value="true">Yes</option>
                            </select>
                        </FormField>
                        <FormField label="Region" id="stor-region">
                            <input id="stor-region" value={editing.region ?? ""} onChange={(e) => setEditing({ ...editing, region: e.target.value })} className={input} placeholder="e.g. eu-west-2" />
                        </FormField>
                        <FormField label="Cost ($/GB/mo)" id="stor-cost">
                            <input id="stor-cost" type="number" step={0.01} min={0} value={editing.costPerGbMonth} onChange={(e) => setEditing({ ...editing, costPerGbMonth: parseFloat(e.target.value) || 0 })} className={input} />
                        </FormField>
                    </div>
                    <FormField label="Allowed Sensitivities" id="stor-sensitivities">
                        <SensitivityCheckboxes value={editing.allowedSensitivities} onChange={(v) => setEditing({ ...editing, allowedSensitivities: v })} />
                    </FormField>
                </>)}
            </FormModal>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {tiers.map((t) => (
                    <div key={t.id} className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
                        <div className="flex items-center justify-between mb-3">
                            <h4 className="font-semibold text-gray-900">{t.name}</h4>
                            <div className="flex items-center gap-2">
                                <span className="text-sm font-medium text-blue-600">${t.costPerGbMonth}/GB/mo</span>
                                <button onClick={() => setEditing({ ...t })} className={btnSecondary}><Pencil className="size-3.5" /></button>
                            </div>
                        </div>
                        <p className="text-sm text-gray-500 mb-4">{t.description}</p>
                        <div className="space-y-2 text-sm">
                            <div className="flex justify-between"><span className="text-gray-500">Encryption</span><span className="font-medium text-gray-700">{t.encryptionType}</span></div>
                            <div className="flex justify-between"><span className="text-gray-500">Immutable</span><span className={`font-medium ${t.immutable ? "text-green-600" : "text-gray-400"}`}>{t.immutable ? "Yes" : "No"}</span></div>
                            <div className="flex justify-between"><span className="text-gray-500">Geo-Restricted</span><span className="font-medium text-gray-700">{t.geographicallyRestricted ? t.region ?? "Yes" : "No"}</span></div>
                            <div className="flex justify-between items-start"><span className="text-gray-500">Sensitivities</span><div className="flex flex-wrap gap-1 justify-end">{t.allowedSensitivities.map((s) => <span key={s} className={`px-2 py-0.5 text-xs font-medium rounded-full ${SC[s] ?? ""}`}>{s}</span>)}</div></div>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

/* ── PII Types Panel ───────────────────────────────── */

type PiiTypeDef = {
    id?: string;
    key: string;
    displayName: string;
    description: string;
    category: string;
    active: boolean;
    examples: string[];
    approvalStatus: "APPROVED" | "PENDING" | "REJECTED";
    submittedBy?: string;
};

const PII_CATEGORIES = ["financial", "identity", "medical", "employment", "contact", "legal", "other"];

function PiiTypesPanel() {
    const [defs, setDefs] = useState<PiiTypeDef[]>([]);
    const [pending, setPending] = useState<PiiTypeDef[]>([]);
    const [editing, setEditing] = useState<PiiTypeDef | null>(null);
    const [saving, setSaving] = useState(false);
    const [filterCat, setFilterCat] = useState("");

    const load = useCallback(async () => {
        try {
            const [all, pend] = await Promise.all([
                api.get("/admin/governance/pii-types/all"),
                api.get("/admin/governance/pii-types/pending"),
            ]);
            setDefs(all.data);
            setPending(pend.data);
        } catch { toast.error("Failed to load PII types"); }
    }, []);
    useEffect(() => { load(); }, [load]);

    const newDef = (): PiiTypeDef => ({
        key: "", displayName: "", description: "", category: "other", active: true, examples: [""], approvalStatus: "APPROVED",
    });

    const handleSave = async () => {
        if (!editing || !editing.key.trim() || !editing.displayName.trim()) return;
        setSaving(true);
        try {
            if (editing.id) { await api.put(`/admin/governance/pii-types/${editing.id}`, editing); }
            else { await api.post("/admin/governance/pii-types", editing); }
            toast.success(`PII type ${editing.id ? "updated" : "created"}`);
            setEditing(null); load();
        } catch { toast.error("Save failed"); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: string) => {
        if (!confirm("Deactivate this PII type?")) return;
        try { await api.delete(`/admin/governance/pii-types/${id}`); toast.success("Deactivated"); load(); }
        catch { toast.error("Failed"); }
    };

    const handleApprove = async (id: string) => {
        try { await api.put(`/admin/governance/pii-types/${id}/approve`); toast.success("Approved"); load(); }
        catch { toast.error("Failed"); }
    };

    const [rejectingId, setRejectingId] = useState<string | null>(null);

    const handleReject = async (id: string, reason: string) => {
        try { await api.put(`/admin/governance/pii-types/${id}/reject`, { reason }); toast.success("Rejected"); setRejectingId(null); load(); }
        catch { toast.error("Failed"); }
    };

    const filtered = filterCat ? defs.filter((d) => d.category === filterCat) : defs;
    const approved = filtered.filter((d) => d.approvalStatus === "APPROVED");

    return (
        <div className="space-y-4">
            <div className="flex justify-between items-center">
                <div className="flex items-center gap-3">
                    <p className="text-sm text-gray-500">Define the PII types available when flagging personal data</p>
                    <select value={filterCat} onChange={(e) => setFilterCat(e.target.value)}
                        className="text-xs border border-gray-300 rounded-md px-2 py-1">
                        <option value="">All categories</option>
                        {PII_CATEGORIES.map((c) => <option key={c} value={c}>{c.charAt(0).toUpperCase() + c.slice(1)}</option>)}
                    </select>
                </div>
                <button onClick={() => setEditing(newDef())} className={btnPrimary}><Plus className="size-3.5" />Add PII Type</button>
            </div>

            {pending.length > 0 && (
                <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
                    <h4 className="text-sm font-semibold text-amber-800 mb-3">Pending Approval ({pending.length})</h4>
                    <div className="space-y-2">
                        {pending.map((p) => (
                            <div key={p.id} className="flex items-center justify-between bg-white rounded-md p-3 border border-amber-100">
                                <div>
                                    <span className="text-sm font-medium text-gray-900">{p.displayName}</span>
                                    <span className="ml-2 text-xs text-gray-400">({p.key})</span>
                                    {p.submittedBy && <span className="ml-2 text-xs text-gray-500">by {p.submittedBy}</span>}
                                    {p.description && <p className="text-xs text-gray-500 mt-0.5">{p.description}</p>}
                                </div>
                                <div className="flex gap-1">
                                    <button onClick={() => handleApprove(p.id!)}
                                        className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium text-green-700 bg-green-50 border border-green-200 rounded hover:bg-green-100">
                                        <Check className="size-3" />Approve
                                    </button>
                                    <button onClick={() => setRejectingId(p.id!)}
                                        className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium text-red-700 bg-red-50 border border-red-200 rounded hover:bg-red-100">
                                        <XCircle className="size-3" />Reject
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            <FormModal title={editing?.id ? "Edit PII Type" : "New PII Type"} open={!!editing} onClose={() => setEditing(null)} width="lg"
                footer={editing ? <>
                    <button onClick={handleSave} disabled={saving} className={btnPrimary}>{saving ? <Loader2 className="size-3.5 animate-spin" /> : <Save className="size-3.5" />}Save</button>
                    <button onClick={() => setEditing(null)} className={btnSecondary}><X className="size-3.5" />Cancel</button>
                </> : undefined}>
                {editing && (<>
                    <div className="grid grid-cols-3 gap-3">
                        <FormField label="Key (e.g. EMPLOYEE_ID)" id="pii-key" required>
                            <input id="pii-key" value={editing.key} onChange={(e) => setEditing({ ...editing, key: e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, "") })}
                                className={input} disabled={!!editing.id} placeholder="EMPLOYEE_ID" />
                        </FormField>
                        <FormField label="Display Name" id="pii-display-name" required>
                            <input id="pii-display-name" value={editing.displayName} onChange={(e) => setEditing({ ...editing, displayName: e.target.value })} className={input} />
                        </FormField>
                        <FormField label="Category" id="pii-category">
                            <select id="pii-category" value={editing.category} onChange={(e) => setEditing({ ...editing, category: e.target.value })} className={input}>
                                {PII_CATEGORIES.map((c) => <option key={c} value={c}>{c.charAt(0).toUpperCase() + c.slice(1)}</option>)}
                            </select>
                        </FormField>
                    </div>
                    <FormField label="Description" id="pii-description">
                        <input id="pii-description" value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })} className={input}
                            placeholder="What does this PII type represent?" />
                    </FormField>
                    <FormField label="Examples" id="pii-example-0">
                        <div className="space-y-1">
                            {editing.examples.map((ex, i) => (
                                <div key={i} className="flex gap-1">
                                    <input id={`pii-example-${i}`} value={ex} onChange={(e) => { const exs = [...editing.examples]; exs[i] = e.target.value; setEditing({ ...editing, examples: exs }); }}
                                        className={input} placeholder="Example..." />
                                    <button onClick={() => setEditing({ ...editing, examples: editing.examples.filter((_, j) => j !== i) })} className={btnDanger}><Trash2 className="size-3" /></button>
                                </div>
                            ))}
                            <button onClick={() => setEditing({ ...editing, examples: [...editing.examples, ""] })} className="text-xs text-blue-600 hover:text-blue-800">+ Add example</button>
                        </div>
                    </FormField>
                </>)}
            </FormModal>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                {approved.map((def) => (
                    <div key={def.id} className={`bg-white rounded-lg shadow-sm border border-gray-200 p-4 ${!def.active ? "opacity-50" : ""}`}>
                        <div className="flex items-center justify-between mb-1">
                            <span className="text-sm font-semibold text-gray-900">{def.displayName}</span>
                            <div className="flex gap-1">
                                <button onClick={() => setEditing(def)} className="text-gray-400 hover:text-blue-600"><Pencil className="size-3.5" /></button>
                                <button onClick={() => handleDelete(def.id!)} className="text-gray-400 hover:text-red-600"><Trash2 className="size-3.5" /></button>
                            </div>
                        </div>
                        <div className="flex items-center gap-2 mb-1">
                            <code className="text-xs text-gray-400">{def.key}</code>
                            <span className="text-xs px-1.5 py-0.5 bg-gray-100 text-gray-600 rounded">{def.category}</span>
                        </div>
                        {def.description && <p className="text-xs text-gray-500">{def.description}</p>}
                    </div>
                ))}
            </div>

            {/* Reject reason modal */}
            {rejectingId && (
                <ReasonModal
                    title="Reject PII Type"
                    description="Explain why this PII type submission is being rejected."
                    placeholder="e.g. This duplicates the existing EMPLOYEE_ID type"
                    confirmLabel="Reject"
                    confirmClass="bg-red-600 hover:bg-red-700"
                    onConfirm={(reason) => handleReject(rejectingId, reason)}
                    onClose={() => setRejectingId(null)}
                />
            )}
        </div>
    );
}

/* ── Metadata Schemas Panel ────────────────────────── */

type MetadataFieldDef = {
    fieldName: string;
    dataType: string;
    required: boolean;
    description: string;
    extractionHint: string;
    examples: string[];
};

type MetadataSchemaDef = {
    id?: string;
    name: string;
    description: string;
    extractionContext: string;
    fields: MetadataFieldDef[];
    linkedCategoryIds: string[];
    active: boolean;
};

const FIELD_TYPES = ["TEXT", "NUMBER", "DATE", "CURRENCY", "BOOLEAN", "KEYWORD"];

function MetadataSchemasPanel() {
    const [schemas, setSchemas] = useState<MetadataSchemaDef[]>([]);
    const [categories, setCategories] = useState<{ id: string; name: string }[]>([]);
    const [editing, setEditing] = useState<MetadataSchemaDef | null>(null);
    const [saving, setSaving] = useState(false);

    const load = useCallback(async () => {
        try {
            const [s, c] = await Promise.all([
                api.get("/admin/governance/metadata-schemas/all"),
                api.get("/admin/governance/taxonomy"),
            ]);
            setSchemas(s.data);
            setCategories(c.data);
        } catch { toast.error("Failed to load metadata schemas"); }
    }, []);
    useEffect(() => { load(); }, [load]);

    const newSchema = (): MetadataSchemaDef => ({
        name: "", description: "", extractionContext: "",
        fields: [emptyField()], linkedCategoryIds: [], active: true,
    });

    const emptyField = (): MetadataFieldDef => ({
        fieldName: "", dataType: "TEXT", required: false,
        description: "", extractionHint: "", examples: [],
    });

    const handleSave = async () => {
        if (!editing || !editing.name.trim()) return;
        setSaving(true);
        try {
            if (editing.id) {
                await api.put(`/admin/governance/metadata-schemas/${editing.id}`, editing);
            } else {
                await api.post("/admin/governance/metadata-schemas", editing);
            }
            toast.success(`Schema ${editing.id ? "updated" : "created"}`);
            setEditing(null); load();
        } catch { toast.error("Save failed"); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: string) => {
        if (!confirm("Deactivate this schema?")) return;
        try { await api.delete(`/admin/governance/metadata-schemas/${id}`); toast.success("Deactivated"); load(); }
        catch { toast.error("Failed"); }
    };

    const updateField = (idx: number, updates: Partial<MetadataFieldDef>) => {
        if (!editing) return;
        const fields = [...editing.fields];
        fields[idx] = { ...fields[idx], ...updates };
        setEditing({ ...editing, fields });
    };

    const removeField = (idx: number) => {
        if (!editing || editing.fields.length <= 1) return;
        setEditing({ ...editing, fields: editing.fields.filter((_, i) => i !== idx) });
    };

    const toggleCategory = (catId: string) => {
        if (!editing) return;
        const linked = editing.linkedCategoryIds.includes(catId)
            ? editing.linkedCategoryIds.filter(id => id !== catId)
            : [...editing.linkedCategoryIds, catId];
        setEditing({ ...editing, linkedCategoryIds: linked });
    };

    return (
        <div className="space-y-4">
            <div className="flex justify-between items-center">
                <p className="text-sm text-gray-500">
                    Define what metadata the LLM extracts for each document type. Each field includes guidance on where to find the data.
                </p>
                <button onClick={() => setEditing(newSchema())} className={btnPrimary}><Plus className="size-3.5" />Add Schema</button>
            </div>

            <FormModal title={editing?.id ? "Edit Schema" : "New Schema"} open={!!editing} onClose={() => setEditing(null)} width="2xl"
                footer={editing ? <>
                    <button onClick={handleSave} disabled={saving} className={btnPrimary}>
                        {saving ? <Loader2 className="size-3.5 animate-spin" /> : <Save className="size-3.5" />}Save
                    </button>
                    <button onClick={() => setEditing(null)} className={btnSecondary}><X className="size-3.5" />Cancel</button>
                </> : undefined}>
                {editing && (<>
                    <div className="grid grid-cols-2 gap-3">
                        <FormField label="Schema Name" id="schema-name" required>
                            <input id="schema-name" value={editing.name} onChange={e => setEditing({ ...editing, name: e.target.value })} className={input}
                                placeholder="e.g. Invoice" />
                        </FormField>
                        <FormField label="Description" id="schema-description">
                            <input id="schema-description" value={editing.description} onChange={e => setEditing({ ...editing, description: e.target.value })} className={input}
                                placeholder="e.g. Invoices, bills, payment requests" />
                        </FormField>
                    </div>

                    <FormField label="Extraction Context" id="schema-context" hint="Tell the LLM what this document type looks like and where to find key information">
                        <textarea id="schema-context" value={editing.extractionContext}
                            onChange={e => setEditing({ ...editing, extractionContext: e.target.value })}
                            rows={3} className={input + " resize-none"}
                            placeholder="e.g. Invoices have vendor details at the top, an invoice number in the header, line items in a table, and a total at the bottom." />
                    </FormField>

                    <FormField label="Linked Categories" id="schema-categories" hint="Which taxonomy categories use this schema?">
                        <div className="flex flex-wrap gap-1.5">
                            {categories.map(cat => (
                                <button key={cat.id} onClick={() => toggleCategory(cat.id)}
                                    className={`px-2 py-1 text-xs rounded-md border transition-colors ${
                                        editing.linkedCategoryIds.includes(cat.id)
                                            ? "bg-blue-100 text-blue-700 border-blue-200"
                                            : "bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100"
                                    }`}>
                                    {cat.name}
                                </button>
                            ))}
                        </div>
                    </FormField>

                    {/* Fields */}
                    <div>
                        <div className="flex items-center justify-between mb-2">
                            <label className="text-xs font-medium text-gray-700">Fields — define what to extract and how to find it</label>
                            <button onClick={() => setEditing({ ...editing, fields: [...editing.fields, emptyField()] })}
                                className="text-xs text-blue-600 hover:text-blue-800 font-medium">+ Add field</button>
                        </div>
                        <div className="space-y-3">
                            {editing.fields.map((field, idx) => (
                                <div key={idx} className="bg-white rounded-lg border border-gray-200 p-3 space-y-2">
                                    <div className="flex items-center justify-between">
                                        <span className="text-xs font-semibold text-gray-500">Field {idx + 1}</span>
                                        {editing.fields.length > 1 && (
                                            <button onClick={() => removeField(idx)} className={btnDanger}><Trash2 className="size-3" /></button>
                                        )}
                                    </div>
                                    <div className="grid grid-cols-4 gap-2">
                                        <input id={`schema-field-name-${idx}`} value={field.fieldName}
                                            onChange={e => updateField(idx, { fieldName: e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, "_") })}
                                            className={input} placeholder="field_name" />
                                        <select id={`schema-field-type-${idx}`} value={field.dataType} onChange={e => updateField(idx, { dataType: e.target.value })} className={input}>
                                            {FIELD_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                                        </select>
                                        <label htmlFor={`schema-field-required-${idx}`} className="flex items-center gap-1.5 text-xs text-gray-600">
                                            <input id={`schema-field-required-${idx}`} type="checkbox" checked={field.required}
                                                onChange={e => updateField(idx, { required: e.target.checked })}
                                                className="rounded border-gray-300 text-blue-600" />
                                            Required
                                        </label>
                                        <input id={`schema-field-examples-${idx}`} value={(field.examples ?? []).join(", ")}
                                            onChange={e => updateField(idx, { examples: e.target.value.split(",").map(s => s.trim()).filter(Boolean) })}
                                            className={input} placeholder="examples (comma sep)" />
                                    </div>
                                    <input id={`schema-field-desc-${idx}`} value={field.description}
                                        onChange={e => updateField(idx, { description: e.target.value })}
                                        className={input} placeholder="What is this field? (e.g. Total invoice amount including VAT)" />
                                    <input id={`schema-field-hint-${idx}`} value={field.extractionHint}
                                        onChange={e => updateField(idx, { extractionHint: e.target.value })}
                                        className={input}
                                        placeholder="Where to find it — e.g. Look for 'Total' or 'Amount Due' at the bottom of the document" />
                                </div>
                            ))}
                        </div>
                    </div>
                </>)}
            </FormModal>

            {/* List */}
            <div className="space-y-4">
                {schemas.filter(s => s.active).map(schema => (
                    <div key={schema.id} className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
                        <div className="flex items-center justify-between mb-2">
                            <div>
                                <h4 className="text-sm font-semibold text-gray-900">{schema.name}</h4>
                                {schema.description && <p className="text-xs text-gray-500">{schema.description}</p>}
                            </div>
                            <div className="flex gap-1">
                                <button onClick={() => setEditing({ ...schema })} className="text-gray-400 hover:text-blue-600"><Pencil className="size-4" /></button>
                                <button onClick={() => handleDelete(schema.id!)} className="text-gray-400 hover:text-red-600"><Trash2 className="size-4" /></button>
                            </div>
                        </div>

                        {schema.extractionContext && (
                            <div className="bg-amber-50 rounded-md p-2.5 mb-3 border border-amber-100">
                                <div className="text-[10px] font-semibold text-amber-700 uppercase tracking-wider mb-0.5">LLM Context</div>
                                <p className="text-xs text-amber-800">{schema.extractionContext}</p>
                            </div>
                        )}

                        {schema.linkedCategoryIds?.length > 0 && (
                            <div className="flex flex-wrap gap-1 mb-3">
                                {schema.linkedCategoryIds.map(id => {
                                    const cat = categories.find(c => c.id === id);
                                    return <span key={id} className="px-1.5 py-0.5 text-[10px] bg-blue-100 text-blue-700 rounded">{cat?.name ?? id}</span>;
                                })}
                            </div>
                        )}

                        <div className="border rounded-md overflow-hidden">
                            <table className="w-full text-xs">
                                <thead>
                                    <tr className="bg-gray-50 border-b">
                                        <th className="text-left px-3 py-1.5 font-medium text-gray-500">Field</th>
                                        <th className="text-left px-3 py-1.5 font-medium text-gray-500">Type</th>
                                        <th className="text-left px-3 py-1.5 font-medium text-gray-500">Description</th>
                                        <th className="text-left px-3 py-1.5 font-medium text-gray-500">Extraction Hint</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {schema.fields?.map((f, i) => (
                                        <tr key={i}>
                                            <td className="px-3 py-1.5 font-medium text-gray-900">
                                                {f.fieldName}{f.required && <span className="text-red-500 ml-0.5">*</span>}
                                            </td>
                                            <td className="px-3 py-1.5 text-gray-500">{f.dataType}</td>
                                            <td className="px-3 py-1.5 text-gray-600">{f.description}</td>
                                            <td className="px-3 py-1.5 text-amber-700">{f.extractionHint || "—"}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
