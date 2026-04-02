"use client";

import { useAuth } from "@/contexts/auth-context";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { LogOut, Shield, User, Database } from "lucide-react";
import { toast } from "sonner";

export default function DashboardPage() {
    const { isLoggedIn, loading, username, roles, clearAuth } = useAuth();
    const router = useRouter();

    useEffect(() => {
        if (!loading && !isLoggedIn) {
            router.replace("/login");
        }
    }, [loading, isLoggedIn, router]);

    const handleLogout = async () => {
        try {
            await fetch("/api/logout", { method: "POST" });
            clearAuth();
            toast.success("Logged out");
            router.replace("/login");
        } catch {
            toast.error("Logout failed");
        }
    };

    if (loading) {
        return (
            <div className="min-h-screen bg-gray-50 flex items-center justify-center">
                <div className="text-gray-500">Loading...</div>
            </div>
        );
    }

    if (!isLoggedIn) {
        return null;
    }

    return (
        <div className="min-h-screen bg-gray-50">
            {/* Header */}
            <header className="bg-white border-b border-gray-200">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="flex justify-between items-center h-16">
                        <div className="flex items-center gap-3">
                            <Database className="size-6 text-blue-600" />
                            <h1 className="text-lg font-semibold text-gray-900">
                                Governance-Led Storage
                            </h1>
                        </div>
                        <div className="flex items-center gap-4">
                            <span className="text-sm text-gray-600">
                                {username || "User"}
                            </span>
                            <button
                                onClick={handleLogout}
                                className="inline-flex items-center gap-2 px-3 py-2 text-sm text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-md transition-colors"
                            >
                                <LogOut className="size-4" />
                                Logout
                            </button>
                        </div>
                    </div>
                </div>
            </header>

            {/* Main content */}
            <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                {/* Welcome card */}
                <div className="bg-gradient-to-r from-blue-600 to-blue-700 rounded-lg p-6 mb-8 text-white">
                    <h2 className="text-2xl font-bold mb-2">
                        Welcome{username ? `, ${username}` : ""}
                    </h2>
                    <p className="text-blue-100">
                        Your governance-led storage dashboard.
                    </p>
                </div>

                {/* Stats grid */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
                    <StatCard
                        icon={<Database className="size-5 text-blue-600" />}
                        label="Storage Used"
                        value="0 GB"
                        description="of allocated storage"
                    />
                    <StatCard
                        icon={<Shield className="size-5 text-green-600" />}
                        label="Policies Active"
                        value="0"
                        description="governance policies"
                    />
                    <StatCard
                        icon={<User className="size-5 text-purple-600" />}
                        label="Account Type"
                        value={roles.length > 0 ? roles[0].replace("ROLE_", "") : "User"}
                        description="current role"
                    />
                </div>

                {/* Quick actions */}
                <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
                    <h3 className="text-lg font-semibold text-gray-900 mb-4">Quick Actions</h3>
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        <ActionCard
                            title="Upload Data"
                            description="Upload files to governed storage"
                        />
                        <ActionCard
                            title="Manage Policies"
                            description="Configure governance policies"
                        />
                        <ActionCard
                            title="View Audit Log"
                            description="Review access and change history"
                        />
                    </div>
                </div>
            </main>
        </div>
    );
}

function StatCard({ icon, label, value, description }: {
    icon: React.ReactNode;
    label: string;
    value: string;
    description: string;
}) {
    return (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
            <div className="flex items-center gap-3 mb-3">
                {icon}
                <span className="text-sm font-medium text-gray-600">{label}</span>
            </div>
            <div className="text-2xl font-bold text-gray-900">{value}</div>
            <div className="text-sm text-gray-500 mt-1">{description}</div>
        </div>
    );
}

function ActionCard({ title, description }: { title: string; description: string }) {
    return (
        <button className="text-left p-4 rounded-lg border border-gray-200 hover:border-blue-300 hover:bg-blue-50 transition-colors">
            <h4 className="font-medium text-gray-900 mb-1">{title}</h4>
            <p className="text-sm text-gray-500">{description}</p>
        </button>
    );
}
