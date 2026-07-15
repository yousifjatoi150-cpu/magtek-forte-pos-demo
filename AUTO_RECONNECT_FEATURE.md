# Auto-Reconnect Feature Implementation

## Overview
Implemented automatic device reconnection for paired MagTek readers, matching iOS app behavior. When a user connects to a device, it's saved and the app automatically reconnects to it on subsequent launches.

## Architecture

### Storage
- **SharedPreferences**: Stores paired device ID in `magtek_prefs` with key `saved_magtek_device_id`
- Persists across app restarts
- Cleared only on manual disconnect

### Auto-Reconnect Flow

```
App Startup
    ↓
RealMagTekGateway initialized (singleton via Koin)
    ↓
init block calls startAutoReconnect()
    ↓
Check if saved device exists
    ↓
If exists: Start discovery with 15s timeout
    ↓
Match discovered devices against saved device ID
    ↓
Auto-connect when saved device is found
    ↓
If timeout: Stop discovery, revert to Idle state
```

## Implementation Details

### Key Methods

1. **`startAutoReconnect()`**
   - Called automatically in `init` block
   - Checks if there's a saved device and if device isn't already connected
   - Starts discovery and sets 15-second timeout
   - Skips if auto-reconnect already in progress

2. **`startDiscovery()` (Enhanced)**
   - Now checks if a device matches the saved device ID
   - Automatically connects to matched device during auto-reconnect
   - Cancels auto-reconnect timeout on successful match

3. **`connectToDevice()` (Enhanced)**
   - Saves device ID on successful connection
   - Clears auto-reconnect flag and timeout
   - Device is now paired for future sessions

4. **`disconnectDevice()` (Enhanced)**
   - Clears saved device ID on manual disconnect
   - Cancels any pending auto-reconnect
   - Reverts to Idle state

### Shared Preferences Storage
```kotlin
private val prefs: SharedPreferences by lazy {
    appContext.getSharedPreferences("magtek_prefs", Context.MODE_PRIVATE)
}
```

**Configuration Constants:**
- `SAVED_DEVICE_ID_KEY = "saved_magtek_device_id"`
- `AUTO_RECONNECT_TIMEOUT_MS = 15000L` (15 seconds)

### State Management
- `isAutoReconnecting`: Tracks if auto-reconnect flow is active
- `autoReconnectTimeout`: Job for timeout mechanism
- Prevents multiple simultaneous auto-reconnect attempts

## User Experience

### Connected → Restart App
1. App detects saved device
2. Automatically starts searching
3. Device reconnects within 15 seconds
4. User sees connected state immediately

### Manual Disconnect
1. User taps disconnect
2. Device ID is cleared from storage
3. App returns to "No Device" state
4. Next restart will not auto-reconnect

### Device Not Available
1. Auto-reconnect times out after 15 seconds
2. App reverts to Idle/Disconnected state
3. User can manually search for devices
4. Previous connection is still saved (can be cleared by reconnecting to different device)

## Technical Stack
- **Coroutines**: `GlobalScope.launch` with delay for timeout
- **SharedPreferences**: Android built-in storage
- **Timber**: Structured logging for debugging
- **StateFlow**: Reactive state management

## Logging
Auto-reconnect flow includes detailed logging:
- 🔌 Gateway initialization
- 🔁 Auto-reconnect start/skip/timeout
- 💾 Device ID save/clear
- 📡 Device discovery and match detection
- ✅ Successful connection
- ❌ Errors and timeouts

## Testing
- Build: ✅ SUCCESS
- Unit Tests: ✅ PASS
- No breaking changes to existing functionality

## Future Enhancements
- Configurable timeout duration
- Exponential backoff for repeated failures
- User preference to enable/disable auto-reconnect
- Bluetooth power-on detection for automatic reconnect

