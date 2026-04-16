"use client";

import { inputCls } from "./EditModal";

export interface RefOption { value: string; label: string }

export function SingleRefSelect({ value, options, onChange, placeholder = "(none)" }: {
    value: string | undefined;
    options: RefOption[];
    onChange: (v: string | undefined) => void;
    placeholder?: string;
}) {
    return (
        <select value={value ?? ""} onChange={(e) => onChange(e.target.value || undefined)} className={inputCls}>
            <option value="">{placeholder}</option>
            {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
    );
}

export function MultiRefSelect({ values, options, onChange }: {
    values: string[];
    options: RefOption[];
    onChange: (next: string[]) => void;
}) {
    const toggle = (v: string) => {
        if (values.includes(v)) onChange(values.filter((x) => x !== v));
        else onChange([...values, v]);
    };
    if (options.length === 0) {
        return <p className="text-xs text-gray-400 italic">No options available — add entries on the related tab first.</p>;
    }
    return (
        <div className="flex flex-wrap gap-1.5">
            {options.map((o) => {
                const active = values.includes(o.value);
                return (
                    <button key={o.value} type="button" onClick={() => toggle(o.value)}
                        className={`rounded-full border px-2.5 py-0.5 text-xs font-medium ${
                            active ? "bg-blue-600 border-blue-600 text-white" : "bg-white border-gray-300 text-gray-600 hover:border-blue-400"
                        }`}>
                        {o.label}
                    </button>
                );
            })}
            {values.filter((v) => !options.find((o) => o.value === v)).map((v) => (
                <span key={v} className="rounded-full border border-red-300 bg-red-50 px-2.5 py-0.5 text-xs text-red-700" title="Reference doesn't resolve in this pack">
                    {v} ⚠
                </span>
            ))}
        </div>
    );
}

/** Comma-separated string list with chips (for keywords, indicators, etc.). */
export function ChipInput({ values, onChange, placeholder }: {
    values: string[];
    onChange: (next: string[]) => void;
    placeholder?: string;
}) {
    return (
        <input type="text" value={values.join(", ")}
            onChange={(e) => onChange(e.target.value.split(",").map((s) => s.trim()).filter(Boolean))}
            placeholder={placeholder} className={inputCls} />
    );
}
