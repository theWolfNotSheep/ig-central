"use client";

import { useState } from "react";
import { X } from "lucide-react";

type ReasonModalProps = {
    title: string;
    description: string;
    placeholder: string;
    confirmLabel: string;
    confirmClass?: string;
    onConfirm: (reason: string) => void;
    onClose: () => void;
};

export default function ReasonModal({
    title, description, placeholder, confirmLabel, confirmClass, onConfirm, onClose,
}: ReasonModalProps) {
    const [reason, setReason] = useState("");

    const handleConfirm = () => {
        if (!reason.trim()) return;
        onConfirm(reason.trim());
    };

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onClose}>
            <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6 space-y-4" onClick={(e) => e.stopPropagation()}>
                <div className="flex items-start justify-between">
                    <div>
                        <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
                        <p className="text-sm text-gray-500 mt-0.5">{description}</p>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
                        <X className="size-5" />
                    </button>
                </div>

                <div>
                    <label className="text-xs font-medium text-gray-700 block mb-1">
                        Reason <span className="text-red-500">*</span>
                    </label>
                    <textarea
                        value={reason}
                        onChange={(e) => setReason(e.target.value)}
                        onKeyDown={(e) => { if (e.key === "Enter" && e.metaKey) handleConfirm(); }}
                        rows={3}
                        placeholder={placeholder}
                        autoFocus
                        className="w-full text-sm border border-gray-300 rounded-md px-3 py-2 resize-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                    />
                </div>

                <div className="flex gap-3 justify-end">
                    <button onClick={onClose}
                        className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">
                        Cancel
                    </button>
                    <button onClick={handleConfirm} disabled={!reason.trim()}
                        className={`px-4 py-2 text-sm font-medium text-white rounded-md disabled:opacity-50 ${
                            confirmClass ?? "bg-blue-600 hover:bg-blue-700"
                        }`}>
                        {confirmLabel}
                    </button>
                </div>
            </div>
        </div>
    );
}
