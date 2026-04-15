"use client";

import { useCallback, useEffect, useState } from "react";
import {
    Users, Plus, Pencil, Shield, ShieldCheck, Lock, Unlock,
    Search, X, Loader2, Check, ChevronDown, ChevronRight,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import ReasonModal from "@/components/reason-modal";
import FormModal, { FormField } from "@/components/form-modal";
import ResizableTh from "@/components/resizable-th";

type UserSummary = {
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
};

type UserDetail = UserSummary & {
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

const SENSITIVITY_LABELS = ["PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"];
const SENSITIVITY_COLORS = ["bg-green-100 text-green-700", "bg-blue-100 text-blue-700", "bg-amber-100 text-amber-700", "bg-red-100 text-red-700"];

export default function AdminUsersPage() {
    const [users, setUsers] = useState<UserSummary[]>([]);
    const [roles, setRoles] = useState<RoleDef[]>([]);
    const [features, setFeatures] = useState<FeatureDef[]>([]);
    const [selectedUser, setSelectedUser] = useState<UserDetail | null>(null);
    const [search, setSearch] = useState("");
    const [showCreate, setShowCreate] = useState(false);
    const [showRoleEditor, setShowRoleEditor] = useState(false);

    const load = useCallback(async () => {
        try {
            const [u, r, f] = await Promise.all([
                api.get("/admin/users"),
                api.get("/admin/users/roles"),
                api.get("/admin/users/features"),
            ]);
            setUsers(u.data);
            setRoles(r.data);
            setFeatures(f.data);
        } catch { toast.error("Failed to load users"); }
    }, []);

    useEffect(() => { load(); }, [load]);

    const selectUser = async (id: string) => {
        try {
            const { data } = await api.get(`/admin/users/${id}`);
            setSelectedUser(data);
        } catch { toast.error("Failed to load user"); }
    };

    const filtered = users.filter(u => {
        if (!search) return true;
        const q = search.toLowerCase();
        return u.email.toLowerCase().includes(q) ||
            (u.firstName?.toLowerCase().includes(q)) ||
            (u.lastName?.toLowerCase().includes(q)) ||
            (u.department?.toLowerCase().includes(q));
    });

    return (
        <>
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">User Management</h2>
                    <p className="text-sm text-gray-500 mt-1">{users.length} user(s)</p>
                </div>
                <div className="flex gap-2">
                    <button onClick={() => setShowRoleEditor(true)}
                        className="inline-flex items-center gap-2 px-3 py-2 text-sm font-medium border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50">
                        <ShieldCheck className="size-4" /> Manage Roles
                    </button>
                    <button onClick={() => setShowCreate(true)}
                        className="inline-flex items-center gap-2 px-3 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700">
                        <Plus className="size-4" /> Create User
                    </button>
                </div>
            </div>

            {/* Search */}
            <div className="relative mb-4">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-gray-400" />
                <input id="user-search" type="text" value={search} onChange={(e) => setSearch(e.target.value)}
                    placeholder="Search by email, name, or department..."
                    className="w-full pl-10 pr-4 py-2.5 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500" />
            </div>

            <div className="flex gap-6">
                {/* User list */}
                <div className="flex-1">
                    <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="bg-gray-50 border-b border-gray-200">
                                    <ResizableTh className="text-left px-4 py-3 font-medium text-gray-600">User</ResizableTh>
                                    <ResizableTh className="text-left px-4 py-3 font-medium text-gray-600">Roles</ResizableTh>
                                    <ResizableTh className="text-left px-4 py-3 font-medium text-gray-600">Clearance</ResizableTh>
                                    <ResizableTh className="text-left px-4 py-3 font-medium text-gray-600">Status</ResizableTh>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                                {filtered.map(u => (
                                    <tr key={u.id} onClick={() => selectUser(u.id)}
                                        className={`cursor-pointer hover:bg-gray-50 ${selectedUser?.id === u.id ? "bg-blue-50" : ""}`}>
                                        <td className="px-4 py-3">
                                            <div className="flex items-center gap-2.5">
                                                {u.avatarUrl ? (
                                                    <img src={u.avatarUrl} alt="" className="size-8 rounded-full border border-gray-200 shrink-0" referrerPolicy="no-referrer" />
                                                ) : (
                                                    <div className="size-8 rounded-full bg-gray-100 flex items-center justify-center shrink-0">
                                                        <span className="text-xs font-medium text-gray-400">
                                                            {(u.firstName?.[0] ?? u.email[0]).toUpperCase()}
                                                        </span>
                                                    </div>
                                                )}
                                                <div>
                                                    <div className="flex items-center gap-1.5">
                                                        <span className="font-medium text-gray-900">
                                                            {u.firstName && u.lastName ? `${u.firstName} ${u.lastName}` : u.email}
                                                        </span>
                                                        {u.identityProvider === "GOOGLE" && (
                                                            <svg className="size-3.5" viewBox="0 0 24 24">
                                                                <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/>
                                                                <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                                                                <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                                                                <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
                                                            </svg>
                                                        )}
                                                    </div>
                                                    <div className="text-xs text-gray-400">{u.email}</div>
                                                    {u.department && <div className="text-xs text-gray-400">{u.department}</div>}
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-4 py-3">
                                            <div className="flex flex-wrap gap-1">
                                                {u.roles.map(r => (
                                                    <span key={r} className="px-1.5 py-0.5 text-[10px] font-medium bg-blue-100 text-blue-700 rounded">
                                                        {r}
                                                    </span>
                                                ))}
                                            </div>
                                        </td>
                                        <td className="px-4 py-3">
                                            <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${SENSITIVITY_COLORS[u.sensitivityClearanceLevel]}`}>
                                                {SENSITIVITY_LABELS[u.sensitivityClearanceLevel]}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3">
                                            <span className={`inline-flex items-center gap-1 text-xs font-medium ${u.enabled ? "text-green-600" : "text-red-500"}`}>
                                                {u.enabled ? <Check className="size-3" /> : <Lock className="size-3" />}
                                                {u.enabled ? "Active" : "Disabled"}
                                            </span>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>

                {/* Detail panel */}
                {selectedUser && (
                    <UserDetailPanel
                        user={selectedUser}
                        roles={roles}
                        features={features}
                        onUpdate={() => { selectUser(selectedUser.id); load(); }}
                    />
                )}
            </div>

            {/* Create User Modal */}
            {showCreate && (
                <CreateUserModal
                    roles={roles}
                    onClose={() => setShowCreate(false)}
                    onCreated={() => { setShowCreate(false); load(); }}
                />
            )}

            {/* Role Editor Modal */}
            {showRoleEditor && (
                <RoleEditorModal
                    roles={roles}
                    features={features}
                    onClose={() => setShowRoleEditor(false)}
                    onUpdated={load}
                />
            )}
        </>
    );
}

/* ── User Detail Panel ────────────────────────────── */

function UserDetailPanel({ user, roles, features, onUpdate }: {
    user: UserDetail; roles: RoleDef[]; features: FeatureDef[]; onUpdate: () => void;
}) {
    const [editingRoles, setEditingRoles] = useState(false);
    const [selectedRoles, setSelectedRoles] = useState<Set<string>>(new Set(user.roles));
    const [savingRoles, setSavingRoles] = useState(false);
    const [editingClearance, setEditingClearance] = useState(false);
    const [clearance, setClearance] = useState(user.sensitivityClearanceLevel);

    useEffect(() => {
        setSelectedRoles(new Set(user.roles));
        setClearance(user.sensitivityClearanceLevel);
        setEditingRoles(false);
        setEditingClearance(false);
    }, [user.id, user.roles, user.sensitivityClearanceLevel]);

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
            onUpdate();
        } catch { toast.error("Failed to update roles"); }
        finally { setSavingRoles(false); }
    };

    const saveClearance = async () => {
        try {
            await api.put(`/admin/users/${user.id}/clearance`, { level: clearance });
            toast.success("Clearance updated");
            setEditingClearance(false);
            onUpdate();
        } catch { toast.error("Failed"); }
    };

    const toggleEnabled = async () => {
        try {
            await api.put(`/admin/users/${user.id}/status`, { enabled: !user.enabled });
            toast.success(user.enabled ? "User disabled" : "User enabled");
            onUpdate();
        } catch { toast.error("Failed"); }
    };

    // Group permissions by category
    const permsByCategory: Record<string, FeatureDef[]> = {};
    for (const f of features) {
        if (user.permissions.includes(f.permissionKey)) {
            (permsByCategory[f.category] ??= []).push(f);
        }
    }

    return (
        <div className="w-96 shrink-0 space-y-4">
            {/* Profile */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-3">
                        {user.avatarUrl ? (
                            <img src={user.avatarUrl} alt="" className="size-10 rounded-full border border-gray-200" referrerPolicy="no-referrer" />
                        ) : (
                            <div className="size-10 rounded-full bg-gray-100 flex items-center justify-center">
                                <span className="text-sm font-medium text-gray-400">
                                    {(user.firstName?.[0] ?? user.email[0]).toUpperCase()}
                                </span>
                            </div>
                        )}
                        <h3 className="font-semibold text-gray-900">
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
                    <div className="text-gray-500">{user.email}</div>
                    {user.department && <div className="text-gray-400 text-xs">{user.department}</div>}
                    {user.jobTitle && <div className="text-gray-400 text-xs">{user.jobTitle}</div>}
                    <div className="flex items-center gap-2 text-xs text-gray-400">
                        <span>Login method:</span>
                        {user.identityProvider === "GOOGLE" ? (
                            <span className="inline-flex items-center gap-1 px-1.5 py-0.5 bg-blue-50 text-blue-700 rounded text-[10px] font-medium">
                                <svg className="size-3" viewBox="0 0 24 24"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>
                                Google
                            </span>
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
                        <div className="text-xs text-gray-400">
                            Last login: {new Date(user.lastLoginAt).toLocaleString()}
                        </div>
                    )}
                </div>
            </div>

            {/* Roles */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-3">
                    <h4 className="text-sm font-semibold text-gray-900">Roles</h4>
                    {!editingRoles ? (
                        <button onClick={() => setEditingRoles(true)} className="text-xs text-blue-600 hover:text-blue-800">Edit</button>
                    ) : (
                        <div className="flex gap-1">
                            <button onClick={saveRoles} disabled={savingRoles}
                                className="text-xs text-blue-600 hover:text-blue-800 font-medium">
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
                            <button key={role.key}
                                onClick={() => editingRoles && toggleRole(role.key)}
                                disabled={!editingRoles}
                                className={`w-full text-left px-3 py-2 rounded-md text-sm transition-colors ${
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
                        <button key={label}
                            onClick={() => editingClearance && setClearance(i)}
                            disabled={!editingClearance}
                            className={`flex-1 py-2 text-xs font-medium rounded-md transition-colors ${
                                (editingClearance ? clearance : user.sensitivityClearanceLevel) >= i
                                    ? SENSITIVITY_COLORS[i]
                                    : "bg-gray-100 text-gray-400"
                            } ${editingClearance ? "cursor-pointer" : ""}`}>
                            {label}
                        </button>
                    ))}
                </div>
                <p className="text-[10px] text-gray-400 mt-2">
                    User can view documents at or below this sensitivity level
                </p>
            </div>

            {/* Taxonomy Access */}
            <TaxonomyAccessPanel userId={user.id} />

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
                                    <span key={p.permissionKey}
                                        className="px-1.5 py-0.5 text-[10px] bg-gray-100 text-gray-600 rounded"
                                        title={p.description}>
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
    );
}

/* ── Create User Modal ────────────────────────────── */

function CreateUserModal({ roles, onClose, onCreated }: {
    roles: RoleDef[]; onClose: () => void; onCreated: () => void;
}) {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [department, setDepartment] = useState("");
    const [jobTitle, setJobTitle] = useState("");
    const [selectedRoles, setSelectedRoles] = useState<Set<string>>(
        new Set(roles.filter(r => r.defaultForNewUsers).map(r => r.key))
    );
    const [saving, setSaving] = useState(false);

    const handleSave = async () => {
        if (!email || !password) { toast.error("Email and password are required"); return; }
        setSaving(true);
        try {
            await api.post("/admin/users", {
                email, password, firstName, lastName, department, jobTitle,
                displayName: [firstName, lastName].filter(Boolean).join(" ") || null,
                roleKeys: [...selectedRoles],
            });
            toast.success("User created");
            onCreated();
        } catch {
            toast.error("Failed to create user");
        } finally {
            setSaving(false);
        }
    };

    return (
        <FormModal title="Create User" open={true} onClose={onClose} width="lg"
            footer={<>
                <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                <button onClick={handleSave} disabled={saving}
                    className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50">
                    {saving ? "Creating..." : "Create User"}
                </button>
            </>}>
            <div className="grid grid-cols-2 gap-3">
                <div className="col-span-2">
                    <FormField label="Email" id="create-user-email" required>
                        <input id="create-user-email" type="email" value={email} onChange={e => setEmail(e.target.value)}
                            className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                    </FormField>
                </div>
                <div className="col-span-2">
                    <FormField label="Password" id="create-user-password" required>
                        <input id="create-user-password" type="password" value={password} onChange={e => setPassword(e.target.value)}
                            className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                    </FormField>
                </div>
                <FormField label="First Name" id="create-user-first-name">
                    <input id="create-user-first-name" value={firstName} onChange={e => setFirstName(e.target.value)}
                        className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                </FormField>
                <FormField label="Last Name" id="create-user-last-name">
                    <input id="create-user-last-name" value={lastName} onChange={e => setLastName(e.target.value)}
                        className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                </FormField>
                <FormField label="Department" id="create-user-department">
                    <input id="create-user-department" value={department} onChange={e => setDepartment(e.target.value)}
                        className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                </FormField>
                <FormField label="Job Title" id="create-user-job-title">
                    <input id="create-user-job-title" value={jobTitle} onChange={e => setJobTitle(e.target.value)}
                        className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                </FormField>
            </div>

            <div>
                <label className="text-xs font-medium text-gray-700 block mb-2">Roles</label>
                <div className="space-y-1">
                    {roles.map(role => (
                        <label key={role.key} className="flex items-center gap-2 px-3 py-1.5 rounded-md hover:bg-gray-50 cursor-pointer">
                            <input type="checkbox" id={`create-user-role-${role.key}`} checked={selectedRoles.has(role.key)}
                                onChange={() => {
                                    const next = new Set(selectedRoles);
                                    if (next.has(role.key)) next.delete(role.key); else next.add(role.key);
                                    setSelectedRoles(next);
                                }}
                                className="rounded border-gray-300 text-blue-600" />
                            <span className="text-sm text-gray-700">{role.name}</span>
                            {role.defaultForNewUsers && <span className="text-[10px] text-gray-400">(default)</span>}
                            {role.adminRole && <Shield className="size-3 text-red-500" />}
                        </label>
                    ))}
                </div>
            </div>
        </FormModal>
    );
}

/* ── Role Editor Modal ────────────────────────────── */

function RoleEditorModal({ roles, features, onClose, onUpdated }: {
    roles: RoleDef[]; features: FeatureDef[]; onClose: () => void; onUpdated: () => void;
}) {
    const [editingRole, setEditingRole] = useState<RoleDef | null>(null);
    const [saving, setSaving] = useState(false);

    const featuresByCategory = features.reduce((acc, f) => {
        (acc[f.category] ??= []).push(f);
        return acc;
    }, {} as Record<string, FeatureDef[]>);

    const handleSave = async () => {
        if (!editingRole) return;
        setSaving(true);
        try {
            if (editingRole.id) {
                await api.put(`/admin/users/roles/${editingRole.id}`, editingRole);
            } else {
                await api.post("/admin/users/roles", editingRole);
            }
            toast.success("Role saved");
            setEditingRole(null);
            onUpdated();
        } catch { toast.error("Failed"); }
        finally { setSaving(false); }
    };

    const toggleFeature = (featureId: string) => {
        if (!editingRole) return;
        const next = editingRole.featureIds.includes(featureId)
            ? editingRole.featureIds.filter(id => id !== featureId)
            : [...editingRole.featureIds, featureId];
        setEditingRole({ ...editingRole, featureIds: next });
    };

    const newRole = (): RoleDef => ({
        id: "", key: "", name: "", description: "", featureIds: [],
        adminRole: false, defaultForNewUsers: false, defaultSensitivityClearance: 0, systemProtected: false,
    });

    return (
        <FormModal title="Manage Roles" open={true} onClose={onClose} width="2xl"
            footer={editingRole ? <>
                <button onClick={() => setEditingRole(null)}
                    className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">Back</button>
                <button onClick={handleSave} disabled={saving || editingRole.systemProtected}
                    className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50">
                    {saving ? "Saving..." : "Save Role"}
                </button>
            </> : undefined}>
            {!editingRole && (
                <>
                    <div className="flex justify-end">
                        <button onClick={() => setEditingRole(newRole())}
                            className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium bg-blue-600 text-white rounded-md hover:bg-blue-700">
                            <Plus className="size-3" /> New Role
                        </button>
                    </div>
                    <div className="space-y-2">
                        {roles.map(role => (
                            <div key={role.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg border border-gray-200">
                                <div>
                                    <div className="flex items-center gap-2">
                                        <span className="text-sm font-medium text-gray-900">{role.name}</span>
                                        <code className="text-[10px] text-gray-400">{role.key}</code>
                                        {role.adminRole && <span className="px-1.5 py-0.5 text-[10px] bg-red-100 text-red-700 rounded">Admin</span>}
                                        {role.defaultForNewUsers && <span className="px-1.5 py-0.5 text-[10px] bg-green-100 text-green-700 rounded">Default</span>}
                                        {role.systemProtected && <span className="px-1.5 py-0.5 text-[10px] bg-gray-100 text-gray-500 rounded">Protected</span>}
                                    </div>
                                    {role.description && <p className="text-xs text-gray-500 mt-0.5">{role.description}</p>}
                                    <p className="text-[10px] text-gray-400 mt-0.5">{role.featureIds.length} permissions</p>
                                </div>
                                <button onClick={() => setEditingRole({ ...role })} aria-label={`Edit role ${role.name}`}
                                    className="text-gray-400 hover:text-blue-600"><Pencil className="size-4" /></button>
                            </div>
                        ))}
                    </div>
                </>
            )}

            {/* Role editor */}
            {editingRole && (
                <div className="space-y-4">
                    <div className="grid grid-cols-2 gap-3">
                        <FormField label="Key" id="role-key">
                            <input id="role-key" value={editingRole.key}
                                onChange={e => setEditingRole({ ...editingRole, key: e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, "") })}
                                disabled={!!editingRole.id}
                                className="w-full text-sm border border-gray-300 rounded-md px-3 py-2 disabled:bg-gray-100" />
                        </FormField>
                        <FormField label="Name" id="role-name">
                            <input id="role-name" value={editingRole.name}
                                onChange={e => setEditingRole({ ...editingRole, name: e.target.value })}
                                className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                        </FormField>
                    </div>
                    <FormField label="Description" id="role-description">
                        <input id="role-description" value={editingRole.description ?? ""}
                            onChange={e => setEditingRole({ ...editingRole, description: e.target.value })}
                            className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                    </FormField>
                    <div className="flex gap-4">
                        <label className="flex items-center gap-2 text-sm">
                            <input type="checkbox" id="role-admin" checked={editingRole.adminRole}
                                onChange={e => setEditingRole({ ...editingRole, adminRole: e.target.checked })}
                                className="rounded border-gray-300 text-blue-600" />
                            Admin role
                        </label>
                        <label className="flex items-center gap-2 text-sm">
                            <input type="checkbox" id="role-default" checked={editingRole.defaultForNewUsers}
                                onChange={e => setEditingRole({ ...editingRole, defaultForNewUsers: e.target.checked })}
                                className="rounded border-gray-300 text-blue-600" />
                            Default for new users
                        </label>
                        <div className="flex items-center gap-2 text-sm">
                            <label htmlFor="role-clearance">Clearance:</label>
                            <select id="role-clearance" value={editingRole.defaultSensitivityClearance}
                                onChange={e => setEditingRole({ ...editingRole, defaultSensitivityClearance: parseInt(e.target.value) })}
                                className="text-sm border border-gray-300 rounded-md px-2 py-1">
                                {SENSITIVITY_LABELS.map((l, i) => <option key={i} value={i}>{l}</option>)}
                            </select>
                        </div>
                    </div>

                    {/* Permission matrix */}
                    <div>
                        <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Permissions</h4>
                        <div className="space-y-3 max-h-64 overflow-y-auto">
                            {Object.entries(featuresByCategory).map(([cat, feats]) => (
                                <div key={cat}>
                                    <div className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1">{cat}</div>
                                    <div className="grid grid-cols-2 gap-1">
                                        {feats.map(f => (
                                            <label key={f.id} className="flex items-center gap-2 px-2 py-1 rounded hover:bg-gray-50 cursor-pointer">
                                                <input type="checkbox" id={`role-feature-${f.id}`}
                                                    checked={editingRole.featureIds.includes(f.id)}
                                                    onChange={() => toggleFeature(f.id)}
                                                    className="rounded border-gray-300 text-blue-600" />
                                                <span className="text-xs text-gray-700" title={f.description}>{f.name}</span>
                                            </label>
                                        ))}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            )}
        </FormModal>
    );
}

/* ── Taxonomy Access Panel ────────────────────────── */

type TaxGrant = {
    id: string;
    categoryId: string;
    includeChildren: boolean;
    operations: string[];
    grantedBy: string;
    grantedAt: string;
    reason?: string;
};

function TaxonomyAccessPanel({ userId }: { userId: string }) {
    const [grants, setGrants] = useState<TaxGrant[]>([]);
    const [categories, setCategories] = useState<{ id: string; name: string }[]>([]);
    const [adding, setAdding] = useState(false);
    const [newCatId, setNewCatId] = useState("");
    const [inclChildren, setInclChildren] = useState(true);
    const [reason, setReason] = useState("");

    const load = useCallback(async () => {
        try {
            const [g, c] = await Promise.all([
                api.get(`/admin/users/${userId}/taxonomy-grants`),
                api.get("/admin/governance/taxonomy"),
            ]);
            setGrants(g.data);
            setCategories(c.data);
        } catch {}
    }, [userId]);

    useEffect(() => { load(); }, [load]);

    const handleGrant = async () => {
        if (!newCatId) return;
        try {
            await api.post(`/admin/users/${userId}/taxonomy-grants`, {
                categoryId: newCatId, includeChildren: inclChildren,
                operations: ["READ", "CREATE", "UPDATE"], reason: reason || "Admin granted",
            });
            toast.success("Access granted");
            setAdding(false); setNewCatId(""); setReason("");
            load();
        } catch { toast.error("Failed"); }
    };

    const handleRevoke = async (grantId: string) => {
        if (!confirm("Revoke this access?")) return;
        try {
            await api.delete(`/admin/users/${userId}/taxonomy-grants/${grantId}`);
            toast.success("Access revoked");
            load();
        } catch { toast.error("Failed"); }
    };

    const catName = (id: string) => categories.find(c => c.id === id)?.name ?? id;

    return (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5">
            <div className="flex items-center justify-between mb-3">
                <h4 className="text-sm font-semibold text-gray-900">Taxonomy Access ({grants.length})</h4>
                <button onClick={() => setAdding(!adding)} className="text-xs text-blue-600 hover:text-blue-800">
                    {adding ? "Cancel" : "+ Grant Access"}
                </button>
            </div>

            {adding && (
                <div className="bg-blue-50 rounded-md p-3 mb-3 space-y-2">
                    <select id="taxonomy-grant-category" value={newCatId} onChange={e => setNewCatId(e.target.value)}
                        className="w-full text-xs border border-gray-300 rounded-md px-2 py-1.5">
                        <option value="">Select category...</option>
                        {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                    </select>
                    <label className="flex items-center gap-2 text-xs text-gray-700">
                        <input type="checkbox" id="taxonomy-grant-children" checked={inclChildren} onChange={e => setInclChildren(e.target.checked)}
                            className="rounded border-gray-300 text-blue-600" />
                        Include sub-categories
                    </label>
                    <input id="taxonomy-grant-reason" value={reason} onChange={e => setReason(e.target.value)} placeholder="Reason (optional)"
                        className="w-full text-xs border border-gray-300 rounded-md px-2 py-1.5" />
                    <button onClick={handleGrant} className="text-xs text-white bg-blue-600 px-3 py-1 rounded hover:bg-blue-700">Grant</button>
                </div>
            )}

            {grants.length === 0 ? (
                <p className="text-xs text-gray-400 italic">No taxonomy grants — user can only see their own documents</p>
            ) : (
                <div className="space-y-1.5">
                    {grants.map(g => (
                        <div key={g.id} className="flex items-center justify-between p-2 bg-gray-50 rounded-md">
                            <div>
                                <span className="text-xs font-medium text-gray-900">{catName(g.categoryId)}</span>
                                {g.includeChildren && <span className="text-[10px] text-gray-400 ml-1">+ subs</span>}
                                <div className="text-[10px] text-gray-400">
                                    {g.operations?.join(", ")} &middot; by {g.grantedBy}
                                </div>
                            </div>
                            <button onClick={() => handleRevoke(g.id)}
                                className="text-[10px] text-red-500 hover:text-red-700">Revoke</button>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
