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
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Base64
import org.json.JSONObject

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
        webView.addJavascriptInterface(MetaMaskInterface(), "AndroidMetaMask")

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()

                // ✅ Handle MetaMask deep links with app data
                if (url.startsWith("intent://") || url.startsWith("metamask://") || url.startsWith("https://metamask.app.link")) {
                    try {
                        // Don't handle these URLs here - let the JavaScript interface handle MetaMask opening
                        return false
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this@MainActivity, "MetaMask not installed", Toast.LENGTH_SHORT).show()
                        return true
                    }
                }

                // Load everything else normally inside the WebView
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectQRScannerJS()
                injectMetaMaskJS()
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
        // webView.loadUrl("http://192.168.1.10:8080/")
        // With ngrok for HTTPS
        webView.loadUrl("https://81ff-2a00-ee2-6b05-5500-6552-69a-45b4-1c21.ngrok-free.app")
    }

    private fun injectQRScannerJS() {
        val jsCode = """
        (function() {
            const sessionInput = document.getElementById('session-token');
            if (sessionInput) {
                const container = document.createElement('div');
                container.style.marginTop = '10px';
                container.style.marginBottom = '10px';

                const qrButton = document.createElement('button');
                qrButton.textContent = '📷 Scan QR Code';
                qrButton.type = 'button';
                qrButton.style.width = '100%';
                qrButton.style.marginTop = '8px';
                qrButton.style.padding = '10px';
                qrButton.style.backgroundColor = '#4CAF50';
                qrButton.style.color = 'white';
                qrButton.style.border = 'none';
                qrButton.style.borderRadius = '5px';
                qrButton.style.fontSize = '16px';

                qrButton.onclick = function() {
                    AndroidQRScanner.startScan();
                };

                container.appendChild(qrButton);
                sessionInput.parentNode.insertBefore(container, sessionInput.nextSibling);
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

    private fun injectMetaMaskJS() {
        val jsCode = """
        (function() {
            // Override the connectWallet function to use Android MetaMask integration
            window.originalConnectWallet = window.connectWallet;
            
            window.connectWallet = async function() {
                try {
                    if (window.AndroidMetaMask) {
                        console.log("Using Android MetaMask integration...");
                        return await connectMetaMaskAndroid();
                    } else {
                        console.log("Using original wallet connection...");
                        return await window.originalConnectWallet();
                    }
                } catch (error) {
                    console.error("Wallet connection failed:", error);
                    throw error;
                }
            };

            async function connectMetaMaskAndroid() {
                // Get current app state
                const currentState = {
                    sessionToken: document.getElementById('session-token')?.value || '',
                    studentId: authSystem?.getCurrentUserInfo()?.id || '',
                    organizer: authSystem?.getCurrentUserInfo()?.fullName || '',
                    studentMessage: document.getElementById('student-message')?.value || '',
                    currentUrl: window.location.href
                };

                // Call Android method to open MetaMask with app data
                const result = await AndroidMetaMask.openInMetaMask(JSON.stringify(currentState));
                
                if (result.success) {
                    // Create mock wallet connection for the current session
                    return {
                        provider: null,
                        signer: {
                            signMessage: async (message) => {
                                // This will be handled by MetaMask in its browser
                                return result.signature || 'mock_signature_' + Date.now();
                            },
                            getAddress: async () => {
                                return result.address || '0x0000000000000000000000000000000000000000';
                            }
                        },
                        address: result.address || '0x0000000000000000000000000000000000000000'
                    };
                } else {
                    throw new Error(result.message || 'Failed to connect to MetaMask');
                }
            }
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

    inner class MetaMaskInterface {
        @JavascriptInterface
        fun openInMetaMask(appStateJson: String): String {
            return try {
                val appState = JSONObject(appStateJson)

                // Encode the app state as URL parameters
                val baseUrl = "https://81ff-2a00-ee2-6b05-5500-6552-69a-45b4-1c21.ngrok-free.app"
                val encodedState = Base64.encodeToString(appStateJson.toByteArray(), Base64.URL_SAFE)
                val urlWithState = "$baseUrl?appState=$encodedState"

                // Create MetaMask deep link
                val metamaskUrl = "https://metamask.app.link/dapp/$urlWithState"

                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(metamaskUrl))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                runOnUiThread {
                    try {
                        startActivity(intent)
                        Toast.makeText(this@MainActivity, "Opening in MetaMask...", Toast.LENGTH_SHORT).show()
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this@MainActivity, "MetaMask not installed", Toast.LENGTH_SHORT).show()
                    }
                }

                // Return success response
                JSONObject().apply {
                    put("success", true)
                    put("message", "Opening in MetaMask browser")
                }.toString()

            } catch (e: Exception) {
                JSONObject().apply {
                    put("success", false)
                    put("message", "Error: ${e.message}")
                }.toString()
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
