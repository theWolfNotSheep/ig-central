"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import {
    ArrowLeft, Download, Globe, Building, BookOpen, Star,
    ChevronDown, ChevronRight, Loader2, Check, Package,
    FileText, Clock, Shield, Brain, Scan, Database, Tag,
    Scale, AlertTriangle, CheckCircle2, XCircle, SkipForward,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

type PackDetail = {
    id: string; slug: string; name: string; description: string;
    author: { name: string; organisation: string; verified: boolean };
    jurisdiction: string; industries: string[]; regulations: string[];
    tags: string[]; downloadCount: number; latestVersionNumber: number;
};

type PackVersion = {
    id: string; versionNumber: number; changelog: string;
    publishedBy: string; publishedAt: string;
    components: { type: string; name: string; description: string; itemCount: number }[];
};

type ImportResult = {
    packSlug: string; packVersion: number; mode: string;
    components: { componentType: string; componentName: string; created: number; updated: number; skipped: number; failed: number; details: string[] }[];
    totalCreated: number; totalUpdated: number; totalSkipped: number; totalFailed: number;
    errors: string[];
};

type ImportHistoryEntry = {
    id: string;
    packSlug: string;
    version: number;
    importedAt: string;
    importedBy: string;
    mode: string;
    componentTypes: string[];
    selectedItemKeys?: string[];
    totalCreated: number;
    totalUpdated: number;
    totalSkipped: number;
    totalFailed: number;
};

// Diff types
type FieldDiff = { field: string; currentValue: any; hubValue: any };
type ItemDiff = {
    componentType: string; itemKey: string; displayName: string;
    status: "NEW" | "CHANGED" | "UNCHANGED" | "CONFLICT";
    hubChanges: FieldDiff[]; localChanges: FieldDiff[];
};
type ComponentDiff = {
    componentType: string; componentName: string; items: ItemDiff[];
    newCount: number; changedCount: number; conflictCount: number; unchangedCount: number;
};
type PackDiffResult = {
    packSlug: string; currentVersion: number; newVersion: number;
    components: ComponentDiff[];
    totalNew: number; totalChanged: number; totalConflicts: number; totalUnchanged: number;
};

const COMPONENT_ICONS: Record<string, { icon: typeof FileText; color: string }> = {
    TAXONOMY_CATEGORIES: { icon: FileText, color: "text-blue-500" },
    RETENTION_SCHEDULES: { icon: Clock, color: "text-amber-500" },
    SENSITIVITY_DEFINITIONS: { icon: Shield, color: "text-red-500" },
    GOVERNANCE_POLICIES: { icon: BookOpen, color: "text-green-500" },
    PII_TYPE_DEFINITIONS: { icon: Scan, color: "text-purple-500" },
    METADATA_SCHEMAS: { icon: Database, color: "text-indigo-500" },
    STORAGE_TIERS: { icon: Database, color: "text-gray-500" },
    TRAIT_DEFINITIONS: { icon: Tag, color: "text-teal-500" },
    PIPELINE_BLOCKS: { icon: Brain, color: "text-pink-500" },
    LEGISLATION: { icon: Scale, color: "text-orange-500" },
};

const STATUS_STYLES: Record<string, { bg: string; text: string; label: string }> = {
    NEW: { bg: "bg-green-100", text: "text-green-700", label: "New" },
    CHANGED: { bg: "bg-blue-100", text: "text-blue-700", label: "Changed" },
    CONFLICT: { bg: "bg-amber-100", text: "text-amber-700", label: "Conflict" },
    UNCHANGED: { bg: "bg-gray-100", text: "text-gray-500", label: "Unchanged" },
};

export default function PackDetailPage() {
    const { slug } = useParams<{ slug: string }>();
    const router = useRouter();
    const [pack, setPack] = useState<PackDetail | null>(null);
    const [versions, setVersions] = useState<PackVersion[]>([]);
    const [selectedVersion, setSelectedVersion] = useState<PackVersion | null>(null);
    const [loading, setLoading] = useState(true);
    const [importing, setImporting] = useState(false);
    const [diffing, setDiffing] = useState(false);
    const [selectedComponents, setSelectedComponents] = useState<Set<string>>(new Set());

    // Diff state
    const [diffResult, setDiffResult] = useState<PackDiffResult | null>(null);
    const [showDiff, setShowDiff] = useState(false);
    const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set());
    const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());
    const [showUnchanged, setShowUnchanged] = useState(false);

    // Result state
    const [importResult, setImportResult] = useState<ImportResult | null>(null);
    const [showResult, setShowResult] = useState(false);
    const [importHistory, setImportHistory] = useState<ImportHistoryEntry[]>([]);

    const loadImportHistory = useCallback(async () => {
        try {
            const { data } = await api.get<ImportHistoryEntry[]>(`/admin/governance/import/history/${slug}`);
            setImportHistory(data ?? []);
        } catch {
            // Non-fatal — first-time pack views won't have history yet.
            setImportHistory([]);
        }
    }, [slug]);

    useEffect(() => {
        Promise.all([
            api.get(`/admin/governance-hub/packs/${slug}`),
            api.get(`/admin/governance-hub/packs/${slug}/versions`),
        ]).then(([packRes, versionsRes]) => {
            setPack(packRes.data);
            const vers = versionsRes.data;
            setVersions(vers);
            if (vers.length > 0) {
                setSelectedVersion(vers[0]);
                setSelectedComponents(new Set(vers[0].components.map((c: any) => c.type)));
            }
        }).catch(() => toast.error("Failed to load pack"))
          .finally(() => setLoading(false));
        loadImportHistory();
    }, [slug, loadImportHistory]);

    const toggleComponent = (type: string) => {
        setSelectedComponents(prev => {
            const next = new Set(prev);
            if (next.has(type)) next.delete(type); else next.add(type);
            return next;
        });
    };

    const handleDiff = async () => {
        if (!selectedVersion || selectedComponents.size === 0) return;
        setDiffing(true);
        try {
            const { data } = await api.post("/admin/governance/import/diff", {
                packSlug: slug,
                versionNumber: selectedVersion.versionNumber,
                componentTypes: [...selectedComponents],
            });
            setDiffResult(data);
            // Auto-select NEW and CHANGED, leave CONFLICT unchecked
            const autoSelected = new Set<string>();
            for (const comp of data.components) {
                for (const item of comp.items) {
                    const key = `${item.componentType}::${item.itemKey}`;
                    if (item.status === "NEW" || item.status === "CHANGED") {
                        autoSelected.add(key);
                    }
                }
            }
            setSelectedItems(autoSelected);
            setExpandedItems(new Set());
            setShowDiff(true);
        } catch {
            toast.error("Diff failed");
        } finally {
            setDiffing(false);
        }
    };

    const handleSelectiveImport = async () => {
        if (!selectedVersion || selectedItems.size === 0) return;
        setImporting(true);
        try {
            const items = [...selectedItems].map(key => {
                const [componentType, itemKey] = key.split("::");
                return { componentType, itemKey };
            });
            const { data } = await api.post("/admin/governance/import/selective", {
                packSlug: slug,
                versionNumber: selectedVersion.versionNumber,
                componentTypes: [...selectedComponents],
                selectedItems: items,
            });
            setImportResult(data);
            setShowDiff(false);
            setShowResult(true);
            toast.success(`Applied ${data.totalCreated + data.totalUpdated} changes`);
            loadImportHistory();
        } catch {
            toast.error("Import failed");
        } finally {
            setImporting(false);
        }
    };

    /**
     * Re-import a previous version. Maps to "rollback" in the §3.9 plan —
     * the existing import endpoint accepts any version number, so a
     * rollback is just an import of the prior version. Selects every
     * component the old import touched so the pre-flight diff catches
     * everything that would change.
     */
    const handleReimportVersion = async (entry: ImportHistoryEntry) => {
        const targetVersion = versions.find(v => v.versionNumber === entry.version);
        if (!targetVersion) {
            toast.error(`v${entry.version} no longer in the hub catalogue — can't re-import`);
            return;
        }
        if (!confirm(`Re-import ${slug} v${entry.version}? This will run the diff preview first so you can review changes before they land.`)) return;
        setSelectedVersion(targetVersion);
        setSelectedComponents(new Set(entry.componentTypes));
        // Defer to the existing diff flow; user clicks Import after reviewing.
        setTimeout(() => {
            void handleDiff();
            toast.info(`Selected v${entry.version} — review the diff and click Import to apply.`);
        }, 0);
    };

    const toggleItem = (key: string) => {
        setSelectedItems(prev => {
            const next = new Set(prev);
            if (next.has(key)) next.delete(key); else next.add(key);
            return next;
        });
    };

    const toggleExpanded = (key: string) => {
        setExpandedItems(prev => {
            const next = new Set(prev);
            if (next.has(key)) next.delete(key); else next.add(key);
            return next;
        });
    };

    if (loading) return <div className="text-center py-12"><Loader2 className="size-8 animate-spin text-gray-300 mx-auto" /></div>;
    if (!pack) return <div className="text-center py-12 text-gray-400">Pack not found</div>;

    return (
        <div className="max-w-4xl mx-auto">
            <button onClick={() => router.push("/governance/hub")}
                className="text-sm text-blue-600 hover:text-blue-800 mb-4 flex items-center gap-1">
                <ArrowLeft className="size-4" /> Back to Hub
            </button>

            {/* Pack header */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
                <div className="flex items-start justify-between gap-4">
                    <div>
                        <h1 className="text-xl font-bold text-gray-900 mb-1">{pack.name}</h1>
                        <div className="flex items-center gap-3 text-sm text-gray-500 mb-3">
                            <span className="flex items-center gap-1"><Globe className="size-3.5" /> {pack.jurisdiction}</span>
                            <span className="flex items-center gap-1"><Building className="size-3.5" /> {pack.industries.join(", ")}</span>
                            <span className="flex items-center gap-1"><Download className="size-3.5" /> {pack.downloadCount} downloads</span>
                            {pack.author.verified && <span className="flex items-center gap-1 text-green-600"><Star className="size-3.5" /> Verified</span>}
                        </div>
                        <p className="text-sm text-gray-600 leading-relaxed">{pack.description}</p>
                        <div className="flex flex-wrap gap-1 mt-3">
                            {pack.regulations.map(r => (
                                <span key={r} className="px-2 py-0.5 text-xs bg-blue-50 text-blue-600 rounded">{r}</span>
                            ))}
                        </div>
                    </div>
                    <div className="text-right shrink-0">
                        <div className="text-xs text-gray-400">by {pack.author.name}</div>
                        <div className="text-xs text-gray-400">{pack.author.organisation}</div>
                    </div>
                </div>
            </div>

            <div className="grid grid-cols-3 gap-6">
                {/* Components */}
                <div className="col-span-2">
                    <h3 className="text-sm font-semibold text-gray-900 mb-3">
                        Components {selectedVersion && `(v${selectedVersion.versionNumber})`}
                    </h3>
                    {selectedVersion && (
                        <div className="space-y-2">
                            {selectedVersion.components.map(comp => {
                                const ci = COMPONENT_ICONS[comp.type] ?? { icon: Package, color: "text-gray-500" };
                                const Icon = ci.icon;
                                const checked = selectedComponents.has(comp.type);
                                return (
                                    <label key={comp.type}
                                        className={`flex items-start gap-3 p-4 rounded-lg border cursor-pointer transition-colors ${
                                            checked ? "bg-blue-50 border-blue-200" : "bg-white border-gray-200 hover:bg-gray-50"
                                        }`}>
                                        <input type="checkbox" checked={checked} onChange={() => toggleComponent(comp.type)}
                                            className="rounded border-gray-300 text-blue-600 mt-0.5" />
                                        <Icon className={`size-5 ${ci.color} shrink-0 mt-0.5`} />
                                        <div className="flex-1 min-w-0">
                                            <div className="text-sm font-medium text-gray-900">{comp.name}</div>
                                            <div className="text-xs text-gray-500 mt-0.5">{comp.description}</div>
                                            <div className="text-[10px] text-gray-400 mt-1">{comp.itemCount} items</div>
                                        </div>
                                    </label>
                                );
                            })}
                        </div>
                    )}

                    <button onClick={handleDiff} disabled={diffing || importing || selectedComponents.size === 0}
                        className="mt-3 w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
                        {diffing ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
                        {diffing ? "Computing diff..." : `Review Changes for ${selectedComponents.size} Component${selectedComponents.size !== 1 ? "s" : ""}`}
                    </button>
                </div>

                {/* Version history */}
                <div>
                    <h3 className="text-sm font-semibold text-gray-900 mb-3">Versions</h3>
                    <div className="space-y-2">
                        {versions.map(v => (
                            <button key={v.id} onClick={() => {
                                setSelectedVersion(v);
                                setSelectedComponents(new Set(v.components.map(c => c.type)));
                            }}
                                className={`w-full text-left p-3 rounded-lg border transition-colors ${
                                    selectedVersion?.id === v.id ? "bg-blue-50 border-blue-200" : "bg-white border-gray-200 hover:bg-gray-50"
                                }`}>
                                <div className="flex items-center justify-between mb-1">
                                    <span className="text-sm font-medium text-gray-900">v{v.versionNumber}</span>
                                    {selectedVersion?.id === v.id && <Check className="size-3.5 text-blue-600" />}
                                </div>
                                <p className="text-xs text-gray-500 line-clamp-2">{v.changelog}</p>
                                <div className="text-[10px] text-gray-400 mt-1">
                                    {v.publishedBy} — {new Date(v.publishedAt).toLocaleDateString()}
                                </div>
                            </button>
                        ))}
                    </div>
                </div>

                {/* Local import history */}
                {importHistory.length > 0 && (
                    <div>
                        <h3 className="text-sm font-semibold text-gray-900 mb-3 flex items-center gap-2">
                            <Clock className="size-4 text-gray-500" />
                            Your import history
                            <span className="text-xs font-normal text-gray-400">({importHistory.length})</span>
                        </h3>
                        <p className="text-xs text-gray-500 mb-2">
                            Every time this pack landed locally. Click <em>Re-import</em> to roll back or refresh — the diff preview runs first.
                        </p>
                        <div className="space-y-2">
                            {importHistory.map(entry => (
                                <div key={entry.id}
                                    className="p-3 rounded-lg border border-gray-200 bg-white hover:bg-gray-50 transition-colors">
                                    <div className="flex items-center justify-between mb-1 gap-2">
                                        <div className="flex items-center gap-2 min-w-0">
                                            <span className="text-sm font-medium text-gray-900">v{entry.version}</span>
                                            <span className={`px-1.5 py-0.5 text-[10px] font-medium rounded ${
                                                entry.mode === "OVERWRITE" ? "bg-amber-100 text-amber-700"
                                                : entry.mode === "SELECTIVE" ? "bg-purple-100 text-purple-700"
                                                : "bg-gray-100 text-gray-600"
                                            }`}>{entry.mode}</span>
                                        </div>
                                        <button onClick={() => handleReimportVersion(entry)}
                                            disabled={diffing || importing}
                                            className="text-xs px-2 py-1 border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50 shrink-0">
                                            Re-import
                                        </button>
                                    </div>
                                    <div className="text-[10px] text-gray-500 flex flex-wrap items-center gap-x-2 gap-y-0.5">
                                        <span>{entry.importedBy}</span>
                                        <span>·</span>
                                        <span>{formatHistoryDate(entry.importedAt)}</span>
                                        {(entry.totalCreated > 0 || entry.totalUpdated > 0) && (
                                            <>
                                                <span>·</span>
                                                <span>{entry.totalCreated} created · {entry.totalUpdated} updated</span>
                                            </>
                                        )}
                                    </div>
                                    {entry.componentTypes.length > 0 && (
                                        <div className="mt-1 flex flex-wrap gap-1">
                                            {entry.componentTypes.slice(0, 4).map(t => (
                                                <span key={t} className="px-1.5 py-0.5 text-[10px] bg-gray-100 text-gray-600 rounded">
                                                    {t.toLowerCase().replace(/_/g, " ")}
                                                </span>
                                            ))}
                                            {entry.componentTypes.length > 4 && (
                                                <span className="text-[10px] text-gray-400">
                                                    +{entry.componentTypes.length - 4} more
                                                </span>
                                            )}
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>

            {/* ── Diff dialog ─────────────────────────── */}
            {showDiff && diffResult && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-lg shadow-xl max-w-3xl w-full max-h-[85vh] overflow-hidden flex flex-col">
                        <div className="p-5 border-b border-gray-200">
                            <h2 className="text-lg font-semibold text-gray-900">Review Changes</h2>
                            <p className="text-sm text-gray-500 mt-1">
                                {pack.name} — v{diffResult.currentVersion} → v{diffResult.newVersion}
                            </p>

                            {/* Summary bar */}
                            <div className="flex gap-3 mt-3">
                                {diffResult.totalNew > 0 && (
                                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium bg-green-100 text-green-700 rounded-full">
                                        <CheckCircle2 className="size-3" /> {diffResult.totalNew} new
                                    </span>
                                )}
                                {diffResult.totalChanged > 0 && (
                                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium bg-blue-100 text-blue-700 rounded-full">
                                        {diffResult.totalChanged} changed
                                    </span>
                                )}
                                {diffResult.totalConflicts > 0 && (
                                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium bg-amber-100 text-amber-700 rounded-full">
                                        <AlertTriangle className="size-3" /> {diffResult.totalConflicts} conflict{diffResult.totalConflicts !== 1 ? "s" : ""}
                                    </span>
                                )}
                                {diffResult.totalUnchanged > 0 && (
                                    <button onClick={() => setShowUnchanged(!showUnchanged)}
                                        className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium bg-gray-100 text-gray-500 rounded-full hover:bg-gray-200">
                                        {diffResult.totalUnchanged} unchanged {showUnchanged ? "(hide)" : "(show)"}
                                    </button>
                                )}
                            </div>
                        </div>

                        <div className="flex-1 overflow-y-auto p-5 space-y-4">
                            {diffResult.components.map(comp => {
                                const ci = COMPONENT_ICONS[comp.componentType] ?? { icon: Package, color: "text-gray-500" };
                                const Icon = ci.icon;
                                const visibleItems = showUnchanged ? comp.items : comp.items.filter(i => i.status !== "UNCHANGED");
                                if (visibleItems.length === 0) return null;

                                return (
                                    <div key={comp.componentType}>
                                        <div className="flex items-center gap-2 mb-2">
                                            <Icon className={`size-4 ${ci.color}`} />
                                            <span className="text-sm font-semibold text-gray-900">{comp.componentName}</span>
                                            <span className="text-xs text-gray-400">{visibleItems.length} item{visibleItems.length !== 1 ? "s" : ""}</span>
                                        </div>

                                        <div className="space-y-1">
                                            {visibleItems.map(item => {
                                                const key = `${item.componentType}::${item.itemKey}`;
                                                const checked = selectedItems.has(key);
                                                const expanded = expandedItems.has(key);
                                                const style = STATUS_STYLES[item.status];
                                                const hasDetails = item.hubChanges.length > 0 || item.localChanges.length > 0;

                                                return (
                                                    <div key={key} className={`rounded-lg border ${
                                                        item.status === "CONFLICT" ? "border-amber-200" : "border-gray-200"
                                                    }`}>
                                                        <div className="flex items-center gap-2 px-3 py-2">
                                                            {item.status !== "UNCHANGED" && (
                                                                <input type="checkbox" checked={checked}
                                                                    onChange={() => toggleItem(key)}
                                                                    className="rounded border-gray-300 text-blue-600 shrink-0" />
                                                            )}
                                                            {hasDetails && (
                                                                <button onClick={() => toggleExpanded(key)} className="shrink-0">
                                                                    {expanded ? <ChevronDown className="size-3.5 text-gray-400" /> : <ChevronRight className="size-3.5 text-gray-400" />}
                                                                </button>
                                                            )}
                                                            <span className="text-sm text-gray-900 flex-1">{item.displayName}</span>
                                                            <span className={`px-2 py-0.5 text-[10px] font-medium rounded-full ${style.bg} ${style.text}`}>
                                                                {style.label}
                                                            </span>
                                                        </div>

                                                        {/* Field-level diff */}
                                                        {expanded && hasDetails && (
                                                            <div className="px-3 pb-3 pt-1 border-t border-gray-100">
                                                                {item.status === "CONFLICT" && item.localChanges.length > 0 && (
                                                                    <div className="mb-2 px-2 py-1.5 bg-amber-50 rounded text-xs text-amber-700">
                                                                        <strong>Local changes:</strong> {item.localChanges.map(c => c.field).join(", ")}
                                                                    </div>
                                                                )}
                                                                <table className="w-full text-xs">
                                                                    <thead>
                                                                        <tr className="text-gray-500">
                                                                            <th className="text-left py-1 pr-2 font-medium w-1/4">Field</th>
                                                                            <th className="text-left py-1 pr-2 font-medium w-[37.5%]">Current</th>
                                                                            <th className="text-left py-1 font-medium w-[37.5%]">Hub</th>
                                                                        </tr>
                                                                    </thead>
                                                                    <tbody className="divide-y divide-gray-50">
                                                                        {item.hubChanges.map(diff => {
                                                                            const isConflictField = item.localChanges.some(lc => lc.field === diff.field);
                                                                            return (
                                                                                <tr key={diff.field} className={isConflictField ? "bg-amber-50/50" : ""}>
                                                                                    <td className="py-1.5 pr-2 font-medium text-gray-700">
                                                                                        {diff.field}
                                                                                        {isConflictField && <span className="ml-1 text-amber-600">!</span>}
                                                                                    </td>
                                                                                    <td className="py-1.5 pr-2 text-gray-500 break-all">
                                                                                        {formatValue(diff.currentValue)}
                                                                                    </td>
                                                                                    <td className="py-1.5 text-gray-900 break-all">
                                                                                        {formatValue(diff.hubValue)}
                                                                                    </td>
                                                                                </tr>
                                                                            );
                                                                        })}
                                                                    </tbody>
                                                                </table>
                                                            </div>
                                                        )}
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>

                        <div className="p-4 border-t border-gray-200 flex items-center justify-between">
                            <span className="text-xs text-gray-500">{selectedItems.size} item{selectedItems.size !== 1 ? "s" : ""} selected</span>
                            <div className="flex gap-3">
                                <button onClick={() => setShowDiff(false)}
                                    className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">
                                    Cancel
                                </button>
                                <button onClick={handleSelectiveImport} disabled={importing || selectedItems.size === 0}
                                    className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 inline-flex items-center gap-2">
                                    {importing && <Loader2 className="size-4 animate-spin" />}
                                    Apply {selectedItems.size} Selected
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* ── Result dialog ────────────────────────── */}
            {showResult && importResult && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-lg shadow-xl max-w-lg w-full max-h-[80vh] overflow-hidden flex flex-col">
                        <div className="p-5 border-b border-gray-200">
                            <h2 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
                                <CheckCircle2 className="size-5 text-green-600" /> Import Complete
                            </h2>
                        </div>
                        <div className="p-5 overflow-y-auto flex-1">
                            <div className="grid grid-cols-4 gap-3 mb-5">
                                <SummaryBox icon={CheckCircle2} color="text-green-600 bg-green-50" label="Created" count={importResult.totalCreated} />
                                <SummaryBox icon={ArrowLeft} color="text-blue-600 bg-blue-50" label="Updated" count={importResult.totalUpdated} />
                                <SummaryBox icon={SkipForward} color="text-gray-500 bg-gray-50" label="Skipped" count={importResult.totalSkipped} />
                                <SummaryBox icon={XCircle} color="text-red-600 bg-red-50" label="Failed" count={importResult.totalFailed} />
                            </div>
                            <div className="space-y-3">
                                {importResult.components.map(comp => (
                                    <div key={comp.componentType} className="border border-gray-200 rounded-lg p-3">
                                        <span className="text-sm font-medium text-gray-900">{comp.componentName}</span>
                                        <div className="flex gap-3 text-xs text-gray-500 mt-1">
                                            {comp.created > 0 && <span className="text-green-600">{comp.created} created</span>}
                                            {comp.updated > 0 && <span className="text-blue-600">{comp.updated} updated</span>}
                                            {comp.skipped > 0 && <span className="text-gray-400">{comp.skipped} skipped</span>}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                        <div className="p-4 border-t border-gray-200 flex gap-3 justify-end">
                            <button onClick={() => router.push("/governance")}
                                className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">
                                View Governance
                            </button>
                            <button onClick={() => setShowResult(false)}
                                className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700">
                                Done
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

function SummaryBox({ icon: Icon, color, label, count }: {
    icon: typeof CheckCircle2; color: string; label: string; count: number;
}) {
    return (
        <div className={`rounded-lg p-3 text-center ${color.split(" ")[1]}`}>
            <Icon className={`size-5 mx-auto mb-1 ${color.split(" ")[0]}`} />
            <div className={`text-lg font-bold ${color.split(" ")[0]}`}>{count}</div>
            <div className="text-[10px] text-gray-500">{label}</div>
        </div>
    );
}

function formatValue(val: any): string {
    if (val === null || val === undefined) return "—";
    if (Array.isArray(val)) return val.length === 0 ? "[]" : val.join(", ");
    if (typeof val === "object") return JSON.stringify(val);
    if (typeof val === "boolean") return val ? "Yes" : "No";
    return String(val);
}

function formatHistoryDate(iso: string): string {
    const t = new Date(iso).getTime();
    if (Number.isNaN(t)) return iso;
    const delta = (Date.now() - t) / 1000;
    if (delta < 60) return `${Math.round(delta)}s ago`;
    if (delta < 3600) return `${Math.round(delta / 60)}m ago`;
    if (delta < 86400) return `${Math.round(delta / 3600)}h ago`;
    if (delta < 86400 * 30) return `${Math.round(delta / 86400)}d ago`;
    return new Date(iso).toLocaleDateString();
}
