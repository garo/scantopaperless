Scan To Paperless for Android
=============================

This app uses Android native document scanning feature to scan PDF documents using camera and uploads them to [Paperless-ngx](https://docs.paperless-ngx.com/). The user interface is very barebone, but it works.

The app doesn't require any permissions as it uses the Android [native document scanner](https://android-developers.googleblog.com/2024/02/ml-kit-document-scanner-api.html).

Usage instructions
==================

1) You need a Paperless-ngx installation, which is publicly exposed to the internet. Technically you only need to expose the "/api/documents/post_document/" path where the app send POST request.
2) Create a personal API key from your Paperless-ngx: Click your user name on top-right to open "Edit Profile" page. If you haven't yet generated any api token you need to press the refresh button and then you can copy the token to your clipboard.
3) Enter your paperless installation url, eg. "https://paperless.foobar.com" and the token you just created.
4) Press "Start scanning" button to start the scanner. Once you finish scanning the app will instantly upload the scanned file and show the upload id in the app. Easy!

![screenshot](/docs/screenshot-1.png "screenshot")
