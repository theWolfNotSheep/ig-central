"use client";

import { useEffect, useState, useMemo } from "react";
import { ChevronRight, Search, FolderOpen, FileText, Layers } from "lucide-react";
import api from "@/lib/axios/axios.client";

type TaxNode = {
    id: string; name: string; classificationCode: string;
    level: string; documentCount: number; ownDocumentCount: number;
    children: TaxNode[];
};

type Props = {
    onSelect: (code: string | null, level: string | null, name: string | null) => void;
    selected?: string | null;
    compact?: boolean;
};

export default function TaxonomyFilter({ onSelect, selected, compact }: Props) {
    const [tree, setTree] = useState<TaxNode[]>([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    const [activeFunction, setActiveFunction] = useState<TaxNode | null>(null);
    const [activeActivity, setActiveActivity] = useState<TaxNode | null>(null);

    useEffect(() => {
        api.get("/search/taxonomy-tree")
            .then(r => setTree(r.data))
            .catch(() => {})
            .finally(() => setLoading(false));
    }, []);

    // Filter tree by search term (matches code or name)
    const filteredTree = useMemo(() => {
        if (!search.trim()) return tree;
        const q = search.toLowerCase();
        return tree.filter(fn =>
            fn.name.toLowerCase().includes(q) ||
            fn.classificationCode?.toLowerCase().includes(q) ||
            fn.children.some(ac =>
                ac.name.toLowerCase().includes(q) ||
                ac.classificationCode?.toLowerCase().includes(q) ||
                ac.children.some(tx =>
                    tx.name.toLowerCase().includes(q) ||
                    tx.classificationCode?.toLowerCase().includes(q)
                )
            )
        );
    }, [tree, search]);

    const handleFunctionClick = (fn: TaxNode) => {
        if (activeFunction?.id === fn.id) {
            setActiveFunction(null);
            setActiveActivity(null);
            onSelect(null, null, null);
        } else {
            setActiveFunction(fn);
            setActiveActivity(null);
            onSelect(fn.classificationCode, "FUNCTION", fn.name);
        }
    };

    const handleActivityClick = (ac: TaxNode) => {
        if (activeActivity?.id === ac.id) {
            setActiveActivity(null);
            onSelect(activeFunction!.classificationCode, "FUNCTION", activeFunction!.name);
        } else {
            setActiveActivity(ac);
            onSelect(ac.classificationCode, "ACTIVITY", ac.name);
        }
    };

    const handleTransactionClick = (tx: TaxNode) => {
        if (selected === tx.classificationCode) {
            onSelect(activeActivity!.classificationCode, "ACTIVITY", activeActivity!.name);
        } else {
            onSelect(tx.classificationCode, "TRANSACTION", tx.name);
        }
    };

    if (loading) return <div className="text-xs text-gray-400 p-3">Loading taxonomy...</div>;

    const activities = activeFunction?.children ?? [];
    const transactions = activeActivity?.children ?? [];

    return (
        <div className="flex flex-col h-full overflow-hidden">
            {/* Search */}
            <div className="px-2 py-2 border-b border-gray-100 shrink-0">
                <div className="relative">
                    <Search className="absolute left-2 top-1/2 -translate-y-1/2 size-3.5 text-gray-400" />
                    <input type="text" value={search} onChange={e => setSearch(e.target.value)}
                        placeholder="Filter by code or name..."
                        className="w-full pl-7 pr-2 py-1.5 text-xs border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-300" />
                </div>
            </div>

            {/* Clear filter */}
            {selected && (
                <button onClick={() => { setActiveFunction(null); setActiveActivity(null); onSelect(null, null, null); }}
                    className="mx-2 mt-2 px-2 py-1 text-[11px] bg-blue-50 text-blue-700 rounded-md hover:bg-blue-100 text-left flex items-center gap-1">
                    <Layers className="size-3" /> All Documents
                </button>
            )}

            {/* Three-tier columns */}
            <div className="flex-1 overflow-y-auto">
                {/* Functions */}
                <div className="py-1">
                    <div className="px-3 py-1 text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Functions</div>
                    {filteredTree.map(fn => (
                        <button key={fn.id} onClick={() => handleFunctionClick(fn)}
                            className={`w-full text-left px-3 py-1.5 flex items-center gap-2 text-xs transition-colors ${
                                activeFunction?.id === fn.id ? "bg-blue-50 text-blue-700 font-medium" : "text-gray-700 hover:bg-gray-50"
                            }`}>
                            <FolderOpen className={`size-3.5 shrink-0 ${activeFunction?.id === fn.id ? "text-blue-500" : "text-amber-400"}`} />
                            <span className="truncate flex-1">{fn.name}</span>
                            <span className="text-[10px] text-gray-400 font-mono shrink-0">{fn.classificationCode}</span>
                            {fn.documentCount > 0 && (
                                <span className="text-[10px] bg-gray-100 text-gray-500 px-1.5 rounded-full shrink-0">{fn.documentCount}</span>
                            )}
                            <ChevronRight className={`size-3 shrink-0 transition-transform ${activeFunction?.id === fn.id ? "rotate-90 text-blue-500" : "text-gray-300"}`} />
                        </button>
                    ))}
                </div>

                {/* Activities */}
                {activities.length > 0 && (
                    <div className="py-1 border-t border-gray-100">
                        <div className="px-3 py-1 text-[10px] font-semibold text-gray-400 uppercase tracking-wider">
                            Activities — {activeFunction?.name}
                        </div>
                        {activities.map(ac => (
                            <button key={ac.id} onClick={() => handleActivityClick(ac)}
                                className={`w-full text-left px-3 pl-6 py-1.5 flex items-center gap-2 text-xs transition-colors ${
                                    activeActivity?.id === ac.id ? "bg-blue-50 text-blue-700 font-medium" : "text-gray-700 hover:bg-gray-50"
                                }`}>
                                <FolderOpen className={`size-3.5 shrink-0 ${activeActivity?.id === ac.id ? "text-blue-500" : "text-amber-300"}`} />
                                <span className="truncate flex-1">{ac.name}</span>
                                <span className="text-[10px] text-gray-400 font-mono shrink-0">{ac.classificationCode}</span>
                                {ac.documentCount > 0 && (
                                    <span className="text-[10px] bg-gray-100 text-gray-500 px-1.5 rounded-full shrink-0">{ac.documentCount}</span>
                                )}
                                {ac.children.length > 0 && (
                                    <ChevronRight className={`size-3 shrink-0 transition-transform ${activeActivity?.id === ac.id ? "rotate-90 text-blue-500" : "text-gray-300"}`} />
                                )}
                            </button>
                        ))}
                    </div>
                )}

                {/* Transactions */}
                {transactions.length > 0 && (
                    <div className="py-1 border-t border-gray-100">
                        <div className="px-3 py-1 text-[10px] font-semibold text-gray-400 uppercase tracking-wider">
                            Transactions — {activeActivity?.name}
                        </div>
                        {transactions.map(tx => (
                            <button key={tx.id} onClick={() => handleTransactionClick(tx)}
                                className={`w-full text-left px-3 pl-9 py-1.5 flex items-center gap-2 text-xs transition-colors ${
                                    selected === tx.classificationCode ? "bg-blue-50 text-blue-700 font-medium" : "text-gray-700 hover:bg-gray-50"
                                }`}>
                                <FileText className={`size-3.5 shrink-0 ${selected === tx.classificationCode ? "text-blue-500" : "text-gray-400"}`} />
                                <span className="truncate flex-1">{tx.name}</span>
                                <span className="text-[10px] text-gray-400 font-mono shrink-0">{tx.classificationCode}</span>
                                {tx.documentCount > 0 && (
                                    <span className="text-[10px] bg-gray-100 text-gray-500 px-1.5 rounded-full shrink-0">{tx.documentCount}</span>
                                )}
                            </button>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
