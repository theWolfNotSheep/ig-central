"use client";

/**
 * Simple line-by-line text diff component.
 * Shows additions (green), removals (red), and unchanged lines (grey).
 */

type DiffLine = {
    type: "add" | "remove" | "same";
    content: string;
    lineOld?: number;
    lineNew?: number;
};

function computeDiff(oldText: string, newText: string): DiffLine[] {
    const oldLines = oldText.split("\n");
    const newLines = newText.split("\n");
    const result: DiffLine[] = [];

    // Simple LCS-based diff
    const m = oldLines.length;
    const n = newLines.length;

    // Build LCS table
    const dp: number[][] = Array.from({ length: m + 1 }, () => Array(n + 1).fill(0));
    for (let i = 1; i <= m; i++) {
        for (let j = 1; j <= n; j++) {
            if (oldLines[i - 1] === newLines[j - 1]) {
                dp[i][j] = dp[i - 1][j - 1] + 1;
            } else {
                dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
    }

    // Backtrack to produce diff
    let i = m, j = n;
    const stack: DiffLine[] = [];
    while (i > 0 || j > 0) {
        if (i > 0 && j > 0 && oldLines[i - 1] === newLines[j - 1]) {
            stack.push({ type: "same", content: oldLines[i - 1], lineOld: i, lineNew: j });
            i--; j--;
        } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
            stack.push({ type: "add", content: newLines[j - 1], lineNew: j });
            j--;
        } else {
            stack.push({ type: "remove", content: oldLines[i - 1], lineOld: i });
            i--;
        }
    }

    stack.reverse();
    return stack;
}

export default function TextDiff({ oldText, newText, oldLabel, newLabel }: {
    oldText: string; newText: string; oldLabel?: string; newLabel?: string;
}) {
    const lines = computeDiff(oldText, newText);
    const hasChanges = lines.some(l => l.type !== "same");

    if (!hasChanges) {
        return (
            <div className="text-xs text-gray-400 text-center py-4">
                No differences — versions are identical
            </div>
        );
    }

    const addCount = lines.filter(l => l.type === "add").length;
    const removeCount = lines.filter(l => l.type === "remove").length;

    return (
        <div className="space-y-2">
            {/* Summary */}
            <div className="flex items-center gap-3 text-[10px]">
                {oldLabel && <span className="text-gray-400">{oldLabel} → {newLabel}</span>}
                <span className="text-green-600 font-medium">+{addCount} added</span>
                <span className="text-red-600 font-medium">-{removeCount} removed</span>
            </div>

            {/* Diff lines */}
            <div className="font-mono text-[11px] leading-relaxed border border-gray-200 rounded-md overflow-auto max-h-80">
                {lines.map((line, idx) => (
                    <div key={idx} className={`flex ${
                        line.type === "add" ? "bg-green-50" :
                        line.type === "remove" ? "bg-red-50" : ""
                    }`}>
                        {/* Line numbers */}
                        <div className="w-8 shrink-0 text-right pr-1 select-none text-gray-300 border-r border-gray-200">
                            {line.type !== "add" ? line.lineOld : ""}
                        </div>
                        <div className="w-8 shrink-0 text-right pr-1 select-none text-gray-300 border-r border-gray-200">
                            {line.type !== "remove" ? line.lineNew : ""}
                        </div>
                        {/* Indicator */}
                        <div className={`w-5 shrink-0 text-center select-none font-bold ${
                            line.type === "add" ? "text-green-600" :
                            line.type === "remove" ? "text-red-600" : "text-gray-300"
                        }`}>
                            {line.type === "add" ? "+" : line.type === "remove" ? "-" : " "}
                        </div>
                        {/* Content */}
                        <div className={`flex-1 px-2 whitespace-pre-wrap break-all ${
                            line.type === "add" ? "text-green-800" :
                            line.type === "remove" ? "text-red-800 line-through" :
                            "text-gray-600"
                        }`}>
                            {line.content || "\u00A0"}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
