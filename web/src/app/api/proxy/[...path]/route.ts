// src/app/api/proxy/[...path]/route.ts
import { NextRequest } from "next/server";

type Ctx = { params: Promise<{ path: string[] }> };

const API_BASE_URL = (process.env.BACKEND_BASE_URL ?? process.env.API_BASE_URL ?? "http://localhost:8080").replace(/\/+$/, "");

export function GET(req: NextRequest, ctx: Ctx) {
    return forward(req, ctx, "GET");
}
export function POST(req: NextRequest, ctx: Ctx) {
    return forward(req, ctx, "POST");
}
export function PUT(req: NextRequest, ctx: Ctx) {
    return forward(req, ctx, "PUT");
}
export function DELETE(req: NextRequest, ctx: Ctx) {
    return forward(req, ctx, "DELETE");
}

type Verb = "GET" | "POST" | "PUT" | "DELETE";

async function forward(req: NextRequest, ctx: Ctx, method: Verb): Promise<Response> {
    const upstreamUrl = await buildUpstreamUrl(req, ctx);
    const headers = buildUpstreamHeaders(req);

    const hasBody = method === "POST" || method === "PUT";
    const body = hasBody ? await req.arrayBuffer() : undefined;

    const upstream = await fetch(upstreamUrl, {
        method,
        headers,
        body: body?.byteLength ? body : undefined,
        redirect: "manual",
        cache: "no-store",
    });

    return buildDownstreamResponse(upstream);
}

async function buildUpstreamUrl(req: NextRequest, ctx: Ctx): Promise<string> {
    const { path } = await ctx.params;
    if (!path?.length) throw new Error("Proxy path is required");

    const relative = path.join("/").replace(/^\/+/, "");
    const upstreamPath = relative.startsWith("api/") ? relative : `api/${relative}`;

    return `${API_BASE_URL}/${upstreamPath}${req.nextUrl.search}`;
}

function buildUpstreamHeaders(req: NextRequest): Headers {
    const h = new Headers();

    const cookie = req.headers.get("cookie");
    if (cookie) h.set("cookie", cookie);

    const contentType = req.headers.get("content-type");
    if (contentType) h.set("content-type", contentType);

    const accept = req.headers.get("accept");
    if (accept) h.set("accept", accept);

    const xsrfFromHeader =
        req.headers.get("x-xsrf-token") ??
        req.headers.get("X-XSRF-TOKEN");

    const xsrfCookie = req.cookies.get("XSRF-TOKEN")?.value;
    const xsrf = xsrfFromHeader ?? xsrfCookie;
    if (xsrf) h.set("X-XSRF-TOKEN", xsrf);

    const incomingAuth = req.headers.get("authorization");
    if (incomingAuth) {
        h.set("authorization", incomingAuth);
    } else {
        const bearer = req.cookies.get("access_token")?.value;
        if (bearer) h.set("authorization", `Bearer ${bearer}`);
    }

    const proto = req.nextUrl.protocol.replace(":", "");
    const host = req.headers.get("host") ?? "";
    const xff = req.headers.get("x-forwarded-for") ?? "";
    h.set("x-forwarded-proto", proto);
    h.set("x-forwarded-host", host);
    if (xff) h.set("x-forwarded-for", xff);

    const ua = req.headers.get("user-agent");
    if (ua) h.set("user-agent", ua);

    h.delete("accept-encoding");

    return h;
}

function buildDownstreamResponse(upstream: Response): Response {
    const responseHeaders = new Headers();

    upstream.headers.forEach((value, key) => {
        const k = key.toLowerCase();
        if (k === "set-cookie") return;
        if (k === "content-encoding") return;
        if (k === "content-length") return;
        responseHeaders.set(key, value);
    });

    const anyHeaders = upstream.headers as any;
    const setCookies: string[] =
        typeof anyHeaders.getSetCookie === "function" ? anyHeaders.getSetCookie() : [];

    for (const sc of setCookies) {
        responseHeaders.append("set-cookie", sc);
    }

    return new Response(upstream.body, {
        status: upstream.status,
        statusText: upstream.statusText,
        headers: responseHeaders,
    });
}
