"use client";

import { useState } from "react";
import { PiiItem } from "../_lib/types";
import { ListView } from "../_components/ListView";
import { EditModal, Field, inputCls } from "../_components/EditModal";

export function PiiTab({ items, onChange }: { items: PiiItem[]; onChange: (next: PiiItem[]) => void }) {
    const [editing, setEditing] = useState<{ item: PiiItem; idx: number | null } | null>(null);
    const [testText, setTestText] = useState("");

    const blank = (): PiiItem => ({ name: "", type: "", regex: "", confidence: 0.85 });

    const save = () => {
        if (!editing) return;
        const next = [...items];
        if (editing.idx !== null) next[editing.idx] = editing.item;
        else next.push(editing.item);
        onChange(next);
        setEditing(null);
        setTestText("");
    };

    const del = (_: PiiItem, idx: number) => {
        if (!confirm("Delete this PII pattern?")) return;
        onChange(items.filter((_, i) => i !== idx));
    };

    const testRegex = (): { match: boolean; preview: string; error?: string } => {
        if (!editing || !editing.item.regex || !testText) return { match: false, preview: "" };
        try {
            const flags = (editing.item.flags?.toLowerCase() ?? "").includes("case_insensitive") ? "gi" : "g";
            const re = new RegExp(editing.item.regex, flags);
            const matches = testText.match(re);
            if (matches) return { match: true, preview: matches.slice(0, 5).join(" • ") };
            return { match: false, preview: "No match" };
        } catch (e) {
            return { match: false, preview: "", error: e instanceof Error ? e.message : "Invalid regex" };
        }
    };
    const result = editing ? testRegex() : { match: false, preview: "" };

    return (
        <>
            <ListView<PiiItem>
                title="PII Patterns"
                items={items}
                addLabel="Add Pattern"
                emptyText="No PII patterns yet. Define regex patterns for the platform's regex-based PII scanner."
                columns={[
                    { label: "Type", render: (i) => <span className="font-mono text-xs text-blue-700">{i.type}</span>, width: "180px" },
                    { label: "Name", render: (i) => <span className="text-gray-900">{i.name}</span> },
                    { label: "Regex", render: (i) => <code className="font-mono text-[10px] text-gray-500 truncate block max-w-md">{i.regex}</code> },
                    { label: "Conf.", render: (i) => <span className="text-xs text-gray-600">{i.confidence}</span>, width: "60px" },
                ]}
                onAdd={() => setEditing({ item: blank(), idx: null })}
                onEdit={(item, idx) => setEditing({ item: { ...item }, idx })}
                onDelete={del}
            />
            {editing && (
                <EditModal
                    title={editing.idx === null ? "Add PII Pattern" : "Edit PII Pattern"}
                    onSave={save}
                    onCancel={() => { setEditing(null); setTestText(""); }}
                    canSave={!!editing.item.name.trim() && !!editing.item.type.trim() && !!editing.item.regex.trim() && !result.error}
                >
                    <div className="grid grid-cols-2 gap-3">
                        <Field label="Display Name *">
                            <input value={editing.item.name} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, name: e.target.value } })} className={inputCls} placeholder="UK National Insurance" />
                        </Field>
                        <Field label="Type Key *" hint="UPPERCASE">
                            <input value={editing.item.type} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, type: e.target.value.toUpperCase().replace(/\s/g, "_") } })} className={`${inputCls} font-mono`} placeholder="NATIONAL_INSURANCE" />
                        </Field>
                    </div>
                    <Field label="Regex Pattern *">
                        <input value={editing.item.regex} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, regex: e.target.value } })} className={`${inputCls} font-mono`} />
                    </Field>
                    <div className="grid grid-cols-2 gap-3">
                        <Field label="Confidence *" hint="0 to 1">
                            <input type="number" min={0} max={1} step={0.05} value={editing.item.confidence} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, confidence: parseFloat(e.target.value) || 0 } })} className={inputCls} />
                        </Field>
                        <Field label="Flags" hint="e.g. CASE_INSENSITIVE">
                            <input value={editing.item.flags ?? ""} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, flags: e.target.value || undefined } })} className={inputCls} />
                        </Field>
                    </div>
                    <Field label="Test against text" hint="Live regex preview">
                        <textarea rows={2} value={testText} onChange={(e) => setTestText(e.target.value)} className={inputCls} placeholder="Paste sample text to test the regex..." />
                    </Field>
                    {testText && (
                        <div className={`rounded-md p-2 text-xs ${result.error ? "bg-red-50 text-red-700" : result.match ? "bg-green-50 text-green-700" : "bg-gray-50 text-gray-500"}`}>
                            {result.error ? <>Regex error: {result.error}</> : result.match ? <>✓ Match: <span className="font-mono">{result.preview}</span></> : "No match"}
                        </div>
                    )}
                </EditModal>
            )}
        </>
    );
}
