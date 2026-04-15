"use client";

import { useAuth } from "@/contexts/auth-context";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import Sidebar from "@/components/sidebar";
import AppHeader from "@/components/app-header";

export default function ProtectedLayout({ children }: { children: React.ReactNode }) {
    const { isLoggedIn, loading } = useAuth();
    const router = useRouter();

    useEffect(() => {
        if (!loading && !isLoggedIn) router.replace("/login");
    }, [loading, isLoggedIn, router]);

    if (loading) {
        return (
            <div className="min-h-screen bg-gray-50 flex items-center justify-center">
                <div className="text-gray-500">Loading...</div>
            </div>
        );
    }

    if (!isLoggedIn) return null;

    return (
        <div className="h-screen bg-gray-50 flex overflow-hidden">
            <Sidebar />
            <div className="flex-1 flex flex-col min-w-0">
                <AppHeader />
                <main className="flex-1 p-6 overflow-auto">{children}</main>
            </div>
        </div>
    );
}
