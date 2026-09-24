# Tournament Scoring App — High-Level Design (Android Client)

| Field | Value |
|---|---|
| Document Type | High-Level Design (HLD) |
| Status | Draft |
| Version | 0.3 |
| Date | 24 September 2026 |
| Author | Scott |
| Language | Kotlin |
| Package | `com.summ0.tournamentscoringapp` |
| Companion | Tournament Management System — Server-Side HLD |
| Audience | Developers, Collaborators, Technical Stakeholders |

---

## 1 — Purpose and Scope

This document describes the Android client component of the Tournament Management System: a tablet-oriented Kotlin application used by ring volunteers and judges during live martial arts tournaments. It is intended to be read with the companion server-side HLD, which defines the Node.js/Express backend, administration functions, and server-owned tournament setup.

### Scope boundary

This HLD covers the Android client’s implemented architecture and the planned functionality that the client is expected to support. Server route internals, server database design, tournament setup, group construction, scheduling, and the administrator web interface are out of scope.

The document distinguishes between:

- **Implemented/confirmed:** behavior evidenced in the reviewed Android source files.
- **Planned or dependent:** behavior described as an intended integration or workflow that is not fully represented in the reviewed source set.

### Intended use

- Provide durable project context for developers, collaborators, and AI-assisted development sessions.
- Record the client’s current responsibilities, local model, network boundary, and known implementation gaps.
- Serve as a build and verification checklist as the app progresses toward live-event use.

---

## 2 — System Overview

The Tournament Scoring App is a native Android application written in Kotlin for the package `com.summ0.tournamentscoringapp`. The launcher is `MainActivity`, which uses Jetpack Compose and Material 3 to host the application workflow. The design is tablet-oriented, but the reviewed code does not enforce a specific tablet size or resource-qualified layout.

The app is designed as a LAN REST client for a tournament server. Its code establishes an HTTP base URL from either a DNS host name or IPv4 address, defaults to `http://HomePC-Sum2:3000`, and applies 10-second connection and read timeouts to HTTP calls.

### Architectural model

| Area | Current design |
|---|---|
| Presentation | Compose-driven `MainActivity`, with a `Scaffold`, edge-to-edge layout, Material 3 theme, and screen state represented by `CompetitionScreen` |
| Application boundary | `MainViewModel` provides a narrow wrapper around connection configuration, health probes, ring/group requests, heartbeats, and server-version retrieval |
| Domain logic | `TournamentEngine` owns form eligibility, forms scoring, awards-sheet construction, sparring pairing, bout resolution, and sample-scenario generation |
| Network | `TournamentRepository` uses `HttpURLConnection`, coroutines on `Dispatchers.IO`, JSON payloads, and explicit response/error wrapping |
| Persistence | `SharedPreferences` stores server connection settings; Compose `Saver` objects preserve selected UI state and competitor/group state across recreation |
| Data format | `org.json` is used for server-response parsing, request bodies, and state serialization |

### Operational responsibility

The intended live-event workflow is ring assignment, competitor check-in, weapons/hyungs scoring, sparring, awards review, assistance, and completion reporting. In the reviewed code, the engine and state models strongly support the competition workflow, while only the connection, ring configuration, ring assignment, heartbeat, and version-check network primitives are directly evidenced in the supplied files.

### UI technology

The theme supports light and dark schemes. On Android 12 and later, dynamic system colors are enabled by default; otherwise the app uses defined purple, gray, and pink Material color schemes. The base typography defines a 16sp body style with a 24sp line height.

---

## 3 — Application Flow and Screens

`CompetitionScreen` defines the application’s principal workflow states:

1. `CHECK_IN`
2. `WEAPONS_SCORING`
3. `HYUNGS_SCORING`
4. `SPARRING_BRACKET`
5. `OVERALL_AWARDS`

`MainActivity` accepts an optional intent extra to start directly at the weapons or hyungs scoring state; otherwise it starts at check-in. It delegates rendering to `CheckInScreen`, which is outside the supplied source set and therefore is not treated here as fully code-verified.

### 3.1 — Server connection and ring selection

**Implemented/confirmed infrastructure**

- The app supports DNS and IPv4 connection modes through `ServerConnectionMode.DNS` and `ServerConnectionMode.IP`.
- DNS input is normalized to HTTP on port 3000. If DNS input is blank or invalid, the client falls back to the default server base URL.
- IPv4 input must be exactly four decimal octets, each within 0–255; it is normalized to HTTP on port 3000.
- `SharedPreferences` retains the selected connection mode, last DNS name, and last address. A legacy last-IP preference is also read and written for compatibility.
- The default DNS host is `HomePC-Sum2`.
- A health probe is available before subsequent connection steps.
- Ring configuration can be fetched with the physical device’s manufacturer/model label included as `tabletLabel`.
- Ring labels matching a letter-plus-number pattern, such as `A1`, can be parsed into grid coordinates.

**Intended UI behavior**

The connection and ring-selection experience is expected to let a volunteer enter or reuse a server address, verify reachability, view permitted ring slots, and request a group for an available ring. The exact Compose UI implementation is not part of the reviewed source set.

### 3.2 — Check-in control board

The domain model supports competitors with registered, checked-in, and no-show states. Each competitor also carries independent registration status for hyungs, weapons, and sparring.

**Implemented/confirmed model behavior**

- A competitor is eligible for a competition only if they are checked in and their entry status is `REGISTERED`.
- Entries may be marked `REGISTERED`, `SCRATCHED`, or `COMPLETED`.
- Competitor state can be serialized with `competitorListSaver`, including profile attributes, check-in status, and all entry statuses.
- `GroupBanner` state can be serialized with `groupBannerSaver`.
- Heartbeat progress uses completed and total counts and produces a rounded, bounded percentage from 0 through 100.

### 3.3 — Forms scoring: weapons and hyungs

Weapons and hyungs share a forms-scoring model through `HyungDiscipline` and `HyungScoreRow`.

**Implemented/confirmed rules**

- A forms result accepts from one to five judge scores.
- With fewer than five scores, the total is the sum of all scores.
- With five scores, the highest and lowest scores are discarded and the middle three are summed.
- Results are sorted by descending total, then competitor name.
- Standard awards provide first place, second place, and two co-third-place slots.
- Placement state supports incomplete scoring, completed placement labels, pending tie-break requests, tie-break details, and re-finalization.

**Rank/form eligibility**

The engine maintains an explicit rank-to-form catalog for TTLD through Sam Dan. TTLD may perform “Any creative set of techniques” in empty-hand hyungs and has no weapons eligibility. Weapons eligibility begins at 4th Gup in the current catalog.

The catalog currently includes the following rank range:

| Rank range | Empty-hand forms | Weapons availability |
|---|---|---|
| TTLD | Any creative set of techniques | None |
| 10th–5th Gup | Sae Kye Hyung / Pyung Ahn progression | None |
| 4th–2nd Gup | Pyung Ahn progression through Bassai | Bong Hyung II Bu |
| 1st Gup–Cho Dan | Bassai through Naihanchi/Sip Soo progression | Bong Hyung E Bu through Bong Hyung Sam Bu |
| E Dan–Sam Dan | Naihanchi, Jin Do, Ro Hai, Kong Sang Koon as applicable | Bong Hyung Sam Bu, Dan Gum, and sword forms as applicable |

### 3.4 — Sparring

The sparring engine uses a single-elimination bracket model and supports both real bout processing and deterministic sample-data simulation.

**Implemented/confirmed rules**

- Standard bout duration is 120 seconds.
- The engine considers a four-inch height difference as the preferred pairing threshold when constructing first-round pairings.
- First-round brackets expand to the next power of two and allocate byes. The bye placement logic attempts to prevent byes from sharing an immediate Round 2 group.
- Eligible competitors are sorted by height for initial pairing; randomization is seeded for repeatable behavior.
- A bout may end by bye, first to three, time expiration, standard-warning disqualification, severe-warning disqualification, double disqualification, required tie-break, or remain in progress.
- Two standard warnings deduct one point. Three standard warnings disqualify the competitor. Any severe warning disqualifies the competitor.
- A tied score at the three-point threshold or at expiration produces a tie-break-required outcome.
- Sparring awards identify champion, runner-up, and two co-third-place finishers when bracket results support them.
- Detailed sparring reports can be generated from completed tournament results.

The model also defines bracket sheet structures, active-bout state, per-bout progress, warnings, adjusted scores, winner information, and overall awards summary structures.

### 3.5 — Overall awards and completion

The reviewed model supports `OVERALL_AWARDS`, `OverallAwardsSummary`, and `SignatureEntry`. This establishes an application-level concept of a final results/awards review with signing information.

Generation of final local division packets, PDF/PNG results, receipt files, reopening historical results, and posting completion to the server are retained as intended functionality from the prior HLD. Those behaviors are **not directly verifiable** from the supplied Kotlin files and should remain tracked as planned or verified against the omitted UI/storage implementation.

### 3.6 — Assistance, updates, and APK installation

The reviewed networking layer includes a server-version fetch from `GET /api/version`. The utility layer includes an APK installer that exposes a local APK through a `FileProvider` and launches Android’s package installer using the APK MIME type.

Assistance request/clear workflows and APK download logic are retained as intended API/UI functionality, but their caller/UI implementation was not supplied for this review.

---

## 4 — Domain and Data Model

### Core competition entities

| Entity | Key contents and responsibilities |
|---|---|
| `Competitor` | Stable ID, name, studio, rank, rank level, age, height in inches, check-in status, and competition-entry map |
| `Division` | ID, name, rank range, display label, age range, competitors, and judges; validates whether a competitor fits rank and age bounds |
| `CompetitionEntry` | Competition type plus registration/scratch/completion status |
| `Judge` | ID, name, and rank |
| `HyungScoreRow` / `HyungResult` | Competitor, discipline, submitted scores, and calculated total |
| `PlacementFinalizeState` / `PendingTieBreak` | Forms finalization labels, tie-break metadata, messages, and deferred tie-break requests |
| `SparringBout` / `SparringBoutResult` | Opponents, warnings, elapsed time, calculated scores, outcome, and winner |
| `SparringTournamentResult` | Completed rounds, champion, and bracket size |

### Remote/server-derived entities

| Entity | Source interpretation |
|---|---|
| `RemoteCompetitor` | Server competitor ID, name, studio/school, rank, age, and height |
| `RemoteGroup` | Group ID/name, age/rank ranges, display label, mat number, and competitors |
| `RingAssignment` | Ring ID/label, server base URL, active group, current phase, phase plan, queued groups, and completed groups |
| `RingOption` | Ring ID/label, availability flag, and status label |
| `ServerConnectionConfig` | DNS/IP mode and last-used connection inputs |

### Parser tolerance and defaults

The remote-group parser accommodates several server field aliases and applies defensive defaults:

- Competitor name: `name` or `fullName`.
- Competitor ID: `id` or `competitorId`; otherwise a deterministic fallback is constructed from the group, index, and name.
- Studio: `studio` or `school`.
- Rank range: `low`/`high` or `min`/`max`; otherwise derived from the competitors’ known ranks.
- Height: `heightInInches` or numeric `height`; otherwise defaults to 60 inches.
- Group ID, group name, age range, and mat number also have safe defaults.

This tolerance is important because it lets the Android client interoperate with modestly different server response shapes while still producing a usable local group.

### Local state durability

The explicit source evidence supports two persistence mechanisms:

- **`SharedPreferences`:** server connection preferences.
- **Compose `Saver` serialization:** competition screen, competitor list, and group banner state, using string/JSON encodings suitable for `rememberSaveable` integration.

The supplied source does not establish a Room/SQLite database. Broader local-result artifact persistence remains planned/needs verification against the omitted UI and storage code.

---

## 5 — Server Integration

### Confirmed endpoint support

| Method | Endpoint | Confirmed client behavior |
|---|---|---|
| GET | `/api/health` | Health/connectivity probe, accepting any 2xx response |
| GET | `/api/rings/config?tabletLabel=...` | Fetches `allowedRings`, parsing IDs, labels, availability, and status text |
| GET or POST | Caller-supplied ring-assignment URL | Fetches/parses ring assignment responses; method and JSON body are caller-selectable |
| POST | `/api/rings/{ringId}/heartbeat` | Sends phase, tablet label, check-in/progress counts, and rounded phase percentage |
| GET | `/api/version` | Fetches an integer `versionCode`, defaulting to 0 when unavailable |

The HLD retains `POST /api/rings/{ringId}/request-group` as the expected ring-assignment route because it is documented in the prior workflow. The supplied repository exposes a generic assignment request function rather than hard-coding that endpoint.

### Heartbeat contract

`sendRingHeartbeat` posts the following JSON fields:

```json
{
  "phase": "check-in | weapons | hyungs | sparring | awards",
  "tabletLabel": "manufacturer model",
  "checkInCount": 0,
  "checkInTotal": 0,
  "phaseCompletedCount": 0,
  "phaseTotalCount": 0,
  "phaseProgress": 0
}
```

The phase values are derived from the current `CompetitionScreen`. Progress is capped at 100 percent and is zero when the total count is zero or negative.

### Transport behavior and limitations

- All confirmed network calls use HTTP, not HTTPS.
- Work is performed on `Dispatchers.IO`.
- Connection and read timeouts are each 10 seconds.
- The generic JSON helper treats only HTTP 200 as success; other status codes return a descriptive error body when available.
- The health probe separately accepts any 2xx response.
- The generic helper returns a JSON object, so endpoint responses must be JSON objects.
- No authentication, authorization, certificate pinning, offline write queue, or automatic retry queue is evidenced in the reviewed source.

### Planned/dependent integrations

The following integrations remain part of the intended application workflow but are not directly demonstrated by the supplied source files:

- Posting assistance requests and clearing assistance.
- Posting finalized division completion packets.
- Downloading a newer APK from the server.
- Server-side score or check-in synchronization beyond heartbeat status.
- Live server push, announcements, or continuous ring-state polling.

---

## 6 — Non-Functional Characteristics

### Usability

- Compose and Material 3 provide the active UI foundation.
- The app enables edge-to-edge layout.
- It can derive the current device manufacturer/model for display to the server as a tablet label.
- The legacy check-in activity demonstrates large tap targets for presence and sparring toggles, but it is not the primary UI architecture.

### Reliability and recoverability

- HTTP calls have bounded 10-second connect/read timeouts and structured error messages.
- Server connection configuration persists between launches.
- UI-state savers are available for screen, competitor, and group-banner restoration across activity recreation.
- Remote JSON parsing supplies fallback values and field aliases to reduce failures from minor schema variations.

### Compatibility

- Dynamic colors are enabled on Android 12+ when available.
- The actual project `minSdk`, `targetSdk`, Gradle dependencies, Android manifest permissions, `FileProvider` declaration, and network-security configuration were not included in the review set and remain to be verified.

### Security

- The client currently uses plain HTTP on the local network.
- No authentication or role enforcement is visible in the supplied code.
- APK installation relies on a correctly configured `FileProvider` and device permission to install unknown-source packages; the manifest/provider configuration must be verified in the Android project.

---

## 7 — Design Decisions and Open Items

### Confirmed decisions

- Kotlin is the implementation language.
- Jetpack Compose/Material 3 is the primary application UI approach.
- The app uses HTTP REST on the tournament LAN.
- DNS and IPv4 connection modes are supported; the default server host is `HomePC-Sum2` on port 3000.
- The client performs local competition calculations for forms and sparring.
- Forms use one to five scores; five-score totals discard the high and low values.
- Sparring uses a 120-second duration and supports warnings, deductions, disqualification, tie-breaks, and byes.
- Server settings are persisted through `SharedPreferences`; state-restoration savers exist for selected workflow state.

### Open or verification-required items

- Verify the full Compose implementation of `CheckInScreen`, including actual navigation, check-in controls, scoring input, completion UI, and heartbeat scheduling.
- Confirm whether the app currently writes completed packets, PDF/PNG files, receipts, and standings to local storage; no evidence appears in the reviewed files.
- Confirm request-group, assistance, completion, update-download, and server score-submission call sites/routes against the full project.
- Verify Android manifest permissions for local HTTP networking and the required `FileProvider` configuration for APK installation.
- Define authentication and role enforcement before deployments beyond a trusted controlled LAN.
- Decide whether authoritative scoring/check-in data must be synchronized server-side rather than retained primarily in local UI state.
- Decide whether live server push, periodic assignment refresh, announcements, or audit logging are required for tournament operations.

---

## 8 — Implementation Checklist

Legend: ✅ confirmed in reviewed source; 🔄 partially evidenced or dependent on omitted UI/storage code; ☐ not yet verified or planned.

### Platform and architecture

- ✅ Kotlin Android client in package `com.summ0.tournamentscoringapp`
- ✅ Compose launcher in `MainActivity` with Material 3 theme and edge-to-edge layout
- ✅ `CompetitionScreen` state model for check-in, weapons, hyungs, sparring, and awards
- ✅ `MainViewModel` wrapper for connection, network, heartbeat, and version operations
- ✅ Coroutine-based HTTP work using `Dispatchers.IO`
- 🔄 Tablet-optimized layouts and control sizing
- ☐ Verify minSdk, targetSdk, manifest configuration, and release build settings

### Connectivity and ring assignment

- ✅ DNS/IP connection modes and input normalization
- ✅ Default connection host/port: `HomePC-Sum2:3000`
- ✅ Last-used server settings persisted in `SharedPreferences`
- ✅ Health probe support through `/api/health`
- ✅ Ring configuration fetch with device `tabletLabel`
- ✅ Ring-label grid-coordinate parser for labels such as `A1`
- ✅ Generic ring-assignment response parser
- 🔄 `POST /api/rings/{ringId}/request-group` UI/call-site verification
- ☐ Live ring-state refresh or server-push mechanism

### Check-in and group state

- ✅ Remote group/competitor parsing with aliases and defensive defaults
- ✅ Check-in, no-show, and competition-registration status model
- ✅ Eligibility filter requiring checked-in plus registered entry status
- ✅ Compose saver support for competitors and group banner
- ✅ Progress snapshot and percentage calculation for head-table status
- 🔄 Actual primary Compose check-in controls and persistence behavior
- ☐ Server synchronization of individual check-in and scratch/no-show updates

### Forms and awards

- ✅ Rank/form eligibility catalog from TTLD through Sam Dan
- ✅ TTLD hyungs eligibility and no TTLD weapons eligibility
- ✅ One-to-five judge score totals; five-judge high/low exclusion
- ✅ Result ranking and standard first/second/two co-third awards pattern
- ✅ Tie-break data structures and finalization result states
- 🔄 Forms score-entry UI and tie-break dialog behavior
- 🔄 Overall awards review and signature capture UI
- ☐ Verify local results packet/PDF/PNG/receipt generation and reopening of prior results

### Sparring

- ✅ Single-elimination bracket construction with power-of-two sizing and byes
- ✅ Height-aware first-round sequencing and deterministic seeded randomization
- ✅ 120-second bout duration
- ✅ First-to-three, expiration, warning, severe-warning, double-DQ, bye, and tie-break outcomes
- ✅ Warning deduction and disqualification rules
- ✅ Bracket/result/awards/report data structures
- 🔄 Bout dialog, timer, live scoring, and winner-progression UI

### Assistance, updates, and security

- ✅ Version-code request via `/api/version`
- ✅ Local APK-install intent via `FileProvider`
- 🔄 Update download and in-app update prompting
- 🔄 Assistance request and clear workflow
- ☐ Verify assistance and completion API call sites
- ☐ Authentication and role enforcement
- ☐ HTTPS or a documented trusted-LAN security posture
- ☐ Verify `FileProvider` manifest/XML configuration and unknown-sources installation workflow

### Testing

- ✅ Deterministic engine behavior can be exercised through seeded sample scenario generation
- ☐ Unit tests for scoring, tie-breaks, warnings, disqualification, bye placement, and parser edge cases
- ☐ Repository tests using mock HTTP responses, including non-200 handling and schema variations
- ☐ Compose UI tests for connection, ring selection, check-in, forms, sparring, awards, and update flows
- ☐ End-to-end LAN testing with the server and multiple tablets

---

## 9 — Revision History

| Version | Date | Author | Notes |
|---|---|---|---|
| 0.3 | 24 September 2026 | Scott | Revised after source-to-HLD comparison. Added confirmed architecture, state savers, parser tolerance/defaults, detailed scoring and sparring rules, precise heartbeat payload/transport behavior, legacy activity status, APK installation capability, and explicit distinctions between reviewed implementation and planned/dependent functions. |
| 0.2 | 22 September 2026 | Scott | Revised to match current client behavior, TTLD support, hyungs-only eligibility, heartbeat cadence, and updated checklist coverage. |
| 0.1 | 18 September 2026 | Scott | Initial Android client HLD draft and implementation checklist. |
