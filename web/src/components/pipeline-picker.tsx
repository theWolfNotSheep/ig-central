"use client";

import { usePipelines } from "@/hooks/use-pipelines";

interface PipelinePickerProps {
    value: string;
    onChange: (pipelineId: string) => void;
    className?: string;
}

export function PipelinePicker({ value, onChange, className }: PipelinePickerProps) {
    const { pipelines, loading } = usePipelines();

    if (loading || pipelines.length <= 1) return null;

    return (
        <select
            value={value}
            onChange={(e) => onChange(e.target.value)}
            className={className ?? "text-xs border border-gray-300 rounded-md px-2 py-1.5 bg-white text-gray-700"}
        >
            <option value="">Auto-detect</option>
            {pipelines.map((p) => (
                <option key={p.id} value={p.id}>
                    {p.name}{p.isDefault ? " (default)" : ""}
                </option>
            ))}
        </select>
    );
}
