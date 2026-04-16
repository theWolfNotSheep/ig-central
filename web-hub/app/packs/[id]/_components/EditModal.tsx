"use client";

import { ReactNode } from "react";
import { Save, X } from "lucide-react";

export function EditModal({ title, onSave, onCancel, canSave = true, children }: {
    title: string;
    onSave: () => void;
    onCancel: () => void;
    canSave?: boolean;
    children: ReactNode;
}) {
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
            <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-lg bg-white shadow-xl">
                <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
                    <h2 className="text-base font-semibold text-gray-900">{title}</h2>
                    <button onClick={onCancel} className="rounded p-1 text-gray-400 hover:text-gray-600">
                        <X className="h-5 w-5" />
                    </button>
                </div>
                <div className="space-y-4 px-6 py-5">{children}</div>
                <div className="flex justify-end gap-2 border-t border-gray-200 px-6 py-3">
                    <button onClick={onCancel} className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
                        Cancel
                    </button>
                    <button onClick={onSave} disabled={!canSave}
                        className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60">
                        <Save className="h-4 w-4" /> Save
                    </button>
                </div>
            </div>
        </div>
    );
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
    return (
        <div>
            <label className="mb-1 block text-xs font-medium text-gray-600">
                {label}{hint && <span className="ml-2 font-normal text-gray-400">{hint}</span>}
            </label>
            {children}
        </div>
    );
}

export const inputCls = "w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500";
