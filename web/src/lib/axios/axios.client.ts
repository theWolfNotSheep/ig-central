"use client"

import axios from "axios";

export const api = axios.create({
    baseURL: "/api/proxy",
    withCredentials: true,
    xsrfCookieName: "XSRF-TOKEN",
    xsrfHeaderName: "X-XSRF-TOKEN",
});

api.interceptors.response.use(undefined, (error) => {
    if (error?.response?.status === 403) {
        const msg = error.response.data?.message || error.response.data?.error || "";
        if (msg === "ACCOUNT_DISABLED") {
            window.location.href = "/login?reason=disabled";
            return new Promise(() => {});
        }
        if (msg === "ACCOUNT_LOCKED") {
            window.location.href = "/login?reason=locked";
            return new Promise(() => {});
        }
    }
    return Promise.reject(error);
});

export default api;
