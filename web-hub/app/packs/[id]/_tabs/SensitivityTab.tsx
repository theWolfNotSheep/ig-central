"use client";

import { useState } from "react";
import { LegislationItem, SensitivityItem } from "../_lib/types";
import { ListView } from "../_components/ListView";
import { EditModal, Field, inputCls } from "../_components/EditModal";
import { ChipInput, MultiRefSelect } from "../_components/RefSelect";

const COLOURS = ["green", "blue", "amber", "red", "purple", "gray"];

export function SensitivityTab({ items, legislation, onChange }: {
    items: SensitivityItem[];
    legislation: LegislationItem[];
    onChange: (next: SensitivityItem[]) => void;
}) {
    const [editing, setEditing] = useState<{ item: SensitivityItem; idx: number | null } | null>(null);

    const blank = (): SensitivityItem => ({
        key: "", displayName: "", description: "", level: items.length,
        colour: COLOURS[items.length % COLOURS.length], guidelines: [], examples: [], legislationRefs: [],
    });

    const save = () => {
        if (!editing) return;
        const next = [...items];
        if (editing.idx !== null) next[editing.idx] = editing.item;
        else next.push(editing.item);
        onChange(next.sort((a, b) => a.level - b.level));
        setEditing(null);
    };

    const del = (_: SensitivityItem, idx: number) => {
        if (!confirm("Delete this sensitivity label?")) return;
        onChange(items.filter((_, i) => i !== idx));
    };

    const legOptions = legislation.map((l) => ({ value: l.key, label: l.shortName || l.name }));

    return (
        <>
            <ListView<SensitivityItem>
                title="Sensitivity Labels"
                items={items}
                addLabel="Add Label"
                emptyText="No sensitivity labels yet. These are the levels users can apply to documents (e.g. PUBLIC, CONFIDENTIAL)."
                columns={[
                    { label: "Level", render: (i) => <span className="font-mono text-xs text-gray-500">{i.level}</span>, width: "60px" },
                    { label: "Key", render: (i) => <span className="font-mono text-xs text-blue-700">{i.key}</span>, width: "120px" },
                    { label: "Display Name", render: (i) => <div><div className="font-medium text-gray-900">{i.displayName}</div><div className="text-xs text-gray-500">{i.description}</div></div> },
                    { label: "Colour", render: (i) => <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium bg-${i.colour}-100 text-${i.colour}-700`}>{i.colour}</span>, width: "100px" },
                ]}
                onAdd={() => setEditing({ item: blank(), idx: null })}
                onEdit={(item, idx) => setEditing({ item: { ...item }, idx })}
                onDelete={del}
            />
            {editing && (
                <EditModal
                    title={editing.idx === null ? "Add Sensitivity Label" : "Edit Sensitivity Label"}
                    onSave={save}
                    onCancel={() => setEditing(null)}
                    canSave={!!editing.item.key.trim() && !!editing.item.displayName.trim()}
                >
                    <div className="grid grid-cols-3 gap-3">
                        <Field label="Key *" hint="Uppercase">
                            <input value={editing.item.key} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, key: e.target.value.toUpperCase() } })} className={`${inputCls} font-mono`} placeholder="CONFIDENTIAL" />
                        </Field>
                        <Field label="Level *" hint="0=lowest">
                            <input type="number" min={0} max={10} value={editing.item.level} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, level: parseInt(e.target.value) || 0 } })} className={inputCls} />
                        </Field>
                        <Field label="Colour">
                            <select value={editing.item.colour} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, colour: e.target.value } })} className={inputCls}>
                                {COLOURS.map((c) => <option key={c} value={c}>{c}</option>)}
                            </select>
                        </Field>
                    </div>
                    <Field label="Display Name *">
                        <input value={editing.item.displayName} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, displayName: e.target.value } })} className={inputCls} />
                    </Field>
                    <Field label="Description *">
                        <textarea rows={2} value={editing.item.description} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, description: e.target.value } })} className={inputCls} />
                    </Field>
                    <Field label="Guidelines" hint="Comma-separated rules">
                        <ChipInput values={editing.item.guidelines} onChange={(v) => setEditing({ ...editing, item: { ...editing.item, guidelines: v } })} placeholder="Need-to-know basis, Encrypted at rest" />
                    </Field>
                    <Field label="Examples" hint="Comma-separated document types">
                        <ChipInput values={editing.item.examples} onChange={(v) => setEditing({ ...editing, item: { ...editing.item, examples: v } })} placeholder="HR records, Financial reports" />
                    </Field>
                    <Field label="Legislation References" hint="Laws that mandate this sensitivity">
                        <MultiRefSelect values={editing.item.legislationRefs} options={legOptions} onChange={(v) => setEditing({ ...editing, item: { ...editing.item, legislationRefs: v } })} />
                    </Field>
                </EditModal>
            )}
        </>
    );
}
