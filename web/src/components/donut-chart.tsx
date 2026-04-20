"use client";

type Segment = { value: number; color: string; label: string };

export default function DonutChart({
    segments,
    size = 120,
    strokeWidth = 14,
    children,
}: {
    segments: Segment[];
    size?: number;
    strokeWidth?: number;
    children?: React.ReactNode;
}) {
    const radius = (size - strokeWidth) / 2;
    const circumference = 2 * Math.PI * radius;
    const total = segments.reduce((s, seg) => s + seg.value, 0);

    let offset = 0;
    const arcs = segments.filter(s => s.value > 0).map((seg) => {
        const pct = total > 0 ? seg.value / total : 0;
        const dashArray = `${pct * circumference} ${circumference}`;
        const dashOffset = -offset * circumference;
        offset += pct;
        return { ...seg, dashArray, dashOffset };
    });

    return (
        <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
            <svg width={size} height={size} className="-rotate-90">
                {/* Background track */}
                <circle cx={size / 2} cy={size / 2} r={radius}
                    fill="none" stroke="#f3f4f6" strokeWidth={strokeWidth} />
                {arcs.map((arc, i) => (
                    <circle key={i} cx={size / 2} cy={size / 2} r={radius}
                        fill="none" stroke={arc.color} strokeWidth={strokeWidth}
                        strokeDasharray={arc.dashArray} strokeDashoffset={arc.dashOffset}
                        strokeLinecap="round" className="transition-all duration-500" />
                ))}
            </svg>
            {children && (
                <div className="absolute inset-0 flex items-center justify-center">
                    {children}
                </div>
            )}
        </div>
    );
}
