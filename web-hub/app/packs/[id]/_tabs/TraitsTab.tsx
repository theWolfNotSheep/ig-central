"use client";

import { useState } from "react";
import { TraitItem } from "../_lib/types";
import { ListView } from "../_components/ListView";
import { EditModal, Field, inputCls } from "../_components/EditModal";
import { ChipInput } from "../_components/RefSelect";

const DIMENSIONS = ["COMPLETENESS", "DIRECTION", "PROVENANCE", "FORMAT", "STATUS"];

export function TraitsTab({ items, onChange }: { items: TraitItem[]; onChange: (next: TraitItem[]) => void }) {
    const [editing, setEditing] = useState<{ item: TraitItem; idx: number | null } | null>(null);

    const blank = (): TraitItem => ({
        key: "", displayName: "", description: "", dimension: "COMPLETENESS",
        suppressPii: false, detectionHint: undefined, indicators: [],
    });

    const save = () => {
        if (!editing) return;
        const next = [...items];
        if (editing.idx !== null) next[editing.idx] = editing.item;
        else next.push(editing.item);
        onChange(next);
        setEditing(null);
    };

    const del = (_: TraitItem, idx: number) => {
        if (!confirm("Delete this trait?")) return;
        onChange(items.filter((_, i) => i !== idx));
    };

    return (
        <>
            <ListView<TraitItem>
                title="Document Traits"
                items={items}
                addLabel="Add Trait"
                emptyText="No traits yet. Traits are characteristics like template/draft/final, inbound/outbound, original/copy."
                columns={[
                    { label: "Key", render: (i) => <span className="font-mono text-xs text-blue-700">{i.key}</span>, width: "120px" },
                    { label: "Display Name", render: (i) => <div><div className="font-medium text-gray-900">{i.displayName}</div><div className="text-xs text-gray-500">{i.description}</div></div> },
                    { label: "Dimension", render: (i) => <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-medium text-gray-700">{i.dimension}</span>, width: "130px" },
                    { label: "Suppress PII", render: (i) => <span className="text-xs">{i.suppressPii ? "✓" : "—"}</span>, width: "100px" },
                ]}
                onAdd={() => setEditing({ item: blank(), idx: null })}
                onEdit={(item, idx) => setEditing({ item: { ...item }, idx })}
                onDelete={del}
            />
            {editing && (
                <EditModal
                    title={editing.idx === null ? "Add Trait" : "Edit Trait"}
                    onSave={save}
                    onCancel={() => setEditing(null)}
                    canSave={!!editing.item.key.trim() && !!editing.item.displayName.trim() && !!editing.item.dimension}
                >
                    <div className="grid grid-cols-3 gap-3">
                        <Field label="Key *" hint="UPPERCASE">
                            <input value={editing.item.key} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, key: e.target.value.toUpperCase().replace(/\s/g, "_") } })} className={`${inputCls} font-mono`} placeholder="TEMPLATE" />
                        </Field>
                        <Field label="Display Name *">
                            <input value={editing.item.displayName} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, displayName: e.target.value } })} className={inputCls} />
                        </Field>
                        <Field label="Dimension *">
                            <select value={editing.item.dimension} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, dimension: e.target.value } })} className={inputCls}>
                                {DIMENSIONS.map((d) => <option key={d} value={d}>{d}</option>)}
                            </select>
                        </Field>
                    </div>
                    <Field label="Description *">
                        <textarea rows={2} value={editing.item.description} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, description: e.target.value } })} className={inputCls} />
                    </Field>
                    <Field label="Detection Hint" hint="Guidance for the LLM">
                        <input value={editing.item.detectionHint ?? ""} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, detectionHint: e.target.value || undefined } })} className={inputCls} placeholder="Look for placeholder patterns like {Name}" />
                    </Field>
                    <Field label="Indicators" hint="Comma-separated keywords/patterns">
                        <ChipInput values={editing.item.indicators} onChange={(v) => setEditing({ ...editing, item: { ...editing.item, indicators: v } })} placeholder="DRAFT, WIP, v0." />
                    </Field>
                    <label className="flex items-center gap-2 text-sm text-gray-700">
                        <input type="checkbox" checked={editing.item.suppressPii} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, suppressPii: e.target.checked } })} className="rounded border-gray-300" />
                        Suppress PII findings (e.g. for templates with placeholder data)
                    </label>
                </EditModal>
            )}
        </>
    );
}
