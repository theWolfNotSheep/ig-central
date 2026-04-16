"use client";

import { useState } from "react";
import { LegislationItem, PolicyItem, SensitivityItem } from "../_lib/types";
import { ListView } from "../_components/ListView";
import { EditModal, Field, inputCls } from "../_components/EditModal";
import { ChipInput, MultiRefSelect } from "../_components/RefSelect";

export function PoliciesTab({ items, sensitivity, legislation, onChange }: {
    items: PolicyItem[];
    sensitivity: SensitivityItem[];
    legislation: LegislationItem[];
    onChange: (next: PolicyItem[]) => void;
}) {
    const [editing, setEditing] = useState<{ item: PolicyItem; idx: number | null } | null>(null);

    const blank = (): PolicyItem => ({
        name: "", description: "", version: 1, active: true,
        applicableSensitivities: [], rules: [], legislationRefs: [],
    });

    const save = () => {
        if (!editing) return;
        const next = [...items];
        if (editing.idx !== null) next[editing.idx] = editing.item;
        else next.push(editing.item);
        onChange(next);
        setEditing(null);
    };

    const del = (_: PolicyItem, idx: number) => {
        if (!confirm("Delete this policy?")) return;
        onChange(items.filter((_, i) => i !== idx));
    };

    const sensOptions = sensitivity.map((s) => ({ value: s.key, label: s.displayName || s.key }));
    const legOptions = legislation.map((l) => ({ value: l.key, label: l.shortName || l.name }));

    return (
        <>
            <ListView<PolicyItem>
                title="Governance Policies"
                items={items}
                addLabel="Add Policy"
                emptyText="No policies yet. Define rules that get enforced after document classification."
                columns={[
                    { label: "Name", render: (i) => <div><div className="font-medium text-gray-900">{i.name}</div><div className="text-xs text-gray-500">{i.description}</div></div> },
                    { label: "Sensitivities", render: (i) => <span className="text-xs text-gray-600">{i.applicableSensitivities.join(", ") || "—"}</span>, width: "180px" },
                    { label: "Rules", render: (i) => <span className="text-xs text-gray-500">{i.rules.length}</span>, width: "70px" },
                    { label: "Status", render: (i) => i.active ? <span className="text-xs text-green-600">Active</span> : <span className="text-xs text-gray-400">Inactive</span>, width: "80px" },
                ]}
                onAdd={() => setEditing({ item: blank(), idx: null })}
                onEdit={(item, idx) => setEditing({ item: { ...item }, idx })}
                onDelete={del}
            />
            {editing && (
                <EditModal
                    title={editing.idx === null ? "Add Policy" : "Edit Policy"}
                    onSave={save}
                    onCancel={() => setEditing(null)}
                    canSave={!!editing.item.name.trim() && !!editing.item.description.trim() && editing.item.rules.length > 0}
                >
                    <Field label="Name *">
                        <input value={editing.item.name} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, name: e.target.value } })} className={inputCls} />
                    </Field>
                    <Field label="Description *">
                        <textarea rows={2} value={editing.item.description} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, description: e.target.value } })} className={inputCls} />
                    </Field>
                    <div className="grid grid-cols-2 gap-3">
                        <Field label="Version">
                            <input type="number" min={1} value={editing.item.version} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, version: parseInt(e.target.value) || 1 } })} className={inputCls} />
                        </Field>
                        <label className="flex items-center gap-2 pt-6 text-sm text-gray-700">
                            <input type="checkbox" checked={editing.item.active} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, active: e.target.checked } })} className="rounded border-gray-300" />
                            Policy is active
                        </label>
                    </div>
                    <Field label="Applicable Sensitivities">
                        <MultiRefSelect values={editing.item.applicableSensitivities} options={sensOptions} onChange={(v) => setEditing({ ...editing, item: { ...editing.item, applicableSensitivities: v } })} />
                    </Field>
                    <Field label="Rules *" hint="Comma-separated, each becomes a list item">
                        <ChipInput values={editing.item.rules} onChange={(v) => setEditing({ ...editing, item: { ...editing.item, rules: v } })} placeholder="Personal data must be processed lawfully, Subject access requests within 30 days" />
                    </Field>
                    <Field label="Legislation References">
                        <MultiRefSelect values={editing.item.legislationRefs} options={legOptions} onChange={(v) => setEditing({ ...editing, item: { ...editing.item, legislationRefs: v } })} />
                    </Field>
                </EditModal>
            )}
        </>
    );
}
