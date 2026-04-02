"use client";

import { Suspense, useCallback, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { toast } from "sonner";
import { Mail, Lock, ArrowLeft } from "lucide-react";

import api from "@/lib/axios/axios.client";
import { useAuth } from "@/contexts/auth-context";
import type { LoginApiResponse } from "@/lib/types";

const ROUTES = {
    home: "/",
    dashboard: "/dashboard",
} as const;

function normalizeRoles(roles?: string[]): string[] {
    return (roles ?? []).filter(Boolean).map((r) =>
        r.startsWith("ROLE_") ? r : `ROLE_${r}`
    );
}

function getAxiosErrorMessage(err: any): string {
    const data = err?.response?.data;
    return (
        data?.message ||
        data?.error ||
        err?.message ||
        "Request failed"
    );
}

async function loginRequest(email: string, password: string): Promise<LoginApiResponse> {
    const { data } = await api.post<LoginApiResponse>("/auth/login", {
        username: email,
        password,
    });
    return data;
}

async function ensureCsrfCookie(): Promise<void> {
    await api.get("/csrf-token", { validateStatus: () => true } as any);
}

function BackToHome() {
    return (
        <Link
            href={ROUTES.home}
            className="inline-flex items-center gap-2 text-gray-600 hover:text-gray-900 mb-6"
        >
            <ArrowLeft className="size-4" />
            Back to Home
        </Link>
    );
}

function LoginForm(props: {
    username: string;
    password: string;
    isSubmitting: boolean;
    onUsernameChange: (v: string) => void;
    onPasswordChange: (v: string) => void;
    onSubmit: (e: React.FormEvent) => void;
}) {
    const { username, password, isSubmitting, onUsernameChange, onPasswordChange, onSubmit } = props;

    return (
        <form onSubmit={onSubmit} className="space-y-4 mb-6">
            <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Email</label>
                <div className="relative">
                    <Mail className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-gray-400" />
                    <input
                        type="email"
                        value={username}
                        onChange={(e) => onUsernameChange(e.target.value)}
                        placeholder="your.email@example.com"
                        className="w-full pl-10 pr-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                        required
                        autoComplete="email"
                    />
                </div>
            </div>

            <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Password</label>
                <div className="relative">
                    <Lock className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-gray-400" />
                    <input
                        type="password"
                        value={password}
                        onChange={(e) => onPasswordChange(e.target.value)}
                        placeholder="Enter your password"
                        className="w-full pl-10 pr-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                        required
                        autoComplete="current-password"
                    />
                </div>
            </div>

            <div className="flex justify-between items-center text-sm">
                <label className="flex items-center gap-2 cursor-pointer">
                    <input type="checkbox" className="rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
                    <span className="text-gray-600">Remember me</span>
                </label>
            </div>

            <button
                type="submit"
                disabled={isSubmitting}
                className="w-full px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed transition-colors font-semibold"
            >
                {isSubmitting ? "Logging in..." : "Log In"}
            </button>
        </form>
    );
}

export default function LoginPage() {
    return (
        <Suspense>
            <LoginPageContent />
        </Suspense>
    );
}

function LoginPageContent() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const { refreshAuth } = useAuth();

    const reason = searchParams.get("reason");

    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    const handleLogin = useCallback(
        async (e: React.FormEvent) => {
            e.preventDefault();

            const email = username.trim();
            if (!email || !password) return;

            setIsSubmitting(true);

            try {
                await ensureCsrfCookie();
                await loginRequest(email, password);
                toast.success("Welcome back!");
                await refreshAuth();
                router.replace(ROUTES.dashboard);
                router.refresh();
            } catch (err: any) {
                const msg = getAxiosErrorMessage(err);
                if (msg === "USER_NOT_FOUND") {
                    toast.error("No account found with those credentials.");
                } else if (msg === "ACCOUNT_DISABLED") {
                    toast.error("Your account has been disabled. Please contact support.");
                } else if (msg === "ACCOUNT_LOCKED") {
                    toast.error("Your account has been locked. Please contact support.");
                } else {
                    toast.error(msg);
                }
            } finally {
                setIsSubmitting(false);
            }
        },
        [username, password, router, refreshAuth]
    );

    return (
        <div className="min-h-screen bg-gray-50 py-8 md:py-12 flex items-center justify-center px-4">
            <div className="w-full max-w-md">
                <div className="mb-8">
                    <BackToHome />
                    <h1 className="text-3xl md:text-4xl font-bold text-gray-900 mb-2">
                        Welcome Back
                    </h1>
                    <p className="text-gray-600">
                        Log in to Governance-Led Storage
                    </p>
                </div>

                {reason === "disabled" && (
                    <div className="mb-6 rounded-lg border border-red-200 bg-red-50 p-4">
                        <h3 className="text-sm font-semibold text-red-800 mb-1">Account Disabled</h3>
                        <p className="text-sm text-red-700">
                            Your account has been disabled by an administrator. If you believe this is a mistake, please contact support.
                        </p>
                    </div>
                )}
                {reason === "locked" && (
                    <div className="mb-6 rounded-lg border border-yellow-200 bg-yellow-50 p-4">
                        <h3 className="text-sm font-semibold text-yellow-800 mb-1">Account Locked</h3>
                        <p className="text-sm text-yellow-700">
                            Your account has been locked. Please contact support for assistance.
                        </p>
                    </div>
                )}

                <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 md:p-8">
                    <LoginForm
                        username={username}
                        password={password}
                        isSubmitting={isSubmitting}
                        onUsernameChange={setUsername}
                        onPasswordChange={setPassword}
                        onSubmit={handleLogin}
                    />
                </div>
            </div>
        </div>
    );
}
