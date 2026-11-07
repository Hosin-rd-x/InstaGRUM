package com.instaos.app.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.instaos.app.data.WebSession

/**
 * Instagram sign-in (phase 2, audit §8 step 2).
 *
 * The user signs in inside Instagram's own page, in a WebView. The app
 * never asks for, sees, or stores a password — after sign-in it only
 * harvests the session cookies via `CookieManager.getCookie()` with the
 * full `https://www.instagram.com/` URL (HttpOnly cookies are invisible
 * to page JavaScript but visible to this native call).
 *
 * The WebView is created only after the user taps "Open Instagram login",
 * so this screen is cheap to show and safe to render in tests.
 * 2FA / checkpoint challenges happen inside the WebView itself; the app
 * only detects success (a `sessionid` appears) or failure.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: (WebSession) -> Unit,
    onContinueOffline: () -> Unit,
) {
    var showWebView by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    if (!showWebView) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                "ورود به اینستاگرام",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Sign in to Instagram",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "برای دیدن فید، اول داخل خودِ صفحهٔ اینستاگرام وارد شوید. " +
                    "رمز را فقط همان‌جا می‌زنید؛ برنامه رمز را نمی‌بیند و ذخیره نمی‌کند. " +
                    "بعد از ورود، برنامه فقط نشستِ ورود (کوکی) را نگه می‌دارد تا فید را بخواند.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "To see your feed, sign in on Instagram's own page. You type your " +
                    "password only there — the app never sees or stores it. After sign-in " +
                    "the app only keeps the login session (cookie) so it can read the feed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "فقط با اکانت تستی وارد شوید، نه اکانت اصلی.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "Use the test account only — not your main account.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showWebView = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("باز کردن صفحهٔ ورود اینستاگرام  ·  Open Instagram login") }
            OutlinedButton(
                onClick = onContinueOffline,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("بدون ورود ادامه بده  ·  Continue without signing in") }
        }
        return
    }
    LoginWebView(
        onBack = { showWebView = false },
        onHarvested = { session ->
            if (session != null) onLoginSuccess(session)
            else status = "Still signed out — finish signing in on the Instagram page, then come back."
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebView(
    onBack: () -> Unit,
    onHarvested: (WebSession?) -> Unit,
) {
    var checking by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    val harvest: () -> Unit = {
        checking = true
        val header = runCatching {
            CookieManager.getInstance().getCookie("https://www.instagram.com/")
        }.getOrNull()
        val session = WebSession.fromCookieHeader(header)
        checking = false
        if (session != null) {
            signedIn = true
            CookieManager.getInstance().flush()
            onHarvested(session)
        } else {
            onHarvested(null)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text(
                "instagram.com",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = harvest, enabled = !checking && !signedIn) {
                Text(if (checking) "Checking…" else "Check sign-in")
            }
        }
        if (signedIn) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Signed in — opening your feed…")
                }
            }
        } else {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        val webView = this
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(webView, true)
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                // Only harvest on the home page: the login page
                                // itself has no session cookie yet.
                                val uri = runCatching { android.net.Uri.parse(url.orEmpty()) }.getOrNull()
                                if (uri?.host == "www.instagram.com" &&
                                    (uri.path.isNullOrEmpty() || uri.path == "/")
                                ) {
                                    harvest()
                                }
                            }
                        }
                        loadUrl("https://www.instagram.com/accounts/login/")
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
