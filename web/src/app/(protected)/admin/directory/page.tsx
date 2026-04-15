"use client";

import { useCallback, useEffect, useState } from "react";
import { Plus, Pencil, Trash2, Save, Loader2, RefreshCw, Users } from "lucide-react";
import FormModal, { FormField } from "@/components/form-modal";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

type Mapping = {
    id?: string;
    directorySource: string;
    externalGroupName: string;
    externalGroupEmail: string;
    internalRoleKey: string;
    sensitivityClearanceLevel: number;
    taxonomyGrantCategoryIds: string[];
    active: boolean;
};

type RoleDef = { key: string; name: string };
type Category = { id: string; name: string };

const CLEARANCE = ["PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"];

export default function DirectoryMappingPage() {
    const [mappings, setMappings] = useState<Mapping[]>([]);
    const [roles, setRoles] = useState<RoleDef[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);
    const [editing, setEditing] = useState<Mapping | null>(null);
    const [saving, setSaving] = useState(false);
    const [syncing, setSyncing] = useState(false);

    const load = useCallback(async () => {
        try {
            const [m, r, c] = await Promise.all([
                api.get("/admin/directory-mappings"),
                api.get("/admin/users/roles"),
                api.get("/admin/governance/taxonomy"),
            ]);
            setMappings(m.data);
            setRoles(r.data);
            setCategories(c.data);
        } catch { toast.error("Failed to load"); }
    }, []);

    useEffect(() => { load(); }, [load]);

    const newMapping = (): Mapping => ({
        directorySource: "GOOGLE", externalGroupName: "", externalGroupEmail: "",
        internalRoleKey: "", sensitivityClearanceLevel: 0, taxonomyGrantCategoryIds: [], active: true,
    });

    const handleSave = async () => {
        if (!editing) return;
        setSaving(true);
        try {
            if (editing.id) { await api.put(`/admin/directory-mappings/${editing.id}`, editing); }
            else { await api.post("/admin/directory-mappings", editing); }
            toast.success("Mapping saved");
            setEditing(null); load();
        } catch { toast.error("Save failed"); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: string) => {
        if (!confirm("Delete this mapping?")) return;
        try { await api.delete(`/admin/directory-mappings/${id}`); toast.success("Deleted"); load(); }
        catch { toast.error("Failed"); }
    };

    const handleSync = async () => {
        setSyncing(true);
        try {
            const { data } = await api.post("/admin/directory-mappings/sync");
            toast.success(`Synced ${data.synced} user(s)`);
        } catch { toast.error("Sync failed"); }
        finally { setSyncing(false); }
    };

    const toggleCategory = (catId: string) => {
        if (!editing) return;
        const ids = editing.taxonomyGrantCategoryIds.includes(catId)
            ? editing.taxonomyGrantCategoryIds.filter(id => id !== catId)
            : [...editing.taxonomyGrantCategoryIds, catId];
        setEditing({ ...editing, taxonomyGrantCategoryIds: ids });
    };

    return (
        <>
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">Directory Mappings</h2>
                    <p className="text-sm text-gray-500 mt-1">Map Google Workspace domains/groups to IG Central roles and access</p>
                </div>
                <div className="flex gap-2">
                    <button onClick={handleSync} disabled={syncing}
                        className="inline-flex items-center gap-2 px-3 py-2 border border-gray-300 text-sm text-gray-700 rounded-lg hover:bg-gray-50 disabled:opacity-50">
                        {syncing ? <Loader2 className="size-4 animate-spin" /> : <RefreshCw className="size-4" />} Sync Now
                    </button>
                    <button onClick={() => setEditing(newMapping())}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700">
                        <Plus className="size-4" /> Add Mapping
                    </button>
                </div>
            </div>

            {/* Info box */}
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-6 text-xs text-blue-800">
                <p className="font-medium mb-1">How directory mappings work:</p>
                <ul className="list-disc ml-4 space-y-0.5">
                    <li><strong>Domain match:</strong> Enter a domain (e.g. "wolfnotsheep.co.uk") as the Group Name — all users with that email domain get the mapped role</li>
                    <li><strong>Email match:</strong> Enter a specific email as the Group Email for individual user mapping</li>
                    <li><strong>On sync:</strong> All Google-authenticated users are checked against mappings. Roles, clearance, and taxonomy grants are applied automatically.</li>
                    <li><strong>On login:</strong> New Google users get default roles. Run "Sync Now" to apply directory mappings to existing users.</li>
                </ul>
            </div>

            {/* Editor Modal */}
            <FormModal
                title={editing?.id ? "Edit Mapping" : "New Mapping"}
                open={!!editing}
                onClose={() => setEditing(null)}
                footer={
                    <>
                        <button onClick={handleSave} disabled={saving}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-md hover:bg-blue-700 disabled:opacity-50">
                            {saving ? <Loader2 className="size-3 animate-spin" /> : <Save className="size-3" />} Save
                        </button>
                        <button onClick={() => setEditing(null)}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-gray-300 text-gray-700 text-xs font-medium rounded-md hover:bg-gray-50">
                            Cancel
                        </button>
                    </>
                }
            >
                {editing && (
                    <>
                        <div className="grid grid-cols-2 gap-3">
                            <FormField label="Group Name / Domain" id="dm-group-name">
                                <input id="dm-group-name" value={editing.externalGroupName}
                                    onChange={e => setEditing({ ...editing, externalGroupName: e.target.value })}
                                    placeholder="e.g. wolfnotsheep.co.uk"
                                    className="w-full text-sm border border-gray-300 rounded-md px-3 py-1.5" />
                            </FormField>
                            <FormField label="Group Email (optional)" id="dm-group-email">
                                <input id="dm-group-email" value={editing.externalGroupEmail}
                                    onChange={e => setEditing({ ...editing, externalGroupEmail: e.target.value })}
                                    placeholder="e.g. compliance@company.com"
                                    className="w-full text-sm border border-gray-300 rounded-md px-3 py-1.5" />
                            </FormField>
                            <FormField label="Map to Role" id="dm-role">
                                <select id="dm-role" value={editing.internalRoleKey}
                                    onChange={e => setEditing({ ...editing, internalRoleKey: e.target.value })}
                                    className="w-full text-sm border border-gray-300 rounded-md px-3 py-1.5">
                                    <option value="">Select role...</option>
                                    {roles.map(r => <option key={r.key} value={r.key}>{r.name} ({r.key})</option>)}
                                </select>
                            </FormField>
                            <FormField label="Sensitivity Clearance" id="dm-clearance">
                                <select id="dm-clearance" value={editing.sensitivityClearanceLevel}
                                    onChange={e => setEditing({ ...editing, sensitivityClearanceLevel: parseInt(e.target.value) })}
                                    className="w-full text-sm border border-gray-300 rounded-md px-3 py-1.5">
                                    {CLEARANCE.map((c, i) => <option key={i} value={i}>{c}</option>)}
                                </select>
                            </FormField>
                        </div>
                        <div>
                            <span className="text-xs font-medium text-gray-700 block mb-1">Auto-grant Taxonomy Access</span>
                            <div className="flex flex-wrap gap-1.5" role="group" aria-label="Taxonomy category selection">
                                {categories.map(cat => (
                                    <button key={cat.id} onClick={() => toggleCategory(cat.id)}
                                        aria-pressed={editing.taxonomyGrantCategoryIds.includes(cat.id)}
                                        className={`px-2 py-1 text-xs rounded-md border ${
                                            editing.taxonomyGrantCategoryIds.includes(cat.id)
                                                ? "bg-blue-100 text-blue-700 border-blue-200"
                                                : "bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100"
                                        }`}>{cat.name}</button>
                                ))}
                            </div>
                        </div>
                    </>
                )}
            </FormModal>

            {/* Mapping list */}
            <div className="space-y-3">
                {mappings.map(m => (
                    <div key={m.id} className={`bg-white rounded-lg shadow-sm border border-gray-200 p-4 ${!m.active ? "opacity-50" : ""}`}>
                        <div className="flex items-center justify-between mb-2">
                            <div className="flex items-center gap-3">
                                <Users className="size-5 text-blue-600" />
                                <div>
                                    <div className="text-sm font-semibold text-gray-900">{m.externalGroupName || m.externalGroupEmail}</div>
                                    <div className="text-xs text-gray-500">
                                        {m.directorySource} → <span className="font-medium text-blue-600">{m.internalRoleKey}</span>
                                        {" "}| Clearance: {CLEARANCE[m.sensitivityClearanceLevel]}
                                    </div>
                                </div>
                            </div>
                            <div className="flex gap-1">
                                <button onClick={() => setEditing({ ...m })} className="text-gray-400 hover:text-blue-600" aria-label="Edit mapping"><Pencil className="size-4" /></button>
                                <button onClick={() => handleDelete(m.id!)} className="text-gray-400 hover:text-red-600" aria-label="Delete mapping"><Trash2 className="size-4" /></button>
                            </div>
                        </div>
                        {m.taxonomyGrantCategoryIds?.length > 0 && (
                            <div className="flex flex-wrap gap-1 mt-1">
                                {m.taxonomyGrantCategoryIds.map(catId => {
                                    const cat = categories.find(c => c.id === catId);
                                    return <span key={catId} className="px-1.5 py-0.5 text-[10px] bg-blue-50 text-blue-600 rounded">{cat?.name ?? catId}</span>;
                                })}
                            </div>
                        )}
                    </div>
                ))}
                {mappings.length === 0 && !editing && (
                    <div className="text-center py-8 text-gray-400 text-sm">No directory mappings configured</div>
                )}
            </div>
        </>
    );
}
