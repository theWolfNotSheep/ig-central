"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, isAuthenticated } from "@/lib/api";
import {
  Package,
  Plus,
  Pencil,
  Loader2,
  X,
  ChevronRight,
} from "lucide-react";

interface PackAuthor {
  name: string;
  organisation: string;
  email: string;
  verified: boolean;
}

interface GovernancePack {
  id: string;
  name: string;
  slug: string;
  description: string;
  author: PackAuthor | null;
  jurisdiction: string;
  industries: string[];
  regulations: string[];
  tags: string[];
  status: string;
  featured: boolean;
  downloadCount: number;
  averageRating: number;
  reviewCount: number;
  latestVersionNumber: number;
  createdAt: string;
  updatedAt: string;
}

const emptyPack = {
  name: "",
  description: "",
  jurisdiction: "",
  regulations: [] as string[],
  tags: [] as string[],
  author: { name: "", organisation: "", email: "", verified: false },
};

export default function PacksPage() {
  const router = useRouter();
  const [packs, setPacks] = useState<GovernancePack[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState({ ...emptyPack });
  const [regulationInput, setRegulationInput] = useState("");
  const [tagInput, setTagInput] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!isAuthenticated()) {
      router.push("/");
      return;
    }
    loadPacks();
  }, [router]);

  async function loadPacks() {
    setLoading(true);
    try {
      const data = await api.get<GovernancePack[]>("/api/hub/admin/packs");
      setPacks(data || []);
    } catch {
      // handled by api.ts
    } finally {
      setLoading(false);
    }
  }

  function openNewForm() {
    setEditingId(null);
    setForm({ ...emptyPack });
    setRegulationInput("");
    setTagInput("");
    setError("");
    setShowForm(true);
  }

  function openEditForm(pack: GovernancePack) {
    setEditingId(pack.id);
    setForm({
      name: pack.name || "",
      description: pack.description || "",
      jurisdiction: pack.jurisdiction || "",
      regulations: pack.regulations || [],
      tags: pack.tags || [],
      author: pack.author || {
        name: "",
        organisation: "",
        email: "",
        verified: false,
      },
    });
    setRegulationInput("");
    setTagInput("");
    setError("");
    setShowForm(true);
  }

  function addRegulation() {
    const val = regulationInput.trim();
    if (val && !form.regulations.includes(val)) {
      setForm({ ...form, regulations: [...form.regulations, val] });
    }
    setRegulationInput("");
  }

  function removeRegulation(val: string) {
    setForm({
      ...form,
      regulations: form.regulations.filter((r) => r !== val),
    });
  }

  function addTag() {
    const val = tagInput.trim();
    if (val && !form.tags.includes(val)) {
      setForm({ ...form, tags: [...form.tags, val] });
    }
    setTagInput("");
  }

  function removeTag(val: string) {
    setForm({ ...form, tags: form.tags.filter((t) => t !== val) });
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setSaving(true);

    try {
      if (editingId) {
        await api.put(`/api/hub/admin/packs/${editingId}`, form);
      } else {
        await api.post("/api/hub/admin/packs", form);
      }
      setShowForm(false);
      await loadPacks();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save pack");
    } finally {
      setSaving(false);
    }
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
          <h1 className="text-2xl font-bold text-gray-900">
            Governance Packs
          </h1>
          <p className="mt-1 text-sm text-gray-500">
            Create and manage governance packs for distribution
          </p>
        </div>
        <button
          onClick={openNewForm}
          className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-blue-700"
        >
          <Plus className="h-4 w-4" />
          New Pack
        </button>
      </div>

      {showForm && (
        <div className="mb-6 rounded-lg border border-gray-200 bg-white p-6">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-gray-900">
              {editingId ? "Edit Pack" : "Create New Pack"}
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

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label
                  htmlFor="pack-name"
                  className="mb-1 block text-sm font-medium text-gray-700"
                >
                  Pack Name
                </label>
                <input
                  id="pack-name"
                  type="text"
                  required
                  value={form.name}
                  onChange={(e) =>
                    setForm({ ...form, name: e.target.value })
                  }
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="UK GDPR Compliance Pack"
                />
              </div>

              <div>
                <label
                  htmlFor="pack-jurisdiction"
                  className="mb-1 block text-sm font-medium text-gray-700"
                >
                  Jurisdiction
                </label>
                <input
                  id="pack-jurisdiction"
                  type="text"
                  value={form.jurisdiction}
                  onChange={(e) =>
                    setForm({ ...form, jurisdiction: e.target.value })
                  }
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="United Kingdom"
                />
              </div>
            </div>

            <div>
              <label
                htmlFor="pack-description"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                Description
              </label>
              <textarea
                id="pack-description"
                rows={3}
                value={form.description}
                onChange={(e) =>
                  setForm({ ...form, description: e.target.value })
                }
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="Describe what this governance pack provides..."
              />
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <div>
                <label
                  htmlFor="author-name"
                  className="mb-1 block text-sm font-medium text-gray-700"
                >
                  Author Name
                </label>
                <input
                  id="author-name"
                  type="text"
                  value={form.author.name}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      author: { ...form.author, name: e.target.value },
                    })
                  }
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>
              <div>
                <label
                  htmlFor="author-org"
                  className="mb-1 block text-sm font-medium text-gray-700"
                >
                  Organisation
                </label>
                <input
                  id="author-org"
                  type="text"
                  value={form.author.organisation}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      author: {
                        ...form.author,
                        organisation: e.target.value,
                      },
                    })
                  }
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>
              <div>
                <label
                  htmlFor="author-email"
                  className="mb-1 block text-sm font-medium text-gray-700"
                >
                  Email
                </label>
                <input
                  id="author-email"
                  type="email"
                  value={form.author.email}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      author: { ...form.author, email: e.target.value },
                    })
                  }
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>
            </div>

            <div>
              <label
                htmlFor="regulation-input"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                Regulations
              </label>
              <div className="flex gap-2">
                <input
                  id="regulation-input"
                  type="text"
                  value={regulationInput}
                  onChange={(e) => setRegulationInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault();
                      addRegulation();
                    }
                  }}
                  className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="e.g. UK GDPR"
                />
                <button
                  type="button"
                  onClick={addRegulation}
                  className="rounded-md border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                >
                  Add
                </button>
              </div>
              {form.regulations.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {form.regulations.map((r) => (
                    <span
                      key={r}
                      className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-medium text-blue-700"
                    >
                      {r}
                      <button
                        type="button"
                        onClick={() => removeRegulation(r)}
                        className="ml-0.5 hover:text-blue-900"
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>

            <div>
              <label
                htmlFor="tag-input"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                Tags
              </label>
              <div className="flex gap-2">
                <input
                  id="tag-input"
                  type="text"
                  value={tagInput}
                  onChange={(e) => setTagInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault();
                      addTag();
                    }
                  }}
                  className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="e.g. compliance, data-protection"
                />
                <button
                  type="button"
                  onClick={addTag}
                  className="rounded-md border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                >
                  Add
                </button>
              </div>
              {form.tags.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {form.tags.map((t) => (
                    <span
                      key={t}
                      className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-700"
                    >
                      {t}
                      <button
                        type="button"
                        onClick={() => removeTag(t)}
                        className="ml-0.5 hover:text-gray-900"
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </span>
                  ))}
                </div>
              )}
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
                disabled={saving}
                className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-blue-700 disabled:opacity-60"
              >
                {saving && <Loader2 className="h-4 w-4 animate-spin" />}
                {editingId ? "Update Pack" : "Create Pack"}
              </button>
            </div>
          </form>
        </div>
      )}

      {packs.length === 0 ? (
        <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
          <Package className="mx-auto h-10 w-10 text-gray-300" />
          <p className="mt-3 text-sm font-medium text-gray-500">
            No governance packs yet
          </p>
          <p className="mt-1 text-sm text-gray-400">
            Get started by creating your first pack.
          </p>
          <button
            onClick={openNewForm}
            className="mt-4 inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700"
          >
            <Plus className="h-4 w-4" />
            New Pack
          </button>
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Name
                </th>
                <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Jurisdiction
                </th>
                <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Status
                </th>
                <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Versions
                </th>
                <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Downloads
                </th>
                <th className="px-5 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {packs.map((pack) => (
                <tr key={pack.id} className="hover:bg-gray-50">
                  <td className="px-5 py-3.5">
                    <Link
                      href={`/packs/${pack.id}`}
                      className="flex items-center gap-1 text-sm font-medium text-blue-600 hover:text-blue-800"
                    >
                      {pack.name}
                      <ChevronRight className="h-3.5 w-3.5" />
                    </Link>
                    <p className="mt-0.5 text-xs text-gray-500">
                      {pack.slug}
                    </p>
                  </td>
                  <td className="px-5 py-3.5 text-sm text-gray-600">
                    {pack.jurisdiction || "--"}
                  </td>
                  <td className="px-5 py-3.5">
                    <span
                      className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
                        pack.status === "PUBLISHED"
                          ? "bg-green-50 text-green-700"
                          : pack.status === "DRAFT"
                            ? "bg-yellow-50 text-yellow-700"
                            : pack.status === "DEPRECATED"
                              ? "bg-red-50 text-red-700"
                              : "bg-gray-100 text-gray-600"
                      }`}
                    >
                      {pack.status}
                    </span>
                  </td>
                  <td className="px-5 py-3.5 text-sm text-gray-600">
                    {pack.latestVersionNumber || 0}
                  </td>
                  <td className="px-5 py-3.5 text-sm text-gray-600">
                    {(pack.downloadCount || 0).toLocaleString()}
                  </td>
                  <td className="px-5 py-3.5 text-right">
                    <button
                      onClick={() => openEditForm(pack)}
                      className="inline-flex items-center gap-1 rounded px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-100 hover:text-gray-900"
                    >
                      <Pencil className="h-3.5 w-3.5" />
                      Edit
                    </button>
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
