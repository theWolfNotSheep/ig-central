"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
    X,
    Download,
    Shield,
    ShieldAlert,
    Brain,
    Tag,
    AlertTriangle,
    CheckCircle,
    FileText,
    FileImage,
    FileSpreadsheet,
    File,
    Layers,
    Lock,
    BookOpen,
    ChevronDown,
    ChevronRight,
    RefreshCw,
    Loader2,
    Check,
    Circle,
    ExternalLink,
    Clock,
    Pencil,
    FolderOpen,
    HelpCircle,
    FlaskConical,
    Sparkles,
    Minus,
} from "lucide-react";
import { ProcessingTimer } from "./processing-timer";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import { usePiiTypes } from "@/hooks/use-pii-types";
import ScopeActionModal, { type ActionScope } from "@/components/scope-action-modal";
import SchemaTestModal from "@/components/schema-tester-modal";

type Doc = {
    id: string;
    originalFileName: string;
    fileName: string;
    mimeType: string;
    fileSizeBytes: number;
    status: string;
    categoryName?: string;
    categoryId?: string;
    sensitivityLabel?: string;
    tags?: string[];
    summary?: string;
    pageCount?: number;
    extractedMetadata?: Record<string, string>;
    dublinCore?: Record<string, string>;
    piiFindings?: { type: string; matchedText: string; redactedText: string; confidence: number; method: string; verified: boolean; dismissed?: boolean; dismissedBy?: string; dismissalReason?: string }[];
    piiScannedAt?: string;
    storageProvider?: string;
    storageBucket?: string;
    storageKey?: string;
    storageTierId?: string;
    externalStorageRef?: Record<string, string>;
    retentionScheduleId?: string;
    retentionExpiresAt?: string;
    legalHold?: boolean;
    legalHoldReason?: string;
    appliedPolicyIds?: string[];
    traits?: string[];
    piiStatus?: string;
    classificationResultId?: string;
    lastError?: string;
    lastErrorStage?: string;
    failedAt?: string;
    retryCount?: number;
    createdAt: string;
    classifiedAt?: string;
    governanceAppliedAt?: string;
};

type ClassificationResult = {
    id: string;
    categoryName: string;
    sensitivityLabel: string;
    confidence: number;
    reasoning: string;
    tags: string[];
    applicablePolicyIds: string[];
    retentionScheduleId?: string;
    modelId?: string;
    classifiedAt: string;
    humanReviewed: boolean;
    reviewedBy?: string;
};

const SENSITIVITY_COLORS: Record<string, string> = {
    PUBLIC: "bg-green-100 text-green-700 border-green-200",
    INTERNAL: "bg-blue-100 text-blue-700 border-blue-200",
    CONFIDENTIAL: "bg-amber-100 text-amber-700 border-amber-200",
    RESTRICTED: "bg-red-100 text-red-700 border-red-200",
};

const DC_LABELS: Record<string, string> = {
    title: "Title",
    creator: "Creator",
    lastAuthor: "Last Author",
    subject: "Subject",
    description: "Description",
    publisher: "Publisher",
    contributor: "Contributor",
    date: "Date Created",
    modified: "Date Modified",
    printDate: "Last Printed",
    type: "Type",
    format: "Format",
    identifier: "Identifier",
    source: "Source",
    language: "Language",
    relation: "Relation",
    coverage: "Coverage",
    rights: "Rights",
    keywords: "Keywords",
    pageCount: "Pages",
    wordCount: "Words",
    characterCount: "Characters",
    lineCount: "Lines",
    paragraphCount: "Paragraphs",
    application: "Application",
    revision: "Revision",
    template: "Template",
    company: "Company",
};

function mimeIcon(mime: string) {
    if (mime?.startsWith("image/")) return FileImage;
    if (mime?.includes("spreadsheet") || mime?.includes("csv")) return FileSpreadsheet;
    if (mime?.includes("pdf") || mime?.includes("word") || mime?.includes("text")) return FileText;
    return File;
}

function formatSize(bytes: number) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

const PIPELINE_STEPS = [
    { key: "UPLOADED", label: "Uploaded" },
    { key: "PROCESSING", label: "Extracting text" },
    { key: "PROCESSED", label: "Text extracted" },
    { key: "CLASSIFYING", label: "Classifying" },
    { key: "CLASSIFIED", label: "Classified" },
    { key: "GOVERNANCE_APPLIED", label: "Governance applied" },
    { key: "INBOX", label: "Inbox" },
    { key: "FILED", label: "Filed" },
];

function pipelineIndex(status: string): number {
    const idx = PIPELINE_STEPS.findIndex((s) => s.key === status);
    return idx >= 0 ? idx : -1;
}

export default function ClassificationPanel({ doc, onClose, onDownload, onReprocess, onDocUpdated }: {
    doc: Doc;
    onClose: () => void;
    onDownload: (doc: Doc) => void;
    onReprocess: (doc: Doc) => void;
    onDocUpdated?: (doc: Doc) => void;
}) {
    const [classification, setClassification] = useState<ClassificationResult | null>(null);
    const [reprocessing, setReprocessing] = useState(false);
    const [policyNames, setPolicyNames] = useState<Map<string, string>>(new Map());
    const [retentionName, setRetentionName] = useState<string | null>(null);
    const [viewPolicyId, setViewPolicyId] = useState<string | null>(null);
    const [viewRetention, setViewRetention] = useState(false);
    const [showReclassify, setShowReclassify] = useState(false);
    const [showPiiReport, setShowPiiReport] = useState(false);
    const [showMetadata, setShowMetadata] = useState(false);
    const [showSchemaTest, setShowSchemaTest] = useState(false);
    const [showSuggest, setShowSuggest] = useState(false);
    const [categoryPath, setCategoryPath] = useState<string | null>(null);

    useEffect(() => {
        setClassification(null);
        if (!doc.classificationResultId && !doc.categoryName) return;

        api.get(`/documents/${doc.id}/classification`)
            .then(({ data }) => { if (data.length > 0) setClassification(data[0]); })
            .catch(() => {});
    }, [doc.id, doc.classificationResultId, doc.categoryName]);

    // Resolve policy IDs to names
    useEffect(() => {
        if (!doc.appliedPolicyIds?.length) { setPolicyNames(new Map()); return; }
        api.get("/admin/governance/policies")
            .then(({ data }) => {
                const map = new Map<string, string>();
                for (const p of data) { map.set(p.id, p.name); }
                setPolicyNames(map);
            })
            .catch(() => {});
    }, [doc.appliedPolicyIds]);

    // Resolve category path (e.g. "Legal > Contracts")
    useEffect(() => {
        setCategoryPath(null);
        if (!doc.categoryId && !doc.categoryName) return;
        api.get("/admin/governance/taxonomy")
            .then(({ data }) => {
                const cats = data as { id: string; name: string; parentId?: string }[];
                // Find by ID or name
                const match = cats.find((c) => c.id === doc.categoryId) ?? cats.find((c) => c.name === doc.categoryName);
                if (!match) { setCategoryPath(doc.categoryName ?? null); return; }
                if (match.parentId) {
                    const parent = cats.find((c) => c.id === match.parentId);
                    setCategoryPath(parent ? `${parent.name} > ${match.name}` : match.name);
                } else {
                    setCategoryPath(match.name);
                }
            })
            .catch(() => {});
    }, [doc.categoryId, doc.categoryName]);

    // Resolve retention schedule ID to name
    useEffect(() => {
        setRetentionName(null);
        if (!doc.retentionScheduleId) return;
        api.get("/admin/governance/retention")
            .then(({ data }) => {
                const match = data.find((r: { id: string; name: string }) => r.id === doc.retentionScheduleId);
                if (match) setRetentionName(match.name);
            })
            .catch(() => {});
    }, [doc.retentionScheduleId]);

    // Reset reprocessing flag when status changes from a processing state to a terminal state
    useEffect(() => {
        if (!["UPLOADING", "UPLOADED", "PROCESSING", "PROCESSED", "CLASSIFYING"].includes(doc.status)) {
            setReprocessing(false);
        }
    }, [doc.status]);

    const handleReprocess = () => {
        setReprocessing(true);
        onReprocess(doc);
    };

    const isProcessing = ["UPLOADING", "PROCESSING", "PROCESSED", "CLASSIFYING"].includes(doc.status);
    const [showReasoning, setShowReasoning] = useState(false);
    const Icon = mimeIcon(doc.mimeType);
    const dc = doc.dublinCore ?? {};
    const hasDc = Object.keys(dc).length > 0;

    return (
        <div className="w-[340px] shrink-0 bg-white border-l border-gray-200 overflow-y-auto" style={{ maxHeight: "calc(100vh - 120px)" }}>
            {/* Header */}
            <div className="p-4 border-b border-gray-200 sticky top-0 bg-white z-10">
                <div className="flex items-start justify-between gap-2">
                    <div className="flex items-center gap-2.5">
                        <div className="size-9 rounded-lg bg-gray-100 flex items-center justify-center shrink-0">
                            <Icon className="size-4.5 text-gray-500" />
                        </div>
                        <div className="min-w-0">
                            <h3 className="font-semibold text-gray-900 text-sm leading-tight truncate">
                                {doc.originalFileName || doc.fileName}
                            </h3>
                            <span className="text-xs text-gray-400">{formatSize(doc.fileSizeBytes)}</span>
                        </div>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600 shrink-0 p-0.5">
                        <X className="size-4" />
                    </button>
                </div>
            </div>

            {/* Processing Pipeline Status */}
            {(isProcessing || reprocessing) && (
                <div className="px-4 py-3 border-b border-gray-100">
                    <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-1.5">
                            <Loader2 className="size-3.5 text-blue-500 animate-spin" />
                            <span className="text-xs font-semibold text-blue-600 uppercase tracking-wider">Processing</span>
                            <ProcessingTimer createdAt={doc.createdAt} />
                        </div>
                        <div className="flex items-center gap-1.5">
                        <button onClick={() => setShowReasoning(true)}
                            className="flex items-center gap-1 px-2 py-0.5 text-[10px] text-indigo-500 hover:text-white hover:bg-indigo-500 border border-indigo-200 hover:border-indigo-500 rounded font-medium transition-colors"
                            title="View LLM reasoning">
                            <HelpCircle className="size-2.5" /> Reasoning
                        </button>
                        <button onClick={async () => {
                            if (!confirm("Cancel processing? The document will be reset to UPLOADED.")) return;
                            try {
                                await api.post(`/documents/${doc.id}/cancel`);
                                toast.success("Processing cancelled");
                                if (onDocUpdated) { const { data } = await api.get(`/documents/${doc.id}`); onDocUpdated(data); }
                            } catch { toast.error("Failed to cancel"); }
                        }} className="flex items-center gap-1 px-2 py-0.5 text-[10px] text-red-500 hover:text-white hover:bg-red-500 border border-red-200 hover:border-red-500 rounded font-medium transition-colors">
                            <X className="size-2.5" />
                            Cancel
                        </button>
                    </div>
                    </div>
                    <div className="space-y-0">
                        {PIPELINE_STEPS.map((step, i) => {
                            const currentIdx = pipelineIndex(doc.status);
                            const isDone = i < currentIdx;
                            const isCurrent = i === currentIdx;
                            const isPending = i > currentIdx;

                            return (
                                <div key={step.key} className="flex items-center gap-2.5 py-1">
                                    {/* Step indicator */}
                                    <div className="flex flex-col items-center w-5">
                                        {isDone ? (
                                            <div className="size-4 rounded-full bg-green-500 flex items-center justify-center">
                                                <Check className="size-2.5 text-white" />
                                            </div>
                                        ) : isCurrent ? (
                                            <div className="size-4 rounded-full bg-blue-500 flex items-center justify-center">
                                                <Loader2 className="size-2.5 text-white animate-spin" />
                                            </div>
                                        ) : (
                                            <Circle className="size-4 text-gray-300" />
                                        )}
                                    </div>
                                    {/* Step label */}
                                    <span className={`text-xs ${
                                        isDone ? "text-green-700 font-medium" :
                                        isCurrent ? "text-blue-700 font-medium" :
                                        "text-gray-400"
                                    }`}>
                                        {step.label}
                                    </span>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}

            {/* Failed State */}
            {doc.status?.includes("FAILED") && doc.lastError && (
                <div className="px-4 py-3 border-b border-red-100 bg-red-50">
                    <div className="flex items-center justify-between mb-2">
                        <div className="flex items-center gap-1.5">
                            <AlertTriangle className="size-3.5 text-red-500" />
                            <span className="text-xs font-semibold text-red-600 uppercase tracking-wider">
                                {doc.status?.replace(/_/g, " ")}
                            </span>
                        </div>
                        <button onClick={() => {
                            if (doc.status === "CLASSIFICATION_FAILED") {
                                api.post(`/documents/${doc.id}/rerun/classify`).then(() => {
                                    toast.success("Re-queued for classification");
                                    if (onDocUpdated) api.get(`/documents/${doc.id}`).then(({ data }) => onDocUpdated(data));
                                }).catch(() => toast.error("Failed to rerun"));
                            } else if (doc.status === "PROCESSING_FAILED") {
                                api.post(`/documents/${doc.id}/rerun/extract`).then(() => {
                                    toast.success("Re-queued for extraction");
                                    if (onDocUpdated) api.get(`/documents/${doc.id}`).then(({ data }) => onDocUpdated(data));
                                }).catch(() => toast.error("Failed to rerun"));
                            } else if (doc.status === "ENFORCEMENT_FAILED") {
                                api.post(`/documents/${doc.id}/rerun/enforce`).then(() => {
                                    toast.success("Re-queued for enforcement");
                                    if (onDocUpdated) api.get(`/documents/${doc.id}`).then(({ data }) => onDocUpdated(data));
                                }).catch(() => toast.error("Failed to rerun"));
                            }
                        }}
                            className="inline-flex items-center gap-1 px-2.5 py-1 text-[10px] font-medium text-white bg-red-600 hover:bg-red-700 rounded-md transition-colors">
                            <RefreshCw className="size-2.5" /> Retry
                        </button>
                    </div>
                    <div className="text-xs text-red-700 bg-white rounded-md border border-red-200 px-3 py-2 font-mono break-words">
                        {doc.lastError}
                    </div>
                    <div className="flex items-center gap-3 mt-2 text-[10px] text-red-400">
                        {doc.lastErrorStage && <span>Stage: {doc.lastErrorStage}</span>}
                        {doc.failedAt && <span>Failed: {new Date(doc.failedAt).toLocaleString()}</span>}
                        {(doc.retryCount ?? 0) > 0 && <span>Retries: {doc.retryCount}</span>}
                    </div>
                </div>
            )}

            {/* IG Classification */}
            {(classification || doc.categoryName) && (
                <Section title="IG Classification" icon={Brain} defaultOpen>
                    {doc.sensitivityLabel && (
                        <div className="mb-3">
                            <span className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-semibold rounded-lg border ${SENSITIVITY_COLORS[doc.sensitivityLabel] ?? ""}`}>
                                <Shield className="size-3.5" />
                                {doc.sensitivityLabel}
                            </span>
                        </div>
                    )}
                    {doc.summary && (
                        <p className="text-xs text-gray-600 leading-relaxed mb-3 italic">
                            {doc.summary}
                        </p>
                    )}
                    {categoryPath && (
                        <div className="flex items-start gap-2 mb-1">
                            <span className="text-xs text-gray-500 shrink-0">Filed under</span>
                            <span className="text-xs font-medium text-gray-900 flex items-center gap-1">
                                <FolderOpen className="size-3 text-blue-500 shrink-0" />
                                {categoryPath}
                            </span>
                        </div>
                    )}
                    <Row label="Status" value={doc.status.replace(/_/g, " ")} />

                    {classification && (
                        <>
                            <div className="mt-2">
                                <div className="flex items-center justify-between text-xs mb-1">
                                    <span className="text-gray-500">Confidence</span>
                                    <span className={`font-bold ${confidenceColor(classification.confidence)}`}>
                                        {(classification.confidence * 100).toFixed(0)}%
                                    </span>
                                </div>
                                <div className="w-full bg-gray-200 rounded-full h-1.5">
                                    <div
                                        className={`h-1.5 rounded-full transition-all ${confidenceBarColor(classification.confidence)}`}
                                        style={{ width: `${classification.confidence * 100}%` }}
                                    />
                                </div>
                            </div>

                            <Row label="Model" value={classification.modelId || "unknown"} />
                            <Row label="Classified" value={new Date(classification.classifiedAt).toLocaleString()} />

                            <div className="flex items-center gap-1.5 mt-2">
                                {classification.humanReviewed ? (
                                    <>
                                        <CheckCircle className="size-3.5 text-green-600" />
                                        <span className="text-xs text-green-700 font-medium">
                                            Reviewed{classification.reviewedBy ? ` by ${classification.reviewedBy}` : ""}
                                        </span>
                                    </>
                                ) : doc.status === "REVIEW_REQUIRED" ? (
                                    <>
                                        <AlertTriangle className="size-3.5 text-amber-500" />
                                        <span className="text-xs text-amber-600">In review queue</span>
                                    </>
                                ) : (
                                    <>
                                        <CheckCircle className="size-3.5 text-blue-500" />
                                        <span className="text-xs text-blue-600">Auto-approved (high confidence)</span>
                                    </>
                                )}
                            </div>
                        </>
                    )}
                </Section>
            )}

            {/* Reasoning */}
            {classification?.reasoning && (
                <Section title="Classification Reasoning" icon={Brain}>
                    <p className="text-xs text-gray-600 leading-relaxed bg-gray-50 rounded-md p-3 border border-gray-100">
                        {classification.reasoning}
                    </p>
                </Section>
            )}

            {/* Tags */}
            {doc.tags && doc.tags.length > 0 && (
                <Section title="Tags" icon={Tag}>
                    <div className="flex flex-wrap gap-1">
                        {doc.tags.map((tag) => (
                            <span key={tag} className="px-2 py-0.5 text-xs bg-blue-50 text-blue-700 rounded-md border border-blue-100">
                                {tag}
                            </span>
                        ))}
                    </div>
                </Section>
            )}

            {/* Traits */}
            {doc.traits && doc.traits.length > 0 && (
                <Section title="Traits" icon={Tag}>
                    <div className="flex flex-wrap gap-1">
                        {doc.traits.map(trait => {
                            const colors: Record<string, string> = {
                                TEMPLATE: "bg-amber-50 text-amber-700 border-amber-200",
                                DRAFT: "bg-yellow-50 text-yellow-700 border-yellow-200",
                                FINAL: "bg-green-50 text-green-700 border-green-200",
                                SIGNED: "bg-emerald-50 text-emerald-700 border-emerald-200",
                                INBOUND: "bg-blue-50 text-blue-700 border-blue-200",
                                OUTBOUND: "bg-indigo-50 text-indigo-700 border-indigo-200",
                                INTERNAL: "bg-gray-50 text-gray-700 border-gray-200",
                                ORIGINAL: "bg-purple-50 text-purple-700 border-purple-200",
                                COPY: "bg-gray-50 text-gray-600 border-gray-200",
                                SCAN: "bg-gray-50 text-gray-600 border-gray-200",
                                GENERATED: "bg-cyan-50 text-cyan-700 border-cyan-200",
                            };
                            return (
                                <span key={trait} className={`px-2 py-0.5 text-xs font-medium rounded-md border ${colors[trait] ?? "bg-gray-50 text-gray-600 border-gray-200"}`}>
                                    {trait}
                                </span>
                            );
                        })}
                    </div>
                    {doc.traits.includes("TEMPLATE") && (
                        <p className="text-[10px] text-amber-600 mt-1">Template document — PII findings are informational only</p>
                    )}
                </Section>
            )}

            {/* PII Findings — consolidated by matched text */}
            {doc.piiFindings && doc.piiFindings.length > 0 && (() => {
                const activeFindings = doc.piiFindings.filter(p => !p.dismissed);
                const dismissedFindings = doc.piiFindings.filter(p => p.dismissed);

                // Group active findings by matchedText (case-insensitive)
                const grouped = new Map<string, { pii: typeof activeFindings[0]; indices: number[]; count: number }>();
                doc.piiFindings.forEach((pii, i) => {
                    if (pii.dismissed) return;
                    const key = `${pii.type}::${pii.matchedText.toLowerCase()}`;
                    const existing = grouped.get(key);
                    if (existing) {
                        existing.count++;
                        existing.indices.push(i);
                    } else {
                        grouped.set(key, { pii, indices: [i], count: 1 });
                    }
                });

                const uniqueCount = grouped.size;

                return (
                    <Section title={`PII Detected (${uniqueCount}${activeFindings.length !== uniqueCount ? ` / ${activeFindings.length} occurrences` : ""})`} icon={AlertTriangle} defaultOpen
                        onEdit={() => setShowPiiReport(true)}>
                        <div className="space-y-2">
                            {[...grouped.values()].map(({ pii, indices, count }) => (
                                <PiiFindingCard key={indices[0]} pii={pii} index={indices[0]} documentId={doc.id}
                                    count={count}
                                    onDismissed={(updated) => { if (onDocUpdated) onDocUpdated(updated); }} />
                            ))}
                            {dismissedFindings.length > 0 && (
                                <details className="group">
                                    <summary className="text-[10px] text-gray-400 cursor-pointer hover:text-gray-600">
                                        {dismissedFindings.length} dismissed finding{dismissedFindings.length !== 1 ? "s" : ""}
                                    </summary>
                                    <div className="space-y-2 mt-2">
                                        {dismissedFindings.map((pii, i) => {
                                            const origIdx = doc.piiFindings!.indexOf(pii);
                                            return <PiiFindingCard key={origIdx} pii={pii} index={origIdx} documentId={doc.id}
                                                count={1} onDismissed={(updated) => { if (onDocUpdated) onDocUpdated(updated); }} />;
                                        })}
                                    </div>
                                </details>
                            )}
                        </div>
                        {doc.piiScannedAt && (
                            <p className="text-[10px] text-gray-400 mt-2">Scanned {new Date(doc.piiScannedAt).toLocaleString()}</p>
                        )}
                    </Section>
                );
            })()}

            {doc.piiFindings && doc.piiFindings.length === 0 && doc.piiScannedAt && (
                <Section title="PII Detection" icon={Shield}>
                    <div className="flex items-center gap-1.5">
                        <CheckCircle className="size-3.5 text-green-500" />
                        <span className="text-xs text-green-700">No PII detected</span>
                    </div>
                    <p className="text-[10px] text-gray-400 mt-1">Scanned {new Date(doc.piiScannedAt).toLocaleString()}</p>
                </Section>
            )}

            {/* Extracted Metadata — always show, pencil to edit */}
            <Section title={`Metadata${doc.extractedMetadata ? ` (${Object.keys(doc.extractedMetadata).filter(k => !k.startsWith("_")).length})` : ""}`}
                icon={Layers} onEdit={() => setShowMetadata(true)}
                defaultOpen={!!doc.extractedMetadata && Object.keys(doc.extractedMetadata).filter(k => !k.startsWith("_")).length > 0}>
                {doc.extractedMetadata && Object.keys(doc.extractedMetadata).filter(k => !k.startsWith("_")).length > 0 ? (
                    <div className="space-y-1.5">
                        {Object.entries(doc.extractedMetadata)
                            .filter(([k]) => !k.startsWith("_"))
                            .map(([key, value]) => (
                                <div key={key} className="flex justify-between items-start gap-3">
                                    <span className="text-xs text-gray-500 shrink-0">{key.replace(/_/g, " ")}</span>
                                    <span className={`text-xs font-medium text-right break-words min-w-0 ${
                                        value === "NOT_FOUND" ? "text-amber-500 italic" : "text-gray-900"
                                    }`}>{value}</span>
                                </div>
                            ))}
                        <div className="pt-1.5 border-t border-gray-100">
                            <button onClick={() => setShowSchemaTest(true)}
                                className="inline-flex items-center gap-1 text-[10px] text-blue-600 hover:text-blue-800 font-medium">
                                <FlaskConical className="size-3" /> Test Schema
                            </button>
                        </div>
                    </div>
                ) : (
                    <p className="text-xs text-gray-400 italic">No metadata extracted yet</p>
                )}
                <MetadataQuality docId={doc.id} categoryId={doc.categoryId} onSuggest={() => setShowSuggest(true)} onTestSchema={() => setShowSchemaTest(true)} />
            </Section>

            {/* Dublin Core Metadata */}
            <Section title="Dublin Core" icon={BookOpen} defaultOpen={hasDc}>
                {hasDc ? (
                    <div className="space-y-1.5">
                        {Object.entries(DC_LABELS).map(([key, label]) => {
                            const val = dc[key];
                            if (!val) return null;
                            return <Row key={key} label={label} value={val} />;
                        })}
                    </div>
                ) : (
                    <p className="text-xs text-gray-400 italic">No Dublin Core metadata extracted</p>
                )}
            </Section>

            {/* Governance */}
            {(doc.retentionScheduleId || doc.legalHold || (doc.appliedPolicyIds && doc.appliedPolicyIds.length > 0)) && (
                <Section title="Governance" icon={Layers} defaultOpen>
                    {doc.retentionScheduleId && (
                        <div className="flex justify-between items-start gap-2">
                            <span className="text-xs text-gray-500 shrink-0">Retention</span>
                            <button onClick={() => setViewRetention(true)} className="text-xs font-medium text-blue-600 hover:text-blue-800 flex items-center gap-1">
                                {retentionName ?? doc.retentionScheduleId}
                                <ExternalLink className="size-2.5" />
                            </button>
                        </div>
                    )}
                    {doc.retentionExpiresAt && <Row label="Expires" value={new Date(doc.retentionExpiresAt).toLocaleDateString()} />}
                    {doc.legalHold && (
                        <div className="flex items-center gap-1.5 mt-1">
                            <Lock className="size-3.5 text-red-600" />
                            <span className="text-xs text-red-700 font-medium">Legal hold active</span>
                        </div>
                    )}
                    {doc.legalHoldReason && <Row label="Hold reason" value={doc.legalHoldReason} />}
                    {doc.appliedPolicyIds && doc.appliedPolicyIds.length > 0 && (
                        <div className="mt-2">
                            <span className="text-xs text-gray-500 block mb-1">Applied Policies</span>
                            <div className="space-y-1">
                                {doc.appliedPolicyIds.map((pid) => (
                                    <button key={pid} onClick={() => setViewPolicyId(pid)}
                                        className="flex items-center gap-1.5 text-xs text-blue-600 hover:text-blue-800 hover:bg-blue-50 rounded px-1.5 py-0.5 -mx-1.5 w-full text-left">
                                        <Shield className="size-3 shrink-0" />
                                        <span className="truncate">{policyNames.get(pid) ?? pid}</span>
                                        <ExternalLink className="size-2.5 shrink-0 ml-auto" />
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}
                </Section>
            )}

            {/* Storage Location */}
            <Section title="Storage" icon={Layers}>
                <Row label="Provider" value={doc.storageProvider === "GOOGLE_DRIVE" ? "Google Drive" : "Local (MinIO)"} />
                {doc.storageProvider === "GOOGLE_DRIVE" && doc.externalStorageRef && (
                    <>
                        <Row label="Owner" value={doc.externalStorageRef.ownerEmail} />
                        <Row label="Account" value={doc.externalStorageRef.providerAccountEmail} />
                        {doc.externalStorageRef.webViewLink && (
                            <div className="flex justify-between items-start gap-3">
                                <span className="text-xs text-gray-500 shrink-0">View in Drive</span>
                                <a href={doc.externalStorageRef.webViewLink} target="_blank" rel="noopener noreferrer"
                                    className="text-xs font-medium text-blue-600 hover:text-blue-800 flex items-center gap-1">
                                    Open <ExternalLink className="size-2.5" />
                                </a>
                            </div>
                        )}
                    </>
                )}
                {doc.storageProvider !== "GOOGLE_DRIVE" && (
                    <>
                        {doc.storageBucket && <Row label="Bucket" value={doc.storageBucket} />}
                        {doc.storageKey && <Row label="Object Key" value={doc.storageKey} />}
                        {doc.storageTierId && <Row label="Storage Tier" value={doc.storageTierId} />}
                    </>
                )}
            </Section>

            {/* File Details */}
            <Section title="File Details" icon={FileText}>
                <Row label="MIME type" value={doc.mimeType} />
                {doc.pageCount != null && doc.pageCount > 0 && <Row label="Pages" value={String(doc.pageCount)} />}
                <Row label="Uploaded" value={new Date(doc.createdAt).toLocaleString()} />
                {doc.classifiedAt && <Row label="Classified" value={new Date(doc.classifiedAt).toLocaleString()} />}
                {doc.governanceAppliedAt && <Row label="Governance" value={new Date(doc.governanceAppliedAt).toLocaleString()} />}
            </Section>

            {/* Actions */}
            <div className="p-4 space-y-3">
                <button onClick={() => onDownload(doc)}
                    className="w-full inline-flex items-center justify-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">
                    <Download className="size-4" /> Download
                </button>

                {/* Manual override */}
                <button onClick={() => setShowReclassify(true)} disabled={isProcessing}
                    className="w-full inline-flex items-center justify-center gap-2 px-3 py-2 border border-amber-300 text-amber-700 text-xs font-medium rounded-lg hover:bg-amber-50 disabled:opacity-50">
                    <Pencil className="size-3.5" /> Reclassify
                </button>

                {/* Re-run pipeline stages */}
                <div>
                    <div className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Re-run Stage</div>
                    <div className="grid grid-cols-3 gap-1.5">
                        <StageButton label="Extract" onClick={async () => {
                            try { await api.post(`/documents/${doc.id}/rerun/extract`); toast.success("Text extraction re-queued"); if (onDocUpdated) { const { data } = await api.get(`/documents/${doc.id}`); onDocUpdated(data); } }
                            catch { toast.error("Failed"); }
                        }} disabled={isProcessing} />
                        <StageButton label="PII Scan" onClick={async () => {
                            try { const { data } = await api.post(`/documents/${doc.id}/rerun/pii`); toast.success("PII re-scanned"); if (onDocUpdated) onDocUpdated(data); }
                            catch { toast.error("Failed"); }
                        }} disabled={isProcessing || !doc.status || doc.status === "UPLOADED"} />
                        <StageButton label="Classify" onClick={async () => {
                            try { await api.post(`/documents/${doc.id}/rerun/classify`); toast.success("Re-classification queued"); if (onDocUpdated) { const { data } = await api.get(`/documents/${doc.id}`); onDocUpdated(data); } }
                            catch { toast.error("Failed"); }
                        }} disabled={isProcessing || !doc.status || doc.status === "UPLOADED"} />
                        <StageButton label="Enforce" onClick={async () => {
                            try { await api.post(`/documents/${doc.id}/rerun/enforce`); toast.success("Governance re-enforcement queued"); if (onDocUpdated) { const { data } = await api.get(`/documents/${doc.id}`); onDocUpdated(data); } }
                            catch { toast.error("Failed"); }
                        }} disabled={isProcessing || !doc.categoryId} />
                        <StageButton label="Full" onClick={() => { handleReprocess(); }} disabled={isProcessing || reprocessing} highlight />
                    </div>
                </div>
            </div>

            {/* Reclassify Modal */}
            {showReclassify && (
                <ReclassifyModal
                    doc={doc}
                    onClose={() => setShowReclassify(false)}
                    onSaved={(updated) => { setShowReclassify(false); if (onDocUpdated) onDocUpdated(updated); }}
                />
            )}

            {/* Metadata Modal */}
            {showMetadata && (
                <MetadataModal
                    documentId={doc.id}
                    onClose={() => setShowMetadata(false)}
                    onSaved={(updated) => { setShowMetadata(false); if (onDocUpdated) onDocUpdated(updated); }}
                />
            )}

            {/* Schema Test Modal */}
            {showSchemaTest && (
                <SchemaTestModal documentId={doc.id} onClose={() => setShowSchemaTest(false)} />
            )}

            {/* Suggest Schema Modal */}
            {showSuggest && (
                <SuggestSchemaModal documentId={doc.id} onClose={() => setShowSuggest(false)} />
            )}

            {/* PII Report Modal */}
            {showPiiReport && (
                <PiiReportModal
                    documentId={doc.id}
                    onClose={() => setShowPiiReport(false)}
                    onSaved={() => { setShowPiiReport(false); toast.success("PII report submitted — this will improve future detection"); }}
                />
            )}

            {/* Policy Detail Modal */}
            {viewPolicyId && (
                <PolicyModal policyId={viewPolicyId} onClose={() => setViewPolicyId(null)} />
            )}

            {/* Retention Detail Modal */}
            {viewRetention && doc.retentionScheduleId && (
                <RetentionModal retentionId={doc.retentionScheduleId} onClose={() => setViewRetention(false)} />
            )}

            {/* LLM Reasoning Modal */}
            {showReasoning && (
                <ReasoningModal documentId={doc.id} fileName={doc.originalFileName} onClose={() => setShowReasoning(false)} />
            )}
        </div>
    );
}

function ReasoningModal({ documentId, fileName, onClose }: {
    documentId: string; fileName: string; onClose: () => void;
}) {
    type LogEntry = { stage: string; level: string; message: string; durationMs: number; timestamp: number };
    const [logs, setLogs] = useState<LogEntry[]>([]);
    const bottomRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const es = new EventSource("/api/admin/monitoring/events", { withCredentials: true });

        es.addEventListener("pipeline-log", (e) => {
            try {
                const data = JSON.parse(e.data);
                // Show all logs (tool calls don't have documentId) + logs matching this document
                if (!data.documentId || data.documentId === documentId || data.documentId === "") {
                    setLogs(prev => [...prev, data]);
                }
            } catch {}
        });

        es.addEventListener("document-status", (e) => {
            try {
                const data = JSON.parse(e.data);
                if (data.documentId === documentId) {
                    setLogs(prev => [...prev, {
                        stage: "STATUS", level: "INFO",
                        message: `Status changed to ${data.status}`,
                        durationMs: 0, timestamp: Date.now(),
                    }]);
                }
            } catch {}
        });

        es.onerror = () => { es.close(); };
        return () => es.close();
    }, [documentId]);

    useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: "smooth" }); }, [logs]);

    const stageColors: Record<string, string> = {
        EXTRACTION: "text-blue-500", PII_SCAN: "text-purple-500", CLASSIFICATION: "text-amber-500",
        ENFORCEMENT: "text-green-500", LLM_TOOL: "text-indigo-500", STATUS: "text-gray-500",
    };

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={onClose}>
            <div className="bg-gray-900 rounded-xl shadow-2xl w-full max-w-2xl max-h-[80vh] flex flex-col" onClick={e => e.stopPropagation()}>
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-3 border-b border-gray-700 shrink-0">
                    <div className="flex items-center gap-2">
                        <Brain className="size-4 text-indigo-400" />
                        <span className="text-sm font-semibold text-white">LLM Reasoning</span>
                        <span className="text-xs text-gray-400 truncate max-w-64">{fileName}</span>
                    </div>
                    <button onClick={onClose} className="text-gray-500 hover:text-gray-300" aria-label="Close">
                        <X className="size-4" />
                    </button>
                </div>

                {/* Log stream */}
                <div className="flex-1 overflow-y-auto p-4 font-mono text-xs space-y-0.5 bg-gray-950">
                    {logs.length === 0 && (
                        <div className="text-center py-8 text-gray-600">
                            <Loader2 className="size-5 animate-spin mx-auto mb-2 text-indigo-500" />
                            Waiting for pipeline events...
                        </div>
                    )}
                    {logs.map((entry, i) => (
                        <div key={i} className={`flex items-start gap-2 py-0.5 ${entry.level === "ERROR" ? "bg-red-950/30" : ""}`}>
                            <span className="text-gray-600 w-14 text-right shrink-0 tabular-nums">
                                {new Date(entry.timestamp).toLocaleTimeString()}
                            </span>
                            <span className={`w-16 shrink-0 font-bold ${stageColors[entry.stage] ?? "text-gray-500"}`}>
                                {entry.stage === "LLM_TOOL" ? "TOOL" : entry.stage?.replace(/_/g, " ")}
                            </span>
                            <span className={entry.level === "ERROR" ? "text-red-400" : "text-gray-300"}>
                                {entry.message}
                            </span>
                            {entry.durationMs > 0 && (
                                <span className="text-gray-600 shrink-0 ml-auto">
                                    {entry.durationMs >= 1000 ? `${(entry.durationMs / 1000).toFixed(1)}s` : `${entry.durationMs}ms`}
                                </span>
                            )}
                        </div>
                    ))}
                    <div ref={bottomRef} />
                </div>

                {/* Footer */}
                <div className="px-5 py-2 border-t border-gray-700 flex items-center justify-between shrink-0">
                    <span className="text-[10px] text-gray-500">{logs.length} events</span>
                    <div className="flex items-center gap-1.5">
                        <span className="size-2 rounded-full bg-green-500 animate-pulse" />
                        <span className="text-[10px] text-gray-500">Live</span>
                    </div>
                </div>
            </div>
        </div>
    );
}

function StageButton({ label, onClick, disabled, highlight }: {
    label: string; onClick: () => void; disabled?: boolean; highlight?: boolean;
}) {
    return (
        <button onClick={onClick} disabled={disabled}
            className={`px-2 py-1.5 text-[10px] font-medium rounded transition-colors disabled:opacity-40 ${
                highlight
                    ? "bg-gray-800 text-white hover:bg-gray-700"
                    : "border border-gray-200 text-gray-600 hover:bg-gray-50"
            }`}>
            {label}
        </button>
    );
}

function PiiFindingCard({ pii, index, documentId, count, onDismissed }: {
    pii: { type: string; matchedText: string; redactedText: string; confidence: number; method: string; dismissed?: boolean; dismissedBy?: string; dismissalReason?: string };
    index: number;
    documentId: string;
    count: number;
    onDismissed: (doc: Doc) => void;
}) {
    const [showDismiss, setShowDismiss] = useState(false);

    if (pii.dismissed) {
        return (
            <div className="p-2 bg-gray-50 rounded-md border border-gray-200 opacity-60">
                <div className="flex items-center gap-1.5">
                    <span className="text-xs font-semibold text-gray-400 line-through">{pii.type.replace(/_/g, " ")}</span>
                    <span className="px-1 py-0.5 text-[10px] rounded bg-gray-100 text-gray-500">FALSE POSITIVE</span>
                </div>
                <code className="text-xs text-gray-400 font-mono block mt-0.5 line-through">{pii.redactedText}</code>
                {pii.dismissalReason && (
                    <p className="text-[10px] text-gray-400 mt-1">{pii.dismissalReason}</p>
                )}
                {pii.dismissedBy && (
                    <p className="text-[10px] text-gray-400">Dismissed by {pii.dismissedBy}</p>
                )}
            </div>
        );
    }

    return (
        <>
            <div className="p-2 bg-red-50 rounded-md border border-red-100">
                <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                        <div className="flex items-center gap-1.5">
                            <span className="text-xs font-semibold text-red-700">{pii.type.replace(/_/g, " ")}</span>
                            <span className={`px-1 py-0.5 text-[10px] rounded ${pii.method === "PATTERN" ? "bg-gray-100 text-gray-600" : "bg-purple-100 text-purple-700"}`}>
                                {pii.method}
                            </span>
                            {count > 1 && (
                                <span className="px-1.5 py-0.5 text-[10px] font-bold rounded-full bg-red-200 text-red-800">
                                    &times;{count}
                                </span>
                            )}
                        </div>
                        <code className="text-xs text-red-600 font-mono block mt-0.5">{pii.redactedText}</code>
                    </div>
                    <div className="flex items-center gap-1.5 shrink-0">
                        <span className="text-[10px] text-gray-400">{(pii.confidence * 100).toFixed(0)}%</span>
                        <button onClick={() => setShowDismiss(true)}
                            className="text-[10px] text-gray-400 hover:text-amber-600 underline">
                            Not PII?
                        </button>
                    </div>
                </div>
            </div>

            {showDismiss && (
                <PiiDismissModal
                    pii={pii}
                    index={index}
                    documentId={documentId}
                    onClose={() => setShowDismiss(false)}
                    onDismissed={onDismissed}
                />
            )}
        </>
    );
}

function PiiDismissModal({ pii, index, documentId, onClose, onDismissed }: {
    pii: { type: string; matchedText: string; redactedText: string; confidence: number; method: string };
    index: number;
    documentId: string;
    onClose: () => void;
    onDismissed: (doc: Doc) => void;
}) {
    const [reason, setReason] = useState("");
    const [context, setContext] = useState("");
    const [dismissing, setDismissing] = useState(false);

    const handleDismiss = async () => {
        if (!reason.trim()) { toast.error("Please explain why this is not PII"); return; }
        setDismissing(true);
        try {
            const { data } = await api.post(`/documents/${documentId}/pii/${index}/dismiss`, {
                reason: reason.trim(),
                context: context.trim() || null,
            });
            toast.success("False positive recorded — this will improve future detection accuracy");
            onClose();
            onDismissed(data);
        } catch {
            toast.error("Failed to dismiss PII finding");
        } finally {
            setDismissing(false);
        }
    };

    return (
        <Overlay onClose={onClose}>
            <div className="p-6 space-y-5">
                <div className="flex items-start justify-between">
                    <div>
                        <h3 className="text-lg font-semibold text-gray-900">Dismiss PII Finding</h3>
                        <p className="text-sm text-gray-500 mt-0.5">Mark this detection as a false positive</p>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X className="size-5" /></button>
                </div>

                {/* What was detected */}
                <div className="bg-red-50 rounded-lg p-4 border border-red-100">
                    <div className="flex items-center gap-2 mb-2">
                        <AlertTriangle className="size-4 text-red-500" />
                        <span className="text-sm font-semibold text-red-700">{pii.type.replace(/_/g, " ")}</span>
                        <span className={`px-1.5 py-0.5 text-[10px] rounded ${pii.method === "PATTERN" ? "bg-gray-100 text-gray-600" : "bg-purple-100 text-purple-700"}`}>
                            Detected by {pii.method === "PATTERN" ? "regex pattern" : "LLM"}
                        </span>
                    </div>
                    <code className="text-sm text-red-800 font-mono bg-red-100 rounded px-2 py-1 block">{pii.redactedText}</code>
                </div>

                {/* Guidance */}
                <div className="bg-amber-50 rounded-lg p-4 border border-amber-100 space-y-2">
                    <h4 className="text-xs font-semibold text-amber-800 uppercase tracking-wider">Why dismiss?</h4>
                    <p className="text-xs text-amber-700 leading-relaxed">
                        Sometimes the system detects patterns that look like personal data but aren&apos;t.
                        Common examples include:
                    </p>
                    <ul className="text-xs text-amber-700 space-y-1 ml-3 list-disc">
                        <li><strong>Business addresses</strong> — a registered office postcode is public, not personal</li>
                        <li><strong>Company registration numbers</strong> — public record, not PII</li>
                        <li><strong>Generic phone numbers</strong> — main switchboard numbers, not personal lines</li>
                        <li><strong>Template placeholders</strong> — &quot;XX-XX-XX-X&quot; in form templates</li>
                        <li><strong>Historical/reference data</strong> — dates that aren&apos;t dates of birth</li>
                    </ul>
                    <p className="text-xs text-amber-700 leading-relaxed">
                        Your explanation helps the AI learn to distinguish real PII from false positives in future documents.
                    </p>
                </div>

                {/* Form */}
                <div className="space-y-4">
                    <div>
                        <label className="text-xs font-medium text-gray-700 block mb-1">
                            Why is this not personal data? <span className="text-red-500">*</span>
                        </label>
                        <p className="text-[11px] text-gray-400 mb-1.5">
                            Explain why the detected value is not PII. Be specific — e.g. &quot;This is a registered
                            company address that appears on public Companies House records&quot;
                        </p>
                        <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3}
                            placeholder="e.g. This postcode belongs to the company's registered office address, which is public information on Companies House. It does not identify an individual."
                            className="w-full text-sm border border-gray-300 rounded-md px-3 py-2 resize-none focus:ring-2 focus:ring-amber-500 focus:border-amber-500" />
                    </div>

                    <div>
                        <label className="text-xs font-medium text-gray-700 block mb-1">
                            What type of data is this instead?
                        </label>
                        <p className="text-[11px] text-gray-400 mb-1.5">
                            Help the AI understand the context. What does this data actually represent?
                            This helps prevent the same false positive on similar documents.
                        </p>
                        <input type="text" value={context} onChange={(e) => setContext(e.target.value)}
                            placeholder="e.g. Registered company office address, Companies House public record"
                            className="w-full text-sm border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-amber-500 focus:border-amber-500" />
                    </div>
                </div>

                {/* Actions */}
                <div className="flex gap-3 justify-end pt-2 border-t border-gray-200">
                    <button onClick={onClose}
                        className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">
                        Cancel
                    </button>
                    <button onClick={handleDismiss} disabled={dismissing}
                        className="px-4 py-2 text-sm font-medium text-white bg-amber-600 rounded-md hover:bg-amber-700 disabled:opacity-50">
                        {dismissing ? "Submitting..." : "Dismiss as False Positive"}
                    </button>
                </div>
            </div>
        </Overlay>
    );
}

function Section({ title, icon: Icon, defaultOpen = false, onEdit, children }: {
    title: string; icon: React.ElementType; defaultOpen?: boolean; onEdit?: () => void; children: React.ReactNode;
}) {
    const [open, setOpen] = useState(defaultOpen);
    return (
        <div className="border-b border-gray-100">
            <div className="flex items-center">
                <button
                    onClick={() => setOpen(!open)}
                    className="flex-1 flex items-center gap-1.5 px-4 py-2.5 text-left hover:bg-gray-50 transition-colors"
                >
                    {open ? <ChevronDown className="size-3.5 text-gray-400" /> : <ChevronRight className="size-3.5 text-gray-400" />}
                    <Icon className="size-3.5 text-gray-400" />
                    <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider flex-1">{title}</span>
                </button>
                {onEdit && (
                    <button onClick={(e) => { e.stopPropagation(); onEdit(); }}
                        className="px-3 py-2.5 text-gray-300 hover:text-blue-600 transition-colors">
                        <Pencil className="size-3" />
                    </button>
                )}
            </div>
            {open && <div className="px-4 pb-3 space-y-1.5">{children}</div>}
        </div>
    );
}

function Row({ label, value }: { label: string; value?: string }) {
    if (!value) return null;
    return (
        <div className="flex justify-between items-start gap-3">
            <span className="text-xs text-gray-500 shrink-0">{label}</span>
            <span className="text-xs font-medium text-gray-900 text-right break-words min-w-0">{value}</span>
        </div>
    );
}

/* ── Modals ─────────────────────────────────────────── */

function ReclassifyModal({ doc, onClose, onSaved }: {
    doc: Doc; onClose: () => void; onSaved: (doc: Doc) => void;
}) {
    const [categories, setCategories] = useState<{ id: string; name: string; parentId?: string; defaultSensitivity: string }[]>([]);
    const [sensitivities, setSensitivities] = useState<{ key: string; displayName: string }[]>([]);
    const [categoryId, setCategoryId] = useState(doc.categoryId ?? "");
    const [categoryName, setCategoryName] = useState(doc.categoryName ?? "");
    const [sensitivity, setSensitivity] = useState(doc.sensitivityLabel ?? "");
    const [reason, setReason] = useState("");
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        api.get("/admin/governance/taxonomy").then(({ data }) => setCategories(data)).catch(() => {});
        api.get("/admin/governance/sensitivities").then(({ data }) => setSensitivities(data)).catch(() => {});
    }, []);

    const handleCategoryChange = (id: string) => {
        setCategoryId(id);
        const cat = categories.find((c) => c.id === id);
        if (cat) {
            setCategoryName(cat.name);
            setSensitivity(cat.defaultSensitivity);
        }
    };

    const handleSave = async () => {
        if (!categoryId || !sensitivity || !reason.trim()) {
            toast.error("Please fill in all fields including a reason");
            return;
        }
        setSaving(true);
        try {
            const { data } = await api.post(`/review/${doc.id}/override`, {
                categoryId,
                categoryName,
                sensitivityLabel: sensitivity,
                tags: doc.tags ?? [],
                reason: reason.trim(),
            });
            toast.success("Document reclassified");
            onSaved(data);
        } catch {
            toast.error("Reclassification failed");
        } finally {
            setSaving(false);
        }
    };

    // Build tree for grouped select
    const roots = categories.filter((c) => !c.parentId);
    const childrenOf = (pid: string) => categories.filter((c) => c.parentId === pid);

    return (
        <Overlay onClose={onClose}>
            <div className="p-6 space-y-4">
                <div className="flex items-start justify-between">
                    <div>
                        <h3 className="text-lg font-semibold text-gray-900">Reclassify Document</h3>
                        <p className="text-sm text-gray-500 mt-0.5">{doc.originalFileName || doc.fileName}</p>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X className="size-5" /></button>
                </div>

                {doc.categoryName && (
                    <div className="bg-gray-50 rounded-lg p-3 flex items-center gap-3">
                        <span className="text-xs text-gray-500">Current:</span>
                        <span className="text-sm font-medium text-gray-900">{doc.categoryName}</span>
                        {doc.sensitivityLabel && (
                            <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${SENSITIVITY_COLORS[doc.sensitivityLabel] ?? ""}`}>
                                {doc.sensitivityLabel}
                            </span>
                        )}
                    </div>
                )}

                <div>
                    <label className="text-xs font-medium text-gray-700 block mb-1">New Category</label>
                    <select value={categoryId} onChange={(e) => handleCategoryChange(e.target.value)}
                        className="w-full text-sm border border-gray-300 rounded-md px-3 py-2">
                        <option value="">Select a category...</option>
                        {roots.map((root) => (
                            <optgroup key={root.id} label={root.name}>
                                <option value={root.id}>{root.name}</option>
                                {childrenOf(root.id).map((child) => (
                                    <option key={child.id} value={child.id}>&nbsp;&nbsp;{child.name}</option>
                                ))}
                            </optgroup>
                        ))}
                    </select>
                </div>

                <div>
                    <label className="text-xs font-medium text-gray-700 block mb-1">Sensitivity Level</label>
                    <div className="flex gap-2">
                        {sensitivities.map((s) => (
                            <button key={s.key} onClick={() => setSensitivity(s.key)}
                                className={`flex-1 px-3 py-2 text-xs font-medium rounded-lg border-2 transition-colors ${
                                    sensitivity === s.key
                                        ? SENSITIVITY_COLORS[s.key]?.replace("bg-", "bg-").replace("text-", "text-") + " border-current"
                                        : "bg-white text-gray-500 border-gray-200 hover:border-gray-300"
                                }`}>
                                {s.displayName}
                            </button>
                        ))}
                    </div>
                </div>

                <div>
                    <label className="text-xs font-medium text-gray-700 block mb-1">Reason for reclassification</label>
                    <textarea value={reason} onChange={(e) => setReason(e.target.value)}
                        placeholder="Why is this document being reclassified? This is recorded in the audit trail..."
                        rows={3}
                        className="w-full text-sm border border-gray-300 rounded-md px-3 py-2" />
                </div>

                <div className="flex gap-3 pt-2">
                    <button onClick={handleSave} disabled={saving || !categoryId || !sensitivity || !reason.trim()}
                        className="flex-1 inline-flex items-center justify-center gap-2 px-4 py-2 bg-amber-600 text-white text-sm font-medium rounded-lg hover:bg-amber-700 disabled:opacity-50 transition-colors">
                        {saving ? <Loader2 className="size-4 animate-spin" /> : <Pencil className="size-4" />}
                        Reclassify
                    </button>
                    <button onClick={onClose}
                        className="px-4 py-2 border border-gray-300 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-50 transition-colors">
                        Cancel
                    </button>
                </div>
            </div>
        </Overlay>
    );
}

function Overlay({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
            <div className="bg-white rounded-xl shadow-xl max-w-lg w-full mx-4 max-h-[80vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
                {children}
            </div>
        </div>
    );
}

const SC_MODAL: Record<string, string> = {
    PUBLIC: "bg-green-100 text-green-700",
    INTERNAL: "bg-blue-100 text-blue-700",
    CONFIDENTIAL: "bg-amber-100 text-amber-700",
    RESTRICTED: "bg-red-100 text-red-700",
};

function PolicyModal({ policyId, onClose }: { policyId: string; onClose: () => void }) {
    const [policy, setPolicy] = useState<{
        name: string; description: string; version: number; active: boolean;
        rules: string[]; applicableSensitivities: string[];
        enforcementActions: Record<string, string>;
    } | null>(null);

    useEffect(() => {
        api.get("/admin/governance/policies")
            .then(({ data }) => {
                const match = data.find((p: { id: string }) => p.id === policyId);
                if (match) setPolicy(match);
            })
            .catch(() => {});
    }, [policyId]);

    if (!policy) return null;

    return (
        <Overlay onClose={onClose}>
            <div className="p-6">
                <div className="flex items-start justify-between mb-4">
                    <div>
                        <div className="flex items-center gap-2">
                            <Shield className="size-5 text-blue-600" />
                            <h3 className="text-lg font-semibold text-gray-900">{policy.name}</h3>
                        </div>
                        <p className="text-sm text-gray-500 mt-1">{policy.description}</p>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X className="size-5" /></button>
                </div>

                <div className="flex items-center gap-2 mb-4">
                    <span className="text-xs text-gray-400">v{policy.version}</span>
                    <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${policy.active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>
                        {policy.active ? "Active" : "Inactive"}
                    </span>
                </div>

                <div className="mb-4">
                    <h4 className="text-sm font-medium text-gray-700 mb-2">Rules</h4>
                    <ul className="space-y-1.5">
                        {policy.rules.map((r, i) => (
                            <li key={i} className="text-sm text-gray-600 flex items-start gap-2">
                                <span className="text-blue-500 mt-0.5 shrink-0">•</span>{r}
                            </li>
                        ))}
                    </ul>
                </div>

                <div className="mb-4">
                    <h4 className="text-sm font-medium text-gray-700 mb-2">Applicable Sensitivities</h4>
                    <div className="flex gap-1.5">
                        {policy.applicableSensitivities.map((s) => (
                            <span key={s} className={`px-2 py-0.5 text-xs font-medium rounded-full ${SC_MODAL[s] ?? ""}`}>{s}</span>
                        ))}
                    </div>
                </div>

                {Object.keys(policy.enforcementActions).length > 0 && (
                    <div>
                        <h4 className="text-sm font-medium text-gray-700 mb-2">Enforcement Actions</h4>
                        <div className="bg-gray-50 rounded-lg p-3 space-y-1">
                            {Object.entries(policy.enforcementActions).map(([k, v]) => (
                                <div key={k} className="flex justify-between text-sm">
                                    <span className="text-gray-500">{k}</span>
                                    <span className="font-medium text-gray-700">{v}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </Overlay>
    );
}

function RetentionModal({ retentionId, onClose }: { retentionId: string; onClose: () => void }) {
    const [schedule, setSchedule] = useState<{
        name: string; description: string; retentionDays: number;
        dispositionAction: string; legalHoldOverride: boolean; regulatoryBasis: string;
    } | null>(null);

    useEffect(() => {
        api.get("/admin/governance/retention")
            .then(({ data }) => {
                const match = data.find((r: { id: string }) => r.id === retentionId);
                if (match) setSchedule(match);
            })
            .catch(() => {});
    }, [retentionId]);

    if (!schedule) return null;

    const fmtDays = (d: number) => d >= 365 ? `${Math.round(d / 365)} years` : `${d} days`;

    return (
        <Overlay onClose={onClose}>
            <div className="p-6">
                <div className="flex items-start justify-between mb-4">
                    <div>
                        <div className="flex items-center gap-2">
                            <Clock className="size-5 text-blue-600" />
                            <h3 className="text-lg font-semibold text-gray-900">{schedule.name}</h3>
                        </div>
                        <p className="text-sm text-gray-500 mt-1">{schedule.description}</p>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X className="size-5" /></button>
                </div>

                <div className="bg-gray-50 rounded-lg p-4 space-y-3">
                    <div className="flex justify-between">
                        <span className="text-sm text-gray-500">Retention Period</span>
                        <span className="text-sm font-semibold text-gray-900">{fmtDays(schedule.retentionDays)}</span>
                    </div>
                    <div className="flex justify-between">
                        <span className="text-sm text-gray-500">Disposition Action</span>
                        <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-gray-200 text-gray-700">{schedule.dispositionAction}</span>
                    </div>
                    <div className="flex justify-between">
                        <span className="text-sm text-gray-500">Legal Hold Override</span>
                        <span className={`text-sm font-medium ${schedule.legalHoldOverride ? "text-amber-600" : "text-gray-400"}`}>
                            {schedule.legalHoldOverride ? "Yes" : "No"}
                        </span>
                    </div>
                    <div className="flex justify-between">
                        <span className="text-sm text-gray-500">Regulatory Basis</span>
                        <span className="text-sm font-medium text-gray-700">{schedule.regulatoryBasis}</span>
                    </div>
                </div>
            </div>
        </Overlay>
    );
}

/* ── Metadata Quality Indicator ────────────────────── */

type QualityData = {
    totalFields: number;
    extractedFields: number;
    missingFields: number;
    notExtractedFields: number;
    completeness: number;
    fields: { fieldName: string; status: "FOUND" | "MISSING" | "NOT_EXTRACTED" }[];
    schemaExists: boolean;
};

function MetadataQuality({ docId, categoryId, onSuggest, onTestSchema }: {
    docId: string;
    categoryId?: string;
    onSuggest: () => void;
    onTestSchema: () => void;
}) {
    const [quality, setQuality] = useState<QualityData | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(false);

    useEffect(() => {
        if (!categoryId) return;
        setLoading(true);
        setError(false);
        api.get(`/admin/governance/metadata-schemas/quality/${docId}`)
            .then(({ data }) => setQuality(data))
            .catch(() => setError(true))
            .finally(() => setLoading(false));
    }, [docId, categoryId]);

    if (!categoryId) return null;

    if (loading) {
        return (
            <div className="flex items-center gap-1.5 pt-2">
                <Loader2 className="size-3 animate-spin text-gray-400" />
                <span className="text-[10px] text-gray-400">Checking schema coverage...</span>
            </div>
        );
    }

    if (error) return null;

    if (quality && !quality.schemaExists) {
        return (
            <div className="pt-2 space-y-1.5">
                <div className="flex items-center gap-1.5">
                    <HelpCircle className="size-3 text-gray-400" />
                    <span className="text-[10px] text-gray-400">No metadata schema linked to this category</span>
                </div>
                <div className="flex items-center gap-2">
                    <button onClick={onSuggest}
                        className="inline-flex items-center gap-1 text-[10px] text-purple-600 hover:text-purple-800 font-medium">
                        <Sparkles className="size-3" /> Suggest Schema
                    </button>
                    <button onClick={onTestSchema}
                        className="inline-flex items-center gap-1 text-[10px] text-blue-600 hover:text-blue-800 font-medium">
                        <FlaskConical className="size-3" /> Test Schema
                    </button>
                </div>
            </div>
        );
    }

    if (!quality || quality.totalFields === 0) return null;

    const pct = Math.round(quality.completeness * 100);

    return (
        <div className="pt-2 space-y-2">
            {/* Completeness bar */}
            <div className="space-y-1">
                <div className="flex items-center justify-between">
                    <span className="text-[10px] text-gray-500 font-medium">
                        {quality.extractedFields}/{quality.totalFields} fields extracted — {pct}%
                    </span>
                </div>
                <div className="w-full h-1.5 bg-gray-100 rounded-full overflow-hidden">
                    <div
                        className={`h-full rounded-full transition-all ${
                            pct >= 80 ? "bg-green-500" : pct >= 50 ? "bg-amber-500" : "bg-red-500"
                        }`}
                        style={{ width: `${pct}%` }}
                    />
                </div>
            </div>

            {/* Per-field status icons */}
            <div className="flex flex-wrap gap-1.5">
                {quality.fields.map((f) => (
                    <span key={f.fieldName} className="inline-flex items-center gap-1 text-[10px] text-gray-500" title={f.fieldName.replace(/_/g, " ")}>
                        {f.status === "FOUND" && <CheckCircle className="size-3 text-green-500" />}
                        {f.status === "MISSING" && <AlertTriangle className="size-3 text-amber-500" />}
                        {f.status === "NOT_EXTRACTED" && <Minus className="size-3 text-gray-300" />}
                        <span className="max-w-[80px] truncate">{f.fieldName.replace(/_/g, " ")}</span>
                    </span>
                ))}
            </div>
        </div>
    );
}

/* ── Suggest Schema Modal ─────────────────────────── */

type SuggestedField = {
    fieldName: string;
    fieldType: string;
    required: boolean;
    description: string;
    examples: string[];
};

function SuggestSchemaModal({ documentId, onClose }: {
    documentId: string;
    onClose: () => void;
}) {
    const [loading, setLoading] = useState(true);
    const [fields, setFields] = useState<SuggestedField[]>([]);
    const [schemaName, setSchemaName] = useState("");
    const [error, setError] = useState(false);

    useEffect(() => {
        api.post("/admin/governance/metadata-schemas/suggest", { documentId })
            .then(({ data }) => {
                setFields(data.fields ?? []);
                setSchemaName(data.name ?? "Suggested Schema");
            })
            .catch(() => { setError(true); toast.error("Failed to generate schema suggestion"); })
            .finally(() => setLoading(false));
    }, [documentId]);

    const handleCreate = () => {
        // Navigate to governance metadata-schemas tab with suggestion data in sessionStorage
        const suggestion = JSON.stringify({ name: schemaName, fields });
        sessionStorage.setItem("gls-schema-suggestion", suggestion);
        window.location.href = "/governance#metadata-schemas";
    };

    return (
        <div className="fixed inset-0 bg-black/40 flex items-start justify-center z-50 pt-[10vh] px-4"
            onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
            <div className="bg-white rounded-lg shadow-xl w-full max-w-lg flex flex-col max-h-[80vh]">
                <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200 shrink-0">
                    <h3 className="text-base font-semibold text-gray-900">Suggested Schema</h3>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600 p-1 rounded" aria-label="Close">
                        <X className="size-5" />
                    </button>
                </div>

                <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
                    {loading ? (
                        <div className="flex flex-col items-center justify-center py-12 gap-3">
                            <Loader2 className="size-6 animate-spin text-purple-500" />
                            <span className="text-sm text-gray-500">Analysing document to suggest fields...</span>
                        </div>
                    ) : error ? (
                        <p className="text-sm text-red-500 text-center py-8">Failed to generate suggestion. Try again later.</p>
                    ) : (
                        <>
                            <div>
                                <span className="text-xs font-medium text-gray-500 uppercase tracking-wider">Schema Name</span>
                                <p className="text-sm font-medium text-gray-900 mt-0.5">{schemaName}</p>
                            </div>

                            <div className="border border-gray-200 rounded-lg overflow-hidden">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="bg-gray-50 text-left">
                                            <th className="px-3 py-2 text-xs font-medium text-gray-500">Field</th>
                                            <th className="px-3 py-2 text-xs font-medium text-gray-500">Type</th>
                                            <th className="px-3 py-2 text-xs font-medium text-gray-500">Required</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-gray-100">
                                        {fields.map((f) => (
                                            <tr key={f.fieldName}>
                                                <td className="px-3 py-2">
                                                    <span className="text-xs font-medium text-gray-700">{f.fieldName.replace(/_/g, " ")}</span>
                                                    {f.description && <p className="text-[10px] text-gray-400 mt-0.5">{f.description}</p>}
                                                </td>
                                                <td className="px-3 py-2 text-xs text-gray-500">{f.fieldType}</td>
                                                <td className="px-3 py-2">
                                                    {f.required ? <Check className="size-3.5 text-green-500" /> : <Minus className="size-3.5 text-gray-300" />}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>

                            {/* JSON preview */}
                            <details className="text-xs">
                                <summary className="text-gray-400 cursor-pointer hover:text-gray-600">View JSON</summary>
                                <pre className="mt-2 bg-gray-50 border border-gray-200 rounded-md p-3 overflow-x-auto text-[10px] text-gray-600">
                                    {JSON.stringify({ name: schemaName, fields }, null, 2)}
                                </pre>
                            </details>
                        </>
                    )}
                </div>

                {!loading && !error && (
                    <div className="flex gap-2 px-5 py-3 border-t border-gray-200 shrink-0 justify-end">
                        <button onClick={onClose}
                            className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">
                            Close
                        </button>
                        <button onClick={handleCreate}
                            className="px-4 py-2 text-sm font-medium text-white bg-purple-600 rounded-md hover:bg-purple-700 inline-flex items-center gap-1.5">
                            <Sparkles className="size-3.5" /> Create Schema from Suggestion
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}

/* ── Metadata Modal ────────────────────────────────── */

type SchemaField = {
    fieldName: string;
    dataType: string;
    required: boolean;
    description: string;
    examples: string[];
};

function MetadataModal({ documentId, onClose, onSaved }: {
    documentId: string; onClose: () => void; onSaved: (doc: Doc) => void;
}) {
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [schemaId, setSchemaId] = useState<string | null>(null);
    const [hasSchema, setHasSchema] = useState(false);
    const [schemaName, setSchemaName] = useState("");
    const [categoryName, setCategoryName] = useState("");
    const [fields, setFields] = useState<SchemaField[]>([]);
    const [values, setValues] = useState<Record<string, string>>({});
    const [extraFields, setExtraFields] = useState<string[]>([]);
    const [addingField, setAddingField] = useState(false);
    const [newFieldName, setNewFieldName] = useState("");

    useEffect(() => {
        (async () => {
            try {
                const { data } = await api.get(`/documents/${documentId}/metadata-schema`);
                setHasSchema(data.hasSchema);
                setSchemaId(data.schemaId ?? null);
                setSchemaName(data.schemaName ?? "");
                setCategoryName(data.categoryName ?? "");
                setFields(data.fields ?? []);
                setValues(data.currentValues ?? {});
                setExtraFields(data.extraFields ?? []);
            } catch { toast.error("Failed to load metadata"); }
            finally { setLoading(false); }
        })();
    }, [documentId]);

    const updateValue = (key: string, val: string) => {
        setValues(prev => ({ ...prev, [key]: val }));
    };

    const [removingField, setRemovingField] = useState<string | null>(null);

    const handleScopedRemove = async (fieldName: string, scope: ActionScope, reason: string) => {
        try {
            await api.post(`/documents/${documentId}/metadata/remove-field`, { fieldName, scope, reason });
            setExtraFields(prev => prev.filter(k => k !== fieldName));
            setValues(prev => { const next = { ...prev }; delete next[fieldName]; return next; });
            if (scope === "category") {
                setFields(prev => prev.filter(f => f.fieldName !== fieldName));
                toast.success(`Removed "${fieldName.replace(/_/g, " ")}" from this document and the category schema`);
            } else if (scope === "type") {
                toast.success(`Removed "${fieldName.replace(/_/g, " ")}" — AI instructed not to extract for this file type`);
            } else {
                toast.success(`Removed "${fieldName.replace(/_/g, " ")}" from this document`);
            }
            setRemovingField(null);
        } catch { toast.error("Failed to remove field"); }
    };

    const addToSchema = async (key: string) => {
        if (!schemaId) { toast.error("No schema to add to"); return; }
        try {
            await api.post(`/admin/governance/metadata-schemas/${schemaId}/fields`, {
                fieldName: key, dataType: "TEXT", required: false,
                description: "Added from document metadata", extractionHint: "", examples: [values[key] ?? ""],
            });
            toast.success(`"${key.replace(/_/g, " ")}" added to ${schemaName} schema — future documents will extract this field`);
            // Move from extra to schema fields
            setFields(prev => [...prev, { fieldName: key, dataType: "TEXT", required: false, description: "Added from document", extractionHint: "", examples: [] }]);
            setExtraFields(prev => prev.filter(k => k !== key));
        } catch { toast.error("Failed to add to schema"); }
    };

    const removeFromSchema = async (fieldName: string) => {
        if (!schemaId) return;
        if (!confirm(`Remove "${fieldName.replace(/_/g, " ")}" from the ${schemaName} schema? Future documents won't extract this field.`)) return;
        try {
            await api.delete(`/admin/governance/metadata-schemas/${schemaId}/fields/${fieldName}`);
            toast.success(`Removed from schema`);
            setFields(prev => prev.filter(f => f.fieldName !== fieldName));
        } catch { toast.error("Failed"); }
    };

    const confirmAddField = () => {
        if (!newFieldName.trim()) return;
        const normalized = newFieldName.trim().toLowerCase().replace(/[^a-z0-9_]/g, "_");
        setExtraFields(prev => [...prev, normalized]);
        setValues(prev => ({ ...prev, [normalized]: "" }));
        setNewFieldName(""); setAddingField(false);
    };

    const handleSave = async () => {
        const cleaned: Record<string, string> = {};
        for (const [k, v] of Object.entries(values)) {
            if (v && v.trim() && v !== "NOT_FOUND") cleaned[k] = v.trim();
        }
        setSaving(true);
        try {
            const { data } = await api.put(`/documents/${documentId}/metadata`, cleaned);
            toast.success("Metadata saved");
            onSaved(data);
        } catch { toast.error("Failed to save"); }
        finally { setSaving(false); }
    };

    return (
        <Overlay onClose={onClose}>
            <div className="p-6 space-y-5 max-h-[80vh] overflow-y-auto">
                <div className="flex items-start justify-between">
                    <div>
                        <h3 className="text-lg font-semibold text-gray-900">Document Metadata</h3>
                        <div className="flex items-center gap-2 mt-0.5">
                            {hasSchema && <span className="text-xs px-1.5 py-0.5 bg-blue-100 text-blue-700 rounded">{schemaName}</span>}
                            {categoryName && <span className="text-xs text-gray-400">{categoryName}</span>}
                        </div>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X className="size-5" /></button>
                </div>

                {loading ? (
                    <div className="flex items-center justify-center py-8"><Loader2 className="size-6 animate-spin text-gray-300" /></div>
                ) : (
                    <>
                        {/* Schema fields — expected metadata for this category */}
                        {fields.length > 0 && (
                            <div>
                                <div className="flex items-center justify-between mb-2">
                                    <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
                                        Schema Fields
                                    </h4>
                                    <span className="text-[10px] text-gray-400">From: {schemaName}</span>
                                </div>
                                <div className="space-y-2">
                                    {fields.map(field => (
                                        <div key={field.fieldName} className="bg-white border border-gray-200 rounded-md p-3">
                                            <div className="flex items-center justify-between mb-1">
                                                <div className="flex items-center gap-2">
                                                    <span className="text-xs font-semibold text-gray-900">{field.fieldName.replace(/_/g, " ")}</span>
                                                    <span className="text-[10px] text-gray-400">{field.dataType}</span>
                                                    {field.required && <span className="text-[10px] text-red-500">required</span>}
                                                </div>
                                                <button onClick={() => setRemovingField(field.fieldName)}
                                                    className="text-[10px] text-gray-400 hover:text-red-500">Remove</button>
                                            </div>
                                            {field.description && <p className="text-[10px] text-gray-400 mb-1.5">{field.description}</p>}
                                            <input type="text" value={values[field.fieldName] ?? ""}
                                                onChange={e => updateValue(field.fieldName, e.target.value)}
                                                placeholder={field.examples?.[0] ?? ""}
                                                className={`w-full text-sm border rounded-md px-2.5 py-1.5 ${
                                                    values[field.fieldName] === "NOT_FOUND" ? "border-amber-300 bg-amber-50 text-amber-600" : "border-gray-300"
                                                } focus:ring-2 focus:ring-blue-500`} />
                                            {values[field.fieldName] === "NOT_FOUND" && (
                                                <p className="text-[10px] text-amber-500 mt-0.5">AI could not extract — enter manually or leave blank</p>
                                            )}
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Extra fields — metadata the LLM extracted that's not in the schema */}
                        {extraFields.length > 0 && (
                            <div>
                                <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
                                    Additional Extracted Fields
                                </h4>
                                <p className="text-[10px] text-gray-400 mb-2">
                                    Fields extracted by the AI that aren&apos;t in the schema. Keep, remove, or add to the schema for future documents.
                                </p>
                                <div className="space-y-1.5">
                                    {extraFields.map(key => (
                                        <div key={key} className="flex items-center gap-2 bg-amber-50 border border-amber-200 rounded-md p-2">
                                            <span className="text-xs font-medium text-gray-700 w-32 shrink-0 truncate">{key.replace(/_/g, " ")}</span>
                                            <input type="text" value={values[key] ?? ""}
                                                onChange={e => updateValue(key, e.target.value)}
                                                className="flex-1 text-xs border border-gray-300 rounded px-2 py-1" />
                                            <div className="flex gap-1 shrink-0">
                                                {schemaId && (
                                                    <button onClick={() => addToSchema(key)}
                                                        className="text-[10px] text-blue-600 hover:text-blue-800 whitespace-nowrap">+ Schema</button>
                                                )}
                                                <button onClick={() => setRemovingField(key)}
                                                    className="text-gray-400 hover:text-red-500"><X className="size-3.5" /></button>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Add custom field */}
                        <div>
                            {addingField ? (
                                <div className="flex gap-1 items-center">
                                    <input type="text" value={newFieldName}
                                        onChange={e => setNewFieldName(e.target.value)}
                                        onKeyDown={e => { if (e.key === "Enter") confirmAddField(); if (e.key === "Escape") setAddingField(false); }}
                                        placeholder="field_name" autoFocus
                                        className="flex-1 text-xs border border-blue-300 rounded px-2 py-1" />
                                    <button onClick={confirmAddField} className="text-xs text-blue-600 font-medium px-1">Add</button>
                                    <button onClick={() => { setAddingField(false); setNewFieldName(""); }}
                                        className="text-gray-400 hover:text-gray-600"><X className="size-3.5" /></button>
                                </div>
                            ) : (
                                <button onClick={() => setAddingField(true)}
                                    className="text-xs text-blue-600 hover:text-blue-800 font-medium">+ Add custom field</button>
                            )}
                        </div>

                        {/* Actions */}
                        <div className="flex gap-3 justify-end pt-2 border-t border-gray-200">
                            <button onClick={onClose}
                                className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                            <button onClick={handleSave} disabled={saving}
                                className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50">
                                {saving ? "Saving..." : "Save Metadata"}
                            </button>
                        </div>
                    </>
                )}
            </div>

            {removingField && (
                <ScopeActionModal
                    title="Remove Metadata Field"
                    description="Choose where to remove this field from. Removing from the category schema will stop the AI extracting it for future documents."
                    fieldName={removingField}
                    categoryName={categoryName}
                    onConfirm={(scope, reason) => handleScopedRemove(removingField, scope, reason)}
                    onClose={() => setRemovingField(null)}
                />
            )}
        </Overlay>
    );
}

function confidenceColor(c: number) {
    if (c >= 0.9) return "text-green-600";
    if (c >= 0.7) return "text-blue-600";
    if (c >= 0.5) return "text-amber-600";
    return "text-red-600";
}

function confidenceBarColor(c: number) {
    if (c >= 0.9) return "bg-green-500";
    if (c >= 0.7) return "bg-blue-500";
    if (c >= 0.5) return "bg-amber-500";
    return "bg-red-500";
}

/* ── PII Report Modal ─────────────────────────────── */

function PiiReportModal({ documentId, initialText, onClose, onSaved }: {
    documentId: string; initialText?: string; onClose: () => void; onSaved: () => void;
}) {
    const piiTypes = usePiiTypes();
    const [items, setItems] = useState([{
        type: "", description: initialText ?? "", context: ""
    }]);
    const [saving, setSaving] = useState(false);

    const updateItem = (idx: number, field: string, value: string) => {
        setItems((prev) => prev.map((item, i) => i === idx ? { ...item, [field]: value } : item));
    };

    const addItem = () => setItems((prev) => [...prev, { type: "", description: "", context: "" }]);

    const removeItem = (idx: number) => {
        if (items.length === 1) return;
        setItems((prev) => prev.filter((_, i) => i !== idx));
    };

    const handleSave = async () => {
        const valid = items.filter((i) => i.type.trim() && i.description.trim());
        if (valid.length === 0) {
            toast.error("Please add at least one PII item with type and description");
            return;
        }
        setSaving(true);
        try {
            await api.post(`/review/${documentId}/report-pii`, { piiItems: valid });
            onSaved();
        } catch {
            toast.error("Failed to submit PII report");
        } finally {
            setSaving(false);
        }
    };

    return (
        <Overlay onClose={onClose}>
            <div className="p-6 space-y-4">
                <div className="flex items-center justify-between">
                    <div>
                        <h3 className="text-lg font-semibold text-gray-900">Report Missed PII</h3>
                        <p className="text-xs text-gray-500 mt-0.5">Flag personal data the system didn&apos;t detect. This improves future scanning.</p>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X className="size-5" /></button>
                </div>

                <div className="space-y-3 max-h-64 overflow-y-auto">
                    {items.map((item, idx) => (
                        <div key={idx} className="bg-gray-50 rounded-lg p-3 space-y-2">
                            <div className="flex items-center gap-2">
                                <select value={item.type} onChange={(e) => updateItem(idx, "type", e.target.value)}
                                    className="flex-1 text-xs border border-gray-300 rounded-md px-2 py-1.5">
                                    <option value="">PII type...</option>
                                    {piiTypes.map((t) => <option key={t.key} value={t.key}>{t.displayName}</option>)}
                                </select>
                                {items.length > 1 && (
                                    <button onClick={() => removeItem(idx)} className="text-gray-400 hover:text-red-500">
                                        <X className="size-4" />
                                    </button>
                                )}
                            </div>
                            <input type="text" placeholder="What is the PII? (e.g. 'Employee reference numbers EMP-XXXX')"
                                value={item.description} onChange={(e) => updateItem(idx, "description", e.target.value)}
                                className="w-full text-xs border border-gray-300 rounded-md px-2 py-1.5" />
                            <input type="text" placeholder="Where in the document? (e.g. 'Table on page 2')"
                                value={item.context} onChange={(e) => updateItem(idx, "context", e.target.value)}
                                className="w-full text-xs border border-gray-300 rounded-md px-2 py-1.5" />
                        </div>
                    ))}
                </div>

                <button onClick={addItem} className="text-xs text-blue-600 hover:text-blue-800 font-medium">+ Add another PII item</button>

                <div className="flex gap-2 justify-end pt-2">
                    <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                    <button onClick={handleSave} disabled={saving}
                        className="px-4 py-2 text-sm font-medium text-white bg-purple-600 rounded-md hover:bg-purple-700 disabled:opacity-50">
                        {saving ? "Submitting..." : "Submit PII Report"}
                    </button>
                </div>
            </div>
        </Overlay>
    );
}
