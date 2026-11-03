# v0.21 configurable app shortcuts
Settings → Display (Οθόνη) → App buttons (Κουμπιά εφαρμογών).
Choose a replacement for the Phone button and up to two optional applications.
The searchable picker lists enabled exported launchable activities, including system apps and dialer handlers. Apps requiring unavailable permissions are excluded.
The selected component, name and app icon are used; choices persist across restarts. Clearing Phone restores ACTION_DIAL; clearing either optional slot hides its button. Home remains unchanged. Toolbar scrolls horizontally on narrow panels to retain 48 dp touch targets.
If an app is removed/disabled or refuses launch, a message directs the user back to settings. No automatic calls, no CALL_PHONE or QUERY_ALL_PACKAGES permission. Targeted manifest visibility queries only. Apps open externally, not embedded. This affects the native Android player, not Android Auto's system-generated UI.
Head-unit BT Phone must expose a launchable or dialer activity; private vendor services cannot be opened through this selector.
