"use client";

import { AlertCircle, Loader2, Save, Download } from "lucide-react";

export function PublishBar({ dirty, nextVersion, changelog, onChangelog, onPublish, onExport, publishing, error }: {
    dirty: boolean;
    nextVersion: number;
    changelog: string;
    onChangelog: (v: string) => void;
    onPublish: () => void;
    onExport: () => void;
    publishing: boolean;
    error?: string;
}) {
    return (
        <div className={`mb-4 rounded-lg border p-3 ${dirty ? "border-amber-300 bg-amber-50" : "border-gray-200 bg-white"}`}>
            <div className="flex items-center gap-3">
                {dirty ? (
                    <span className="flex items-center gap-1.5 text-xs font-medium text-amber-800">
                        <AlertCircle className="h-3.5 w-3.5" /> Unsaved changes
                    </span>
                ) : (
                    <span className="text-xs font-medium text-gray-500">No changes</span>
                )}
                <input type="text" placeholder="Changelog (required to publish)..."
                    value={changelog} onChange={(e) => onChangelog(e.target.value)}
                    className="flex-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm" />
                <button onClick={onExport} title="Export current state as JSON"
                    className="flex items-center gap-1.5 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50">
                    <Download className="h-3.5 w-3.5" /> Export
                </button>
                <button onClick={onPublish} disabled={!dirty || !changelog.trim() || publishing}
                    className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60">
                    {publishing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                    Publish v{nextVersion}
                </button>
            </div>
            {error && <p className="mt-2 text-xs text-red-700">{error}</p>}
        </div>
    );
}
