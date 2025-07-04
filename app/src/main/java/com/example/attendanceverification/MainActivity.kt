package com.example.attendanceverification

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var qrScanCallback: ValueCallback<String>? = null
    private val LOCATION_REQUEST_CODE = 1001

    // Camera permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startQRScanner()
        } else {
            Toast.makeText(this, "Camera permission required for QR scanning", Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
        }

        setupWebView()
        loadWebApp()
        setupBackPressHandler()
    }

    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(true)

        webView = findViewById(R.id.webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
        }

        webView.addJavascriptInterface(QRScannerInterface(), "AndroidQRScanner")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectQRScannerJS()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    println("WebView Console: ${it.message()}")
                }
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }
        }
    }

    private fun loadWebApp() {
        // Local Host
        webView.loadUrl("http://192.168.1.10:8080/")
        // With ngrok for HTTPS
        // webView.loadUrl("http://192.168.1.10:8080/")
    }

    private fun injectQRScannerJS() {
        val jsCode = """
            (function() {
                const sessionInput = document.getElementById('session-token');
                if (sessionInput) {
                    const qrButton = document.createElement('button');
                    qrButton.textContent = '📷 Scan QR Code';
                    qrButton.type = 'button';
                    qrButton.style.marginLeft = '10px';
                    qrButton.onclick = function() {
                        AndroidQRScanner.startScan();
                    };
                    sessionInput.parentNode.insertBefore(qrButton, sessionInput.nextSibling);
                }

                window.onQRScanned = function(qrData) {
                    const sessionInput = document.getElementById('session-token');
                    if (sessionInput) {
                        sessionInput.value = qrData;
                    }
                };
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startQRScanner()
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startQRScanner() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Scan QR Code for Session")
        integrator.setCameraId(0)
        integrator.setBeepEnabled(true)
        integrator.setBarcodeImageEnabled(true)
        integrator.initiateScan()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: android.content.Intent?
    ) {
        val result: IntentResult =
            IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
            } else {
                val scannedData = result.contents
                webView.evaluateJavascript("window.onQRScanned('$scannedData');", null)
                Toast.makeText(this, "Scanned: $scannedData", Toast.LENGTH_SHORT).show()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    inner class QRScannerInterface {
        @JavascriptInterface
        fun startScan() {
            runOnUiThread {
                checkCameraPermission()
            }
        }
    }

    private fun setupBackPressHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    // Optional: Show toast on permission result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
