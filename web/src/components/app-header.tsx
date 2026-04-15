"use client";

import { useAuth } from "@/contexts/auth-context";
import { useRouter } from "next/navigation";
import { LogOut, User } from "lucide-react";
import { toast } from "sonner";

export default function AppHeader() {
    const { username, avatarUrl, clearAuth } = useAuth();
    const router = useRouter();

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

    return (
        <header className="h-16 bg-white border-b border-gray-200 flex items-center justify-between px-6 shrink-0">
            <div className="flex items-center gap-2">
                <span className="text-xs font-medium text-gray-400 uppercase tracking-wider">IG Central</span>
            </div>
            <div className="flex items-center gap-3">
                <div className="flex items-center gap-2">
                    {avatarUrl ? (
                        <img src={avatarUrl} alt="" className="size-8 rounded-full border border-gray-200" referrerPolicy="no-referrer" />
                    ) : (
                        <div className="size-8 rounded-full bg-gray-100 flex items-center justify-center">
                            <User className="size-4 text-gray-400" />
                        </div>
                    )}
                    <span className="text-sm text-gray-700 font-medium">{username || "User"}</span>
                </div>
                <button
                    onClick={handleLogout}
                    className="inline-flex items-center gap-2 px-3 py-2 text-sm text-gray-500 hover:text-gray-900 hover:bg-gray-100 rounded-md transition-colors"
                >
                    <LogOut className="size-4" />
                    Logout
                </button>
            </div>
        </header>
    );
}
