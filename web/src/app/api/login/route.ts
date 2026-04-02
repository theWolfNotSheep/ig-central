import { NextResponse } from "next/server";
import serverApi from "@/lib/axios/axios.server";

type CsrfTokenResponse = {
    token: string;
    headerName?: string;
    parameterName?: string;
};

type SpringLoginResponse = {
    username: string;
    roles: string[];
    jwtToken: string;
    accountType?: string;
    emailVerified?: boolean;
};

function setCookieArrayToCookieHeader(setCookie?: string[] | string): string {
    if (!setCookie) return "";
    const arr = Array.isArray(setCookie) ? setCookie : [setCookie];
    return arr.map((c) => c.split(";")[0]).join("; ");
}

export async function POST(req: Request) {
    const { username, password } = (await req.json()) as { username: string; password: string };

    // 1) Fetch CSRF token from Spring
    const csrfRes = await serverApi.get<CsrfTokenResponse>("/api/csrf-token", {
        headers: { Accept: "application/json" },
        validateStatus: () => true,
    });

    if (csrfRes.status < 200 || csrfRes.status >= 300) {
        return NextResponse.json(
            { message: "CSRF token fetch failed", springStatus: csrfRes.status },
            { status: 500 }
        );
    }
    const csrfToken = csrfRes.data?.token;
    if (!csrfToken) {
        return NextResponse.json({ message: "CSRF token missing from /api/csrf-token" }, { status: 500 });
    }

    const springCookieHeader = setCookieArrayToCookieHeader(csrfRes.headers["set-cookie"]);

    // 2) POST login to Spring
    const loginRes = await serverApi.post<SpringLoginResponse>(
        "/api/auth/login",
        { username, password },
        {
            headers: {
                Accept: "application/json",
                "Content-Type": "application/json",
                ...(springCookieHeader ? { Cookie: springCookieHeader } : {}),
                "X-XSRF-TOKEN": csrfToken,
            },
            validateStatus: () => true,
        }
    );

    if (loginRes.status < 200 || loginRes.status >= 300) {
        return NextResponse.json(
            { message: "Spring login failed", springStatus: loginRes.status, springBody: loginRes.data },
            { status: loginRes.status }
        );
    }

    const data = loginRes.data;

    // 3) Set HttpOnly cookie with JWT
    const res = NextResponse.json({
        username: data.username,
        roles: data.roles ?? [],
        accountType: data.accountType,
        emailVerified: data.emailVerified,
    });

    const isProduction = process.env.NODE_ENV === "production";

    res.cookies.set("access_token", data.jwtToken, {
        httpOnly: true,
        sameSite: "lax",
        secure: isProduction,
        path: "/",
        maxAge: 60 * 60 * 48,
    });

    return res;
}
