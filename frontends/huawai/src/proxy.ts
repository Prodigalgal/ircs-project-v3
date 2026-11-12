import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function proxy(request: NextRequest) {
    const { pathname, search } = request.nextUrl;
    const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080';

    if (pathname.startsWith('/api/portal/') || pathname.startsWith('/api/captcha/')) {
        const targetUrl = new URL(pathname + search, backendUrl);
        return NextResponse.rewrite(targetUrl);
    }

    if (pathname.startsWith('/media/')) {
        const targetUrl = new URL(pathname + search, backendUrl);
        return NextResponse.rewrite(targetUrl);
    }

    return NextResponse.next();
}

export const config = {
    matcher: [
        '/api/portal/:path*',
        '/api/captcha/:path*',
        '/media/:path*',
    ],
};
