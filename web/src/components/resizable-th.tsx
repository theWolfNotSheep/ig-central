"use client";

import { useCallback, useRef, useState } from "react";

interface ResizableThProps {
    children: React.ReactNode;
    className?: string;
    minWidth?: number;
    initialWidth?: number;
    onClick?: () => void;
}

/**
 * A table header cell that can be resized by dragging its right edge.
 * Drop-in replacement for <th> — wraps children and adds a drag handle.
 */
export default function ResizableTh({ children, className = "", minWidth = 40, initialWidth, onClick }: ResizableThProps) {
    const thRef = useRef<HTMLTableCellElement>(null);
    const [width, setWidth] = useState<number | undefined>(initialWidth);
    const dragging = useRef(false);
    const startX = useRef(0);
    const startW = useRef(0);

    const onMouseDown = useCallback((e: React.MouseEvent) => {
        e.preventDefault();
        e.stopPropagation();
        dragging.current = true;
        startX.current = e.clientX;
        startW.current = thRef.current?.offsetWidth ?? 100;

        const onMouseMove = (ev: MouseEvent) => {
            if (!dragging.current) return;
            const diff = ev.clientX - startX.current;
            const newWidth = Math.max(minWidth, startW.current + diff);
            setWidth(newWidth);
        };

        const onMouseUp = () => {
            dragging.current = false;
            document.removeEventListener("mousemove", onMouseMove);
            document.removeEventListener("mouseup", onMouseUp);
        };

        document.addEventListener("mousemove", onMouseMove);
        document.addEventListener("mouseup", onMouseUp);
    }, [minWidth]);

    return (
        <th
            ref={thRef}
            className={`relative select-none ${className}`}
            style={width ? { width: `${width}px`, minWidth: `${minWidth}px` } : { minWidth: `${minWidth}px` }}
            onClick={onClick}
        >
            {children}
            {/* Drag handle */}
            <div
                onMouseDown={onMouseDown}
                className="absolute top-0 right-0 w-1 h-full cursor-col-resize hover:bg-blue-400 active:bg-blue-500 transition-colors"
                style={{ zIndex: 10 }}
            />
        </th>
    );
}
