"use client";

import { useCallback, useEffect, useState, use } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Shield, Check, Loader2 } from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

type UserDetail = {
    id: string;
    email: string;
    firstName?: string;
    lastName?: string;
    displayName?: string;
    avatarUrl?: string;
    department?: string;
    jobTitle?: string;
    roles: string[];
    accountType?: string;
    enabled: boolean;
    sensitivityClearanceLevel: number;
    signUpMethod?: string;
    identityProvider?: string;
    createdDate?: string;
    permissions: string[];
    accountNonLocked: boolean;
    lastLoginAt?: string;
};

type RoleDef = {
    id: string;
    key: string;
    name: string;
    description?: string;
    featureIds: string[];
    adminRole: boolean;
    defaultForNewUsers: boolean;
    defaultSensitivityClearance: number;
    systemProtected: boolean;
};

type FeatureDef = {
    id: string;
    permissionKey: string;
    name: string;
    description?: string;
    category: string;
};

type TaxCategory = {
    id: string;
    classificationCode?: string;
    name: string;
    parentId?: string;
    level?: "FUNCTION" | "ACTIVITY" | "TRANSACTION";
    defaultSensitivity?: string;
    jurisdiction?: string;
};

type TaxGrant = {
    id: string;
    userId: string;
    categoryId: string;
    includeChildren: boolean;
    operations: string[];
    grantedBy: string;
    grantedAt: string;
    reason?: string;
};

const SENSITIVITY_LABELS = ["PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"];
const SENSITIVITY_COLORS = ["bg-green-100 text-green-700", "bg-blue-100 text-blue-700", "bg-amber-100 text-amber-700", "bg-red-100 text-red-700"];
const ALL_OPS = ["READ", "CREATE", "UPDATE", "DELETE"];

export default function UserProfilePage({ params }: { params: Promise<{ id: string }> }) {
    const { id } = use(params);
    const router = useRouter();

    const [user, setUser] = useState<UserDetail | null>(null);
    const [roles, setRoles] = useState<RoleDef[]>([]);
    const [features, setFeatures] = useState<FeatureDef[]>([]);
    const [loading, setLoading] = useState(true);
    const [editingRoles, setEditingRoles] = useState(false);
    const [selectedRoles, setSelectedRoles] = useState<Set<string>>(new Set());
    const [savingRoles, setSavingRoles] = useState(false);
    const [editingClearance, setEditingClearance] = useState(false);
    const [clearance, setClearance] = useState(0);
    const [showResetPassword, setShowResetPassword] = useState(false);
    const [newPassword, setNewPassword] = useState("");
    const [resettingPassword, setResettingPassword] = useState(false);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const [u, r, f] = await Promise.all([
                api.get(`/admin/users/${id}`),
                api.get("/admin/users/roles"),
                api.get("/admin/users/features"),
            ]);
            setUser(u.data);
            setRoles(r.data);
            setFeatures(f.data);
            setSelectedRoles(new Set(u.data.roles));
            setClearance(u.data.sensitivityClearanceLevel);
        } catch {
            toast.error("Failed to load user");
        } finally {
            setLoading(false);
        }
    }, [id]);

    useEffect(() => { load(); }, [load]);

    if (loading) {
        return <div className="flex items-center justify-center py-12"><Loader2 className="size-6 animate-spin text-gray-400" /></div>;
    }
    if (!user) {
        return <div className="p-8 text-sm text-gray-500">User not found.</div>;
    }

    const isOAuthUser = user.identityProvider === "GOOGLE" || user.identityProvider === "GITHUB" || user.identityProvider === "LINKEDIN";

    const toggleEnabled = async () => {
        try {
            await api.put(`/admin/users/${user.id}/status`, { enabled: !user.enabled });
            toast.success(user.enabled ? "User disabled" : "User enabled");
            load();
        } catch { toast.error("Failed"); }
    };

    const toggleRole = (key: string) => {
        const next = new Set(selectedRoles);
        if (next.has(key)) next.delete(key); else next.add(key);
        setSelectedRoles(next);
    };

    const saveRoles = async () => {
        setSavingRoles(true);
        try {
            await api.put(`/admin/users/${user.id}/roles`, { roleKeys: [...selectedRoles] });
            toast.success("Roles updated");
            setEditingRoles(false);
            load();
        } catch { toast.error("Failed to update roles"); }
        finally { setSavingRoles(false); }
    };

    const saveClearance = async () => {
        try {
            await api.put(`/admin/users/${user.id}/clearance`, { level: clearance });
            toast.success("Clearance updated");
            setEditingClearance(false);
            load();
        } catch { toast.error("Failed"); }
    };

    const resetPassword = async () => {
        if (!newPassword || newPassword.length < 8) { toast.error("Password must be at least 8 characters"); return; }
        setResettingPassword(true);
        try {
            await api.post(`/admin/users/${user.id}/reset-password`, { password: newPassword });
            toast.success("Password reset");
            setShowResetPassword(false);
            setNewPassword("");
        } catch { toast.error("Failed to reset password"); }
        finally { setResettingPassword(false); }
    };

    const permsByCategory: Record<string, FeatureDef[]> = {};
    for (const f of features) {
        if (user.permissions.includes(f.permissionKey)) {
            (permsByCategory[f.category] ??= []).push(f);
        }
    }

    return (
        <>
            <div className="mb-4">
                <button onClick={() => router.push("/admin/users")}
                    className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
                    <ArrowLeft className="size-4" /> Back to Users
                </button>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
                {/* Left column: profile, roles, clearance */}
                <div className="space-y-4">
                    {/* Profile */}
                    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
                        <div className="flex items-center justify-between mb-3">
                            <div className="flex items-center gap-3 min-w-0">
                                {user.avatarUrl ? (
                                    <img src={user.avatarUrl} alt="" className="size-10 rounded-full border border-gray-200 shrink-0" referrerPolicy="no-referrer" />
                                ) : (
                                    <div className="size-10 rounded-full bg-gray-100 flex items-center justify-center shrink-0">
                                        <span className="text-sm font-medium text-gray-400">
                                            {(user.firstName?.[0] ?? user.email[0]).toUpperCase()}
                                        </span>
                                    </div>
                                )}
                                <h3 className="font-semibold text-gray-900 truncate">
                                    {user.firstName && user.lastName ? `${user.firstName} ${user.lastName}` : user.email}
                                </h3>
                            </div>
                            <button onClick={toggleEnabled}
                                className={`text-xs px-2 py-1 rounded-md border ${user.enabled
                                    ? "border-red-200 text-red-600 hover:bg-red-50"
                                    : "border-green-200 text-green-600 hover:bg-green-50"}`}>
                                {user.enabled ? "Disable" : "Enable"}
                            </button>
                        </div>
                        <div className="space-y-1.5 text-sm">
                            <div className="text-gray-500 break-all">{user.email}</div>
                            {user.department && <div className="text-gray-400 text-xs">{user.department}</div>}
                            {user.jobTitle && <div className="text-gray-400 text-xs">{user.jobTitle}</div>}
                            <div className="flex items-center gap-2 text-xs text-gray-400">
                                <span>Login method:</span>
                                {user.identityProvider === "GOOGLE" ? (
                                    <span className="px-1.5 py-0.5 bg-blue-50 text-blue-700 rounded text-[10px] font-medium">Google</span>
                                ) : (
                                    <span className="px-1.5 py-0.5 bg-gray-100 text-gray-600 rounded text-[10px] font-medium">
                                        {user.signUpMethod === "ADMIN_CREATED" ? "Admin Created" : "Email / Password"}
                                    </span>
                                )}
                            </div>
                            <div className="text-xs text-gray-400">
                                Created: {user.createdDate ? new Date(user.createdDate).toLocaleDateString() : "—"}
                            </div>
                            {user.lastLoginAt && (
                                <div className="text-xs text-gray-400">Last login: {new Date(user.lastLoginAt).toLocaleString()}</div>
                            )}
                        </div>
                        {!isOAuthUser && (
                            <div className="mt-3 pt-3 border-t border-gray-100">
                                {!showResetPassword ? (
                                    <button onClick={() => setShowResetPassword(true)} className="text-xs text-blue-600 hover:text-blue-800 font-medium">
                                        Reset Password
                                    </button>
                                ) : (
                                    <div className="space-y-2">
                                        <label className="block text-xs font-medium text-gray-600">New Password</label>
                                        <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)}
                                            placeholder="Min 8 characters"
                                            className="w-full rounded-md border border-gray-300 px-2.5 py-1.5 text-sm" />
                                        <div className="flex gap-1.5">
                                            <button onClick={resetPassword} disabled={resettingPassword || newPassword.length < 8}
                                                className="text-xs font-medium text-white bg-blue-600 hover:bg-blue-700 px-3 py-1 rounded-md disabled:opacity-50">
                                                {resettingPassword ? "..." : "Reset"}
                                            </button>
                                            <button onClick={() => { setShowResetPassword(false); setNewPassword(""); }}
                                                className="text-xs text-gray-400 hover:text-gray-600 px-2 py-1">Cancel</button>
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}
                    </div>

                    {/* Roles */}
                    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
                        <div className="flex items-center justify-between mb-3">
                            <h4 className="text-sm font-semibold text-gray-900">Roles</h4>
                            {!editingRoles ? (
                                <button onClick={() => setEditingRoles(true)} className="text-xs text-blue-600 hover:text-blue-800">Edit</button>
                            ) : (
                                <div className="flex gap-1">
                                    <button onClick={saveRoles} disabled={savingRoles} className="text-xs text-blue-600 hover:text-blue-800 font-medium">
                                        {savingRoles ? "..." : "Save"}
                                    </button>
                                    <button onClick={() => { setEditingRoles(false); setSelectedRoles(new Set(user.roles)); }}
                                        className="text-xs text-gray-400 hover:text-gray-600">Cancel</button>
                                </div>
                            )}
                        </div>
                        <div className="space-y-1.5">
                            {roles.map(role => {
                                const active = editingRoles ? selectedRoles.has(role.key) : user.roles.includes(role.key);
                                return (
                                    <button key={role.key} onClick={() => editingRoles && toggleRole(role.key)} disabled={!editingRoles}
                                        className={`w-full text-left px-3 py-2 rounded-md text-sm ${
                                            active
                                                ? "bg-blue-50 text-blue-700 border border-blue-200"
                                                : editingRoles
                                                    ? "bg-gray-50 text-gray-500 border border-gray-200 hover:bg-gray-100"
                                                    : "bg-gray-50 text-gray-400 border border-transparent"
                                        }`}>
                                        <div className="flex items-center justify-between">
                                            <div>
                                                <span className="font-medium">{role.name}</span>
                                                {role.adminRole && <Shield className="inline size-3 text-red-500 ml-1" />}
                                            </div>
                                            {active && <Check className="size-4" />}
                                        </div>
                                        {role.description && <div className="text-[10px] opacity-70 mt-0.5">{role.description}</div>}
                                    </button>
                                );
                            })}
                        </div>
                    </div>

                    {/* Clearance */}
                    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
                        <div className="flex items-center justify-between mb-3">
                            <h4 className="text-sm font-semibold text-gray-900">Sensitivity Clearance</h4>
                            {!editingClearance ? (
                                <button onClick={() => setEditingClearance(true)} className="text-xs text-blue-600 hover:text-blue-800">Edit</button>
                            ) : (
                                <div className="flex gap-1">
                                    <button onClick={saveClearance} className="text-xs text-blue-600 hover:text-blue-800 font-medium">Save</button>
                                    <button onClick={() => { setEditingClearance(false); setClearance(user.sensitivityClearanceLevel); }}
                                        className="text-xs text-gray-400 hover:text-gray-600">Cancel</button>
                                </div>
                            )}
                        </div>
                        <div className="flex gap-1">
                            {SENSITIVITY_LABELS.map((label, i) => (
                                <button key={label} onClick={() => editingClearance && setClearance(i)} disabled={!editingClearance}
                                    className={`flex-1 py-2 text-xs font-medium rounded-md ${
                                        (editingClearance ? clearance : user.sensitivityClearanceLevel) >= i
                                            ? SENSITIVITY_COLORS[i]
                                            : "bg-gray-100 text-gray-400"
                                    }`}>
                                    {label}
                                </button>
                            ))}
                        </div>
                        <p className="text-[10px] text-gray-400 mt-2">User can view documents at or below this sensitivity level</p>
                    </div>

                    {/* Effective Permissions */}
                    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
                        <h4 className="text-sm font-semibold text-gray-900 mb-3">
                            Effective Permissions ({user.permissions.length})
                        </h4>
                        <div className="space-y-2 max-h-48 overflow-y-auto">
                            {Object.entries(permsByCategory).map(([cat, perms]) => (
                                <div key={cat}>
                                    <div className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1">{cat}</div>
                                    <div className="flex flex-wrap gap-1">
                                        {perms.map(p => (
                                            <span key={p.permissionKey} className="px-1.5 py-0.5 text-[10px] bg-gray-100 text-gray-600 rounded" title={p.description}>
                                                {p.permissionKey}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            ))}
                            {user.permissions.length === 0 && (
                                <p className="text-xs text-gray-400 italic">No permissions assigned</p>
                            )}
                        </div>
                    </div>
                </div>

                {/* Right column: taxonomy access matrix (2 columns wide on large screens) */}
                <div className="lg:col-span-2">
                    <TaxonomyAccessMatrix userId={user.id} />
                </div>
            </div>
        </>
    );
}

/* ── Taxonomy Access Matrix ────────────────────────── */

function TaxonomyAccessMatrix({ userId }: { userId: string }) {
    const [categories, setCategories] = useState<TaxCategory[]>([]);
    const [grants, setGrants] = useState<TaxGrant[]>([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    const [busy, setBusy] = useState<Set<string>>(new Set());

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const [g, c] = await Promise.all([
                api.get(`/admin/users/${userId}/taxonomy-grants`),
                api.get("/admin/governance/taxonomy"),
            ]);
            setGrants(g.data);
            setCategories(c.data);
        } catch {
            toast.error("Failed to load taxonomy access");
        } finally {
            setLoading(false);
        }
    }, [userId]);

    useEffect(() => { load(); }, [load]);

    const grantByCategory = new Map<string, TaxGrant>();
    for (const g of grants) grantByCategory.set(g.categoryId, g);

    const filtered = categories.filter(c => {
        if (c.level === "FUNCTION") return false; // Skip functions; apply grants to activities/transactions
        if (!search) return true;
        const q = search.toLowerCase();
        return c.name.toLowerCase().includes(q) ||
            c.classificationCode?.toLowerCase().includes(q);
    });

    // Group by level
    const activityCats = filtered.filter(c => c.level === "ACTIVITY");
    const recordCats = filtered.filter(c => c.level === "TRANSACTION");

    const setBusyFor = (catId: string, isBusy: boolean) => {
        const next = new Set(busy);
        isBusy ? next.add(catId) : next.delete(catId);
        setBusy(next);
    };

    const applyOps = async (cat: TaxCategory, nextOps: Set<string>, includeChildren = true) => {
        setBusyFor(cat.id, true);
        try {
            const existing = grantByCategory.get(cat.id);
            if (nextOps.size === 0) {
                // No operations selected → revoke if exists
                if (existing) {
                    await api.delete(`/admin/users/${userId}/taxonomy-grants/${existing.id}`);
                }
            } else if (existing) {
                // Update existing grant
                await api.put(`/admin/users/${userId}/taxonomy-grants/${existing.id}`, {
                    operations: [...nextOps],
                    includeChildren: existing.includeChildren,
                });
            } else {
                // Create new grant
                await api.post(`/admin/users/${userId}/taxonomy-grants`, {
                    categoryId: cat.id, includeChildren,
                    operations: [...nextOps], reason: "Admin granted via matrix",
                });
            }
            load();
        } catch {
            toast.error("Failed to update access");
        } finally {
            setBusyFor(cat.id, false);
        }
    };

    const toggleOp = (cat: TaxCategory, op: string) => {
        const existing = grantByCategory.get(cat.id);
        const ops = new Set(existing?.operations ?? []);
        if (ops.has(op)) ops.delete(op);
        else {
            ops.add(op);
            // Granting any write op implies READ
            if (op !== "READ") ops.add("READ");
        }
        applyOps(cat, ops);
    };

    const toggleAll = (cat: TaxCategory) => {
        const existing = grantByCategory.get(cat.id);
        const hasAll = existing && ALL_OPS.every(op => existing.operations.includes(op));
        applyOps(cat, hasAll ? new Set() : new Set(ALL_OPS));
    };

    const toggleIncludeChildren = async (cat: TaxCategory) => {
        const existing = grantByCategory.get(cat.id);
        if (!existing) return;
        setBusyFor(cat.id, true);
        try {
            await api.put(`/admin/users/${userId}/taxonomy-grants/${existing.id}`, {
                operations: existing.operations,
                includeChildren: !existing.includeChildren,
            });
            load();
        } catch {
            toast.error("Failed to update");
        } finally {
            setBusyFor(cat.id, false);
        }
    };

    if (loading) {
        return <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
            <Loader2 className="size-5 animate-spin text-gray-400" />
        </div>;
    }

    return (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
            <div className="flex items-center justify-between mb-3">
                <div>
                    <h4 className="text-sm font-semibold text-gray-900">Taxonomy Access</h4>
                    <p className="text-xs text-gray-500 mt-0.5">
                        {grants.length} of {filtered.length} taxonomies granted
                    </p>
                </div>
                <input type="text" value={search} onChange={(e) => setSearch(e.target.value)}
                    placeholder="Search taxonomies..."
                    className="text-sm border border-gray-300 rounded-md px-3 py-1.5 w-64" />
            </div>

            <TaxonomyMatrixTable
                title="Activities"
                items={activityCats}
                grantByCategory={grantByCategory}
                busy={busy}
                onToggleOp={toggleOp}
                onToggleAll={toggleAll}
                onToggleIncludeChildren={toggleIncludeChildren}
            />
            <TaxonomyMatrixTable
                title="Record Classes"
                items={recordCats}
                grantByCategory={grantByCategory}
                busy={busy}
                onToggleOp={toggleOp}
                onToggleAll={toggleAll}
                onToggleIncludeChildren={toggleIncludeChildren}
            />

            {filtered.length === 0 && (
                <p className="text-xs text-gray-400 italic py-4">No taxonomies found.</p>
            )}
        </div>
    );
}

function TaxonomyMatrixTable({
    title, items, grantByCategory, busy, onToggleOp, onToggleAll, onToggleIncludeChildren,
}: {
    title: string;
    items: TaxCategory[];
    grantByCategory: Map<string, TaxGrant>;
    busy: Set<string>;
    onToggleOp: (cat: TaxCategory, op: string) => void;
    onToggleAll: (cat: TaxCategory) => void;
    onToggleIncludeChildren: (cat: TaxCategory) => void;
}) {
    if (items.length === 0) return null;
    return (
        <div className="mt-4">
            <h5 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">{title}</h5>
            <div className="overflow-x-auto">
                <table className="w-full text-sm">
                    <thead>
                        <tr className="border-b border-gray-200 text-left text-xs text-gray-500">
                            <th className="py-2 pr-3 font-medium">Code</th>
                            <th className="py-2 pr-3 font-medium">Name</th>
                            <th className="py-2 px-2 text-center font-medium w-14">READ</th>
                            <th className="py-2 px-2 text-center font-medium w-14">CREATE</th>
                            <th className="py-2 px-2 text-center font-medium w-14">UPDATE</th>
                            <th className="py-2 px-2 text-center font-medium w-14">DELETE</th>
                            <th className="py-2 px-2 text-center font-medium w-14">ALL</th>
                            <th className="py-2 px-2 text-center font-medium w-16">+ subs</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                        {items.map(cat => {
                            const grant = grantByCategory.get(cat.id);
                            const ops = new Set(grant?.operations ?? []);
                            const hasAll = ALL_OPS.every(op => ops.has(op));
                            const isBusy = busy.has(cat.id);
                            return (
                                <tr key={cat.id} className={isBusy ? "opacity-50" : ""}>
                                    <td className="py-2 pr-3">
                                        {cat.classificationCode && (
                                            <span className="font-mono text-xs text-blue-600 bg-blue-50 px-1.5 py-0.5 rounded">{cat.classificationCode}</span>
                                        )}
                                    </td>
                                    <td className="py-2 pr-3">
                                        <span className="text-gray-900">{cat.name}</span>
                                        {cat.jurisdiction && (
                                            <span className="ml-2 px-1.5 py-0.5 text-[10px] bg-indigo-50 text-indigo-700 rounded">{cat.jurisdiction}</span>
                                        )}
                                    </td>
                                    {ALL_OPS.map(op => (
                                        <td key={op} className="py-2 px-2 text-center">
                                            <button
                                                onClick={() => onToggleOp(cat, op)}
                                                disabled={isBusy}
                                                className={`w-12 py-1 text-xs font-medium rounded-md transition-colors ${
                                                    ops.has(op)
                                                        ? "bg-blue-600 text-white hover:bg-blue-700"
                                                        : "bg-gray-100 text-gray-400 hover:bg-gray-200"
                                                }`}
                                            >
                                                {ops.has(op) ? "✓" : "—"}
                                            </button>
                                        </td>
                                    ))}
                                    <td className="py-2 px-2 text-center">
                                        <button
                                            onClick={() => onToggleAll(cat)}
                                            disabled={isBusy}
                                            className={`w-12 py-1 text-xs font-medium rounded-md transition-colors ${
                                                hasAll
                                                    ? "bg-green-600 text-white hover:bg-green-700"
                                                    : "bg-gray-100 text-gray-500 hover:bg-gray-200"
                                            }`}
                                        >
                                            {hasAll ? "✓" : "ALL"}
                                        </button>
                                    </td>
                                    <td className="py-2 px-2 text-center">
                                        {grant && (
                                            <button
                                                onClick={() => onToggleIncludeChildren(cat)}
                                                disabled={isBusy}
                                                className={`w-14 py-1 text-xs font-medium rounded-md ${
                                                    grant.includeChildren
                                                        ? "bg-amber-100 text-amber-700 hover:bg-amber-200"
                                                        : "bg-gray-100 text-gray-400 hover:bg-gray-200"
                                                }`}
                                                title={grant.includeChildren ? "Includes sub-categories" : "Direct only"}
                                            >
                                                {grant.includeChildren ? "yes" : "no"}
                                            </button>
                                        )}
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
