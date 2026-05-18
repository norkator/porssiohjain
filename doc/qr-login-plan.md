# Native Android QR Login Handoff

## Status

Spring API and `hybrid-web` support for browser QR login have been implemented in this repository. The remaining work is in the native Android app.

This document is intended to be copied to the native Android project as the implementation reference for the QR scanner and bridge integration.

## Goal

Allow a user who is already logged in to the native Android app to log in to the hosted `hybrid-web` browser version by scanning the QR code shown on the browser login page.

The QR code does not contain account UUID, account secret, or an auth token. It contains only a short-lived challenge id and scan secret. Android approves that challenge using the existing native app auth token.

## User Flow

1. User opens the browser version of `hybrid-web`.
2. User clicks `Login with QR code`.
3. Browser shows a QR code and waits for approval.
4. User opens the native Android app where they are already logged in.
5. User taps the main menu action to scan a browser login QR.
6. Android opens a native QR scanner.
7. Android scans and validates the QR payload.
8. Android calls the Spring approval endpoint using the native app's existing token.
9. Browser receives its own normal login token through its existing polling flow.

## Hybrid-Web Bridge Entry

The implemented `hybrid-web` main menu button tries this first:

```kotlin
@JavascriptInterface
fun scanQrLoginCode() {
    openQrLoginScanner()
}
```

If that method is not present, `hybrid-web` falls back to the existing screen bridge:

```kotlin
@JavascriptInterface
fun openNativeScreen(screen: String) {
    when (screen) {
        "qrLoginScanner" -> openQrLoginScanner()
    }
}
```

Recommended native implementation:

- Add `scanQrLoginCode()` if possible.
- Keep or add `openNativeScreen("qrLoginScanner")` support for compatibility.

## QR Payload

The browser QR code contains a URI in this format:

```text
porssiohjain://qr-login?challengeId=<uuid>&scanSecret=<secret>&apiBaseUrl=<encoded-api-base-url>
```

Example:

```text
porssiohjain://qr-login?challengeId=4f98aefe-8dbb-47a5-b8d8-7f432b9614a8&scanSecret=abc123&apiBaseUrl=https%3A%2F%2Fapp.porssiohjain.fi
```

Android must validate:

- URI scheme is `porssiohjain`.
- URI host is `qr-login`.
- `challengeId` exists and parses as UUID.
- `scanSecret` exists and is not blank.
- `apiBaseUrl` is optional for Android use. Prefer the native app's configured API base URL. If using the QR `apiBaseUrl`, allow only trusted production/dev hosts.

## Approval API

After scanning a valid QR payload, Android calls:

```http
POST {apiBaseUrl}/account/qr-login/challenges/{challengeId}/approve
Authorization: <existing-native-app-token>
Content-Type: application/json

{
  "scanSecret": "<scanSecret>"
}
```

Use the same raw authorization token format the native app already uses for the existing API. This backend currently expects the token value directly in the `Authorization` header, not `Bearer <token>`, unless the native app already has a wrapper that matches the API behavior.

Success response:

```json
{
  "status": "APPROVED",
  "expiresAt": "2026-05-18T12:00:00Z"
}
```

Expected failures:

- `400`: invalid scan secret or malformed request.
- `401`: missing, expired, or invalid native app token.
- `404`: challenge does not exist.
- `409`: challenge expired, already approved, consumed, or cancelled.

## Scanner Requirements

Use a native QR scanner based on a maintained library or platform stack:

- CameraX + ML Kit Barcode Scanning
- ZXing
- another established scanner already used by the native app

Scanner behavior:

- Request camera permission when needed.
- Start scanning after permission is granted.
- Stop scanning while an approval request is in progress.
- Reject non-QR or invalid payloads with a clear message.
- On approval success, show a short success state and close the scanner.
- On approval failure, show a useful failure state and allow retry or close.

## Security Rules

- Never send or store account secret from this flow.
- Never trust `apiBaseUrl` blindly from the QR code.
- Do not log `scanSecret`.
- Do not show `scanSecret` in UI.
- Treat approval as a sensitive auth action and require the native app to already have a valid login token.
- Expired QR codes should simply fail with a message telling the user to create a new QR code in the browser.

## Suggested Android Structure

Possible components:

- `QrLoginScannerActivity` or Compose screen
- `QrLoginPayloadParser`
- `QrLoginApprovalRequest`
- `QrLoginApprovalResponse`
- `QrLoginRepository.approveChallenge(challengeId, scanSecret)`

Parser output model:

```kotlin
data class QrLoginPayload(
    val challengeId: UUID,
    val scanSecret: String,
    val apiBaseUrl: String?
)
```

Approval request model:

```kotlin
data class QrLoginApprovalRequest(
    val scanSecret: String
)
```

Approval response model:

```kotlin
data class QrLoginApprovalResponse(
    val status: String,
    val expiresAt: String
)
```

## Manual Test Checklist

- WebView main menu button opens the native scanner through `scanQrLoginCode()`.
- Fallback `openNativeScreen("qrLoginScanner")` also opens the scanner if used.
- Camera permission request works from a fresh install.
- Invalid QR code is rejected.
- QR with wrong scheme is rejected.
- QR with missing `challengeId` is rejected.
- QR with missing `scanSecret` is rejected.
- Expired browser QR returns failure and does not crash scanner.
- Valid QR returns `APPROVED`.
- Browser automatically logs in after Android approval.
- Re-scanning the same QR after browser completion fails gracefully.
