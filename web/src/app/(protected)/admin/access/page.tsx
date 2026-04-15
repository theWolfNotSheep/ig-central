"use client";

import { useCallback, useEffect, useState } from "react";
import {
    Shield, Users, FileText, Loader2, CheckCircle, XCircle,
    AlertTriangle, Lock,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

type MatrixCategory = { id: string; name: string; parentId?: string };
type MatrixUser = {
    userId: string; email: string; firstName?: string; lastName?: string;
    clearanceLevel: number; roles: string[];
    categoryAccess: Record<string, string>;
};

type DocumentAccessEntry = {
    userId: string; email: string; firstName?: string; lastName?: string;
    roles: string[]; clearanceLevel: number; reasons: string[]; canAccess: boolean;
};

type CategoryAccessEntry = {
    userId: string; email: string; firstName?: string; lastName?: string;
    clearanceLevel: number; grantSource: string; operations: string[];
};

const CLEARANCE = ["PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"];
const CLEARANCE_COLORS = ["bg-green-100 text-green-700", "bg-blue-100 text-blue-700", "bg-amber-100 text-amber-700", "bg-red-100 text-red-700"];

const ACCESS_COLORS: Record<string, string> = {
    ADMIN: "bg-purple-500",
    GRANTED: "bg-green-500",
    CLEARANCE_BLOCKED: "bg-amber-400",
    NONE: "bg-gray-200",
};

export default function AccessAuditPage() {
    const [tab, setTab] = useState<"matrix" | "document" | "category">("matrix");
    const [categories, setCategories] = useState<MatrixCategory[]>([]);
    const [matrixUsers, setMatrixUsers] = useState<MatrixUser[]>([]);
    const [loading, setLoading] = useState(true);

    // Document/category lookup
    const [lookupId, setLookupId] = useState("");
    const [docAccess, setDocAccess] = useState<DocumentAccessEntry[] | null>(null);
    const [catAccess, setCatAccess] = useState<CategoryAccessEntry[] | null>(null);
    const [lookupLoading, setLookupLoading] = useState(false);

    const loadMatrix = useCallback(async () => {
        setLoading(true);
        try {
            const { data } = await api.get("/admin/access/matrix");
            setCategories(data.categories);
            setMatrixUsers(data.users);
        } catch { toast.error("Failed to load access matrix"); }
        finally { setLoading(false); }
    }, []);

    useEffect(() => { loadMatrix(); }, [loadMatrix]);

    const lookupDocAccess = async () => {
        if (!lookupId.trim()) return;
        setLookupLoading(true);
        try {
            const { data } = await api.get(`/admin/access/document/${lookupId.trim()}`);
            setDocAccess(data);
        } catch { toast.error("Document not found"); setDocAccess(null); }
        finally { setLookupLoading(false); }
    };

    const lookupCatAccess = async (catId: string) => {
        setLookupLoading(true);
        try {
            const { data } = await api.get(`/admin/access/category/${catId}`);
            setCatAccess(data);
            setLookupId(catId);
        } catch { toast.error("Failed"); setCatAccess(null); }
        finally { setLookupLoading(false); }
    };

    const rootCats = categories.filter(c => !c.parentId);

    return (
        <>
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">Access Audit</h2>
                    <p className="text-sm text-gray-500 mt-1">See who has access to what across the platform</p>
                </div>
            </div>

            {/* Tabs */}
            <div className="flex gap-1 mb-6 bg-gray-100 rounded-lg p-1 w-fit">
                {([
                    { key: "matrix", label: "Access Matrix", icon: Shield },
                    { key: "document", label: "Document Access", icon: FileText },
                    { key: "category", label: "Category Access", icon: Users },
                ] as const).map(t => (
                    <button key={t.key} onClick={() => setTab(t.key)}
                        className={`inline-flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-md transition-colors ${
                            tab === t.key ? "bg-white text-gray-900 shadow-sm" : "text-gray-600 hover:text-gray-900"
                        }`}>
                        <t.icon className="size-4" />{t.label}
                    </button>
                ))}
            </div>

            {/* Matrix tab */}
            {tab === "matrix" && (
                <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-auto">
                    {loading ? (
                        <div className="p-8 text-center"><Loader2 className="size-6 animate-spin text-gray-300 mx-auto" /></div>
                    ) : (
                        <>
                            {/* Legend */}
                            <div className="flex items-center gap-4 px-4 py-2 border-b border-gray-200 bg-gray-50 text-xs">
                                <span className="text-gray-500">Legend:</span>
                                <span className="flex items-center gap-1"><span className="size-3 rounded bg-purple-500" /> Admin</span>
                                <span className="flex items-center gap-1"><span className="size-3 rounded bg-green-500" /> Granted</span>
                                <span className="flex items-center gap-1"><span className="size-3 rounded bg-amber-400" /> Clearance blocked</span>
                                <span className="flex items-center gap-1"><span className="size-3 rounded bg-gray-200" /> No access</span>
                            </div>

                            <table className="text-xs">
                                <thead>
                                    <tr>
                                        <th className="sticky left-0 bg-white z-20 px-3 py-2 text-left font-medium text-gray-600 min-w-48 border-r border-gray-200">
                                            User
                                        </th>
                                        {rootCats.map(cat => (
                                            <th key={cat.id} className="px-2 py-2 font-medium text-gray-600 text-center min-w-20 whitespace-nowrap">
                                                <button onClick={() => { setTab("category"); lookupCatAccess(cat.id); }}
                                                    className="hover:text-blue-600">{cat.name}</button>
                                            </th>
                                        ))}
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {matrixUsers.map(user => (
                                        <tr key={user.userId}>
                                            <td className="sticky left-0 bg-white z-10 px-3 py-2 border-r border-gray-200">
                                                <div className="font-medium text-gray-900">
                                                    {user.firstName && user.lastName ? `${user.firstName} ${user.lastName}` : user.email}
                                                </div>
                                                <div className="text-[10px] text-gray-400 flex items-center gap-1">
                                                    <span className={`px-1 py-0.5 rounded ${CLEARANCE_COLORS[user.clearanceLevel]}`}>
                                                        {CLEARANCE[user.clearanceLevel]}
                                                    </span>
                                                    {user.roles.includes("ADMIN") && (
                                                        <span className="px-1 py-0.5 bg-purple-100 text-purple-700 rounded">Admin</span>
                                                    )}
                                                </div>
                                            </td>
                                            {rootCats.map(cat => {
                                                const access = user.categoryAccess[cat.id] ?? "NONE";
                                                return (
                                                    <td key={cat.id} className="px-2 py-2 text-center">
                                                        <div className={`size-5 rounded mx-auto ${ACCESS_COLORS[access] ?? "bg-gray-200"}`}
                                                            title={`${user.email} → ${cat.name}: ${access}`} />
                                                    </td>
                                                );
                                            })}
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </>
                    )}
                </div>
            )}

            {/* Document access tab */}
            {tab === "document" && (
                <div className="space-y-4">
                    <div className="flex gap-2">
                        <input value={lookupId} onChange={e => setLookupId(e.target.value)}
                            placeholder="Enter document ID..."
                            onKeyDown={e => e.key === "Enter" && lookupDocAccess()}
                            className="flex-1 text-sm border border-gray-300 rounded-lg px-3 py-2" />
                        <button onClick={lookupDocAccess} disabled={lookupLoading}
                            className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50">
                            {lookupLoading ? <Loader2 className="size-4 animate-spin" /> : "Check Access"}
                        </button>
                    </div>

                    {docAccess && (
                        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                            <div className="px-4 py-3 border-b border-gray-200 bg-gray-50">
                                <span className="text-sm font-medium text-gray-700">{docAccess.length} user(s) can access this document</span>
                            </div>
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b border-gray-200">
                                        <th className="text-left px-4 py-2 font-medium text-gray-600">User</th>
                                        <th className="text-left px-3 py-2 font-medium text-gray-600">Clearance</th>
                                        <th className="text-left px-3 py-2 font-medium text-gray-600">Reason</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {docAccess.map(entry => (
                                        <tr key={entry.userId}>
                                            <td className="px-4 py-2">
                                                <div className="font-medium text-gray-900">
                                                    {entry.firstName && entry.lastName ? `${entry.firstName} ${entry.lastName}` : entry.email}
                                                </div>
                                                <div className="text-xs text-gray-400">{entry.email}</div>
                                            </td>
                                            <td className="px-3 py-2">
                                                <span className={`px-1.5 py-0.5 text-[10px] font-medium rounded-full ${CLEARANCE_COLORS[entry.clearanceLevel]}`}>
                                                    {CLEARANCE[entry.clearanceLevel]}
                                                </span>
                                            </td>
                                            <td className="px-3 py-2">
                                                <div className="flex flex-wrap gap-1">
                                                    {entry.reasons.map((r, i) => (
                                                        <span key={i} className="px-1.5 py-0.5 text-[10px] bg-gray-100 text-gray-600 rounded">{r}</span>
                                                    ))}
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            )}

            {/* Category access tab */}
            {tab === "category" && (
                <div className="space-y-4">
                    <div className="flex flex-wrap gap-2">
                        {categories.filter(c => !c.parentId).map(cat => (
                            <button key={cat.id} onClick={() => lookupCatAccess(cat.id)}
                                className={`px-3 py-1.5 text-sm border rounded-lg ${
                                    lookupId === cat.id ? "bg-blue-50 text-blue-700 border-blue-200" : "text-gray-600 border-gray-300 hover:bg-gray-50"
                                }`}>
                                {cat.name}
                            </button>
                        ))}
                    </div>

                    {catAccess && (
                        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                            <div className="px-4 py-3 border-b border-gray-200 bg-gray-50">
                                <span className="text-sm font-medium text-gray-700">{catAccess.length} user(s) can access this category</span>
                            </div>
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b border-gray-200">
                                        <th className="text-left px-4 py-2 font-medium text-gray-600">User</th>
                                        <th className="text-left px-3 py-2 font-medium text-gray-600">Clearance</th>
                                        <th className="text-left px-3 py-2 font-medium text-gray-600">Grant Source</th>
                                        <th className="text-left px-3 py-2 font-medium text-gray-600">Operations</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {catAccess.map(entry => (
                                        <tr key={entry.userId}>
                                            <td className="px-4 py-2">
                                                <div className="font-medium text-gray-900">
                                                    {entry.firstName && entry.lastName ? `${entry.firstName} ${entry.lastName}` : entry.email}
                                                </div>
                                                <div className="text-xs text-gray-400">{entry.email}</div>
                                            </td>
                                            <td className="px-3 py-2">
                                                <span className={`px-1.5 py-0.5 text-[10px] font-medium rounded-full ${CLEARANCE_COLORS[entry.clearanceLevel]}`}>
                                                    {CLEARANCE[entry.clearanceLevel]}
                                                </span>
                                            </td>
                                            <td className="px-3 py-2 text-xs text-gray-600">{entry.grantSource}</td>
                                            <td className="px-3 py-2">
                                                <div className="flex gap-1">
                                                    {entry.operations.map(op => (
                                                        <span key={op} className="px-1.5 py-0.5 text-[10px] bg-gray-100 text-gray-600 rounded">{op}</span>
                                                    ))}
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            )}
        </>
    );
}
