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

`MainActivity` is the launcher and owns the current production workflow. The app is effectively a single Compose-driven control board with layered screens and dialogs rather than a set of loosely coupled activities. A separate `ringcheckin/RingCheckInActivity` still exists in the codebase, but it is not the launcher and is not the primary path used by the current app flow.

### 3.1 — Launch / Server Connection Screen

The first UI shown by `MainActivity` when the app has not yet connected to a ring. Volunteers can switch between DNS and IPv4 modes, edit the server address, and retry the connection.

**Key responsibilities:**

- Validate the server address based on the selected mode.
- Probe `GET /api/health` before loading ring configuration.
- Fetch available ring slots from `GET /api/rings/config?tabletLabel=...`.
- Persist the selected mode plus the last DNS name / IP address in `SharedPreferences`.
- Show a clear failure message and allow retry without restarting the app.

### 3.2 — Ring Selection Screen

Once the server is reachable, the launch screen turns into a ring picker. Rings are rendered as a selectable grid keyed by ring label, with unavailable rings visually disabled.

**Key responsibilities:**

- Render each ring label and availability/status text.
- Let the volunteer select a ring from the grid.
- Connect the selected ring via `POST /api/rings/:ringId/request-group`.
- Handle the empty-ring state when the server reports no configured rings.

### 3.3 — Check-In / Control Board

This is the main operational screen after a ring is connected. It shows the active group banner, checked-in count, entrant summary, judge-count toggle, assistance controls, reconnect controls, and the next competition action.

**Key responsibilities:**

- Display the loaded group name and ring label.
- Track competitor check-in state locally in memory.
- Toggle sparring opt-out status for competitors.
- Offer assistance actions and reconnect actions while assigned to a ring.
- Advance into the scoring flows when a group is loaded.
- Keep the screen alive with periodic heartbeat calls and reconnect handling.

### 3.4 — Weapons Scoring Screen

Local scoring flow for weapons/hyungs-style forms judging. Judges enter numeric scores per competitor, the app computes totals, and tie-break dialogs are used when needed.

**Key responsibilities:**

- Accept 3-judge or 5-judge score entry.
- Drop high/low scores when applicable.
- Finalize placements with tie-break resolution.
- Capture judge-choice tie-breaks when a second pass is required.

### 3.5 — Hyungs Scoring Screen

Same scoring engine as weapons, but with the hyungs catalog and related placement flow.

**Key responsibilities:**

- Reuse the same numeric scoring and placement logic.
- Support tie-break recovery and re-finalization.
- Preserve per-competitor score state in memory for the current session.

### 3.6 — Sparring Bracket / Overall Awards / Assistance

Sparring is handled as a local bracket workflow with bout dialogs, winner progression, and an overall awards screen that compiles review lines and completion packets. Assistance is a modal dialog that posts medical/arbitrator/general requests and can also clear an existing request.

**Key responsibilities:**

- Build and render a single-elimination sparring bracket.
- Open bout dialogs, record winners, and advance rounds.
- Generate the final round packet, PDF/PNG summary, and local receipt files.
- Submit and clear assistance requests for the current ring.

---

  
    

## 4 — Data Model (Local & Server-Synced)

The app keeps competition state in memory and persists only a few durable artifacts on disk. `SharedPreferences` stores the last-used server connection details; completed division packets, PDFs, PNGs, and standings live under `files/TournamentScoringApp/Results`.

- `ServerConnectionConfig` stores the current connection mode plus the last DNS name / IP address.
- `RemoteGroup`, `RemoteCompetitor`, `RingAssignment`, and `RingOption` model server responses during group requests and ring selection.
- `GroupBanner` tracks the currently loaded group shown in the header.
- `Competitor`, `Division`, `CompetitionEntry`, and the scoring maps in `MainActivity` hold the live in-memory tournament workflow.
- `SparringBoutProgress`, `PlacementFinalizeState`, `PendingTieBreak`, and `SparringBoutAssessment` hold transient scoring state for the current session.
- Completed division artifacts are written locally so the app can rebuild championship standings and reopen prior results without the server.
    

---

  
    

## 5 — API Integration Points

The app talks to the tournament server over HTTP on the venue LAN. The current code uses these endpoints:

| Method | Endpoint | Used By | Purpose |
| --- | --- | --- | --- |
| GET | `/api/health` | Launch / server config | Connectivity probe before loading ring options. |
| GET | `/api/rings/config?tabletLabel=...` | Launch / ring selection | Fetch ring slots and availability for the tablet. |
| POST | `/api/rings/:ringId/request-group` | Ring connect | Bind the tablet to a ring and receive the active group. |
| POST | `/api/rings/:ringId/heartbeat` | Connected workflow | Keep the server informed of the tablet's phase and label. |
| POST | `/api/rings/:ringId/complete` | Overall awards / completion | Upload the completed division packet and advance to the next group. |
| POST | `/api/rings/:ringId/assistance` | Assistance dialog | Request medical/arbitrator/general assistance. |
| POST | `/api/rings/:ringId/assistance/clear` | Assistance dialog | Clear the current assistance request. |
| GET | `/api/version` | Update check | Compare the server app version to the installed client. |
| GET | `/download-app` | Update flow | Download the updated APK when a newer version is available. |

Refresh behavior is not a generic ring-state poll; the app refreshes launch availability while disconnected, sends heartbeats while assigned, and retries completion while waiting for the next group.


---

  
    

## 6 — Integration with the Server-Side System

The app is not a strict thin client. It uses the server for ring assignment requests, heartbeats, assistance, completion, and update checks, while the competition workflow itself lives in memory on the client.

**Implications of this architecture:**

- A reconnect restores the ring assignment and active group, but local scoring/check-in state stays in the app.
- Completed division packets are persisted locally so prior results can be reopened after a restart.
- Network failures are surfaced in the UI with reconnect messaging; the app does not silently queue arbitrary writes.

For server-side route details and ring assignment logic, refer to the server HLD.

---

  
    

## 7 — Non-Functional Requirements

- **Availability:** The app stays usable on the venue LAN without internet access and shows reconnect status when the server drops.
- **Performance:** HTTP calls run off the main thread; the code uses explicit connect/read timeouts and coroutine-based background work.
- **Usability:** The UI adapts to compact and wide tablet layouts, with larger control surfaces for scoring and check-in.
- **Compatibility:** `minSdk` is 26 and the app targets modern Android tablets.
- **Security:** No authentication or role enforcement exists yet.
- **Recoverability:** Completed division artifacts are saved locally so prior results can be reopened after a restart.
    

---

  
    

## 8 — Open Questions & Decisions

- **OPEN:** Authentication / role enforcement. No login or tablet-user validation is implemented.
- **OPEN:** Whether the remaining ring-assignment workflow should stay client-driven or move more of it onto the server.
- **OPEN:** Whether live announcements or richer server push should be added later.
- **RESOLVED:** Kotlin is the implementation language.
- **RESOLVED:** The app is LAN-only and uses HTTP.
- **RESOLVED:** Connection settings are persisted in `SharedPreferences`.
- **RESOLVED:** Local results are written to app files; no SQLite/Room database is used.
    

---

  
    

## 9 — Comprehensive Component Checklist

**Legend:** ✅ Complete / Confirmed from code review · 🔄 In Progress · ☐ Not Started

### Server Connection / Launch

- ✅ DNS/IP mode selection, validation, and saved defaults
- ✅ Connectivity probe via `GET /api/health`
- ✅ Ring configuration fetch via `GET /api/rings/config`
- ✅ Last-used server settings persisted in `SharedPreferences`
- ✅ Ring request-group flow via `POST /api/rings/:ringId/request-group`
- 🔄 Live ring-state dashboard / server push is not implemented yet

### Check-In / Control Board

- ✅ Check-in list, tap-to-toggle status, and sparring opt-out
- ✅ Checked-in count and entrant summary
- ✅ Assistance button and reconnect handling
- 🔄 No server POST for check-in state in the current MainActivity flow

### Scoring / Awards

- ✅ Weapons scoring screen with judge entry and tie-break handling
- ✅ Hyungs scoring screen with judge entry and tie-break handling
- ✅ Sparring bracket, bout dialog, and winner progression
- ✅ Overall awards screen, packet generation, and local receipt persistence
- 🔄 No server POST for score submission in the current MainActivity flow

### Assistance / Updates

- ✅ Medical / arbitrator / general assistance requests and clear action
- ✅ Version check plus APK download/install flow
- ☐ Announcements banner and sent-assistance history

### Tests

- ✅ Engine and scoring logic unit tests in `ExampleUnitTest`
- ✅ Basic instrumentation smoke test in `ExampleInstrumentedTest`
- ☐ UI / integration tests for launcher, check-in, scoring, and assistance flows
      

    

---

  
    

## 10 — Revision History

| Version | Date | Author | Notes |
| --- | --- | --- | --- |
| 0.1 | 2026-09-18 | Scott | Initial HLD draft for Android client; checklist updated to reflect the current MainActivity flow, local scoring workflow, update flow, and existing test coverage. |