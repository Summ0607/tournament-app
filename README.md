# Tournament Ring App — Android (Kotlin)

## HTTPS server connection

The app's default connection is `https://tourn-svr.home.arpa:3443`. The alternative
manual-IP mode uses `https://192.168.4.42:3443`. Both host identities are present
in the server certificate. The app includes the authorized **public** Tournament
Root CA, scoped to these two exact hostnames by Android Network Security Config.
It does not install a CA into Android's device-wide trust store. Cleartext HTTP
is disabled, certificate and hostname verification remain enabled, and there is
no HTTP retry or trust bypass. Existing saved `HomePC-Sum2` default DNS settings
are migrated to `tourn-svr.home.arpa`; other saved DNS/IP choices are preserved.

The app also checks for updates and downloads APKs from this same configured
HTTPS base URL, so health checks, ring operations, result submissions, and APK
downloads all use the same validated TLS connection.

### Build the installable APK

From `C:\Karate4\tournament-app`, run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to
`C:\Karate4\tournament-app\app\build\outputs\apk\debug\app-debug.apk`.
This checkpoint is version **1.50 (51)**. The installed version is shown beneath
the startup heading and in Server Configuration, including compact layouts and
when the server is unreachable. Only the public root certificate is
bundled at `app\src\main\res\raw\tournament_root_ca.pem`; never copy or bundle a
private key or account database. The bundled PEM file's SHA-256 file hash is
`0108FF564EFF18C9487422CDBCF36ED951BA947487C566B7B992959FD2706741`.

### Install and verify on a physical tablet

1. On the tournament server, start the HTTPS service and confirm its DNS record
   resolves `tourn-svr.home.arpa` to `192.168.4.42`. The tablet and server must
   be on a network that permits the tablet to reach TCP port **3443**. Correct
   the tablet's date and time before TLS testing.
2. From a trusted Windows PowerShell prompt on the server/network, verify the
   server health endpoint, CA chain, and both certificate identities using the
   public CA (adjust the OpenSSL path if it is installed elsewhere):

   ```powershell
   "GET /api/health HTTP/1.1`r`nHost: tourn-svr.home.arpa`r`nConnection: close`r`n`r`n" | & 'C:\Program Files\Git\usr\bin\openssl.exe' s_client -brief -connect tourn-svr.home.arpa:3443 -servername tourn-svr.home.arpa -CAfile C:\TournamentPrivate\https\root-ca.pem -verify_hostname tourn-svr.home.arpa -verify_return_error
   "GET /api/health HTTP/1.1`r`nHost: tourn-svr.home.arpa`r`nConnection: close`r`n`r`n" | & 'C:\Program Files\Git\usr\bin\openssl.exe' s_client -brief -connect 192.168.4.42:3443 -servername tourn-svr.home.arpa -CAfile C:\TournamentPrivate\https\root-ca.pem -verify_ip 192.168.4.42 -verify_return_error
   ```

   Both must report `Verification: OK`, the expected peer identity, and
   `HTTP/1.1 200 OK`. Do not add options that bypass verification.
3. Transfer the debug APK to the tablet over an approved method. If Android
   prompts, allow the file manager/installer to install this local app, then
   install **TournamentScoringApp 1.50 (51)**. For example, with USB debugging:

   ```powershell
   adb install -r C:\Karate4\tournament-app\app\build\outputs\apk\debug\app-debug.apk
   ```

   The bundled root certificate is app-scoped; no Android system certificate
   installation or user CA approval is required.
4. Connect the tablet to tournament Wi-Fi. On the laptop, activate a **test
   tournament**, open **Ring Assignment**, select a ring, and open **Daily tablet
   QR code**. On the tablet, tap **Scan daily ring QR code**, allow camera access,
   and scan the laptop screen. The app authorizes and automatically connects to
   the encoded ring; no account password or manual ring choice is needed.
5. Exercise the normal app flows: load a ring/group, send a heartbeat, submit
   test results, and confirm the server receives them. For the update path,
   make the server's offered APK/version newer than the installed app, wait for
   **Update Available**, download it, and verify Android's installer reports
   the expected app/version. APK downloads use the configured HTTPS host too.
6. Reopen the app and briefly disconnect/reconnect Wi-Fi; authorization should
   survive. The same daily code can be rescanned. A second tablet sees a warning
   before replacing the first tablet's authorization. Verify an expired code is
   rejected: both the code and authorization expire at midnight according to the
   laptop clock. Do not change the live laptop clock for testing. Never resolve a
   certificate error by switching to HTTP, installing an unknown CA globally,
   or disabling hostname/certificate checks.

### Daily ring credentials

The app exchanges the reusable daily QR token over HTTPS for a credential scoped
to that tournament, ring and laptop-local day. Credentials survive app/server
restarts, are excluded from Android backup and device transfer, and are attached
only to the paired HTTPS origin (never redirected to another host). The server
rejects other ring operations, revoked tablets and expired credentials. Rescanning
the current ring code restores authorization without clearing unsent scores.
While a division is loaded, scanning another ring or tournament is rejected.

The QR address is authoritative for the paired connection. The app retains DNS/IP
configuration, but changing those settings does not move an existing credential
to another server. Scan that server's code to authorize a different connection.
Fixed-IP QR scanning and authorization have been verified on the physical tablet.
QR codes default to the fixed IP while tablet DNS resolution is unavailable.
Replacement and score-submission acceptance checks remain part of tablet testing.
The server now uses HTTPS-only listeners; no HTTP fallback is available.

### Durable division storage and restart recovery

Loaded divisions are stored durably under the app's private storage at
`filesDir/divisions/`:

- `index.json` — catalog of every stored division with its status
  (`LOADED` → `IN_PROGRESS` → `COMPLETED` → `SUBMITTED`), source, timestamps,
  and the active division anchor.
- `{division}/division.json` — the immutable division as loaded (network now,
  QR in a later stage).
- `{division}/session.json` — the latest check-in/scoring snapshot: check-in
  states, entries, locked phases, weapons/hyungs scores and tie-breaks, judge
  choices, placements, pending tie-breaks, sparring winners and bout progress,
  current screen, and phase history.

Every write is atomic (temp file + rename), so a crash or power loss never
leaves a partially written document. Session snapshots are written automatically
whenever scoring state changes; untouched divisions stay `LOADED`. On startup the
app recovers the active division and its session before contacting the server,
and reconnecting a ring preserves the recovered division unless the server
assigns a different one. An in-flight sparring bout timer is not persisted:
reopen the bout after a restart; recorded points and warnings are retained.
Completing a division marks it `COMPLETED`, and a server acknowledgment marks it
`SUBMITTED` with the accepted submission id. Stored divisions are retained for
later use and survive app restarts.

Validate with `.\gradlew.bat testDebugUnitTest` (`DivisionStoreTest`,
`DivisionSessionTest`).

### Division QR loading and network packages

Divisions reach the tablet two ways, both producing an identical loaded division:

- **Self-contained QR codes** — from the launch screen, **Scan division QR**
  decodes a `tournament-division-qr` code printed in advance by the server. The
  payload is raw-deflate + base64url with a SHA-256 integrity prefix, matching
  the server encoder; a tampered or foreign code is rejected. No network is
  required, so divisions can be loaded fully offline.
- **Network package** — **Stored divisions → Load ring divisions** fetches
  `GET /api/rings/{ringId}/division-package` and stores the ring's current and
  queued divisions at once.

Both paths cap a division at 12 competitors and persist through the durable
store, so multiple divisions live on one tablet. **Stored divisions** lists them
with status and lets the operator open (and resume) any one; opening restores its
saved session.

### Judge service recording

**Judge Service** on the check-in header records who is actually serving the
loaded division. Choose P1/P2 assignment phase; it is independent of the scoring
phase. The screen supports seating, replacing a seated judge, swapping a
judge's role, and removing a judge, keeping a timestamped history of every
`SEATED`/`REPLACED`/`SWAPPED` change alongside the final lineup. Center, corner,
scorekeeper, timekeeper and arbitrator are distinct actual roles. Division
histories are stored separately per phase and survive restarts. **Load / refresh
saved plan** downloads the ring/phase roster, assignment/source revisions, arbitrator
reserves, and hidden event-PIN verification data without changing the server
plan. The app persists the complete plan by event/ring/phase. It can show the
saved roster after restart or when the server is unreachable, clearly marking
that offline copy as potentially stale. The PIN itself is never displayed.

**Transfer ring-phase service** consolidates all locally stored division
histories for that ring and phase into one packet with its own submission ID.
The packet is saved before network transfer; failed or interrupted transfers
retry with the same ID, and the app records the server receipt durably. A second
packet for an already acknowledged ring-phase is prevented. The Judge Management
coordinator can review and import received packets as unverified Tournament
experience; coordinator verification remains a separate action. Legacy combined
SK/TK entries require the coordinator to identify Scorekeeper or Timekeeper.

When network transfer is unavailable, **Save packet to USB** writes the same
durable packet through Android's document picker. A coordinator imports it from
USB in the server's active-tournament Judge Management workspace and returns the
downloaded receipt file to the tablet. **Import USB receipt** validates that the
receipt matches a saved submission and records the acknowledgment durably.

Validate with `.\gradlew.bat testDebugUnitTest` (`DivisionPackageTest`,
`DivisionStoreTest`, `RingPhaseServiceTest`).
