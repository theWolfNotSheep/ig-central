import axios from "axios";
import { redirect } from "next/navigation";

function normalizeBase(base: string) {
    return base.replace(/\/+$/, "");
}

const rawBase =
    process.env.BACKEND_BASE_URL ||
    process.env.API_BASE_URL ||
    "http://localhost:8080";

export const serverApi = axios.create({
    baseURL: normalizeBase(rawBase),
    withCredentials: true,
    xsrfCookieName: "XSRF-TOKEN",
    xsrfHeaderName: "X-XSRF-TOKEN",
    proxy: false,
});

serverApi.interceptors.response.use(undefined, (error) => {
    if (error?.response?.status === 403) {
        const msg = error.response.data?.message || error.response.data?.error || "";
        if (msg === "ACCOUNT_DISABLED") redirect("/login?reason=disabled");
        if (msg === "ACCOUNT_LOCKED") redirect("/login?reason=locked");
    }
    return Promise.reject(error);
});

export default serverApi;
