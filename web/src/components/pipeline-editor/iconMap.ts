/**
 * Static map from icon name strings to lucide-react icon components.
 * NodeTypeDefinition.iconName stores one of these keys.
 *
 * To add a new icon: import it from lucide-react and add the entry.
 */
import {
    Zap, FileText, Scan, Brain, GitBranch, Shield,
    UserCheck, Bell, AlertTriangle,
    Fingerprint, ListChecks, Scissors, Search, Cpu, Cog,
    Globe, Webhook, Database, Code, Terminal, Workflow,
    type LucideIcon,
} from "lucide-react";

export const ICON_MAP: Record<string, LucideIcon> = {
    Zap,
    FileText,
    Scan,
    Brain,
    GitBranch,
    Shield,
    UserCheck,
    Bell,
    AlertTriangle,
    Fingerprint,
    ListChecks,
    Scissors,
    Search,
    Cpu,
    Cog,
    Globe,
    Webhook,
    Database,
    Code,
    Terminal,
    Workflow,
};

export const DEFAULT_ICON = Cog;

export function resolveIcon(name: string | undefined): LucideIcon {
    return (name && ICON_MAP[name]) || DEFAULT_ICON;
}
