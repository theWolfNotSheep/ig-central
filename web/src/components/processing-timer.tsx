"use client";

import { useEffect, useState } from "react";
import { Clock } from "lucide-react";

interface ProcessingTimerProps {
    createdAt: string;
}

export function ProcessingTimer({ createdAt }: ProcessingTimerProps) {
    const [elapsed, setElapsed] = useState("");

    useEffect(() => {
        const start = new Date(createdAt).getTime();

        function update() {
            const diff = Math.max(0, Date.now() - start);
            const secs = Math.floor(diff / 1000);
            const mins = Math.floor(secs / 60);
            const hrs = Math.floor(mins / 60);

            if (hrs > 0) {
                setElapsed(`${hrs}h ${mins % 60}m ${secs % 60}s`);
            } else if (mins > 0) {
                setElapsed(`${mins}m ${secs % 60}s`);
            } else {
                setElapsed(`${secs}s`);
            }
        }

        update();
        const interval = setInterval(update, 1000);
        return () => clearInterval(interval);
    }, [createdAt]);

    return (
        <span className="inline-flex items-center gap-1 text-[10px] text-gray-400 font-mono tabular-nums">
            <Clock className="size-2.5" />
            {elapsed}
        </span>
    );
}
