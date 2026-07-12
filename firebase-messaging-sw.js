// firebase-messaging-sw.js
// 1. Compose service workers by importing sw.js caching and routing assets
try {
  importScripts('sw.js');
  console.log('[RawMilkFCM] Successfully loaded sw.js into FCM service worker context.');
} catch (e) {
  console.error('[RawMilkFCM] Failed to import sw.js caching script:', e);
}

importScripts('https://www.gstatic.com/firebasejs/10.13.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.13.0/firebase-messaging-compat.js');

// Initialize Firebase in the service worker
firebase.initializeApp({
  apiKey: "AIzaSyCuyp4F1u821wBQb8av5QI3FsLgmcXQCMg",
  authDomain: "raw-milk-1e36d.firebaseapp.com",
  projectId: "raw-milk-1e36d",
  storageBucket: "raw-milk-1e36d.firebasestorage.app",
  messagingSenderId: "294465354146",
  appId: "1:294465354146:web:13596b8eb6b2060c079afb"
});

const messaging = firebase.messaging();

// Handle background messages
messaging.onBackgroundMessage((payload) => {
  console.log('[RawMilkFCM] Received background push message payload: ', payload);
  
  // Resolve title and body from payload notification or data fields robustly
  const notificationTitle = payload.notification?.title 
    || payload.data?.title 
    || '🥛 RAW MILK Farm Ops';

  const notificationBody = payload.notification?.body 
    || payload.data?.body 
    || payload.data?.message 
    || 'New Operational Event Alert!';

  // Build the notification options object
  const notificationOptions = {
    body: notificationBody,
    icon: payload.data?.icon || '/icon-512x512.png',
    badge: payload.data?.badge || '/icon-512x512.png',
    tag: payload.data?.notificationTag || 'rawmilk-alert',
    renotify: true,
    data: {
      click_action: payload.data?.click_action || payload.data?.url || '/admin'
    }
  };

  console.log('[RawMilkFCM] Displaying background notification:', notificationTitle, notificationOptions);
  self.registration.showNotification(notificationTitle, notificationOptions);
});

// Helper to check if URL belongs to approved domain list and uses HTTPS
const isApprovedDomain = (urlStr) => {
  try {
    const url = new URL(urlStr);
    if (url.protocol !== 'https:') {
      return false;
    }
    const approvedHosts = [
      'rawmilk.in',
      'www.rawmilk.in',
      'raw-milk-1e36d.firebaseapp.com',
      'raw-milk-1e36d.web.app'
    ];
    return approvedHosts.includes(url.hostname);
  } catch (e) {
    return false;
  }
};

// Helper function to compare routes safely (ignores query params/trailing slashes)
const isUrlEquivalent = (url1, url2) => {
  try {
    const u1 = new URL(url1);
    const u2 = new URL(url2);
    // Ignore trailing slashes in pathnames
    const path1 = u1.pathname.replace(/\/$/, '');
    const path2 = u2.pathname.replace(/\/$/, '');
    return u1.origin === u2.origin && path1 === path2;
  } catch (e) {
    return url1 === url2;
  }
};

// Configure Notification Click Action Listener
self.addEventListener('notificationclick', (event) => {
  console.log('[RawMilkFCM] Notification clicked. Closing notification item.');
  event.notification.close();
  
  const data = event.notification.data;
  const clickAction = data?.click_action || data?.FCM_MSG?.data?.click_action || '/admin';
  
  // Resolve relative/absolute URL
  let targetUrl = clickAction;
  if (!targetUrl.startsWith('http://') && !targetUrl.startsWith('https://')) {
    targetUrl = new URL(clickAction, self.location.origin).href;
  } else if (targetUrl.startsWith('http://')) {
    targetUrl = 'https://' + targetUrl.substring(7);
  }

  console.log('[RawMilkFCM] Resolved redirection URL:', targetUrl);

  // Validate the resolved target URL strictly against approved domains
  if (!isApprovedDomain(targetUrl)) {
    console.warn('[RawMilkFCM] Blocked redirect to unapproved domain:', targetUrl);
    targetUrl = self.location.origin + '/';
  }

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
      // 1. Focus if an equivalent tab under the domain is already open
      for (let i = 0; i < windowClients.length; i++) {
        const client = windowClients[i];
        if (isUrlEquivalent(client.url, targetUrl) && 'focus' in client) {
          console.log('[RawMilkFCM] Found matching open window tab. Focusing tab:', client.url);
          return client.focus();
        }
      }
      
      // 2. Fallback: If any window of our site is open, navigate it
      for (let i = 0; i < windowClients.length; i++) {
        const client = windowClients[i];
        if (client.url.startsWith(self.location.origin) && 'navigate' in client) {
          console.log('[RawMilkFCM] Found active domain tab. Navigating existing tab to target route:', targetUrl);
          return client.navigate(targetUrl).then(c => c?.focus());
        }
      }

      // 3. Otherwise, open a fresh window tab
      if (clients.openWindow) {
        console.log('[RawMilkFCM] No active tabs found. Spawning fresh tab:', targetUrl);
        return clients.openWindow(targetUrl);
      }
    })
  );
});
