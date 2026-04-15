"use client";

import { useEffect, useState } from "react";
import { Loader2, FlaskConical } from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import FormModal, { FormField } from "@/components/form-modal";

type Schema = {
    id: string;
    name: string;
    categoryIds: string[];
    fields: { fieldName: string; fieldType: string; required: boolean }[];
};

type ExtractionResult = {
    fieldName: string;
    value: string | null;
    status: "FOUND" | "MISSING" | "NOT_EXTRACTED";
};

export default function SchemaTestModal({ documentId, onClose }: {
    documentId: string;
    onClose: () => void;
}) {
    const [schemas, setSchemas] = useState<Schema[]>([]);
    const [loading, setLoading] = useState(true);
    const [selectedSchemaId, setSelectedSchemaId] = useState("");
    const [testing, setTesting] = useState(false);
    const [results, setResults] = useState<ExtractionResult[] | null>(null);

    useEffect(() => {
        api.get("/admin/governance/metadata-schemas")
            .then(({ data }) => {
                const list = Array.isArray(data) ? data : data.content ?? [];
                setSchemas(list);
                if (list.length > 0) setSelectedSchemaId(list[0].id);
            })
            .catch(() => toast.error("Failed to load schemas"))
            .finally(() => setLoading(false));
    }, []);

    const handleTest = async () => {
        if (!selectedSchemaId) return;
        setTesting(true);
        setResults(null);
        try {
            const { data } = await api.post("/admin/governance/metadata-schemas/test", {
                documentId,
                schemaId: selectedSchemaId,
            });
            setResults(Array.isArray(data) ? data : data.results ?? []);
        } catch {
            toast.error("Schema test failed");
        } finally {
            setTesting(false);
        }
    };

    const statusIcon = (status: string) => {
        if (status === "FOUND") return <span className="size-2 rounded-full bg-green-500 inline-block" />;
        if (status === "MISSING") return <span className="size-2 rounded-full bg-amber-500 inline-block" />;
        return <span className="size-2 rounded-full bg-gray-300 inline-block" />;
    };

    return (
        <FormModal
            title="Test Metadata Schema"
            open
            onClose={onClose}
            width="lg"
            footer={
                <>
                    <button
                        onClick={onClose}
                        className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50"
                    >
                        Close
                    </button>
                    <button
                        onClick={handleTest}
                        disabled={testing || !selectedSchemaId || loading}
                        className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50 inline-flex items-center gap-2"
                    >
                        {testing ? <Loader2 className="size-4 animate-spin" /> : <FlaskConical className="size-4" />}
                        {testing ? "Extracting..." : "Test Extraction"}
                    </button>
                </>
            }
        >
            {loading ? (
                <div className="flex items-center justify-center py-8">
                    <Loader2 className="size-5 animate-spin text-gray-400" />
                </div>
            ) : schemas.length === 0 ? (
                <p className="text-sm text-gray-500 text-center py-8">No metadata schemas configured yet.</p>
            ) : (
                <div className="space-y-4">
                    <FormField label="Schema" id="schema-select">
                        <select
                            id="schema-select"
                            value={selectedSchemaId}
                            onChange={(e) => { setSelectedSchemaId(e.target.value); setResults(null); }}
                            className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        >
                            {schemas.map((s) => (
                                <option key={s.id} value={s.id}>
                                    {s.name} ({s.fields?.length ?? 0} fields)
                                </option>
                            ))}
                        </select>
                    </FormField>

                    {results && (
                        <div className="border border-gray-200 rounded-lg overflow-hidden">
                            <div className="bg-gray-50 px-3 py-2 border-b border-gray-200">
                                <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Extraction Results</span>
                            </div>
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="bg-gray-50 text-left">
                                        <th className="px-3 py-2 text-xs font-medium text-gray-500">Status</th>
                                        <th className="px-3 py-2 text-xs font-medium text-gray-500">Field</th>
                                        <th className="px-3 py-2 text-xs font-medium text-gray-500">Value</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {results.map((r) => (
                                        <tr key={r.fieldName}>
                                            <td className="px-3 py-2">{statusIcon(r.status)}</td>
                                            <td className="px-3 py-2 text-xs text-gray-700 font-medium">{r.fieldName.replace(/_/g, " ")}</td>
                                            <td className={`px-3 py-2 text-xs ${
                                                r.status === "FOUND" ? "text-gray-900" :
                                                r.status === "MISSING" ? "text-amber-500 italic" : "text-gray-400 italic"
                                            }`}>
                                                {r.value ?? (r.status === "MISSING" ? "NOT_FOUND" : "Not extracted")}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            )}
        </FormModal>
    );
}
