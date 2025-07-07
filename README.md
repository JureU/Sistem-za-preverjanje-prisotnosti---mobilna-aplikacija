# Attendance-verification-mobile-app
Attendance verification mobile application for master's thesis.
Link to web application repository:
https://github.com/JureU/MagistrskaNaloga

Mobile app differs from web app in way that users can scan the QR the organizers provides. All other functionalities stay the same in both variations.
For working mobile app users must have MetaMask installed. The application opens MetaMask's build in browser to preform necessary operation for safe and trusted attendance verification.
After verifying attendance users can return to mobile app or continue using app in MetaMask build in browser - both variations work.

### Instructions:
For mobile app use you must use ngrok to expose Intellij web application over HTTPS.

In cdm run:
ngrok http 8080

You will see output like:
Forwarding https://abcd1234.ngrok.io -> http://localhost:8080

In MainActivity.kt change your mainUrl:
// Change URL here
// Local Host
// webView.loadUrl("http://192.168.1.10:8080/")
// With ngrok for HTTPS
private val mainUrl = "https://abcd1234.ngrok.io"

Now, your mobile app loads the secure HTTPS version!
(The URL changes every time you restart ngrok)
