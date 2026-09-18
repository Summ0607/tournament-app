# Tournament Scoring App — High-Level Design (Android Client)

| Field | Value |
|---|---|
| Document Type | High-Level Design (HLD) |
| Status | Draft |
| Version | 0.1 |
| Date | 18 September 2026 |
| Author | Scott |
| Language | Kotlin |
| Package | `com.summ0.tournamentscoringapp` |
| Companion | Tournament Management System — Server-Side HLD |
| Audience | Developers, Collaborators, Technical Stakeholders |

---

## 1 — Document Purpose & How to Use This Document

This document describes the **Android client component** of the Tournament Management System — a field-facing tablet application used by ring volunteers and judges during live martial arts tournaments. It is the companion document to the *Tournament Management System — Server-Side HLD*, which covers the Node.js + Express backend hosted on the venue LAN. Readers of this document are expected to be familiar with, or to read alongside, that companion document.

> **Scope Boundary**
> 
This document covers the Android client only: its screens, local data model, API integration points, non-functional requirements, open design questions, and build checklist. Server implementation details (routes, database schema, admin web UI) are out of scope here and are addressed in the server-side HLD.

**How to use this document:**

    
- **As LLM context:** Paste this document (or its URL) at the start of a new session to give a language model full context about the Android client architecture without re-explaining from scratch.
    
- **As a collaborator briefing:** Share with any developer or stakeholder joining the project to orient them to the app's structure and current state.
    
- **As a personal reference:** Use Section 9 as a living build checklist. Mark items as In Progress or Complete as work proceeds.
    
- **As a design record:** Decisions made and questions still open are captured explicitly so context is not lost between sessions.

All checklist items in Section 9 begin as **Not Started (☐)** unless explicitly marked with ✅ (confirmed from prior code review) or 🔄 (in progress).

---

  
    

## 2 — System Overview

The **Tournament Scoring App** (`com.summ0.tournamentscoringapp`) is a native Android application written in Kotlin. It serves as the **primary field interface** for ring volunteers and judges during a live tournament event. It connects to the **tournament-server** over the venue's local Wi-Fi network (LAN) using standard HTTP REST calls — no internet connection is required or expected. The application is designed and optimized for use on **Android tablets**.

> **Operational Scope**
> 
The app's responsibilities are scoped to the **live-event operational phase**. It does not handle tournament setup, division configuration, group building, or scheduling — those functions belong to the server-side admin web interface. The app picks up where the server leaves off: once groups are assigned to rings, volunteers use this app to check competitors in, track match progression, enter scores, and flag issues to the head table.

**Key technology choices:**

    
- **Language:** Kotlin (confirmed)
    
- **Target form factor:** Android tablet (8-inch and 10-inch)
    
- **Minimum API level:** TBD — see Section 8
    
- **Network:** HTTP REST calls to the tournament-server on the local LAN; base URL configurable by the volunteer at app startup
    
- **Persistence:** Minimal local state; the server is the authoritative source of truth for all ring and group data; `SharedPreferences` used only for last-used server address
    
- **Third-party dependencies:** No additional frameworks confirmed beyond the standard Android SDK at this time

**Relationship to the server-side system:** The app is a pure REST client. It reads server state, presents it to volunteers, and writes back user actions (check-ins, scores, alerts) via REST calls. The server owns all authoritative data; the app holds only in-memory state derived from the latest server response.

---

  
    

## 3 — Core Screens & Activities

The app is organized around six primary screens. Navigation flows linearly from setup through to match completion, though the Ring Dashboard serves as the persistent hub volunteers return to between tasks.

  
    

### 3.1 — Server Connection / Setup Screen

The **first screen** a volunteer sees on launch. Its sole purpose is to establish connectivity to the tournament-server before any operational screens are accessible. Volunteers enter the server's LAN IP address and port (e.g., `192.168.1.100:3000`).

**Key responsibilities:**

    
- Provide text input fields for server IP address and port number, pre-populated with sensible defaults or the last-used values.
    
- On "Connect" tap, validate input format and perform a connectivity test against a known server endpoint (e.g., `GET /api/rings` or `/health`).
    
- On success: navigate to the Ring Selection screen.
    
- On failure: display a clear, human-readable error message with a retry option. Do not crash or leave the volunteer stranded.
    
- Persist the last-used server address (IP + port) in `SharedPreferences` so volunteers do not need to re-enter it on every launch.

  
    

### 3.2 — Ring Selection Screen

Displays the list of available rings fetched from the server. The volunteer selects their assigned ring to proceed to the Ring Dashboard.

**Key responsibilities:**

    
- Fetch ring list from `GET /api/rings` on the server.
    
- Display each ring's label (e.g., "Ring 1", "Ring A") and its current phase (Scheduled / In Progress / Complete), ideally with a visual phase indicator.
    
- Navigate to the Ring Dashboard for the selected ring on tap.
    
- Handle edge cases: empty ring list (server not yet configured), network error, or server returning an unexpected shape.

  
    

### 3.3 — Ring Dashboard (Main Operational Screen)

The **primary working screen** for a volunteer during the event. Volunteers spend the majority of their time on this screen, navigating out to Check-In or Score Entry as needed and returning here after each task. This screen must always reflect the current server state.

**Key responsibilities:**

    
- Display the currently assigned group name, division name, and competitor count for the ring.
    
- Show the ring's current phase (Scheduled / In Progress / Complete) with a clear visual indicator.
    
- Display the upcoming group queue so volunteers can anticipate what comes next.
    
- Provide navigation controls to Check-In and Score Entry screens.
    
- Provide a phase-advance control (e.g., "Start Group", "Complete Group") that POSTs to the server.
    
- Implement a polling loop (default 5-second interval) to refresh ring state from `GET /api/rings/:ringId`.
    
- Display an offline / reconnecting banner when the LAN connection cannot be reached.
    
- Display incoming broadcast announcements from the head table (fetched via polling).
    
- Provide access to the Alert / Assistance screen.

  
    

### 3.4 — Competitor Check-In Screen (RingCheckInActivity)

Allows the volunteer to confirm which competitors are physically present at the ring before a match begins. This screen must make it fast and error-resistant to check in a list of competitors by tapping.

**Key responsibilities:**

    
- Fetch and display the competitor list for the current group.
    
- Provide a tap-to-toggle check-in control for each competitor (Present / Absent).
    
- Allow marking a competitor as Scratch or Walkover from a contextual control.
    
- Visually distinguish Present / Absent / Scratch states clearly (color, icon, or label — not color alone).
    
- Show check-in progress summary (e.g., "4 of 6 checked in").
    
- Block match start if the minimum check-in threshold has not been met, or allow a volunteer override with an explicit confirmation step.
    
- On submission: POST check-in state to `POST /api/rings/:ringId/checkin`.

  
    

### 3.5 — Score Entry Screen

Presented after check-in is complete. Supports the scoring modes configured on the server. The screen layout adapts to the active scoring mode for the current group's division.

**Key responsibilities:**

    
- Display the current match participants (red corner vs. blue corner, or sequential for kata/forms).
    
- Provide scoring controls appropriate to the mode: numeric point entry for point-based sparring; flag-raise buttons for flag-based sparring.
    
- Display running totals or current point differentials.
    
- Require an explicit confirmation step (e.g., "Submit Score?" dialog) before final submission to prevent accidental entry.
    
- On confirmation: POST result to `POST /api/rings/:ringId/score`.
    
- Disable or lock the entry form after an outcome has been decided for a match to prevent re-submission.

  
    

### 3.6 — Alert / Assistance Screen

Allows the volunteer to send a structured alert to the head table. Alert types mirror those defined on the server. This screen also surfaces incoming messages from the head table.

**Key responsibilities:**

    
- Present a selection of alert types: Score Dispute, Medical Hold, Division Complete, Other.
    
- Provide an optional free-text notes field for additional context.
    
- On send: POST to `POST /api/rings/:ringId/alert` and display a confirmation acknowledgment to the volunteer.
    
- Poll for incoming broadcast announcements from the head table via `GET /api/rings/:ringId/announcements` and display them as a dismissible banner on the Ring Dashboard.
    
- Allow the volunteer to view sent alert history for the current session.

---

  
    

## 4 — Data Model (Local & Server-Synced)

The app does not maintain a local database. All entities below are **in-memory representations** populated by API calls to the tournament-server. The server is the authoritative source of truth. The only data persisted locally is the server address in `SharedPreferences`.

    
- 
      ServerConfig
      Persisted in SharedPreferences. Stores the volunteer's last-used server connection details.
      ip: String  |  port: Int  |  lastConnectedAt: Long (epoch ms)
    

    
- 
      RingState
      Reflects the current state of a single ring as returned by the server. Refreshed on every poll cycle.
      ringId: String  |  ringLabel: String  |  phase: Enum(scheduled, in-progress, complete)  |  currentGroupId: String?  |  currentGroupName: String?  |  queue: List<String>  |  assistanceType: String?  |  assistanceRequestedAt: Long?
    

    
- 
      Group
      Represents a competition group assigned to a ring. Populated from the server when the volunteer navigates to Check-In.
      groupId: String  |  groupName: String  |  divisionName: String  |  competitors: List<Competitor>
    

    
- 
      Competitor
      A single registered competitor within a group. checkedIn and scratchStatus are local-only fields managed by the volunteer during the Check-In flow and POSTed to the server on submission.
      competitorId: String  |  firstName: String  |  lastName: String  |  beltRank: String  |  ageGroup: String  |  gender: String  |  school: String  |  checkedIn: Boolean [local]  |  scratchStatus: Enum(none, scratch, walkover) [local]
    

    
- 
      MatchResult
      Constructed in the Score Entry screen and POSTed to the server. Not persisted locally after submission.
      matchId: String?  |  redCornerCompetitorId: String  |  blueCornerCompetitorId: String  |  redScore: Int  |  blueScore: Int  |  scoringMode: String  |  outcome: Enum(win, loss, bye, DQ, walkover)  |  submittedAt: Long
    

    
- 
      Alert
      Represents an assistance request sent from the ring to the head table. Constructed in the Alert screen and POSTed to the server.
      alertType: Enum(dispute, medical, division-complete, other)  |  ringId: String  |  notes: String?  |  sentAt: Long
    

---

  
    

## 5 — API Integration Points

The app communicates exclusively with the tournament-server over HTTP on the local LAN. No cloud services, no internet connectivity required. All requests use the base URL configured on the Connection screen. The poll interval for refresh endpoints defaults to **5 seconds** and should be configurable without a code change (e.g., via a build config constant or in-app setting).

| Method | Endpoint | Used By | Purpose |
| --- | --- | --- | --- |
| GET | /api/rings | Ring Selection, Ring Dashboard | Fetch all rings and their current state. Used on Ring Selection screen and as a connectivity test on Setup. |
| GET | /api/rings/:ringId | Ring Dashboard (poll) | Fetch a single ring's current state. Called on the poll loop to refresh phase, group, queue, and announcement data. |
| GET | /api/groups/:groupId | Check-In Screen | Fetch the full competitor list for the current group before check-in begins. |
| POST | /api/rings/:ringId/checkin | Check-In Screen | Submit final check-in status (present, absent, scratch, walkover) for all competitors in the current group. |
| POST | /api/rings/:ringId/phase | Ring Dashboard | Advance the ring phase: scheduled → in-progress → complete. |
| POST | /api/rings/:ringId/score | Score Entry Screen | Submit a completed match result including scores, outcome, and scoring mode. |
| POST | /api/rings/:ringId/alert | Alert / Assistance Screen | Send a structured assistance request (dispute, medical, division complete, other) with optional notes to the head table. |
| GET | /api/rings/:ringId/announcements | Ring Dashboard (poll) | Poll for broadcast announcements sent from the head table to this ring. Displayed as a dismissible banner. |

> **Polling vs. Real-Time Push**
> 
The app currently uses HTTP polling on a timer for real-time updates (both ring state and announcements). A WebSocket or Server-Sent Events connection would reduce latency and server load. This is an open design question — see Section 8. The poll interval should be tunable without a code deployment.

---

  
    

## 6 — Integration with the Server-Side System

The Tournament Scoring App is a **pure REST client**. It holds no authoritative state of its own. All ring state, group data, competitor records, match results, and alert history are owned and persisted by the tournament-server. The app reads server state, presents it to volunteers, and writes back user actions via REST calls.

**Implications of this architecture:**

    
- **Crash recovery:** If the app is closed, crashes, or the tablet is rebooted mid-event, relaunching and reconnecting to the server restores the full current state within one poll cycle. No in-progress data is lost because no authoritative data is held locally.
    
- **Multi-device consistency:** Multiple tablets viewing the same ring will see consistent state because all reads originate from the same server. There is no client-side state that could diverge.
    
- **LAN drop handling:** If the network connection is interrupted, the app must surface a clear offline / reconnecting indicator and must not queue writes silently. The policy for handling mid-submission network drops is an open question — see Section 8.
    
- **No local database required:** Because the server is the source of truth, the app does not need SQLite or Room. All data structures are in-memory for the duration of a session.

> **Companion Document Reference**
> 
For full details on the server's data model, route definitions, ring state machine, alert schema, and admin web interface, refer to the *Tournament Management System — Server-Side High-Level Design*. That document and this one together constitute the complete system HLD.

---

  
    

## 7 — Non-Functional Requirements

    
- 
      Availability
      The app must function on the venue LAN with no internet connectivity at any time. If the LAN connection drops, the app must transition to a visible offline state — displaying a reconnecting indicator — and must not crash, blank out, or silently discard work. Operations must resume automatically when connectivity is restored without requiring the volunteer to restart the app.
    

    
- 
      Performance
      Ring state must reflect server-side changes within 2–3 seconds under normal LAN conditions (polling interval ≤ 5 seconds plus round-trip time). Check-in and score submission requests must acknowledge (200 OK or equivalent) within 1 second on a stable LAN. The UI must not block or freeze during background network operations — all HTTP calls must be made off the main thread.
    

    
- 
      Usability
      The app must be operable by a non-technical adult volunteer after no more than five minutes of orientation. All interactive touch targets must be large enough for reliable finger tapping on a tablet (minimum 48dp recommended). The three most common tasks — check in a competitor, submit a score, send an alert — must each be completable in three taps or fewer from the Ring Dashboard. Text must be legible at arm's length on a tablet screen without zooming.
    

    
- 
      Compatibility
      The application targets Android tablets. It must work correctly and without layout degradation on both 8-inch and 10-inch tablet form factors. The minimum API level has not yet been confirmed (see Section 8) and should be set based on the actual tablet hardware available at events.
    

    
- 
      Security
      The app does not implement its own authentication or role enforcement in the current design. Access control relies on physical security — only authorized volunteers hold the event tablets. This is explicitly acknowledged as a gap. Future versions may require per-volunteer authentication before accessing a ring. See Section 8 for the open question on authentication hardening.
    

    
- 
      Recoverability
      If the app crashes mid-match or mid-check-in, relaunching the app and reconnecting to the server must restore the full current ring state within one refresh cycle. No in-progress scoring or check-in data that has already been submitted to the server should be lost. Local-only state (e.g., partially completed check-in not yet submitted) is non-recoverable by design — the volunteer must re-enter it.
    

---

  
    

## 8 — Open Questions & Decisions

    
- 
      OPEN
      Real-time update mechanism
      The app currently polls `GET /api/rings/:ringId` on a configurable timer (default 5 seconds). A WebSocket or Server-Sent Events (SSE) connection to the tournament-server would reduce update latency and eliminate unnecessary polling load. This would require server-side changes to add a WS or SSE endpoint. Decision pending; polling is a workable interim solution.
    

    
- 
      OPEN
      Authentication & role enforcement
      No login mechanism or role check exists in the app currently. Any user who can reach the server on the LAN can take any action. Future versions may require volunteers to authenticate (e.g., PIN, QR code, or credential) before accessing a ring. The server would need to support session tokens or a similar mechanism. Decision pending; physical access control is the current mitigation.
    

    
- 
      OPEN
      Scoring modes supported in app UI
      Point-based sparring is the primary confirmed scoring mode. Flag-based sparring and kata/forms scoring have been identified as future modes but the app UI for those screens has not been designed. The extent of scoring mode support needed at the first live event must be confirmed with the tournament director. This affects scope of the Score Entry screen (Section 3.5).
    

    
- 
      OPEN
      Minimum Android API level
      Not yet confirmed. The minimum API level should be determined by the oldest Android OS version present on the tablet hardware that will be used at actual events. Setting it too low adds maintenance burden; setting it too high may exclude available hardware.
    

    
- 
      OPEN
      Offline write queue policy
      If the LAN connection drops mid-submission (score, check-in, or alert), should the app: (a) queue the write locally and retry automatically on reconnect, or (b) surface an error, discard the in-flight request, and require the volunteer to resubmit? Option (a) is safer for data integrity but more complex to implement correctly. Option (b) is simpler but places burden on the volunteer at a stressful moment. Policy not yet decided.
    

    
- 
      RESOLVED
      Language: Kotlin confirmed
      The application is written in Kotlin. Java interop is available but new code should be Kotlin-first.
    

    
- 
      RESOLVED
      Network architecture: LAN-only, HTTP REST
      The app communicates with the tournament-server over the venue LAN via standard HTTP REST calls. No cloud services, no internet dependency. The server base URL is user-configurable at startup.
    

    
- 
      RESOLVED
      Persistence strategy: server is source of truth
      The app holds only in-memory state derived from server API responses. The only local persistence is the server address (IP + port) stored in SharedPreferences. No local database (SQLite/Room) is required or planned.
    

---

  
    

## 9 — Comprehensive Component Checklist

    **Legend:**   ✅ Complete / Confirmed from code review    🔄 In Progress    ☐ Not Started

  Server Connection / Setup (Section 3.1)

    
- ☐ IP/port input screen with validation
      

        
- ☐ Text fields for server IP and port with sensible defaults
        
- ☐ "Connect" button triggers ping to `GET /api/rings` or `/health` endpoint
        
- ☐ Success: navigate to Ring Selection; failure: show error message with retry
        
- ☐ Persist last-used address in SharedPreferences
        
- ✅ Base URL configurable (confirmed from prior code review)
      

    

  Ring Selection (Section 3.2)

    
- ☐ Fetch and display ring list from server
      

        
- ✅ `GET /api/rings` endpoint exists on server
        
- ☐ Display ring labels, count, and current phase on selection screen
        
- ☐ Navigate to Ring Dashboard on ring tap
        
- ☐ Handle empty ring list (server not yet configured)
      

    

  Ring Dashboard (Section 3.3)

    
- 🔄 Ring state display (current group, phase, queue)
      

        
- ✅ Server provides phase, currentGroupId, currentGroupName, queue in ring state
        
- ☐ Display group name and competitor count on dashboard
        
- ☐ Display current phase with visual indicator
        
- ☐ Implement poll loop (default 5-second interval)
        
- ☐ Show offline / reconnecting banner when LAN is unreachable
      

    

  Competitor Check-In (Section 3.4 — RingCheckInActivity)

    
- 🔄 Check-in UI and state management
      

        
- ✅ `RingCheckInActivity.kt` exists in codebase
        
- ☐ Display competitor list with tap-to-check-in toggle
        
- ☐ Distinguish present / absent / scratch states visually
        
- ☐ Show check-in progress (X of N checked in)
        
- ☐ Block or warn if match started without minimum check-ins
        
- ☐ POST check-in state to server on submission
      

    

  Score Entry (Section 3.5)

    
- ☐ Score entry interface by scoring mode
      

        
- ☐ Build score entry screen for point-based sparring (numeric input)
        
- ☐ Build score entry screen for flag-based sparring (flag raise buttons)
        
- ☐ Confirmation step before final score submission
        
- ☐ POST `/api/rings/:ringId/score` on confirm
        
- ☐ Disable re-submission after outcome is decided
      

    

  Alert / Assistance (Section 3.6)

    
- 🔄 Alert sending and receiving
      

        
- ✅ `assistanceType` and `assistanceRequestedAt` fields confirmed on server
        
- ☐ Build alert type selection UI (dispute, medical, division complete, other)
        
- ☐ Optional notes text field
        
- ☐ POST `/api/rings/:ringId/alert` on send
        
- ☐ Display confirmation toast / message on successful send
        
- ☐ Poll for incoming head table announcements and display as banner
      

    

  Testing & Quality (all not started)

    
- ☐ Unit tests for local data mapping (server JSON → app data model)
      

        
- ☐ Test RingState deserialization from all known server response shapes
        
- ☐ Test Competitor check-in state transitions (absent → present → scratch)
      

    
    
- ☐ Integration tests for API calls
      

        
- ☐ Mock server responses for all key endpoints
        
- ☐ Test error handling: 404, 500, and network timeout
      

    
    
- ☐ UI / usability test with non-technical volunteers on tablet hardware
      

        
- ☐ Test check-in, score entry, and alert flows end to end on device
        
- ☐ Verify all touch targets are reachable on 8-inch and 10-inch tablets
      

    

---

  
    

## 10 — Revision History

| Version | Date | Author | Notes |
| --- | --- | --- | --- |
| 0.1 | 2026-09-18 | Scott | Initial HLD draft for Android client. All sections written; checklist items unchecked except where confirmed from prior code review (base URL configuration, GET /api/rings server endpoint, RingCheckInActivity.kt, assistanceType/assistanceRequestedAt server fields). |