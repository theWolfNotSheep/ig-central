"use client";

import { useState } from "react";
import { Clock, ChevronDown, ChevronUp } from "lucide-react";
import { PackVersion } from "../_lib/types";

export function VersionsTab({ versions }: { versions: PackVersion[] }) {
    const [expanded, setExpanded] = useState<string | null>(versions[0]?.id ?? null);

    if (versions.length === 0) {
        return (
            <div className="rounded-lg border border-dashed border-gray-300 py-10 text-center">
                <p className="text-sm text-gray-400">No versions published yet. Edit the other tabs and publish a version using the bar above.</p>
            </div>
        );
    }

    return (
        <div>
            <h3 className="mb-3 text-sm font-semibold text-gray-900">Version History <span className="text-gray-400 font-normal">({versions.length})</span></h3>
            <div className="space-y-2">
                {versions.map((v) => {
                    const isExpanded = expanded === v.id;
                    return (
                        <div key={v.id} className="rounded-lg border border-gray-200 bg-white">
                            <button onClick={() => setExpanded(isExpanded ? null : v.id)}
                                className="flex w-full items-center justify-between px-4 py-3 text-left">
                                <div className="flex items-center gap-3">
                                    <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-blue-50 text-sm font-semibold text-blue-700">v{v.versionNumber}</span>
                                    <div>
                                        <p className="text-sm font-medium text-gray-900">{v.changelog || "(no changelog)"}</p>
                                        <div className="flex items-center gap-3 text-xs text-gray-500">
                                            <span className="flex items-center gap-1"><Clock className="h-3 w-3" />{v.publishedAt ? new Date(v.publishedAt).toLocaleString() : "—"}</span>
                                            <span>by {v.publishedBy}</span>
                                            <span>{v.components.length} components</span>
                                        </div>
                                    </div>
                                </div>
                                {isExpanded ? <ChevronUp className="h-4 w-4 text-gray-400" /> : <ChevronDown className="h-4 w-4 text-gray-400" />}
                            </button>
                            {isExpanded && (
                                <div className="border-t border-gray-100 px-4 py-3">
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                                        {v.components.map((c, ci) => (
                                            <div key={ci} className="rounded bg-gray-50 p-2 text-xs">
                                                <span className="font-mono text-[10px] uppercase tracking-wider text-gray-500">{c.type.replace(/_/g, " ").toLowerCase()}</span>
                                                <p className="text-gray-700">{c.name} <span className="text-gray-400">({c.itemCount} items)</span></p>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
