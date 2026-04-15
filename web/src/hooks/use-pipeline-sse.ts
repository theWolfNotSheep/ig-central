"use client";

import { useCallback, useEffect, useRef, useState } from "react";

type PipelineData = {
    statusCounts: Record<string, number>;
    totalDocuments: number;
    throughput: { last24h: number; last7d: number };
    avgClassificationTimeMs: number;
    staleDocuments: number;
    queueDepths: Record<string, number>;
};

type DocumentStatusEvent = {
    documentId: string;
    status: string;
    fileName: string;
};

type PipelineLogEvent = {
    documentId: string;
    fileName: string;
    stage: string;
    level: string;
    message: string;
    durationMs: number;
    timestamp: number;
};

type UsePipelineSSEOptions = {
    onPipelineMetrics?: (data: PipelineData) => void;
    onDocumentStatus?: (data: DocumentStatusEvent) => void;
    onPipelineLog?: (data: PipelineLogEvent) => void;
};

/**
 * Hook that connects to the monitoring SSE endpoint for real-time pipeline updates.
 * Falls back to polling if SSE connection fails.
 */
export function usePipelineSSE(options: UsePipelineSSEOptions) {
    const [connected, setConnected] = useState(false);
    const eventSourceRef = useRef<EventSource | null>(null);
    const optionsRef = useRef(options);
    optionsRef.current = options;

    const connect = useCallback(() => {
        // SSE goes directly to the Spring Boot API via nginx (bypasses Next.js proxy)
        const es = new EventSource("/api/admin/monitoring/events", { withCredentials: true });
        eventSourceRef.current = es;

        es.addEventListener("connected", () => {
            setConnected(true);
        });

        es.addEventListener("pipeline-metrics", (e) => {
            try {
                const data = JSON.parse(e.data);
                optionsRef.current.onPipelineMetrics?.(data);
            } catch { /* ignore parse errors */ }
        });

        es.addEventListener("document-status", (e) => {
            try {
                const data = JSON.parse(e.data);
                optionsRef.current.onDocumentStatus?.(data);
            } catch { /* ignore parse errors */ }
        });

        es.addEventListener("pipeline-log", (e) => {
            try {
                const data = JSON.parse(e.data);
                optionsRef.current.onPipelineLog?.(data);
            } catch { /* ignore parse errors */ }
        });

        es.onerror = () => {
            setConnected(false);
            es.close();
            // Reconnect after 3 seconds
            setTimeout(connect, 3000);
        };
    }, []);

    useEffect(() => {
        connect();
        return () => {
            eventSourceRef.current?.close();
            eventSourceRef.current = null;
        };
    }, [connect]);

    return { connected };
}
