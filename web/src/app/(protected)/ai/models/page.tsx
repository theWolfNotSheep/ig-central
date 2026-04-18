"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
    Cpu, RefreshCw, Download, Loader2, CheckCircle, XCircle,
    AlertTriangle, Database, BarChart3, FileText, Brain, Trash2,
    Upload, Plus, Check, Search, Settings2, ChevronDown, ChevronRight,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

type OllamaModel = { name: string; model: string; size: number; details: { parameter_size: string; quantization_level: string } };

type BertStatus = {
    serviceUrl: string;
    reachable: boolean;
    status: string;
    modelLoaded: boolean;
    mode: string;
    labelCount: number;
    models?: { id: string; path: string; has_onnx: boolean; has_transformers: boolean; has_label_map: boolean }[];
    activeModelDir?: string;
    error?: string;
};

type BertStats = {
    totalClassified: number;
    bertHits: number;
    llmClassified: number;
    bertHitRate: string;
    avgBertConfidence: number;
};

export default function ModelsPage() {
    const [status, setStatus] = useState<BertStatus | null>(null);
    const [stats, setStats] = useState<BertStats | null>(null);
    const [loading, setLoading] = useState(true);

    // Ollama state
    const [ollamaModels, setOllamaModels] = useState<OllamaModel[]>([]);
    const [ollamaReachable, setOllamaReachable] = useState<boolean | null>(null);
    const [ollamaLoading, setOllamaLoading] = useState(false);
    const [pullName, setPullName] = useState("");
    const [pulling, setPulling] = useState(false);
    const [pullProgress, setPullProgress] = useState("");

    const loadOllamaModels = useCallback(async () => {
        setOllamaLoading(true);
        try {
            const { data } = await api.get("/admin/ollama/models");
            setOllamaModels(data.models ?? []);
            setOllamaReachable(!data.error);
        } catch { setOllamaReachable(false); }
        finally { setOllamaLoading(false); }
    }, []);

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

    const loadStatus = useCallback(async () => {
        try {
            const [s, st] = await Promise.all([
                api.get("/admin/bert/status"),
                api.get("/admin/bert/stats"),
            ]);
            setStatus(s.data);
            setStats(st.data);
        } catch { toast.error("Failed to load BERT status"); }
        finally { setLoading(false); }
    }, []);

    useEffect(() => { loadStatus(); loadOllamaModels(); }, [loadStatus, loadOllamaModels]);

    const modeLabel = (mode: string) => {
        switch (mode) {
            case "onnx": return { text: "ONNX (Production)", color: "bg-green-100 text-green-700" };
            case "transformers": return { text: "HuggingFace (Dev)", color: "bg-blue-100 text-blue-700" };
            case "demo": return { text: "Demo (Random)", color: "bg-amber-100 text-amber-700" };
            default: return { text: mode, color: "bg-gray-100 text-gray-700" };
        }
    };

    return (
        <>
            {/* Service Status */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
                <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
                    <div className="flex items-center gap-2">
                        <Cpu className="size-5 text-violet-500" />
                        <h3 className="text-base font-semibold text-gray-900">BERT Classifier Service</h3>
                    </div>
                    <button onClick={loadStatus} disabled={loading}
                        className="text-xs text-gray-400 hover:text-gray-600">
                        <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
                    </button>
                </div>

                {status && (
                    <div className="px-6 py-4">
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
                            <div className="flex items-center gap-2">
                                {status.reachable
                                    ? <CheckCircle className="size-4 text-green-500" />
                                    : <XCircle className="size-4 text-red-500" />}
                                <div>
                                    <p className="text-[10px] text-gray-400 uppercase">Status</p>
                                    <p className="text-sm font-medium">{status.reachable ? "Online" : "Unreachable"}</p>
                                </div>
                            </div>
                            <div>
                                <p className="text-[10px] text-gray-400 uppercase">Mode</p>
                                {status.mode && (
                                    <span className={`inline-block px-2 py-0.5 text-xs font-medium rounded ${modeLabel(status.mode).color}`}>
                                        {modeLabel(status.mode).text}
                                    </span>
                                )}
                            </div>
                            <div>
                                <p className="text-[10px] text-gray-400 uppercase">Labels</p>
                                <p className="text-sm font-medium">{status.labelCount} categories</p>
                            </div>
                            <div>
                                <p className="text-[10px] text-gray-400 uppercase">Service URL</p>
                                <p className="text-xs font-mono text-gray-500 truncate">{status.serviceUrl}</p>
                            </div>
                        </div>

                        {status.mode === "demo" && (
                            <div className="flex items-start gap-2 p-3 bg-amber-50 border border-amber-200 rounded-lg">
                                <AlertTriangle className="size-4 text-amber-500 shrink-0 mt-0.5" />
                                <div>
                                    <p className="text-sm font-medium text-amber-800">No trained model loaded</p>
                                    <p className="text-xs text-amber-600 mt-0.5">
                                        BERT is running in demo mode with random predictions. Export your training data below,
                                        fine-tune a model, then mount it to <code className="bg-amber-100 px-1 rounded">/app/models/default/</code> in the container.
                                    </p>
                                </div>
                            </div>
                        )}

                        {status.mode === "onnx" && (
                            <div className="flex items-start gap-2 p-3 bg-green-50 border border-green-200 rounded-lg">
                                <CheckCircle className="size-4 text-green-500 shrink-0 mt-0.5" />
                                <div>
                                    <p className="text-sm font-medium text-green-800">Production model loaded</p>
                                    <p className="text-xs text-green-600 mt-0.5">
                                        ONNX model is active with {status.labelCount} labels. BERT will accelerate classification
                                        for documents matching trained categories.
                                    </p>
                                </div>
                            </div>
                        )}

                        {status.models && status.models.length > 0 && (
                            <div className="mt-4">
                                <h4 className="text-[10px] font-semibold text-gray-500 uppercase mb-2">Available Models</h4>
                                <div className="space-y-1.5">
                                    {status.models.map(m => (
                                        <div key={m.id} className="flex items-center justify-between bg-gray-50 rounded-lg border border-gray-200 px-3 py-2">
                                            <div className="flex items-center gap-3">
                                                <Cpu className="size-4 text-violet-500 shrink-0" />
                                                <div>
                                                    <span className="text-sm font-medium text-gray-900">{m.id}</span>
                                                    <div className="flex gap-2 mt-0.5">
                                                        {m.has_onnx && <span className="text-[9px] px-1.5 py-0.5 bg-green-100 text-green-700 rounded">ONNX</span>}
                                                        {m.has_transformers && <span className="text-[9px] px-1.5 py-0.5 bg-blue-100 text-blue-700 rounded">HF</span>}
                                                        {m.has_label_map && <span className="text-[9px] px-1.5 py-0.5 bg-purple-100 text-purple-700 rounded">Labels</span>}
                                                        {!m.has_onnx && !m.has_transformers && <span className="text-[9px] px-1.5 py-0.5 bg-gray-100 text-gray-500 rounded">Empty</span>}
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* Ollama Models */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
                <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
                    <div className="flex items-center gap-2">
                        <Brain className="size-5 text-purple-500" />
                        <h3 className="text-base font-semibold text-gray-900">Ollama Models</h3>
                    </div>
                    <div className="flex items-center gap-3">
                        <div className="flex items-center gap-1.5">
                            <span className={`size-2 rounded-full ${ollamaReachable === true ? "bg-green-500" : ollamaReachable === false ? "bg-red-500" : "bg-gray-300"}`} />
                            <span className={`text-xs ${ollamaReachable === true ? "text-green-600" : ollamaReachable === false ? "text-red-500" : "text-gray-400"}`}>
                                {ollamaLoading ? "Checking..." : ollamaReachable === true ? "Running" : ollamaReachable === false ? "Not reachable" : "Unknown"}
                            </span>
                        </div>
                        <button onClick={loadOllamaModels} disabled={ollamaLoading}
                            className="text-xs text-gray-400 hover:text-gray-600">
                            <RefreshCw className={`size-4 ${ollamaLoading ? "animate-spin" : ""}`} />
                        </button>
                    </div>
                </div>

                <div className="px-6 py-4">
                    {/* Installed models */}
                    {ollamaModels.length > 0 ? (
                        <div className="space-y-2 mb-4">
                            {ollamaModels.map(m => (
                                <div key={m.name} className="flex items-center justify-between bg-gray-50 rounded-lg border border-gray-200 px-4 py-3">
                                    <div className="flex items-center gap-3">
                                        <Brain className="size-5 text-purple-500 shrink-0" />
                                        <div>
                                            <span className="text-sm font-medium text-gray-900">{m.name}</span>
                                            <div className="text-[10px] text-gray-400 mt-0.5">
                                                {m.details?.parameter_size} &middot; {m.details?.quantization_level} &middot; {fmtSize(m.size)}
                                            </div>
                                        </div>
                                    </div>
                                    <button onClick={() => handleDeleteModel(m.name)}
                                        className="inline-flex items-center gap-1 text-xs text-red-400 hover:text-red-600">
                                        <Trash2 className="size-3" /> Delete
                                    </button>
                                </div>
                            ))}
                        </div>
                    ) : ollamaReachable ? (
                        <p className="text-sm text-gray-400 mb-4">No models installed. Pull one below.</p>
                    ) : (
                        <div className="flex items-start gap-2 p-3 bg-red-50 border border-red-200 rounded-lg mb-4">
                            <XCircle className="size-4 text-red-500 shrink-0 mt-0.5" />
                            <div>
                                <p className="text-sm font-medium text-red-800">Ollama not reachable</p>
                                <p className="text-xs text-red-600 mt-0.5">
                                    Check that Ollama is running locally and the URL is configured in AI Settings.
                                </p>
                            </div>
                        </div>
                    )}

                    {/* Pull model */}
                    <div className="border-t border-gray-100 pt-4">
                        <label className="text-[10px] font-semibold text-gray-500 uppercase block mb-2">Pull New Model</label>
                        <div className="flex gap-2">
                            <input value={pullName} onChange={e => setPullName(e.target.value)}
                                onKeyDown={e => { if (e.key === "Enter") handlePull(); }}
                                placeholder="e.g. qwen2.5:72b, llama3:8b, mistral"
                                disabled={pulling}
                                className="flex-1 text-sm border border-gray-300 rounded-md px-3 py-2 disabled:opacity-50" />
                            <button onClick={handlePull} disabled={pulling || !pullName.trim()}
                                className="px-4 py-2 bg-purple-600 text-white text-sm font-medium rounded-md hover:bg-purple-700 disabled:opacity-50">
                                {pulling ? <Loader2 className="size-4 animate-spin" /> : "Pull"}
                            </button>
                        </div>
                        {pullProgress && (
                            <div className="mt-2 text-xs text-purple-600 font-mono bg-purple-50 rounded px-3 py-1.5">
                                {pullProgress}
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* Statistics */}
            {stats && (
                <div className="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
                    <div className="flex items-center gap-2 px-6 py-4 border-b border-gray-200">
                        <BarChart3 className="size-5 text-blue-500" />
                        <h3 className="text-base font-semibold text-gray-900">Pipeline Statistics</h3>
                    </div>
                    <div className="px-6 py-4">
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                            <div className="bg-gray-50 rounded-lg p-3">
                                <p className="text-[10px] text-gray-400 uppercase">Total Classified</p>
                                <p className="text-2xl font-bold text-gray-900">{stats.totalClassified}</p>
                            </div>
                            <div className="bg-violet-50 rounded-lg p-3">
                                <p className="text-[10px] text-violet-400 uppercase">BERT Hits</p>
                                <p className="text-2xl font-bold text-violet-700">{stats.bertHits}</p>
                            </div>
                            <div className="bg-indigo-50 rounded-lg p-3">
                                <p className="text-[10px] text-indigo-400 uppercase">LLM Classified</p>
                                <p className="text-2xl font-bold text-indigo-700">{stats.llmClassified}</p>
                            </div>
                            <div className="bg-green-50 rounded-lg p-3">
                                <p className="text-[10px] text-green-400 uppercase">BERT Hit Rate</p>
                                <p className="text-2xl font-bold text-green-700">{stats.bertHitRate}</p>
                            </div>
                        </div>
                        {stats.totalClassified > 0 && stats.bertHits === 0 && (
                            <p className="text-xs text-gray-500 mt-3">
                                BERT has not accelerated any classifications yet. Train a model to start saving LLM costs.
                            </p>
                        )}
                    </div>
                </div>
            )}

            <TrainingDataSection />
        </>
    );
}

/* ------------------------------------------------------------------ */
/*  Training Data Management Section                                   */
/* ------------------------------------------------------------------ */

type TrainingJob = {
    id: string;
    status: "PENDING" | "TRAINING" | "COMPLETED" | "FAILED" | "PROMOTED";
    modelVersion: string;
    baseModel: string;
    sampleCount: number;
    categoryCount: number;
    metrics?: { accuracy?: number; f1?: number; loss?: number; train_samples?: number; val_samples?: number };
    modelPath?: string;
    promoted: boolean;
    startedBy: string;
    startedAt: string;
    completedAt?: string;
    error?: string;
};

type TrainingSample = {
    id: string; text: string; categoryId: string; categoryName: string;
    sensitivityLabel: string; source: string; sourceDocumentId?: string;
    confidence: number; verified: boolean; fileName?: string; createdAt: string;
};

type TrainingConfig = {
    autoCollectEnabled: boolean; autoCollectMinConfidence: number;
    autoCollectCategories: string[]; autoCollectCorrectedOnly: boolean; maxTextLength: number;
};

type TrainingStats = {
    total: number; verified: number; manual: number; autoCollected: number; bulkImport: number;
    categoryCount: number; categories: Record<string, number>;
};

function TrainingDataSection() {
    const [samples, setSamples] = useState<TrainingSample[]>([]);
    const [sampleStats, setSampleStats] = useState<TrainingStats | null>(null);
    const [config, setConfig] = useState<TrainingConfig | null>(null);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(false);
    const [configSaving, setConfigSaving] = useState(false);
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [expanded, setExpanded] = useState<string | null>(null);
    const [showSettings, setShowSettings] = useState(false);
    const [categories, setCategories] = useState<{ id: string; name: string }[]>([]);

    // Upload state
    const [uploadCategory, setUploadCategory] = useState("");
    const [uploadCategoryName, setUploadCategoryName] = useState("");
    const [uploading, setUploading] = useState(false);
    const [dragOver, setDragOver] = useState(false);
    const fileInput = useRef<HTMLInputElement>(null);

    // Manual text entry
    const [manualText, setManualText] = useState("");
    const [manualCategory, setManualCategory] = useState("");
    const [manualCategoryName, setManualCategoryName] = useState("");

    // Training jobs
    const [jobs, setJobs] = useState<TrainingJob[]>([]);
    const [trainingInProgress, setTrainingInProgress] = useState(false);
    const [promoting, setPromoting] = useState<string | null>(null);

    const fileInputRef = useRef<HTMLInputElement>(null);

    const loadJobs = useCallback(async () => {
        try { const { data } = await api.get("/admin/bert/training-jobs"); setJobs(data); }
        catch {}
    }, []);

    const loadSamples = useCallback(async (p = 0) => {
        setLoading(true);
        try {
            const { data } = await api.get("/admin/bert/training-samples", { params: { page: p, size: 20 } });
            setSamples(data.samples);
            setTotalPages(data.totalPages);
            setTotalElements(data.totalElements);
            setPage(data.page);
        } catch { toast.error("Failed to load training samples"); }
        finally { setLoading(false); }
    }, []);

    const loadStats = useCallback(async () => {
        try {
            const { data } = await api.get("/admin/bert/training-samples/stats");
            setSampleStats(data);
        } catch {}
    }, []);

    const loadConfig = useCallback(async () => {
        try {
            const { data } = await api.get("/admin/bert/training-samples/config");
            setConfig(data);
        } catch {}
    }, []);

    const loadCategories = useCallback(async () => {
        try {
            const { data } = await api.get("/admin/governance/taxonomy");
            setCategories(data);
        } catch {}
    }, []);

    useEffect(() => { loadSamples(); loadStats(); loadConfig(); loadCategories(); loadJobs(); }, [loadSamples, loadStats, loadConfig, loadCategories, loadJobs]);

    // Poll while training in progress
    useEffect(() => {
        if (!jobs.some(j => j.status === "TRAINING")) return;
        const interval = setInterval(loadJobs, 5000);
        return () => clearInterval(interval);
    }, [jobs, loadJobs]);

    const saveConfig = async () => {
        if (!config) return;
        setConfigSaving(true);
        try {
            const { data } = await api.put("/admin/bert/training-samples/config", config);
            setConfig(data);
            toast.success("Training settings saved");
        } catch { toast.error("Failed to save settings"); }
        finally { setConfigSaving(false); }
    };

    const handleUpload = async (files: File[]) => {
        if (!files.length || !uploadCategoryName) { toast.error("Select a category first"); return; }
        setUploading(true);
        let uploaded = 0;
        for (const file of files) {
            try {
                const form = new FormData();
                form.append("file", file);
                form.append("categoryId", uploadCategory);
                form.append("categoryName", uploadCategoryName);
                await api.post("/admin/bert/training-samples/upload", form, { headers: { "Content-Type": "multipart/form-data" } });
                uploaded++;
            } catch { toast.error(`Failed: ${file.name}`); }
        }
        if (uploaded > 0) { toast.success(`${uploaded} file(s) uploaded as training data`); loadSamples(); loadStats(); }
        setUploading(false);
        setDragOver(false);
    };

    const handleDrop = (e: React.DragEvent) => {
        e.preventDefault();
        setDragOver(false);
        handleUpload(Array.from(e.dataTransfer.files));
    };

    const handleManualAdd = async () => {
        if (!manualText.trim() || !manualCategoryName) return;
        try {
            await api.post("/admin/bert/training-samples", {
                text: manualText.trim(), categoryId: manualCategory, categoryName: manualCategoryName,
            });
            toast.success("Sample added");
            setManualText(""); setManualCategory(""); setManualCategoryName("");
            loadSamples(); loadStats();
        } catch { toast.error("Failed to add sample"); }
    };

    const handleVerify = async (id: string) => {
        try {
            await api.post(`/admin/bert/training-samples/verify/${id}`);
            loadSamples();
        } catch { toast.error("Failed to verify"); }
    };

    const handleDelete = async (id: string) => {
        try {
            await api.delete(`/admin/bert/training-samples/${id}`);
            toast.success("Deleted");
            loadSamples(); loadStats();
        } catch { toast.error("Failed to delete"); }
    };

    const handleBulkDelete = async () => {
        if (selected.size === 0) return;
        if (!confirm(`Delete ${selected.size} sample(s)?`)) return;
        try {
            await api.delete("/admin/bert/training-samples/bulk", { data: { ids: Array.from(selected) } });
            toast.success(`Deleted ${selected.size} sample(s)`);
            setSelected(new Set());
            loadSamples(); loadStats();
        } catch { toast.error("Failed to bulk delete"); }
    };

    const handleExport = async () => {
        try {
            const { data } = await api.get("/admin/bert/training-samples/export");
            const jsonl = data.samples.map((s: Record<string, unknown>) => JSON.stringify({ text: s.text, label: s.label })).join("\n");
            const blob = new Blob([jsonl], { type: "application/jsonl" });
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url; a.download = `bert-training-data-${data.totalSamples}-samples.jsonl`; a.click();
            URL.revokeObjectURL(url);

            const lmBlob = new Blob([JSON.stringify(data.labelMap, null, 2)], { type: "application/json" });
            const lmUrl = URL.createObjectURL(lmBlob);
            const lmA = document.createElement("a");
            lmA.href = lmUrl; lmA.download = "label_map.json"; lmA.click();
            URL.revokeObjectURL(lmUrl);

            toast.success(`Exported ${data.totalSamples} samples + label map`);
        } catch { toast.error("Export failed"); }
    };

    const handleTrainSelected = async () => {
        setTrainingInProgress(true);
        try {
            const { data } = await api.post("/admin/bert/training-jobs",
                selected.size > 0 ? { selectedSampleIds: [...selected] } : {});
            toast.success(`Training started: ${data.modelVersion} (${data.sampleCount} samples, ${data.categoryCount} categories)`);
            loadJobs();
        } catch (e: any) {
            toast.error(e.response?.data?.error ?? "Failed to start training");
        }
        finally { setTrainingInProgress(false); }
    };

    const handlePromote = async (id: string) => {
        setPromoting(id);
        try {
            const { data } = await api.post(`/admin/bert/training-jobs/${id}/promote`);
            toast.success(`Model ${data.modelVersion} promoted — BERT classifier now using trained model`);
            loadJobs();
        } catch (e: any) {
            toast.error(e.response?.data?.error ?? "Failed to promote model");
        }
        finally { setPromoting(null); }
    };

    const statusStyle = (s: string) => {
        switch (s) {
            case "TRAINING": return "bg-blue-100 text-blue-700";
            case "COMPLETED": return "bg-green-100 text-green-700";
            case "PROMOTED": return "bg-violet-100 text-violet-700";
            case "FAILED": return "bg-red-100 text-red-700";
            default: return "bg-gray-100 text-gray-700";
        }
    };

    const selectCategory = (catId: string) => {
        const cat = categories.find(c => c.id === catId);
        setUploadCategory(catId);
        setUploadCategoryName(cat?.name ?? "");
    };

    const selectManualCategory = (catId: string) => {
        const cat = categories.find(c => c.id === catId);
        setManualCategory(catId);
        setManualCategoryName(cat?.name ?? "");
    };

    return (
        <>
            {/* Training Data Settings */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
                <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
                    <div className="flex items-center gap-2">
                        <Settings2 className="size-5 text-gray-400" />
                        <h3 className="text-base font-semibold text-gray-900">Auto-Collection Settings</h3>
                    </div>
                    <button onClick={() => setShowSettings(!showSettings)} className="text-xs text-gray-400 hover:text-gray-600">
                        {showSettings ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
                    </button>
                </div>
                {showSettings && config && (
                    <div className="px-6 py-4 space-y-4">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-sm font-medium text-gray-900">Auto-collect from pipeline</p>
                                <p className="text-[10px] text-gray-400">Automatically save training data when documents are classified</p>
                            </div>
                            <button onClick={() => setConfig({ ...config, autoCollectEnabled: !config.autoCollectEnabled })}
                                className={`relative w-11 h-6 rounded-full transition-colors ${config.autoCollectEnabled ? "bg-blue-600" : "bg-gray-300"}`}>
                                <span className={`absolute top-0.5 left-0.5 size-5 rounded-full bg-white shadow transition-transform ${config.autoCollectEnabled ? "translate-x-5" : ""}`} />
                            </button>
                        </div>
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-sm font-medium text-gray-900">Min confidence</p>
                                <p className="text-[10px] text-gray-400">Only collect when classification confidence is above this</p>
                            </div>
                            <div className="flex items-center gap-2 w-40">
                                <input type="range" min={0.5} max={1} step={0.05} value={config.autoCollectMinConfidence}
                                    onChange={e => setConfig({ ...config, autoCollectMinConfidence: parseFloat(e.target.value) })}
                                    className="flex-1" />
                                <span className="text-sm font-mono w-10 text-right">{config.autoCollectMinConfidence}</span>
                            </div>
                        </div>
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-sm font-medium text-gray-900">Corrected only</p>
                                <p className="text-[10px] text-gray-400">Only collect from human-corrected classifications</p>
                            </div>
                            <button onClick={() => setConfig({ ...config, autoCollectCorrectedOnly: !config.autoCollectCorrectedOnly })}
                                className={`relative w-11 h-6 rounded-full transition-colors ${config.autoCollectCorrectedOnly ? "bg-blue-600" : "bg-gray-300"}`}>
                                <span className={`absolute top-0.5 left-0.5 size-5 rounded-full bg-white shadow transition-transform ${config.autoCollectCorrectedOnly ? "translate-x-5" : ""}`} />
                            </button>
                        </div>
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-sm font-medium text-gray-900">Max text length</p>
                                <p className="text-[10px] text-gray-400">Characters per sample (BERT uses ~512 tokens)</p>
                            </div>
                            <input type="number" min={500} max={10000} step={500} value={config.maxTextLength}
                                onChange={e => setConfig({ ...config, maxTextLength: parseInt(e.target.value) || 2000 })}
                                className="w-24 text-sm border border-gray-300 rounded-md px-2 py-1 text-right" />
                        </div>
                        <button onClick={saveConfig} disabled={configSaving}
                            className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-xs font-medium rounded-md hover:bg-blue-700 disabled:opacity-50">
                            {configSaving ? <Loader2 className="size-3 animate-spin" /> : <Check className="size-3" />} Save Settings
                        </button>
                    </div>
                )}
            </div>

            {/* Upload Training Data */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
                <div className="flex items-center gap-2 px-6 py-4 border-b border-gray-200">
                    <Upload className="size-5 text-green-500" />
                    <h3 className="text-base font-semibold text-gray-900">Upload Training Data</h3>
                </div>
                <div className="px-6 py-4 space-y-4">
                    {/* Category selector */}
                    <div>
                        <label className="text-[10px] font-semibold text-gray-500 uppercase block mb-1">Target Category</label>
                        <select value={uploadCategory} onChange={e => selectCategory(e.target.value)}
                            className="w-full text-sm border border-gray-300 rounded-md px-3 py-2">
                            <option value="">Select category...</option>
                            {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                        </select>
                    </div>

                    {/* Drag-drop zone */}
                    <div
                        onDragOver={e => { e.preventDefault(); if (uploadCategoryName) setDragOver(true); }}
                        onDragLeave={() => setDragOver(false)}
                        onDrop={uploadCategoryName ? handleDrop : undefined}
                        className={`relative border-2 border-dashed rounded-lg p-6 text-center transition-colors ${
                            dragOver ? "border-green-400 bg-green-50" : "border-gray-200 hover:border-gray-300"
                        } ${!uploadCategoryName ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}`}
                        onClick={() => uploadCategoryName && fileInputRef.current?.click()}
                    >
                        <input type="file" ref={fileInputRef} multiple style={{ display: "none" }}
                            onChange={e => handleUpload(Array.from(e.target.files ?? []))} />
                        <Upload className={`size-8 mx-auto mb-2 ${dragOver ? "text-green-500" : "text-gray-300"}`} />
                        <p className="text-sm text-gray-600">
                            {uploading ? "Uploading..." : uploadCategoryName ? "Drop files here or click to browse" : "Select a category first"}
                        </p>
                        <p className="text-[10px] text-gray-400 mt-1">Text will be extracted with Tika. Files are NOT stored — only the text is kept.</p>
                    </div>

                    {/* Manual text entry */}
                    <div className="border-t border-gray-100 pt-4">
                        <label className="text-[10px] font-semibold text-gray-500 uppercase block mb-1">Or Add Text Manually</label>
                        <div className="space-y-2">
                            <select value={manualCategory} onChange={e => selectManualCategory(e.target.value)}
                                className="w-full text-sm border border-gray-300 rounded-md px-3 py-1.5">
                                <option value="">Select category...</option>
                                {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                            </select>
                            <textarea value={manualText} onChange={e => setManualText(e.target.value)}
                                placeholder="Paste document text here..."
                                rows={3} className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                            <button onClick={handleManualAdd} disabled={!manualText.trim() || !manualCategoryName}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-green-600 text-white text-xs font-medium rounded-md hover:bg-green-700 disabled:opacity-50">
                                <Plus className="size-3" /> Add Sample
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {/* Training Data Browser */}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
                <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
                    <div className="flex items-center gap-2">
                        <Database className="size-5 text-orange-500" />
                        <h3 className="text-base font-semibold text-gray-900">Training Samples</h3>
                        <span className="text-xs text-gray-400">{totalElements} total</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <button onClick={handleTrainSelected}
                            disabled={trainingInProgress || totalElements < 10 || jobs.some(j => j.status === "TRAINING")}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-violet-600 text-white rounded-md hover:bg-violet-700 disabled:opacity-50 transition-colors">
                            {trainingInProgress ? <Loader2 className="size-3.5 animate-spin" /> : <Cpu className="size-3.5" />}
                            {selected.size > 0 ? `Train BERT (${selected.size} selected)` : "Train BERT (all)"}
                        </button>
                        {selected.size > 0 && (
                            <button onClick={handleBulkDelete}
                                className="inline-flex items-center gap-1 px-2.5 py-1 text-xs text-red-600 border border-red-200 rounded-md hover:bg-red-50">
                                <Trash2 className="size-3" /> Delete {selected.size}
                            </button>
                        )}
                        <button onClick={handleExport}
                            className="inline-flex items-center gap-1 px-2.5 py-1 text-xs border border-gray-300 rounded-md hover:bg-gray-50">
                            <Download className="size-3" /> Export
                        </button>
                        <button onClick={() => loadSamples(page)} disabled={loading}
                            className="text-xs text-gray-400 hover:text-gray-600">
                            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
                        </button>
                    </div>
                </div>

                {/* Stats bar */}
                {sampleStats && sampleStats.total > 0 && (
                    <div className="px-6 py-2 bg-gray-50 border-b border-gray-100 flex gap-4 text-[10px]">
                        <span className="text-gray-500">Manual: <strong>{sampleStats.manual}</strong></span>
                        <span className="text-gray-500">Auto: <strong>{sampleStats.autoCollected}</strong></span>
                        <span className="text-gray-500">Verified: <strong className="text-green-600">{sampleStats.verified}</strong></span>
                        <span className="text-gray-500">Categories: <strong>{sampleStats.categoryCount}</strong></span>
                    </div>
                )}

                {/* Select all / deselect all */}
                {samples.length > 0 && (
                    <div className="px-6 py-2 border-b border-gray-100 flex items-center gap-3">
                        <input type="checkbox"
                            checked={samples.length > 0 && samples.every(s => selected.has(s.id))}
                            onChange={() => {
                                const allOnPage = samples.map(s => s.id);
                                const allSelected = allOnPage.every(id => selected.has(id));
                                setSelected(prev => {
                                    const next = new Set(prev);
                                    if (allSelected) { allOnPage.forEach(id => next.delete(id)); }
                                    else { allOnPage.forEach(id => next.add(id)); }
                                    return next;
                                });
                            }}
                            className="rounded border-gray-300" />
                        <span className="text-xs text-gray-500">
                            {samples.every(s => selected.has(s.id)) ? "Deselect all" : "Select all"} on this page
                        </span>
                        {selected.size > 0 && (
                            <span className="text-xs text-blue-600">{selected.size} selected</span>
                        )}
                    </div>
                )}

                {/* Sample list */}
                {samples.length === 0 ? (
                    <div className="px-6 py-8 text-center">
                        <Database className="size-8 text-gray-200 mx-auto mb-2" />
                        <p className="text-sm text-gray-400">No training samples yet. Upload files or enable auto-collection.</p>
                    </div>
                ) : (
                    <div className="divide-y divide-gray-100">
                        {samples.map(s => (
                            <div key={s.id} className="px-6 py-3">
                                <div className="flex items-center gap-3">
                                    <input type="checkbox" checked={selected.has(s.id)}
                                        onChange={() => setSelected(prev => { const n = new Set(prev); if (n.has(s.id)) n.delete(s.id); else n.add(s.id); return n; })}
                                        className="rounded border-gray-300" />
                                    <button onClick={() => setExpanded(expanded === s.id ? null : s.id)} className="flex-1 text-left">
                                        <div className="flex items-center gap-2">
                                            <span className="text-xs font-medium text-gray-900 truncate max-w-xs">
                                                {s.text.substring(0, 80)}{s.text.length > 80 ? "..." : ""}
                                            </span>
                                            <span className="px-1.5 py-0.5 text-[9px] bg-blue-50 text-blue-600 rounded">{s.categoryName}</span>
                                            <span className={`px-1.5 py-0.5 text-[9px] rounded ${
                                                s.source === "MANUAL_UPLOAD" ? "bg-green-50 text-green-600" :
                                                s.source === "AUTO_COLLECTED" ? "bg-purple-50 text-purple-600" :
                                                "bg-gray-100 text-gray-500"
                                            }`}>{s.source === "MANUAL_UPLOAD" ? "Manual" : s.source === "AUTO_COLLECTED" ? "Auto" : "Bulk"}</span>
                                            {s.verified && <Check className="size-3 text-green-500" />}
                                        </div>
                                    </button>
                                    <div className="flex items-center gap-1 shrink-0">
                                        {!s.verified && (
                                            <button onClick={() => handleVerify(s.id)} title="Verify"
                                                className="p-1 text-gray-400 hover:text-green-600 rounded"><Check className="size-3.5" /></button>
                                        )}
                                        <button onClick={() => handleDelete(s.id)} title="Delete"
                                            className="p-1 text-gray-400 hover:text-red-600 rounded"><Trash2 className="size-3.5" /></button>
                                    </div>
                                </div>
                                {expanded === s.id && (
                                    <div className="mt-2 ml-8 p-3 bg-gray-50 rounded-lg text-xs">
                                        <pre className="whitespace-pre-wrap text-gray-600 max-h-40 overflow-y-auto">{s.text}</pre>
                                        <div className="mt-2 flex gap-3 text-[10px] text-gray-400">
                                            {s.fileName && <span>File: {s.fileName}</span>}
                                            {s.confidence > 0 && <span>Confidence: {(s.confidence * 100).toFixed(0)}%</span>}
                                            <span>{new Date(s.createdAt).toLocaleDateString()}</span>
                                        </div>
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                )}

                {/* Pagination */}
                {totalPages > 1 && (
                    <div className="px-6 py-3 border-t border-gray-100 flex items-center justify-between">
                        <button onClick={() => loadSamples(page - 1)} disabled={page === 0}
                            className="text-xs text-gray-500 hover:text-gray-700 disabled:opacity-30">Previous</button>
                        <span className="text-xs text-gray-400">Page {page + 1} of {totalPages}</span>
                        <button onClick={() => loadSamples(page + 1)} disabled={page >= totalPages - 1}
                            className="text-xs text-gray-500 hover:text-gray-700 disabled:opacity-30">Next</button>
                    </div>
                )}

                {/* Category distribution */}
                {sampleStats && sampleStats.categoryCount > 0 && (
                    <div className="px-6 py-4 border-t border-gray-100">
                        <h4 className="text-[10px] font-semibold text-gray-500 uppercase mb-2">Category Distribution</h4>
                        <div className="space-y-1.5">
                            {Object.entries(sampleStats.categories)
                                .sort(([, a], [, b]) => b - a)
                                .map(([cat, count]) => (
                                    <div key={cat} className="flex items-center justify-between">
                                        <span className="text-xs text-gray-700">{cat}</span>
                                        <div className="flex items-center gap-2">
                                            <div className="w-32 bg-gray-200 rounded-full h-1.5">
                                                <div className="bg-violet-500 h-1.5 rounded-full"
                                                    style={{ width: `${Math.min(100, (count / sampleStats.total) * 100)}%` }} />
                                            </div>
                                            <span className="text-[10px] text-gray-400 w-8 text-right">{count}</span>
                                        </div>
                                    </div>
                                ))}
                        </div>
                    </div>
                )}

                {/* Training Jobs */}
                {jobs.length > 0 && (
                    <div className="px-6 py-4 border-t border-gray-200">
                        <h4 className="text-[10px] font-semibold text-gray-500 uppercase mb-3">Training History</h4>
                        <div className="space-y-2">
                            {jobs.map(job => (
                                <div key={job.id} className={`rounded-lg border px-4 py-3 ${
                                    job.promoted ? "border-violet-200 bg-violet-50/30" : "border-gray-200"
                                }`}>
                                    <div className="flex items-center justify-between">
                                        <div className="flex items-center gap-2">
                                            <span className="text-sm font-semibold text-gray-900">{job.modelVersion}</span>
                                            <span className={`px-2 py-0.5 text-[10px] font-medium rounded-full ${statusStyle(job.status)}`}>
                                                {job.status}
                                                {job.status === "TRAINING" && <Loader2 className="inline size-3 ml-1 animate-spin" />}
                                            </span>
                                            <span className="text-[10px] text-gray-400">{job.sampleCount} samples, {job.categoryCount} categories</span>
                                        </div>
                                        <div className="flex items-center gap-2">
                                            {job.status === "COMPLETED" && !job.promoted && (
                                                <button onClick={() => handlePromote(job.id)} disabled={promoting === job.id}
                                                    className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium text-violet-700 border border-violet-200 rounded-md hover:bg-violet-50">
                                                    {promoting === job.id ? <Loader2 className="size-3 animate-spin" /> : <CheckCircle className="size-3" />}
                                                    Promote
                                                </button>
                                            )}
                                            {job.promoted && (
                                                <span className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium text-violet-700 bg-violet-100 rounded-md">
                                                    <CheckCircle className="size-3" /> Active
                                                </span>
                                            )}
                                            <span className="text-[10px] text-gray-400">
                                                {new Date(job.startedAt).toLocaleDateString()}
                                            </span>
                                        </div>
                                    </div>
                                    {job.metrics && (
                                        <div className="mt-1.5 flex gap-4 text-xs">
                                            {job.metrics.accuracy != null && <span className="text-gray-600">Accuracy: <strong className="text-green-700">{(job.metrics.accuracy * 100).toFixed(1)}%</strong></span>}
                                            {job.metrics.f1 != null && <span className="text-gray-600">F1: <strong className="text-blue-700">{(job.metrics.f1 * 100).toFixed(1)}%</strong></span>}
                                            {job.metrics.loss != null && <span className="text-gray-600">Loss: <strong>{job.metrics.loss.toFixed(4)}</strong></span>}
                                        </div>
                                    )}
                                    {job.error && <div className="mt-1.5 text-xs text-red-600 bg-red-50 rounded px-2 py-1">{job.error}</div>}
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </>
    );
}
