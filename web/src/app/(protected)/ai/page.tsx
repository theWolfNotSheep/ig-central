"use client";

import { useState, useEffect } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { Brain, Workflow, Database, History, Settings } from "lucide-react";
import dynamic from "next/dynamic";

const PipelinesTab = dynamic(() => import("./pipelines/page"), { ssr: false });
const BlocksTab = dynamic(() => import("./blocks/page"), { ssr: false });
const UsageTab = dynamic(() => import("./usage/page"), { ssr: false });
const SettingsTab = dynamic(() => import("./settings/page"), { ssr: false });

type Tab = "pipelines" | "blocks" | "usage" | "settings";

const TABS: { key: Tab; label: string; icon: typeof Brain }[] = [
    { key: "pipelines", label: "Pipelines", icon: Workflow },
    { key: "blocks", label: "Blocks", icon: Database },
    { key: "usage", label: "Usage Log", icon: History },
    { key: "settings", label: "Settings", icon: Settings },
];

export default function AiPage() {
    const searchParams = useSearchParams();
    const router = useRouter();
    const [tab, setTab] = useState<Tab>(() => {
        const hash = typeof window !== "undefined" ? window.location.hash.replace("#", "") : "";
        return TABS.some(t => t.key === hash) ? (hash as Tab) : "pipelines";
    });

    useEffect(() => {
        const hash = window.location.hash.replace("#", "");
        if (TABS.some(t => t.key === hash)) setTab(hash as Tab);
    }, []);

    const changeTab = (key: Tab) => {
        setTab(key);
        window.history.replaceState(null, "", `/ai#${key}`);
    };

    return (
        <>
            <div className="flex items-center justify-between mb-6">
                <div className="flex items-center gap-3">
                    <Brain className="size-6 text-indigo-500" />
                    <h2 className="text-xl font-bold text-gray-900">AI</h2>
                </div>
            </div>

            <div className="flex gap-1 mb-6 bg-gray-100 rounded-lg p-1 w-fit">
                {TABS.map(t => (
                    <button key={t.key} onClick={() => changeTab(t.key)}
                        className={`inline-flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-md transition-colors ${
                            tab === t.key ? "bg-white text-gray-900 shadow-sm" : "text-gray-600 hover:text-gray-900"
                        }`}>
                        <t.icon className="size-4" />{t.label}
                    </button>
                ))}
            </div>

            {tab === "pipelines" && <PipelinesTab />}
            {tab === "blocks" && <BlocksTab />}
            {tab === "usage" && <UsageTab />}
            {tab === "settings" && <SettingsTab />}
        </>
    );
}
