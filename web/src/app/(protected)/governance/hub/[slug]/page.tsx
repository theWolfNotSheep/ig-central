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

type ComponentResult = {
    componentType: string; componentName: string;
    created: number; updated: number; skipped: number; failed: number;
    details: string[];
};

type ImportResult = {
    packSlug: string; packVersion: number; mode: string;
    components: ComponentResult[];
    totalCreated: number; totalUpdated: number; totalSkipped: number; totalFailed: number;
    errors: string[];
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

export default function PackDetailPage() {
    const { slug } = useParams<{ slug: string }>();
    const router = useRouter();
    const [pack, setPack] = useState<PackDetail | null>(null);
    const [versions, setVersions] = useState<PackVersion[]>([]);
    const [selectedVersion, setSelectedVersion] = useState<PackVersion | null>(null);
    const [loading, setLoading] = useState(true);
    const [importing, setImporting] = useState(false);
    const [previewing, setPreviewing] = useState(false);
    const [selectedComponents, setSelectedComponents] = useState<Set<string>>(new Set());
    const [importMode, setImportMode] = useState<"MERGE" | "OVERWRITE">("MERGE");

    // Preview / result state
    const [previewResult, setPreviewResult] = useState<ImportResult | null>(null);
    const [importResult, setImportResult] = useState<ImportResult | null>(null);
    const [showPreview, setShowPreview] = useState(false);
    const [showResult, setShowResult] = useState(false);

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
    }, [slug]);

    const toggleComponent = (type: string) => {
        setSelectedComponents(prev => {
            const next = new Set(prev);
            if (next.has(type)) next.delete(type); else next.add(type);
            return next;
        });
    };

    const handlePreview = async () => {
        if (!selectedVersion || selectedComponents.size === 0) return;
        setPreviewing(true);
        try {
            const { data } = await api.post("/admin/governance/import/preview", {
                packSlug: slug,
                versionNumber: selectedVersion.versionNumber,
                componentTypes: [...selectedComponents],
                mode: importMode,
            });
            setPreviewResult(data);
            setShowPreview(true);
        } catch {
            toast.error("Preview failed");
        } finally {
            setPreviewing(false);
        }
    };

    const handleImport = async () => {
        if (!selectedVersion || selectedComponents.size === 0) return;
        setImporting(true);
        setShowPreview(false);
        try {
            const { data } = await api.post("/admin/governance/import", {
                packSlug: slug,
                versionNumber: selectedVersion.versionNumber,
                componentTypes: [...selectedComponents],
                mode: importMode,
            });
            setImportResult(data);
            setShowResult(true);
            toast.success(`Imported ${data.totalCreated} new items, updated ${data.totalUpdated}`);
        } catch {
            toast.error("Import failed");
        } finally {
            setImporting(false);
        }
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

                    {/* Import mode selector */}
                    <div className="mt-4 flex items-center gap-3">
                        <label className="text-xs font-medium text-gray-600">Import mode:</label>
                        <select value={importMode} onChange={e => setImportMode(e.target.value as any)}
                            className="text-xs border border-gray-300 rounded px-2 py-1 bg-white">
                            <option value="MERGE">Merge (skip existing)</option>
                            <option value="OVERWRITE">Overwrite (replace existing)</option>
                        </select>
                    </div>

                    {/* Import button */}
                    <button onClick={handlePreview} disabled={previewing || importing || selectedComponents.size === 0}
                        className="mt-3 w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
                        {previewing ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
                        {previewing ? "Previewing..." : importing ? "Importing..." :
                            `Preview Import of ${selectedComponents.size} Component${selectedComponents.size !== 1 ? "s" : ""}`}
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
            </div>

            {/* Preview dialog */}
            {showPreview && previewResult && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
                    <div className="bg-white rounded-lg shadow-xl max-w-lg w-full max-h-[80vh] overflow-hidden flex flex-col">
                        <div className="p-5 border-b border-gray-200">
                            <h2 className="text-lg font-semibold text-gray-900">Import Preview</h2>
                            <p className="text-sm text-gray-500 mt-1">
                                {pack.name} v{previewResult.packVersion} — {importMode.toLowerCase()} mode
                            </p>
                        </div>

                        <div className="p-5 overflow-y-auto flex-1">
                            {/* Summary */}
                            <div className="grid grid-cols-4 gap-3 mb-5">
                                <SummaryBox icon={CheckCircle2} color="text-green-600 bg-green-50"
                                    label="New" count={previewResult.totalCreated} />
                                <SummaryBox icon={ArrowLeft} color="text-blue-600 bg-blue-50"
                                    label="Updated" count={previewResult.totalUpdated} />
                                <SummaryBox icon={SkipForward} color="text-gray-500 bg-gray-50"
                                    label="Skipped" count={previewResult.totalSkipped} />
                                <SummaryBox icon={XCircle} color="text-red-600 bg-red-50"
                                    label="Failed" count={previewResult.totalFailed} />
                            </div>

                            {/* Component breakdown */}
                            <div className="space-y-3">
                                {previewResult.components.map(comp => (
                                    <div key={comp.componentType} className="border border-gray-200 rounded-lg p-3">
                                        <div className="flex items-center justify-between mb-1">
                                            <span className="text-sm font-medium text-gray-900">{comp.componentName}</span>
                                            <span className="text-xs text-gray-400">{comp.componentType}</span>
                                        </div>
                                        <div className="flex gap-3 text-xs text-gray-500">
                                            {comp.created > 0 && <span className="text-green-600">{comp.created} new</span>}
                                            {comp.updated > 0 && <span className="text-blue-600">{comp.updated} updated</span>}
                                            {comp.skipped > 0 && <span className="text-gray-400">{comp.skipped} skipped</span>}
                                            {comp.failed > 0 && <span className="text-red-600">{comp.failed} failed</span>}
                                        </div>
                                        {comp.details.length > 0 && (
                                            <div className="mt-2 text-xs text-gray-400 space-y-0.5 max-h-24 overflow-y-auto">
                                                {comp.details.map((d, i) => <div key={i}>{d}</div>)}
                                            </div>
                                        )}
                                    </div>
                                ))}
                            </div>

                            {previewResult.errors.length > 0 && (
                                <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg">
                                    <div className="text-sm font-medium text-red-700 mb-1 flex items-center gap-1">
                                        <AlertTriangle className="size-4" /> Errors
                                    </div>
                                    {previewResult.errors.map((e, i) => (
                                        <div key={i} className="text-xs text-red-600">{e}</div>
                                    ))}
                                </div>
                            )}
                        </div>

                        <div className="p-4 border-t border-gray-200 flex gap-3 justify-end">
                            <button onClick={() => setShowPreview(false)}
                                className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">
                                Cancel
                            </button>
                            <button onClick={handleImport} disabled={importing}
                                className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 inline-flex items-center gap-2">
                                {importing && <Loader2 className="size-4 animate-spin" />}
                                Confirm Import
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Result dialog */}
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
                                <SummaryBox icon={CheckCircle2} color="text-green-600 bg-green-50"
                                    label="Created" count={importResult.totalCreated} />
                                <SummaryBox icon={ArrowLeft} color="text-blue-600 bg-blue-50"
                                    label="Updated" count={importResult.totalUpdated} />
                                <SummaryBox icon={SkipForward} color="text-gray-500 bg-gray-50"
                                    label="Skipped" count={importResult.totalSkipped} />
                                <SummaryBox icon={XCircle} color="text-red-600 bg-red-50"
                                    label="Failed" count={importResult.totalFailed} />
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
