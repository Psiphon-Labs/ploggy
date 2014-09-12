# Ploggy Location Scheduling

## Motivation

In our first prototype, the app constantly obtained location fixes every ~10 minutes, even when running in the background. Each new location fix was pushed to all friends. This design wasted too much battery and data resources providing updates when not desired.

The new schedule will obtain location fixes only when the app is in the foreground; and request friend locations only when the app is brought to the foreground; and thereafter when the user views the friend location.

## Functionality

* When the app is brought to the foreground, start the LocationManager to get fixes every ~10 minutes. Continue to allow up to 30 seconds for a fix (to allow the system GPS provider to complete).
* When the app is brought to the foreground, send a /requestLocation request to each friend.
* When the friend's location is viewed, send a /requestLocation request to the friend.
* For self and friends, the UI displays the location found in the local data store. The timestamp of the location is displayed.
* In the UI, display an indicator when a location refresh is pending.
* Upon completion of a location fix, update self location in the local data store.
* Upon receipt, from a friend, of a /push containing a Location, update the friend's location in the local data store. Clear any pending location refresh indicator in the UI.
* A pushed location contains coordinates and [for now] street address. Map data is downloaded from the friend in the same manner as photo attachments to posts, as a second phase with a download progress indicator shown in place of a map.
* Friends request locations on demand. We must allow sufficient time for a full fix period, up to 30 seconds, before responding. So the request for a location, /requestLocation, is simply acknowledged and the actual data is sent in a subsequent /push to the friend.
* When receive /requestLocation from a friend:
 * Verify friend's location access permissions (e.g., can refresh location at current day/time).
 * If a fix recently completed (within the last minute), push that location.
 * Otherwise: add friend to list of receivers for new location and if a fix is already pending, wait. Otherwise, initiate a new fix. When the location fix completes, send the location to the list of receivers.
