"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Check, Loader2, RefreshCw, Tag, Trash2, AlertTriangle } from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";

type LabelFieldInfo = { id: string; displayName: string; type: string };
type LabelInfo = { id: string; name: string; fields: LabelFieldInfo[] };

const GLS_FIELDS = [
    { key: "category", label: "Category", description: "Classification category name" },
    { key: "sensitivity", label: "Sensitivity", description: "PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED" },
    { key: "retention_until", label: "Retention Until", description: "Retention expiry date" },
    { key: "vital_record", label: "Vital Record", description: "Yes / No" },
    { key: "legal_hold", label: "Legal Hold", description: "Yes / No" },
];

export default function DriveLabelsPage() {
    const params = useParams();
    const router = useRouter();
    const driveId = params.id as string;

    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [labels, setLabels] = useState<LabelInfo[]>([]);
    const [selectedLabelId, setSelectedLabelId] = useState("");
    const [selectedLabelName, setSelectedLabelName] = useState("");
    const [fieldMappings, setFieldMappings] = useState<Record<string, string>>({});
    const [needsLabelScope, setNeedsLabelScope] = useState(false);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const { data } = await api.get(`/drives/${driveId}/labels`);
            setLabels(data.labels || []);
            setSelectedLabelId(data.currentLabelId || "");
            setSelectedLabelName(data.currentLabelName || "");
            setFieldMappings(data.fieldMappings || {});
            setNeedsLabelScope(data.needsLabelScope || false);
        } catch {
            toast.error("Failed to load label configuration");
        } finally {
            setLoading(false);
        }
    }, [driveId]);

    useEffect(() => { load(); }, [load]);

    const selectedLabel = labels.find(l => l.id === selectedLabelId);

    const handleSelectLabel = (label: LabelInfo) => {
        setSelectedLabelId(label.id);
        setSelectedLabelName(label.name);
        // Reset field mappings when label changes
        setFieldMappings({});
    };

    const handleSave = async () => {
        if (!selectedLabelId) return;
        setSaving(true);
        try {
            await api.put(`/drives/${driveId}/label-config`, {
                labelId: selectedLabelId,
                labelName: selectedLabelName,
                fieldMappings,
            });
            toast.success("Label configuration saved");
        } catch {
            toast.error("Failed to save label configuration");
        } finally {
            setSaving(false);
        }
    };

    const handleClear = async () => {
        setSaving(true);
        try {
            await api.delete(`/drives/${driveId}/label-config`);
            setSelectedLabelId("");
            setSelectedLabelName("");
            setFieldMappings({});
            toast.success("Label configuration cleared");
        } catch {
            toast.error("Failed to clear label configuration");
        } finally {
            setSaving(false);
        }
    };

    const handleRelabel = async () => {
        try {
            const { data } = await api.post(`/drives/${driveId}/relabel`);
            toast.success(`Relabelling started: ${data.documentCount} documents queued`);
        } catch {
            toast.error("Failed to start relabelling");
        }
    };

    return (
        <div className="max-w-3xl mx-auto">
            {/* Header */}
            <div className="flex items-center gap-3 mb-6">
                <button onClick={() => router.push("/drives")}
                    className="p-1.5 text-gray-400 hover:text-gray-600 rounded-md hover:bg-gray-100">
                    <ArrowLeft className="size-5" />
                </button>
                <div>
                    <h2 className="text-xl font-bold text-gray-900">Drive Label Configuration</h2>
                    <p className="text-sm text-gray-500 mt-0.5">
                        Configure a Google Workspace label to surface classification metadata in the Drive UI.
                    </p>
                </div>
            </div>

            {/* Scope warning */}
            {needsLabelScope && (
                <div className="mb-4 p-3 bg-orange-50 border border-orange-200 rounded-lg flex items-start gap-2">
                    <AlertTriangle className="size-4 text-orange-500 mt-0.5 shrink-0" />
                    <div className="text-sm text-orange-700">
                        <p className="font-medium">Reconnection required</p>
                        <p className="mt-0.5">This drive needs to be reconnected to grant the <code className="text-xs bg-orange-100 px-1 rounded">drive.labels</code> scope. Disconnect and reconnect from the Drives page.</p>
                    </div>
                </div>
            )}

            {loading ? (
                <div className="flex items-center justify-center py-16">
                    <Loader2 className="size-8 animate-spin text-gray-300" />
                </div>
            ) : (
                <div className="space-y-6">
                    {/* Label selector */}
                    <div className="bg-white rounded-lg border border-gray-200 p-5">
                        <h3 className="text-sm font-semibold text-gray-900 mb-3">Workspace Label</h3>

                        {labels.length === 0 ? (
                            <div className="text-sm text-gray-500 py-4 text-center">
                                <Tag className="size-8 text-gray-300 mx-auto mb-2" />
                                <p>No labels found in this Google Workspace.</p>
                                <p className="mt-1 text-xs">A Workspace admin needs to create a label in Google Admin &rarr; Drive &rarr; Labels first.</p>
                            </div>
                        ) : (
                            <div className="space-y-2">
                                {labels.map(label => (
                                    <button key={label.id}
                                        onClick={() => handleSelectLabel(label)}
                                        className={`w-full text-left p-3 rounded-lg border transition-colors ${
                                            selectedLabelId === label.id
                                                ? "border-blue-300 bg-blue-50"
                                                : "border-gray-200 hover:border-gray-300 hover:bg-gray-50"
                                        }`}>
                                        <div className="flex items-center justify-between">
                                            <div>
                                                <span className="text-sm font-medium text-gray-900">{label.name}</span>
                                                <span className="text-xs text-gray-400 ml-2">{label.fields.length} field(s)</span>
                                            </div>
                                            {selectedLabelId === label.id && <Check className="size-4 text-blue-600" />}
                                        </div>
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* Field mapping */}
                    {selectedLabel && (
                        <div className="bg-white rounded-lg border border-gray-200 p-5">
                            <h3 className="text-sm font-semibold text-gray-900 mb-1">Field Mapping</h3>
                            <p className="text-xs text-gray-500 mb-4">
                                Map each GLS classification field to a field in the &ldquo;{selectedLabel.name}&rdquo; label.
                            </p>

                            <div className="space-y-3">
                                {GLS_FIELDS.map(glsField => (
                                    <div key={glsField.key} className="flex items-center gap-3">
                                        <div className="w-40 shrink-0">
                                            <div className="text-sm font-medium text-gray-700">{glsField.label}</div>
                                            <div className="text-[10px] text-gray-400">{glsField.description}</div>
                                        </div>
                                        <span className="text-gray-300">&rarr;</span>
                                        <select
                                            value={fieldMappings[glsField.key] || ""}
                                            onChange={e => setFieldMappings(prev => ({ ...prev, [glsField.key]: e.target.value }))}
                                            className="flex-1 text-sm border border-gray-300 rounded-md px-3 py-1.5 bg-white">
                                            <option value="">-- Not mapped --</option>
                                            {selectedLabel.fields.map(f => (
                                                <option key={f.id} value={f.id}>
                                                    {f.displayName} ({f.type})
                                                </option>
                                            ))}
                                        </select>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Actions */}
                    <div className="flex items-center justify-between">
                        <div className="flex gap-2">
                            {selectedLabelId && (
                                <>
                                    <button onClick={handleClear} disabled={saving}
                                        className="inline-flex items-center gap-2 px-3 py-2 text-sm text-red-600 border border-red-200 rounded-lg hover:bg-red-50 disabled:opacity-50">
                                        <Trash2 className="size-4" /> Remove Label
                                    </button>
                                    <button onClick={handleRelabel}
                                        className="inline-flex items-center gap-2 px-3 py-2 text-sm text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50">
                                        <RefreshCw className="size-4" /> Relabel Existing Docs
                                    </button>
                                </>
                            )}
                        </div>
                        <button onClick={handleSave} disabled={saving || !selectedLabelId}
                            className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50">
                            {saving ? <Loader2 className="size-4 animate-spin" /> : <Check className="size-4" />}
                            Save Configuration
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}
