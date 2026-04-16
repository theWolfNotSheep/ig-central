"use client";

import { useState } from "react";
import { LegislationItem, RetentionItem } from "../_lib/types";
import { ListView } from "../_components/ListView";
import { EditModal, Field, inputCls } from "../_components/EditModal";
import { MultiRefSelect } from "../_components/RefSelect";

const DISPOSITIONS = ["DELETE", "ARCHIVE", "TRANSFER", "REVIEW", "ANONYMISE", "PERMANENT"];
const TRIGGERS = ["DATE_CREATED", "DATE_LAST_MODIFIED", "DATE_CLOSED", "EVENT_BASED", "END_OF_FINANCIAL_YEAR", "SUPERSEDED"];

export function RetentionTab({ items, legislation, onChange }: {
    items: RetentionItem[];
    legislation: LegislationItem[];
    onChange: (next: RetentionItem[]) => void;
}) {
    const [editing, setEditing] = useState<{ item: RetentionItem; idx: number | null } | null>(null);

    const blank = (): RetentionItem => ({
        name: "", description: "", retentionDays: 2555, retentionDuration: "P7Y",
        retentionTrigger: "DATE_CREATED", dispositionAction: "REVIEW",
        legalHoldOverride: true, regulatoryBasis: "", jurisdiction: "UK", legislationRefs: [],
    });

    const save = () => {
        if (!editing) return;
        const next = [...items];
        if (editing.idx !== null) next[editing.idx] = editing.item;
        else next.push(editing.item);
        onChange(next);
        setEditing(null);
    };

    const del = (_: RetentionItem, idx: number) => {
        if (!confirm("Delete this retention schedule? Taxonomy entries referencing it by name will show a warning.")) return;
        onChange(items.filter((_, i) => i !== idx));
    };

    const legOptions = legislation.map((l) => ({ value: l.key, label: l.shortName || l.name }));

    return (
        <>
            <ListView<RetentionItem>
                title="Retention Schedules"
                items={items}
                addLabel="Add Schedule"
                emptyText="No retention schedules yet. Each defines how long records are kept and what happens after."
                columns={[
                    { label: "Name", render: (i) => <div><div className="font-medium text-gray-900">{i.name}</div><div className="text-xs text-gray-500">{i.description}</div></div> },
                    { label: "Duration", render: (i) => <span className="font-mono text-xs text-gray-700">{i.retentionDuration || `${i.retentionDays}d`}</span>, width: "100px" },
                    { label: "Trigger", render: (i) => <span className="text-xs text-gray-600">{i.retentionTrigger?.replace(/_/g, " ").toLowerCase() || "—"}</span>, width: "140px" },
                    { label: "Disposition", render: (i) => <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-medium text-gray-700">{i.dispositionAction}</span>, width: "100px" },
                    { label: "Jurisdiction", render: (i) => <span className="text-xs text-gray-500">{i.jurisdiction || "—"}</span>, width: "90px" },
                ]}
                onAdd={() => setEditing({ item: blank(), idx: null })}
                onEdit={(item, idx) => setEditing({ item: { ...item }, idx })}
                onDelete={del}
            />
            {editing && (
                <EditModal
                    title={editing.idx === null ? "Add Retention Schedule" : "Edit Retention Schedule"}
                    onSave={save}
                    onCancel={() => setEditing(null)}
                    canSave={!!editing.item.name.trim() && !!editing.item.dispositionAction}
                >
                    <Field label="Name *" hint="Used as reference key by taxonomy entries">
                        <input value={editing.item.name} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, name: e.target.value } })} className={inputCls} placeholder="e.g. Standard Business" />
                    </Field>
                    <Field label="Description *">
                        <textarea rows={2} value={editing.item.description} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, description: e.target.value } })} className={inputCls} />
                    </Field>
                    <div className="grid grid-cols-3 gap-3">
                        <Field label="Days *">
                            <input type="number" min={-1} value={editing.item.retentionDays} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, retentionDays: parseInt(e.target.value) || 0 } })} className={inputCls} />
                        </Field>
                        <Field label="ISO 8601 Duration" hint="e.g. P7Y">
                            <input value={editing.item.retentionDuration ?? ""} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, retentionDuration: e.target.value || undefined } })} className={inputCls} placeholder="P7Y or PERMANENT" />
                        </Field>
                        <Field label="Jurisdiction">
                            <input value={editing.item.jurisdiction ?? ""} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, jurisdiction: e.target.value || undefined } })} className={inputCls} placeholder="UK, US, EU" />
                        </Field>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                        <Field label="Trigger">
                            <select value={editing.item.retentionTrigger ?? ""} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, retentionTrigger: e.target.value || undefined } })} className={inputCls}>
                                <option value="">(not set)</option>
                                {TRIGGERS.map((t) => <option key={t} value={t}>{t.replace(/_/g, " ").toLowerCase()}</option>)}
                            </select>
                        </Field>
                        <Field label="Disposition Action *">
                            <select value={editing.item.dispositionAction} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, dispositionAction: e.target.value } })} className={inputCls}>
                                {DISPOSITIONS.map((d) => <option key={d} value={d}>{d}</option>)}
                            </select>
                        </Field>
                    </div>
                    <Field label="Regulatory Basis *" hint="Free-text justification">
                        <input value={editing.item.regulatoryBasis} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, regulatoryBasis: e.target.value } })} className={inputCls} placeholder="e.g. HMRC requirements, Companies Act 2006 s.388" />
                    </Field>
                    <label className="flex items-center gap-2 text-sm text-gray-700">
                        <input type="checkbox" checked={editing.item.legalHoldOverride} onChange={(e) => setEditing({ ...editing, item: { ...editing.item, legalHoldOverride: e.target.checked } })} className="rounded border-gray-300" />
                        Legal hold can override this retention period
                    </label>
                    <Field label="Legislation References">
                        <MultiRefSelect values={editing.item.legislationRefs} options={legOptions} onChange={(v) => setEditing({ ...editing, item: { ...editing.item, legislationRefs: v } })} />
                    </Field>
                </EditModal>
            )}
        </>
    );
}
