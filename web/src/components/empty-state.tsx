"use client";

import Link from "next/link";
import { Inbox, type LucideIcon } from "lucide-react";

interface EmptyStateProps {
    icon?: LucideIcon;
    title: string;
    description?: string;
    action?: string;
    href?: string;
    onAction?: () => void;
}

export default function EmptyState({ icon: Icon = Inbox, title, description, action, href, onAction }: EmptyStateProps) {
    return (
        <div className="flex flex-col items-center justify-center py-12 px-6 text-center">
            <div className="size-16 rounded-full bg-gray-100 flex items-center justify-center mb-4">
                <Icon className="size-8 text-gray-300" />
            </div>
            <h3 className="text-sm font-semibold text-gray-700 mb-1">{title}</h3>
            {description && <p className="text-xs text-gray-400 max-w-xs mb-4">{description}</p>}
            {action && href && (
                <Link href={href}
                    className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700 transition-colors">
                    {action}
                </Link>
            )}
            {action && onAction && !href && (
                <button onClick={onAction}
                    className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700 transition-colors">
                    {action}
                </button>
            )}
        </div>
    );
}
