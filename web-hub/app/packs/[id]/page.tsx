"use client";

import { useCallback, useEffect, useState, use } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Loader2 } from "lucide-react";
import { api, isAuthenticated } from "@/lib/api";

import { GovernancePack, PackVersion, TABS, TabKey, WorkspaceData } from "./_lib/types";
import { loadWorkspace, buildComponents } from "./_lib/io";
import { PublishBar } from "./_components/PublishBar";

import { TaxonomyTab } from "./_tabs/TaxonomyTab";
import { LegislationTab } from "./_tabs/LegislationTab";
import { SensitivityTab } from "./_tabs/SensitivityTab";
import { RetentionTab } from "./_tabs/RetentionTab";
import { StorageTab } from "./_tabs/StorageTab";
import { MetadataTab } from "./_tabs/MetadataTab";
import { PiiTab } from "./_tabs/PiiTab";
import { TraitsTab } from "./_tabs/TraitsTab";
import { PoliciesTab } from "./_tabs/PoliciesTab";
import { VersionsTab } from "./_tabs/VersionsTab";

const EMPTY: WorkspaceData = {
    legislation: [], sensitivity: [], retention: [], storage: [], metadata: [],
    pii: [], traits: [], policies: [], taxonomy: [], preserved: [],
};

export default function PackWorkspacePage({ params }: { params: Promise<{ id: string }> }) {
    const { id } = use(params);
    const router = useRouter();

    const [pack, setPack] = useState<GovernancePack | null>(null);
    const [versions, setVersions] = useState<PackVersion[]>([]);
    const [data, setData] = useState<WorkspaceData>(EMPTY);
    const [loading, setLoading] = useState(true);
    const [activeTab, setActiveTab] = useState<TabKey>("taxonomy");
    const [dirty, setDirty] = useState(false);
    const [changelog, setChangelog] = useState("");
    const [publishing, setPublishing] = useState(false);
    const [error, setError] = useState("");

    const loadData = useCallback(async () => {
        setLoading(true);
        try {
            const [packData, versionsData] = await Promise.all([
                api.get<GovernancePack>(`/api/hub/admin/packs/${id}`),
                api.get<PackVersion[]>(`/api/hub/admin/packs/${id}/versions`),
            ]);
            setPack(packData);
            setVersions(versionsData || []);
            const latest = (versionsData || [])[0];
            setData(latest ? loadWorkspace(latest.components) : EMPTY);
            setDirty(false);
        } catch {
            // api.ts handles 401
        } finally {
            setLoading(false);
        }
    }, [id]);

    useEffect(() => {
        if (!isAuthenticated()) { router.push("/"); return; }
        loadData();
    }, [id, router, loadData]);

    // Generic update helper — sets a slice of WorkspaceData and marks dirty
    const update = <K extends keyof WorkspaceData>(key: K, value: WorkspaceData[K]) => {
        setData((prev) => ({ ...prev, [key]: value }));
        setDirty(true);
    };

    const handlePublish = async () => {
        if (!pack || !changelog.trim()) return;
        setError("");
        setPublishing(true);
        try {
            const components = buildComponents(pack.name, data);
            await api.post(`/api/hub/admin/packs/${pack.id}/versions`, {
                changelog,
                components,
                publishedBy: "admin",
            });
            setChangelog("");
            await loadData();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to publish");
        } finally {
            setPublishing(false);
        }
    };

    const handleExport = () => {
        if (!pack) return;
        const components = buildComponents(pack.name, data);
        const blob = new Blob([JSON.stringify({ pack, components }, null, 2)], { type: "application/json" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `${pack.slug}-draft.json`;
        a.click();
        URL.revokeObjectURL(url);
    };

    if (loading) {
        return <div className="flex h-full items-center justify-center"><Loader2 className="h-6 w-6 animate-spin text-gray-400" /></div>;
    }
    if (!pack) {
        return <div className="p-8 text-sm text-gray-500">Pack not found.</div>;
    }

    const tabCount = (key: TabKey): number => {
        switch (key) {
            case "taxonomy": return data.taxonomy.length;
            case "retention": return data.retention.length;
            case "legislation": return data.legislation.length;
            case "sensitivity": return data.sensitivity.length;
            case "metadata": return data.metadata.length;
            case "pii": return data.pii.length;
            case "policies": return data.policies.length;
            case "storage": return data.storage.length;
            case "traits": return data.traits.length;
            case "versions": return versions.length;
        }
    };

    return (
        <div className="p-6 max-w-7xl mx-auto">
            <Link href="/packs" className="mb-3 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
                <ArrowLeft className="h-4 w-4" /> Back to packs
            </Link>

            {/* Pack header */}
            <div className="mb-4 rounded-lg border border-gray-200 bg-white px-5 py-4">
                <div className="flex items-start justify-between">
                    <div>
                        <div className="flex items-center gap-3">
                            <h1 className="text-xl font-bold text-gray-900">{pack.name}</h1>
                            <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
                                pack.status === "PUBLISHED" ? "bg-green-50 text-green-700" :
                                pack.status === "DRAFT" ? "bg-yellow-50 text-yellow-700" :
                                pack.status === "DEPRECATED" ? "bg-red-50 text-red-700" : "bg-gray-100 text-gray-600"
                            }`}>{pack.status}</span>
                            {pack.jurisdiction && <span className="rounded bg-indigo-50 px-2 py-0.5 text-xs text-indigo-700">{pack.jurisdiction}</span>}
                        </div>
                        <p className="mt-1 text-xs text-gray-500">
                            <code className="font-mono">{pack.slug}</code> · {pack.downloadCount.toLocaleString()} downloads · v{pack.latestVersionNumber}
                        </p>
                    </div>
                </div>
            </div>

            {/* Publish bar */}
            <PublishBar
                dirty={dirty}
                nextVersion={(pack.latestVersionNumber || 0) + 1}
                changelog={changelog}
                onChangelog={setChangelog}
                onPublish={handlePublish}
                onExport={handleExport}
                publishing={publishing}
                error={error}
            />

            {/* Tab strip */}
            <div className="mb-4 border-b border-gray-200">
                <nav className="flex gap-1 overflow-x-auto">
                    {TABS.map((tab) => {
                        const active = activeTab === tab.key;
                        const count = tabCount(tab.key);
                        return (
                            <button key={tab.key} onClick={() => setActiveTab(tab.key)}
                                className={`flex items-center gap-1.5 whitespace-nowrap px-3 py-2 text-sm border-b-2 transition-colors ${
                                    active ? "border-blue-600 text-blue-700 font-medium" : "border-transparent text-gray-600 hover:text-gray-900"
                                }`}>
                                {tab.label}
                                <span className={`rounded-full px-1.5 py-0 text-[10px] font-medium ${active ? "bg-blue-100 text-blue-700" : "bg-gray-100 text-gray-500"}`}>{count}</span>
                            </button>
                        );
                    })}
                </nav>
            </div>

            {/* Tab content */}
            <div>
                {activeTab === "versions" && <VersionsTab versions={versions} />}
                {activeTab === "taxonomy" && (
                    <TaxonomyTab
                        items={data.taxonomy}
                        retention={data.retention}
                        metadata={data.metadata}
                        sensitivity={data.sensitivity}
                        legislation={data.legislation}
                        packJurisdiction={pack.jurisdiction || "UK"}
                        onChange={(v) => update("taxonomy", v)}
                    />
                )}
                {activeTab === "retention" && (
                    <RetentionTab items={data.retention} legislation={data.legislation} onChange={(v) => update("retention", v)} />
                )}
                {activeTab === "legislation" && (
                    <LegislationTab items={data.legislation} onChange={(v) => update("legislation", v)} />
                )}
                {activeTab === "sensitivity" && (
                    <SensitivityTab items={data.sensitivity} legislation={data.legislation} onChange={(v) => update("sensitivity", v)} />
                )}
                {activeTab === "metadata" && (
                    <MetadataTab items={data.metadata} onChange={(v) => update("metadata", v)} />
                )}
                {activeTab === "pii" && (
                    <PiiTab items={data.pii} onChange={(v) => update("pii", v)} />
                )}
                {activeTab === "policies" && (
                    <PoliciesTab items={data.policies} sensitivity={data.sensitivity} legislation={data.legislation} onChange={(v) => update("policies", v)} />
                )}
                {activeTab === "storage" && (
                    <StorageTab items={data.storage} onChange={(v) => update("storage", v)} />
                )}
                {activeTab === "traits" && (
                    <TraitsTab items={data.traits} onChange={(v) => update("traits", v)} />
                )}
            </div>
        </div>
    );
}
