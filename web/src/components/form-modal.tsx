"use client";

import { useEffect, useRef } from "react";
import { X } from "lucide-react";

interface FormModalProps {
    title: string;
    open: boolean;
    onClose: () => void;
    children: React.ReactNode;
    footer?: React.ReactNode;
    width?: "sm" | "md" | "lg" | "xl" | "2xl";
}

const WIDTH_MAP = {
    sm: "max-w-sm",
    md: "max-w-md",
    lg: "max-w-lg",
    xl: "max-w-xl",
    "2xl": "max-w-2xl",
};

export default function FormModal({ title, open, onClose, children, footer, width = "lg" }: FormModalProps) {
    const overlayRef = useRef<HTMLDivElement>(null);
    const contentRef = useRef<HTMLDivElement>(null);
    const onCloseRef = useRef(onClose);
    onCloseRef.current = onClose;
    const hasAutoFocused = useRef(false);

    // Trap focus inside modal
    useEffect(() => {
        if (!open) {
            hasAutoFocused.current = false;
            return;
        }
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === "Escape") onCloseRef.current();
            if (e.key === "Tab" && contentRef.current) {
                const focusable = contentRef.current.querySelectorAll<HTMLElement>(
                    'input, select, textarea, button, [tabindex]:not([tabindex="-1"])'
                );
                if (focusable.length === 0) return;
                const first = focusable[0];
                const last = focusable[focusable.length - 1];
                if (e.shiftKey && document.activeElement === first) {
                    e.preventDefault();
                    last.focus();
                } else if (!e.shiftKey && document.activeElement === last) {
                    e.preventDefault();
                    first.focus();
                }
            }
        };
        document.addEventListener("keydown", handleKeyDown);
        // Focus first input only on initial open (not on every re-render)
        if (!hasAutoFocused.current) {
            hasAutoFocused.current = true;
            setTimeout(() => {
                const first = contentRef.current?.querySelector<HTMLElement>("input, select, textarea");
                first?.focus();
            }, 50);
        }
        return () => document.removeEventListener("keydown", handleKeyDown);
    }, [open]);

    if (!open) return null;

    return (
        <div
            ref={overlayRef}
            className="fixed inset-0 bg-black/40 flex items-start justify-center z-50 pt-[10vh] px-4"
            onClick={(e) => { if (e.target === overlayRef.current) onClose(); }}
            role="dialog"
            aria-modal="true"
            aria-label={title}
        >
            <div ref={contentRef} className={`bg-white rounded-lg shadow-xl w-full ${WIDTH_MAP[width]} flex flex-col max-h-[80vh]`}>
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200 shrink-0">
                    <h3 className="text-base font-semibold text-gray-900">{title}</h3>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600 p-1 rounded" aria-label="Close">
                        <X className="size-5" />
                    </button>
                </div>

                {/* Scrollable body */}
                <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
                    {children}
                </div>

                {/* Footer */}
                {footer && (
                    <div className="flex gap-2 px-5 py-3 border-t border-gray-200 shrink-0">
                        {footer}
                    </div>
                )}
            </div>
        </div>
    );
}

/**
 * Accessible form field with proper id/htmlFor association.
 */
export function FormField({ label, id, hint, required, children }: {
    label: string;
    id: string;
    hint?: string;
    required?: boolean;
    children: React.ReactNode;
}) {
    return (
        <div>
            <label htmlFor={id} className="text-xs font-medium text-gray-700 block mb-1">
                {label}
                {required && <span className="text-red-500 ml-0.5">*</span>}
            </label>
            {children}
            {hint && <p className="text-[10px] text-gray-400 mt-0.5">{hint}</p>}
        </div>
    );
}
