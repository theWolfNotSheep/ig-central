"use client";

import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { MetadataField, MetadataSchemaItem } from "../_lib/types";
import { ListView } from "../_components/ListView";
import { EditModal, Field, inputCls } from "../_components/EditModal";
import { ChipInput } from "../_components/RefSelect";

const FIELD_TYPES = ["TEXT", "DATE", "NUMBER", "CURRENCY", "KEYWORD", "BOOLEAN"];

export function MetadataTab({ items, onChange }: { items: MetadataSchemaItem[]; onChange: (next: MetadataSchemaItem[]) => void }) {
    const [editing, setEditing] = useState<{ item: MetadataSchemaItem; idx: number | null } | null>(null);

    const blank = (): MetadataSchemaItem => ({ name: "", description: "", extractionContext: "", fields: [] });

    const save = () => {
        if (!editing) return;
        const next = [...items];
        if (editing.idx !== null) next[editing.idx] = editing.item;
        else next.push(editing.item);
        onChange(next);
        setEditing(null);
    };

    const del = (_: MetadataSchemaItem, idx: number) => {
        if (!confirm("Delete this metadata schema?")) return;
        onChange(items.filter((_, i) => i !== idx));
    };

    const addField = () => {
        if (!editing) return;
        const f: MetadataField = { fieldName: "", dataType: "TEXT", required: false, description: "", examples: [] };
        setEditing({ ...editing, item: { ...editing.item, fields: [...editing.item.fields, f] } });
    };
    const updateField = (idx: number, f: MetadataField) => {
        if (!editing) return;
        const next = [...editing.item.fields];
        next[idx] = f;
        setEditing({ ...editing, item: { ...editing.item, fields: next } });
    };
    const removeField = (idx: number) => {
        if (!editing) return;
        setEditing({ ...editing, item: { ...editing.item, fields: editing.item.fields.filter((_, i) => i !== idx) } });
    };

    return (
        <>
            <ListView<MetadataSchemaItem>
                title="Metadata Schemas"
                items={items}
                addLabel="Add Schema"
                emptyText="No metadata schemas yet. Each schema defines fields the LLM extracts from documents in linked taxonomy categories."
                columns={[
                    { label: "Name", render: (i) => <div><div className="font-medium text-gray-900">{i.name}</div><div className="text-xs text-gray-500">{i.description}</div></div> },
                    { label: "Fields", render: (i) => <span className="text-xs text-gray-500">{i.fields.length}</span>, width: "70px" },
                ]}
                onAdd={() => setEditing({ item: blank(), idx: null })}
                onEdit={(item, idx) => setEditing({ item: { ...item, fields: [...item.fields] }, idx })}
                onDelete={del}
            />
            {editing && (
                <EditModal
                    title={editing.idx === null ? "Add Metadata Schema" : "Edit Metadata Schema"}
                    onSave={save}
                    onCancel={() => setEditing(null)}
                    canSave={!!editing.item.name.trim() && editing.item.fields.length > 0 && editing.item.fields.every((f) => f.fieldName.trim())}
                >
                    <Field label="Name *" hint="Used as reference key by taxonomy entries">
                        <input value={editing.item.name} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, name: e.target.value } })} className={inputCls} placeholder="e.g. Contract / Agreement" />
                    </Field>
                    <Field label="Description *">
                        <textarea rows={2} value={editing.item.description} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, description: e.target.value } })} className={inputCls} />
                    </Field>
                    <Field label="Extraction Context *" hint="Prompt guidance for the LLM">
                        <textarea rows={2} value={editing.item.extractionContext} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, extractionContext: e.target.value } })} className={inputCls} placeholder="Extract key contract details including parties, dates, and value" />
                    </Field>

                    <div>
                        <div className="mb-2 flex items-center justify-between">
                            <label className="text-xs font-medium text-gray-600">Fields *</label>
                            <button type="button" onClick={addField} className="flex items-center gap-1 text-xs text-blue-600 hover:text-blue-800">
                                <Plus className="h-3 w-3" /> Add Field
                            </button>
                        </div>
                        <div className="space-y-3">
                            {editing.item.fields.map((f, fi) => (
                                <div key={fi} className="rounded-md border border-gray-200 bg-gray-50 p-3">
                                    <div className="mb-2 flex items-center justify-between">
                                        <span className="text-[10px] font-semibold uppercase tracking-wider text-gray-500">Field {fi + 1}</span>
                                        <button type="button" onClick={() => removeField(fi)} className="text-gray-400 hover:text-red-600">
                                            <Trash2 className="h-3.5 w-3.5" />
                                        </button>
                                    </div>
                                    <div className="grid grid-cols-3 gap-2">
                                        <input value={f.fieldName} onChange={(e) => updateField(fi, { ...f, fieldName: e.target.value })} className={inputCls} placeholder="Field name" />
                                        <select value={f.dataType} onChange={(e) => updateField(fi, { ...f, dataType: e.target.value })} className={inputCls}>
                                            {FIELD_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                                        </select>
                                        <label className="flex items-center gap-1.5 text-xs text-gray-700">
                                            <input type="checkbox" checked={f.required} onChange={(e) => updateField(fi, { ...f, required: e.target.checked })} className="rounded border-gray-300" />
                                            Required
                                        </label>
                                    </div>
                                    <input value={f.description} onChange={(e) => updateField(fi, { ...f, description: e.target.value })} className={`${inputCls} mt-2`} placeholder="Description / extraction hint" />
                                    <ChipInput values={f.examples ?? []} onChange={(v) => updateField(fi, { ...f, examples: v })} placeholder="Example values, comma-separated" />
                                </div>
                            ))}
                            {editing.item.fields.length === 0 && <p className="text-xs text-gray-400 italic">No fields yet — add at least one.</p>}
                        </div>
                    </div>
                </EditModal>
            )}
        </>
    );
}
