"use client";

import { useState } from "react";
import {
    Plus, Trash2, GripVertical, ChevronDown, ChevronRight,
    HelpCircle, Info, Code, Eye,
} from "lucide-react";

type BlockContent = Record<string, unknown>;

interface BlockFormEditorProps {
    type: string;
    content: BlockContent;
    onChange: (content: BlockContent) => void;
    readOnly?: boolean;
}

// ── Pattern type for REGEX_SET ────────────────────────
type PatternEntry = {
    name: string;
    type: string;
    regex: string;
    confidence: number;
    flags?: string;
};

export default function BlockFormEditor({ type, content, onChange, readOnly }: BlockFormEditorProps) {
    const [rawMode, setRawMode] = useState(false);
    const [rawJson, setRawJson] = useState(JSON.stringify(content, null, 2));
    const [jsonError, setJsonError] = useState("");

    const toggleRaw = () => {
        if (rawMode) {
            // Switching back to form — try to parse
            try {
                const parsed = JSON.parse(rawJson);
                onChange(parsed);
                setJsonError("");
                setRawMode(false);
            } catch {
                setJsonError("Invalid JSON — fix before switching to form view");
            }
        } else {
            // Switching to raw — sync
            setRawJson(JSON.stringify(content, null, 2));
            setRawMode(true);
        }
    };

    return (
        <div className="space-y-3">
            {/* Mode toggle — hidden in readOnly */}
            {!readOnly && (
                <div className="flex justify-end">
                    <button onClick={toggleRaw}
                        className="inline-flex items-center gap-1.5 px-2 py-1 text-[10px] font-medium text-gray-500 hover:text-gray-700 border border-gray-200 rounded-md hover:bg-gray-50">
                        {rawMode ? <><Eye className="size-3" /> Form View</> : <><Code className="size-3" /> Raw JSON</>}
                    </button>
                </div>
            )}

            {rawMode && !readOnly ? (
                <div>
                    <textarea
                        id="block-raw-json"
                        aria-label="Raw JSON editor"
                        value={rawJson}
                        onChange={e => { setRawJson(e.target.value); setJsonError(""); }}
                        rows={16}
                        className="w-full text-xs font-mono border border-gray-300 rounded-md px-3 py-2 leading-relaxed focus:ring-2 focus:ring-blue-500"
                    />
                    {jsonError && <p className="text-[10px] text-red-500 mt-1">{jsonError}</p>}
                </div>
            ) : (
                <div>
                    {type === "PROMPT" && <PromptForm content={content} onChange={onChange} readOnly={readOnly} />}
                    {type === "REGEX_SET" && <RegexSetForm content={content} onChange={onChange} readOnly={readOnly} />}
                    {type === "EXTRACTOR" && <ExtractorForm content={content} onChange={onChange} readOnly={readOnly} />}
                    {type === "ROUTER" && <RouterForm content={content} onChange={onChange} readOnly={readOnly} />}
                    {type === "ENFORCER" && <EnforcerForm content={content} onChange={onChange} readOnly={readOnly} />}
                </div>
            )}
        </div>
    );
}

// ── PROMPT Form ───────────────────────────────────────
function PromptForm({ content, onChange, readOnly }: { content: BlockContent; onChange: (c: BlockContent) => void; readOnly?: boolean }) {
    const [showGuide, setShowGuide] = useState(false);

    const update = (key: string, value: unknown) => onChange({ ...content, [key]: value });

    return (
        <div className="space-y-4">
            {/* Guide — hidden in readOnly */}
            {!readOnly && (
                <button onClick={() => setShowGuide(!showGuide)}
                    className="flex items-center gap-1.5 text-[11px] text-blue-600 hover:text-blue-800">
                    <HelpCircle className="size-3.5" />
                    {showGuide ? "Hide prompt guide" : "How do prompts work?"}
                </button>
            )}

            {showGuide && !readOnly && (
                <div className="bg-white border border-blue-200 rounded-lg p-4 space-y-3 text-xs text-gray-600">
                    <div>
                        <h5 className="font-semibold text-gray-900 mb-1 flex items-center gap-1.5">
                            <Info className="size-3.5 text-blue-500" /> How prompts work
                        </h5>
                        <p>Each LLM step sends two prompts to Claude. Together they instruct the AI on what to do with each document.</p>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                        <div className="bg-purple-50 rounded-md p-3 border border-purple-100">
                            <h6 className="font-semibold text-purple-800 mb-1">System Prompt</h6>
                            <p className="text-purple-700">Defines the AI&apos;s <strong>role, expertise, and rules</strong>. Sets the context for all processing.</p>
                        </div>
                        <div className="bg-blue-50 rounded-md p-3 border border-blue-100">
                            <h6 className="font-semibold text-blue-800 mb-1">User Prompt Template</h6>
                            <p className="text-blue-700">The <strong>per-document instruction</strong> with placeholders replaced at runtime.</p>
                        </div>
                    </div>
                    <div>
                        <h5 className="font-semibold text-gray-900 mb-1">Available placeholders</h5>
                        <div className="grid grid-cols-2 gap-x-4 gap-y-1 bg-gray-50 rounded-md p-2 font-mono text-[10px]">
                            <div><code className="text-blue-700">{"{documentId}"}</code> <span className="text-gray-400">— unique document ID</span></div>
                            <div><code className="text-blue-700">{"{fileName}"}</code> <span className="text-gray-400">— original file name</span></div>
                            <div><code className="text-blue-700">{"{mimeType}"}</code> <span className="text-gray-400">— file type</span></div>
                            <div><code className="text-blue-700">{"{fileSizeBytes}"}</code> <span className="text-gray-400">— file size in bytes</span></div>
                            <div><code className="text-blue-700">{"{uploadedBy}"}</code> <span className="text-gray-400">— who uploaded the file</span></div>
                            <div><code className="text-blue-700">{"{extractedText}"}</code> <span className="text-gray-400">— full document text</span></div>
                            <div><code className="text-blue-700">{"{piiFindings}"}</code> <span className="text-gray-400">— PII entities from scan</span></div>
                        </div>
                    </div>
                </div>
            )}

            {/* System Prompt */}
            <div>
                <label htmlFor="prompt-system" className="text-xs font-medium text-gray-700 block mb-1">
                    System Prompt
                    <span className="text-gray-400 font-normal ml-1">— the AI&apos;s role and instructions</span>
                </label>
                <textarea
                    id="prompt-system"
                    value={String(content.systemPrompt ?? "")}
                    onChange={e => update("systemPrompt", e.target.value)}
                    readOnly={readOnly}
                    placeholder="You are a document classification specialist..."
                    rows={readOnly ? 4 : 8}
                    className={`w-full text-xs border border-gray-300 rounded-md px-3 py-2 leading-relaxed ${readOnly ? "bg-gray-50 cursor-default" : "focus:ring-2 focus:ring-blue-500"}`}
                />
            </div>

            {/* User Prompt Template */}
            <div>
                <label htmlFor="prompt-user-template" className="text-xs font-medium text-gray-700 block mb-1">
                    User Prompt Template
                    <span className="text-gray-400 font-normal ml-1">— sent per document, {"{placeholders}"} replaced</span>
                </label>
                <textarea
                    id="prompt-user-template"
                    value={String(content.userPromptTemplate ?? "")}
                    onChange={e => update("userPromptTemplate", e.target.value)}
                    readOnly={readOnly}
                    placeholder={"Please classify the following document.\n\n**Document ID:** {documentId}\n**File name:** {fileName}"}
                    rows={readOnly ? 4 : 6}
                    className={`w-full text-xs border border-gray-300 rounded-md px-3 py-2 leading-relaxed ${readOnly ? "bg-gray-50 cursor-default" : "focus:ring-2 focus:ring-blue-500"}`}
                />
            </div>

            <p className="text-[10px] text-gray-400">Model, max tokens, and temperature are configured per pipeline — not per prompt block.</p>
        </div>
    );
}

// ── REGEX_SET Form ────────────────────────────────────
function RegexSetForm({ content, onChange, readOnly }: { content: BlockContent; onChange: (c: BlockContent) => void; readOnly?: boolean }) {
    const patterns: PatternEntry[] = Array.isArray(content.patterns) ? (content.patterns as PatternEntry[]) : [];
    const [expandedIdx, setExpandedIdx] = useState<number | null>(readOnly && patterns.length <= 5 ? 0 : null);

    const updatePatterns = (updated: PatternEntry[]) => onChange({ ...content, patterns: updated });

    const addPattern = () => {
        updatePatterns([...patterns, { name: "", type: "", regex: "", confidence: 0.8 }]);
        setExpandedIdx(patterns.length);
    };

    const removePattern = (idx: number) => {
        updatePatterns(patterns.filter((_, i) => i !== idx));
        if (expandedIdx === idx) setExpandedIdx(null);
    };

    const updatePattern = (idx: number, key: keyof PatternEntry, value: unknown) => {
        const updated = [...patterns];
        updated[idx] = { ...updated[idx], [key]: value };
        updatePatterns(updated);
    };

    return (
        <div className="space-y-3">
            <div className="flex items-center justify-between">
                <div>
                    <label className="text-xs font-medium text-gray-700">PII Detection Patterns</label>
                    <p className="text-[10px] text-gray-400 mt-0.5">Regex patterns matched against document text to detect personally identifiable information</p>
                </div>
                {!readOnly && (
                    <button onClick={addPattern}
                        className="inline-flex items-center gap-1 px-2 py-1 text-[10px] font-medium text-blue-600 hover:text-blue-800 border border-blue-200 rounded hover:bg-blue-50">
                        <Plus className="size-3" /> Add Pattern
                    </button>
                )}
            </div>

            <div className="space-y-2">
                {patterns.map((p, i) => (
                    <div key={i} className="border border-gray-200 rounded-lg overflow-hidden">
                        {/* Header — always visible */}
                        <button
                            onClick={() => setExpandedIdx(expandedIdx === i ? null : i)}
                            className="w-full flex items-center gap-2 px-3 py-2 bg-gray-50 hover:bg-gray-100 text-left">
                            {expandedIdx === i ? <ChevronDown className="size-3 text-gray-400" /> : <ChevronRight className="size-3 text-gray-400" />}
                            <span className="text-xs font-medium text-gray-800 flex-1">{p.name || "Untitled pattern"}</span>
                            <span className="text-[10px] text-gray-400 font-mono">{p.type || "—"}</span>
                            <span className={`px-1.5 py-0.5 text-[10px] rounded font-medium ${
                                p.confidence >= 0.9 ? "bg-green-100 text-green-700" :
                                p.confidence >= 0.7 ? "bg-amber-100 text-amber-700" :
                                "bg-red-100 text-red-700"
                            }`}>
                                {Math.round(p.confidence * 100)}%
                            </span>
                            <button onClick={e => { e.stopPropagation(); removePattern(i); }}
                                aria-label={`Delete pattern ${p.name || i}`}
                                className="p-0.5 text-red-400 hover:text-red-600">
                                <Trash2 className="size-3" />
                            </button>
                        </button>

                        {/* Expanded detail */}
                        {expandedIdx === i && (
                            <div className="px-3 py-3 space-y-2 border-t border-gray-100">
                                <div className="grid grid-cols-2 gap-2">
                                    <div>
                                        <label htmlFor={`regex-pattern-${i}-name`} className="text-[10px] font-medium text-gray-500 uppercase block mb-0.5">Display Name</label>
                                        <input id={`regex-pattern-${i}-name`} value={p.name} onChange={e => updatePattern(i, "name", e.target.value)}
                                            placeholder="e.g. UK National Insurance"
                                            className="w-full text-xs border border-gray-300 rounded px-2 py-1.5" />
                                    </div>
                                    <div>
                                        <label htmlFor={`regex-pattern-${i}-type`} className="text-[10px] font-medium text-gray-500 uppercase block mb-0.5">PII Type Key</label>
                                        <input id={`regex-pattern-${i}-type`} value={p.type} onChange={e => updatePattern(i, "type", e.target.value)}
                                            placeholder="e.g. NATIONAL_INSURANCE"
                                            className="w-full text-xs border border-gray-300 rounded px-2 py-1.5 font-mono" />
                                    </div>
                                </div>
                                <div>
                                    <label htmlFor={`regex-pattern-${i}-regex`} className="text-[10px] font-medium text-gray-500 uppercase block mb-0.5">Regex Pattern</label>
                                    <input id={`regex-pattern-${i}-regex`} value={p.regex} onChange={e => updatePattern(i, "regex", e.target.value)}
                                        placeholder="\\b[A-Z]{2}\\d{6}[A-Z]\\b"
                                        className="w-full text-xs font-mono border border-gray-300 rounded px-2 py-1.5" />
                                </div>
                                <div className="grid grid-cols-2 gap-2">
                                    <div>
                                        <label htmlFor={`regex-pattern-${i}-confidence`} className="text-[10px] font-medium text-gray-500 uppercase block mb-0.5">Confidence ({Math.round(p.confidence * 100)}%)</label>
                                        <input id={`regex-pattern-${i}-confidence`} type="range" min="0" max="1" step="0.05"
                                            value={p.confidence}
                                            onChange={e => updatePattern(i, "confidence", parseFloat(e.target.value))}
                                            className="w-full accent-blue-600" />
                                    </div>
                                    <div>
                                        <label htmlFor={`regex-pattern-${i}-flags`} className="text-[10px] font-medium text-gray-500 uppercase block mb-0.5">Flags</label>
                                        <select id={`regex-pattern-${i}-flags`} value={p.flags ?? ""} onChange={e => updatePattern(i, "flags", e.target.value || undefined)}
                                            className="w-full text-xs border border-gray-300 rounded px-2 py-1.5">
                                            <option value="">None</option>
                                            <option value="CASE_INSENSITIVE">Case insensitive</option>
                                            <option value="MULTILINE">Multiline</option>
                                        </select>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                ))}

                {patterns.length === 0 && (
                    <div className="text-center py-6 text-xs text-gray-400 border border-dashed border-gray-200 rounded-lg">
                        No patterns defined. Click &quot;Add Pattern&quot; to create one.
                    </div>
                )}
            </div>
        </div>
    );
}

// ── EXTRACTOR Form ────────────────────────────────────
function ExtractorForm({ content, onChange, readOnly }: { content: BlockContent; onChange: (c: BlockContent) => void; readOnly?: boolean }) {
    const update = (key: string, value: unknown) => onChange({ ...content, [key]: value });

    return (
        <div className="space-y-4">
            <div>
                <label htmlFor="extractor-engine" className="text-xs font-medium text-gray-700 block mb-1">Extraction Engine</label>
                <p className="text-[10px] text-gray-400 mb-1.5">The text extraction engine used to pull content from uploaded documents</p>
                <select
                    id="extractor-engine"
                    value={String(content.extractorType ?? "TIKA")}
                    onChange={e => update("extractorType", e.target.value)}
                    className="w-full text-xs border border-gray-300 rounded-md px-3 py-2">
                    <option value="TIKA">Apache Tika (recommended)</option>
                    <option value="TESSERACT">Tesseract OCR</option>
                    <option value="CUSTOM">Custom</option>
                </select>
            </div>

            <div>
                <label htmlFor="extractor-max-length" className="text-xs font-medium text-gray-700 block mb-1">Max Text Length</label>
                <p className="text-[10px] text-gray-400 mb-1.5">Maximum number of characters to extract. Longer documents are truncated.</p>
                <input
                    id="extractor-max-length"
                    type="number"
                    value={Number(content.maxTextLength ?? 500000)}
                    onChange={e => update("maxTextLength", parseInt(e.target.value) || 500000)}
                    className="w-full text-xs border border-gray-300 rounded-md px-3 py-2"
                />
            </div>

            <div className="space-y-2">
                <label className="text-xs font-medium text-gray-700 block">Extraction Options</label>
                <label htmlFor="extractor-dublin-core" className="flex items-start gap-2 p-2.5 border border-gray-200 rounded-lg hover:bg-gray-50 cursor-pointer">
                    <input
                        id="extractor-dublin-core"
                        type="checkbox"
                        checked={Boolean(content.extractDublinCore ?? true)}
                        onChange={e => update("extractDublinCore", e.target.checked)}
                        className="rounded border-gray-300 text-blue-600 mt-0.5"
                    />
                    <div>
                        <span className="text-xs font-medium text-gray-800">Extract Dublin Core metadata</span>
                        <p className="text-[10px] text-gray-400 mt-0.5">Reads standard metadata fields like title, author, date, subject from the document</p>
                    </div>
                </label>
                <label htmlFor="extractor-file-metadata" className="flex items-start gap-2 p-2.5 border border-gray-200 rounded-lg hover:bg-gray-50 cursor-pointer">
                    <input
                        id="extractor-file-metadata"
                        type="checkbox"
                        checked={Boolean(content.extractMetadata ?? true)}
                        onChange={e => update("extractMetadata", e.target.checked)}
                        className="rounded border-gray-300 text-blue-600 mt-0.5"
                    />
                    <div>
                        <span className="text-xs font-medium text-gray-800">Extract file metadata</span>
                        <p className="text-[10px] text-gray-400 mt-0.5">Reads file-level metadata like creation date, page count, word count</p>
                    </div>
                </label>
            </div>
        </div>
    );
}

// ── ROUTER Form ───────────────────────────────────────
function RouterForm({ content, onChange, readOnly }: { content: BlockContent; onChange: (c: BlockContent) => void; readOnly?: boolean }) {
    const update = (key: string, value: unknown) => onChange({ ...content, [key]: value });

    return (
        <div className="space-y-4">
            <div className="bg-amber-50 border border-amber-200 rounded-lg p-3">
                <p className="text-xs text-amber-800">
                    <strong>Routers</strong> evaluate a condition and send documents down one of two paths.
                    For example, route low-confidence classifications to human review.
                </p>
            </div>

            <div>
                <label htmlFor="router-condition" className="text-xs font-medium text-gray-700 block mb-1">Condition Field</label>
                <p className="text-[10px] text-gray-400 mb-1.5">The document property to evaluate</p>
                <select
                    id="router-condition"
                    value={String(content.condition ?? "confidence")}
                    onChange={e => update("condition", e.target.value)}
                    className="w-full text-xs border border-gray-300 rounded-md px-3 py-2">
                    <option value="confidence">Classification Confidence</option>
                    <option value="piiCount">PII Entity Count</option>
                    <option value="sensitivityLevel">Sensitivity Level</option>
                    <option value="fileSize">File Size</option>
                </select>
            </div>

            <div className="grid grid-cols-2 gap-3">
                <div>
                    <label htmlFor="router-operator" className="text-xs font-medium text-gray-700 block mb-1">Operator</label>
                    <select
                        id="router-operator"
                        value={String(content.operator ?? "LESS_THAN")}
                        onChange={e => update("operator", e.target.value)}
                        className="w-full text-xs border border-gray-300 rounded-md px-3 py-2">
                        <option value="LESS_THAN">Less than</option>
                        <option value="GREATER_THAN">Greater than</option>
                        <option value="EQUALS">Equals</option>
                        <option value="NOT_EQUALS">Not equals</option>
                    </select>
                </div>
                <div>
                    <label htmlFor="router-threshold" className="text-xs font-medium text-gray-700 block mb-1">Threshold</label>
                    <input
                        id="router-threshold"
                        type="number"
                        step="0.1"
                        value={Number(content.threshold ?? 0.7)}
                        onChange={e => update("threshold", parseFloat(e.target.value))}
                        className="w-full text-xs border border-gray-300 rounded-md px-3 py-2"
                    />
                </div>
            </div>

            {/* Visual routing diagram */}
            <div className="border border-gray-200 rounded-lg p-4">
                <p className="text-[10px] font-medium text-gray-500 uppercase mb-3">Routing Paths</p>
                <div className="flex items-center gap-3">
                    <div className="flex-1 bg-green-50 border border-green-200 rounded-lg p-3 text-center">
                        <label htmlFor="router-true-output" className="text-[10px] text-green-600 font-medium mb-1 block">If TRUE</label>
                        <input
                            id="router-true-output"
                            value={String(content.trueOutput ?? "human_review")}
                            onChange={e => update("trueOutput", e.target.value)}
                            className="w-full text-xs text-center border border-green-200 rounded px-2 py-1.5 bg-white"
                            placeholder="e.g. human_review"
                        />
                    </div>
                    <div className="text-gray-300 text-lg font-light">/</div>
                    <div className="flex-1 bg-blue-50 border border-blue-200 rounded-lg p-3 text-center">
                        <label htmlFor="router-false-output" className="text-[10px] text-blue-600 font-medium mb-1 block">If FALSE</label>
                        <input
                            id="router-false-output"
                            value={String(content.falseOutput ?? "auto_approve")}
                            onChange={e => update("falseOutput", e.target.value)}
                            className="w-full text-xs text-center border border-blue-200 rounded px-2 py-1.5 bg-white"
                            placeholder="e.g. auto_approve"
                        />
                    </div>
                </div>
            </div>
        </div>
    );
}

// ── ENFORCER Form ─────────────────────────────────────
function EnforcerForm({ content, onChange, readOnly }: { content: BlockContent; onChange: (c: BlockContent) => void; readOnly?: boolean }) {
    const update = (key: string, value: unknown) => onChange({ ...content, [key]: value });

    const toggles: { key: string; label: string; description: string }[] = [
        { key: "applyRetention", label: "Apply retention schedules", description: "Automatically set retention periods based on the document's category and governance policy" },
        { key: "migrateStorageTier", label: "Migrate storage tier", description: "Move documents to the appropriate storage tier (hot/warm/cold) based on their classification" },
        { key: "enforcePolicies", label: "Enforce governance policies", description: "Apply access controls, sensitivity labels, and compliance rules from matched policies" },
        { key: "autoArchiveOnExpiry", label: "Auto-archive on expiry", description: "Automatically archive documents when their retention period expires" },
    ];

    return (
        <div className="space-y-3">
            <div className="bg-green-50 border border-green-200 rounded-lg p-3">
                <p className="text-xs text-green-800">
                    <strong>Enforcers</strong> apply governance rules after classification — retention schedules,
                    storage tiers, access controls, and policy compliance.
                </p>
            </div>

            <div className="space-y-2">
                {toggles.map(t => (
                    <label key={t.key} htmlFor={`enforcer-${t.key}`} className="flex items-start gap-3 p-3 border border-gray-200 rounded-lg hover:bg-gray-50 cursor-pointer">
                        <input
                            id={`enforcer-${t.key}`}
                            type="checkbox"
                            checked={Boolean(content[t.key] ?? false)}
                            onChange={e => update(t.key, e.target.checked)}
                            className="rounded border-gray-300 text-green-600 mt-0.5"
                        />
                        <div>
                            <span className="text-xs font-medium text-gray-800">{t.label}</span>
                            <p className="text-[10px] text-gray-400 mt-0.5">{t.description}</p>
                        </div>
                    </label>
                ))}
            </div>
        </div>
    );
}
