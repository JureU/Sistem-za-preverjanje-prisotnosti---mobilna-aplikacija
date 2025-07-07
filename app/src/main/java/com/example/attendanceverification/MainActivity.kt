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
    private val LOCATION_REQUEST_CODE = 1001

    // Change URL here
    // Local Host
    // webView.loadUrl("http://192.168.1.10:8080/")
    // With ngrok for HTTPS
    private val mainUrl = "https://3309-2a00-ee2-6b05-5500-d012-dcf9-ef7f-cd43.ngrok-free.app"

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
                // Load everything inside the WebView
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
        webView.loadUrl(mainUrl)
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
            // Wait for the page to fully load and then override
            setTimeout(function() {
                // Change button text to "Povezi z Metamaskom"
                const verifyButton = document.getElementById('verify-attendance');
                if (verifyButton) {
                    verifyButton.textContent = '🦊 Povezi z Metamaskom';
                    
                    // Remove any existing event listeners and add our own
                    const newButton = verifyButton.cloneNode(true);
                    verifyButton.parentNode.replaceChild(newButton, verifyButton);
                    
                    newButton.onclick = function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        if (window.AndroidMetaMask) {
                            showMetaMaskModal();
                        }
                        return false;
                    };
                }
            }, 1000);

            // Also override the verifyAttendance function completely
            window.verifyAttendance = function() {
                if (window.AndroidMetaMask) {
                    showMetaMaskModal();
                } else {
                    console.log('AndroidMetaMask not available, using default behavior');
                }
            };

            function showMetaMaskModal() {
                // Create modal overlay
                const modal = document.createElement('div');
                modal.id = 'metamask-connect-modal';
                modal.style.cssText = `
                    position: fixed;
                    top: 0; left: 0; width: 100%; height: 100%;
                    background: rgba(0,0,0,0.8);
                    display: flex; justify-content: center; align-items: center;
                    z-index: 9999;
                `;

                modal.innerHTML = `
                    <div style="background: white; padding: 30px; border-radius: 15px; text-align: center; max-width: 400px; margin: 20px;">
                        <h3 style="color: #333; margin-bottom: 20px;">🦊 Povezava z MetaMask</h3>
                        
                        <p style="color: #666; margin-bottom: 15px; line-height: 1.5;">
                            To dejanje bo odprlo MetaMask vgrajeni brskalnik.
                        </p>
                        
                        <p style="color: #666; margin-bottom: 20px; line-height: 1.5;">
                            Zaradi večje varnosti se boste morali ponovno prijaviti, vendar bo vaša seja ohranjena.
                        </p>

                        <div style="display: flex; gap: 10px; justify-content: center;">
                            <button id="connect-metamask" 
                                    style="padding: 12px 24px; background: #f6851b; color: white; border: none; border-radius: 8px; font-weight: bold; cursor: pointer;">
                                Povezava
                            </button>
                            
                            <button id="cancel-metamask" 
                                    style="padding: 12px 24px; background: #ccc; color: #333; border: none; border-radius: 8px; cursor: pointer;">
                                Prekliči
                            </button>
                        </div>
                    </div>
                `;

                document.body.appendChild(modal);

                // Connect button handler
                document.getElementById('connect-metamask').onclick = function() {
                    hideMetaMaskModal();
                    connectToMetaMask();
                };

                // Cancel button handler
                document.getElementById('cancel-metamask').onclick = function() {
                    hideMetaMaskModal();
                };
            }

            function hideMetaMaskModal() {
                const modal = document.getElementById('metamask-connect-modal');
                if (modal) modal.remove();
            }

            function connectToMetaMask() {
                // Get current app state
                const currentState = {
                    sessionToken: document.getElementById('session-token')?.value || '',
                    studentId: authSystem?.getCurrentUserInfo()?.id || '',
                    organizer: authSystem?.getCurrentUserInfo()?.fullName || '',
                    studentMessage: document.getElementById('student-message')?.value || '',
                    currentUrl: window.location.href
                };

                // Call Android method to open MetaMask with app data
                const result = AndroidMetaMask.openInMetaMask(JSON.stringify(currentState));
                console.log('MetaMask opening result:', result);
            }

            // Override error handling for mobile
            window.addEventListener('error', function(e) {
                // Suppress attendance verification errors in mobile app
                if (e.message && e.message.includes('Napaka pri preverjanju prisotnosti')) {
                    e.preventDefault();
                    return false;
                }
            });

            // Override console.error for attendance verification
            const originalConsoleError = console.error;
            console.error = function(...args) {
                const message = args.join(' ');
                if (message.includes('Napaka pri preverjanju prisotnosti')) {
                    // Don't show this error in mobile app
                    return;
                }
                originalConsoleError.apply(console, args);
            };

            // Override alert for attendance verification
            const originalAlert = window.alert;
            window.alert = function(message) {
                if (message && message.includes('Napaka pri preverjanju prisotnosti')) {
                    // Don't show this alert in mobile app
                    return;
                }
                originalAlert(message);
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

    inner class MetaMaskInterface {
        @JavascriptInterface
        fun openInMetaMask(appStateJson: String): String {
            return try {
                // Encode the app state as URL parameters
                val encodedState = Base64.encodeToString(appStateJson.toByteArray(), Base64.URL_SAFE)
                val urlWithState = "$mainUrl?appState=$encodedState"

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
