"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
    Mail, Loader2, RefreshCw, Search, Paperclip, Check,
    CheckSquare, Square, ExternalLink, X, Plus, Trash2,
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios/axios.client";
import EmptyState from "@/components/empty-state";
import { SkeletonTable } from "@/components/skeleton";

type ConnectedMailbox = {
    id: string; displayName: string; providerAccountEmail: string;
    providerAccountName: string; active: boolean; connectedAt: string;
};

type GmailMessage = {
    id: string; threadId: string; snippet: string; internalDate: number;
    from: string; subject: string; hasAttachments: boolean;
};

export default function MailboxesPage() {
    const [mailboxes, setMailboxes] = useState<ConnectedMailbox[]>([]);
    const [activeMailbox, setActiveMailbox] = useState<ConnectedMailbox | null>(null);
    const [messages, setMessages] = useState<GmailMessage[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadingMessages, setLoadingMessages] = useState(false);
    const [importing, setImporting] = useState(false);
    const [query, setQuery] = useState("");
    const [searchInput, setSearchInput] = useState("");
    const [nextPageToken, setNextPageToken] = useState("");
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const popupRef = useRef<Window | null>(null);

    // ── Load mailboxes ──────────────────────────────────

    const loadMailboxes = useCallback(async () => {
        setLoading(true);
        try {
            const { data } = await api.get("/mailboxes");
            setMailboxes(data);
            if (data.length > 0 && !activeMailbox) {
                setActiveMailbox(data[0]);
            }
        } catch {
            toast.error("Failed to load mailboxes");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { loadMailboxes(); }, [loadMailboxes]);

    // ── Load messages ───────────────────────────────────

    const loadMessages = useCallback(async (append = false) => {
        if (!activeMailbox) return;
        setLoadingMessages(true);
        try {
            const params = new URLSearchParams();
            if (query) params.set("q", query);
            if (append && nextPageToken) params.set("pageToken", nextPageToken);
            params.set("maxResults", "25");

            const { data } = await api.get(`/mailboxes/${activeMailbox.id}/messages?${params}`);
            if (append) {
                setMessages(prev => [...prev, ...(data.messages || [])]);
            } else {
                setMessages(data.messages || []);
            }
            setNextPageToken(data.nextPageToken || "");
        } catch {
            toast.error("Failed to load messages");
        } finally {
            setLoadingMessages(false);
        }
    }, [activeMailbox, query, nextPageToken]);

    useEffect(() => {
        if (activeMailbox) {
            setMessages([]);
            setSelected(new Set());
            loadMessages();
        }
    }, [activeMailbox, query]);

    // ── OAuth connect ───────────────────────────────────

    const connectGmail = async () => {
        try {
            const { data } = await api.get("/mailboxes/gmail/auth-url");
            popupRef.current = window.open(data.url, "gmail-oauth",
                "width=500,height=600,scrollbars=yes");
        } catch {
            toast.error("Failed to get authorization URL");
        }
    };

    useEffect(() => {
        const handler = (e: MessageEvent) => {
            if (e.data?.type === "gmail-connected") {
                toast.success("Gmail connected successfully");
                loadMailboxes();
            } else if (e.data?.type === "gmail-error") {
                toast.error("Gmail connection failed");
            }
        };
        window.addEventListener("message", handler);
        return () => window.removeEventListener("message", handler);
    }, [loadMailboxes]);

    // ── Import ──────────────────────────────────────────

    const importSelected = async () => {
        if (selected.size === 0 || !activeMailbox) return;
        setImporting(true);
        try {
            const { data } = await api.post(`/mailboxes/${activeMailbox.id}/messages/import`, {
                messageIds: Array.from(selected),
            });
            if (data.error) {
                toast.error(data.error);
            } else {
                toast.success(`Imported ${data.imported}, skipped ${data.skipped}, failed ${data.failed}`);
                setSelected(new Set());
            }
        } catch (e: any) {
            if (e?.response?.status === 429) {
                toast.error(e.response.data?.error || "Pipeline at capacity — wait for current documents to finish");
            } else {
                toast.error("Import failed");
            }
        } finally {
            setImporting(false);
        }
    };

    const importSingle = async (messageId: string) => {
        if (!activeMailbox) return;
        setImporting(true);
        try {
            const { data } = await api.post(`/mailboxes/${activeMailbox.id}/messages/import`, {
                messageIds: [messageId],
            });
            if (data.imported > 0) toast.success("Email imported");
            else if (data.skipped > 0) toast.info("Email already imported");
            else toast.error("Import failed");
        } catch {
            toast.error("Import failed");
        } finally {
            setImporting(false);
        }
    };

    // ── Selection ───────────────────────────────────────

    const toggleSelect = (id: string) => {
        setSelected(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id); else next.add(id);
            return next;
        });
    };

    const selectAll = () => {
        if (selected.size === messages.length) setSelected(new Set());
        else setSelected(new Set(messages.map(m => m.id)));
    };

    // ── Disconnect ──────────────────────────────────────

    const disconnect = async (id: string) => {
        try {
            await api.delete(`/mailboxes/${id}`);
            toast.success("Mailbox disconnected");
            if (activeMailbox?.id === id) setActiveMailbox(null);
            loadMailboxes();
        } catch {
            toast.error("Failed to disconnect");
        }
    };

    // ── Search ──────────────────────────────────────────

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        setQuery(searchInput);
    };

    // ── Format ──────────────────────────────────────────

    function formatDate(epoch: number) {
        if (!epoch) return "";
        const d = new Date(epoch);
        const now = new Date();
        if (d.toDateString() === now.toDateString()) {
            return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
        }
        return d.toLocaleDateString([], { month: "short", day: "numeric" });
    }

    function shortenFrom(from: string) {
        if (!from) return "";
        const match = from.match(/^"?([^"<]+)"?\s*</);
        return match ? match[1].trim() : from.split("@")[0];
    }

    // ── Render ──────────────────────────────────────────

    return (
        <div className="flex h-[calc(100vh-4rem)] bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
            {/* Left: Mailbox selector */}
            <div className="w-56 border-r border-gray-200 flex flex-col shrink-0">
                <div className="p-3 border-b border-gray-200 flex items-center justify-between">
                    <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Mailboxes</span>
                    <button onClick={connectGmail}
                        className="p-1 text-gray-400 hover:text-blue-600" title="Connect Gmail">
                        <Plus className="size-4" />
                    </button>
                </div>
                <div className="flex-1 overflow-auto">
                    {loading && mailboxes.length === 0 ? (
                        <div className="flex justify-center py-8">
                            <Loader2 className="size-5 animate-spin text-gray-300" />
                        </div>
                    ) : mailboxes.length === 0 ? (
                        <div className="p-4 text-center text-xs text-gray-400">
                            <Mail className="size-8 text-gray-300 mx-auto mb-2" />
                            <p>No mailboxes connected</p>
                            <button onClick={connectGmail}
                                className="mt-2 text-blue-600 hover:text-blue-700 font-medium">
                                Connect Gmail
                            </button>
                        </div>
                    ) : (
                        mailboxes.map(m => (
                            <div key={m.id} className="flex items-center group">
                                <button
                                    onClick={() => setActiveMailbox(m)}
                                    className={`flex-1 text-left px-3 py-2 text-sm transition-colors ${
                                        activeMailbox?.id === m.id
                                            ? "bg-blue-50 text-blue-700"
                                            : "text-gray-700 hover:bg-gray-50"
                                    }`}>
                                    <div className="flex items-center gap-2">
                                        <Mail className={`size-4 shrink-0 ${activeMailbox?.id === m.id ? "text-blue-500" : "text-gray-400"}`} />
                                        <div className="min-w-0 flex-1">
                                            <div className="text-xs font-medium truncate">{m.providerAccountEmail}</div>
                                            <div className="text-[10px] text-gray-400 truncate">{m.displayName}</div>
                                        </div>
                                    </div>
                                </button>
                                <button onClick={() => disconnect(m.id)}
                                    className="p-1 text-gray-300 hover:text-red-500 opacity-0 group-hover:opacity-100 mr-1"
                                    title="Disconnect">
                                    <X className="size-3" />
                                </button>
                            </div>
                        ))
                    )}
                </div>
            </div>

            {/* Right: Messages */}
            <div className="flex-1 flex flex-col min-w-0">
                {/* Search bar */}
                <div className="px-4 py-2 border-b border-gray-200 flex items-center gap-2">
                    <form onSubmit={handleSearch} className="flex-1 flex items-center gap-2">
                        <div className="flex-1 relative">
                            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 size-4 text-gray-400" />
                            <input
                                type="text"
                                value={searchInput}
                                onChange={e => setSearchInput(e.target.value)}
                                placeholder="Search emails... (e.g. from:john subject:invoice)"
                                className="w-full pl-8 pr-3 py-1.5 text-sm border border-gray-300 rounded-md focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
                            />
                        </div>
                        <button type="button"
                            onClick={() => {
                                const has = searchInput.includes("has:attachment");
                                const next = has ? searchInput.replace(/\s*has:attachment\s*/g, " ").trim() : (searchInput + " has:attachment").trim();
                                setSearchInput(next);
                                setQuery(next);
                            }}
                            className={`inline-flex items-center gap-1 px-2.5 py-1.5 text-sm border rounded-md whitespace-nowrap ${
                                searchInput.includes("has:attachment")
                                    ? "bg-blue-50 border-blue-300 text-blue-700"
                                    : "border-gray-300 text-gray-600 hover:bg-gray-50"
                            }`}
                            title="Filter to emails with attachments">
                            <Paperclip className="size-3.5" /> Attachments
                        </button>
                        <button type="submit" className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700">
                            Search
                        </button>
                    </form>
                    <div className="flex items-center gap-1">
                        {selected.size > 0 && (
                            <button onClick={importSelected} disabled={importing}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm bg-green-600 text-white rounded-md hover:bg-green-700 disabled:opacity-50">
                                {importing ? <Loader2 className="size-3 animate-spin" /> : <Check className="size-3" />}
                                Import {selected.size}
                            </button>
                        )}
                        <button onClick={() => loadMessages()} disabled={loadingMessages}
                            className="p-1.5 text-gray-400 hover:text-gray-600" title="Refresh">
                            <RefreshCw className={`size-4 ${loadingMessages ? "animate-spin" : ""}`} />
                        </button>
                    </div>
                </div>

                {/* Message list */}
                <div className="flex-1 overflow-auto">
                    {!activeMailbox ? (
                        <EmptyState
                            icon={Mail}
                            title="Select a mailbox"
                            description="Connect a Gmail account to browse and import emails for classification."
                            action="Connect Gmail"
                            onAction={connectGmail}
                        />
                    ) : loadingMessages && messages.length === 0 ? (
                        <SkeletonTable rows={10} cols={4} />
                    ) : messages.length === 0 ? (
                        <EmptyState
                            icon={Mail}
                            title="No messages found"
                            description={query ? `No results for "${query}". Try a different search.` : "This mailbox is empty."}
                        />
                    ) : (
                        <>
                            <table className="w-full text-sm">
                                <thead className="sticky top-0 bg-gray-50 z-10">
                                    <tr className="border-b border-gray-200">
                                        <th className="w-10 px-3 py-2">
                                            <button onClick={selectAll} aria-label="Select all">
                                                {selected.size === messages.length && messages.length > 0
                                                    ? <CheckSquare className="size-4 text-blue-600" />
                                                    : <Square className="size-4 text-gray-400" />}
                                            </button>
                                        </th>
                                        <th className="text-left px-3 py-2 font-medium text-gray-600 w-44">From</th>
                                        <th className="text-left px-3 py-2 font-medium text-gray-600">Subject</th>
                                        <th className="text-left px-3 py-2 font-medium text-gray-600 w-16">Date</th>
                                        <th className="w-20"></th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {messages.map(msg => (
                                        <tr key={msg.id}
                                            className={`hover:bg-gray-50 transition-colors ${selected.has(msg.id) ? "bg-blue-50" : ""}`}>
                                            <td className="px-3 py-2">
                                                <button onClick={() => toggleSelect(msg.id)}>
                                                    {selected.has(msg.id)
                                                        ? <CheckSquare className="size-4 text-blue-600" />
                                                        : <Square className="size-4 text-gray-400" />}
                                                </button>
                                            </td>
                                            <td className="px-3 py-2">
                                                <span className="text-sm font-medium text-gray-900 truncate block max-w-[10rem]">
                                                    {shortenFrom(msg.from)}
                                                </span>
                                            </td>
                                            <td className="px-3 py-2">
                                                <div className="flex items-center gap-1.5 min-w-0">
                                                    <span className="text-sm text-gray-900 truncate">{msg.subject || "(no subject)"}</span>
                                                    {msg.hasAttachments && <Paperclip className="size-3 text-gray-400 shrink-0" />}
                                                </div>
                                                <div className="text-xs text-gray-400 truncate mt-0.5">{msg.snippet}</div>
                                            </td>
                                            <td className="px-3 py-2 text-xs text-gray-500 whitespace-nowrap">
                                                {formatDate(msg.internalDate)}
                                            </td>
                                            <td className="px-3 py-2">
                                                <div className="flex items-center gap-1">
                                                    <button onClick={() => importSingle(msg.id)} disabled={importing}
                                                        className="px-2 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50">
                                                        Import
                                                    </button>
                                                    <a href={`https://mail.google.com/mail/u/0/#inbox/${msg.id}`}
                                                        target="_blank" rel="noopener noreferrer"
                                                        className="p-1 text-gray-300 hover:text-gray-500">
                                                        <ExternalLink className="size-3" />
                                                    </a>
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                            {nextPageToken && (
                                <div className="p-3 text-center border-t border-gray-200">
                                    <button onClick={() => loadMessages(true)} disabled={loadingMessages}
                                        className="text-sm text-blue-600 hover:text-blue-700 font-medium disabled:opacity-50">
                                        {loadingMessages ? "Loading..." : "Load more"}
                                    </button>
                                </div>
                            )}
                        </>
                    )}
                </div>
            </div>
        </div>
    );
}
