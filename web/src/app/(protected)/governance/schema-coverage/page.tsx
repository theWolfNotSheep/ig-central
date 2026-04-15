"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
    ArrowLeft, Database, Loader2, Plus, CheckCircle, AlertTriangle, XCircle,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import ResizableTh from "@/components/resizable-th";

type CoverageRow = {
    categoryId: string;
    categoryName: string;
    schemaId?: string;
    schemaName?: string;
    fieldCount: number;
    documentCount: number;
    withMetadata: number;
    missingMetadata: number;
    coveragePercent: number;
};

export default function SchemaCoveragePage() {
    const [rows, setRows] = useState<CoverageRow[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        api.get("/admin/governance/metadata-schemas/coverage")
            .then(({ data }) => setRows(Array.isArray(data) ? data : []))
            .catch(() => toast.error("Failed to load schema coverage"))
            .finally(() => setLoading(false));
    }, []);

    const coverageColor = (row: CoverageRow) => {
        if (!row.schemaId) return "bg-red-50 text-red-700";
        if (row.coveragePercent >= 80) return "bg-green-50 text-green-700";
        if (row.coveragePercent >= 50) return "bg-amber-50 text-amber-700";
        return "bg-red-50 text-red-700";
    };

    const coverageIcon = (row: CoverageRow) => {
        if (!row.schemaId) return <XCircle className="size-4 text-red-400" />;
        if (row.coveragePercent >= 80) return <CheckCircle className="size-4 text-green-500" />;
        if (row.coveragePercent >= 50) return <AlertTriangle className="size-4 text-amber-500" />;
        return <AlertTriangle className="size-4 text-red-400" />;
    };

    return (
        <>
            <div className="flex items-center justify-between mb-6">
                <div className="flex items-center gap-3">
                    <Link href="/governance" className="p-1.5 rounded-md hover:bg-gray-100 transition-colors">
                        <ArrowLeft className="size-5 text-gray-500" />
                    </Link>
                    <div>
                        <h2 className="text-xl font-bold text-gray-900">Schema Coverage</h2>
                        <p className="text-sm text-gray-500 mt-0.5">Metadata schema coverage across taxonomy categories</p>
                    </div>
                </div>
                <Link href="/governance#metadata-schemas"
                    className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors">
                    <Database className="size-4" /> Manage Schemas
                </Link>
            </div>

            {loading ? (
                <div className="flex items-center justify-center py-16">
                    <Loader2 className="size-6 animate-spin text-gray-400" />
                </div>
            ) : rows.length === 0 ? (
                <div className="text-center py-16">
                    <Database className="size-8 text-gray-300 mx-auto mb-3" />
                    <p className="text-sm text-gray-500">No taxonomy categories found.</p>
                    <p className="text-xs text-gray-400 mt-1">Create categories in the Governance taxonomy tab first.</p>
                </div>
            ) : (
                <div className="border border-gray-200 rounded-lg overflow-hidden">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="bg-gray-50 text-left border-b border-gray-200">
                                <ResizableTh className="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Category</ResizableTh>
                                <ResizableTh className="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Schema</ResizableTh>
                                <ResizableTh className="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider text-center">Fields</ResizableTh>
                                <ResizableTh className="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider text-center">Documents</ResizableTh>
                                <ResizableTh className="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider text-center">With Metadata</ResizableTh>
                                <ResizableTh className="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider text-center">Missing</ResizableTh>
                                <ResizableTh className="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider text-center">Coverage</ResizableTh>
                                <th className="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider"></th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {rows.map((row) => (
                                <tr key={row.categoryId} className={`hover:bg-gray-50 transition-colors ${!row.schemaId ? "bg-amber-50/30" : ""}`}>
                                    <td className="px-4 py-3">
                                        <span className="text-sm font-medium text-gray-900">{row.categoryName}</span>
                                    </td>
                                    <td className="px-4 py-3">
                                        {row.schemaName ? (
                                            <span className="text-sm text-gray-700">{row.schemaName}</span>
                                        ) : (
                                            <span className="text-xs text-red-500 italic">No schema</span>
                                        )}
                                    </td>
                                    <td className="px-4 py-3 text-center text-sm text-gray-600">{row.fieldCount || "—"}</td>
                                    <td className="px-4 py-3 text-center text-sm text-gray-600">{row.documentCount}</td>
                                    <td className="px-4 py-3 text-center text-sm text-gray-600">{row.withMetadata}</td>
                                    <td className="px-4 py-3 text-center text-sm text-gray-600">{row.missingMetadata}</td>
                                    <td className="px-4 py-3">
                                        <div className="flex items-center justify-center gap-2">
                                            {coverageIcon(row)}
                                            {row.schemaId ? (
                                                <div className="flex items-center gap-2 min-w-[80px]">
                                                    <div className="flex-1 h-1.5 bg-gray-100 rounded-full overflow-hidden">
                                                        <div
                                                            className={`h-full rounded-full ${
                                                                row.coveragePercent >= 80 ? "bg-green-500" :
                                                                row.coveragePercent >= 50 ? "bg-amber-500" : "bg-red-500"
                                                            }`}
                                                            style={{ width: `${row.coveragePercent}%` }}
                                                        />
                                                    </div>
                                                    <span className={`text-xs font-medium ${coverageColor(row).split(" ")[1]}`}>
                                                        {row.coveragePercent}%
                                                    </span>
                                                </div>
                                            ) : (
                                                <span className="text-xs text-red-500 font-medium">—</span>
                                            )}
                                        </div>
                                    </td>
                                    <td className="px-4 py-3 text-right">
                                        {!row.schemaId && (
                                            <Link href="/governance#metadata-schemas"
                                                className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-blue-600 border border-blue-200 rounded-md hover:bg-blue-50 transition-colors">
                                                <Plus className="size-3" /> Create Schema
                                            </Link>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </>
    );
}
