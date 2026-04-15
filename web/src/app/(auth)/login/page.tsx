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
                    <img src="/logo.svg" alt="IG Central" className="size-12 mb-4" />
                    <h1 className="text-3xl md:text-4xl font-bold text-gray-900 mb-2">
                        Welcome Back
                    </h1>
                    <p className="text-gray-600">
                        Log in to IG Central
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

                    {/* Divider */}
                    <div className="relative my-6">
                        <div className="absolute inset-0 flex items-center">
                            <div className="w-full border-t border-gray-200" />
                        </div>
                        <div className="relative flex justify-center text-sm">
                            <span className="bg-gray-50 px-4 text-gray-400">or</span>
                        </div>
                    </div>

                    {/* Google Login */}
                    <button
                        onClick={() => { window.location.href = "/api/auth/public/google/login"; }}
                        className="w-full inline-flex items-center justify-center gap-3 px-4 py-2.5 bg-white border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 hover:border-gray-400 transition-colors shadow-sm"
                    >
                        <svg className="size-5" viewBox="0 0 24 24">
                            <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/>
                            <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                            <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                            <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
                        </svg>
                        Continue with Google
                    </button>
                </div>
            </div>
        </div>
    );
}
