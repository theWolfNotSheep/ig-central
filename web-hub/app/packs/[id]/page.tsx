"use client";

import { useEffect, useState, use } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, isAuthenticated } from "@/lib/api";
import {
  ArrowLeft,
  Plus,
  Loader2,
  X,
  Clock,
  Package,
  Trash2,
  ChevronDown,
  ChevronUp,
} from "lucide-react";

const COMPONENT_TYPES = [
  "TAXONOMY_CATEGORIES",
  "RETENTION_SCHEDULES",
  "SENSITIVITY_DEFINITIONS",
  "GOVERNANCE_POLICIES",
  "PII_TYPE_DEFINITIONS",
  "METADATA_SCHEMAS",
  "STORAGE_TIERS",
  "TRAIT_DEFINITIONS",
  "PIPELINE_BLOCKS",
] as const;

type ComponentType = (typeof COMPONENT_TYPES)[number];

interface PackComponent {
  type: ComponentType;
  name: string;
  description: string;
  itemCount: number;
  data: Record<string, unknown>[];
}

interface PackVersion {
  id: string;
  packId: string;
  versionNumber: number;
  changelog: string;
  publishedBy: string;
  publishedAt: string;
  components: PackComponent[];
  compatibilityVersion: string;
}

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
  publishedAt: string;
}

interface ComponentFormData {
  type: ComponentType;
  name: string;
  description: string;
  dataJson: string;
}

const emptyComponent: ComponentFormData = {
  type: "TAXONOMY_CATEGORIES",
  name: "",
  description: "",
  dataJson: "[]",
};

function formatType(type: string): string {
  return type
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export default function PackDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const router = useRouter();

  const [pack, setPack] = useState<GovernancePack | null>(null);
  const [versions, setVersions] = useState<PackVersion[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedVersion, setExpandedVersion] = useState<string | null>(null);

  // New version form
  const [showVersionForm, setShowVersionForm] = useState(false);
  const [changelog, setChangelog] = useState("");
  const [publishedBy, setPublishedBy] = useState("");
  const [components, setComponents] = useState<ComponentFormData[]>([
    { ...emptyComponent },
  ]);
  const [publishing, setPublishing] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!isAuthenticated()) {
      router.push("/");
      return;
    }
    loadData();
  }, [id, router]);

  async function loadData() {
    setLoading(true);
    try {
      const [packData, versionsData] = await Promise.all([
        api.get<GovernancePack>(`/api/hub/admin/packs/${id}`),
        api.get<PackVersion[]>(`/api/hub/admin/packs/${id}/versions`),
      ]);
      setPack(packData);
      setVersions(versionsData || []);
    } catch {
      // handled by api.ts
    } finally {
      setLoading(false);
    }
  }

  function addComponent() {
    setComponents([...components, { ...emptyComponent }]);
  }

  function removeComponent(index: number) {
    if (components.length <= 1) return;
    setComponents(components.filter((_, i) => i !== index));
  }

  function updateComponent(
    index: number,
    field: keyof ComponentFormData,
    value: string
  ) {
    const updated = [...components];
    updated[index] = { ...updated[index], [field]: value };
    setComponents(updated);
  }

  async function handlePublish(e: React.FormEvent) {
    e.preventDefault();
    setError("");

    // Validate JSON
    const parsedComponents: PackComponent[] = [];
    for (let i = 0; i < components.length; i++) {
      const comp = components[i];
      let data: Record<string, unknown>[];
      try {
        data = JSON.parse(comp.dataJson);
        if (!Array.isArray(data)) {
          setError(`Component ${i + 1}: data must be a JSON array`);
          return;
        }
      } catch {
        setError(`Component ${i + 1}: invalid JSON in data field`);
        return;
      }
      parsedComponents.push({
        type: comp.type,
        name: comp.name,
        description: comp.description,
        itemCount: data.length,
        data,
      });
    }

    setPublishing(true);
    try {
      await api.post(`/api/hub/admin/packs/${id}/versions`, {
        changelog,
        components: parsedComponents,
        publishedBy: publishedBy || "admin",
      });
      setShowVersionForm(false);
      setChangelog("");
      setPublishedBy("");
      setComponents([{ ...emptyComponent }]);
      await loadData();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to publish version"
      );
    } finally {
      setPublishing(false);
    }
  }

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
      </div>
    );
  }

  if (!pack) {
    return (
      <div className="p-8">
        <p className="text-sm text-gray-500">Pack not found.</p>
        <Link
          href="/packs"
          className="mt-2 inline-flex items-center gap-1 text-sm text-blue-600 hover:text-blue-800"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to packs
        </Link>
      </div>
    );
  }

  return (
    <div className="p-8">
      <Link
        href="/packs"
        className="mb-4 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to packs
      </Link>

      {/* Pack metadata */}
      <div className="mb-6 rounded-lg border border-gray-200 bg-white p-6">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold text-gray-900">
                {pack.name}
              </h1>
              <span
                className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${
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
            </div>
            {pack.description && (
              <p className="mt-2 max-w-2xl text-sm text-gray-600">
                {pack.description}
              </p>
            )}
          </div>
        </div>

        <div className="mt-4 grid grid-cols-2 gap-x-8 gap-y-2 text-sm md:grid-cols-4">
          <div>
            <span className="text-gray-500">Slug:</span>{" "}
            <span className="font-mono text-xs text-gray-700">
              {pack.slug}
            </span>
          </div>
          <div>
            <span className="text-gray-500">Jurisdiction:</span>{" "}
            <span className="text-gray-700">
              {pack.jurisdiction || "--"}
            </span>
          </div>
          <div>
            <span className="text-gray-500">Downloads:</span>{" "}
            <span className="text-gray-700">
              {(pack.downloadCount || 0).toLocaleString()}
            </span>
          </div>
          <div>
            <span className="text-gray-500">Rating:</span>{" "}
            <span className="text-gray-700">
              {pack.averageRating > 0
                ? `${pack.averageRating}/5 (${pack.reviewCount} reviews)`
                : "No reviews"}
            </span>
          </div>
          {pack.author && (
            <div className="col-span-2">
              <span className="text-gray-500">Author:</span>{" "}
              <span className="text-gray-700">
                {pack.author.name}
                {pack.author.organisation &&
                  ` (${pack.author.organisation})`}
              </span>
            </div>
          )}
        </div>

        {pack.regulations && pack.regulations.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-1.5">
            {pack.regulations.map((r) => (
              <span
                key={r}
                className="inline-flex rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-medium text-blue-700"
              >
                {r}
              </span>
            ))}
          </div>
        )}

        {pack.tags && pack.tags.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1.5">
            {pack.tags.map((t) => (
              <span
                key={t}
                className="inline-flex rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-600"
              >
                {t}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Versions */}
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-900">Versions</h2>
        <button
          onClick={() => {
            setShowVersionForm(true);
            setError("");
          }}
          className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-blue-700"
        >
          <Plus className="h-4 w-4" />
          Publish New Version
        </button>
      </div>

      {showVersionForm && (
        <div className="mb-6 rounded-lg border border-gray-200 bg-white p-6">
          <div className="mb-4 flex items-center justify-between">
            <h3 className="text-base font-semibold text-gray-900">
              Publish Version {(pack.latestVersionNumber || 0) + 1}
            </h3>
            <button
              onClick={() => setShowVersionForm(false)}
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

          <form onSubmit={handlePublish} className="space-y-5">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label
                  htmlFor="version-changelog"
                  className="mb-1 block text-sm font-medium text-gray-700"
                >
                  Changelog
                </label>
                <textarea
                  id="version-changelog"
                  rows={3}
                  required
                  value={changelog}
                  onChange={(e) => setChangelog(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="Describe what changed in this version..."
                />
              </div>
              <div>
                <label
                  htmlFor="version-published-by"
                  className="mb-1 block text-sm font-medium text-gray-700"
                >
                  Published By
                </label>
                <input
                  id="version-published-by"
                  type="text"
                  value={publishedBy}
                  onChange={(e) => setPublishedBy(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="admin"
                />
              </div>
            </div>

            <div>
              <div className="mb-3 flex items-center justify-between">
                <label className="block text-sm font-medium text-gray-700">
                  Components
                </label>
                <button
                  type="button"
                  onClick={addComponent}
                  className="flex items-center gap-1 text-xs font-medium text-blue-600 hover:text-blue-800"
                >
                  <Plus className="h-3.5 w-3.5" />
                  Add Component
                </button>
              </div>

              <div className="space-y-4">
                {components.map((comp, index) => (
                  <div
                    key={index}
                    className="rounded-md border border-gray-200 bg-gray-50 p-4"
                  >
                    <div className="mb-3 flex items-center justify-between">
                      <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">
                        Component {index + 1}
                      </span>
                      {components.length > 1 && (
                        <button
                          type="button"
                          onClick={() => removeComponent(index)}
                          className="rounded p-1 text-gray-400 hover:text-red-600"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      )}
                    </div>

                    <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                      <div>
                        <label
                          htmlFor={`comp-type-${index}`}
                          className="mb-1 block text-xs font-medium text-gray-600"
                        >
                          Type
                        </label>
                        <select
                          id={`comp-type-${index}`}
                          value={comp.type}
                          onChange={(e) =>
                            updateComponent(
                              index,
                              "type",
                              e.target.value
                            )
                          }
                          className="w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                        >
                          {COMPONENT_TYPES.map((t) => (
                            <option key={t} value={t}>
                              {formatType(t)}
                            </option>
                          ))}
                        </select>
                      </div>
                      <div>
                        <label
                          htmlFor={`comp-name-${index}`}
                          className="mb-1 block text-xs font-medium text-gray-600"
                        >
                          Name
                        </label>
                        <input
                          id={`comp-name-${index}`}
                          type="text"
                          required
                          value={comp.name}
                          onChange={(e) =>
                            updateComponent(
                              index,
                              "name",
                              e.target.value
                            )
                          }
                          className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                          placeholder="Component name"
                        />
                      </div>
                      <div>
                        <label
                          htmlFor={`comp-desc-${index}`}
                          className="mb-1 block text-xs font-medium text-gray-600"
                        >
                          Description
                        </label>
                        <input
                          id={`comp-desc-${index}`}
                          type="text"
                          value={comp.description}
                          onChange={(e) =>
                            updateComponent(
                              index,
                              "description",
                              e.target.value
                            )
                          }
                          className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                          placeholder="Optional description"
                        />
                      </div>
                    </div>

                    <div className="mt-3">
                      <label
                        htmlFor={`comp-data-${index}`}
                        className="mb-1 block text-xs font-medium text-gray-600"
                      >
                        Data (JSON array)
                      </label>
                      <textarea
                        id={`comp-data-${index}`}
                        rows={6}
                        required
                        value={comp.dataJson}
                        onChange={(e) =>
                          updateComponent(
                            index,
                            "dataJson",
                            e.target.value
                          )
                        }
                        className="w-full rounded-md border border-gray-300 px-3 py-2 font-mono text-xs shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                        placeholder='[{"key": "value"}]'
                      />
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setShowVersionForm(false)}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={publishing}
                className="flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-blue-700 disabled:opacity-60"
              >
                {publishing && (
                  <Loader2 className="h-4 w-4 animate-spin" />
                )}
                Publish Version
              </button>
            </div>
          </form>
        </div>
      )}

      {versions.length === 0 ? (
        <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
          <Package className="mx-auto h-10 w-10 text-gray-300" />
          <p className="mt-3 text-sm font-medium text-gray-500">
            No versions published yet
          </p>
          <p className="mt-1 text-sm text-gray-400">
            Publish your first version to make this pack available.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {versions.map((version) => {
            const isExpanded = expandedVersion === version.id;
            return (
              <div
                key={version.id}
                className="rounded-lg border border-gray-200 bg-white"
              >
                <button
                  onClick={() =>
                    setExpandedVersion(
                      isExpanded ? null : version.id
                    )
                  }
                  className="flex w-full items-center justify-between px-5 py-4 text-left"
                >
                  <div className="flex items-center gap-4">
                    <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-blue-50 text-sm font-semibold text-blue-700">
                      v{version.versionNumber}
                    </span>
                    <div>
                      <p className="text-sm font-medium text-gray-900">
                        {version.changelog || "No changelog"}
                      </p>
                      <div className="flex items-center gap-3 text-xs text-gray-500">
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3" />
                          {version.publishedAt
                            ? new Date(
                                version.publishedAt
                              ).toLocaleDateString("en-GB", {
                                day: "numeric",
                                month: "short",
                                year: "numeric",
                              })
                            : "--"}
                        </span>
                        <span>
                          by {version.publishedBy || "unknown"}
                        </span>
                        <span>
                          {version.components.length} component
                          {version.components.length !== 1 ? "s" : ""}
                        </span>
                      </div>
                    </div>
                  </div>
                  {isExpanded ? (
                    <ChevronUp className="h-4 w-4 text-gray-400" />
                  ) : (
                    <ChevronDown className="h-4 w-4 text-gray-400" />
                  )}
                </button>

                {isExpanded && (
                  <div className="border-t border-gray-100 px-5 py-4">
                    {version.components.length === 0 ? (
                      <p className="text-sm text-gray-400">
                        No components in this version.
                      </p>
                    ) : (
                      <div className="space-y-3">
                        {version.components.map((comp, ci) => (
                          <div
                            key={ci}
                            className="rounded-md border border-gray-100 bg-gray-50 p-3"
                          >
                            <div className="flex items-center justify-between">
                              <div>
                                <span className="inline-flex rounded bg-gray-200 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-gray-600">
                                  {formatType(comp.type)}
                                </span>
                                <p className="mt-1 text-sm font-medium text-gray-900">
                                  {comp.name}
                                </p>
                                {comp.description && (
                                  <p className="text-xs text-gray-500">
                                    {comp.description}
                                  </p>
                                )}
                              </div>
                              <span className="text-xs text-gray-500">
                                {comp.itemCount || comp.data?.length || 0}{" "}
                                items
                              </span>
                            </div>
                            {comp.data && comp.data.length > 0 && (
                              <pre className="mt-2 max-h-48 overflow-auto rounded bg-gray-900 p-3 text-xs text-gray-300">
                                {JSON.stringify(comp.data, null, 2)}
                              </pre>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
