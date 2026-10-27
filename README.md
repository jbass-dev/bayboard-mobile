# BayBoard Mobile

Native Android companion to [BayBoard](https://github.com/jbass-dev/bayboard), the real-time quick-lube shop board ([live web demo](https://bayboard.vercel.app)). Same Firebase project, same Firestore documents: a car checked in at the counter on the web board shows up on a tech's phone right away, and the other way around.

## Features (v0.1)

- **Sign in** with a BayBoard account, or **continue as guest** (Firebase anonymous auth, same as the web demo).
- **Live board**: waiting queue (oldest first) and one section per bay, kept current by Firestore snapshot listeners.
- **Elapsed-time badges** update every 30 s and use colour, a shape marker (● ▲ ■) and a spoken label, so severity isn't shown by colour alone.
- **Ticket lifecycle**: new ticket → assign to a bay and tech → complete, or send back to waiting.
- **Complete with parts**: the ticket status change and the inventory decrements are written in a **single Firestore transaction**, the same guarantee the web app gives.
- **Inventory** tab with restock steppers, plus a low-stock banner on the board that TalkBack announces.

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM with `StateFlow` · Navigation Compose · Firebase Auth + Cloud Firestore · Coroutines/Flow (`callbackFlow` over snapshot listeners) · JUnit.

## Architecture

```
model/   Ticket, TicketStatus (sealed — mirrors the web's discriminated union), Bay, InventoryItem…
logic/   Pure functions ported from the web app: transitions, severity, low stock, next-service date
data/    FirestoreMappers (map <-> model, JVM-testable), BayBoardRepository, AuthRepository
ui/      Compose screens + ViewModels (Board and Inventory share one BoardViewModel)
```

Timestamps are written in the exact `Date#toISOString()` format so the web app's string sorting keeps working on documents written from Android.

## Setup

1. Open this folder in Android Studio and let Gradle sync.
2. In the [Firebase console](https://console.firebase.google.com), open the BayBoard project → **Add app → Android**, package name `com.jbassdev.bayboard`.
3. Download `google-services.json` into `app/`.
4. Make sure **Anonymous** and **Email/Password** sign-in are enabled (they already are if the web demo works).
5. Run on an emulator or device (minSdk 26).

Unit tests: `./gradlew test`, or right-click `app/src/test` → Run tests.
