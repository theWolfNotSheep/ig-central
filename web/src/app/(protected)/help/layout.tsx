"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import {
    Rocket, BookOpen, FileSearch, Settings, Search, ChevronDown, ChevronRight,
    Database, Brain, ShieldAlert, Shield, Users, Workflow, Activity, HelpCircle, ArrowLeft,
} from "lucide-react";
import { helpSections, helpCategories } from "./help-content";

const ICONS: Record<string, React.ElementType> = {
    Rocket, BookOpen, FileSearch, Settings, Search, Database, Brain, ShieldAlert,
    Shield, Users, Workflow, Activity, HelpCircle, Blocks: Database,
};

export default function HelpLayout({ children }: { children: React.ReactNode }) {
    const pathname = usePathname();
    const [search, setSearch] = useState("");
    const [expandedCats, setExpandedCats] = useState<Set<string>>(new Set(helpCategories.map(c => c.key)));

    const toggleCat = (key: string) => {
        setExpandedCats(prev => {
            const next = new Set(prev);
            if (next.has(key)) next.delete(key); else next.add(key);
            return next;
        });
    };

    const filtered = search.trim()
        ? helpSections.filter(s =>
            s.title.toLowerCase().includes(search.toLowerCase()) ||
            s.summary.toLowerCase().includes(search.toLowerCase()))
        : helpSections;

    return (
        <div className="flex h-[calc(100vh-120px)] -m-6">
            {/* Help sidebar */}
            <div className="w-64 shrink-0 bg-white border-r border-gray-200 flex flex-col">
                <div className="px-4 py-3 border-b border-gray-100 shrink-0">
                    <div className="flex items-center justify-between mb-2">
                        <Link href="/help" className="text-sm font-semibold text-gray-900 flex items-center gap-2 hover:text-blue-700">
                            <HelpCircle className="size-4" /> Help Centre
                        </Link>
                        <Link href="/dashboard" className="text-[10px] text-gray-400 hover:text-gray-600 flex items-center gap-1">
                            <ArrowLeft className="size-3" /> Back
                        </Link>
                    </div>
                    <div className="relative">
                        <Search className="absolute left-2 top-1/2 -translate-y-1/2 size-3.5 text-gray-400" />
                        <input
                            id="help-search"
                            type="search"
                            value={search}
                            onChange={e => setSearch(e.target.value)}
                            placeholder="Search help..."
                            className="w-full text-xs border border-gray-200 rounded-md pl-7 pr-2 py-1.5 focus:ring-2 focus:ring-blue-500"
                            aria-label="Search help topics"
                        />
                    </div>
                </div>

                <nav className="flex-1 overflow-y-auto py-2">
                    {helpCategories.map(cat => {
                        const CatIcon = ICONS[cat.icon] ?? HelpCircle;
                        const sections = filtered.filter(s => s.category === cat.key);
                        if (search.trim() && sections.length === 0) return null;
                        const isOpen = expandedCats.has(cat.key);

                        return (
                            <div key={cat.key} className="mb-1">
                                <button onClick={() => toggleCat(cat.key)}
                                    className="w-full flex items-center gap-2 px-4 py-1.5 text-xs font-semibold text-gray-500 uppercase tracking-wider hover:text-gray-700">
                                    {isOpen ? <ChevronDown className="size-3" /> : <ChevronRight className="size-3" />}
                                    <CatIcon className="size-3.5" />
                                    {cat.label}
                                    <span className="text-[10px] text-gray-400 font-normal ml-auto">{sections.length}</span>
                                </button>
                                {isOpen && sections.map(section => {
                                    const SIcon = ICONS[section.icon] ?? HelpCircle;
                                    const active = pathname === `/help/${section.slug}`;
                                    return (
                                        <Link key={section.slug} href={`/help/${section.slug}`}
                                            className={`flex items-center gap-2 pl-10 pr-4 py-1.5 text-xs transition-colors ${
                                                active ? "bg-blue-50 text-blue-700 font-medium" : "text-gray-600 hover:bg-gray-50 hover:text-gray-900"
                                            }`}>
                                            <SIcon className="size-3.5 shrink-0" />
                                            <span className="truncate">{section.title}</span>
                                        </Link>
                                    );
                                })}
                            </div>
                        );
                    })}
                </nav>
            </div>

            {/* Help content */}
            <div className="flex-1 overflow-y-auto">
                {children}
            </div>
        </div>
    );
}
