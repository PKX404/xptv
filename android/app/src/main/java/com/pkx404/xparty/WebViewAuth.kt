package com.pkx404.xparty

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Caller presents the login WebView. Only explicitly allowed media hosts can hand off.
 * DRM services need a provider-issued license configuration; cookies are not a license.
 * No generic localStorage scraping or credential persistence is performed.
 */
class WebViewAuth(private val web: WebView, private val loginHosts: Set<String>, private val mediaHosts: Set<String>) : FastOpenProvider {
 override suspend fun extract(url: String): RavePlaybackInfo = withContext(Dispatchers.Main) {
  val uri = requireMediaUri(url)
  if (uri.path.orEmpty().matches(Regex(".*\\.(m3u8|mpd|mp4|webm)$", RegexOption.IGNORE_CASE))) {
   require(uri.host in mediaHosts); return@withContext media(url)
  }
  require(uri.host in loginHosts)
  val result = CompletableDeferred<RavePlaybackInfo>()
  web.webViewClient = object : WebViewClient() {
   override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.isForMainFrame && request.url.host !in loginHosts
   override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
    val target = request.url
    if (target.scheme == "https" && target.host in mediaHosts && target.path.orEmpty().matches(Regex(".*\\.(m3u8|mpd|mp4|webm)$", RegexOption.IGNORE_CASE))) {
     result.complete(media(target.toString()))
    }
    return null
   }
  }
  web.loadUrl(url)
  try { withTimeout(180_000) { result.await() } } finally { web.stopLoading(); web.webViewClient = WebViewClient() }
 }
 private fun media(url: String): RavePlaybackInfo {
  val path = android.net.Uri.parse(url).path.orEmpty()
  return RavePlaybackInfo(url, mimeType = when { path.endsWith(".m3u8", true) -> "application/x-mpegURL"; path.endsWith(".mpd", true) -> "application/dash+xml"; else -> null }, cookies = CookieManager.getInstance().getCookie(url))
 }
}
