Ploggy README
================================================================================

Overview
--------------------------------------------------------------------------------

Ploggy is a surveillance- and traffic analysis-resistant privacy-enhanced social networking app which features location sharing, photo sharing, and private blogs.

The core privacy goals of the Ploggy project are unlinkability and confidentiality. From an adversary's perspective, unlinkability within Ploggy means that the adversary cannot distinguish whether two or more users belong to the same private Ploggy network.

Ploggy has a simple friend-to-friend architecture with no central servers or services. Each user runs an instance of the app on their device (currently, Android devices) that is both a client and a server for the user's own data. All communication happens over the Tor network. Each user's server runs as a Tor Hidden Service. All communication is private, anonymous, and does not involve any central party or intermediary.

Users establish trust with an in-person key exchange (currently, via Android Beam with a visual fingerprint). All communication is secured with TLS using pre-exchanged peer certificates, mutual authentication, and strong ciphersuites and key sizes.

Goals of the Ploggy project include:

* Application-level unlinkability design. For example, in location sharing, an app exchanging user coordinates via Tor and then using a 3rd party service to map the coordinates or reverse geocode a street address would allow an adversary to link users. Ploggy provides a way for users to share street address and map info without using a central intermediary.
* Offer enhanced privacy settings tailored to social network functionality, including time, location, and precision [limits for location sharing](http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.173.973).
* Support multiple personas for every user, each with its own network of peers and different privacy settings. For example, a restricted location sharing persona for co-workers and a distinct, less restricted persona for family.
* Provide a user experience that mitigates high latency/offline network conditions.
* Make use of existing well-studied security protocols, algorithms, and infrastructure: in particular, Tor and TLS.
* Minimal security work flow: users don't need to know about Tor Hidden Service .onion addresses, for example -- just click a button and exchange identities via NFC, etc.

_At this time, Ploggy is strictly a prototype which has not been subject to a thorough security audit. Do not use this code for any purpose other than development or testing._

Location Sharing
--------------------------------------------------------------------------------

For location sharing, there are two distinct privacy goals with distinct design constraints:

* Confidentiality of user's precise location
  * Users obtain their location coordinates from GPS only and disable centralized services such as Google's Network Location Provider.
  * Users download a coarse region mapping "package" from a central service; the package contains maps and geocoding data for a large region. Only the user's device knows the precise location within the region. Ploggy uses this package to prepare the location sharing message -- containing street address and map -- for peers.
* Unlinkability of two or more Ploggy users sharing location
  * Each user may obtain their location coordinates in a privacy-enhanced manner; or from a centralized, 3rd party service (for example, Google's Network Location Provider) and may obtain further location data, including street address and maps from a centralized service.
  * When sharing location information with peers, the user shares **all** location information -- coordinates, street address, maps -- with peers via Ploggy. Users must not refer to central services to map or geocode a peer's location.

Ploggy will use [Open Street Map](http://wiki.openstreetmap.org/wiki/Main_Page) data hosted on a central server to provide map and geocoding data. At this time, the feasibility of the regional package concept is not known and largely depends on creating packages of a reasonable size for download and storage on mobile devices.

Security Limitations
--------------------------------------------------------------------------------

Security assumptions:

* The user's device is trusted and not compromised. The user has a legitimate copy of the Ploggy app.
* Peers act in good faith: e.g., they will not expose private data or compromise the application protocols.
* The security protocols and cryptographic algorithms in TLS provide the expected security properties. An adversary cannot authenticate as a legitimate user, read plaintext messages, modify or forge or replay messages.
* Tor provides sufficient [anonymity properties](https://blog.torproject.org/blog/top-changes-tor-2004-design-paper-part-1) for Ploggy peers. The known [shortcomings](https://blog.torproject.org/blog/hidden-services-need-some-love) of the Tor Hidden Service architecture do not presently compromise the security properties of the system and will be addressed over time.

Limitations:

* Users can track when their peers are online, beyond the granularity of what's presented by the application user interface.
* Users could modify their app to omit data or send invalid data to peers.
* A network-situated adversary can observe the frequency with which any given user sends and receives Ploggy messages. This information could be used to infer, for example, rate of location change. This adversary can also block arbitrary Ploggy messages.
* The location service observes a single user's (coarse) location.
* A global, passive adversary can attempt to enumerate the number of Ploggy users. This adversary can also perform traffic confirmation attacks and link users if sufficient Tor relays are monitored.

Intentional exclusions:

* Secure distribution of the Ploggy app is outside the scope of the project (for Android, standard developer key signing will be used).
* Local security -- e.g., local storage encryption, self-destruct button -- is currently outside the scope of the Ploggy app.
* Circumvention or censorship/blocking of the Tor network is outside the scope of the Ploggy app; users can simply make use of existing circumvention tools such as [Psiphon](http://psiphon3.com).


Performance
--------------------------------------------------------------------------------

Performance considerations:

* There is no store and forward in Ploggy. The straightforward friend-to-friend design trades off availability for readily achievable unlinkability (without store and forward intermediaries, transport-level unlinkability depends solely on Tor anonymity properties). An untested assumption is that by running the Ploggy service on mobile devices, users will be online often enough to serve reasonably fresh updates to their peers.
* All aspects of the Ploggy app work "offline": users can publish content locally without peers connected; users cache peer updates locally and can view recent peer updates without peers connected.
* Where possible, make use of pipelining, consolidation, concurrency, and prefetching to create the best user experience on top of a network that has high latency and on top of services that frequently go offline.
* Ploggy provides settings to restrict/throttle use of mobile data networks and shutdown sharing in low battery conditions.
* The Tor Hidden Service mechanism provides NAT traversal.

Current Status
--------------------------------------------------------------------------------

Currently progressing towards an alpha version featuring location sharing, messaging and photo sharing.

Implemented:

* User Interface (manage identity, exchange identities, list friends, show location details, preferences)
* Identity management (X509 certificate and Tor Hidden Service identity generation)
* Identity exchange via NFC (Android Beam) with [Robohash](http://robohash.org)
* Data persistence (friend list and recent status updates)
* Tor wrapper (local SOCKS-enabled web client and Hidden Service)
* Embedded web server
* TLS transport security using custom trust management and restricted protocol and ciphersuite
* Logging with persistence
* Unit tests (covering primarily Tor client/Hidden Service and web client/server)
* Location manager (implemented using Android open source API, not new closed-source Play Service API)
* Reverse geocoding using Open Street Map data
* Messaging
* Photo/image sharing

Missing:
* Coarse location service design and implementation
* Map sharing
* private groups

Android App Screenshots
--------------------------------------------------------------------------------

![Messages](AndroidApp/screenshots/messages.png?raw=true "Messages")

![Friends](AndroidApp/screenshots/friends.png?raw=true "Friends")

![Your Status](AndroidApp/screenshots/your-status.png?raw=true "Your Status")

![Add Friend](AndroidApp/screenshots/add-friend.png?raw=true "Add Friend")

3rd Party Components
--------------------------------------------------------------------------------

* Tor - https://www.torproject.org
* Briar Project (jtorctl, TorPlugin components) - http://briar.sourceforge.net
* NanoHTTPD - https://github.com/NanoHttpd/nanohttpd
* httpclientandroidlib - https://code.google.com/p/httpclientandroidlib
* GSON - http://code.google.com/p/google-gson
* Spongy Castle - http://rtyley.github.io/spongycastle
* Otto - https://github.com/square/otto
* LinuxSecureRandom - http://code.google.com/p/bitcoin-wallet
* Robohash - http://robohash.org
* SeekBarPreference - http://robobunny.com/wp/2013/08/24/android-seekbar-preference-v2
* TimePickerPreference - http://code.google.com/p/android-my-time
* osmbonuspack - http://code.google.com/p/osmbonuspack/ (only a very little code)

License
--------------------------------------------------------------------------------

Please see the LICENSE file.

```
Ploggy free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see http://www.gnu.org/licenses/.
```

Contacts
--------------------------------------------------------------------------------

For more information on Psiphon Inc, please visit our web site at:

[www.psiphon.ca](http://www.psiphon.ca)
