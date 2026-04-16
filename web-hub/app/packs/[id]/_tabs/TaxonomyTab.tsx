"use client";

import { useMemo, useRef, useState } from "react";
import { Plus, Pencil, Trash2, ChevronDown, ChevronRight, Upload, FileText, X } from "lucide-react";
import {
    Level, LegislationItem, MetadataSchemaItem, RetentionItem, SensitivityItem, TaxonomyItem,
} from "../_lib/types";
import { EditModal, Field, inputCls } from "../_components/EditModal";
import { ChipInput, SingleRefSelect } from "../_components/RefSelect";

const LEVELS: Level[] = ["FUNCTION", "ACTIVITY", "TRANSACTION"];
const TRIGGERS = ["DATE_CREATED", "DATE_LAST_MODIFIED", "DATE_CLOSED", "EVENT_BASED", "END_OF_FINANCIAL_YEAR", "SUPERSEDED"];

interface CsvRow {
    function: string; functionCode: string; activity: string;
    recordClass: string; recordCode: string; typicalRecords: string;
    jurisdiction: string; retentionPeriod: string; legalCitation: string;
}

type FieldKey = keyof CsvRow;

interface FieldDef {
    key: FieldKey;
    label: string;
    required: boolean;
    aliases: string[];
}

// Canonical expected columns + common aliases (normalised — lowercase, alphanumeric only).
// `required` fields must be mapped before the user can import.
const FIELD_DEFS: FieldDef[] = [
    { key: "function", label: "Tier 1 Function", required: true, aliases: ["tier1function", "function", "tier1", "functionname", "toplevelfunction"] },
    { key: "functionCode", label: "Function Code", required: false, aliases: ["functioncode", "funccode", "tier1code"] },
    { key: "activity", label: "Tier 2 Activity", required: true, aliases: ["tier2activity", "activity", "tier2", "subfunction", "activityname"] },
    { key: "recordClass", label: "Tier 3 Record Class", required: true, aliases: ["tier3recordclass", "recordclass", "tier3", "documenttype", "classname"] },
    { key: "recordCode", label: "Record Code", required: true, aliases: ["recordcode", "classificationcode", "recordclasscode", "code"] },
    { key: "typicalRecords", label: "Typical Records", required: false, aliases: ["typicalrecords", "examples", "records", "exampledocuments"] },
    { key: "jurisdiction", label: "Jurisdiction", required: false, aliases: ["jurisdiction", "country", "region"] },
    { key: "retentionPeriod", label: "Retention Period", required: false, aliases: ["retentionperiod", "retention", "keep", "retentionschedule"] },
    { key: "legalCitation", label: "Legal Citation", required: false, aliases: ["legalcitation", "citation", "legal", "authority", "statute"] },
];

function normalize(s: string): string {
    return s.toLowerCase().replace(/[^a-z0-9]/g, "");
}

function parseCsvLine(line: string): string[] {
    const fields: string[] = [];
    let cur = ""; let inQ = false;
    for (let i = 0; i < line.length; i++) {
        const ch = line[i];
        if (inQ) {
            if (ch === '"' && line[i + 1] === '"') { cur += '"'; i++; }
            else if (ch === '"') inQ = false;
            else cur += ch;
        } else {
            if (ch === '"') inQ = true;
            else if (ch === ",") { fields.push(cur.trim()); cur = ""; }
            else cur += ch;
        }
    }
    fields.push(cur.trim());
    return fields;
}

interface RawCsv { headers: string[]; rows: string[][]; errors: string[] }

function parseCsvRaw(text: string): RawCsv {
    const lines = text.split(/\r?\n/).filter((l) => l.trim());
    if (lines.length < 2) return { headers: [], rows: [], errors: ["CSV must have a header row and at least one data row"] };
    const headers = parseCsvLine(lines[0]);
    const rows: string[][] = [];
    const errors: string[] = [];
    for (let i = 1; i < lines.length; i++) {
        const f = parseCsvLine(lines[i]);
        if (f.length === 1 && !f[0]) continue;
        rows.push(f);
    }
    return { headers, rows, errors };
}

function autoMap(headers: string[]): Record<FieldKey, number> {
    const normHeaders = headers.map(normalize);
    const used = new Set<number>();
    const out = {} as Record<FieldKey, number>;
    for (const def of FIELD_DEFS) {
        const labelNorm = normalize(def.label);
        const candidates = [labelNorm, ...def.aliases];
        let idx = -1;
        // Pass 1: exact match against label or alias
        for (let i = 0; i < normHeaders.length; i++) {
            if (used.has(i)) continue;
            if (candidates.includes(normHeaders[i])) { idx = i; break; }
        }
        // Pass 2: substring match (header contains alias or vice versa)
        if (idx < 0) {
            for (let i = 0; i < normHeaders.length; i++) {
                if (used.has(i)) continue;
                if (candidates.some((a) => normHeaders[i].includes(a) || a.includes(normHeaders[i]))) { idx = i; break; }
            }
        }
        if (idx >= 0) used.add(idx);
        out[def.key] = idx;
    }
    return out;
}

function buildCsvRows(rows: string[][], mapping: Record<FieldKey, number>): CsvRow[] {
    const get = (r: string[], i: number) => (i >= 0 && i < r.length ? r[i] : "");
    return rows
        .map((r) => ({
            function: get(r, mapping.function),
            functionCode: get(r, mapping.functionCode),
            activity: get(r, mapping.activity),
            recordClass: get(r, mapping.recordClass),
            recordCode: get(r, mapping.recordCode),
            typicalRecords: get(r, mapping.typicalRecords),
            jurisdiction: get(r, mapping.jurisdiction),
            retentionPeriod: get(r, mapping.retentionPeriod),
            legalCitation: get(r, mapping.legalCitation),
        }))
        // Drop rows that are missing any required field after mapping
        .filter((row) => FIELD_DEFS.every((d) => !d.required || row[d.key].trim()));
}

function buildFromCsv(rows: CsvRow[], packJurisdiction: string): TaxonomyItem[] {
    const out: TaxonomyItem[] = [];
    const seenF = new Set<string>(); const seenA = new Set<string>();
    for (const row of rows) {
        const fc = row.functionCode || row.function.slice(0, 3).toUpperCase();
        const jur = row.jurisdiction || packJurisdiction;
        if (!seenF.has(fc)) {
            seenF.add(fc);
            out.push({ classificationCode: fc, name: row.function, level: "FUNCTION", description: row.function, defaultSensitivity: "INTERNAL", keywords: [row.function.toLowerCase()], jurisdiction: jur });
        }
        const codeParts = row.recordCode.split("-");
        const ac = codeParts.length >= 2 ? `${codeParts[0]}-${codeParts[1]}` : `${fc}-GEN`;
        const akey = `${fc}:${row.activity}`;
        if (!seenA.has(akey)) {
            seenA.add(akey);
            out.push({ classificationCode: ac, name: row.activity, parentName: row.function, level: "ACTIVITY", description: row.activity, defaultSensitivity: "INTERNAL", keywords: [row.activity.toLowerCase()], jurisdiction: jur });
        }
        const tr = row.typicalRecords ? row.typicalRecords.split(/,\s*/).filter(Boolean) : [];
        out.push({
            classificationCode: row.recordCode, name: row.recordClass, parentName: row.activity, level: "TRANSACTION",
            description: `${row.recordClass}: ${row.typicalRecords || row.recordClass}`,
            defaultSensitivity: "CONFIDENTIAL", keywords: tr.map((r) => r.toLowerCase().slice(0, 30)),
            typicalRecords: tr, jurisdiction: jur, retentionPeriodText: row.retentionPeriod, legalCitation: row.legalCitation,
        });
    }
    return out;
}

export function TaxonomyTab({ items, retention, metadata, sensitivity, packJurisdiction, onChange }: {
    items: TaxonomyItem[];
    retention: RetentionItem[];
    metadata: MetadataSchemaItem[];
    sensitivity: SensitivityItem[];
    legislation: LegislationItem[];
    packJurisdiction: string;
    onChange: (next: TaxonomyItem[]) => void;
}) {
    const [expanded, setExpanded] = useState<Set<string>>(new Set());
    const [editing, setEditing] = useState<{ entry: TaxonomyItem; idx: number | null } | null>(null);
    const [csvOpen, setCsvOpen] = useState(false);
    const [csvFile, setCsvFile] = useState<File | null>(null);
    const [csvRaw, setCsvRaw] = useState<RawCsv | null>(null);
    const [mapping, setMapping] = useState<Record<FieldKey, number> | null>(null);
    const [dragOver, setDragOver] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const byParent = useMemo(() => {
        const m = new Map<string, TaxonomyItem[]>();
        for (const e of items) {
            const k = e.parentName || "__ROOT__";
            if (!m.has(k)) m.set(k, []);
            m.get(k)!.push(e);
        }
        return m;
    }, [items]);

    const roots = useMemo(() => items.filter((e) => !e.parentName), [items]);

    const toggle = (n: string) => {
        const x = new Set(expanded);
        x.has(n) ? x.delete(n) : x.add(n);
        setExpanded(x);
    };

    const handleAddRoot = () => setEditing({ entry: { classificationCode: "", name: "", level: "FUNCTION", jurisdiction: packJurisdiction }, idx: null });
    const handleAddChild = (parent: TaxonomyItem) => {
        const nextLevel: Level = parent.level === "FUNCTION" ? "ACTIVITY" : "TRANSACTION";
        setEditing({ entry: { classificationCode: "", name: "", parentName: parent.name, level: nextLevel, jurisdiction: parent.jurisdiction }, idx: null });
    };
    const handleEdit = (entry: TaxonomyItem) => {
        const idx = items.findIndex((e) => e.classificationCode === entry.classificationCode && e.name === entry.name);
        setEditing({ entry: { ...entry }, idx });
    };
    const handleDelete = (entry: TaxonomyItem) => {
        if (!confirm(`Delete "${entry.name}" and all its descendants?`)) return;
        const toDelete = new Set<string>([entry.name]);
        let changed = true;
        while (changed) {
            changed = false;
            for (const e of items) {
                if (e.parentName && toDelete.has(e.parentName) && !toDelete.has(e.name)) {
                    toDelete.add(e.name); changed = true;
                }
            }
        }
        onChange(items.filter((e) => !toDelete.has(e.name)));
    };

    const handleSaveEntry = () => {
        if (!editing || !editing.entry.name.trim()) return;
        const e = editing.entry;
        if (!e.classificationCode.trim()) {
            e.classificationCode = e.name.substring(0, 3).toUpperCase().replace(/[^A-Z]/g, "");
        }
        const next = [...items];
        if (editing.idx !== null) {
            const oldName = items[editing.idx].name;
            if (oldName !== e.name) {
                for (let i = 0; i < next.length; i++) {
                    if (next[i].parentName === oldName) next[i] = { ...next[i], parentName: e.name };
                }
            }
            next[editing.idx] = e;
        } else next.push(e);
        onChange(next);
        setEditing(null);
    };

    const resetCsvState = () => {
        setCsvFile(null);
        setCsvRaw(null);
        setMapping(null);
        setDragOver(false);
        if (fileInputRef.current) fileInputRef.current.value = "";
    };

    const handleFileSelected = async (file: File) => {
        if (!file.name.toLowerCase().endsWith(".csv") && file.type !== "text/csv") {
            alert("Please select a .csv file");
            return;
        }
        const text = await file.text();
        const raw = parseCsvRaw(text);
        setCsvFile(file);
        setCsvRaw(raw);
        setMapping(raw.headers.length > 0 ? autoMap(raw.headers) : null);
    };

    const builtRows: CsvRow[] = useMemo(() => {
        if (!csvRaw || !mapping) return [];
        return buildCsvRows(csvRaw.rows, mapping);
    }, [csvRaw, mapping]);

    const missingRequired = useMemo(() => {
        if (!mapping) return FIELD_DEFS.filter((d) => d.required).map((d) => d.label);
        return FIELD_DEFS.filter((d) => d.required && mapping[d.key] < 0).map((d) => d.label);
    }, [mapping]);

    const handleCsvImport = () => {
        if (!csvRaw || !mapping || missingRequired.length > 0 || builtRows.length === 0) return;
        const newItems = buildFromCsv(builtRows, packJurisdiction);
        onChange(newItems);
        setCsvOpen(false);
        resetCsvState();
    };

    // Parent options for the modal: all non-TRANSACTION nodes
    const parentOptions = items
        .filter((e) => e.level !== "TRANSACTION" && e.name !== editing?.entry.name)
        .map((e) => ({ value: e.name, label: `${e.classificationCode ? `[${e.classificationCode}] ` : ""}${e.name} (${e.level})` }));
    const retOptions = retention.map((r) => ({ value: r.name, label: r.name }));
    const metaOptions = metadata.map((m) => ({ value: m.name, label: m.name }));
    const sensOptions = sensitivity.map((s) => ({ value: s.key, label: s.displayName || s.key }));

    return (
        <div>
            <div className="mb-4 flex items-center justify-between">
                <div>
                    <h3 className="text-sm font-semibold text-gray-900">Taxonomy <span className="text-gray-400 font-normal">({items.length})</span></h3>
                    <p className="text-xs text-gray-500 mt-0.5">
                        {items.filter((i) => i.level === "FUNCTION").length} Functions · {items.filter((i) => i.level === "ACTIVITY").length} Activities · {items.filter((i) => i.level === "TRANSACTION").length} Record Classes
                    </p>
                </div>
                <div className="flex gap-2">
                    <button onClick={() => setCsvOpen(true)} className="flex items-center gap-1.5 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50">
                        <Upload className="h-3.5 w-3.5" /> Import CSV
                    </button>
                    <button onClick={handleAddRoot} className="flex items-center gap-1.5 rounded-md bg-blue-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-700">
                        <Plus className="h-3.5 w-3.5" /> Add Function
                    </button>
                </div>
            </div>

            {items.length === 0 ? (
                <div className="rounded-lg border border-dashed border-gray-300 py-10 text-center">
                    <p className="text-sm text-gray-400">No taxonomy entries yet. Add a function above or import a CSV.</p>
                </div>
            ) : (
                <div className="rounded-lg border border-gray-200 bg-white">
                    {roots.map((entry) => (
                        <Node key={`${entry.classificationCode}-${entry.name}`} entry={entry} depth={0} byParent={byParent} expanded={expanded}
                            onToggle={toggle} onEdit={handleEdit} onAddChild={handleAddChild} onDelete={handleDelete} />
                    ))}
                </div>
            )}

            {csvOpen && (
                <EditModal title="Import Taxonomy from CSV"
                    onSave={handleCsvImport}
                    onCancel={() => { setCsvOpen(false); resetCsvState(); }}
                    canSave={!!csvRaw && !!mapping && missingRequired.length === 0 && builtRows.length > 0}>
                    <p className="text-xs text-gray-500">
                        Upload a CSV file. Ideal format is the 9-column ISO 15489 layout:<br />
                        <code className="text-[10px]">Tier 1 Function, Function Code, Tier 2 Activity, Tier 3 Record Class, Record Code, Typical Records, Jurisdiction, Retention Period, Legal Citation</code><br />
                        If your headers don&apos;t match exactly, you can map them below. Importing replaces the entire taxonomy.
                    </p>

                    {!csvFile ? (
                        <div
                            onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
                            onDragLeave={() => setDragOver(false)}
                            onDrop={(e) => {
                                e.preventDefault();
                                setDragOver(false);
                                const f = e.dataTransfer.files?.[0];
                                if (f) handleFileSelected(f);
                            }}
                            onClick={() => fileInputRef.current?.click()}
                            className={`cursor-pointer rounded-lg border-2 border-dashed p-8 text-center transition ${dragOver ? "border-blue-500 bg-blue-50" : "border-gray-300 bg-gray-50 hover:bg-gray-100"}`}
                        >
                            <Upload className="mx-auto h-8 w-8 text-gray-400" />
                            <p className="mt-2 text-sm font-medium text-gray-700">Drop a CSV file here, or click to browse</p>
                            <p className="mt-1 text-xs text-gray-500">.csv files only</p>
                            <input
                                ref={fileInputRef}
                                type="file"
                                accept=".csv,text/csv"
                                className="hidden"
                                onChange={(e) => {
                                    const f = e.target.files?.[0];
                                    if (f) handleFileSelected(f);
                                }}
                            />
                        </div>
                    ) : (
                        <div className="rounded-md border border-gray-200 bg-gray-50 px-3 py-2 flex items-center gap-3">
                            <FileText className="h-4 w-4 text-gray-500" />
                            <div className="flex-1 min-w-0">
                                <div className="text-sm font-medium text-gray-800 truncate">{csvFile.name}</div>
                                <div className="text-xs text-gray-500">
                                    {csvRaw ? `${csvRaw.headers.length} columns · ${csvRaw.rows.length} data rows` : "Parsing…"}
                                </div>
                            </div>
                            <button onClick={resetCsvState} title="Remove file" className="rounded p-1 text-gray-400 hover:bg-gray-200 hover:text-gray-700">
                                <X className="h-4 w-4" />
                            </button>
                        </div>
                    )}

                    {csvRaw && csvRaw.errors.length > 0 && (
                        <div className="rounded-md bg-red-50 p-3 text-xs text-red-700">
                            <ul className="list-disc pl-4">{csvRaw.errors.slice(0, 5).map((e, i) => <li key={i}>{e}</li>)}</ul>
                        </div>
                    )}

                    {csvRaw && csvRaw.headers.length > 0 && mapping && (
                        <>
                            <div>
                                <h4 className="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-600">Map columns</h4>
                                <p className="mb-3 text-xs text-gray-500">
                                    We auto-matched your headers to the expected fields. Adjust any that are wrong — required fields are marked with *.
                                </p>
                                <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                                    {FIELD_DEFS.map((def) => {
                                        const isUnmapped = mapping[def.key] < 0;
                                        const showWarn = def.required && isUnmapped;
                                        return (
                                            <div key={def.key} className="flex items-center gap-2">
                                                <label className={`w-40 shrink-0 text-xs ${def.required ? "font-medium text-gray-800" : "text-gray-600"}`}>
                                                    {def.label}{def.required && <span className="text-red-500"> *</span>}
                                                </label>
                                                <select
                                                    value={mapping[def.key]}
                                                    onChange={(e) => setMapping({ ...mapping, [def.key]: parseInt(e.target.value, 10) })}
                                                    className={`${inputCls} flex-1 text-xs ${showWarn ? "border-red-300 bg-red-50" : ""}`}
                                                >
                                                    <option value={-1}>— not mapped —</option>
                                                    {csvRaw.headers.map((h, i) => (
                                                        <option key={i} value={i}>{h || `(column ${i + 1})`}</option>
                                                    ))}
                                                </select>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>

                            <div className={`rounded-md p-3 text-xs ${missingRequired.length > 0 ? "bg-red-50 text-red-700" : builtRows.length === 0 ? "bg-amber-50 text-amber-700" : "bg-green-50 text-green-700"}`}>
                                {missingRequired.length > 0 ? (
                                    <>Map required fields before importing: <strong>{missingRequired.join(", ")}</strong></>
                                ) : builtRows.length === 0 ? (
                                    <>Mapping looks valid but no rows have all required fields populated — check the source data.</>
                                ) : (
                                    <>Ready to import <strong>{builtRows.length}</strong> record {builtRows.length === 1 ? "class" : "classes"} — will replace the existing taxonomy on save.</>
                                )}
                            </div>
                        </>
                    )}
                </EditModal>
            )}

            {editing && (
                <EditModal title={editing.idx === null ? "Add Taxonomy Entry" : "Edit Taxonomy Entry"}
                    onSave={handleSaveEntry} onCancel={() => setEditing(null)} canSave={!!editing.entry.name.trim()}>
                    <div className="grid grid-cols-3 gap-3">
                        <Field label="Name *">
                            <input value={editing.entry.name} onChange={(e) => setEditing({ ...editing, entry: { ...editing.entry, name: e.target.value } })} className={inputCls} />
                        </Field>
                        <Field label="Code" hint="Auto if blank">
                            <input value={editing.entry.classificationCode} onChange={(e) => setEditing({ ...editing, entry: { ...editing.entry, classificationCode: e.target.value } })} className={`${inputCls} font-mono`} placeholder="HR-EMP-PER" />
                        </Field>
                        <Field label="Level">
                            <select value={editing.entry.level} onChange={(e) => setEditing({ ...editing, entry: { ...editing.entry, level: e.target.value as Level } })} className={inputCls}>
                                {LEVELS.map((l) => <option key={l} value={l}>{l}</option>)}
                            </select>
                        </Field>
                    </div>
                    <Field label="Parent">
                        <SingleRefSelect value={editing.entry.parentName} options={parentOptions} onChange={(v) => setEditing({ ...editing, entry: { ...editing.entry, parentName: v } })} placeholder="(Root)" />
                    </Field>
                    <Field label="Description">
                        <input value={editing.entry.description ?? ""} onChange={(e) => setEditing({ ...editing, entry: { ...editing.entry, description: e.target.value } })} className={inputCls} />
                    </Field>
                    <Field label="Scope Notes" hint="Inclusion/exclusion guidance">
                        <textarea rows={2} value={editing.entry.scopeNotes ?? ""} onChange={(e) => setEditing({ ...editing, entry: { ...editing.entry, scopeNotes: e.target.value } })} className={inputCls} />
                    </Field>
                    <div className="grid grid-cols-2 gap-3">
                        <Field label="Default Sensitivity">
                            <SingleRefSelect value={editing.entry.defaultSensitivity} options={sensOptions} onChange={(v) => setEditing({ ...editing, entry: { ...editing.entry, defaultSensitivity: v } })} />
                        </Field>
                        <Field label="Retention Trigger">
                            <select value={editing.entry.retentionTrigger ?? ""} onChange={(e) => setEditing({ ...editing, entry: { ...editing.entry, retentionTrigger: e.target.value || undefined } })} className={inputCls}>
                                <option value="">(not set)</option>
                                {TRIGGERS.map((t) => <option key={t} value={t}>{t.replace(/_/g, " ").toLowerCase()}</option>)}
                            </select>
                        </Field>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                        <Field label="Jurisdiction">
                            <input value={editing.entry.jurisdiction ?? ""} onChange={(e) => setEditing({ ...editing, entry: { ...editing.entry, jurisdiction: e.target.value || undefined } })} className={inputCls} />
                        </Field>
                        <Field label="Retention Period">
                            <input value={editing.entry.retentionPeriodText ?? ""} onChange={(e) => setEditing({ ...editing, entry: { ...editing.entry, retentionPeriodText: e.target.value || undefined } })} className={inputCls} placeholder="7 years, Permanent" />
                        </Field>
                    </div>
                    <Field label="Legal Citation">
                        <input value={editing.entry.legalCitation ?? ""} onChange={(e) => setEditing({ ...editing, entry: { ...editing.entry, legalCitation: e.target.value || undefined } })} className={inputCls} placeholder="IRS requirements (IRC §6001)" />
                    </Field>
                    <div className="grid grid-cols-2 gap-3">
                        <Field label="Linked Retention Schedule">
                            <SingleRefSelect value={editing.entry.retentionScheduleRef} options={retOptions} onChange={(v) => setEditing({ ...editing, entry: { ...editing.entry, retentionScheduleRef: v } })} />
                        </Field>
                        <Field label="Linked Metadata Schema">
                            <SingleRefSelect value={editing.entry.metadataSchemaRef} options={metaOptions} onChange={(v) => setEditing({ ...editing, entry: { ...editing.entry, metadataSchemaRef: v } })} />
                        </Field>
                    </div>
                    <Field label="Keywords (LLM matching)">
                        <ChipInput values={editing.entry.keywords ?? []} onChange={(v) => setEditing({ ...editing, entry: { ...editing.entry, keywords: v } })} />
                    </Field>
                    <Field label="Typical Records (descriptive examples)">
                        <ChipInput values={editing.entry.typicalRecords ?? []} onChange={(v) => setEditing({ ...editing, entry: { ...editing.entry, typicalRecords: v } })} placeholder="Board agendas, Meeting minutes" />
                    </Field>
                    <div className="flex gap-4">
                        <label className="flex items-center gap-2 text-sm text-gray-700">
                            <input type="checkbox" checked={editing.entry.personalDataFlag ?? false} onChange={(e) => setEditing({ ...editing, entry: { ...editing.entry, personalDataFlag: e.target.checked } })} className="rounded border-gray-300" />
                            Personal data
                        </label>
                        <label className="flex items-center gap-2 text-sm text-gray-700">
                            <input type="checkbox" checked={editing.entry.vitalRecordFlag ?? false} onChange={(e) => setEditing({ ...editing, entry: { ...editing.entry, vitalRecordFlag: e.target.checked } })} className="rounded border-gray-300" />
                            Vital record
                        </label>
                    </div>
                </EditModal>
            )}
        </div>
    );
}

function Node({ entry, depth, byParent, expanded, onToggle, onEdit, onAddChild, onDelete }: {
    entry: TaxonomyItem; depth: number;
    byParent: Map<string, TaxonomyItem[]>;
    expanded: Set<string>;
    onToggle: (n: string) => void;
    onEdit: (e: TaxonomyItem) => void;
    onAddChild: (parent: TaxonomyItem) => void;
    onDelete: (e: TaxonomyItem) => void;
}) {
    const children = byParent.get(entry.name) || [];
    const isExpanded = expanded.has(entry.name);
    const indent = depth === 0 ? "pl-5" : depth === 1 ? "pl-14" : "pl-24";
    const bg = depth === 0 ? "hover:bg-gray-50" : depth === 1 ? "bg-gray-50/50 hover:bg-gray-100/60" : "bg-gray-100/40 hover:bg-gray-100";

    return (
        <>
            <div className={`flex items-center gap-3 border-b border-gray-100 py-3 pr-5 ${indent} ${bg}`}>
                <button onClick={() => onToggle(entry.name)} className="shrink-0">
                    {children.length > 0 ? (isExpanded ? <ChevronDown className="h-4 w-4 text-gray-400" /> : <ChevronRight className="h-4 w-4 text-gray-400" />) : <div className="w-4" />}
                </button>
                <div className="flex-1 min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                        {entry.classificationCode && <span className="rounded bg-blue-50 px-1.5 py-0.5 font-mono text-xs text-blue-700">{entry.classificationCode}</span>}
                        <span className={depth === 0 ? "font-semibold text-gray-900" : "font-medium text-gray-800 text-sm"}>{entry.name}</span>
                        <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] uppercase tracking-wider text-gray-500">{entry.level}</span>
                        {entry.defaultSensitivity && <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[10px] text-gray-700">{entry.defaultSensitivity}</span>}
                        {entry.jurisdiction && <span className="rounded bg-indigo-50 px-1.5 py-0.5 text-[10px] text-indigo-700">{entry.jurisdiction}</span>}
                        {entry.personalDataFlag && <span className="rounded-full bg-purple-50 px-1.5 py-0.5 text-[10px] text-purple-700">PII</span>}
                        {entry.vitalRecordFlag && <span className="rounded-full bg-red-50 px-1.5 py-0.5 text-[10px] text-red-700">Vital</span>}
                    </div>
                    {entry.description && <p className="mt-0.5 text-xs text-gray-500">{entry.description}</p>}
                    {entry.retentionPeriodText && <p className="mt-0.5 text-xs text-gray-500">Retention: <span className="text-gray-700">{entry.retentionPeriodText}</span>{entry.legalCitation && <span className="text-gray-400"> — {entry.legalCitation}</span>}</p>}
                </div>
                <div className="flex shrink-0 gap-1">
                    {entry.level !== "TRANSACTION" && <button onClick={() => onAddChild(entry)} title="Add child" className="rounded p-1.5 text-gray-400 hover:bg-gray-200 hover:text-gray-700"><Plus className="h-3.5 w-3.5" /></button>}
                    <button onClick={() => onEdit(entry)} title="Edit" className="rounded p-1.5 text-gray-400 hover:bg-gray-200 hover:text-gray-700"><Pencil className="h-3.5 w-3.5" /></button>
                    <button onClick={() => onDelete(entry)} title="Delete" className="rounded p-1.5 text-gray-400 hover:bg-red-100 hover:text-red-600"><Trash2 className="h-3.5 w-3.5" /></button>
                </div>
            </div>
            {isExpanded && children.map((c) => (
                <Node key={`${c.classificationCode}-${c.name}`} entry={c} depth={depth + 1} byParent={byParent} expanded={expanded}
                    onToggle={onToggle} onEdit={onEdit} onAddChild={onAddChild} onDelete={onDelete} />
            ))}
        </>
    );
}
