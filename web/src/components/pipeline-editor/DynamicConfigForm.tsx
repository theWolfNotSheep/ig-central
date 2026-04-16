"use client";

import { useCallback, useState } from "react";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

type PropertySchema = {
    type?: string;
    enum?: string[];
    minimum?: number;
    maximum?: number;
    default?: unknown;
    "ui:widget"?: string;
    "ui:placeholder"?: string;
    "ui:help"?: string;
    "ui:step"?: number;
    "ui:group"?: string;
};

type ConfigSchema = {
    properties?: Record<string, PropertySchema>;
};

type Props = {
    schema: ConfigSchema | undefined;
    config: Record<string, string>;
    onConfigChange: (key: string, value: string) => void;
    /** Registry of custom widget components keyed by widget name (e.g. "rulesEditor") */
    customWidgets?: Record<string, React.ComponentType<CustomWidgetProps>>;
};

export type CustomWidgetProps = {
    config: Record<string, string>;
    onConfigChange: (key: string, value: string) => void;
};

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

function toLabel(key: string): string {
    // camelCase → Title Case
    return key
        .replace(/([A-Z])/g, " $1")
        .replace(/^./, s => s.toUpperCase())
        .trim();
}

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

/**
 * Renders a dynamic config form from a JSON Schema-like configSchema.
 * Supports: text, number, range, select, checkbox, readonly, textarea,
 * and custom:* escape hatches for complex widgets.
 */
export default function DynamicConfigForm({ schema, config, onConfigChange, customWidgets }: Props) {
    if (!schema?.properties || Object.keys(schema.properties).length === 0) {
        return <p className="text-[10px] text-gray-400">No configuration options.</p>;
    }

    // Group fields by ui:group, preserving insertion order. Ungrouped fields go into "" (rendered first).
    const groups: Record<string, [string, PropertySchema][]> = {};
    const groupOrder: string[] = [];
    for (const [key, prop] of Object.entries(schema.properties)) {
        const group = prop["ui:group"] ?? "";
        if (!groups[group]) {
            groups[group] = [];
            groupOrder.push(group);
        }
        groups[group].push([key, prop]);
    }

    return (
        <div className="space-y-4">
            {groupOrder.map(groupName => (
                <div key={groupName} className={groupName ? "rounded-md border border-gray-200 bg-gray-50/50 p-3" : ""}>
                    {groupName && (
                        <h5 className="text-[10px] font-semibold uppercase tracking-wider text-gray-500 mb-2">
                            {groupName}
                        </h5>
                    )}
                    <div className="space-y-3">
                        {groups[groupName].map(([key, prop]) => (
                            <DynamicField
                                key={key}
                                fieldKey={key}
                                prop={prop}
                                value={config[key] ?? ""}
                                onChange={onConfigChange}
                                config={config}
                                customWidgets={customWidgets}
                            />
                        ))}
                    </div>
                </div>
            ))}
        </div>
    );
}

/* ------------------------------------------------------------------ */
/*  Field renderer                                                     */
/* ------------------------------------------------------------------ */

function DynamicField({ fieldKey, prop, value, onChange, config, customWidgets }: {
    fieldKey: string;
    prop: PropertySchema;
    value: string;
    onChange: (key: string, value: string) => void;
    config: Record<string, string>;
    customWidgets?: Record<string, React.ComponentType<CustomWidgetProps>>;
}) {
    const widget = prop["ui:widget"] ?? inferWidget(prop);
    const label = toLabel(fieldKey);
    const placeholder = prop["ui:placeholder"] ?? "";
    const help = prop["ui:help"];
    const id = `cfg-${fieldKey}`;

    const handleChange = useCallback((v: string) => onChange(fieldKey, v), [fieldKey, onChange]);

    // Custom widget escape hatch
    if (widget.startsWith("custom:")) {
        const widgetName = widget.slice(7); // strip "custom:"
        const CustomWidget = customWidgets?.[widgetName];
        if (CustomWidget) {
            return <CustomWidget config={config} onConfigChange={onChange} />;
        }
        return <p className="text-[10px] text-red-400">Unknown custom widget: {widgetName}</p>;
    }

    switch (widget) {
        case "range":
            return (
                <div>
                    <label htmlFor={id} className="text-[10px] font-medium text-gray-500 block mb-1">{label}</label>
                    <input
                        id={id}
                        type="range"
                        min={prop.minimum ?? 0}
                        max={prop.maximum ?? 1}
                        step={prop["ui:step"] ?? 0.05}
                        value={value || String(prop.default ?? prop.minimum ?? 0)}
                        onChange={e => handleChange(e.target.value)}
                        className="w-full"
                    />
                    <div className="flex justify-between text-[10px] text-gray-400 mt-0.5">
                        <span>{prop.minimum ?? 0}</span>
                        <span className="font-semibold text-gray-600">{value || String(prop.default ?? "")}</span>
                        <span>{prop.maximum ?? 1}</span>
                    </div>
                    {help && <p className="text-[10px] text-gray-400 mt-0.5">{help}</p>}
                </div>
            );

        case "select":
            return (
                <div>
                    <label htmlFor={id} className="text-[10px] font-medium text-gray-500 block mb-1">{label}</label>
                    <select
                        id={id}
                        value={value || String(prop.default ?? "")}
                        onChange={e => handleChange(e.target.value)}
                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5"
                    >
                        {placeholder && <option value="">{placeholder}</option>}
                        {(prop.enum ?? []).map(opt => (
                            <option key={opt} value={opt}>{opt}</option>
                        ))}
                    </select>
                    {help && <p className="text-[10px] text-gray-400 mt-0.5">{help}</p>}
                </div>
            );

        case "checkbox":
            return (
                <label className="flex items-center gap-2 text-sm text-gray-700">
                    <input
                        type="checkbox"
                        checked={value ? value === "true" : prop.default === true}
                        onChange={e => handleChange(e.target.checked ? "true" : "false")}
                        className="rounded border-gray-300"
                    />
                    <span>{label}</span>
                    {help && <span className="text-[10px] text-gray-400 ml-1">({help})</span>}
                </label>
            );

        case "readonly":
            return (
                <div>
                    <label className="text-[10px] font-medium text-gray-500 block mb-1">{label}</label>
                    <input
                        value={value || placeholder}
                        readOnly
                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5 bg-gray-50 text-gray-500"
                    />
                    {help && <p className="text-[10px] text-gray-400 mt-0.5">{help}</p>}
                </div>
            );

        case "textarea":
            return (
                <div>
                    <label htmlFor={id} className="text-[10px] font-medium text-gray-500 block mb-1">{label}</label>
                    <textarea
                        id={id}
                        value={value}
                        onChange={e => handleChange(e.target.value)}
                        placeholder={placeholder}
                        rows={3}
                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5"
                    />
                    {help && <p className="text-[10px] text-gray-400 mt-0.5">{help}</p>}
                </div>
            );

        case "password":
            return <PasswordField id={id} label={label} value={value} onChange={handleChange} placeholder={placeholder} help={help} />;

        case "number":
            return (
                <div>
                    <label htmlFor={id} className="text-[10px] font-medium text-gray-500 block mb-1">{label}</label>
                    <input
                        id={id}
                        type="number"
                        min={prop.minimum}
                        max={prop.maximum}
                        step={prop["ui:step"]}
                        value={value}
                        onChange={e => handleChange(e.target.value)}
                        placeholder={placeholder || (prop.default != null ? String(prop.default) : "")}
                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5"
                    />
                    {help && <p className="text-[10px] text-gray-400 mt-0.5">{help}</p>}
                </div>
            );

        // Default: text input
        default:
            return (
                <div>
                    <label htmlFor={id} className="text-[10px] font-medium text-gray-500 block mb-1">{label}</label>
                    <input
                        id={id}
                        value={value}
                        onChange={e => handleChange(e.target.value)}
                        placeholder={placeholder}
                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5"
                    />
                    {help && <p className="text-[10px] text-gray-400 mt-0.5">{help}</p>}
                </div>
            );
    }
}

/* ------------------------------------------------------------------ */
/*  Widget inference                                                   */
/* ------------------------------------------------------------------ */

function inferWidget(prop: PropertySchema): string {
    if (prop["ui:widget"]) return prop["ui:widget"];
    if (prop.enum && prop.enum.length > 0) return "select";
    if (prop.type === "boolean") return "checkbox";
    if (prop.type === "integer" || prop.type === "number") return "number";
    return "text";
}

/* ------------------------------------------------------------------ */
/*  Password field with show/hide toggle                               */
/* ------------------------------------------------------------------ */

function PasswordField({ id, label, value, onChange, placeholder, help }: {
    id: string; label: string; value: string; onChange: (v: string) => void;
    placeholder?: string; help?: string;
}) {
    const [visible, setVisible] = useState(false);
    return (
        <div>
            <label htmlFor={id} className="text-[10px] font-medium text-gray-500 block mb-1">{label}</label>
            <div className="relative">
                <input
                    id={id}
                    type={visible ? "text" : "password"}
                    value={value}
                    onChange={e => onChange(e.target.value)}
                    placeholder={placeholder}
                    className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5 pr-8"
                />
                <button
                    type="button"
                    onClick={() => setVisible(!visible)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-[10px] text-gray-400 hover:text-gray-600"
                    aria-label={visible ? "Hide" : "Show"}
                >
                    {visible ? "Hide" : "Show"}
                </button>
            </div>
            {help && <p className="text-[10px] text-gray-400 mt-0.5">{help}</p>}
        </div>
    );
}
