"use client";

import "./globals.css";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  Package,
  KeyRound,
  Download,
  Shield,
  LogOut,
  Users,
} from "lucide-react";
import { clearCredentials, isAuthenticated } from "@/lib/api";

const navItems = [
  { href: "/", label: "Dashboard", icon: LayoutDashboard },
  { href: "/packs", label: "Packs", icon: Package },
  { href: "/users", label: "Users", icon: Users },
  { href: "/api-keys", label: "API Keys", icon: KeyRound },
  { href: "/downloads", label: "Downloads", icon: Download },
];

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const authed =
    typeof window !== "undefined" ? isAuthenticated() : false;

  function handleLogout() {
    clearCredentials();
    window.location.href = "/";
  }

  return (
    <html lang="en">
      <body className="flex h-screen bg-gray-50 text-gray-900 antialiased">
        {authed && (
          <aside className="flex w-60 shrink-0 flex-col bg-gray-900 text-gray-300">
            <div className="flex h-14 items-center gap-2 border-b border-gray-800 px-5">
              <Shield className="h-6 w-6 text-blue-400" />
              <span className="text-lg font-semibold tracking-tight text-white">
                IG Hub
              </span>
            </div>

            <nav className="flex-1 space-y-0.5 px-3 py-4">
              {navItems.map((item) => {
                const Icon = item.icon;
                const active =
                  item.href === "/"
                    ? pathname === "/"
                    : pathname.startsWith(item.href);
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={`flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                      active
                        ? "bg-gray-800 text-white"
                        : "text-gray-400 hover:bg-gray-800/60 hover:text-gray-200"
                    }`}
                  >
                    <Icon className="h-4 w-4" />
                    {item.label}
                  </Link>
                );
              })}
            </nav>

            <div className="border-t border-gray-800 p-3">
              <button
                onClick={handleLogout}
                className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-gray-400 transition-colors hover:bg-gray-800/60 hover:text-gray-200"
              >
                <LogOut className="h-4 w-4" />
                Sign Out
              </button>
            </div>
          </aside>
        )}

        <main className="flex-1 overflow-y-auto bg-white">
          {children}
        </main>
      </body>
    </html>
  );
}
