package com.ironkinoko.autonovel

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ironkinoko.autonovel.ui.theme.AutoNovelTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.text.toInt
import kotlin.times

fun Color.toHexString(): String {
  val r = (red * 255).toInt().toString(16).padStart(2, '0')
  val g = (green * 255).toInt().toString(16).padStart(2, '0')
  val b = (blue * 255).toInt().toString(16).padStart(2, '0')
  return "#$r$g$b"
}

fun parseColor(str: String): Color {
  val rgb = Regex("""rgb\((\d+),\s*(\d+),\s*(\d+)\)""").find(str)
  if (rgb != null) {
    val (r, g, b) = rgb.destructured
    return Color(r.toInt(), g.toInt(), b.toInt())
  }
  val rgba = Regex("""rgba\((\d+),\s*(\d+),\s*(\d+),\s*([0-9.]+)\)""").find(str)
  if (rgba != null) {
    val (r, g, b, a) = rgba.destructured
    return Color(
      r.toInt(), g.toInt(), b.toInt(), (a.toFloat() * 255).toInt()
    )
  }
  return Color.White
}

class MainActivity : ComponentActivity() {

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    var url = intent.getStringExtra("url") ?: "https://n.novelia.cc/"
    url = url.replace("https://books.fishhawk.top/", "https://n.novelia.cc/")
    setContent {
      AutoNovelTheme {
        var webView: WebView? by remember { mutableStateOf(null) }
        var color: Color by remember { mutableStateOf(Color.White) }

        LaunchedEffect(color) {
          WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            color.luminance() > 0.5
        }

        class AndroidBridge {
          @android.webkit.JavascriptInterface
          fun setBackgroundColor(rgba: String) {
            Log.d("AndroidBridge", "setBackgroundColor: $rgba")
            color = parseColor(rgba)
          }
        }
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          containerColor = color,
        ) { innerPadding ->
          AndroidView(
            onRelease = { it.destroy() },
            factory = { context ->
              WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = object : WebViewClient() {
                  override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    evaluateJavascript(
                      """
                        (function() {
                          function notifyColor() {
                            if (window.AndroidBridge && window.AndroidBridge.setBackgroundColor) {
                              let color = '';
                              let nav = document.querySelector('.n-layout-header');
                              if (nav) {
                                color = getComputedStyle(nav).getPropertyValue('--n-color');
                                window.AndroidBridge.setBackgroundColor(color);
                              } else {
                                color = window.getComputedStyle(document.body).backgroundColor;
                              }
                              window.AndroidBridge.setBackgroundColor(color);
                            }
                          }
                          // 初始通知一次
                          notifyColor();
                          // 监听 style 属性变化
                          var observer = new MutationObserver(function(mutations) {
                            document.body.addEventListener('transitionend', function() {
                              notifyColor();
                            },{ once: true });
                          });
                          observer.observe(document.body, { attributes: true, attributeFilter: ["style"] });
                        })();
                      """.trimIndent(), null
                    )
                  }
                }
                webChromeClient = object : WebChromeClient() {
                  override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message
                  ): Boolean {
                    val context = view.context
                    val newWebView = WebView(context)
                    var hasLoaded = false
                    newWebView.webViewClient = object : WebViewClient() {
                      override fun onPageStarted(
                        view: WebView?, url: String?, favicon: android.graphics.Bitmap?
                      ) {
                        super.onPageStarted(view, url, favicon)
                        if (!hasLoaded && !url.isNullOrEmpty()) {
                          hasLoaded = true
                          val intent = Intent(context, MainActivity::class.java)
                          intent.putExtra("url", url)
                          context.startActivity(intent)
                        }
                      }
                    }
                    val transport = resultMsg.obj as WebView.WebViewTransport
                    transport.webView = newWebView
                    resultMsg.sendToTarget()
                    return true
                  }
                }

                settings.apply {
                  javaScriptEnabled = true
                  domStorageEnabled = true
                  loadWithOverviewMode = true
                  useWideViewPort = true
                  setSupportMultipleWindows(true)
                }
                loadUrl(url)
                webView = this

                addJavascriptInterface(AndroidBridge(), "AndroidBridge")
              }
            },
            modifier = Modifier
              .fillMaxSize()
              .padding(top = innerPadding.calculateTopPadding())
          )

          BackHandler {
            if (webView?.canGoBack() == true) {
              webView!!.goBack()
            } else {
              finish() // 如果不能后退，则关闭应用
            }
          }
        }
      }
    }
  }
}

