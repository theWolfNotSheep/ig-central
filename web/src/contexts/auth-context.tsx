"use client";

import React, { createContext, useContext, useEffect, useMemo, useState } from "react";
import api from "@/lib/axios/axios.client";
import type { AccountType } from "@/lib/types";

export type AuthState = {
    isAuthenticated: boolean;
    userId?: string;
    username?: string;
    avatarUrl?: string;
    roles: string[];
    permissions: string[];
    accountType?: AccountType;
    sensitivityClearanceLevel: number;
};

type AuthContextValue = AuthState & {
    isLoggedIn: boolean;
    loading: boolean;
    setAuth: (next: Partial<AuthState>) => void;
    clearAuth: () => void;
    refreshAuth: () => Promise<void>;
    hasPermission: (permission: string) => boolean;
    hasAnyPermission: (...permissions: string[]) => boolean;
    hasRole: (role: string) => boolean;
    canViewSensitivity: (level: number) => boolean;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const EMPTY_AUTH: AuthState = {
    isAuthenticated: false,
    roles: [],
    permissions: [],
    sensitivityClearanceLevel: 0,
};

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [state, setState] = useState<AuthState>(EMPTY_AUTH);
    const [loading, setLoading] = useState(true);

    const setAuth = (next: Partial<AuthState>) => {
        setState((prev) => ({
            ...prev,
            ...next,
            roles: Array.isArray(next.roles) ? next.roles : prev.roles,
            permissions: Array.isArray(next.permissions) ? next.permissions : prev.permissions,
        }));
    };

    const clearAuth = () => setState(EMPTY_AUTH);

    const fetchAuth = async () => {
        try {
            const { data: me } = await api.get("/user/me");

            if (me?.isAuthenticated) {
                const roles = (me.roles ?? []).map((x: string) =>
                    x.startsWith("ROLE_") ? x : `ROLE_${x}`
                );
                setAuth({
                    isAuthenticated: true,
                    userId: me.userId,
                    username: me.displayName || [me.firstName, me.lastName].filter(Boolean).join(" ") || me.userId,
                    avatarUrl: me.avatarUrl ?? undefined,
                    roles,
                    permissions: me.permissions ?? [],
                    accountType: me.accountType as AccountType | undefined,
                    sensitivityClearanceLevel: me.sensitivityClearanceLevel ?? 0,
                });
            } else {
                clearAuth();
            }
        } catch {
            clearAuth();
        } finally {
            setLoading(false);
        }
    };

    const refreshAuth = async () => {
        setLoading(true);
        await fetchAuth();
    };

    useEffect(() => {
        let cancelled = false;

        (async () => {
            await fetchAuth();
            if (cancelled) return;
        })();

        return () => {
            cancelled = true;
        };
    }, []);

    const value = useMemo<AuthContextValue>(
        () => ({
            ...state,
            isLoggedIn: state.isAuthenticated,
            loading,
            setAuth,
            clearAuth,
            refreshAuth,
            hasPermission: (permission: string) =>
                state.permissions.includes(permission),
            hasAnyPermission: (...perms: string[]) =>
                perms.some((p) => state.permissions.includes(p)),
            hasRole: (role: string) =>
                state.roles.some((r) => r === role || r === `ROLE_${role}`),
            canViewSensitivity: (level: number) =>
                state.sensitivityClearanceLevel >= level,
        }),
        [state, loading]
    );

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
    return ctx;
}
