/**
 * next.config.js
 * 🥛 Production-Grade Next.js Security Configuration (Strict Security Headers)
 */

const cspHeader = `
  default-src 'self';
  script-src 'self' 'unsafe-inline' 'unsafe-eval' https://www.gstatic.com https://apis.google.com https://accounts.google.com https://www.google-analytics.com;
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

const securityHeaders = [
  {
    key: 'Content-Security-Policy',
    value: cspHeader
  },
  {
    key: 'Strict-Transport-Security',
    value: 'max-age=63072000; includeSubDomains; preload'
  },
  {
    key: 'X-Frame-Options',
    value: 'DENY'
  },
  {
    key: 'X-Content-Type-Options',
    value: 'nosniff'
  },
  {
    key: 'Referrer-Policy',
    value: 'strict-origin-when-cross-origin'
  },
  {
    key: 'Permissions-Policy',
    value: 'camera=(), microphone=(), geolocation=(), interest-cohort=()'
  },
  {
    key: 'Cross-Origin-Embedder-Policy',
    value: 'require-corp'
  },
  {
    key: 'Cross-Origin-Opener-Policy',
    value: 'same-origin'
  },
  {
    key: 'Cross-Origin-Resource-Policy',
    value: 'same-origin'
  },
  {
    key: 'X-DNS-Prefetch-Control',
    value: 'on'
  }
];

module.exports = {
  reactStrictMode: true,
  poweredByHeader: false, // Security: Remove X-Powered-By header
  async headers() {
    return [
      {
        source: '/:path*',
        headers: securityHeaders,
      },
    ];
  },
};
