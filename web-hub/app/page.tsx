"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  api,
  storeCredentials,
  isAuthenticated,
} from "@/lib/api";
import {
  Package,
  KeyRound,
  Download,
  Shield,
  Loader2,
  AlertCircle,
} from "lucide-react";

interface GovernancePack {
  id: string;
  name: string;
  slug: string;
  status: string;
  downloadCount: number;
  latestVersionNumber: number;
  createdAt: string;
  updatedAt: string;
}

interface HubApiKey {
  id: string;
  keyPrefix: string;
  tenantName: string;
  active: boolean;
}

export default function DashboardPage() {
  const router = useRouter();
  const [authed, setAuthed] = useState(false);
  const [checking, setChecking] = useState(true);

  // Login form
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loginError, setLoginError] = useState("");
  const [loginLoading, setLoginLoading] = useState(false);

  // Dashboard data
  const [packs, setPacks] = useState<GovernancePack[]>([]);
  const [apiKeys, setApiKeys] = useState<HubApiKey[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (isAuthenticated()) {
      setAuthed(true);
    }
    setChecking(false);
  }, []);

  useEffect(() => {
    if (!authed) return;
    loadDashboard();
  }, [authed]);

  async function loadDashboard() {
    setLoading(true);
    try {
      const [packsData, keysData] = await Promise.all([
        api.get<GovernancePack[]>("/api/hub/admin/packs"),
        api.get<HubApiKey[]>("/api/hub/admin/api-keys"),
      ]);
      setPacks(packsData || []);
      setApiKeys(keysData || []);
    } catch {
      // If 401, api.ts will redirect
    } finally {
      setLoading(false);
    }
  }

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault();
    setLoginError("");
    setLoginLoading(true);

    try {
      storeCredentials(username, password);
      await api.get<HubApiKey[]>("/api/hub/admin/api-keys");
      setAuthed(true);
      router.refresh();
    } catch {
      setLoginError("Invalid credentials. Please try again.");
      const { clearCredentials } = await import("@/lib/api");
      clearCredentials();
    } finally {
      setLoginLoading(false);
    }
  }

  if (checking) {
    return (
      <div className="flex h-screen items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
      </div>
    );
  }

  if (!authed) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-50">
        <div className="w-full max-w-sm">
          <div className="mb-8 text-center">
            <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-gray-900">
              <Shield className="h-6 w-6 text-blue-400" />
            </div>
            <h1 className="text-2xl font-bold text-gray-900">IG Hub Admin</h1>
            <p className="mt-1 text-sm text-gray-500">
              Sign in to manage governance packs
            </p>
          </div>

          <form onSubmit={handleLogin} className="space-y-4">
            {loginError && (
              <div className="flex items-center gap-2 rounded-md bg-red-50 p-3 text-sm text-red-700">
                <AlertCircle className="h-4 w-4 shrink-0" />
                {loginError}
              </div>
            )}

            <div>
              <label
                htmlFor="username"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                Username
              </label>
              <input
                id="username"
                type="text"
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="admin"
              />
            </div>

            <div>
              <label
                htmlFor="password"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                Password
              </label>
              <input
                id="password"
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="Enter password"
              />
            </div>

            <button
              type="submit"
              disabled={loginLoading}
              className="flex w-full items-center justify-center rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-blue-700 disabled:opacity-60"
            >
              {loginLoading ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                "Sign In"
              )}
            </button>
          </form>
        </div>
      </div>
    );
  }

  const totalDownloads = packs.reduce((sum, p) => sum + (p.downloadCount || 0), 0);
  const publishedCount = packs.filter((p) => p.status === "PUBLISHED").length;

  const stats = [
    {
      label: "Total Packs",
      value: packs.length,
      icon: Package,
      color: "text-blue-600 bg-blue-50",
    },
    {
      label: "Published",
      value: publishedCount,
      icon: Package,
      color: "text-green-600 bg-green-50",
    },
    {
      label: "API Keys",
      value: apiKeys.length,
      icon: KeyRound,
      color: "text-purple-600 bg-purple-50",
    },
    {
      label: "Total Downloads",
      value: totalDownloads,
      icon: Download,
      color: "text-orange-600 bg-orange-50",
    },
  ];

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
      </div>
    );
  }

  return (
    <div className="p-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="mt-1 text-sm text-gray-500">
          Governance Hub overview
        </p>
      </div>

      <div className="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => {
          const Icon = stat.icon;
          return (
            <div
              key={stat.label}
              className="rounded-lg border border-gray-200 bg-white p-5"
            >
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-500">
                    {stat.label}
                  </p>
                  <p className="mt-1 text-2xl font-semibold text-gray-900">
                    {stat.value.toLocaleString()}
                  </p>
                </div>
                <div className={`rounded-lg p-2.5 ${stat.color}`}>
                  <Icon className="h-5 w-5" />
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="rounded-lg border border-gray-200 bg-white">
          <div className="border-b border-gray-200 px-5 py-4">
            <h2 className="text-sm font-semibold text-gray-900">
              Recent Packs
            </h2>
          </div>
          <div className="divide-y divide-gray-100">
            {packs.length === 0 ? (
              <div className="px-5 py-8 text-center text-sm text-gray-400">
                No packs yet. Create your first governance pack.
              </div>
            ) : (
              packs.slice(0, 5).map((pack) => (
                <div
                  key={pack.id}
                  className="flex items-center justify-between px-5 py-3"
                >
                  <div>
                    <p className="text-sm font-medium text-gray-900">
                      {pack.name}
                    </p>
                    <p className="text-xs text-gray-500">
                      v{pack.latestVersionNumber} &middot;{" "}
                      {pack.downloadCount} downloads
                    </p>
                  </div>
                  <span
                    className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
                      pack.status === "PUBLISHED"
                        ? "bg-green-50 text-green-700"
                        : pack.status === "DRAFT"
                          ? "bg-yellow-50 text-yellow-700"
                          : "bg-gray-100 text-gray-600"
                    }`}
                  >
                    {pack.status}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white">
          <div className="border-b border-gray-200 px-5 py-4">
            <h2 className="text-sm font-semibold text-gray-900">
              Active API Keys
            </h2>
          </div>
          <div className="divide-y divide-gray-100">
            {apiKeys.length === 0 ? (
              <div className="px-5 py-8 text-center text-sm text-gray-400">
                No API keys yet. Generate one to start.
              </div>
            ) : (
              apiKeys
                .filter((k) => k.active)
                .slice(0, 5)
                .map((key) => (
                  <div
                    key={key.id}
                    className="flex items-center justify-between px-5 py-3"
                  >
                    <div>
                      <p className="text-sm font-medium text-gray-900">
                        {key.tenantName}
                      </p>
                      <p className="font-mono text-xs text-gray-500">
                        {key.keyPrefix}...
                      </p>
                    </div>
                    <span className="inline-flex rounded-full bg-green-50 px-2 py-0.5 text-xs font-medium text-green-700">
                      Active
                    </span>
                  </div>
                ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
