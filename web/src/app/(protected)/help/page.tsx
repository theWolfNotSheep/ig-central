"use client";

import Link from "next/link";
import {
    Rocket, BookOpen, FileSearch, Settings, HelpCircle, Database, Brain,
    ShieldAlert, Shield, Users, Workflow, Activity, Search,
} from "lucide-react";
import { helpSections, helpCategories } from "./help-content";

const ICONS: Record<string, React.ElementType> = {
    Rocket, BookOpen, FileSearch, Settings, Search, Database, Brain, ShieldAlert,
    Shield, Users, Workflow, Activity, HelpCircle, Blocks: Database,
};

const CAT_COLORS: Record<string, { bg: string; border: string; text: string; iconBg: string }> = {
    "getting-started": { bg: "bg-blue-50", border: "border-blue-200", text: "text-blue-700", iconBg: "bg-blue-100" },
    user: { bg: "bg-green-50", border: "border-green-200", text: "text-green-700", iconBg: "bg-green-100" },
    "records-manager": { bg: "bg-amber-50", border: "border-amber-200", text: "text-amber-700", iconBg: "bg-amber-100" },
    admin: { bg: "bg-purple-50", border: "border-purple-200", text: "text-purple-700", iconBg: "bg-purple-100" },
};

export default function HelpPage() {
    return (
        <div className="max-w-4xl mx-auto px-8 py-8">
            <div className="mb-8">
                <h1 className="text-2xl font-bold text-gray-900">Help Centre</h1>
                <p className="text-sm text-gray-500 mt-1">
                    Guides and documentation for every part of IG Central.
                </p>
            </div>

            {helpCategories.map(cat => {
                const sections = helpSections.filter(s => s.category === cat.key);
                const CatIcon = ICONS[cat.icon] ?? HelpCircle;
                const colors = CAT_COLORS[cat.key] ?? CAT_COLORS.user;

                return (
                    <div key={cat.key} className="mb-8">
                        <div className="flex items-center gap-2 mb-3">
                            <div className={`size-7 rounded-lg flex items-center justify-center ${colors.iconBg}`}>
                                <CatIcon className={`size-4 ${colors.text}`} />
                            </div>
                            <h2 className="text-sm font-semibold text-gray-900">{cat.label}</h2>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                            {sections.map(section => {
                                const Icon = ICONS[section.icon] ?? HelpCircle;
                                return (
                                    <Link key={section.slug} href={`/help/${section.slug}`}
                                        className={`group p-4 rounded-lg border ${colors.border} ${colors.bg} hover:shadow-sm transition-all`}>
                                        <div className="flex items-start gap-3">
                                            <Icon className={`size-5 ${colors.text} shrink-0 mt-0.5`} />
                                            <div>
                                                <h3 className="text-sm font-medium text-gray-900 group-hover:text-blue-700 transition-colors">
                                                    {section.title}
                                                </h3>
                                                <p className="text-xs text-gray-500 mt-0.5 line-clamp-2">
                                                    {section.summary}
                                                </p>
                                            </div>
                                        </div>
                                    </Link>
                                );
                            })}
                        </div>
                    </div>
                );
            })}
        </div>
    );
}
