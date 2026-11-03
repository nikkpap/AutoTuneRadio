# AutoTune Radio 0.19 — Android Auto

This version exposes the existing saved station list through Android's media browser service and uses the existing playback engine and media session.

- Browse and play saved stations, in the same order as the phone.
- Play/pause, next/previous and last-station resumption.
- Play a saved station by name through media search.
- Station logos and live stream title metadata when available.
- Browsing alone does not start playback. Android Auto requests playback.
- Existing phone settings, imports, EQ, calendars and screensaver remain in the phone app.

Install as an update to v0.18. The original project signing key is retained.
The Android Auto screen uses the host's media interface, not a copy of the phone's split layout.

## Device acceptance checks (not performed here)
Connect a phone to Android Auto; check discovery, station selection, pause/resume,
next/previous, reordered station list, phone/car synchronization, logos, voice
station search, call interruption and resumption, disconnection, reconnection,
empty library and offline stream errors. Confirm the chosen external-control
inversion behavior. Android Auto media commands share the external-control path.

A sideloaded build may require Android Auto developer settings to allow unknown
sources. This is separate from Android's APK installation permission.
No physical car/DHU validation or Play Store approval is claimed.
