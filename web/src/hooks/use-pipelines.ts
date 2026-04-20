"use client";

import { useEffect, useState } from "react";
import api from "@/lib/axios/axios.client";

export type PipelineOption = {
    id: string;
    name: string;
    description: string;
    isDefault: boolean;
};

export function usePipelines() {
    const [pipelines, setPipelines] = useState<PipelineOption[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        api.get<PipelineOption[]>("/pipelines/available")
            .then(({ data }) => setPipelines(data))
            .catch(() => {})
            .finally(() => setLoading(false));
    }, []);

    return { pipelines, loading };
}
