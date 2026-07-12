import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

/**
 * Next.js Edge Middleware for Security Headers & CSP Nonce Generation
 */
export function middleware(request: NextRequest) {
  // Generate a cryptographically secure random nonce
  // In Next.js Edge runtime, we can use crypto.randomUUID()
  let nonce = '';
  try {
    const uuid = crypto.randomUUID();
    nonce = btoa(uuid);
  } catch (e) {
    // Fallback if UUID generation fails
    nonce = btoa(Math.random().toString(36).substring(2) + Date.now().toString(36));
  }
  
  // Strict Content-Security-Policy incorporating the dynamic nonce
  const cspHeader = `
    default-src 'self';
    script-src 'self' 'nonce-${nonce}' 'strict-dynamic' https://www.gstatic.com https://apis.google.com https://accounts.google.com;
    style-src 'self' 'unsafe-inline' https://fonts.googleapis.com;
    img-src 'self' data: https://www.rawmilk.in https://lh3.googleusercontent.com;
    font-src 'self' data: https://fonts.gstatic.com;
    connect-src 'self' https://*.googleapis.com https://*.firebaseapp.com https://*.firebaseio.com wss://*.firebaseio.com https://identitytoolkit.googleapis.com https://securetoken.googleapis.com;
    frame-src 'self' https://raw-milk-1e36d.firebaseapp.com https://accounts.google.com;
    object-src 'none';
    base-uri 'self';
    form-action 'self';
    frame-ancestors 'none';
  `.replace(/\s{2,}/g, ' ').trim();

  // Set the nonce in request headers so it can be read in server components
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set('x-nonce', nonce);
  requestHeaders.set('Content-Security-Policy', cspHeader);

  const response = NextResponse.next({
    request: {
      headers: requestHeaders,
    },
  });

  // Inject strict HTTP security headers into the response
  response.headers.set('Content-Security-Policy', cspHeader);
  response.headers.set('Strict-Transport-Security', 'max-age=63072000; includeSubDomains; preload');
  response.headers.set('X-Frame-Options', 'DENY');
  response.headers.set('X-Content-Type-Options', 'nosniff');
  response.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');
  response.headers.set('Permissions-Policy', 'camera=(), microphone=(), geolocation=(), interest-cohort=()');
  response.headers.set('Cross-Origin-Embedder-Policy', 'require-corp');
  response.headers.set('Cross-Origin-Opener-Policy', 'same-origin');
  response.headers.set('Cross-Origin-Resource-Policy', 'same-origin');

  return response;
}

export const config = {
  matcher: [
    /*
     * Match all request paths except for the ones starting with:
     * - api (API routes)
     * - _next/static (static files)
     * - _next/image (image optimization files)
     * - favicon.ico (favicon file)
     * - manifest.json / sw.js / fcm worker
     */
    {
      source: '/((?!api|_next/static|_next/image|favicon.ico|manifest.json|sw.js|firebase-messaging-sw.js).*)',
      missing: [
        { type: 'header', key: 'next-router-prefetch' },
        { type: 'header', key: 'purpose', value: 'prefetch' },
      ],
    },
  ],
};
