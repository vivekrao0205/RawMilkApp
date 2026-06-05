package com.rawmilk.app

import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var googleSignInClient: GoogleSignInClient? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var isWebViewLoaded = false
    private var cachedFcmToken: String? = null // Cached native FCM token

    // Cached Google ID token to prevent race conditions during web app initialization
    private var cachedGoogleIdToken: String? = null

    // Google Sign-in Web Client ID (User can change this if needed)
    private val WEB_CLIENT_ID = "294465354146-m5f5dn7ubk3m2n1fhr6ih1ie53em041f.apps.googleusercontent.com"

    // BroadcastReceiver to receive dynamically refreshed tokens from MyFirebaseMessagingService in real-time
    private val fcmTokenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val token = intent?.getStringExtra("token")
            if (token != null) {
                FCMDiagnostics.log("fcmTokenReceiver: Received token refresh broadcast natively. Token length: ${token.length}")
                cachedFcmToken = token
                if (isWebViewLoaded) {
                    syncFcmTokenWithWeb(token)
                }
            }
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("RawMilkAuth", "googleSignInLauncher: Received Google Sign-In activity result. resultCode = ${result.resultCode}")
        val data = result.data
        if (data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account: GoogleSignInAccount? = task.getResult(ApiException::class.java)
                val googleIdToken = account?.idToken
                Log.d("RawMilkAuth", "googleSignInLauncher: Google Account selected: ${account?.email}, hasToken = ${googleIdToken != null}")
                if (googleIdToken != null) {
                    cachedGoogleIdToken = googleIdToken
                    // Sign in natively to Firebase Auth
                    Log.d("RawMilkAuth", "googleSignInLauncher: Initiating native Firebase Auth signInWithCredential...")
                    val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(googleIdToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(credential)
                        .addOnCompleteListener { authTask ->
                            if (authTask.isSuccessful) {
                                val firebaseUser = authTask.result?.user
                                Log.d("RawMilkAuth", "googleSignInLauncher: Native Firebase Auth sign-in SUCCESSFUL. Email: ${firebaseUser?.email}, UID: ${firebaseUser?.uid}")
                                runOnUiThread {
                                    sendGoogleTokenToWeb(googleIdToken)
                                }
                            } else {
                                val exception = authTask.exception
                                Log.e("RawMilkAuth", "googleSignInLauncher: Native Firebase Auth sign-in FAILED", exception)
                                runOnUiThread {
                                    showToast("Authentication failed: ${exception?.message}")
                                    sendGoogleSignInFailureToWeb("Firebase native auth failed: ${exception?.message}")
                                }
                            }
                        }
                } else {
                    Log.e("RawMilkAuth", "googleSignInLauncher: Failed to retrieve Google ID token (token was null).")
                    showToast("Failed to retrieve Google ID token.")
                    sendGoogleSignInFailureToWeb("Failed to retrieve Google ID token.")
                }
            } catch (e: ApiException) {
                Log.e("RawMilkAuth", "googleSignInLauncher: Google Sign-In failed with ApiException. Code: ${e.statusCode}", e)
                if (e.statusCode == 10) {
                    val msg = "Developer Error (code 10). Ensure SHA-1 [32:87:85:09:A7:F3:B7:A4:EB:F9:33:16:27:B0:27:2D:71:46:42:4D] is registered in your Firebase Console."
                    Log.e("RawMilkAuth", msg)
                    showToast(msg)
                    sendGoogleSignInFailureToWeb(msg)
                } else if (e.statusCode == 12501) {
                    Log.w("RawMilkAuth", "googleSignInLauncher: Sign-In cancelled by user (code 12501).")
                    sendGoogleSignInFailureToWeb("Sign-In cancelled.")
                } else {
                    showToast("Google Sign-In failed. Please try again. Code: ${e.statusCode}")
                    sendGoogleSignInFailureToWeb("Google Sign-In failed: code ${e.statusCode}")
                }
            }
        } else {
            Log.w("RawMilkAuth", "googleSignInLauncher: Sign-In cancelled or failed (result data is null).")
            sendGoogleSignInFailureToWeb("Sign-In cancelled.")
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FCMDiagnostics.log("MainActivity: App launching, executing onCreate.")

        // Configure edge-to-edge layout with transparent status & navigation bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.decorView.systemUiVisibility = 
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var flags = window.decorView.systemUiVisibility
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            window.decorView.systemUiVisibility = flags
        }
        
        // Setup Firebase natively programmatically to avoid google-services.json compilation issues
        initFirebaseNatively()
        
        // Initialize Firebase Auth state listener and WebView cookie synchronization
        initAuthStateListener()

        // Clear Service Workers and Cache folders recursively BEFORE initializing WebView to avoid file locks
        clearWebViewFilesOnDisk()

        // Setup layouts programmatically for full-screen WebView context
        setupWebViewLayout()

        // Setup native Google Sign-in client options
        initGoogleSignInOptions()

        // Perform silent sign-in sync if user was logged in previously (Issue 2)
        performSilentSignInSync()

        // Sync and push FCM Token to WebView on load
        fetchFcmTokenAndSync()

        // Handle native notification permission checks on Android 13+
        requestNotificationPermissionIfNeeded()

        // Create high-importance notification channel on startup
        createNotificationChannel()

        // Register token refresh broadcast receiver dynamically
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(fcmTokenReceiver, android.content.IntentFilter("com.rawmilk.app.FCM_TOKEN_REFRESH"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(fcmTokenReceiver, android.content.IntentFilter("com.rawmilk.app.FCM_TOKEN_REFRESH"))
        }
        FCMDiagnostics.log("MainActivity: fcmTokenReceiver registered.")
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(fcmTokenReceiver)
            Log.d("RawMilkNative", "fcmTokenReceiver dynamic receiver successfully unregistered inside onDestroy.")
        } catch (e: Exception) {
            Log.e("RawMilkNative", "Failed to unregister fcmTokenReceiver:", e)
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("RawMilkNative", "onRequestPermissionsResult: POST_NOTIFICATIONS permission GRANTED by user.")
                fetchFcmTokenAndSync()
            } else {
                Log.w("RawMilkNative", "onRequestPermissionsResult: POST_NOTIFICATIONS permission DENIED by user.")
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val rawUrl = intent?.getStringExtra("target_url") 
            ?: intent?.getStringExtra("click_action") 
            ?: intent?.getStringExtra("url")
        FCMDiagnostics.log("onNewIntent: Intercepted rawUrl='$rawUrl'")
        rawUrl?.let { action ->
            if (action.isNotEmpty()) {
                val launchUrl = if (action.startsWith("http")) action else "https://www.rawmilk.in" + (if (action.startsWith("/")) "" else "/") + action
                FCMDiagnostics.log("onNewIntent: Injecting notification launch URL into WebView: $launchUrl")
                webView.loadUrl(launchUrl)
            }
        }
    }

    private fun initFirebaseNatively() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("RawMilkNative", "Firebase App initialized manually using google-services resources.")
            } else {
                Log.d("RawMilkNative", "Firebase App already auto-initialized by google-services plugin.")
            }
        } catch (e: Exception) {
            Log.e("RawMilkNative", "Failed to initialize Firebase natively:", e)
        }
    }

    private fun setupWebViewLayout() {
        webView = WebView(this).apply {
            // Match parent full-screen layouts
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Set background color to match the splash screen background to prevent white flash
            setBackgroundColor(Color.parseColor("#F4F3ED"))

            // Consuming long clicks disables default browser context menus (Part 2)
            setOnLongClickListener { true }
            isLongClickable = false
        }

        configureWebViewSettings(webView)
        setContentView(webView)

        // Clear WebView Cache and History (safe to call after instantiation)
        try {
            webView.clearCache(true)
            webView.clearHistory()
            Log.d("RawMilkNative", "WebView HTTP Cache and History cleared natively after initialization.")
        } catch (e: Exception) {
            Log.e("RawMilkNative", "Failed to clear WebView Cache/History:", e)
        }

        // Read launchUrl injected via Gradle resources R.string.launchUrl, or target_url/click_action/url from intent
        val rawUrl = intent?.getStringExtra("target_url") 
            ?: intent?.getStringExtra("click_action") 
            ?: intent?.getStringExtra("url")
        FCMDiagnostics.log("setupWebViewLayout: Intercepted launch rawUrl='$rawUrl'")
        val launchUrl = rawUrl?.let { action ->
            if (action.isNotEmpty()) {
                if (action.startsWith("http")) action else "https://www.rawmilk.in" + (if (action.startsWith("/")) "" else "/") + action
            } else null
        } ?: getString(R.string.launchUrl)
        FCMDiagnostics.log("setupWebViewLayout: Loading URL into WebView: $launchUrl")
        webView.loadUrl(launchUrl)
    }

    private fun clearWebViewFilesOnDisk() {
        try {
            // Delete Service Worker and Cache folders recursively to prevent stale PWA caching
            val appDir = filesDir.parentFile
            if (appDir != null && appDir.exists()) {
                val webviewDir = java.io.File(appDir, "app_webview")
                if (webviewDir.exists()) {
                    deleteServiceWorkerDirs(webviewDir)
                }
            }
        } catch (e: Exception) {
            Log.e("RawMilkNative", "Failed to clear WebView Service Worker/Cache folders on disk:", e)
        }
    }

    private fun deleteServiceWorkerDirs(file: java.io.File) {
        if (file.isDirectory) {
            val name = file.name
            if (name.contains("Service Worker", ignoreCase = true) || name.equals("Cache", ignoreCase = true)) {
                Log.d("RawMilkNative", "Deleting stale cache/service worker directory: ${file.absolutePath}")
                deleteRecursive(file)
            } else {
                file.listFiles()?.forEach { child ->
                    deleteServiceWorkerDirs(child)
                }
            }
        }
    }

    private fun deleteRecursive(fileOrDirectory: java.io.File) {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        fileOrDirectory.delete()
    }

    private fun configureWebViewSettings(wv: WebView) {
        // Enable hardware acceleration for smooth rendering
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false

        // Optimize viewport and disable zooming to act like a native app shell
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false

        // Configure CookieManager to accept cookies and persist sessions
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(wv, true)
        }

        // Custom User-Agent suffix to identify Android container in React/Next.js
        val defaultUserAgent = settings.userAgentString
        settings.userAgentString = "$defaultUserAgent RawMilkAndroid"

        // Handle navigation client
        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Inject script to unregister Service Worker and delete Cache Storage (Issue 1)
                injectCacheClearAndDisableServiceWorker(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isWebViewLoaded = true
                Log.d("RawMilkNative", "onPageFinished: WebView loaded. Flushing cookies...")
                
                // Flush cookies to disk on page load to ensure session persistence
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush()
                }

                // Safely push FCM token now that JS environment is fully active
                cachedFcmToken?.let { token ->
                    Log.d("RawMilkNative", "onPageFinished: Syncing cached FCM token to WebView...")
                    syncFcmTokenWithWeb(token)
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                isWebViewLoaded = true
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                
                // Allow all local paths, subdomains, and redirects under rawmilk.in inside WebView
                if (url.startsWith("https://www.rawmilk.in") || url.startsWith("https://rawmilk.in") || url.contains("rawmilk.in")) {
                    return false
                }
                
                // Allow firebaseapp.com (used for Firebase authentication redirects/handlers)
                if (url.contains("firebaseapp.com")) {
                    return false
                }
                
                // Launch external links in custom intents
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("RawMilkAuth", "Failed to open external link: $url", e)
                }
                return true
            }
        }

        // Handle file choosers, console logs, and camera uploads
        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback = filePathCallback
                try {
                    val intent = fileChooserParams?.createIntent()
                    fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    return false
                }
                return true
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("RawMilkWebViewConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                return true
            }
        }

        // Add JavaScript Interface Bridge
        wv.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
    }

    private fun injectCacheClearAndDisableServiceWorker(view: WebView?) {
        try {
            view?.evaluateJavascript("""
                (function() {
                    // Unregister all Service Workers to prevent caching conflicts
                    if (navigator.serviceWorker) {
                        navigator.serviceWorker.getRegistrations().then(function(registrations) {
                            for (let registration of registrations) {
                                registration.unregister();
                                console.log('Successfully unregistered service worker inside WebView');
                            }
                        });
                        
                        // Disable navigator.serviceWorker to block subsequent registration attempts inside WebView
                        Object.defineProperty(navigator, 'serviceWorker', {
                            get: function() { return undefined; }
                        });
                    }
                    
                    // Clear Service Worker Cache Storage API caches to free up space/force update
                    if (window.caches) {
                        caches.keys().then(function(keys) {
                            keys.forEach(function(key) {
                                caches.delete(key);
                                console.log('Deleted Cache Storage key: ' + key);
                            });
                        });
                    }
                })();
            """.trimIndent(), null)
        } catch (e: Exception) {
            Log.e("RawMilkNative", "Failed to inject service worker cleanup script:", e)
        }
    }

    private fun initGoogleSignInOptions() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(WEB_CLIENT_ID)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
        } catch (e: Exception) {
            Log.e("RawMilkNative", "Failed to configure Google Sign-In SDK options:", e)
        }
    }

    private fun performSilentSignInSync() {
        Log.d("RawMilkAuth", "performSilentSignInSync: Initiating Google silent sign-in...")
        try {
            googleSignInClient?.silentSignIn()?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val account = task.result
                    val googleIdToken = account?.idToken
                    Log.d("RawMilkAuth", "performSilentSignInSync: Google silent sign-in successful. Token retrieved: ${googleIdToken != null}")
                    if (googleIdToken != null) {
                        cachedGoogleIdToken = googleIdToken
                        
                        // Sign in natively to Firebase Auth
                        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(googleIdToken, null)
                        FirebaseAuth.getInstance().signInWithCredential(credential)
                            .addOnCompleteListener { authTask ->
                                if (authTask.isSuccessful) {
                                    Log.d("RawMilkAuth", "performSilentSignInSync: Logged in natively to Firebase Auth via Silent Sign-In. User: ${authTask.result?.user?.email}")
                                    runOnUiThread {
                                        sendGoogleTokenToWeb(googleIdToken)
                                    }
                                } else {
                                    Log.e("RawMilkAuth", "performSilentSignInSync: Native Firebase Auth sign-in failed during silent sync", authTask.exception)
                                }
                            }
                    }
                } else {
                    Log.d("RawMilkAuth", "performSilentSignInSync: No active Google Sign-in session found or silent sign-in failed.")
                }
            }
        } catch (e: Exception) {
            Log.e("RawMilkAuth", "performSilentSignInSync: Failed to perform silent Google Sign-In sync", e)
        }
    }

    private fun triggerNativeGoogleSignIn() {
        runOnUiThread {
            googleSignInClient?.let { client ->
                // Sign out first to ensure account selection dialog always pops up
                client.signOut().addOnCompleteListener {
                    val signInIntent = client.signInIntent
                    googleSignInLauncher.launch(signInIntent)
                }
            } ?: run {
                showToast("Google Sign-In client not configured.")
            }
        }
    }

    private fun sendGoogleTokenToWeb(token: String) {
        webView.post {
            webView.evaluateJavascript("window.onGoogleSignInSuccess('$token')", null)
        }
        webView.postDelayed({
            webView.evaluateJavascript("window.onGoogleSignInSuccess('$token')", null)
        }, 500)
    }

    private fun sendGoogleSignInFailureToWeb(error: String) {
        webView.post {
            webView.evaluateJavascript("window.onGoogleSignInFailure('$error')", null)
        }
        webView.postDelayed({
            webView.evaluateJavascript("window.onGoogleSignInFailure('$error')", null)
        }, 500)
    }

    private fun fetchFcmTokenAndSync() {
        try {
            // First load the cached token from SharedPreferences if available
            val sharedPrefs = getSharedPreferences("RawMilkPrefs", Context.MODE_PRIVATE)
            val cachedToken = sharedPrefs.getString("fcm_token", null)
            if (cachedToken != null) {
                FCMDiagnostics.log("fetchFcmTokenAndSync: Found cached token in SharedPreferences. Length: ${cachedToken.length}")
                cachedFcmToken = cachedToken
                if (isWebViewLoaded) {
                    FCMDiagnostics.log("fetchFcmTokenAndSync: Syncing pre-cached token to WebView...")
                    syncFcmTokenWithWeb(cachedToken)
                }
            }

            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    FCMDiagnostics.log("ERROR: Fetching FCM registration token failed: ${task.exception?.message}")
                    return@OnCompleteListener
                }
                val token = task.result
                FCMDiagnostics.log("fetchFcmTokenAndSync: FCM token retrieved from Firebase: $token")
                cachedFcmToken = token
                
                // Save it back to SharedPreferences for consistency
                sharedPrefs.edit().putString("fcm_token", token).apply()
                
                // Only push token if WebView has completed page loading and JS is fully registered
                if (isWebViewLoaded) {
                    FCMDiagnostics.log("fetchFcmTokenAndSync: WebView loaded. Syncing fresh token to Web...")
                    syncFcmTokenWithWeb(token)
                } else {
                    FCMDiagnostics.log("fetchFcmTokenAndSync: WebView not loaded yet. Caching fresh token for onPageFinished.")
                }
            })
        } catch (e: Exception) {
            FCMDiagnostics.log("ERROR: FCM setup failed programmatically: ${e.message}")
        }
    }

    private fun syncFcmTokenWithWeb(token: String) {
        FCMDiagnostics.log("syncFcmTokenWithWeb: Pushing token to Javascript environment...")
        webView.post {
            webView.evaluateJavascript("javascript:window.onFcmTokenReceived('$token')", null)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "rawmilk_notifications"
            val channelName = "Order and Subscription Notifications"
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channels for raw milk updates, delivery, and orders"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(defaultSoundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d("RawMilkFCM", "Notification channel created/verified programmatically in MainActivity.")
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // Inner class defining WebView JavaScript API interfaces
    inner class AndroidBridge {
        @JavascriptInterface
        fun launchGoogleSignIn() {
            triggerNativeGoogleSignIn()
        }

        @JavascriptInterface
        fun getFcmToken() {
            runOnUiThread {
                requestNotificationPermissionIfNeeded()
            }
            fetchFcmTokenAndSync()
        }
        
        @JavascriptInterface
        fun postNotificationPermissionRequest() {
            requestNotificationPermissionIfNeeded()
        }

        @JavascriptInterface
        fun checkNativeAuthSync() {
            Log.d("RawMilkAuth", "checkNativeAuthSync: JS bridge requested auth sync.")
            runOnUiThread {
                cachedGoogleIdToken?.let { token ->
                    Log.d("RawMilkAuth", "checkNativeAuthSync: Sending cached Google ID token to Web (length: ${token.length}).")
                    sendGoogleTokenToWeb(token)
                } ?: run {
                    Log.d("RawMilkAuth", "checkNativeAuthSync: No cached token in memory. Triggering silent sign-in for fresh token...")
                    performSilentSignInSync()
                }
            }
        }

        @JavascriptInterface
        fun logout() {
            Log.d("RawMilkAuth", "logout: JS bridge requested logout.")
            runOnUiThread {
                try {
                    cachedGoogleIdToken = null
                    FirebaseAuth.getInstance().signOut()
                    googleSignInClient?.signOut()
                    Log.d("RawMilkAuth", "logout: Successfully logged out natively via JS Bridge request.")
                    // Inform the WebView immediately to log out on JS side
                    webView.evaluateJavascript("if (window.onNativeLogout) { window.onNativeLogout(); }", null)
                } catch (e: Exception) {
                    Log.e("RawMilkAuth", "logout: Bridge logout call failed", e)
                }
            }
        }
    }

    private fun initAuthStateListener() {
        FCMDiagnostics.log("initAuthStateListener: Initializing native Firebase AuthStateListener...")
        try {
            FirebaseAuth.getInstance().addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                FCMDiagnostics.log("initAuthStateListener: Native Firebase Auth state changed. currentUser = ${user?.email}")
                if (user != null) {
                    user.getIdToken(false).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val token = task.result?.token
                            if (token != null) {
                                FCMDiagnostics.log("initAuthStateListener: Retrieved ID Token for user ${user.email} (length: ${token.length})")
                                // Sync token as cookie in WebView
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setCookie("https://www.rawmilk.in", "puredairy_token=$token; Domain=.rawmilk.in; Path=/; Secure; SameSite=Lax")
                                cookieManager.flush()
                                FCMDiagnostics.log("initAuthStateListener: Synced puredairy_token cookie to WebView successfully.")
                            }
                        } else {
                            FCMDiagnostics.log("ERROR: Failed to retrieve native ID Token: ${task.exception?.message}")
                        }
                    }
                } else {
                    FCMDiagnostics.log("initAuthStateListener: Clearing WebView auth cookie due to logout.")
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setCookie("https://www.rawmilk.in", "puredairy_token=; Domain=.rawmilk.in; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                    cookieManager.flush()
                    // Inform the WebView immediately to log out on JS side
                    runOnUiThread {
                        webView.evaluateJavascript("if (window.onNativeLogout) { window.onNativeLogout(); }", null)
                    }
                }
            }
        } catch (e: Exception) {
            FCMDiagnostics.log("ERROR: Failed to add AuthStateListener: ${e.message}")
        }
    }
}
