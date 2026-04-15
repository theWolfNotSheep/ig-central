"use client";

import { useAuth } from "@/contexts/auth-context";
import { useCallback, useEffect, useState } from "react";
import { usePipelineSSE } from "@/hooks/use-pipeline-sse";
import {
    FileText, Database, AlertTriangle, XCircle, CheckCircle, Clock,
    Upload, Shield, Search, Eye, ArrowRight, Loader2, Activity,
    TrendingUp, Inbox, BarChart3, Zap,
} from "lucide-react";
import Link from "next/link";
import api from "@/lib/axios/axios.client";

type Stats = Record<string, number>;
type RecentDoc = {
    id: string; slug?: string; originalFileName: string; status: string;
    categoryName?: string; sensitivityLabel?: string; createdAt: string;
};
type PipelineMetrics = {
    statusCounts: Record<string, number>;
    totalDocuments: number;
    throughput: { last24h: number; last7d: number };
    avgClassificationTimeMs: number;
    staleDocuments: number;
};

const SENSITIVITY_COLORS: Record<string, string> = {
    PUBLIC: "bg-green-100 text-green-700",
    INTERNAL: "bg-blue-100 text-blue-700",
    CONFIDENTIAL: "bg-amber-100 text-amber-700",
    RESTRICTED: "bg-red-100 text-red-700",
};

export default function DashboardPage() {
    const { username, avatarUrl } = useAuth();
    const [stats, setStats] = useState<Stats>({});
    const [recent, setRecent] = useState<RecentDoc[]>([]);
    const [metrics, setMetrics] = useState<PipelineMetrics | null>(null);
    const [loading, setLoading] = useState(true);

    const [statusFilter, setStatusFilter] = useState<string | null>(null);

    const loadRecent = useCallback(() => {
        const params = statusFilter
            ? `/documents?size=20&sort=createdAt,desc&status=${statusFilter}`
            : "/documents?size=20&sort=createdAt,desc";
        api.get(params).then(r => setRecent(r.data.content ?? [])).catch(() => {});
    }, [statusFilter]);

    // Initial load
    useEffect(() => {
        Promise.all([
            api.get("/documents/stats").then(r => {
                const raw = r.data as Record<string, number>;
                const normalized: Record<string, number> = {};
                const keyMap: Record<string, string> = {
                    uploaded: "UPLOADED", processing: "PROCESSING", processingFailed: "PROCESSING_FAILED",
                    processed: "PROCESSED", classifying: "CLASSIFYING", classified: "CLASSIFIED",
                    classificationFailed: "CLASSIFICATION_FAILED", enforcementFailed: "ENFORCEMENT_FAILED",
                    reviewRequired: "REVIEW_REQUIRED", governanceApplied: "GOVERNANCE_APPLIED",
                    inbox: "INBOX", filed: "FILED",
                };
                for (const [k, v] of Object.entries(raw)) {
                    normalized[keyMap[k] ?? k] = v;
                }
                setStats(normalized);
            }).catch(() => {}),
            loadRecent(),
            api.get("/admin/monitoring/pipeline").then(r => setMetrics(r.data)).catch(() => {}),
        ]).finally(() => setLoading(false));
    }, [loadRecent]);

    // Real-time updates via SSE
    usePipelineSSE({
        onPipelineMetrics: (data) => {
            setMetrics(data);
            if (data.statusCounts) setStats(data.statusCounts);
        },
        onDocumentStatus: () => {
            loadRecent();
        },
    });

    const total = Object.values(stats).reduce((a, b) => a + b, 0);
    const classified = (stats["CLASSIFIED"] ?? 0) + (stats["GOVERNANCE_APPLIED"] ?? 0) + (stats["INBOX"] ?? 0) + (stats["FILED"] ?? 0);
    const reviewCount = stats["REVIEW_REQUIRED"] ?? 0;
    const failedCount = (stats["PROCESSING_FAILED"] ?? 0) + (stats["CLASSIFICATION_FAILED"] ?? 0) + (stats["ENFORCEMENT_FAILED"] ?? 0);
    const inProgress = (stats["PROCESSING"] ?? 0) + (stats["CLASSIFYING"] ?? 0) + (stats["UPLOADED"] ?? 0) + (stats["PROCESSED"] ?? 0);
    const governed = (stats["GOVERNANCE_APPLIED"] ?? 0) + (stats["INBOX"] ?? 0) + (stats["FILED"] ?? 0);
    const inboxCount = stats["INBOX"] ?? 0;

    // Sensitivity distribution from recent docs
    const sensCounts: Record<string, number> = {};
    recent.forEach(d => { if (d.sensitivityLabel) sensCounts[d.sensitivityLabel] = (sensCounts[d.sensitivityLabel] ?? 0) + 1; });

    const greet = () => {
        const h = new Date().getHours();
        if (h < 12) return "Good morning";
        if (h < 18) return "Good afternoon";
        return "Good evening";
    };

    return (
        <>
            {/* Welcome */}
            <div className="bg-gradient-to-r from-blue-600 to-blue-700 rounded-xl p-6 mb-6 text-white flex items-center justify-between">
                <div>
                    <h2 className="text-xl font-bold">
                        {greet()}{username ? `, ${username}` : ""}
                    </h2>
                    <p className="text-blue-200 text-sm mt-1">
                        {total === 0
                            ? "Upload your first document to get started with AI classification."
                            : `${total} documents managed across your governance framework.`}
                    </p>
                </div>
                {avatarUrl && (
                    <img src={avatarUrl} alt="" className="size-12 rounded-full border-2 border-white/30" />
                )}
            </div>

            {/* Top stats */}
            <div className="grid grid-cols-2 lg:grid-cols-5 gap-4 mb-6">
                <StatCard icon={FileText} color="blue" label="Total" value={total} href="/documents" />
                <StatCard icon={CheckCircle} color="green" label="Governed" value={governed} href="/documents" />
                <StatCard icon={AlertTriangle} color="amber" label="Review" value={reviewCount} href="/review" badge />
                <StatCard icon={XCircle} color="red" label="Failed" value={failedCount} href="/monitoring" badge={failedCount > 0} />
                <StatCard icon={Loader2} color="gray" label="In Progress" value={inProgress} />
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
                {/* Pipeline Funnel */}
                <div className="lg:col-span-2 bg-white rounded-xl shadow-sm border border-gray-200 p-5">
                    <h3 className="text-sm font-semibold text-gray-900 mb-4 flex items-center gap-2">
                        <Activity className="size-4 text-blue-500" /> Pipeline Funnel
                    </h3>
                    <PipelineFunnel stats={stats} total={total} activeFilter={statusFilter} onFilterChange={(f) => setStatusFilter(f)} />
                </div>

                {/* Throughput & Speed */}
                <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-5 space-y-4">
                    <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
                        <TrendingUp className="size-4 text-green-500" /> Performance
                    </h3>
                    <div className="space-y-3">
                        <MetricRow label="Last 24h" value={metrics?.throughput?.last24h ?? 0} unit="docs" />
                        <MetricRow label="Last 7 days" value={metrics?.throughput?.last7d ?? 0} unit="docs" />
                        <MetricRow label="Avg classification" value={metrics?.avgClassificationTimeMs ? Math.round(metrics.avgClassificationTimeMs / 1000) : 0} unit="sec" />
                        <MetricRow label="Stale documents" value={metrics?.staleDocuments ?? 0} unit="" warn={(metrics?.staleDocuments ?? 0) > 0} />
                    </div>
                </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Recent Activity */}
                <div className="lg:col-span-2 bg-white rounded-xl shadow-sm border border-gray-200 p-5">
                    <div className="flex items-center justify-between mb-4">
                        <div className="flex items-center gap-3">
                            <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
                                <Clock className="size-4 text-gray-400" /> Documents
                            </h3>
                            {statusFilter && (
                                <button onClick={() => setStatusFilter(null)}
                                    className="inline-flex items-center gap-1 px-2 py-0.5 text-[10px] font-medium bg-blue-100 text-blue-700 rounded-full hover:bg-blue-200 transition-colors">
                                    {statusFilter.replace(/_/g, " ")}
                                    <span className="ml-0.5">&times;</span>
                                </button>
                            )}
                        </div>
                        <Link href="/documents" className="text-xs text-blue-600 hover:text-blue-800 flex items-center gap-1">
                            View all <ArrowRight className="size-3" />
                        </Link>
                    </div>
                    {recent.length > 0 ? (
                        <div className="space-y-1 max-h-[420px] overflow-y-auto">
                            {recent.map(doc => (
                                <Link key={doc.id} href={`/documents?doc=${doc.slug ?? doc.id}`}
                                    className="flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-gray-50 transition-colors group">
                                    <StatusDot status={doc.status} />
                                    <span className="text-sm text-gray-800 truncate flex-1 group-hover:text-blue-700">{doc.originalFileName}</span>
                                    {doc.sensitivityLabel && (
                                        <span className={`px-1.5 py-0.5 text-[10px] font-medium rounded ${SENSITIVITY_COLORS[doc.sensitivityLabel] ?? ""}`}>
                                            {doc.sensitivityLabel}
                                        </span>
                                    )}
                                    {doc.categoryName && <span className="text-[10px] text-gray-400 hidden sm:inline">{doc.categoryName}</span>}
                                    <span className="text-[10px] text-gray-400">{timeAgo(doc.createdAt)}</span>
                                </Link>
                            ))}
                        </div>
                    ) : (
                        <EmptyState icon={Inbox} message={statusFilter ? "No documents with this status" : "No documents yet"} action="Upload your first document" href="/documents" />
                    )}
                </div>

                {/* Quick Actions */}
                <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-5">
                    <h3 className="text-sm font-semibold text-gray-900 mb-4 flex items-center gap-2">
                        <Zap className="size-4 text-amber-500" /> Quick Actions
                    </h3>
                    <div className="space-y-2">
                        <QuickAction href="/documents" icon={Upload} label="Upload Documents" desc="Add files for classification" />
                        <QuickAction href="/review" icon={Eye} label="Review Queue" desc={reviewCount > 0 ? `${reviewCount} awaiting review` : "All caught up"} />
                        <QuickAction href="/search" icon={Search} label="Search" desc="Find documents by content" />
                        <QuickAction href="/governance" icon={Shield} label="Governance" desc="Policies, taxonomy, retention" />
                        <QuickAction href="/monitoring" icon={BarChart3} label="Monitoring" desc="Pipeline health & metrics" />
                    </div>
                </div>
            </div>
        </>
    );
}

// ── Sub-components ────────────────────────────────────

function StatCard({ icon: Icon, color, label, value, href, badge }: {
    icon: typeof FileText; color: string; label: string; value: number; href?: string; badge?: boolean;
}) {
    const colors: Record<string, { bg: string; text: string; icon: string }> = {
        blue: { bg: "bg-blue-50", text: "text-blue-700", icon: "text-blue-500" },
        green: { bg: "bg-green-50", text: "text-green-700", icon: "text-green-500" },
        amber: { bg: "bg-amber-50", text: "text-amber-700", icon: "text-amber-500" },
        red: { bg: "bg-red-50", text: "text-red-700", icon: "text-red-500" },
        gray: { bg: "bg-gray-50", text: "text-gray-700", icon: "text-gray-400" },
    };
    const c = colors[color] ?? colors.gray;
    const Wrapper = href ? Link : "div";
    return (
        <Wrapper href={href ?? ""} className={`relative ${c.bg} rounded-xl p-4 border border-transparent hover:border-gray-200 transition-colors`}>
            <div className="flex items-center gap-2 mb-1">
                <Icon className={`size-4 ${c.icon}`} />
                <span className="text-[11px] font-medium text-gray-500 uppercase tracking-wide">{label}</span>
            </div>
            <div className={`text-2xl font-bold ${c.text}`}>{value}</div>
            {badge && value > 0 && (
                <span className="absolute top-2 right-2 size-2.5 rounded-full bg-red-500 animate-pulse" />
            )}
        </Wrapper>
    );
}

function PipelineFunnel({ stats, total, activeFilter, onFilterChange }: {
    stats: Stats; total: number; activeFilter: string | null; onFilterChange: (filter: string | null) => void;
}) {
    const stages = [
        { label: "Uploaded", key: "UPLOADED", color: "bg-gray-300" },
        { label: "Processing", key: "PROCESSING", color: "bg-yellow-400" },
        { label: "Processed", key: "PROCESSED", color: "bg-yellow-300" },
        { label: "Classifying", key: "CLASSIFYING", color: "bg-blue-400" },
        { label: "Classified", key: "CLASSIFIED", color: "bg-blue-500" },
        { label: "Governed", key: "GOVERNANCE_APPLIED", color: "bg-green-500" },
        { label: "Inbox", key: "INBOX", color: "bg-blue-400" },
        { label: "Filed", key: "FILED", color: "bg-emerald-500" },
    ];
    const failStages = [
        { label: "Extract Failed", key: "PROCESSING_FAILED", color: "bg-red-400" },
        { label: "Classify Failed", key: "CLASSIFICATION_FAILED", color: "bg-red-500" },
        { label: "Enforce Failed", key: "ENFORCEMENT_FAILED", color: "bg-red-600" },
    ];
    const review = stats["REVIEW_REQUIRED"] ?? 0;
    const max = Math.max(total, 1);

    const FunnelRow = ({ label, skey, color, textColor = "text-gray-500", valueColor = "text-gray-700" }: {
        label: string; skey: string; color: string; textColor?: string; valueColor?: string;
    }) => {
        const v = stats[skey] ?? 0;
        const pct = (v / max) * 100;
        const active = activeFilter === skey;
        return (
            <button onClick={() => onFilterChange(active ? null : skey)}
                className={`flex items-center gap-3 w-full rounded-md px-1 py-0.5 transition-colors ${active ? "bg-blue-50 ring-1 ring-blue-200" : "hover:bg-gray-50"}`}>
                <span className={`text-[10px] ${textColor} w-20 text-right shrink-0`}>{label}</span>
                <div className="flex-1 bg-gray-100 rounded-full h-5 overflow-hidden">
                    <div className={`${color} h-full rounded-full transition-all duration-700`} style={{ width: `${Math.max(pct, v > 0 ? 2 : 0)}%` }} />
                </div>
                <span className={`text-xs font-medium ${valueColor} w-8 text-right`}>{v}</span>
            </button>
        );
    };

    return (
        <div className="space-y-2">
            {stages.map(s => <FunnelRow key={s.key} label={s.label} skey={s.key} color={s.color} />)}
            {review > 0 && (
                <FunnelRow label="Review" skey="REVIEW_REQUIRED" color="bg-amber-400" textColor="text-amber-600" valueColor="text-amber-700" />
            )}
            {failStages.some(s => (stats[s.key] ?? 0) > 0) && (
                <div className="pt-1 border-t border-gray-100 mt-1">
                    {failStages.filter(s => (stats[s.key] ?? 0) > 0).map(s => (
                        <FunnelRow key={s.key} label={s.label} skey={s.key} color={s.color} textColor="text-red-500" valueColor="text-red-600" />
                    ))}
                </div>
            )}
        </div>
    );
}

function MetricRow({ label, value, unit, warn }: { label: string; value: number; unit: string; warn?: boolean }) {
    return (
        <div className="flex items-center justify-between">
            <span className="text-xs text-gray-500">{label}</span>
            <span className={`text-sm font-semibold ${warn ? "text-red-600" : "text-gray-900"}`}>
                {value} <span className="text-[10px] font-normal text-gray-400">{unit}</span>
            </span>
        </div>
    );
}

function QuickAction({ href, icon: Icon, label, desc }: {
    href: string; icon: typeof Upload; label: string; desc: string;
}) {
    return (
        <Link href={href} className="flex items-center gap-3 p-3 rounded-lg border border-gray-100 hover:border-blue-200 hover:bg-blue-50/50 transition-colors group">
            <div className="size-8 rounded-lg bg-gray-50 flex items-center justify-center group-hover:bg-blue-100 transition-colors shrink-0">
                <Icon className="size-4 text-gray-400 group-hover:text-blue-600" />
            </div>
            <div className="min-w-0">
                <div className="text-sm font-medium text-gray-900 group-hover:text-blue-700">{label}</div>
                <div className="text-[10px] text-gray-400 truncate">{desc}</div>
            </div>
        </Link>
    );
}

function StatusDot({ status }: { status: string }) {
    const colors: Record<string, string> = {
        UPLOADED: "bg-gray-300", PROCESSING: "bg-yellow-400 animate-pulse",
        PROCESSED: "bg-yellow-300", CLASSIFYING: "bg-blue-400 animate-pulse",
        CLASSIFIED: "bg-blue-500", GOVERNANCE_APPLIED: "bg-green-500",
        INBOX: "bg-blue-400", FILED: "bg-emerald-500",
        REVIEW_REQUIRED: "bg-amber-400", PROCESSING_FAILED: "bg-red-500",
        CLASSIFICATION_FAILED: "bg-red-500", ENFORCEMENT_FAILED: "bg-red-500",
    };
    return <span className={`size-2 rounded-full shrink-0 ${colors[status] ?? "bg-gray-300"}`} />;
}

function EmptyState({ icon: Icon, message, action, href }: {
    icon: typeof Inbox; message: string; action: string; href: string;
}) {
    return (
        <div className="text-center py-8">
            <Icon className="size-10 text-gray-200 mx-auto mb-3" />
            <p className="text-sm text-gray-400 mb-2">{message}</p>
            <Link href={href} className="text-xs text-blue-600 hover:text-blue-800 font-medium">{action}</Link>
        </div>
    );
}

function timeAgo(iso: string): string {
    const diff = Date.now() - new Date(iso).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return "just now";
    if (mins < 60) return `${mins}m ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs}h ago`;
    const days = Math.floor(hrs / 24);
    return `${days}d ago`;
}
