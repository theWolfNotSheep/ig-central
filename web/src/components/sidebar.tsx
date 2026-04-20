"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/contexts/auth-context";
import {
    Database,
    LayoutDashboard,
    Upload,
    Shield,
    FileSearch,
    Search,
    Settings,
    Activity,
    Workflow,
    Users,
    ScrollText,
    ShieldCheck,
    ChevronLeft,
    ChevronRight,
    HelpCircle,
    Brain,
    History,
    Inbox,
    Mail,
    BarChart3,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";

const navItems = [
    { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
    { href: "/inbox", label: "Inbox", icon: Inbox, badge: true },
    { href: "/drives", label: "Drives", icon: Database },
    { href: "/mailboxes", label: "Mailboxes", icon: Mail },
    { href: "/documents", label: "Documents", icon: Search },
    { href: "/search", label: "Advanced Search", icon: FileSearch },
    { href: "/pii", label: "PII & SARs", icon: Shield },
    { href: "/governance", label: "Governance", icon: Shield },
    { href: "/review", label: "Review Queue", icon: FileSearch },
];

export default function Sidebar() {
    const pathname = usePathname();
    const [collapsed, setCollapsed] = useState(false);
    const [inboxCount, setInboxCount] = useState(0);
    const { hasAnyPermission } = useAuth();

    const fetchInboxCount = useCallback(async () => {
        try {
            const { default: api } = await import("@/lib/axios/axios.client");
            const { data } = await api.get("/filing/inbox/count");
            setInboxCount(data.count ?? 0);
        } catch {}
    }, []);

    useEffect(() => {
        fetchInboxCount();
        const interval = setInterval(fetchInboxCount, 30000);
        return () => clearInterval(interval);
    }, [fetchInboxCount]);
    const showAdmin = hasAnyPermission(
        "PIPELINE_READ", "MONITORING_READ", "SETTINGS_READ",
        "USER_READ", "GOV_POLICY_READ"
    );

    return (
        <aside
            className={`${
                collapsed ? "w-16" : "w-56"
            } bg-white border-r border-gray-200 flex flex-col transition-all duration-200 shrink-0`}
        >
            {/* Logo */}
            <div className="h-16 flex items-center gap-3 px-4 border-b border-gray-200">
                <img src="/favicon.svg" alt="IG Central" className="size-7 shrink-0" />
                {!collapsed && (
                    <span className="text-sm font-semibold text-gray-900 truncate">
                        IG Central
                    </span>
                )}
            </div>

            {/* Nav */}
            <nav className="flex-1 py-4 space-y-1 px-2">
                {navItems.map((item) => (
                    <NavLink key={item.href} item={item} pathname={pathname} collapsed={collapsed}
                        badgeCount={item.badge ? inboxCount : 0} />
                ))}
            </nav>

            {/* Admin — pinned to bottom */}
            {showAdmin && (
                <div className="px-2 pb-2 border-t border-gray-200 pt-2 space-y-1">
                    <NavLink item={{ href: "/admin/users", label: "Users", icon: Users }} pathname={pathname} collapsed={collapsed} />
                    <NavLink item={{ href: "/ai", label: "AI", icon: Brain }} pathname={pathname} collapsed={collapsed} />
                    <NavLink item={{ href: "/reports", label: "Reports", icon: BarChart3 }} pathname={pathname} collapsed={collapsed} />
                    <NavLink item={{ href: "/monitoring", label: "Monitoring", icon: Activity }} pathname={pathname} collapsed={collapsed} />
                    <NavLink item={{ href: "/admin/audit", label: "Audit Log", icon: ScrollText }} pathname={pathname} collapsed={collapsed} />
                    <NavLink item={{ href: "/admin/directory", label: "Directory", icon: Users }} pathname={pathname} collapsed={collapsed} />
                    <NavLink item={{ href: "/admin/access", label: "Access Audit", icon: ShieldCheck }} pathname={pathname} collapsed={collapsed} />
                    <NavLink item={{ href: "/settings", label: "Settings", icon: Settings }} pathname={pathname} collapsed={collapsed} />
                </div>
            )}

            {/* Help link */}
            <div className="px-2 pb-1">
                <NavLink item={{ href: "/help", label: "Help", icon: HelpCircle }} pathname={pathname} collapsed={collapsed} />
            </div>

            {/* Collapse toggle */}
            <button
                onClick={() => setCollapsed(!collapsed)}
                className="h-10 flex items-center justify-center border-t border-gray-200 text-gray-400 hover:text-gray-600 transition-colors"
            >
                {collapsed ? <ChevronRight className="size-4" /> : <ChevronLeft className="size-4" />}
            </button>
        </aside>
    );
}

function NavLink({ item, pathname, collapsed, badgeCount = 0 }: {
    item: { href: string; label: string; icon: React.ElementType };
    pathname: string;
    collapsed: boolean;
    badgeCount?: number;
}) {
    const active = pathname === item.href || pathname.startsWith(item.href + "/");
    return (
        <Link
            href={item.href}
            className={`flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                active
                    ? "bg-blue-50 text-blue-700"
                    : "text-gray-600 hover:bg-gray-100 hover:text-gray-900"
            }`}
            title={collapsed ? item.label : undefined}
        >
            <item.icon className="size-5 shrink-0" />
            {!collapsed && (
                <>
                    <span>{item.label}</span>
                    {badgeCount > 0 && (
                        <span className="ml-auto inline-flex items-center justify-center min-w-5 h-5 text-[10px] font-bold bg-blue-500 text-white rounded-full px-1">
                            {badgeCount}
                        </span>
                    )}
                </>
            )}
            {collapsed && badgeCount > 0 && (
                <span className="absolute left-10 top-0 inline-flex items-center justify-center size-4 text-[9px] font-bold bg-blue-500 text-white rounded-full">
                    {badgeCount}
                </span>
            )}
        </Link>
    );
}
