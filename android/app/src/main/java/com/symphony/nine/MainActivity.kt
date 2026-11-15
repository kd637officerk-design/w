package com.symphony.nine

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.View
import android.view.WindowManager

class MainActivity : Activity() {
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        window.statusBarColor = android.graphics.Color.rgb(9,9,11)
        window.navigationBarColor = android.graphics.Color.rgb(9,9,11)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 40)
        }
        web = WebView(this)
        web.setBackgroundColor(android.graphics.Color.rgb(9,9,11))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        web.addJavascriptInterface(PlayerBridge(), "AndroidPlayer")
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params?.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                }
                startActivityForResult(intent, 41)
                return true
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(bridgeScript, null)
            }
        }
        setContentView(web)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The service promotes itself to foreground immediately in onStartCommand.
            startForegroundService(Intent(this, PlaybackService::class.java))
        } else {
            startService(Intent(this, PlaybackService::class.java))
        }
        web.loadUrl("file:///android_asset/index.html")
    }

    inner class PlayerBridge {
        @JavascriptInterface fun update(title: String?, artist: String?, playing: Boolean, duration: Double, position: Double) {
            runOnUiThread {
                PlaybackService.update(this@MainActivity, title ?: "Symphony No. 9", artist ?: "Local audio", playing, duration, position)
            }
        }
        @JavascriptInterface fun chooseFiles() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(intent, 41)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41) {
            val results = mutableListOf<Uri>()
            if (resultCode == Activity.RESULT_OK && data != null) {
                data.clipData?.let { clip -> for (i in 0 until clip.itemCount) results.add(clip.getItemAt(i).uri) }
                if (results.isEmpty()) data.data?.let { results.add(it) }
            }
            fileCallback?.onReceiveValue(results.toTypedArray())
            fileCallback = null
        }
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    companion object {
        @Volatile private var current: MainActivity? = null
        fun sendMediaCommand(command: String) {
            current?.runOnUiThread {
                current?.web?.evaluateJavascript(
                    """(function(){const a=[...document.querySelectorAll('audio,video')];const el=a.find(x=>!x.paused)||a[a.length-1];if(!el)return;if('$command'==='play')el.play();else if('$command'==='pause')el.pause();else if('$command'==='next'){document.querySelector('[aria-label*=Next],button[title*=Next]')?.click()}else if('$command'==='previous'){document.querySelector('[aria-label*=Previous],button[title*=Previous]')?.click()}else if('$command'.startsWith('seek:')){el.currentTime=parseInt('$command'.split(':')[1],10)/1000}})();""", null)
            }
        }
    }

    override fun onResume() { super.onResume(); current = this }
    override fun onDestroy() { current = null; fileCallback?.onReceiveValue(null); super.onDestroy() }

    private val bridgeScript = """
      (function(){
        if(window.__nativeMediaBridgeInstalled)return; window.__nativeMediaBridgeInstalled=true;
        const bind=a=>{
          if(a.__nativeBound)return; a.__nativeBound=true;
          const send=()=>{try{
            const title=document.title||'Symphony No. 9';
            const meta=document.querySelector('meta[name="music:musician"]')?.content||'Local audio';
            AndroidPlayer.update(title,meta,!a.paused,Number(a.duration)||0,Number(a.currentTime)||0);
          }catch(e){}};
          ['play','pause','ended','loadedmetadata','timeupdate','seeked'].forEach(ev=>a.addEventListener(ev,send));
        };
        document.querySelectorAll('audio,video').forEach(bind);
        new MutationObserver(ms=>ms.forEach(m=>m.addedNodes.forEach(n=>{
          if(n.nodeType===1){if(n.matches?.('audio,video'))bind(n);n.querySelectorAll?.('audio,video').forEach(bind);}
        }))).observe(document.documentElement,{childList:true,subtree:true});
        if(navigator.mediaSession){
          ['play','pause','previoustrack','nexttrack','seekbackward','seekforward'].forEach(k=>{
            try{navigator.mediaSession.setActionHandler(k,()=>AndroidPlayer.update(document.title,'Local audio',true,0,0))}catch(e){}
          });
        }
      })();
    """.trimIndent()
}
