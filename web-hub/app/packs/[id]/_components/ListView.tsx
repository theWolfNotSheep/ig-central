"use client";

import { ReactNode } from "react";
import { Plus, Pencil, Trash2 } from "lucide-react";

interface Column<T> {
    label: string;
    render: (item: T) => ReactNode;
    width?: string;
}

export function ListView<T>({ title, items, columns, onAdd, onEdit, onDelete, addLabel = "Add", emptyText }: {
    title: string;
    items: T[];
    columns: Column<T>[];
    onAdd: () => void;
    onEdit: (item: T, idx: number) => void;
    onDelete: (item: T, idx: number) => void;
    addLabel?: string;
    emptyText?: string;
}) {
    return (
        <div>
            <div className="mb-4 flex items-center justify-between">
                <h3 className="text-sm font-semibold text-gray-900">{title} <span className="text-gray-400 font-normal">({items.length})</span></h3>
                <button onClick={onAdd}
                    className="flex items-center gap-1.5 rounded-md bg-blue-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-700">
                    <Plus className="h-3.5 w-3.5" /> {addLabel}
                </button>
            </div>
            {items.length === 0 ? (
                <div className="rounded-lg border border-dashed border-gray-300 py-10 text-center">
                    <p className="text-sm text-gray-400">{emptyText ?? "No entries yet."}</p>
                </div>
            ) : (
                <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs text-gray-500">
                                {columns.map((c, i) => (
                                    <th key={i} className="px-3 py-2 font-medium" style={c.width ? { width: c.width } : undefined}>{c.label}</th>
                                ))}
                                <th className="px-3 py-2 font-medium text-right" style={{ width: "100px" }}>Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {items.map((item, idx) => (
                                <tr key={idx} className="hover:bg-gray-50">
                                    {columns.map((c, i) => (
                                        <td key={i} className="px-3 py-2 align-top">{c.render(item)}</td>
                                    ))}
                                    <td className="px-3 py-2 text-right">
                                        <button onClick={() => onEdit(item, idx)} title="Edit"
                                            className="rounded p-1 text-gray-400 hover:bg-gray-200 hover:text-gray-700">
                                            <Pencil className="h-3.5 w-3.5" />
                                        </button>
                                        <button onClick={() => onDelete(item, idx)} title="Delete"
                                            className="ml-1 rounded p-1 text-gray-400 hover:bg-red-100 hover:text-red-600">
                                            <Trash2 className="h-3.5 w-3.5" />
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
