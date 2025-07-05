# Attendance-verification-mobile-app
Attendance verification mobile application for master's thesis.
Link to web application repository:
https://github.com/JureU/MagistrskaNaloga

Mobile app differs from web app in way that users can scan the QR the organizers provides. All other functionalities stay the same in both variations.
For working mobile app users must have MetaMask installed.

### Instructions:
For mobile app use you must use ngrok to expose Intellij web app for HTTPS

In cdm run:
ngrok http 8080

You will see output like:
Forwarding https://abcd1234.ngrok.io -> http://localhost:8080

Change your loadUrl() call in MainActivity.kt:
webView.loadUrl("https://abcd1234.ngrok.io/")

Now, your mobile app loads the secure HTTPS version!
(The URL changes every time you restart ngrok)
