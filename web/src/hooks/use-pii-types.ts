"use client";

import { useEffect, useState } from "react";
import api from "@/lib/axios/axios.client";

export type PiiTypeDef = {
    id: string;
    key: string;
    displayName: string;
    description: string;
    category: string;
    active: boolean;
    examples: string[];
    approvalStatus: "APPROVED" | "PENDING" | "REJECTED";
};

/**
 * Fetches active, approved PII type definitions from the config.
 * Falls back to the documents endpoint (non-admin) first,
 * then tries admin endpoint.
 */
export function usePiiTypes() {
    const [piiTypes, setPiiTypes] = useState<PiiTypeDef[]>([]);

    useEffect(() => {
        api.get("/documents/pii-types")
            .then(({ data }) => setPiiTypes(data))
            .catch(() => {
                // Fallback to admin endpoint
                api.get("/admin/governance/pii-types")
                    .then(({ data }) => setPiiTypes(data))
                    .catch(() => {});
            });
    }, []);

    return piiTypes;
}
