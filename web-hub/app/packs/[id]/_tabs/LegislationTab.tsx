"use client";

import { useState } from "react";
import { LegislationItem } from "../_lib/types";
import { ListView } from "../_components/ListView";
import { EditModal, Field, inputCls } from "../_components/EditModal";
import { ChipInput } from "../_components/RefSelect";

export function LegislationTab({ items, onChange }: { items: LegislationItem[]; onChange: (next: LegislationItem[]) => void }) {
    const [editing, setEditing] = useState<{ item: LegislationItem; idx: number | null } | null>(null);

    const blank = (): LegislationItem => ({
        key: "", name: "", shortName: "", jurisdiction: "UK",
        url: "", description: "", relevantArticles: [],
    });

    const save = () => {
        if (!editing) return;
        const next = [...items];
        if (editing.idx !== null) next[editing.idx] = editing.item;
        else next.push(editing.item);
        onChange(next);
        setEditing(null);
    };

    const del = (_: LegislationItem, idx: number) => {
        if (!confirm("Delete this legislation entry? Any retention schedules or policies referencing it will show a warning.")) return;
        onChange(items.filter((_, i) => i !== idx));
    };

    return (
        <>
            <ListView<LegislationItem>
                title="Legislation"
                items={items}
                addLabel="Add Legislation"
                emptyText="No legislation yet. Add the laws and regulations that this pack references."
                columns={[
                    { label: "Key", render: (i) => <span className="font-mono text-xs text-blue-700">{i.key}</span>, width: "120px" },
                    { label: "Name", render: (i) => <div><div className="font-medium text-gray-900">{i.shortName || i.name}</div><div className="text-xs text-gray-500">{i.name}</div></div> },
                    { label: "Jurisdiction", render: (i) => <span className="text-xs text-gray-600">{i.jurisdiction}</span>, width: "100px" },
                    { label: "Articles", render: (i) => <span className="text-xs text-gray-500">{i.relevantArticles.length}</span>, width: "70px" },
                ]}
                onAdd={() => setEditing({ item: blank(), idx: null })}
                onEdit={(item, idx) => setEditing({ item: { ...item }, idx })}
                onDelete={del}
            />
            {editing && (
                <EditModal
                    title={editing.idx === null ? "Add Legislation" : "Edit Legislation"}
                    onSave={save}
                    onCancel={() => setEditing(null)}
                    canSave={!!editing.item.key.trim() && !!editing.item.name.trim()}
                >
                    <div className="grid grid-cols-2 gap-3">
                        <Field label="Key *" hint="Unique uppercase identifier">
                            <input value={editing.item.key} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, key: e.target.value.toUpperCase().replace(/\s/g, "_") } })} className={`${inputCls} font-mono`} placeholder="e.g. GDPR" />
                        </Field>
                        <Field label="Jurisdiction *">
                            <input value={editing.item.jurisdiction} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, jurisdiction: e.target.value } })} className={inputCls} placeholder="e.g. UK, US, EU" />
                        </Field>
                    </div>
                    <Field label="Full Name *">
                        <input value={editing.item.name} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, name: e.target.value } })} className={inputCls} />
                    </Field>
                    <Field label="Short Name *">
                        <input value={editing.item.shortName} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, shortName: e.target.value } })} className={inputCls} placeholder="e.g. GDPR" />
                    </Field>
                    <Field label="URL">
                        <input value={editing.item.url} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, url: e.target.value } })} className={inputCls} placeholder="https://..." />
                    </Field>
                    <Field label="Description *">
                        <textarea rows={3} value={editing.item.description} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, description: e.target.value } })} className={inputCls} />
                    </Field>
                    <Field label="Relevant Articles" hint="Comma-separated">
                        <ChipInput values={editing.item.relevantArticles} onChange={(v) => setEditing({ ...editing, item: { ...editing.item, relevantArticles: v } })} placeholder="Article 5, Article 17, Section 386" />
                    </Field>
                </EditModal>
            )}
        </>
    );
}
