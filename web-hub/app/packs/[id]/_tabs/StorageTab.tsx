"use client";

import { useState } from "react";
import { StorageItem } from "../_lib/types";
import { ListView } from "../_components/ListView";
import { EditModal, Field, inputCls } from "../_components/EditModal";

export function StorageTab({ items, onChange }: { items: StorageItem[]; onChange: (next: StorageItem[]) => void }) {
    const [editing, setEditing] = useState<{ item: StorageItem; idx: number | null } | null>(null);

    const blank = (): StorageItem => ({
        name: "", description: "", encryptionType: "AES-256",
        immutable: false, geographicallyRestricted: false, region: undefined,
    });

    const save = () => {
        if (!editing) return;
        const next = [...items];
        if (editing.idx !== null) next[editing.idx] = editing.item;
        else next.push(editing.item);
        onChange(next);
        setEditing(null);
    };

    const del = (_: StorageItem, idx: number) => {
        if (!confirm("Delete this storage tier?")) return;
        onChange(items.filter((_, i) => i !== idx));
    };

    return (
        <>
            <ListView<StorageItem>
                title="Storage Tiers"
                items={items}
                addLabel="Add Tier"
                emptyText="No storage tiers yet. Define encryption + geographic constraints for each sensitivity level."
                columns={[
                    { label: "Name", render: (i) => <div><div className="font-medium text-gray-900">{i.name}</div><div className="text-xs text-gray-500">{i.description}</div></div> },
                    { label: "Encryption", render: (i) => <span className="font-mono text-xs text-gray-700">{i.encryptionType}</span>, width: "120px" },
                    { label: "Immutable", render: (i) => <span className="text-xs">{i.immutable ? "✓" : "—"}</span>, width: "90px" },
                    { label: "Region", render: (i) => <span className="text-xs text-gray-600">{i.geographicallyRestricted ? (i.region ?? "Restricted") : "—"}</span>, width: "100px" },
                ]}
                onAdd={() => setEditing({ item: blank(), idx: null })}
                onEdit={(item, idx) => setEditing({ item: { ...item }, idx })}
                onDelete={del}
            />
            {editing && (
                <EditModal
                    title={editing.idx === null ? "Add Storage Tier" : "Edit Storage Tier"}
                    onSave={save}
                    onCancel={() => setEditing(null)}
                    canSave={!!editing.item.name.trim() && !!editing.item.encryptionType.trim()}
                >
                    <Field label="Name *">
                        <input value={editing.item.name} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, name: e.target.value } })} className={inputCls} placeholder="e.g. Confidential Store" />
                    </Field>
                    <Field label="Description *">
                        <textarea rows={2} value={editing.item.description} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, description: e.target.value } })} className={inputCls} />
                    </Field>
                    <div className="grid grid-cols-2 gap-3">
                        <Field label="Encryption Type *">
                            <input value={editing.item.encryptionType} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, encryptionType: e.target.value } })} className={inputCls} placeholder="AES-256, AES-256-GCM" />
                        </Field>
                        <Field label="Region" hint="Required when geo-restricted">
                            <input value={editing.item.region ?? ""} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, region: e.target.value || undefined } })} className={inputCls} placeholder="UK, EU, US" />
                        </Field>
                    </div>
                    <div className="flex gap-4">
                        <label className="flex items-center gap-2 text-sm text-gray-700">
                            <input type="checkbox" checked={editing.item.immutable} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, immutable: e.target.checked } })} className="rounded border-gray-300" />
                            Immutable (write-once)
                        </label>
                        <label className="flex items-center gap-2 text-sm text-gray-700">
                            <input type="checkbox" checked={editing.item.geographicallyRestricted} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, geographicallyRestricted: e.target.checked } })} className="rounded border-gray-300" />
                            Geographically restricted
                        </label>
                    </div>
                </EditModal>
            )}
        </>
    );
}
