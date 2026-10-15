# Implementation Plan: Map Location Sharing in MAX AI

Add map and GPS location sharing capabilities to MAX AI, allowing users to inspect their current location via an in-app Map Location Card and instantly share Google Maps links with contacts through WhatsApp and SMS voice commands or one-tap action buttons.

## Confirmed User Requirements
- **Trigger & Presentation**: Voice commands (Hindi, English, Hinglish) and an interactive in-app Map Location Card.
- **Sharing Channels**: Direct Google Maps link sharing via WhatsApp and SMS messaging.
- **Privacy & Permissions**: Android `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` with graceful permission prompts and explanations.

---

## 1. Architecture & Component Design

```
┌────────────────────────────────────────────────────────────────────────┐
│                              MAX AI UI                                 │
│                                                                        │
│   ┌────────────────────────────────┐   ┌───────────────────────────┐   │
│   │       MaxConsoleScreen         │   │   MaxPermissionsScreen    │   │
│   │   - Voice Mic & Chat Feed      │   │   - Location Permission   │   │
│   │   - Interactive Location Card  │   │     Status & Auth Card    │   │
│   └───────────────┬────────────────┘   └─────────────┬─────────────┘   │
└───────────────────┼──────────────────────────────────┼─────────────────┘
                    │                                  │
       ┌────────────▼──────────────────────────────────▼───────────┐
       │                       MaxViewModel                         │
       │   - shareCurrentLocation(recipientName, platform)          │
       │   - fetchCurrentLocation(): StateFlow<LocationSnapshot?>   │
       │   - locationPermissionState                                │
       └────────────────────────────┬───────────────────────────────┘
                                    │
       ┌────────────────────────────▼───────────────────────────────┐
       │                    MaxLocationService                      │
       │   - LocationManager (GPS + Network Provider)               │
       │   - Reverse Geocoder (Street, City, Postal Code)           │
       │   - Google Maps Link Generator                             │
       │   - WhatsApp & SMS Share Intent Dispatcher                 │
       └────────────────────────────┬───────────────────────────────┘
                                    │
       ┌────────────────────────────▼───────────────────────────────┐
       │                   MaxCommandRouter & Brain                 │
       │   - "Meri location Rahul ko WhatsApp karo"                 │
       │   - "Send my location to Mom via SMS"                      │
       │   - "Kahan hoon main?" / "Where am I?"                     │
       └────────────────────────────────────────────────────────────┘
```

---

## 2. Proposed Changes

### 1. Core Location Service (`com.example.tools.MaxLocationService.kt`)
- Create `MaxLocationService`:
  - Uses Android's native `LocationManager` (`GPS_PROVIDER` and `NETWORK_PROVIDER`) to fetch coordinates with fine or coarse accuracy.
  - Generates standard universal Google Maps link:
    `https://maps.google.com/?q={latitude},{longitude}`
  - Reverse geocoding via `android.location.Geocoder` to provide human-readable address (e.g., "Connaught Place, New Delhi, Delhi 110001").
  - `shareLocationViaWhatsApp(context, phoneNumber, mapUrl, addressText)`: Formats a natural message in Hindi or English and opens WhatsApp chat (`whatsapp://send` / `https://api.whatsapp.com/send`).
  - `shareLocationViaSms(context, phoneNumber, mapUrl, addressText)`: Prepares `Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber"))` with pre-filled message text.

### 2. Intent Parsing & Voice Routing (`MaxCommandRouter.kt` & `CallingAndSpeakerExecutor.kt`)
- Add deterministic and AI brain routing for location queries:
  - `"Meri location Rahul ko bhejo"` / `"Rahul ko location send karo"` -> Resolves contact "Rahul" -> Fetches GPS -> Prepares WhatsApp/SMS share intent.
  - `"Mummy ko location SMS karo"` -> Resolves contact "Mummy" -> Sends SMS intent.
  - `"Meri location kya hai"` / `"Main kahan hoon"` / `"Where am I"` -> Fetches GPS fix -> Displays Map Location Card in chat and speaks current address.
- Seamless contact lookup using existing `ContactMatch` database integration.

### 3. In-App Interactive Location Card (`MapLocationCard.kt` in `com.example.ui`)
- Rich Material 3 card rendered in the chat bubble or as an action card:
  - Pin icon with Electric Cyan accent.
  - Reverse-geocoded address display and GPS accuracy pill.
  - "Open in Google Maps" button (launches Google Maps app or browser).
  - "Share via WhatsApp" button with recipient contact picker dialog.
  - "Share via SMS" button.

### 4. Permissions & Manifest (`AndroidManifest.xml` & `MaxPermissionsScreen.kt`)
- Declare in `AndroidManifest.xml`:
  ```xml
  <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
  <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
  ```
- Add Location permission item in `MaxPermissionsScreen`:
  - Shows "Location Access (ACCESS_FINE_LOCATION)" status.
  - Explains why needed: "Required to share your exact Google Maps location with contacts via WhatsApp and SMS on your voice command."
  - "Authorize Location" button triggering runtime permission request.

---

## 3. Verification Plan

1. **Compilation Check**:
   - Run `compile_applet` to ensure zero compilation or syntax errors.
2. **Local Robolectric Unit Tests**:
   - Run `gradle :app:testDebugUnitTest` to verify intent parsing for `"Rahul ko WhatsApp par location bhejo"` and `"Kahan hoon main"`.
   - Verify Google Maps link formatting and SMS/WhatsApp intent construction.
3. **Runtime & Fallbacks**:
   - Graceful fallback when GPS is disabled or permission is not yet granted (prompts user to enable GPS or grant permission).
