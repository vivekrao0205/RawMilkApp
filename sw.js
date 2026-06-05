/**
 * sw.js
 * 🥛 RAW MILK Realtime PWA Service Worker (Production Grade)
 * 
 * Implements a modern caching strategy:
 * 1. Network-First for all HTML pages, dynamic navigation routes (/cart, /orders, /admin, etc.),
 *    and Next.js page data (_next/data/*) to guarantee 100% real-time synchronized cart,
 *    order, and user data while providing an offline fallback shell.
 * 2. Stale-While-Revalidate for static bundles (_next/static/*), local fonts, and static assets.
 * 3. Complete cache bypass for external Firebase databases, Auth endpoints, and Next.js APIs (/api/*).
 */

const CACHE_NAME = 'rawmilk-realtime-cache-v2';

// Core static assets to cache on service worker installation
const STATIC_ASSETS = [
  '/',
  '/manifest.json',
  '/icon-512x512.png'
];

// Install Event - Pre-cache minimal shell
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      console.log('[RawMilkSW] Pre-caching core static assets...');
      return cache.addAll(STATIC_ASSETS);
    })
  );
  self.skipWaiting();
});

// Activate Event - Clean up stale caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames.map((cache) => {
          if (cache !== CACHE_NAME) {
            console.log('[RawMilkSW] Deleting deprecated service worker cache:', cache);
            return caches.delete(cache);
          }
        })
      );
    })
  );
  self.clients.claim();
});

// Fetch Interceptor - Real-time Intelligent Routing
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // 1. BYPASS Caching entirely for:
  // - Non-GET requests (e.g. POST, PUT, DELETE)
  // - API routes (/api/*)
  // - Auth redirects and handlers (/__/auth/*, firebaseapp.com)
  // - Firestore connections and Firebase Auth operations
  const isExcluded = 
    event.request.method !== 'GET' ||
    url.origin !== self.location.origin ||
    url.pathname.startsWith('/api/') ||
    url.pathname.startsWith('/__/auth/') ||
    url.hostname.includes('firebase') ||
    url.hostname.includes('firestore');

  if (isExcluded) {
    return; // Let the browser handle the network request directly without intercepting
  }

  // 2. NETWORK-FIRST Caching for Dynamic Routes & NextJS Page Data:
  // Ensures fresh cart count, live product list, admin changes, and instant checkout states.
  const isDynamicRoute = 
    event.request.mode === 'navigate' || 
    url.pathname.includes('_next/data/') || 
    !url.pathname.includes('.'); // Matches clean paths like /cart, /admin, /orders

  if (isDynamicRoute) {
    event.respondWith(
      fetch(event.request)
        .then((networkResponse) => {
          // If the network request is successful, clone and update the cache dynamically
          if (networkResponse.status === 200) {
            const responseToCache = networkResponse.clone();
            caches.open(CACHE_NAME).then((cache) => {
              cache.put(event.request, responseToCache);
            });
          }
          return networkResponse;
        })
        .catch(() => {
          console.warn('[RawMilkSW] Client offline. Serving dynamic route from cache fallback:', url.pathname);
          // If offline, try to serve from the cache fallback
          return caches.match(event.request).then((cachedResponse) => {
            if (cachedResponse) {
              return cachedResponse;
            }
            // If dynamic page has no cache match, return root/PWA shell
            if (event.request.mode === 'navigate') {
              return caches.match('/');
            }
          });
        })
    );
    return;
  }

  // 3. STALE-WHILE-REVALIDATE Caching for versioned Static Bundles:
  // Speeds up transitions by loading cached bundles instantly while updating them in the background.
  const isStaticAsset = 
    url.pathname.startsWith('/_next/static/') ||
    url.pathname.startsWith('/images/') ||
    url.pathname.startsWith('/icon-') ||
    url.pathname === '/manifest.json';

  if (isStaticAsset) {
    event.respondWith(
      caches.match(event.request).then((cachedResponse) => {
        if (cachedResponse) {
          // background sync to keep assets updated
          fetch(event.request).then((networkResponse) => {
            if (networkResponse.status === 200) {
              caches.open(CACHE_NAME).then((cache) => {
                cache.put(event.request, networkResponse);
              });
            }
          }).catch(() => {/* Suppress silent background sync errors */});
          
          return cachedResponse; // Return fast cached bundle instantly
        }
        
        // Cache miss - fetch from network, update cache, and return
        return fetch(event.request).then((networkResponse) => {
          if (networkResponse.status === 200) {
            const responseToCache = networkResponse.clone();
            caches.open(CACHE_NAME).then((cache) => {
              cache.put(event.request, responseToCache);
            });
          }
          return networkResponse;
        });
      })
    );
    return;
  }

  // 4. Default: Network only for other assets
  return;
});
