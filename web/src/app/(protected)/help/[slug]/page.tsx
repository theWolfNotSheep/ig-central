"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { useState } from "react";
import {
    Rocket, BookOpen, FileSearch, Settings, HelpCircle, Database, Brain,
    ShieldAlert, Shield, Users, Workflow, Activity, Search, Cloud, Mail,
    ChevronLeft, ChevronRight, AlertTriangle, Lightbulb, Info, ChevronDown,
    ThumbsUp, ThumbsDown,
} from "lucide-react";
import { helpSections, helpCategories, type HelpBlock } from "../help-content";

const ICONS: Record<string, React.ElementType> = {
    Rocket, BookOpen, FileSearch, Settings, Search, Database, Brain, ShieldAlert,
    Shield, Users, Workflow, Activity, HelpCircle, Cloud, Mail, Blocks: Database,
};

export default function HelpArticlePage() {
    const { slug } = useParams<{ slug: string }>();
    const section = helpSections.find(s => s.slug === slug);

    if (!section) {
        return (
            <div className="max-w-3xl mx-auto px-8 py-12 text-center">
                <HelpCircle className="size-12 text-gray-200 mx-auto mb-4" />
                <h1 className="text-lg font-semibold text-gray-700">Article not found</h1>
                <p className="text-sm text-gray-400 mt-1">The help page you're looking for doesn't exist.</p>
                <Link href="/help" className="text-sm text-blue-600 hover:text-blue-800 mt-4 inline-block">Back to Help Centre</Link>
            </div>
        );
    }

    const Icon = ICONS[section.icon] ?? HelpCircle;
    const category = helpCategories.find(c => c.key === section.category);

    // Find prev/next articles
    const idx = helpSections.findIndex(s => s.slug === slug);
    const prev = idx > 0 ? helpSections[idx - 1] : null;
    const next = idx < helpSections.length - 1 ? helpSections[idx + 1] : null;

    return (
        <div className="max-w-3xl mx-auto px-8 py-8">
            {/* Breadcrumb */}
            <div className="flex items-center gap-2 text-xs text-gray-400 mb-6">
                <Link href="/help" className="hover:text-blue-600">Help Centre</Link>
                <ChevronRight className="size-3" />
                <span className="text-gray-500">{category?.label}</span>
                <ChevronRight className="size-3" />
                <span className="text-gray-700 font-medium">{section.title}</span>
            </div>

            {/* Header */}
            <div className="flex items-start gap-4 mb-8">
                <div className="size-12 rounded-xl bg-blue-50 flex items-center justify-center shrink-0">
                    <Icon className="size-6 text-blue-600" />
                </div>
                <div>
                    <h1 className="text-xl font-bold text-gray-900">{section.title}</h1>
                    <p className="text-sm text-gray-500 mt-1">{section.summary}</p>
                </div>
            </div>

            {/* Content blocks */}
            <div className="space-y-5">
                {section.content.map((block, i) => (
                    <ContentBlock key={i} block={block} />
                ))}
            </div>

            {/* Feedback */}
            <FeedbackWidget slug={slug} />

            {/* Prev/Next navigation */}
            <div className="flex items-center justify-between mt-10 pt-6 border-t border-gray-200">
                {prev ? (
                    <Link href={`/help/${prev.slug}`} className="flex items-center gap-2 text-sm text-gray-600 hover:text-blue-700">
                        <ChevronLeft className="size-4" />
                        <div>
                            <div className="text-[10px] text-gray-400">Previous</div>
                            <div className="font-medium">{prev.title}</div>
                        </div>
                    </Link>
                ) : <div />}
                {next ? (
                    <Link href={`/help/${next.slug}`} className="flex items-center gap-2 text-sm text-gray-600 hover:text-blue-700 text-right">
                        <div>
                            <div className="text-[10px] text-gray-400">Next</div>
                            <div className="font-medium">{next.title}</div>
                        </div>
                        <ChevronRight className="size-4" />
                    </Link>
                ) : <div />}
            </div>
        </div>
    );
}

function ContentBlock({ block }: { block: HelpBlock }) {
    switch (block.type) {
        case "paragraph":
            return <p className="text-sm text-gray-700 leading-relaxed" dangerouslySetInnerHTML={{ __html: formatInline(block.text) }} />;

        case "heading":
            return <h2 className="text-base font-semibold text-gray-900 mt-8 mb-2 first:mt-0">{block.text}</h2>;

        case "steps":
            return (
                <ol className="space-y-2">
                    {block.items.map((item, i) => (
                        <li key={i} className="flex items-start gap-3">
                            <span className="size-6 rounded-full bg-blue-100 text-blue-700 text-xs font-bold flex items-center justify-center shrink-0 mt-0.5">
                                {i + 1}
                            </span>
                            <span className="text-sm text-gray-700 leading-relaxed" dangerouslySetInnerHTML={{ __html: formatInline(item) }} />
                        </li>
                    ))}
                </ol>
            );

        case "list":
            return (
                <ul className="space-y-1.5 pl-1">
                    {block.items.map((item, i) => (
                        <li key={i} className="flex items-start gap-2 text-sm text-gray-700">
                            <span className="text-blue-400 mt-1 shrink-0">-</span>
                            <span className="leading-relaxed" dangerouslySetInnerHTML={{ __html: formatInline(item) }} />
                        </li>
                    ))}
                </ul>
            );

        case "tip":
            return (
                <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 flex items-start gap-3">
                    <Lightbulb className="size-5 text-blue-500 shrink-0 mt-0.5" />
                    <p className="text-sm text-blue-800 leading-relaxed" dangerouslySetInnerHTML={{ __html: formatInline(block.text) }} />
                </div>
            );

        case "warning":
            return (
                <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 flex items-start gap-3">
                    <AlertTriangle className="size-5 text-amber-500 shrink-0 mt-0.5" />
                    <p className="text-sm text-amber-800 leading-relaxed" dangerouslySetInnerHTML={{ __html: formatInline(block.text) }} />
                </div>
            );

        case "table":
            return (
                <div className="overflow-x-auto border border-gray-200 rounded-lg">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="bg-gray-50 border-b border-gray-200">
                                {block.headers.map((h, i) => (
                                    <th key={i} className="text-left px-4 py-2 font-semibold text-gray-700">{h}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {block.rows.map((row, i) => (
                                <tr key={i}>
                                    {row.map((cell, j) => (
                                        <td key={j} className="px-4 py-2 text-gray-600" dangerouslySetInnerHTML={{ __html: formatInline(cell) }} />
                                    ))}
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            );

        case "faq":
            return <FaqItem question={block.question} answer={block.answer} />;

        default:
            return null;
    }
}

function FaqItem({ question, answer }: { question: string; answer: string }) {
    const [open, setOpen] = useState(false);
    return (
        <div className="border border-gray-200 rounded-lg overflow-hidden">
            <button onClick={() => setOpen(!open)}
                className="w-full flex items-center justify-between px-4 py-3 text-left text-sm font-medium text-gray-900 hover:bg-gray-50">
                {question}
                <ChevronDown className={`size-4 text-gray-400 transition-transform ${open ? "rotate-180" : ""}`} />
            </button>
            {open && (
                <div className="px-4 pb-3 text-sm text-gray-600 border-t border-gray-100 pt-2" dangerouslySetInnerHTML={{ __html: formatInline(answer) }} />
            )}
        </div>
    );
}

function FeedbackWidget({ slug }: { slug: string }) {
    const [feedback, setFeedback] = useState<"up" | "down" | null>(null);

    return (
        <div className="mt-10 pt-6 border-t border-gray-200 flex items-center gap-4">
            <span className="text-sm text-gray-500">Was this helpful?</span>
            <button onClick={() => setFeedback("up")}
                className={`p-2 rounded-lg border transition-colors ${feedback === "up" ? "bg-green-50 border-green-300 text-green-700" : "border-gray-200 text-gray-400 hover:text-green-600 hover:border-green-200"}`}
                aria-label="Yes, this was helpful">
                <ThumbsUp className="size-4" />
            </button>
            <button onClick={() => setFeedback("down")}
                className={`p-2 rounded-lg border transition-colors ${feedback === "down" ? "bg-red-50 border-red-300 text-red-700" : "border-gray-200 text-gray-400 hover:text-red-600 hover:border-red-200"}`}
                aria-label="No, this wasn't helpful">
                <ThumbsDown className="size-4" />
            </button>
            {feedback && <span className="text-xs text-gray-400">Thanks for your feedback!</span>}
        </div>
    );
}

/** Format **bold** and `code` in inline text */
function formatInline(text: string): string {
    return text
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/`(.*?)`/g, '<code class="px-1 py-0.5 bg-gray-100 text-gray-800 rounded text-xs">$1</code>');
}
