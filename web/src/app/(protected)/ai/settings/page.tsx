"use client";

import { useCallback, useEffect, useState } from "react";
import {
    Brain, Gauge, Save, Loader2, RefreshCw, Eye, EyeOff, Settings2,
    AlertTriangle, XCircle, CheckCircle, Info, Stethoscope,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

type ConfigEntry = { id: string; key: string; category: string; value: unknown; description: string; updatedAt: string };
type OllamaModel = { name: string; model: string; size: number; details: { parameter_size: string; quantization_level: string } };
type DiagnosticItem = { severity: "error" | "warning" | "info"; category: string; title: string; detail: string; action: string };
type DiagnosticsData = { status: string; items: DiagnosticItem[]; counts: { errors: number; warnings: number; info: number }; checkedAt: string };

export default function AiSettingsPage() {
    const [configs, setConfigs] = useState<Map<string, ConfigEntry>>(new Map());
    const [edited, setEdited] = useState<Map<string, unknown>>(new Map());
    const [saving, setSaving] = useState(false);

    // Ollama state
    const [ollamaModels, setOllamaModels] = useState<OllamaModel[]>([]);
    const [ollamaReachable, setOllamaReachable] = useState<boolean | null>(null);
    const [ollamaLoading, setOllamaLoading] = useState(false);
    const [pullName, setPullName] = useState("");
    const [pulling, setPulling] = useState(false);
    const [pullProgress, setPullProgress] = useState("");

    // Diagnostics state
    const [diagnostics, setDiagnostics] = useState<DiagnosticsData | null>(null);
    const [diagLoading, setDiagLoading] = useState(false);

    const loadConfigs = useCallback(async () => {
        try {
            const [llm, pipeline] = await Promise.all([
                api.get("/admin/config", { params: { category: "llm" } }),
                api.get("/admin/config", { params: { category: "pipeline" } }),
            ]);
            const map = new Map<string, ConfigEntry>();
            for (const entry of [...llm.data, ...pipeline.data]) map.set(entry.key, entry);
            setConfigs(map);
            setEdited(new Map());
        } catch { toast.error("Failed to load settings"); }
    }, []);

    const loadDiagnostics = useCallback(async () => {
        setDiagLoading(true);
        try {
            const { data } = await api.get("/admin/ai/diagnostics");
            setDiagnostics(data);
        } catch { /* ignore — diagnostics is best-effort */ }
        finally { setDiagLoading(false); }
    }, []);

    useEffect(() => { loadConfigs(); loadDiagnostics(); }, [loadConfigs, loadDiagnostics]);

    const getValue = (key: string): unknown => edited.has(key) ? edited.get(key) : configs.get(key)?.value;
    const setValue = (key: string, value: unknown) => setEdited(prev => new Map(prev).set(key, value));

    const handleSave = async () => {
        if (edited.size === 0) return;
        setSaving(true);
        try {
            for (const [key, value] of edited) {
                const category = key.startsWith("llm.") ? "llm" : "pipeline";
                await api.put("/admin/config", { key, value, category, description: "" });
            }
            toast.success(`Saved ${edited.size} setting(s)`);
            loadConfigs();
        } catch { toast.error("Save failed"); }
        finally { setSaving(false); }
    };

    const provider = String(getValue("llm.provider") ?? "anthropic");
    const isOllama = provider === "ollama";

    // Ollama model loading
    const loadOllamaModels = useCallback(async () => {
        setOllamaLoading(true);
        try {
            const { data } = await api.get("/admin/ollama/models");
            setOllamaModels(data.models ?? []);
            setOllamaReachable(!data.error);
        } catch { setOllamaReachable(false); }
        finally { setOllamaLoading(false); }
    }, []);

    // Always check Ollama status on mount so the indicator is visible regardless of provider
    useEffect(() => { loadOllamaModels(); }, [loadOllamaModels]);

    const handlePull = async () => {
        const name = pullName.trim();
        if (!name) return;
        setPulling(true); setPullProgress("Connecting...");
        try {
            const es = new EventSource(`/api/admin/ollama/pull?model=${encodeURIComponent(name)}`, { withCredentials: true });
            es.addEventListener("progress", (e) => {
                try { const d = JSON.parse(e.data); setPullProgress(d.total && d.completed ? `${d.status ?? "Downloading"} ${Math.round((d.completed / d.total) * 100)}%` : d.status ?? "Pulling..."); } catch { setPullProgress("Pulling..."); }
            });
            es.addEventListener("done", () => { es.close(); setPulling(false); setPullProgress(""); setPullName(""); toast.success(`Model ${name} pulled`); loadOllamaModels(); });
            es.addEventListener("error", () => { es.close(); setPulling(false); setPullProgress(""); toast.error("Pull failed"); });
            es.onerror = () => { es.close(); setPulling(false); setPullProgress(""); };
        } catch { setPulling(false); setPullProgress(""); toast.error("Failed"); }
    };

    const handleDeleteModel = async (name: string) => {
        if (!confirm(`Delete model ${name}?`)) return;
        try { await api.delete("/admin/ollama/models", { data: { model: name } }); toast.success(`Deleted ${name}`); loadOllamaModels(); }
        catch { toast.error("Delete failed"); }
    };

    const fmtSize = (b: number) => b > 1e9 ? `${(b / 1e9).toFixed(1)} GB` : `${(b / 1e6).toFixed(0)} MB`;

    return (
        <>
            {/* Save bar */}
            {edited.size > 0 && (
                <div className="flex items-center justify-between bg-blue-50 border border-blue-200 rounded-lg px-4 py-2 mb-6">
                    <span className="text-sm text-blue-700">{edited.size} unsaved change(s)</span>
                    <div className="flex gap-2">
                        <button onClick={() => setEdited(new Map())} className="px-3 py-1.5 text-xs text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">Discard</button>
                        <button onClick={handleSave} disabled={saving}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-md hover:bg-blue-700">
                            {saving ? <Loader2 className="size-3 animate-spin" /> : <Save className="size-3" />} Save
                        </button>
                    </div>
                </div>
            )}

            {/* Pipeline Diagnostics */}
            {diagnostics && diagnostics.items.length > 0 && (
                <div className={`rounded-lg shadow-sm border mb-6 ${
                    diagnostics.status === "error" ? "border-red-200 bg-red-50/50" :
                    diagnostics.status === "warning" ? "border-amber-200 bg-amber-50/50" :
                    "border-green-200 bg-green-50/50"
                }`}>
                    <div className="flex items-center justify-between px-6 py-3 border-b border-inherit">
                        <div className="flex items-center gap-2">
                            <Stethoscope className={`size-5 ${
                                diagnostics.status === "error" ? "text-red-500" :
                                diagnostics.status === "warning" ? "text-amber-500" :
                                "text-green-500"
                            }`} />
                            <h3 className="text-sm font-semibold text-gray-900">Pipeline Diagnostics</h3>
                            {diagnostics.counts.errors > 0 && (
                                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium bg-red-100 text-red-700">
                                    <XCircle className="size-3" /> {diagnostics.counts.errors} error{diagnostics.counts.errors !== 1 ? "s" : ""}
                                </span>
                            )}
                            {diagnostics.counts.warnings > 0 && (
                                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium bg-amber-100 text-amber-700">
                                    <AlertTriangle className="size-3" /> {diagnostics.counts.warnings} warning{diagnostics.counts.warnings !== 1 ? "s" : ""}
                                </span>
                            )}
                        </div>
                        <button onClick={loadDiagnostics} disabled={diagLoading}
                            className="text-xs text-gray-400 hover:text-gray-600">
                            <RefreshCw className={`size-3.5 ${diagLoading ? "animate-spin" : ""}`} />
                        </button>
                    </div>
                    <div className="divide-y divide-inherit">
                        {diagnostics.items.filter(i => i.severity !== "info").map((item, idx) => (
                            <div key={idx} className="px-6 py-3 flex gap-3">
                                <div className="shrink-0 mt-0.5">
                                    {item.severity === "error" ? <XCircle className="size-4 text-red-500" /> :
                                     item.severity === "warning" ? <AlertTriangle className="size-4 text-amber-500" /> :
                                     <Info className="size-4 text-blue-400" />}
                                </div>
                                <div className="min-w-0">
                                    <p className="text-sm font-medium text-gray-900">{item.title}</p>
                                    <p className="text-xs text-gray-500 mt-0.5">{item.detail}</p>
                                    <p className="text-xs text-blue-600 mt-1">{item.action}</p>
                                </div>
                            </div>
                        ))}
                    </div>
                    {diagnostics.items.some(i => i.severity === "info") && (
                        <div className="px-6 py-2 border-t border-inherit bg-white/50">
                            {diagnostics.items.filter(i => i.severity === "info").map((item, idx) => (
                                <div key={idx} className="flex items-center gap-2 py-1">
                                    <Info className="size-3 text-gray-400 shrink-0" />
                                    <span className="text-[11px] text-gray-500">{item.title}</span>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {diagnostics && diagnostics.items.length === 0 && (
                <div className="flex items-center gap-2 bg-green-50 border border-green-200 rounded-lg px-4 py-2 mb-6">
                    <CheckCircle className="size-4 text-green-500" />
                    <span className="text-sm text-green-700">All pipeline checks passed</span>
                    <button onClick={loadDiagnostics} disabled={diagLoading}
                        className="ml-auto text-xs text-gray-400 hover:text-gray-600">
                        <RefreshCw className={`size-3.5 ${diagLoading ? "animate-spin" : ""}`} />
                    </button>
                </div>
            )}

            {/* LLM Provider */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
                <div className="flex items-center gap-2 px-6 py-4 border-b border-gray-200">
                    <Brain className="size-5 text-indigo-500" />
                    <h3 className="text-base font-semibold text-gray-900">LLM Provider</h3>
                </div>
                <div className="px-6 py-2 bg-blue-50/50 border-b border-blue-100">
                    <p className="text-[10px] text-blue-600">
                        These are <strong>global defaults</strong>. Individual pipeline nodes can override model, temperature, and tokens in the visual editor.
                    </p>
                </div>

                {/* Provider selector */}
                <div className="px-6 py-4 flex items-center justify-between border-b border-gray-200">
                    <div>
                        <label htmlFor="llm-provider" className="text-sm font-medium text-gray-900 block mb-1">Provider</label>
                        <p className="text-xs text-gray-400">Save and restart the LLM worker after changing.</p>
                    </div>
                    <select id="llm-provider" value={provider} onChange={e => setValue("llm.provider", e.target.value)}
                        className="w-64 text-sm border border-gray-300 rounded-md px-3 py-2 ml-6">
                        <option value="anthropic">Anthropic (Claude) — Cloud</option>
                        <option value="ollama">Ollama — Local Model</option>
                    </select>
                </div>

                {/* Ollama status bar — always visible */}
                <div className="px-6 py-3 flex items-center justify-between border-b border-gray-200 bg-gray-50/50">
                    <div className="flex items-center gap-2">
                        <span className={`size-2 rounded-full ${ollamaReachable === true ? "bg-green-500" : ollamaReachable === false ? "bg-red-500" : "bg-gray-300"}`} />
                        <span className="text-xs font-medium text-gray-600">Ollama</span>
                        <span className={`text-xs ${ollamaReachable === true ? "text-green-600" : ollamaReachable === false ? "text-red-500" : "text-gray-400"}`}>
                            {ollamaLoading ? "Checking..." : ollamaReachable === true ? `Running — ${ollamaModels.length} model${ollamaModels.length !== 1 ? "s" : ""} installed` : ollamaReachable === false ? "Not reachable" : "Unknown"}
                        </span>
                    </div>
                    <button onClick={loadOllamaModels} disabled={ollamaLoading}
                        className="text-xs text-gray-400 hover:text-gray-600">
                        <RefreshCw className={`size-3.5 ${ollamaLoading ? "animate-spin" : ""}`} />
                    </button>
                </div>

                {/* Anthropic config */}
                {!isOllama && (
                    <div className="px-6 py-4 space-y-3 bg-blue-50/30">
                        <PasswordRow id="anthropic-key" label="API Key" value={String(getValue("llm.anthropic.api_key") ?? "")}
                            onChange={v => setValue("llm.anthropic.api_key", v)} placeholder="sk-ant-api03-..." hint="From console.anthropic.com" />
                        <div>
                            <label htmlFor="anthropic-model" className="text-xs font-medium text-gray-700 block mb-1">Model</label>
                            <select id="anthropic-model" value={String(getValue("llm.anthropic.model") ?? "claude-sonnet-4-20250514")}
                                onChange={e => setValue("llm.anthropic.model", e.target.value)}
                                className="w-full text-sm border border-gray-300 rounded-md px-3 py-2">
                                <option value="claude-sonnet-4-20250514">Claude Sonnet 4 — recommended</option>
                                <option value="claude-sonnet-4-latest">Claude Sonnet 4 (Latest)</option>
                                <option value="claude-haiku-4-5-20251001">Claude Haiku 4.5 — fast &amp; cheap</option>
                                <option value="claude-opus-4-20250514">Claude Opus 4 — highest quality</option>
                            </select>
                        </div>
                    </div>
                )}

                {/* Ollama config */}
                {isOllama && (
                    <div className="px-6 py-4 space-y-4 bg-purple-50/30">
                        <div>
                            <label htmlFor="ollama-url" className="text-xs font-medium text-gray-700 block mb-1">Ollama URL</label>
                            <div className="flex gap-2">
                                <input id="ollama-url" value={String(getValue("llm.ollama.base_url") ?? "http://localhost:11434")}
                                    onChange={e => { setValue("llm.ollama.base_url", e.target.value); setOllamaReachable(null); }}
                                    className="flex-1 text-sm border border-gray-300 rounded-md px-3 py-2" />
                                <button onClick={async () => {
                                    setOllamaLoading(true); setOllamaReachable(null);
                                    try {
                                        const { data } = await api.get("/admin/ollama/status");
                                        setOllamaReachable(data.reachable);
                                        if (data.reachable) { toast.success("Connected"); loadOllamaModels(); }
                                        else toast.error("Not reachable");
                                    } catch { setOllamaReachable(false); }
                                    finally { setOllamaLoading(false); }
                                }} disabled={ollamaLoading}
                                    className={`px-4 py-2 text-sm font-medium rounded-md border ${ollamaReachable === true ? "bg-green-50 text-green-700 border-green-300" : ollamaReachable === false ? "bg-red-50 text-red-700 border-red-300" : "bg-white text-gray-700 border-gray-300 hover:bg-gray-50"}`}>
                                    {ollamaLoading ? <Loader2 className="size-3.5 animate-spin" /> : ollamaReachable === true ? "Connected" : ollamaReachable === false ? "Failed" : "Test"}
                                </button>
                            </div>
                        </div>

                        <div>
                            <label htmlFor="ollama-model" className="text-xs font-medium text-gray-700 block mb-1">Model</label>
                            {ollamaModels.length > 0 ? (
                                <select id="ollama-model" value={String(getValue("llm.ollama.model") ?? "")}
                                    onChange={e => setValue("llm.ollama.model", e.target.value)}
                                    className="w-full text-sm border border-gray-300 rounded-md px-3 py-2">
                                    <option value="">Select model...</option>
                                    {ollamaModels.map(m => <option key={m.name} value={m.name}>{m.name} ({m.details?.parameter_size}, {fmtSize(m.size)})</option>)}
                                </select>
                            ) : (
                                <input id="ollama-model" value={String(getValue("llm.ollama.model") ?? "")}
                                    onChange={e => setValue("llm.ollama.model", e.target.value)} placeholder="qwen2.5:32b"
                                    className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                            )}
                        </div>

                        <div>
                            <label htmlFor="ollama-ctx" className="text-xs font-medium text-gray-700 block mb-1">Context Window</label>
                            <input id="ollama-ctx" type="number" min={2048} max={131072} step={1024}
                                value={Number(getValue("llm.ollama.ctx_size") ?? 32768)}
                                onChange={e => setValue("llm.ollama.ctx_size", parseInt(e.target.value) || 32768)}
                                className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                        </div>

                        {/* Link to Models tab */}
                        <div className="border-t border-purple-200 pt-3">
                            <p className="text-xs text-gray-500">
                                {ollamaModels.length > 0
                                    ? `${ollamaModels.length} model${ollamaModels.length !== 1 ? "s" : ""} installed.`
                                    : "No models installed."
                                }
                                {" "}
                                <a href="/ai#models" className="text-purple-600 hover:text-purple-800 font-medium underline">
                                    Manage models in the Models tab
                                </a>
                            </p>
                        </div>
                    </div>
                )}
            </div>

            {/* Classification Settings */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
                <div className="flex items-center gap-2 px-6 py-4 border-b border-gray-200">
                    <Brain className="size-5 text-gray-500" />
                    <h3 className="text-base font-semibold text-gray-900">Classification</h3>
                </div>
                <div className="px-6 py-4 space-y-4">
                    <SliderRow id="temperature" label="Temperature" value={Number(getValue("pipeline.llm.temperature") ?? 0.1)}
                        onChange={v => setValue("pipeline.llm.temperature", v)} min={0} max={1} step={0.05}
                        hint="0 = deterministic, 1 = creative. 0-0.1 for classification." />
                    <NumberRow id="max-tokens" label="Max Tokens" value={Number(getValue("pipeline.llm.max_tokens") ?? 4096)}
                        onChange={v => setValue("pipeline.llm.max_tokens", v)} min={256} max={16384} step={256}
                        hint="Maximum response length. 4096 is usually sufficient." />
                    <SliderRow id="review-threshold" label="Human Review Threshold" value={Number(getValue("pipeline.confidence.review_threshold") ?? 0.7)}
                        onChange={v => setValue("pipeline.confidence.review_threshold", v)} min={0} max={1} step={0.05}
                        hint="Documents below this score go to the review queue." />
                    <SliderRow id="auto-approve" label="Auto-Approve Threshold" value={Number(getValue("pipeline.confidence.auto_approve_threshold") ?? 0.95)}
                        onChange={v => setValue("pipeline.confidence.auto_approve_threshold", v)} min={0} max={1} step={0.05}
                        hint="Documents above this score are auto-approved." />
                </div>
            </div>

            {/* Per-Provider Timeouts */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
                <div className="flex items-center gap-2 px-6 py-4 border-b border-gray-200">
                    <Brain className="size-5 text-gray-500" />
                    <h3 className="text-base font-semibold text-gray-900">LLM Timeouts</h3>
                </div>
                <div className="px-6 py-4 space-y-4">
                    <p className="text-xs text-gray-500">
                        Hard timeout per LLM classification call, configurable per provider.
                        If a model wanders past the limit the call is aborted and the auto-retry kicks in (then fails).
                        Pipeline nodes can override these with a per-node value if needed.
                    </p>
                    <NumberRow id="timeout-anthropic" label="Anthropic (Claude) Timeout — seconds"
                        value={Number(getValue("pipeline.llm.timeout_seconds.anthropic") ?? 60)}
                        onChange={v => setValue("pipeline.llm.timeout_seconds.anthropic", v)}
                        min={10} max={600} step={5}
                        hint="Cloud — Claude is fast (5-15s typical). 60s is a generous default." />
                    <NumberRow id="timeout-ollama" label="Ollama (Local) Timeout — seconds"
                        value={Number(getValue("pipeline.llm.timeout_seconds.ollama") ?? 240)}
                        onChange={v => setValue("pipeline.llm.timeout_seconds.ollama", v)}
                        min={30} max={900} step={10}
                        hint="Local — depends on hardware. 240s is generous for qwen2.5:32b on Apple Silicon." />
                </div>
            </div>

            {/* Processing */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
                <div className="flex items-center gap-2 px-6 py-4 border-b border-gray-200">
                    <Gauge className="size-5 text-gray-500" />
                    <h3 className="text-base font-semibold text-gray-900">Processing</h3>
                </div>
                <div className="px-6 py-4 space-y-4">
                    <NumberRow id="max-text" label="Max Text Length (chars)" value={Number(getValue("pipeline.text.max_length") ?? 100000)}
                        onChange={v => setValue("pipeline.text.max_length", v)} min={1000} max={500000} step={1000} />
                    <ToggleRow id="auto-classify" label="Auto-Classify After Extraction" value={Boolean(getValue("pipeline.processing.auto_classify") ?? true)}
                        onChange={v => setValue("pipeline.processing.auto_classify", v)} />
                    <ToggleRow id="dublin-core" label="Extract Dublin Core Metadata" value={Boolean(getValue("pipeline.processing.extract_dublin_core") ?? true)}
                        onChange={v => setValue("pipeline.processing.extract_dublin_core", v)} />
                    <ToggleRow id="auto-enforce" label="Auto-Enforce Governance Rules" value={Boolean(getValue("pipeline.governance.auto_enforce") ?? true)}
                        onChange={v => setValue("pipeline.governance.auto_enforce", v)} />
                </div>
            </div>
        </>
    );
}

// ── Inline components ─────────────────────────────────

function SliderRow({ id, label, value, onChange, min, max, step, hint }: {
    id: string; label: string; value: number; onChange: (v: number) => void;
    min: number; max: number; step: number; hint?: string;
}) {
    return (
        <div className="flex items-center justify-between">
            <div>
                <label htmlFor={id} className="text-sm font-medium text-gray-900">{label}</label>
                {hint && <p className="text-[10px] text-gray-400">{hint}</p>}
            </div>
            <div className="flex items-center gap-3 w-48">
                <input id={id} type="range" min={min} max={max} step={step} value={value}
                    onChange={e => onChange(parseFloat(e.target.value))} className="flex-1 accent-blue-600" />
                <span className="text-sm font-mono text-gray-700 w-10 text-right">{value}</span>
            </div>
        </div>
    );
}

function NumberRow({ id, label, value, onChange, min, max, step, hint }: {
    id: string; label: string; value: number; onChange: (v: number) => void;
    min?: number; max?: number; step?: number; hint?: string;
}) {
    return (
        <div className="flex items-center justify-between">
            <div>
                <label htmlFor={id} className="text-sm font-medium text-gray-900">{label}</label>
                {hint && <p className="text-[10px] text-gray-400">{hint}</p>}
            </div>
            <input id={id} type="number" min={min} max={max} step={step} value={value}
                onChange={e => onChange(parseInt(e.target.value) || 0)}
                className="w-32 text-sm border border-gray-300 rounded-md px-3 py-1.5 text-right" />
        </div>
    );
}

function ToggleRow({ id, label, value, onChange }: {
    id: string; label: string; value: boolean; onChange: (v: boolean) => void;
}) {
    return (
        <div className="flex items-center justify-between">
            <label htmlFor={id} className="text-sm font-medium text-gray-900">{label}</label>
            <button id={id} role="switch" aria-checked={value} onClick={() => onChange(!value)}
                className={`relative w-11 h-6 rounded-full transition-colors ${value ? "bg-blue-600" : "bg-gray-300"}`}>
                <span className={`absolute top-0.5 left-0.5 size-5 rounded-full bg-white shadow transition-transform ${value ? "translate-x-5" : ""}`} />
            </button>
        </div>
    );
}

function PasswordRow({ id, label, value, onChange, placeholder, hint }: {
    id: string; label: string; value: string; onChange: (v: string) => void; placeholder?: string; hint?: string;
}) {
    const [visible, setVisible] = useState(false);
    return (
        <div>
            <label htmlFor={id} className="text-xs font-medium text-gray-700 block mb-1">{label}</label>
            <div className="relative">
                <input id={id} type={visible ? "text" : "password"} value={value} onChange={e => onChange(e.target.value)}
                    placeholder={placeholder} className="w-full text-sm border border-gray-300 rounded-md px-3 py-2 pr-10" />
                <button onClick={() => setVisible(!visible)} className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                    aria-label={visible ? "Hide" : "Show"}>
                    {visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
            </div>
            {hint && <p className="text-[10px] text-gray-400 mt-0.5">{hint}</p>}
        </div>
    );
}
