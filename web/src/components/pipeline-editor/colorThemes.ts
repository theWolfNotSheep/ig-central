/**
 * Static color theme map for pipeline nodes.
 * Each key maps to Tailwind classes that are statically referenced
 * so they survive Tailwind's class detection (no dynamic interpolation).
 *
 * To add a new theme: add a new entry here with all 5 fields.
 */
export type ColorTheme = {
    bg: string;
    border: string;
    text: string;
    iconBg: string;
    minimap: string;
};

export const COLOR_THEMES: Record<string, ColorTheme> = {
    amber: {
        bg: "bg-amber-50", border: "border-amber-300", text: "text-amber-700",
        iconBg: "bg-amber-100", minimap: "#f59e0b",
    },
    blue: {
        bg: "bg-blue-50", border: "border-blue-300", text: "text-blue-700",
        iconBg: "bg-blue-100", minimap: "#3b82f6",
    },
    purple: {
        bg: "bg-purple-50", border: "border-purple-300", text: "text-purple-700",
        iconBg: "bg-purple-100", minimap: "#a855f7",
    },
    indigo: {
        bg: "bg-indigo-50", border: "border-indigo-300", text: "text-indigo-700",
        iconBg: "bg-indigo-100", minimap: "#6366f1",
    },
    green: {
        bg: "bg-green-50", border: "border-green-300", text: "text-green-700",
        iconBg: "bg-green-100", minimap: "#22c55e",
    },
    pink: {
        bg: "bg-pink-50", border: "border-pink-300", text: "text-pink-700",
        iconBg: "bg-pink-100", minimap: "#ec4899",
    },
    red: {
        bg: "bg-red-50", border: "border-red-300", text: "text-red-700",
        iconBg: "bg-red-100", minimap: "#ef4444",
    },
    teal: {
        bg: "bg-teal-50", border: "border-teal-300", text: "text-teal-700",
        iconBg: "bg-teal-100", minimap: "#14b8a6",
    },
    orange: {
        bg: "bg-orange-50", border: "border-orange-300", text: "text-orange-700",
        iconBg: "bg-orange-100", minimap: "#f97316",
    },
    gray: {
        bg: "bg-gray-50", border: "border-gray-300", text: "text-gray-700",
        iconBg: "bg-gray-100", minimap: "#6b7280",
    },
    cyan: {
        bg: "bg-cyan-50", border: "border-cyan-300", text: "text-cyan-700",
        iconBg: "bg-cyan-100", minimap: "#06b6d4",
    },
    violet: {
        bg: "bg-violet-50", border: "border-violet-300", text: "text-violet-700",
        iconBg: "bg-violet-100", minimap: "#8b5cf6",
    },
    emerald: {
        bg: "bg-emerald-50", border: "border-emerald-300", text: "text-emerald-700",
        iconBg: "bg-emerald-100", minimap: "#10b981",
    },
    rose: {
        bg: "bg-rose-50", border: "border-rose-300", text: "text-rose-700",
        iconBg: "bg-rose-100", minimap: "#f43f5e",
    },
    sky: {
        bg: "bg-sky-50", border: "border-sky-300", text: "text-sky-700",
        iconBg: "bg-sky-100", minimap: "#0ea5e9",
    },
    lime: {
        bg: "bg-lime-50", border: "border-lime-300", text: "text-lime-700",
        iconBg: "bg-lime-100", minimap: "#84cc16",
    },
};

export const DEFAULT_THEME = COLOR_THEMES.gray;
