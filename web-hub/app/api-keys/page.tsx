"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, isAuthenticated } from "@/lib/api";
import {
  KeyRound,
  Plus,
  Loader2,
  X,
  Copy,
  Check,
  Ban,
  ToggleLeft,
  ToggleRight,
  AlertCircle,
} from "lucide-react";

interface HubApiKey {
  id: string;
  keyPrefix: string;
  tenantName: string;
  tenantEmail: string;
  permissions: string[];
  active: boolean;
  rateLimit: number;
  downloadQuota: number;
  downloadsThisMonth: number;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
}

interface GeneratedKeyResponse {
  key: string;
  id: string;
  keyPrefix: string;
  tenantName: string;
  message: string;
}

const AVAILABLE_PERMISSIONS = [
  "BROWSE_PACKS",
  "DOWNLOAD_PACKS",
  "SUBMIT_REVIEWS",
];

export default function ApiKeysPage() {
  const router = useRouter();
  const [keys, setKeys] = useState<HubApiKey[]>([]);
  const [loading, setLoading] = useState(true);

  // Generate form
  const [showForm, setShowForm] = useState(false);
  const [tenantName, setTenantName] = useState("");
  const [tenantEmail, setTenantEmail] = useState("");
  const [permissions, setPermissions] = useState<string[]>([
    "BROWSE_PACKS",
    "DOWNLOAD_PACKS",
  ]);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState("");

  // Generated key modal
  const [generatedKey, setGeneratedKey] = useState<GeneratedKeyResponse | null>(
    null
  );
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!isAuthenticated()) {
      router.push("/");
      return;
    }
    loadKeys();
  }, [router]);

  async function loadKeys() {
    setLoading(true);
    try {
      const data = await api.get<HubApiKey[]>("/api/hub/admin/api-keys");
      setKeys(data || []);
    } catch {
      // handled by api.ts
    } finally {
      setLoading(false);
    }
  }

  function togglePermission(perm: string) {
    if (permissions.includes(perm)) {
      setPermissions(permissions.filter((p) => p !== perm));
    } else {
      setPermissions([...permissions, perm]);
    }
  }

  async function handleGenerate(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setGenerating(true);

    try {
      const result = await api.post<GeneratedKeyResponse>(
        "/api/hub/admin/api-keys",
        { tenantName, tenantEmail, permissions }
      );
      setGeneratedKey(result);
      setShowForm(false);
      setTenantName("");
      setTenantEmail("");
      setPermissions(["BROWSE_PACKS", "DOWNLOAD_PACKS"]);
      await loadKeys();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate key");
    } finally {
      setGenerating(false);
    }
  }

  async function toggleActive(key: HubApiKey) {
    try {
      await api.put(`/api/hub/admin/api-keys/${key.id}`, {
        active: !key.active,
        rateLimit: key.rateLimit,
        downloadQuota: key.downloadQuota,
      });
      await loadKeys();
    } catch {
      // silent
    }
  }

  async function revokeKey(key: HubApiKey) {
    if (!confirm(`Revoke API key ${key.keyPrefix}... for ${key.tenantName}?`)) {
      return;
    }
    try {
      await api.delete(`/api/hub/admin/api-keys/${key.id}`);
      await loadKeys();
    } catch {
      // silent
    }
  }

  function copyToClipboard(text: string) {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
      </div>
    );
  }

  return (
    <div className="p-8">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">API Keys</h1>
          <p className="mt-1 text-sm text-gray-500">
            Manage API keys for pack distribution
          </p>
        </div>
        <button
          onClick={() => {
            setShowForm(true);
            setError("");
          }}
          className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-blue-700"
        >
          <Plus className="h-4 w-4" />
          Generate Key
        </button>
      </div>

      {/* Generated key modal */}
      {generatedKey && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="mx-4 w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-gray-900">
                API Key Generated
              </h2>
              <button
                onClick={() => setGeneratedKey(null)}
                className="rounded p-1 text-gray-400 hover:text-gray-600"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="mb-4 flex items-center gap-2 rounded-md bg-amber-50 p-3 text-sm text-amber-800">
              <AlertCircle className="h-4 w-4 shrink-0" />
              {generatedKey.message}
            </div>

            <div className="mb-4">
              <label className="mb-1 block text-xs font-medium text-gray-500">
                Tenant
              </label>
              <p className="text-sm font-medium text-gray-900">
                {generatedKey.tenantName}
              </p>
            </div>

            <div className="mb-4">
              <label className="mb-1 block text-xs font-medium text-gray-500">
                API Key
              </label>
              <div className="flex items-center gap-2">
                <code className="flex-1 break-all rounded-md bg-gray-100 px-3 py-2 font-mono text-sm text-gray-800">
                  {generatedKey.key}
                </code>
                <button
                  onClick={() => copyToClipboard(generatedKey.key)}
                  className="shrink-0 rounded-md border border-gray-300 p-2 text-gray-500 hover:bg-gray-50 hover:text-gray-700"
                >
                  {copied ? (
                    <Check className="h-4 w-4 text-green-600" />
                  ) : (
                    <Copy className="h-4 w-4" />
                  )}
                </button>
              </div>
            </div>

            <button
              onClick={() => setGeneratedKey(null)}
              className="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800"
            >
              Done
            </button>
          </div>
        </div>
      )}

      {/* Generate form */}
      {showForm && (
        <div className="mb-6 rounded-lg border border-gray-200 bg-white p-6">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-gray-900">
              Generate New API Key
            </h2>
            <button
              onClick={() => setShowForm(false)}
              className="rounded p-1 text-gray-400 hover:text-gray-600"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          {error && (
            <div className="mb-4 rounded-md bg-red-50 p-3 text-sm text-red-700">
              {error}
            </div>
          )}

          <form onSubmit={handleGenerate} className="space-y-4">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label
                  htmlFor="tenant-name"
                  className="mb-1 block text-sm font-medium text-gray-700"
                >
                  Tenant Name
                </label>
                <input
                  id="tenant-name"
                  type="text"
                  required
                  value={tenantName}
                  onChange={(e) => setTenantName(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="Acme Corp"
                />
              </div>
              <div>
                <label
                  htmlFor="tenant-email"
                  className="mb-1 block text-sm font-medium text-gray-700"
                >
                  Tenant Email
                </label>
                <input
                  id="tenant-email"
                  type="email"
                  required
                  value={tenantEmail}
                  onChange={(e) => setTenantEmail(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="admin@acme.com"
                />
              </div>
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-gray-700">
                Permissions
              </label>
              <div className="space-y-2">
                {AVAILABLE_PERMISSIONS.map((perm) => (
                  <label
                    key={perm}
                    className="flex items-center gap-2 cursor-pointer"
                  >
                    <input
                      type="checkbox"
                      checked={permissions.includes(perm)}
                      onChange={() => togglePermission(perm)}
                      className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                    />
                    <span className="text-sm text-gray-700">{perm}</span>
                  </label>
                ))}
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={generating}
                className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-blue-700 disabled:opacity-60"
              >
                {generating && (
                  <Loader2 className="h-4 w-4 animate-spin" />
                )}
                Generate Key
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Keys table */}
      {keys.length === 0 ? (
        <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
          <KeyRound className="mx-auto h-10 w-10 text-gray-300" />
          <p className="mt-3 text-sm font-medium text-gray-500">
            No API keys yet
          </p>
          <p className="mt-1 text-sm text-gray-400">
            Generate a key to allow tenants to download packs.
          </p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Key Prefix
                </th>
                <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Tenant
                </th>
                <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Permissions
                </th>
                <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Status
                </th>
                <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Usage
                </th>
                <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Last Used
                </th>
                <th className="px-5 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {keys.map((key) => (
                <tr key={key.id} className="hover:bg-gray-50">
                  <td className="px-5 py-3.5 font-mono text-sm text-gray-800">
                    {key.keyPrefix}...
                  </td>
                  <td className="px-5 py-3.5">
                    <p className="text-sm font-medium text-gray-900">
                      {key.tenantName}
                    </p>
                    <p className="text-xs text-gray-500">
                      {key.tenantEmail}
                    </p>
                  </td>
                  <td className="px-5 py-3.5">
                    <div className="flex flex-wrap gap-1">
                      {(key.permissions || []).map((p) => (
                        <span
                          key={p}
                          className="inline-flex rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-medium text-gray-600"
                        >
                          {p}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="px-5 py-3.5">
                    <span
                      className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
                        key.active
                          ? "bg-green-50 text-green-700"
                          : "bg-red-50 text-red-700"
                      }`}
                    >
                      {key.active ? "Active" : "Inactive"}
                    </span>
                  </td>
                  <td className="px-5 py-3.5 text-sm text-gray-600">
                    {key.downloadsThisMonth || 0}
                    {key.downloadQuota > 0 && (
                      <span className="text-gray-400">
                        {" "}
                        / {key.downloadQuota}
                      </span>
                    )}
                    <span className="text-xs text-gray-400"> this month</span>
                  </td>
                  <td className="px-5 py-3.5 text-sm text-gray-500">
                    {key.lastUsedAt
                      ? new Date(key.lastUsedAt).toLocaleDateString(
                          "en-GB",
                          {
                            day: "numeric",
                            month: "short",
                            year: "numeric",
                          }
                        )
                      : "Never"}
                  </td>
                  <td className="px-5 py-3.5 text-right">
                    <div className="flex items-center justify-end gap-2">
                      <button
                        onClick={() => toggleActive(key)}
                        title={key.active ? "Deactivate" : "Activate"}
                        className="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-700"
                      >
                        {key.active ? (
                          <ToggleRight className="h-5 w-5 text-green-600" />
                        ) : (
                          <ToggleLeft className="h-5 w-5" />
                        )}
                      </button>
                      <button
                        onClick={() => revokeKey(key)}
                        title="Revoke"
                        className="rounded p-1 text-gray-400 hover:bg-red-50 hover:text-red-600"
                      >
                        <Ban className="h-4 w-4" />
                      </button>
                    </div>
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
