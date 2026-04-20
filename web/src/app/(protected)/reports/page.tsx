"use client";

import { useCallback, useEffect, useState } from "react";
import api from "@/lib/axios/axios.client";
import {
    BarChart3, Brain, TrendingUp, Database, DollarSign, MessageSquare,
    RefreshCw, Loader2, AlertTriangle, CheckCircle, ArrowUpRight, Crosshair,
} from "lucide-react";
import {
    BarChart, Bar, LineChart, Line, AreaChart, Area,
    XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
    PieChart, Pie, Cell, ScatterChart, Scatter, ZAxis,
} from "recharts";

/* ── colour palette ─────────────────────────────────────── */
const COLORS = {
    bert: "#3b82f6",
    llm: "#8b5cf6",
    auto: "#3b82f6",
    manual: "#10b981",
    correction: "#f59e0b",
    bulk: "#6366f1",
    primary: "#3b82f6",
    secondary: "#8b5cf6",
    success: "#10b981",
    warning: "#f59e0b",
    danger: "#ef4444",
    muted: "#94a3b8",
};

const SENSITIVITY_COLORS: Record<string, string> = {
    PUBLIC: "#22c55e",
    INTERNAL: "#3b82f6",
    CONFIDENTIAL: "#f59e0b",
    RESTRICTED: "#ef4444",
};

const CORRECTION_COLORS: Record<string, string> = {
    CATEGORY_CHANGED: "#ef4444",
    SENSITIVITY_CHANGED: "#f59e0b",
    BOTH_CHANGED: "#8b5cf6",
    PII_FLAGGED: "#ec4899",
    PII_DISMISSED: "#94a3b8",
    APPROVED_CORRECT: "#22c55e",
};

type Tab = "performance" | "quality" | "training" | "cost" | "feedback" | "scatter";

const TABS: { key: Tab; label: string; icon: React.ElementType }[] = [
    { key: "performance", label: "Model Performance", icon: Brain },
    { key: "quality", label: "Classification Quality", icon: TrendingUp },
    { key: "training", label: "Training Data", icon: Database },
    { key: "cost", label: "Cost & Efficiency", icon: DollarSign },
    { key: "feedback", label: "Feedback Loop", icon: MessageSquare },
    { key: "scatter", label: "Scatter Analysis", icon: Crosshair },
];

/* ── types ───────────────────────────────────────────────── */
type ConfidenceBucket = { range: string; bert: number; llm: number; total: number };
type ModelVersion = {
    version: string; accuracy: number | null; f1: number | null; loss: number | null;
    sampleCount: number; categoryCount: number; promoted: boolean;
    startedAt: string | null; completedAt: string | null;
    perClass: Record<string, { precision: number; recall: number; f1: number; support: number }> | null;
    warnings: string[] | null;
};
type DailyHitRate = { date: string; bert: number; llm: number; total: number; hitRate: number };
type TrainingCategory = {
    category: string; autoCollected: number; manual: number;
    correction: number; bulkImport: number; total: number; graduated: boolean;
};
type GrowthPoint = {
    date: string; autoCollected: number; manual: number;
    correction: number; bulkImport: number; total: number;
};
type DailyCost = {
    date: string; inputTokens: number; outputTokens: number; totalTokens: number;
    cost: number; avgLatencyMs: number; calls: number;
};
type CorrectionDaily = {
    date: string; corrections: number; approvals: number;
    total: number; overrideRate: number;
};
type DailyVolume = { date: string; count: number };

type ConfidenceLatencyPoint = {
    confidence: number; latencyMs: number; provider: string; model: string; tokens: number;
};
type SamplesAccuracyPoint = {
    category: string; samples: number; f1: number; precision: number; recall: number; support: number;
};
type TokensCostPoint = {
    totalTokens: number; inputTokens: number; outputTokens: number;
    cost: number; provider: string; model: string; usageType: string; latencyMs: number;
};
type SizeConfidencePoint = {
    textLength: number; fileSizeBytes: number; confidence: number;
    category: string; classifier: string; sensitivity: string | null;
};

export default function ReportsPage() {
    const [tab, setTab] = useState<Tab>("performance");
    const [loading, setLoading] = useState(false);

    // Data states
    const [confidenceDist, setConfidenceDist] = useState<{ buckets: ConfidenceBucket[]; totalClassifications: number } | null>(null);
    const [modelPerf, setModelPerf] = useState<{ versions: ModelVersion[] } | null>(null);
    const [bertHitRate, setBertHitRate] = useState<{ daily: DailyHitRate[] } | null>(null);
    const [trainingData, setTrainingData] = useState<{
        categories: TrainingCategory[]; growth: GrowthPoint[];
        totalSamples: number; graduationThreshold: number;
    } | null>(null);
    const [aiCost, setAiCost] = useState<{
        daily: DailyCost[];
        byProvider: Record<string, { calls: number; totalTokens: number; totalCost: number; avgLatencyMs: number }>;
        byModel: Record<string, { calls: number; totalCost: number }>;
    } | null>(null);
    const [corrections, setCorrections] = useState<{
        byType: Record<string, number>; byCategory: Record<string, number>;
        daily: CorrectionDaily[]; confusionPairs: Record<string, number>;
        totalCorrections: number;
    } | null>(null);
    const [classOverview, setClassOverview] = useState<{
        byCategory: Record<string, number>; bySensitivity: Record<string, number>;
        dailyVolume: DailyVolume[]; total: number;
    } | null>(null);
    const [scatterData, setScatterData] = useState<{
        confidenceLatency: ConfidenceLatencyPoint[];
        samplesAccuracy: { points: SamplesAccuracyPoint[]; version: string };
        tokensCost: TokensCostPoint[];
        sizeConfidence: SizeConfidencePoint[];
    } | null>(null);

    const loadTab = useCallback(async (t: Tab) => {
        setLoading(true);
        try {
            switch (t) {
                case "performance": {
                    const [mp, bhr] = await Promise.all([
                        api.get("/admin/reports/model-performance"),
                        api.get("/admin/reports/bert-hit-rate?days=30"),
                    ]);
                    setModelPerf(mp.data);
                    setBertHitRate(bhr.data);
                    break;
                }
                case "quality": {
                    const [cd, co] = await Promise.all([
                        api.get("/admin/reports/confidence-distribution"),
                        api.get("/admin/reports/classification-overview?days=30"),
                    ]);
                    setConfidenceDist(cd.data);
                    setClassOverview(co.data);
                    break;
                }
                case "training": {
                    const { data } = await api.get("/admin/reports/training-data");
                    setTrainingData(data);
                    break;
                }
                case "cost": {
                    const { data } = await api.get("/admin/reports/ai-cost?days=30");
                    setAiCost(data);
                    break;
                }
                case "feedback": {
                    const { data } = await api.get("/admin/reports/corrections");
                    setCorrections(data);
                    break;
                }
                case "scatter": {
                    const [cl, sa, tc, sc] = await Promise.all([
                        api.get("/admin/reports/scatter/confidence-vs-latency"),
                        api.get("/admin/reports/scatter/samples-vs-accuracy"),
                        api.get("/admin/reports/scatter/tokens-vs-cost"),
                        api.get("/admin/reports/scatter/size-vs-confidence"),
                    ]);
                    setScatterData({
                        confidenceLatency: cl.data.points,
                        samplesAccuracy: { points: sa.data.points, version: sa.data.version },
                        tokensCost: tc.data.points,
                        sizeConfidence: sc.data.points,
                    });
                    break;
                }
            }
        } catch (e) {
            console.error("Failed to load report data", e);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { loadTab(tab); }, [tab, loadTab]);

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <BarChart3 className="size-6 text-blue-600" />
                    <h1 className="text-2xl font-bold text-gray-900">ML Reports</h1>
                </div>
                <button onClick={() => loadTab(tab)}
                    className="flex items-center gap-2 px-3 py-2 text-sm bg-white border border-gray-200 rounded-lg hover:bg-gray-50">
                    <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
                    Refresh
                </button>
            </div>

            {/* Tabs */}
            <div className="flex gap-1 bg-gray-100 rounded-lg p-1">
                {TABS.map(t => (
                    <button key={t.key}
                        onClick={() => setTab(t.key)}
                        className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors flex-1 justify-center ${
                            tab === t.key ? "bg-white text-blue-700 shadow-sm" : "text-gray-600 hover:text-gray-900"
                        }`}>
                        <t.icon className="size-4" />
                        {t.label}
                    </button>
                ))}
            </div>

            {/* Content */}
            {loading ? (
                <div className="flex items-center justify-center py-20">
                    <Loader2 className="size-8 animate-spin text-blue-500" />
                </div>
            ) : (
                <>
                    {tab === "performance" && <ModelPerformanceTab data={modelPerf} hitRate={bertHitRate} />}
                    {tab === "quality" && <ClassificationQualityTab confidence={confidenceDist} overview={classOverview} />}
                    {tab === "training" && <TrainingDataTab data={trainingData} />}
                    {tab === "cost" && <CostTab data={aiCost} />}
                    {tab === "feedback" && <FeedbackTab data={corrections} />}
                    {tab === "scatter" && <ScatterAnalysisTab data={scatterData} />}
                </>
            )}
        </div>
    );
}

/* ══════════════════════════════════════════════════════════
   TAB 1 — Model Performance
   ══════════════════════════════════════════════════════════ */

function ModelPerformanceTab({ data, hitRate }: {
    data: { versions: ModelVersion[] } | null;
    hitRate: { daily: DailyHitRate[] } | null;
}) {
    if (!data) return <EmptyState message="No model performance data available" />;

    const versions = data.versions;
    const promoted = versions.find(v => v.promoted);

    // Find selected version for per-class detail
    const [selectedVersion, setSelectedVersion] = useState<string | null>(null);
    const selectedJob = versions.find(v => v.version === selectedVersion);

    return (
        <div className="space-y-6">
            {/* Summary cards */}
            <div className="grid grid-cols-4 gap-4">
                <StatCard label="Training Runs" value={versions.length} icon={Brain} />
                <StatCard label="Active Model" value={promoted?.version ?? "None"} icon={CheckCircle}
                    color={promoted ? "text-green-600" : "text-gray-400"} />
                <StatCard label="Best Accuracy"
                    value={versions.length > 0
                        ? `${(Math.max(...versions.filter(v => v.accuracy != null).map(v => v.accuracy!)) * 100).toFixed(1)}%`
                        : "N/A"}
                    icon={TrendingUp} />
                <StatCard label="Best F1"
                    value={versions.length > 0
                        ? `${(Math.max(...versions.filter(v => v.f1 != null).map(v => v.f1!)) * 100).toFixed(1)}%`
                        : "N/A"}
                    icon={TrendingUp} />
            </div>

            {/* Accuracy & F1 over versions */}
            {versions.length > 0 && (
                <ChartCard title="Accuracy & F1 Over Training Versions"
                    subtitle="Track model improvement across training runs">
                    <ResponsiveContainer width="100%" height={320}>
                        <LineChart data={versions.filter(v => v.accuracy != null)}
                            onClick={(e) => { if (e?.activeLabel) setSelectedVersion(e.activeLabel as string); }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis dataKey="version" tick={{ fontSize: 12 }} />
                            <YAxis domain={[0, 1]} tickFormatter={v => `${(v * 100).toFixed(0)}%`} tick={{ fontSize: 12 }} />
                            <Tooltip formatter={(v) => `${(Number(v) * 100).toFixed(1)}%`} />
                            <Legend />
                            <Line type="monotone" dataKey="accuracy" stroke={COLORS.primary} strokeWidth={2}
                                dot={{ r: 5 }} activeDot={{ r: 7 }} name="Accuracy" />
                            <Line type="monotone" dataKey="f1" stroke={COLORS.secondary} strokeWidth={2}
                                dot={{ r: 5 }} activeDot={{ r: 7 }} name="F1 Score" />
                        </LineChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}

            {/* Sample count per version */}
            {versions.length > 0 && (
                <ChartCard title="Training Samples Per Version"
                    subtitle="Number of training samples and categories used in each run">
                    <ResponsiveContainer width="100%" height={280}>
                        <BarChart data={versions}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis dataKey="version" tick={{ fontSize: 12 }} />
                            <YAxis tick={{ fontSize: 12 }} />
                            <Tooltip />
                            <Legend />
                            <Bar dataKey="sampleCount" fill={COLORS.primary} name="Samples" radius={[4, 4, 0, 0]} />
                            <Bar dataKey="categoryCount" fill={COLORS.success} name="Categories" radius={[4, 4, 0, 0]} />
                        </BarChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}

            {/* Per-class metrics for selected version */}
            {selectedJob?.perClass && (
                <ChartCard title={`Per-Class Metrics — ${selectedJob.version}`}
                    subtitle="Precision, recall, and F1 for each category. Click a version above to change.">
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-gray-200">
                                    <th className="text-left py-2 px-3 font-medium text-gray-600">Category</th>
                                    <th className="text-right py-2 px-3 font-medium text-gray-600">Precision</th>
                                    <th className="text-right py-2 px-3 font-medium text-gray-600">Recall</th>
                                    <th className="text-right py-2 px-3 font-medium text-gray-600">F1</th>
                                    <th className="text-right py-2 px-3 font-medium text-gray-600">Support</th>
                                </tr>
                            </thead>
                            <tbody>
                                {Object.entries(selectedJob.perClass)
                                    .sort(([, a], [, b]) => (b.f1 ?? 0) - (a.f1 ?? 0))
                                    .map(([cat, m]) => (
                                    <tr key={cat} className="border-b border-gray-100 hover:bg-gray-50">
                                        <td className="py-2 px-3 font-medium">{cat}</td>
                                        <td className="text-right py-2 px-3">
                                            <MetricBadge value={m.precision} />
                                        </td>
                                        <td className="text-right py-2 px-3">
                                            <MetricBadge value={m.recall} />
                                        </td>
                                        <td className="text-right py-2 px-3">
                                            <MetricBadge value={m.f1} />
                                        </td>
                                        <td className="text-right py-2 px-3 text-gray-500">{m.support}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    {selectedJob.warnings && selectedJob.warnings.length > 0 && (
                        <div className="mt-4 p-3 bg-amber-50 rounded-lg">
                            <div className="flex items-center gap-2 text-amber-700 text-sm font-medium mb-1">
                                <AlertTriangle className="size-4" /> Training Warnings
                            </div>
                            <ul className="text-sm text-amber-600 space-y-1">
                                {selectedJob.warnings.map((w, i) => <li key={i}>{w}</li>)}
                            </ul>
                        </div>
                    )}
                </ChartCard>
            )}

            {/* BERT hit rate over time */}
            {hitRate && hitRate.daily.length > 0 && (
                <ChartCard title="BERT Hit Rate (Last 30 Days)"
                    subtitle="Percentage of classifications handled by BERT vs passed to LLM">
                    <ResponsiveContainer width="100%" height={320}>
                        <AreaChart data={hitRate.daily}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis dataKey="date" tick={{ fontSize: 11 }} tickFormatter={d => d.substring(5)} />
                            <YAxis tick={{ fontSize: 12 }} />
                            <Tooltip />
                            <Legend />
                            <Area type="monotone" dataKey="bert" stackId="1" fill={COLORS.bert}
                                stroke={COLORS.bert} fillOpacity={0.6} name="BERT" />
                            <Area type="monotone" dataKey="llm" stackId="1" fill={COLORS.llm}
                                stroke={COLORS.llm} fillOpacity={0.6} name="LLM" />
                        </AreaChart>
                    </ResponsiveContainer>
                    {/* Hit rate trend line */}
                    <ResponsiveContainer width="100%" height={200}>
                        <LineChart data={hitRate.daily}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis dataKey="date" tick={{ fontSize: 11 }} tickFormatter={d => d.substring(5)} />
                            <YAxis domain={[0, 100]} tickFormatter={v => `${v}%`} tick={{ fontSize: 12 }} />
                            <Tooltip formatter={(v) => `${v}%`} />
                            <Line type="monotone" dataKey="hitRate" stroke={COLORS.primary} strokeWidth={2}
                                dot={{ r: 3 }} name="BERT Hit Rate %" />
                        </LineChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}
        </div>
    );
}

/* ══════════════════════════════════════════════════════════
   TAB 2 — Classification Quality
   ══════════════════════════════════════════════════════════ */

function ClassificationQualityTab({ confidence, overview }: {
    confidence: { buckets: ConfidenceBucket[]; totalClassifications: number } | null;
    overview: {
        byCategory: Record<string, number>; bySensitivity: Record<string, number>;
        dailyVolume: DailyVolume[]; total: number;
    } | null;
}) {
    if (!confidence && !overview) return <EmptyState message="No classification data available" />;

    const sensitivityData = overview?.bySensitivity
        ? Object.entries(overview.bySensitivity).map(([name, value]) => ({ name, value }))
        : [];

    const categoryData = overview?.byCategory
        ? Object.entries(overview.byCategory).map(([name, value]) => ({ name, value }))
        : [];

    return (
        <div className="space-y-6">
            {/* Summary */}
            {overview && (
                <div className="grid grid-cols-4 gap-4">
                    <StatCard label="Total Classifications (30d)" value={overview.total} icon={BarChart3} />
                    <StatCard label="Categories Used" value={Object.keys(overview.byCategory).length} icon={Database} />
                    <StatCard label="Avg Daily Volume"
                        value={overview.dailyVolume.length > 0
                            ? Math.round(overview.total / overview.dailyVolume.length)
                            : 0}
                        icon={TrendingUp} />
                    <StatCard label="Total Confidence Dist"
                        value={confidence?.totalClassifications ?? 0} icon={BarChart3} />
                </div>
            )}

            {/* Confidence distribution histogram */}
            {confidence && confidence.buckets.length > 0 && (
                <ChartCard title="Confidence Score Distribution"
                    subtitle="How confident are BERT and LLM classifications? Poorly calibrated models cluster at extremes.">
                    <ResponsiveContainer width="100%" height={320}>
                        <BarChart data={confidence.buckets}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis dataKey="range" tick={{ fontSize: 11 }} />
                            <YAxis tick={{ fontSize: 12 }} />
                            <Tooltip />
                            <Legend />
                            <Bar dataKey="bert" fill={COLORS.bert} name="BERT" radius={[4, 4, 0, 0]} />
                            <Bar dataKey="llm" fill={COLORS.llm} name="LLM" radius={[4, 4, 0, 0]} />
                        </BarChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}

            <div className="grid grid-cols-2 gap-6">
                {/* Sensitivity distribution */}
                {sensitivityData.length > 0 && (
                    <ChartCard title="Sensitivity Distribution" subtitle="Classification sensitivity breakdown">
                        <ResponsiveContainer width="100%" height={280}>
                            <PieChart>
                                <Pie data={sensitivityData} dataKey="value" nameKey="name"
                                    cx="50%" cy="50%" outerRadius={100} label={({ name, percent }) =>
                                        `${name} ${((percent ?? 0) * 100).toFixed(0)}%`}>
                                    {sensitivityData.map(d => (
                                        <Cell key={d.name}
                                            fill={SENSITIVITY_COLORS[d.name] ?? COLORS.muted} />
                                    ))}
                                </Pie>
                                <Tooltip />
                            </PieChart>
                        </ResponsiveContainer>
                    </ChartCard>
                )}

                {/* Top categories */}
                {categoryData.length > 0 && (
                    <ChartCard title="Top Categories (30d)" subtitle="Most frequently assigned classification categories">
                        <ResponsiveContainer width="100%" height={280}>
                            <BarChart data={categoryData.slice(0, 10)} layout="vertical">
                                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                                <XAxis type="number" tick={{ fontSize: 12 }} />
                                <YAxis type="category" dataKey="name" width={150}
                                    tick={{ fontSize: 11 }} />
                                <Tooltip />
                                <Bar dataKey="value" fill={COLORS.primary} name="Classifications"
                                    radius={[0, 4, 4, 0]} />
                            </BarChart>
                        </ResponsiveContainer>
                    </ChartCard>
                )}
            </div>

            {/* Daily classification volume */}
            {overview && overview.dailyVolume.length > 0 && (
                <ChartCard title="Daily Classification Volume (30d)"
                    subtitle="Number of documents classified per day">
                    <ResponsiveContainer width="100%" height={240}>
                        <AreaChart data={overview.dailyVolume}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis dataKey="date" tick={{ fontSize: 11 }} tickFormatter={d => d.substring(5)} />
                            <YAxis tick={{ fontSize: 12 }} />
                            <Tooltip />
                            <Area type="monotone" dataKey="count" fill={COLORS.primary}
                                stroke={COLORS.primary} fillOpacity={0.3} name="Classifications" />
                        </AreaChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}
        </div>
    );
}

/* ══════════════════════════════════════════════════════════
   TAB 3 — Training Data Health
   ══════════════════════════════════════════════════════════ */

function TrainingDataTab({ data }: {
    data: {
        categories: TrainingCategory[]; growth: GrowthPoint[];
        totalSamples: number; graduationThreshold: number;
    } | null;
}) {
    if (!data) return <EmptyState message="No training data available" />;

    const graduated = data.categories.filter(c => c.graduated).length;
    const notGraduated = data.categories.filter(c => !c.graduated).length;

    return (
        <div className="space-y-6">
            {/* Summary */}
            <div className="grid grid-cols-4 gap-4">
                <StatCard label="Total Samples" value={data.totalSamples} icon={Database} />
                <StatCard label="Categories" value={data.categories.length} icon={BarChart3} />
                <StatCard label="Graduated" value={graduated} icon={CheckCircle} color="text-green-600" />
                <StatCard label="Below Threshold" value={notGraduated} icon={AlertTriangle}
                    color={notGraduated > 0 ? "text-amber-600" : "text-gray-400"} />
            </div>

            {/* Category sample distribution */}
            {data.categories.length > 0 && (
                <ChartCard title="Samples Per Category (by Source)"
                    subtitle={`Categories need ${data.graduationThreshold}+ samples to graduate. Red line = threshold.`}>
                    <ResponsiveContainer width="100%" height={Math.max(300, data.categories.length * 32)}>
                        <BarChart data={data.categories} layout="vertical">
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis type="number" tick={{ fontSize: 12 }} />
                            <YAxis type="category" dataKey="category" width={180}
                                tick={{ fontSize: 11 }} />
                            <Tooltip />
                            <Legend />
                            <Bar dataKey="autoCollected" stackId="a" fill={COLORS.auto} name="Auto-Collected" />
                            <Bar dataKey="manual" stackId="a" fill={COLORS.manual} name="Manual Upload" />
                            <Bar dataKey="correction" stackId="a" fill={COLORS.correction} name="Corrections" />
                            <Bar dataKey="bulkImport" stackId="a" fill={COLORS.bulk} name="Bulk Import" />
                        </BarChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}

            {/* Graduation progress */}
            {data.categories.length > 0 && (
                <ChartCard title="Graduation Progress"
                    subtitle="Categories approaching the training threshold">
                    <div className="space-y-2 max-h-96 overflow-y-auto">
                        {data.categories.map(c => (
                            <div key={c.category} className="flex items-center gap-3">
                                <span className="text-sm text-gray-700 w-48 truncate" title={c.category}>
                                    {c.category}
                                </span>
                                <div className="flex-1 bg-gray-100 rounded-full h-5 relative overflow-hidden">
                                    <div className={`h-full rounded-full transition-all ${
                                        c.graduated ? "bg-green-500" : "bg-amber-400"}`}
                                        style={{ width: `${Math.min(100, (c.total / data.graduationThreshold) * 100)}%` }} />
                                    {!c.graduated && (
                                        <div className="absolute inset-0 flex items-center justify-end pr-2">
                                            <span className="text-[10px] font-medium text-gray-500">
                                                {c.total}/{data.graduationThreshold}
                                            </span>
                                        </div>
                                    )}
                                </div>
                                <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${
                                    c.graduated
                                        ? "bg-green-100 text-green-700"
                                        : "bg-amber-100 text-amber-700"
                                }`}>
                                    {c.total}
                                </span>
                            </div>
                        ))}
                    </div>
                </ChartCard>
            )}

            {/* Cumulative data growth */}
            {data.growth.length > 0 && (
                <ChartCard title="Training Data Growth"
                    subtitle="Cumulative training samples over time, by collection source">
                    <ResponsiveContainer width="100%" height={320}>
                        <AreaChart data={data.growth}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis dataKey="date" tick={{ fontSize: 11 }} tickFormatter={d => d.substring(5)} />
                            <YAxis tick={{ fontSize: 12 }} />
                            <Tooltip />
                            <Legend />
                            <Area type="monotone" dataKey="autoCollected" stackId="1" fill={COLORS.auto}
                                stroke={COLORS.auto} fillOpacity={0.6} name="Auto-Collected" />
                            <Area type="monotone" dataKey="manual" stackId="1" fill={COLORS.manual}
                                stroke={COLORS.manual} fillOpacity={0.6} name="Manual" />
                            <Area type="monotone" dataKey="correction" stackId="1" fill={COLORS.correction}
                                stroke={COLORS.correction} fillOpacity={0.6} name="Corrections" />
                            <Area type="monotone" dataKey="bulkImport" stackId="1" fill={COLORS.bulk}
                                stroke={COLORS.bulk} fillOpacity={0.6} name="Bulk Import" />
                        </AreaChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}
        </div>
    );
}

/* ══════════════════════════════════════════════════════════
   TAB 4 — Cost & Efficiency
   ══════════════════════════════════════════════════════════ */

function CostTab({ data }: {
    data: {
        daily: DailyCost[];
        byProvider: Record<string, { calls: number; totalTokens: number; totalCost: number; avgLatencyMs: number }>;
        byModel: Record<string, { calls: number; totalCost: number }>;
    } | null;
}) {
    if (!data) return <EmptyState message="No AI usage data available" />;

    const totalCost = data.daily.reduce((sum, d) => sum + d.cost, 0);
    const totalCalls = data.daily.reduce((sum, d) => sum + d.calls, 0);
    const totalTokens = data.daily.reduce((sum, d) => sum + d.totalTokens, 0);
    const avgLatency = totalCalls > 0
        ? Math.round(data.daily.reduce((sum, d) => sum + d.avgLatencyMs * d.calls, 0) / totalCalls)
        : 0;

    const providerData = Object.entries(data.byProvider).map(([name, d]) => ({
        name, ...d,
    }));

    const modelData = Object.entries(data.byModel)
        .map(([name, d]) => ({ name, ...d }))
        .sort((a, b) => b.totalCost - a.totalCost);

    return (
        <div className="space-y-6">
            {/* Summary */}
            <div className="grid grid-cols-4 gap-4">
                <StatCard label="Total Cost (30d)" value={`$${totalCost.toFixed(4)}`} icon={DollarSign} />
                <StatCard label="Total Calls" value={totalCalls} icon={BarChart3} />
                <StatCard label="Total Tokens" value={formatNumber(totalTokens)} icon={TrendingUp} />
                <StatCard label="Avg Latency" value={`${avgLatency}ms`} icon={TrendingUp} />
            </div>

            {/* Daily cost trend */}
            {data.daily.length > 0 && (
                <ChartCard title="Daily AI Cost (30d)" subtitle="Estimated spend per day across all AI providers">
                    <ResponsiveContainer width="100%" height={280}>
                        <AreaChart data={data.daily}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis dataKey="date" tick={{ fontSize: 11 }} tickFormatter={d => d.substring(5)} />
                            <YAxis tickFormatter={v => `$${v}`} tick={{ fontSize: 12 }} />
                            <Tooltip formatter={(v) => `$${Number(v).toFixed(4)}`} />
                            <Area type="monotone" dataKey="cost" fill={COLORS.warning}
                                stroke={COLORS.warning} fillOpacity={0.3} name="Cost (USD)" />
                        </AreaChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}

            {/* Token usage */}
            {data.daily.length > 0 && (
                <ChartCard title="Daily Token Usage" subtitle="Input vs output tokens consumed per day">
                    <ResponsiveContainer width="100%" height={280}>
                        <AreaChart data={data.daily}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis dataKey="date" tick={{ fontSize: 11 }} tickFormatter={d => d.substring(5)} />
                            <YAxis tickFormatter={v => formatNumber(v)} tick={{ fontSize: 12 }} />
                            <Tooltip formatter={(v) => formatNumber(Number(v))} />
                            <Legend />
                            <Area type="monotone" dataKey="inputTokens" stackId="1" fill={COLORS.primary}
                                stroke={COLORS.primary} fillOpacity={0.6} name="Input Tokens" />
                            <Area type="monotone" dataKey="outputTokens" stackId="1" fill={COLORS.secondary}
                                stroke={COLORS.secondary} fillOpacity={0.6} name="Output Tokens" />
                        </AreaChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}

            {/* Latency trend */}
            {data.daily.length > 0 && (
                <ChartCard title="Average Latency Per Day" subtitle="Mean AI response time in milliseconds">
                    <ResponsiveContainer width="100%" height={240}>
                        <LineChart data={data.daily}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis dataKey="date" tick={{ fontSize: 11 }} tickFormatter={d => d.substring(5)} />
                            <YAxis tickFormatter={v => `${v}ms`} tick={{ fontSize: 12 }} />
                            <Tooltip formatter={(v) => `${v}ms`} />
                            <Line type="monotone" dataKey="avgLatencyMs" stroke={COLORS.danger} strokeWidth={2}
                                dot={{ r: 3 }} name="Avg Latency" />
                        </LineChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}

            {/* Provider & model breakdown */}
            <div className="grid grid-cols-2 gap-6">
                {providerData.length > 0 && (
                    <ChartCard title="By Provider" subtitle="Cost and usage breakdown per AI provider">
                        <div className="space-y-3">
                            {providerData.map(p => (
                                <div key={p.name} className="p-3 bg-gray-50 rounded-lg">
                                    <div className="flex items-center justify-between mb-1">
                                        <span className="font-medium text-sm">{p.name}</span>
                                        <span className="text-sm text-gray-500">${p.totalCost.toFixed(4)}</span>
                                    </div>
                                    <div className="flex gap-4 text-xs text-gray-500">
                                        <span>{p.calls} calls</span>
                                        <span>{formatNumber(p.totalTokens)} tokens</span>
                                        <span>{p.avgLatencyMs}ms avg</span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </ChartCard>
                )}

                {modelData.length > 0 && (
                    <ChartCard title="By Model" subtitle="Usage breakdown per AI model">
                        <ResponsiveContainer width="100%" height={Math.max(200, modelData.length * 40)}>
                            <BarChart data={modelData} layout="vertical">
                                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                                <XAxis type="number" tickFormatter={v => `$${v}`} tick={{ fontSize: 12 }} />
                                <YAxis type="category" dataKey="name" width={180} tick={{ fontSize: 10 }} />
                                <Tooltip formatter={(v) => `$${Number(v).toFixed(4)}`} />
                                <Bar dataKey="totalCost" fill={COLORS.secondary} name="Total Cost"
                                    radius={[0, 4, 4, 0]} />
                            </BarChart>
                        </ResponsiveContainer>
                    </ChartCard>
                )}
            </div>
        </div>
    );
}

/* ══════════════════════════════════════════════════════════
   TAB 5 — Feedback Loop
   ══════════════════════════════════════════════════════════ */

function FeedbackTab({ data }: {
    data: {
        byType: Record<string, number>; byCategory: Record<string, number>;
        daily: CorrectionDaily[]; confusionPairs: Record<string, number>;
        totalCorrections: number;
    } | null;
}) {
    if (!data) return <EmptyState message="No correction data available" />;

    const typeData = Object.entries(data.byType).map(([name, value]) => ({ name, value }));
    const categoryData = Object.entries(data.byCategory).map(([name, value]) => ({ name, value }));
    const confusionData = Object.entries(data.confusionPairs).map(([name, value]) => ({ name, value }));

    const overrides = Object.entries(data.byType)
        .filter(([k]) => k !== "APPROVED_CORRECT")
        .reduce((sum, [, v]) => sum + v, 0);
    const approvals = data.byType["APPROVED_CORRECT"] ?? 0;
    const overrideRate = data.totalCorrections > 0
        ? ((overrides / data.totalCorrections) * 100).toFixed(1)
        : "0";

    return (
        <div className="space-y-6">
            {/* Summary */}
            <div className="grid grid-cols-4 gap-4">
                <StatCard label="Total Reviews" value={data.totalCorrections} icon={MessageSquare} />
                <StatCard label="Overrides" value={overrides} icon={ArrowUpRight} color="text-red-600" />
                <StatCard label="Approvals" value={approvals} icon={CheckCircle} color="text-green-600" />
                <StatCard label="Override Rate" value={`${overrideRate}%`} icon={TrendingUp}
                    color={Number(overrideRate) > 20 ? "text-red-600" : "text-green-600"} />
            </div>

            {/* Override rate warning */}
            {Number(overrideRate) > 20 && (
                <div className="p-4 bg-red-50 border border-red-200 rounded-lg flex items-start gap-3">
                    <AlertTriangle className="size-5 text-red-500 mt-0.5" />
                    <div>
                        <p className="font-medium text-red-800">High override rate detected ({overrideRate}%)</p>
                        <p className="text-sm text-red-600 mt-1">
                            More than 20% of reviewed classifications are being overridden.
                            This indicates model drift or poor classification accuracy. Consider retraining.
                        </p>
                    </div>
                </div>
            )}

            <div className="grid grid-cols-2 gap-6">
                {/* Correction type distribution */}
                {typeData.length > 0 && (
                    <ChartCard title="Correction Types" subtitle="Breakdown of human review outcomes">
                        <ResponsiveContainer width="100%" height={280}>
                            <PieChart>
                                <Pie data={typeData} dataKey="value" nameKey="name"
                                    cx="50%" cy="50%" outerRadius={100}
                                    label={({ name, percent }) =>
                                        `${(name ?? "").replace(/_/g, " ")} ${((percent ?? 0) * 100).toFixed(0)}%`}
                                    labelLine={{ strokeWidth: 1 }}>
                                    {typeData.map(d => (
                                        <Cell key={d.name}
                                            fill={CORRECTION_COLORS[d.name] ?? COLORS.muted} />
                                    ))}
                                </Pie>
                                <Tooltip />
                            </PieChart>
                        </ResponsiveContainer>
                    </ChartCard>
                )}

                {/* Confusion pairs */}
                {confusionData.length > 0 && (
                    <ChartCard title="Top Confusion Pairs"
                        subtitle="Most common category misclassifications (original to corrected)">
                        <div className="space-y-2">
                            {confusionData.map(({ name, value }) => (
                                <div key={name} className="flex items-center gap-3">
                                    <span className="text-xs text-gray-600 flex-1 truncate" title={name}>
                                        {name}
                                    </span>
                                    <div className="w-24 bg-gray-100 rounded-full h-4 overflow-hidden">
                                        <div className="h-full bg-red-400 rounded-full"
                                            style={{ width: `${Math.min(100, (value / Math.max(...confusionData.map(d => d.value))) * 100)}%` }} />
                                    </div>
                                    <span className="text-xs font-medium text-gray-500 w-8 text-right">{value}</span>
                                </div>
                            ))}
                        </div>
                    </ChartCard>
                )}
            </div>

            {/* Most overridden categories */}
            {categoryData.length > 0 && (
                <ChartCard title="Most Overridden Categories"
                    subtitle="Categories with the highest number of human corrections">
                    <ResponsiveContainer width="100%" height={Math.max(200, categoryData.length * 32)}>
                        <BarChart data={categoryData} layout="vertical">
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis type="number" tick={{ fontSize: 12 }} />
                            <YAxis type="category" dataKey="name" width={180} tick={{ fontSize: 11 }} />
                            <Tooltip />
                            <Bar dataKey="value" fill={COLORS.danger} name="Corrections"
                                radius={[0, 4, 4, 0]} />
                        </BarChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}

            {/* Override rate over time */}
            {data.daily.length > 0 && (
                <ChartCard title="Override Rate Over Time"
                    subtitle="Daily override rate — red dashed line marks the 20% drift threshold">
                    <ResponsiveContainer width="100%" height={280}>
                        <LineChart data={data.daily}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis dataKey="date" tick={{ fontSize: 11 }} tickFormatter={d => d.substring(5)} />
                            <YAxis domain={[0, 100]} tickFormatter={v => `${v}%`} tick={{ fontSize: 12 }} />
                            <Tooltip formatter={(v) => `${v}%`} />
                            <Legend />
                            <Line type="monotone" dataKey="overrideRate" stroke={COLORS.danger} strokeWidth={2}
                                dot={{ r: 3 }} name="Override Rate" />
                            {/* 20% threshold reference line */}
                            <Line type="monotone" dataKey={() => 20} stroke={COLORS.danger}
                                strokeDasharray="5 5" strokeWidth={1} dot={false} name="Drift Threshold (20%)" />
                        </LineChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}
        </div>
    );
}

/* ══════════════════════════════════════════════════════════
   TAB 6 — Scatter Analysis
   ══════════════════════════════════════════════════════════ */

const SCATTER_PROVIDER_COLORS: Record<string, string> = {
    anthropic: "#3b82f6",
    ollama: "#10b981",
};

const CLASSIFIER_COLORS: Record<string, string> = {
    BERT: "#3b82f6",
    LLM: "#8b5cf6",
};

function ScatterAnalysisTab({ data }: {
    data: {
        confidenceLatency: ConfidenceLatencyPoint[];
        samplesAccuracy: { points: SamplesAccuracyPoint[]; version: string };
        tokensCost: TokensCostPoint[];
        sizeConfidence: SizeConfidencePoint[];
    } | null;
}) {
    if (!data) return <EmptyState message="No scatter data available" />;

    // Split confidence-latency by provider
    const clByProvider = new Map<string, ConfidenceLatencyPoint[]>();
    for (const p of data.confidenceLatency) {
        const key = p.provider ?? "unknown";
        if (!clByProvider.has(key)) clByProvider.set(key, []);
        clByProvider.get(key)!.push(p);
    }

    // Split size-confidence by classifier
    const scByClassifier = new Map<string, SizeConfidencePoint[]>();
    for (const p of data.sizeConfidence) {
        const key = p.classifier ?? "unknown";
        if (!scByClassifier.has(key)) scByClassifier.set(key, []);
        scByClassifier.get(key)!.push(p);
    }

    // Split tokens-cost by provider
    const tcByProvider = new Map<string, TokensCostPoint[]>();
    for (const p of data.tokensCost) {
        const key = p.provider ?? "unknown";
        if (!tcByProvider.has(key)) tcByProvider.set(key, []);
        tcByProvider.get(key)!.push(p);
    }

    return (
        <div className="space-y-6">
            {/* Summary */}
            <div className="grid grid-cols-4 gap-4">
                <StatCard label="Confidence vs Latency" value={data.confidenceLatency.length} icon={Crosshair} />
                <StatCard label="Samples vs F1" value={data.samplesAccuracy.points.length} icon={Crosshair} />
                <StatCard label="Tokens vs Cost" value={data.tokensCost.length} icon={Crosshair} />
                <StatCard label="Size vs Confidence" value={data.sizeConfidence.length} icon={Crosshair} />
            </div>

            {/* 1. Confidence vs Latency */}
            {data.confidenceLatency.length > 0 && (
                <ChartCard title="Confidence vs Latency"
                    subtitle="Does the model take longer on uncertain classifications? Points bottom-right = fast & confident.">
                    <ResponsiveContainer width="100%" height={400}>
                        <ScatterChart margin={{ top: 10, right: 30, bottom: 10, left: 10 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis type="number" dataKey="confidence" name="Confidence"
                                domain={[0, 1]} tickFormatter={v => `${(v * 100).toFixed(0)}%`}
                                tick={{ fontSize: 12 }} />
                            <YAxis type="number" dataKey="latencyMs" name="Latency (ms)"
                                tick={{ fontSize: 12 }} />
                            <ZAxis type="number" dataKey="tokens" range={[30, 300]} name="Tokens" />
                            <Tooltip
                                content={({ payload }) => {
                                    if (!payload?.length) return null;
                                    const d = payload[0].payload as ConfidenceLatencyPoint;
                                    return (
                                        <div className="bg-white border border-gray-200 rounded-lg shadow-lg p-3 text-xs">
                                            <p><b>Confidence:</b> {(d.confidence * 100).toFixed(1)}%</p>
                                            <p><b>Latency:</b> {d.latencyMs}ms</p>
                                            <p><b>Tokens:</b> {d.tokens}</p>
                                            <p><b>Provider:</b> {d.provider}</p>
                                            <p><b>Model:</b> {d.model}</p>
                                        </div>
                                    );
                                }}
                            />
                            <Legend />
                            {[...clByProvider.entries()].map(([provider, points]) => (
                                <Scatter key={provider} name={provider} data={points}
                                    fill={SCATTER_PROVIDER_COLORS[provider] ?? COLORS.muted}
                                    fillOpacity={0.6} />
                            ))}
                        </ScatterChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}

            {/* 2. Training Samples vs F1 Score */}
            {data.samplesAccuracy.points.length > 0 && (
                <ChartCard title={`Training Samples vs F1 Score (${data.samplesAccuracy.version})`}
                    subtitle="Does more data improve per-category accuracy? Points top-right = well-trained categories. Point size = support count.">
                    <ResponsiveContainer width="100%" height={400}>
                        <ScatterChart margin={{ top: 10, right: 30, bottom: 10, left: 10 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis type="number" dataKey="samples" name="Training Samples"
                                tick={{ fontSize: 12 }} />
                            <YAxis type="number" dataKey="f1" name="F1 Score"
                                domain={[0, 1]} tickFormatter={v => `${(v * 100).toFixed(0)}%`}
                                tick={{ fontSize: 12 }} />
                            <ZAxis type="number" dataKey="support" range={[40, 400]} name="Support" />
                            <Tooltip
                                content={({ payload }) => {
                                    if (!payload?.length) return null;
                                    const d = payload[0].payload as SamplesAccuracyPoint;
                                    return (
                                        <div className="bg-white border border-gray-200 rounded-lg shadow-lg p-3 text-xs">
                                            <p className="font-semibold mb-1">{d.category}</p>
                                            <p><b>Samples:</b> {d.samples}</p>
                                            <p><b>F1:</b> {(d.f1 * 100).toFixed(1)}%</p>
                                            <p><b>Precision:</b> {(d.precision * 100).toFixed(1)}%</p>
                                            <p><b>Recall:</b> {(d.recall * 100).toFixed(1)}%</p>
                                            <p><b>Support:</b> {d.support}</p>
                                        </div>
                                    );
                                }}
                            />
                            <Scatter name="Categories" data={data.samplesAccuracy.points}
                                fill={COLORS.success} fillOpacity={0.7} />
                        </ScatterChart>
                    </ResponsiveContainer>
                    {/* Annotate outliers */}
                    {data.samplesAccuracy.points.some(p => p.f1 < 0.5 && p.samples >= 10) && (
                        <div className="mt-3 p-3 bg-amber-50 rounded-lg">
                            <div className="flex items-center gap-2 text-amber-700 text-sm font-medium mb-1">
                                <AlertTriangle className="size-4" /> Categories with enough data but poor F1
                            </div>
                            <ul className="text-sm text-amber-600 space-y-1">
                                {data.samplesAccuracy.points
                                    .filter(p => p.f1 < 0.5 && p.samples >= 10)
                                    .map(p => (
                                        <li key={p.category}>
                                            <b>{p.category}</b> — {p.samples} samples, F1: {(p.f1 * 100).toFixed(1)}%
                                            (may need better quality data or is confused with another category)
                                        </li>
                                    ))}
                            </ul>
                        </div>
                    )}
                </ChartCard>
            )}

            {/* 3. Token Count vs Cost */}
            {data.tokensCost.length > 0 && (
                <ChartCard title="Token Count vs Cost"
                    subtitle="Spot outlier AI calls consuming disproportionate resources. Points far from the cluster = investigate.">
                    <ResponsiveContainer width="100%" height={400}>
                        <ScatterChart margin={{ top: 10, right: 30, bottom: 10, left: 10 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis type="number" dataKey="totalTokens" name="Total Tokens"
                                tickFormatter={v => formatNumber(v)} tick={{ fontSize: 12 }} />
                            <YAxis type="number" dataKey="cost" name="Cost (USD)"
                                tickFormatter={v => `$${v}`} tick={{ fontSize: 12 }} />
                            <ZAxis type="number" dataKey="latencyMs" range={[30, 250]} name="Latency" />
                            <Tooltip
                                content={({ payload }) => {
                                    if (!payload?.length) return null;
                                    const d = payload[0].payload as TokensCostPoint;
                                    return (
                                        <div className="bg-white border border-gray-200 rounded-lg shadow-lg p-3 text-xs">
                                            <p><b>Tokens:</b> {d.totalTokens.toLocaleString()} ({d.inputTokens.toLocaleString()} in / {d.outputTokens.toLocaleString()} out)</p>
                                            <p><b>Cost:</b> ${d.cost.toFixed(5)}</p>
                                            <p><b>Latency:</b> {d.latencyMs}ms</p>
                                            <p><b>Provider:</b> {d.provider}</p>
                                            <p><b>Model:</b> {d.model}</p>
                                            <p><b>Type:</b> {d.usageType}</p>
                                        </div>
                                    );
                                }}
                            />
                            <Legend />
                            {[...tcByProvider.entries()].map(([provider, points]) => (
                                <Scatter key={provider} name={provider} data={points}
                                    fill={SCATTER_PROVIDER_COLORS[provider] ?? COLORS.muted}
                                    fillOpacity={0.6} />
                            ))}
                        </ScatterChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}

            {/* 4. Document Size vs Confidence */}
            {data.sizeConfidence.length > 0 && (
                <ChartCard title="Document Size vs Confidence"
                    subtitle="Are longer or shorter documents harder to classify? Coloured by classifier (BERT vs LLM).">
                    <ResponsiveContainer width="100%" height={400}>
                        <ScatterChart margin={{ top: 10, right: 30, bottom: 10, left: 10 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                            <XAxis type="number" dataKey="textLength" name="Text Length (chars)"
                                tickFormatter={v => formatNumber(v)} tick={{ fontSize: 12 }} />
                            <YAxis type="number" dataKey="confidence" name="Confidence"
                                domain={[0, 1]} tickFormatter={v => `${(v * 100).toFixed(0)}%`}
                                tick={{ fontSize: 12 }} />
                            <Tooltip
                                content={({ payload }) => {
                                    if (!payload?.length) return null;
                                    const d = payload[0].payload as SizeConfidencePoint;
                                    return (
                                        <div className="bg-white border border-gray-200 rounded-lg shadow-lg p-3 text-xs">
                                            <p><b>Text:</b> {d.textLength.toLocaleString()} chars</p>
                                            <p><b>File:</b> {formatNumber(d.fileSizeBytes)} bytes</p>
                                            <p><b>Confidence:</b> {(d.confidence * 100).toFixed(1)}%</p>
                                            <p><b>Classifier:</b> {d.classifier}</p>
                                            <p><b>Category:</b> {d.category}</p>
                                            {d.sensitivity && <p><b>Sensitivity:</b> {d.sensitivity}</p>}
                                        </div>
                                    );
                                }}
                            />
                            <Legend />
                            {[...scByClassifier.entries()].map(([classifier, points]) => (
                                <Scatter key={classifier} name={classifier} data={points}
                                    fill={CLASSIFIER_COLORS[classifier] ?? COLORS.muted}
                                    fillOpacity={0.6} />
                            ))}
                        </ScatterChart>
                    </ResponsiveContainer>
                </ChartCard>
            )}
        </div>
    );
}

/* ══════════════════════════════════════════════════════════
   Shared components
   ══════════════════════════════════════════════════════════ */

function ChartCard({ title, subtitle, children }: {
    title: string; subtitle?: string; children: React.ReactNode;
}) {
    return (
        <div className="bg-white border border-gray-200 rounded-xl p-6">
            <h3 className="text-base font-semibold text-gray-900">{title}</h3>
            {subtitle && <p className="text-sm text-gray-500 mt-1 mb-4">{subtitle}</p>}
            {!subtitle && <div className="mt-4" />}
            {children}
        </div>
    );
}

function StatCard({ label, value, icon: Icon, color }: {
    label: string; value: string | number; icon: React.ElementType; color?: string;
}) {
    return (
        <div className="bg-white border border-gray-200 rounded-xl p-4">
            <div className="flex items-center justify-between mb-2">
                <span className="text-sm text-gray-500">{label}</span>
                <Icon className={`size-4 ${color ?? "text-gray-400"}`} />
            </div>
            <p className={`text-2xl font-bold ${color ?? "text-gray-900"}`}>{value}</p>
        </div>
    );
}

function MetricBadge({ value }: { value: number }) {
    const pct = (value * 100).toFixed(1);
    const color = value >= 0.8 ? "text-green-700 bg-green-50"
        : value >= 0.6 ? "text-blue-700 bg-blue-50"
        : value >= 0.4 ? "text-amber-700 bg-amber-50"
        : "text-red-700 bg-red-50";
    return (
        <span className={`text-xs font-medium px-2 py-0.5 rounded ${color}`}>
            {pct}%
        </span>
    );
}

function EmptyState({ message }: { message: string }) {
    return (
        <div className="flex flex-col items-center justify-center py-20 text-gray-400">
            <BarChart3 className="size-12 mb-3" />
            <p className="text-sm">{message}</p>
            <p className="text-xs mt-1">Data will appear here once documents are classified</p>
        </div>
    );
}

function formatNumber(n: number): string {
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
    if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
    return String(n);
}
