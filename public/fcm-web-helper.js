/**
 * fcm-web-helper.js
 * 🥛 RawMilk FCM Push Notification Integration Helper (Production Grade)
 * 
 * This client-side helper manages FCM push registration and token synchronization.
 * It automatically detects if the user is inside the Native Android App (TWA/WebView wrapper)
 * or a standard Web Browser client, preventing duplicate notifications and caching conflicts.
 */

const FCM_HELPER_CONFIG = {
  // Replace with your actual Firebase VAPID key for web push notifications
  vapidKey: "YOUR_FIREBASE_PUBLIC_VAPID_KEY", 
  debug: true
};

function fcmLog(...args) {
  if (FCM_HELPER_CONFIG.debug) {
    console.log('[RawMilkWebFCM]', ...args);
  }
}

/**
 * Check if the current context is running inside the Android Native container.
 */
function isRunningInAndroidApp() {
  if (typeof window === 'undefined') return false;
  return !!(
    window.AndroidBridge || 
    navigator.userAgent.includes('RawMilkAndroid')
  );
}

/**
 * Initialize FCM Push Notifications based on client environment.
 * Call this function inside your web application's root entry point
 * (e.g., in React's useEffect, Next.js _app.js, or window.onload).
 * 
 * @param {Object} options callbacks for token events
 * @param {Function} options.onTokenSync callback when a valid token is retrieved/synchronized
 * @param {Function} options.onNotificationReceived callback for foreground notifications (web only)
 */
function initializeRawMilkPush(options = {}) {
  const { onTokenSync, onNotificationReceived } = options;

  if (typeof window === 'undefined') return;

  fcmLog('Initializing FCM helper...');

  if (isRunningInAndroidApp()) {
    fcmLog('Context detected: Running inside NATIVE ANDROID APP.');
    fcmLog('Disabling web service worker registration in app to prevent duplicate alerts.');

    // 1. Hook the global Android token callback.
    // The Android app calls this method programmatically via evaluateJavascript.
    window.onFcmTokenReceived = function(token) {
      fcmLog('Successfully received Native FCM Token from AndroidBridge:', token.substring(0, 10) + '...');
      
      // Save it in localStorage for fast local reads
      localStorage.setItem('rawmilk_fcm_token', token);
      localStorage.setItem('rawmilk_fcm_type', 'android_native');

      if (typeof onTokenSync === 'function') {
        onTokenSync(token, 'android_native');
      }
    };

    // 2. Request token sync from the Android container.
    // Calling getFcmToken() triggers notification permissions on Android 13+ if needed
    // and syncs the token via window.onFcmTokenReceived instantly.
    if (window.AndroidBridge && typeof window.AndroidBridge.getFcmToken === 'function') {
      fcmLog('Triggering AndroidBridge.getFcmToken() to sync token...');
      window.AndroidBridge.getFcmToken();
    } else {
      fcmLog('Warning: AndroidBridge is not immediately available. The token will be pushed on Page Finished.');
    }

    // 3. Setup dynamic auth sync listener for Google Sign-In
    if (window.AndroidBridge && typeof window.AndroidBridge.checkNativeAuthSync === 'function') {
      fcmLog('Triggering native auth sync check...');
      window.AndroidBridge.checkNativeAuthSync();
    }

  } else {
    fcmLog('Context detected: Running inside STANDARD WEB BROWSER.');
    fcmLog('Registering web-based service worker for background notifications...');

    // Register firebase-messaging-sw.js from root scope
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('/firebase-messaging-sw.js', { scope: '/' })
        .then((registration) => {
          fcmLog('Web Service Worker successfully registered at root. Scope:', registration.scope);
          
          // Request permissions and fetch Web FCM token
          requestWebPushPermissions(registration, onTokenSync);
        })
        .catch((error) => {
          console.error('[RawMilkWebFCM] Service Worker registration failed:', error);
        });
    }

    // Handle foreground web push notifications via the Web Firebase SDK if integrated
    // (Usually configured inside your Firebase client initialization script)
  }
}

/**
 * Request Notification Permissions in Web Browser and generate Web FCM token.
 */
function requestWebPushPermissions(registration, onTokenSync) {
  if (typeof Notification === 'undefined') {
    fcmLog('Notification API is not supported by this browser.');
    return;
  }

  fcmLog('Requesting notification permissions...');
  Notification.requestPermission().then((permission) => {
    fcmLog('Web browser notification permission status:', permission);
    if (permission === 'granted') {
      // Fetch web push token using Firebase Client SDK
      // Ensure the Firebase JS SDK is loaded or import it.
      // Example of fetching the token via standard Firebase JS:
      /*
      import { getMessaging, getToken } from "firebase/messaging";
      const messaging = getMessaging();
      getToken(messaging, { serviceWorkerRegistration: registration, vapidKey: FCM_HELPER_CONFIG.vapidKey })
        .then((currentToken) => {
          if (currentToken) {
            fcmLog('Web FCM token generated:', currentToken);
            localStorage.setItem('rawmilk_fcm_token', currentToken);
            localStorage.setItem('rawmilk_fcm_type', 'web_browser');
            if (onTokenSync) onTokenSync(currentToken, 'web_browser');
          } else {
            fcmLog('No registration token available. Request permission to generate one.');
          }
        })
        .catch((err) => {
          console.error('[RawMilkWebFCM] An error occurred while retrieving token:', err);
        });
      */
    }
  });
}

// Export functions for ES6 modules (React/Next.js) or bind to window for vanilla JS
if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    initializeRawMilkPush,
    isRunningInAndroidApp
  };
} else {
  window.initializeRawMilkPush = initializeRawMilkPush;
  window.isRunningInAndroidApp = isRunningInAndroidApp;
}
