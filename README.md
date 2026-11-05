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
This checkpoint is version **1.45 (46)**. The installed version is shown beneath
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
   install **TournamentScoringApp 1.45 (46)**. For example, with USB debugging:

   ```powershell
   adb install -r C:\Karate4\tournament-app\app\build\outputs\apk\debug\app-debug.apk
   ```

   The bundled root certificate is app-scoped; no Android system certificate
   installation or user CA approval is required.
4. Connect the tablet to the tournament Wi-Fi and open the app's Server
   Configuration. Try the default DNS name `tourn-svr.home.arpa` and tap
   **Save & Retry**. Confirm that the server is reachable, rings are listed,
   and a ring can be connected. If local DNS is unavailable, choose **IP** and
   enter `192.168.4.42`; reconnect and verify ring operations.
5. Exercise the normal app flows: load a ring/group, send a heartbeat, submit
   test results, and confirm the server receives them. For the update path,
   make the server's offered APK/version newer than the installed app, wait for
   **Update Available**, download it, and verify Android's installer reports
   the expected app/version. APK downloads use the configured HTTPS host too.
6. Confirm failures are fail-closed: disconnect Wi-Fi or enter a nonmatching
   server name and verify an actionable DNS/network/TLS error appears. Restore
   the correct DNS/IP and verify the app reconnects. Never resolve a
   certificate error by switching to HTTP, installing an unknown CA globally,
   or disabling hostname/certificate checks.

**Scope limitation:** this implements validated HTTPS transport for the
authorized server certificate and the app workflows described above. Server
device enrollment/authentication is not implemented by the server, so this is
not a completed device-authentication or full security-rollout feature.
