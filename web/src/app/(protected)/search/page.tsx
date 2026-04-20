"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
    Search, Filter, FileText, ChevronDown, ChevronRight,
    X, Loader2, Shield,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

type SearchResult = {
    id: string;
    slug?: string;
    originalFileName: string;
    mimeType: string;
    fileSizeBytes: number;
    status: string;
    categoryName?: string;
    classificationCode?: string;
    sensitivityLabel?: string;
    uploadedBy: string;
    createdAt: string;
    extractedMetadata?: Record<string, string>;
    tags?: string[];
};

type TaxNode = {
    id: string; name: string; classificationCode: string;
    level: string; documentCount: number; ownDocumentCount: number;
    children: TaxNode[];
};

type PageResponse = {
    content: SearchResult[];
    totalElements: number;
    totalPages: number;
    number: number;
};

type SchemaField = {
    fieldName: string;
    dataType: string;
    required: boolean;
    description: string;
    examples: string[];
};

type MetadataSchemaInfo = {
    id: string;
    name: string;
    fields: SchemaField[];
    linkedCategoryIds: string[];
};

type Facets = {
    categories: Record<string, number>;
    sensitivities: Record<string, number>;
    statuses: Record<string, number>;
    metadataSchemas: MetadataSchemaInfo[];
};

const SENSITIVITY_COLORS: Record<string, string> = {
    PUBLIC: "bg-green-100 text-green-700",
    INTERNAL: "bg-blue-100 text-blue-700",
    CONFIDENTIAL: "bg-amber-100 text-amber-700",
    RESTRICTED: "bg-red-100 text-red-700",
};

export default function SearchPage() {
    const router = useRouter();
    const [query, setQuery] = useState("");
    const [results, setResults] = useState<PageResponse | null>(null);
    const [facets, setFacets] = useState<Facets | null>(null);
    const [loading, setLoading] = useState(false);
    const [page, setPage] = useState(0);
    const [filtersOpen, setFiltersOpen] = useState(true);

    // Standard filters
    const [categoryFilter, setCategoryFilter] = useState("");
    const [codeFilter, setCodeFilter] = useState("");
    const [sensitivityFilter, setSensitivityFilter] = useState("");
    const [statusFilter, setStatusFilter] = useState("");
    const [dateAfter, setDateAfter] = useState("");
    const [dateBefore, setDateBefore] = useState("");

    // Taxonomy cascading filter
    const [taxTree, setTaxTree] = useState<TaxNode[]>([]);
    const [selFunction, setSelFunction] = useState<TaxNode | null>(null);
    const [selActivity, setSelActivity] = useState<TaxNode | null>(null);
    const [selTransaction, setSelTransaction] = useState<TaxNode | null>(null);

    // Dynamic metadata filters (populated from schema)
    const [metadataFilters, setMetadataFilters] = useState<Record<string, string>>({});
    const [activeSchema, setActiveSchema] = useState<MetadataSchemaInfo | null>(null);

    useEffect(() => {
        api.get("/search/taxonomy-tree").then(({ data }) => setTaxTree(data)).catch(() => {});
    }, []);

    // Load facets — re-fetch when category or sensitivity changes so counts are scoped
    const fetchFacets = useCallback(async () => {
        try {
            const params = new URLSearchParams();
            if (categoryFilter) params.set("categoryName", categoryFilter);
            if (sensitivityFilter) params.set("sensitivity", sensitivityFilter);
            const { data } = await api.get(`/search/facets?${params}`);
            setFacets(data);
        } catch { /* ignore */ }
    }, [categoryFilter, sensitivityFilter]);

    useEffect(() => { fetchFacets(); }, [fetchFacets]);

    // Reset status if it's no longer valid for the selected category
    useEffect(() => {
        if (!facets || !statusFilter) return;
        if (facets.statuses[statusFilter] === undefined) {
            setStatusFilter("");
        }
    }, [facets, statusFilter]);

    const doSearch = useCallback(async () => {
        setLoading(true);
        try {
            const body: Record<string, unknown> = {};
            if (query) body.q = query;
            if (codeFilter) body.classificationCodePrefix = codeFilter;
            else if (categoryFilter) body.categoryName = categoryFilter;
            if (sensitivityFilter) body.sensitivity = sensitivityFilter;
            if (statusFilter) body.status = statusFilter;
            if (dateAfter) body.createdAfter = new Date(dateAfter).toISOString();
            if (dateBefore) body.createdBefore = new Date(dateBefore).toISOString();

            const activeMeta = Object.fromEntries(
                Object.entries(metadataFilters).filter(([, v]) => v.trim())
            );
            if (Object.keys(activeMeta).length > 0) body.metadata = activeMeta;

            const { data } = await api.post(`/search?page=${page}&size=20&sort=createdAt,desc`, body);
            setResults(data);
        } catch {
            toast.error("Search failed");
        } finally {
            setLoading(false);
        }
    }, [query, categoryFilter, codeFilter, sensitivityFilter, statusFilter, dateAfter, dateBefore, metadataFilters, page]);

    // Search on filter change
    useEffect(() => { doSearch(); }, [doSearch]);

    const clearFilters = () => {
        setQuery("");
        setCategoryFilter("");
        setCodeFilter("");
        setSelFunction(null);
        setSelActivity(null);
        setSelTransaction(null);
        setSensitivityFilter("");
        setStatusFilter("");
        setDateAfter("");
        setDateBefore("");
        setMetadataFilters({});
        setActiveSchema(null);
        setPage(0);
    };

    const selectSchema = (schema: MetadataSchemaInfo | null) => {
        setActiveSchema(schema);
        setMetadataFilters({});
    };

    const updateMetaFilter = (key: string, value: string) => {
        setMetadataFilters(prev => ({ ...prev, [key]: value }));
        setPage(0);
    };

    const hasActiveFilters = categoryFilter || codeFilter || sensitivityFilter || statusFilter ||
        dateAfter || dateBefore || Object.values(metadataFilters).some(v => v.trim());

    return (
        <>
            {/* Header */}
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">Search</h2>
                    <p className="text-sm text-gray-500 mt-1">
                        {results ? `${results.totalElements} result(s)` : "Search across all documents and metadata"}
                    </p>
                </div>
            </div>

            {/* Search bar */}
            <div className="flex gap-3 mb-4">
                <div className="relative flex-1">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-gray-400" />
                    <input type="text" value={query}
                        onChange={(e) => { setQuery(e.target.value); setPage(0); }}
                        placeholder="Search by filename, category, tags, or document content..."
                        className="w-full pl-10 pr-4 py-2.5 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
                </div>
                <button onClick={() => setFiltersOpen(!filtersOpen)}
                    className={`inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium border rounded-lg transition-colors ${
                        filtersOpen ? "bg-blue-50 text-blue-700 border-blue-200" : "text-gray-600 border-gray-300 hover:bg-gray-50"
                    }`}>
                    <Filter className="size-4" />
                    Filters
                    {hasActiveFilters && <span className="size-2 bg-blue-500 rounded-full" />}
                </button>
            </div>

            <div className="flex gap-4">
                {/* Filter panel */}
                {filtersOpen && (
                    <div className="w-72 shrink-0 space-y-4">
                        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 space-y-4">
                            <div className="flex items-center justify-between">
                                <h3 className="text-sm font-semibold text-gray-900">Filters</h3>
                                {hasActiveFilters && (
                                    <button onClick={clearFilters} className="text-xs text-blue-600 hover:text-blue-800">Clear all</button>
                                )}
                            </div>

                            {/* Taxonomy: Function → Activity → Transaction */}
                            <FilterSection label="Function">
                                <select value={selFunction?.id ?? ""} onChange={e => {
                                    const fn = taxTree.find(f => f.id === e.target.value) ?? null;
                                    setSelFunction(fn); setSelActivity(null); setSelTransaction(null);
                                    setCodeFilter(fn?.classificationCode ?? ""); setCategoryFilter(""); setPage(0);
                                }} className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5">
                                    <option value="">All functions</option>
                                    {taxTree.map(fn => (
                                        <option key={fn.id} value={fn.id}>{fn.name} ({fn.classificationCode})</option>
                                    ))}
                                </select>
                            </FilterSection>

                            {selFunction && selFunction.children.length > 0 && (
                                <FilterSection label="Activity">
                                    <select value={selActivity?.id ?? ""} onChange={e => {
                                        const ac = selFunction.children.find(a => a.id === e.target.value) ?? null;
                                        setSelActivity(ac); setSelTransaction(null);
                                        setCodeFilter(ac?.classificationCode ?? selFunction.classificationCode); setPage(0);
                                    }} className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5">
                                        <option value="">All activities</option>
                                        {selFunction.children.map(ac => (
                                            <option key={ac.id} value={ac.id}>{ac.name} ({ac.classificationCode})</option>
                                        ))}
                                    </select>
                                </FilterSection>
                            )}

                            {selActivity && selActivity.children.length > 0 && (
                                <FilterSection label="Transaction">
                                    <select value={selTransaction?.id ?? ""} onChange={e => {
                                        const tx = selActivity.children.find(t => t.id === e.target.value) ?? null;
                                        setSelTransaction(tx);
                                        setCodeFilter(tx?.classificationCode ?? selActivity.classificationCode); setPage(0);
                                    }} className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5">
                                        <option value="">All transactions</option>
                                        {selActivity.children.map(tx => (
                                            <option key={tx.id} value={tx.id}>{tx.name} ({tx.classificationCode})</option>
                                        ))}
                                    </select>
                                </FilterSection>
                            )}

                            {/* Record codes accordion */}
                            <FilterSection label="Record Codes">
                                <div className="max-h-48 overflow-y-auto space-y-0.5 border border-gray-200 rounded-md p-2">
                                    {(() => {
                                        // Derive visible codes from current taxonomy selection
                                        let codes: { code: string; name: string; count: number }[] = [];
                                        const codeCounts = (facets as any)?.classificationCodes as Record<string, number> | undefined;
                                        if (selActivity) {
                                            // Show transactions under selected activity
                                            codes = selActivity.children.map(tx => ({
                                                code: tx.classificationCode, name: tx.name,
                                                count: codeCounts?.[tx.classificationCode] ?? 0,
                                            }));
                                            // Include activity-level docs too
                                            if (codeCounts?.[selActivity.classificationCode]) {
                                                codes.unshift({ code: selActivity.classificationCode, name: selActivity.name,
                                                    count: codeCounts[selActivity.classificationCode] });
                                            }
                                        } else if (selFunction) {
                                            // Show activities + their transactions
                                            for (const ac of selFunction.children) {
                                                codes.push({ code: ac.classificationCode, name: ac.name,
                                                    count: codeCounts?.[ac.classificationCode] ?? 0 });
                                                for (const tx of ac.children) {
                                                    codes.push({ code: tx.classificationCode, name: tx.name,
                                                        count: codeCounts?.[tx.classificationCode] ?? 0 });
                                                }
                                            }
                                        } else {
                                            // Show all codes with counts
                                            if (codeCounts) {
                                                codes = Object.entries(codeCounts)
                                                    .sort(([a], [b]) => a.localeCompare(b))
                                                    .map(([code, count]) => ({ code, name: "", count }));
                                            }
                                        }
                                        if (codes.length === 0) return <span className="text-xs text-gray-400">No codes available</span>;
                                        return codes.map(c => (
                                            <button key={c.code} onClick={() => { setCodeFilter(codeFilter === c.code ? (selActivity?.classificationCode ?? selFunction?.classificationCode ?? "") : c.code); setPage(0); }}
                                                className={`w-full text-left flex items-center gap-2 px-2 py-1 rounded text-xs transition-colors ${
                                                    codeFilter === c.code ? "bg-blue-50 text-blue-700 font-medium" : "text-gray-700 hover:bg-gray-50"
                                                }`}>
                                                <span className="font-mono shrink-0 w-24 truncate">{c.code}</span>
                                                {c.name && <span className="truncate flex-1 text-gray-500">{c.name}</span>}
                                                {c.count > 0 && <span className="text-[10px] bg-gray-100 text-gray-500 px-1.5 rounded-full shrink-0">{c.count}</span>}
                                            </button>
                                        ));
                                    })()}
                                </div>
                            </FilterSection>

                            {/* Sensitivity */}
                            <FilterSection label="Sensitivity">
                                <div className="flex flex-wrap gap-1.5">
                                    {["PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"].map(s => (
                                        <button key={s} onClick={() => { setSensitivityFilter(sensitivityFilter === s ? "" : s); setPage(0); }}
                                            className={`px-2 py-1 text-xs font-medium rounded-full transition-colors ${
                                                sensitivityFilter === s
                                                    ? SENSITIVITY_COLORS[s]
                                                    : "bg-gray-100 text-gray-500 hover:bg-gray-200"
                                            }`}>
                                            {s}
                                        </button>
                                    ))}
                                </div>
                            </FilterSection>

                            {/* Status */}
                            <FilterSection label="Status">
                                <select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
                                    className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5">
                                    <option value="">All statuses</option>
                                    {facets && Object.entries(facets.statuses).map(([s, count]) => (
                                        <option key={s} value={s}>{s.replace(/_/g, " ")} ({count})</option>
                                    ))}
                                </select>
                            </FilterSection>

                            {/* Date range */}
                            <FilterSection label="Created">
                                <div className="grid grid-cols-2 gap-2">
                                    <input type="date" value={dateAfter} onChange={(e) => { setDateAfter(e.target.value); setPage(0); }}
                                        className="text-xs border border-gray-300 rounded-md px-2 py-1.5" />
                                    <input type="date" value={dateBefore} onChange={(e) => { setDateBefore(e.target.value); setPage(0); }}
                                        className="text-xs border border-gray-300 rounded-md px-2 py-1.5" />
                                </div>
                            </FilterSection>
                        </div>

                        {/* Dynamic metadata filters */}
                        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 space-y-4">
                            <h3 className="text-sm font-semibold text-gray-900">Metadata Filters</h3>

                            {/* Schema selector */}
                            {facets && facets.metadataSchemas.length > 0 && (
                                <div>
                                    <label className="text-xs text-gray-500 block mb-1">Document type</label>
                                    <select
                                        value={activeSchema?.id ?? ""}
                                        onChange={(e) => {
                                            const schema = facets.metadataSchemas.find(s => s.id === e.target.value) ?? null;
                                            selectSchema(schema);
                                        }}
                                        className="w-full text-sm border border-gray-300 rounded-md px-2.5 py-1.5">
                                        <option value="">Select schema...</option>
                                        {facets.metadataSchemas.map(s => (
                                            <option key={s.id} value={s.id}>{s.name}</option>
                                        ))}
                                    </select>
                                </div>
                            )}

                            {/* Schema fields as filter inputs */}
                            {activeSchema && activeSchema.fields.length > 0 && (
                                <div className="space-y-3 pt-1">
                                    {activeSchema.fields.map(field => (
                                        <div key={field.fieldName}>
                                            <label className="text-xs font-medium text-gray-700 block mb-1">
                                                {field.fieldName.replace(/_/g, " ")}
                                                <span className="text-gray-400 font-normal ml-1">({field.dataType.toLowerCase()})</span>
                                            </label>
                                            {field.dataType === "DATE" ? (
                                                <input type="date"
                                                    value={metadataFilters[field.fieldName] ?? ""}
                                                    onChange={(e) => updateMetaFilter(field.fieldName, e.target.value)}
                                                    className="w-full text-xs border border-gray-300 rounded-md px-2.5 py-1.5" />
                                            ) : field.dataType === "BOOLEAN" ? (
                                                <select
                                                    value={metadataFilters[field.fieldName] ?? ""}
                                                    onChange={(e) => updateMetaFilter(field.fieldName, e.target.value)}
                                                    className="w-full text-xs border border-gray-300 rounded-md px-2.5 py-1.5">
                                                    <option value="">Any</option>
                                                    <option value="true">Yes</option>
                                                    <option value="false">No</option>
                                                </select>
                                            ) : (
                                                <input type="text"
                                                    value={metadataFilters[field.fieldName] ?? ""}
                                                    onChange={(e) => updateMetaFilter(field.fieldName, e.target.value)}
                                                    placeholder={field.examples?.[0] ?? `Search ${field.fieldName}...`}
                                                    className="w-full text-xs border border-gray-300 rounded-md px-2.5 py-1.5" />
                                            )}
                                        </div>
                                    ))}
                                </div>
                            )}

                            {!activeSchema && (
                                <p className="text-xs text-gray-400">Select a document type above to filter by its metadata fields</p>
                            )}
                        </div>
                    </div>
                )}

                {/* Results */}
                <div className="flex-1 min-w-0">
                    <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                        {loading && !results ? (
                            <div className="divide-y divide-gray-100">
                                {Array.from({ length: 5 }).map((_, i) => (
                                    <div key={i} className="flex items-center gap-4 px-4 py-3 animate-pulse">
                                        <div className="size-4 rounded bg-gray-200" />
                                        <div className="h-3 flex-1 rounded bg-gray-200" />
                                        <div className="h-3 w-20 rounded bg-gray-200" />
                                    </div>
                                ))}
                            </div>
                        ) : results?.content.length === 0 ? (
                            <div className="flex flex-col items-center justify-center py-12 px-6 text-center">
                                <div className="size-16 rounded-full bg-gray-100 flex items-center justify-center mb-4">
                                    <Search className="size-8 text-gray-300" />
                                </div>
                                <h3 className="text-sm font-semibold text-gray-700 mb-1">No results found</h3>
                                <p className="text-xs text-gray-400 max-w-xs">Try different keywords, adjust your filters, or broaden your search criteria.</p>
                            </div>
                        ) : (
                            <div className="divide-y divide-gray-100">
                                {results?.content.map(doc => (
                                    <button key={doc.id}
                                        onClick={() => router.push(`/documents?doc=${doc.slug ?? doc.id}`)}
                                        className="w-full text-left px-5 py-4 hover:bg-gray-50 transition-colors">
                                        <div className="flex items-start justify-between gap-4">
                                            <div className="min-w-0 flex-1">
                                                <div className="flex items-center gap-2 mb-1">
                                                    <FileText className="size-4 text-gray-400 shrink-0" />
                                                    <span className="text-sm font-medium text-gray-900 truncate">
                                                        {doc.originalFileName}
                                                    </span>
                                                </div>
                                                <div className="flex items-center gap-2 flex-wrap">
                                                    {doc.classificationCode && (
                                                        <span className="text-[10px] font-mono font-semibold text-blue-700 bg-blue-50 px-1.5 py-0.5 rounded">
                                                            {doc.classificationCode}
                                                        </span>
                                                    )}
                                                    {doc.categoryName && (
                                                        <span className="text-xs text-gray-500">{doc.categoryName}</span>
                                                    )}
                                                    {doc.sensitivityLabel && (
                                                        <span className={`px-1.5 py-0.5 text-[10px] font-medium rounded-full ${SENSITIVITY_COLORS[doc.sensitivityLabel] ?? ""}`}>
                                                            {doc.sensitivityLabel}
                                                        </span>
                                                    )}
                                                    {doc.tags?.slice(0, 3).map(tag => (
                                                        <span key={tag} className="px-1.5 py-0.5 text-[10px] bg-gray-100 text-gray-500 rounded">
                                                            {tag}
                                                        </span>
                                                    ))}
                                                </div>

                                                {/* Show matched metadata values */}
                                                {doc.extractedMetadata && Object.keys(doc.extractedMetadata).length > 0 && (
                                                    <div className="flex flex-wrap gap-x-4 gap-y-0.5 mt-1.5">
                                                        {Object.entries(doc.extractedMetadata).slice(0, 4).map(([k, v]) => (
                                                            <span key={k} className="text-[11px] text-gray-400">
                                                                <span className="text-gray-500">{k.replace(/_/g, " ")}:</span> {v}
                                                            </span>
                                                        ))}
                                                    </div>
                                                )}
                                            </div>

                                            <div className="text-right shrink-0">
                                                <div className="text-xs text-gray-400">
                                                    {new Date(doc.createdAt).toLocaleDateString()}
                                                </div>
                                                <div className="text-[10px] text-gray-300 mt-0.5">
                                                    {doc.uploadedBy}
                                                </div>
                                            </div>
                                        </div>
                                    </button>
                                ))}
                            </div>
                        )}

                        {/* Pagination */}
                        {results && results.totalPages > 1 && (
                            <div className="flex items-center justify-between px-5 py-3 border-t border-gray-200 bg-gray-50">
                                <span className="text-xs text-gray-500">
                                    Page {results.number + 1} of {results.totalPages} ({results.totalElements} total)
                                </span>
                                <div className="flex gap-1">
                                    <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={results.number === 0}
                                        className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50">
                                        Prev
                                    </button>
                                    <button onClick={() => setPage(p => p + 1)} disabled={results.number >= results.totalPages - 1}
                                        className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50">
                                        Next
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </>
    );
}

function FilterSection({ label, children }: { label: string; children: React.ReactNode }) {
    return (
        <div>
            <label className="text-xs font-medium text-gray-700 block mb-1.5">{label}</label>
            {children}
        </div>
    );
}
