"use client";

export function SkeletonRow({ cols = 4 }: { cols?: number }) {
    return (
        <div className="flex items-center gap-4 px-4 py-3 animate-pulse">
            <div className="size-4 rounded bg-gray-200 shrink-0" />
            {Array.from({ length: cols }).map((_, i) => (
                <div key={i} className={`h-3 rounded bg-gray-200 ${i === 0 ? "flex-1" : "w-20"}`} />
            ))}
        </div>
    );
}

export function SkeletonTable({ rows = 6, cols = 4 }: { rows?: number; cols?: number }) {
    return (
        <div className="divide-y divide-gray-100">
            {Array.from({ length: rows }).map((_, i) => (
                <SkeletonRow key={i} cols={cols} />
            ))}
        </div>
    );
}

export function SkeletonCard() {
    return (
        <div className="bg-white rounded-lg border border-gray-200 p-5 animate-pulse space-y-3">
            <div className="flex items-center gap-3">
                <div className="size-10 rounded-lg bg-gray-200" />
                <div className="space-y-1.5 flex-1">
                    <div className="h-3 w-32 rounded bg-gray-200" />
                    <div className="h-2 w-48 rounded bg-gray-200" />
                </div>
            </div>
            <div className="h-2 w-full rounded bg-gray-100" />
            <div className="h-2 w-3/4 rounded bg-gray-100" />
        </div>
    );
}

export function SkeletonCards({ count = 3 }: { count?: number }) {
    return (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {Array.from({ length: count }).map((_, i) => (
                <SkeletonCard key={i} />
            ))}
        </div>
    );
}

export function SkeletonStats({ count = 4 }: { count?: number }) {
    return (
        <div className={`grid grid-cols-2 lg:grid-cols-${count} gap-4`}>
            {Array.from({ length: count }).map((_, i) => (
                <div key={i} className="bg-gray-50 rounded-xl p-4 animate-pulse">
                    <div className="h-2 w-16 rounded bg-gray-200 mb-2" />
                    <div className="h-6 w-12 rounded bg-gray-200" />
                </div>
            ))}
        </div>
    );
}
