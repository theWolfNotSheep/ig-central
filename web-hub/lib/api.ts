// Resolved lazily on first API call to avoid SSR window access
let _hubUrl: string | null = null;
function getHubUrl(): string {
  if (_hubUrl !== null) return _hubUrl;
  const env = process.env.NEXT_PUBLIC_HUB_URL;
  if (env) { _hubUrl = env; return _hubUrl; }
  if (typeof window !== "undefined" && window.location.port === "3002") {
    _hubUrl = "http://localhost:8090";
  } else {
    _hubUrl = "";
  }
  return _hubUrl;
}

const CREDS_KEY = "hub_admin_credentials";

export function storeCredentials(username: string, password: string): void {
  const encoded = btoa(`${username}:${password}`);
  sessionStorage.setItem(CREDS_KEY, encoded);
}

export function getCredentials(): string | null {
  if (typeof window === "undefined") return null;
  return sessionStorage.getItem(CREDS_KEY);
}

export function clearCredentials(): void {
  sessionStorage.removeItem(CREDS_KEY);
}

export function isAuthenticated(): boolean {
  return getCredentials() !== null;
}

function getAuthHeaders(): Record<string, string> {
  const creds = getCredentials();
  if (!creds) return {};
  return { Authorization: `Basic ${creds}` };
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown
): Promise<T> {
  const url = `${getHubUrl()}${path}`;
  const headers: Record<string, string> = {
    ...getAuthHeaders(),
    "Content-Type": "application/json",
  };

  const res = await fetch(url, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401) {
    clearCredentials();
    if (typeof window !== "undefined") {
      window.location.href = "/";
    }
    throw new Error("Unauthorized");
  }

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`HTTP ${res.status}: ${text || res.statusText}`);
  }

  const text = await res.text();
  if (!text) return undefined as T;

  return JSON.parse(text) as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  delete: <T>(path: string) => request<T>("DELETE", path),
};
