"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { Users, Plus, Loader2, X, Pencil, Trash2, KeyRound, AlertCircle, Check, Shield } from "lucide-react";
import { api, isAuthenticated } from "@/lib/api";

interface HubUser {
    id: string;
    username: string;
    displayName?: string;
    email?: string;
    roles: string[];
    active: boolean;
    createdAt: string;
    lastLoginAt?: string;
    createdBy?: string;
}

const AVAILABLE_ROLES = ["HUB_ADMIN"];

export default function HubUsersPage() {
    const router = useRouter();
    const [users, setUsers] = useState<HubUser[]>([]);
    const [loading, setLoading] = useState(true);
    const [showCreate, setShowCreate] = useState(false);
    const [editing, setEditing] = useState<HubUser | null>(null);
    const [resettingPwd, setResettingPwd] = useState<HubUser | null>(null);
    const [error, setError] = useState("");

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const data = await api.get<HubUser[]>("/api/hub/admin/users");
            setUsers(data || []);
        } catch {} finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (!isAuthenticated()) { router.push("/"); return; }
        load();
    }, [router, load]);

    const handleDelete = async (u: HubUser) => {
        if (!confirm(`Delete user "${u.username}"? This cannot be undone.`)) return;
        setError("");
        try {
            await api.delete(`/api/hub/admin/users/${u.id}`);
            load();
        } catch (e) {
            setError(e instanceof Error ? e.message : "Delete failed");
        }
    };

    const handleToggleActive = async (u: HubUser) => {
        try {
            await api.put(`/api/hub/admin/users/${u.id}`, { active: !u.active });
            load();
        } catch (e) {
            setError(e instanceof Error ? e.message : "Update failed");
        }
    };

    if (loading) {
        return <div className="flex h-full items-center justify-center"><Loader2 className="h-6 w-6 animate-spin text-gray-400" /></div>;
    }

    return (
        <div className="p-8">
            <div className="mb-6 flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <Users className="h-6 w-6 text-gray-700" />
                    <div>
                        <h1 className="text-2xl font-bold text-gray-900">Hub Users</h1>
                        <p className="text-sm text-gray-500">Manage admin accounts that can access this hub</p>
                    </div>
                </div>
                <button onClick={() => setShowCreate(true)}
                    className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700">
                    <Plus className="h-4 w-4" /> Create User
                </button>
            </div>

            {error && (
                <div className="mb-4 flex items-center gap-2 rounded-md bg-red-50 p-3 text-sm text-red-700">
                    <AlertCircle className="h-4 w-4" /> {error}
                </div>
            )}

            <div className="rounded-lg border border-gray-200 bg-white">
                <table className="w-full text-sm">
                    <thead>
                        <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs text-gray-500">
                            <th className="px-4 py-2 font-medium">Username</th>
                            <th className="px-4 py-2 font-medium">Display Name</th>
                            <th className="px-4 py-2 font-medium">Email</th>
                            <th className="px-4 py-2 font-medium">Roles</th>
                            <th className="px-4 py-2 font-medium">Status</th>
                            <th className="px-4 py-2 font-medium">Last Login</th>
                            <th className="px-4 py-2 font-medium text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                        {users.length === 0 ? (
                            <tr><td colSpan={7} className="px-4 py-10 text-center text-sm text-gray-400">No users yet.</td></tr>
                        ) : users.map((u) => (
                            <tr key={u.id} className="hover:bg-gray-50">
                                <td className="px-4 py-3 font-mono text-xs text-gray-900">{u.username}</td>
                                <td className="px-4 py-3">{u.displayName || "—"}</td>
                                <td className="px-4 py-3 text-gray-600">{u.email || "—"}</td>
                                <td className="px-4 py-3">
                                    <div className="flex flex-wrap gap-1">
                                        {u.roles.map((r) => (
                                            <span key={r} className="inline-flex items-center gap-1 rounded bg-blue-50 px-1.5 py-0.5 text-[10px] font-medium text-blue-700">
                                                {r === "HUB_ADMIN" && <Shield className="h-2.5 w-2.5" />}{r}
                                            </span>
                                        ))}
                                    </div>
                                </td>
                                <td className="px-4 py-3">
                                    <button onClick={() => handleToggleActive(u)}
                                        className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium ${
                                            u.active ? "bg-green-50 text-green-700 hover:bg-green-100" : "bg-gray-100 text-gray-500 hover:bg-gray-200"
                                        }`}>
                                        {u.active ? <Check className="h-2.5 w-2.5" /> : null}
                                        {u.active ? "Active" : "Disabled"}
                                    </button>
                                </td>
                                <td className="px-4 py-3 text-xs text-gray-500">
                                    {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : <span className="text-gray-400 italic">never</span>}
                                </td>
                                <td className="px-4 py-3 text-right">
                                    <button onClick={() => setResettingPwd(u)} title="Reset password"
                                        className="rounded p-1 text-gray-400 hover:bg-gray-200 hover:text-gray-700">
                                        <KeyRound className="h-3.5 w-3.5" />
                                    </button>
                                    <button onClick={() => setEditing(u)} title="Edit"
                                        className="ml-1 rounded p-1 text-gray-400 hover:bg-gray-200 hover:text-gray-700">
                                        <Pencil className="h-3.5 w-3.5" />
                                    </button>
                                    <button onClick={() => handleDelete(u)} title="Delete"
                                        className="ml-1 rounded p-1 text-gray-400 hover:bg-red-100 hover:text-red-600">
                                        <Trash2 className="h-3.5 w-3.5" />
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {showCreate && <CreateUserModal onClose={() => setShowCreate(false)} onCreated={() => { setShowCreate(false); load(); }} />}
            {editing && <EditUserModal user={editing} onClose={() => setEditing(null)} onUpdated={() => { setEditing(null); load(); }} />}
            {resettingPwd && <ResetPasswordModal user={resettingPwd} onClose={() => setResettingPwd(null)} onReset={() => { setResettingPwd(null); load(); }} />}
        </div>
    );
}

const inputCls = "w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500";

function ModalShell({ title, onClose, children, footer }: { title: string; onClose: () => void; children: React.ReactNode; footer: React.ReactNode }) {
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
            <div className="w-full max-w-md rounded-lg bg-white shadow-xl">
                <div className="flex items-center justify-between border-b border-gray-200 px-5 py-4">
                    <h2 className="text-base font-semibold text-gray-900">{title}</h2>
                    <button onClick={onClose} className="rounded p-1 text-gray-400 hover:text-gray-600"><X className="h-5 w-5" /></button>
                </div>
                <div className="space-y-4 px-5 py-4">{children}</div>
                <div className="flex justify-end gap-2 border-t border-gray-200 px-5 py-3">{footer}</div>
            </div>
        </div>
    );
}

function CreateUserModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [displayName, setDisplayName] = useState("");
    const [email, setEmail] = useState("");
    const [roles, setRoles] = useState<Set<string>>(new Set(["HUB_ADMIN"]));
    const [saving, setSaving] = useState(false);
    const [err, setErr] = useState("");

    const save = async () => {
        if (!username || password.length < 8) { setErr("Username and 8+ char password required"); return; }
        setSaving(true); setErr("");
        try {
            await api.post("/api/hub/admin/users", { username, password, displayName, email, roles: [...roles] });
            onCreated();
        } catch (e) {
            setErr(e instanceof Error ? e.message : "Failed");
        } finally {
            setSaving(false);
        }
    };

    return (
        <ModalShell title="Create User" onClose={onClose}
            footer={<>
                <button onClick={onClose} className="rounded-md border border-gray-300 px-4 py-2 text-sm">Cancel</button>
                <button onClick={save} disabled={saving} className="rounded-md bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 disabled:opacity-60">
                    {saving ? "Creating..." : "Create"}
                </button>
            </>}>
            {err && <div className="rounded bg-red-50 p-2 text-xs text-red-700">{err}</div>}
            <div>
                <label className="mb-1 block text-xs font-medium text-gray-600">Username *</label>
                <input value={username} onChange={(e) => setUsername(e.target.value)} className={inputCls} placeholder="username" />
            </div>
            <div>
                <label className="mb-1 block text-xs font-medium text-gray-600">Password * <span className="text-gray-400">(min 8 chars)</span></label>
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} className={inputCls} />
            </div>
            <div>
                <label className="mb-1 block text-xs font-medium text-gray-600">Display Name</label>
                <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} className={inputCls} />
            </div>
            <div>
                <label className="mb-1 block text-xs font-medium text-gray-600">Email</label>
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className={inputCls} />
            </div>
            <div>
                <label className="mb-1 block text-xs font-medium text-gray-600">Roles</label>
                <div className="space-y-1">
                    {AVAILABLE_ROLES.map((r) => (
                        <label key={r} className="flex items-center gap-2 text-sm">
                            <input type="checkbox" checked={roles.has(r)} onChange={() => {
                                const next = new Set(roles);
                                next.has(r) ? next.delete(r) : next.add(r);
                                setRoles(next);
                            }} className="rounded border-gray-300" />
                            {r}
                        </label>
                    ))}
                </div>
            </div>
        </ModalShell>
    );
}

function EditUserModal({ user, onClose, onUpdated }: { user: HubUser; onClose: () => void; onUpdated: () => void }) {
    const [displayName, setDisplayName] = useState(user.displayName ?? "");
    const [email, setEmail] = useState(user.email ?? "");
    const [roles, setRoles] = useState<Set<string>>(new Set(user.roles));
    const [active, setActive] = useState(user.active);
    const [saving, setSaving] = useState(false);
    const [err, setErr] = useState("");

    const save = async () => {
        setSaving(true); setErr("");
        try {
            await api.put(`/api/hub/admin/users/${user.id}`, { displayName, email, roles: [...roles], active });
            onUpdated();
        } catch (e) {
            setErr(e instanceof Error ? e.message : "Failed");
        } finally {
            setSaving(false);
        }
    };

    return (
        <ModalShell title={`Edit ${user.username}`} onClose={onClose}
            footer={<>
                <button onClick={onClose} className="rounded-md border border-gray-300 px-4 py-2 text-sm">Cancel</button>
                <button onClick={save} disabled={saving} className="rounded-md bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 disabled:opacity-60">
                    {saving ? "Saving..." : "Save"}
                </button>
            </>}>
            {err && <div className="rounded bg-red-50 p-2 text-xs text-red-700">{err}</div>}
            <div>
                <label className="mb-1 block text-xs font-medium text-gray-600">Display Name</label>
                <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} className={inputCls} />
            </div>
            <div>
                <label className="mb-1 block text-xs font-medium text-gray-600">Email</label>
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className={inputCls} />
            </div>
            <div>
                <label className="mb-1 block text-xs font-medium text-gray-600">Roles</label>
                <div className="space-y-1">
                    {AVAILABLE_ROLES.map((r) => (
                        <label key={r} className="flex items-center gap-2 text-sm">
                            <input type="checkbox" checked={roles.has(r)} onChange={() => {
                                const next = new Set(roles);
                                next.has(r) ? next.delete(r) : next.add(r);
                                setRoles(next);
                            }} className="rounded border-gray-300" />
                            {r}
                        </label>
                    ))}
                </div>
            </div>
            <label className="flex items-center gap-2 text-sm text-gray-700">
                <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} className="rounded border-gray-300" />
                Account is active
            </label>
        </ModalShell>
    );
}

function ResetPasswordModal({ user, onClose, onReset }: { user: HubUser; onClose: () => void; onReset: () => void }) {
    const [password, setPassword] = useState("");
    const [saving, setSaving] = useState(false);
    const [err, setErr] = useState("");

    const save = async () => {
        if (password.length < 8) { setErr("Password must be at least 8 chars"); return; }
        setSaving(true); setErr("");
        try {
            await api.post(`/api/hub/admin/users/${user.id}/reset-password`, { password });
            onReset();
        } catch (e) {
            setErr(e instanceof Error ? e.message : "Failed");
        } finally {
            setSaving(false);
        }
    };

    return (
        <ModalShell title={`Reset password — ${user.username}`} onClose={onClose}
            footer={<>
                <button onClick={onClose} className="rounded-md border border-gray-300 px-4 py-2 text-sm">Cancel</button>
                <button onClick={save} disabled={saving || password.length < 8} className="rounded-md bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 disabled:opacity-60">
                    {saving ? "Resetting..." : "Reset Password"}
                </button>
            </>}>
            {err && <div className="rounded bg-red-50 p-2 text-xs text-red-700">{err}</div>}
            <div>
                <label className="mb-1 block text-xs font-medium text-gray-600">New Password <span className="text-gray-400">(min 8 chars)</span></label>
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} className={inputCls} autoFocus />
            </div>
            <p className="text-xs text-gray-500">The user will need to log out and back in.</p>
        </ModalShell>
    );
}
