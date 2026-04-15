"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
    Search, Download, Star, Globe, Building, BookOpen,
    Loader2, AlertTriangle, Package, ChevronRight, Filter,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

type Pack = {
    id: string; slug: string; name: string; description: string;
    author: { name: string; organisation: string; verified: boolean };
    jurisdiction: string; industries: string[]; regulations: string[];
    tags: string[]; featured: boolean;
    downloadCount: number; averageRating: number; reviewCount: number;
    latestVersionNumber: number;
};

type PageResult = { content: Pack[]; totalElements: number; totalPages: number; number: number };

export default function GovernanceHubPage() {
    const [configured, setConfigured] = useState<boolean | null>(null);
    const [results, setResults] = useState<PageResult | null>(null);
    const [loading, setLoading] = useState(false);
    const [query, setQuery] = useState("");
    const [jurisdiction, setJurisdiction] = useState("");
    const [page, setPage] = useState(0);

    useEffect(() => {
        api.get("/admin/governance-hub/status").then(({ data }) => setConfigured(data.configured)).catch(() => setConfigured(false));
    }, []);

    const search = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            params.set("page", String(page));
            params.set("size", "20");
            if (query) params.set("q", query);
            if (jurisdiction) params.set("jurisdiction", jurisdiction);
            const { data } = await api.get(`/admin/governance-hub/packs?${params}`);
            setResults(data);
        } catch { toast.error("Failed to load packs from Governance Hub"); }
        finally { setLoading(false); }
    }, [query, jurisdiction, page]);

    useEffect(() => { if (configured) search(); }, [configured, search]);

    if (configured === false) {
        return (
            <div className="max-w-2xl mx-auto py-12 text-center">
                <AlertTriangle className="size-12 text-amber-300 mx-auto mb-4" />
                <h3 className="text-lg font-semibold text-gray-900 mb-2">Governance Hub Not Connected</h3>
                <p className="text-sm text-gray-500 mb-4">
                    Configure the Governance Hub URL and API key in <strong>Settings</strong> to browse and import governance packs.
                </p>
                <Link href="/settings" className="text-sm text-blue-600 hover:text-blue-800">Go to Settings</Link>
            </div>
        );
    }

    return (
        <div className="max-w-5xl mx-auto">
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2">
                        <Globe className="size-5 text-blue-500" /> Governance Hub
                    </h2>
                    <p className="text-sm text-gray-500 mt-1">
                        Browse and import governance frameworks from the shared marketplace
                    </p>
                </div>
            </div>

            {/* Search bar */}
            <div className="flex gap-3 mb-6">
                <div className="relative flex-1">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-gray-400" />
                    <input
                        id="hub-search"
                        type="search"
                        value={query}
                        onChange={e => { setQuery(e.target.value); setPage(0); }}
                        onKeyDown={e => { if (e.key === "Enter") search(); }}
                        placeholder="Search governance packs..."
                        className="w-full pl-10 pr-4 py-2.5 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                        aria-label="Search governance packs"
                    />
                </div>
                <select
                    id="hub-jurisdiction"
                    value={jurisdiction}
                    onChange={e => { setJurisdiction(e.target.value); setPage(0); }}
                    className="text-sm border border-gray-300 rounded-lg px-3 py-2.5"
                    aria-label="Filter by jurisdiction"
                >
                    <option value="">All Jurisdictions</option>
                    <option value="UK">UK</option>
                    <option value="US">US</option>
                    <option value="EU">EU</option>
                    <option value="CA">Canada</option>
                    <option value="AU">Australia</option>
                </select>
            </div>

            {/* Results */}
            {loading && !results ? (
                <div className="text-center py-12"><Loader2 className="size-8 animate-spin text-gray-300 mx-auto" /></div>
            ) : results?.content.length === 0 ? (
                <div className="text-center py-12">
                    <Package className="size-12 text-gray-200 mx-auto mb-4" />
                    <h3 className="text-sm font-semibold text-gray-700">No packs found</h3>
                    <p className="text-xs text-gray-400 mt-1">Try a different search or check your hub connection.</p>
                </div>
            ) : (
                <div className="space-y-4">
                    {results?.content.map(pack => (
                        <Link key={pack.id} href={`/governance/hub/${pack.slug}`}
                            className="block bg-white rounded-lg shadow-sm border border-gray-200 p-5 hover:border-blue-200 hover:shadow-md transition-all group">
                            <div className="flex items-start justify-between gap-4">
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2 mb-1">
                                        <h3 className="text-base font-semibold text-gray-900 group-hover:text-blue-700 transition-colors">
                                            {pack.name}
                                        </h3>
                                        {pack.featured && (
                                            <span className="px-2 py-0.5 text-[10px] font-medium bg-amber-100 text-amber-700 rounded-full">Featured</span>
                                        )}
                                        <span className="text-xs text-gray-400">v{pack.latestVersionNumber}</span>
                                    </div>
                                    <p className="text-sm text-gray-600 line-clamp-2 mb-3">{pack.description}</p>

                                    <div className="flex items-center gap-4 flex-wrap">
                                        <span className="inline-flex items-center gap-1 text-xs text-gray-500">
                                            <Globe className="size-3" /> {pack.jurisdiction}
                                        </span>
                                        <span className="inline-flex items-center gap-1 text-xs text-gray-500">
                                            <Building className="size-3" /> {pack.industries.join(", ")}
                                        </span>
                                        <span className="inline-flex items-center gap-1 text-xs text-gray-500">
                                            <Download className="size-3" /> {pack.downloadCount} downloads
                                        </span>
                                        {pack.author.verified && (
                                            <span className="inline-flex items-center gap-1 text-xs text-green-600">
                                                <Star className="size-3" /> Verified
                                            </span>
                                        )}
                                    </div>

                                    <div className="flex flex-wrap gap-1 mt-2">
                                        {pack.regulations.slice(0, 4).map(r => (
                                            <span key={r} className="px-1.5 py-0.5 text-[10px] bg-blue-50 text-blue-600 rounded">{r}</span>
                                        ))}
                                        {pack.tags.slice(0, 3).map(t => (
                                            <span key={t} className="px-1.5 py-0.5 text-[10px] bg-gray-100 text-gray-500 rounded">{t}</span>
                                        ))}
                                    </div>
                                </div>

                                <div className="text-xs text-gray-400 flex items-center gap-1 shrink-0">
                                    <ChevronRight className="size-4" />
                                </div>
                            </div>
                        </Link>
                    ))}
                </div>
            )}

            {/* Pagination */}
            {results && results.totalPages > 1 && (
                <div className="flex items-center justify-between mt-6">
                    <span className="text-xs text-gray-500">Page {results.number + 1} of {results.totalPages}</span>
                    <div className="flex gap-1">
                        <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
                            className="px-3 py-1 text-xs border rounded hover:bg-gray-50 disabled:opacity-50">Prev</button>
                        <button onClick={() => setPage(p => p + 1)} disabled={page >= results.totalPages - 1}
                            className="px-3 py-1 text-xs border rounded hover:bg-gray-50 disabled:opacity-50">Next</button>
                    </div>
                </div>
            )}
        </div>
    );
}
