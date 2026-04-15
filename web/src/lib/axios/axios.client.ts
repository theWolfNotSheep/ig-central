"use client"

import axios from "axios";
import { toast } from "sonner";

export const api = axios.create({
    baseURL: "/api/proxy",
    withCredentials: true,
    xsrfCookieName: "XSRF-TOKEN",
    xsrfHeaderName: "X-XSRF-TOKEN",
});

api.interceptors.response.use(undefined, (error) => {
    const status = error?.response?.status;
    const msg = error?.response?.data?.message || error?.response?.data?.error || "";

    if (status === 401) {
        // Session expired — redirect to login (but not if already on login or home page)
        const path = window.location.pathname;
        if (path !== "/" && !path.startsWith("/login")) {
            window.location.href = "/login?reason=expired";
        }
        return new Promise(() => {});
    }

    if (status === 403) {
        if (msg === "ACCOUNT_DISABLED") {
            window.location.href = "/login?reason=disabled";
            return new Promise(() => {});
        }
        if (msg === "ACCOUNT_LOCKED") {
            window.location.href = "/login?reason=locked";
            return new Promise(() => {});
        }
        if (msg === "ACCESS_DENIED") {
            toast.error("Access denied — you don't have permission for this action");
        }
    }

    if (status >= 500) {
        const endpoint = error?.config?.url?.replace("/api/proxy", "") ?? "";
        const serverMsg = msg || "An unexpected error occurred";
        console.error("Server error:", status, serverMsg, endpoint);

        // Show specific toast based on the error context
        if (endpoint.includes("/documents") && serverMsg.includes("queue")) {
            toast.error("Failed to queue document for processing — RabbitMQ may be unavailable");
        } else if (endpoint.includes("/classify") || endpoint.includes("/llm")) {
            toast.error("Classification failed — the LLM service is not responding");
        } else if (endpoint.includes("/drives") || serverMsg.toLowerCase().includes("drive")) {
            toast.error("Google Drive error — the connection may have expired");
        } else if (serverMsg.toLowerCase().includes("timeout")) {
            toast.error("Request timed out — try again");
        } else {
            toast.error(`Server error: ${serverMsg}`);
        }
    }

    if (!error.response) {
        console.error("Network error:", error.message);
        toast.error("Network error — check your connection");
    }

    return Promise.reject(error);
});

export default api;
