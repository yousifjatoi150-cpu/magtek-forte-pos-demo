# GitHub Copilot Instructions

## Android POS App: MagTek Universal SDK + Forte (Backend-Mediated)

This repository is for an Android POS application using Kotlin and Jetpack Compose.

Current baseline (from project config):
- Namespace/application ID: `com.arrow.tappaymentdemo`
- `minSdk = 29`
- `targetSdk = 36`
- Compose UI with Material 3
- Java/Kotlin target: Java 11

Copilot must generate code that respects the architecture and rules below.

---

## 1) Product Context

This app is a **semi-integrated EMV payment client**.

- MagTek device securely handles card + PIN capture and EMV processing.
- Android app orchestrates UX, device state, and transaction lifecycle.
- Backend owns all Forte credentials and calls Forte REST APIs.
- Android app must never call Forte directly.

### High-level flow
1. Cashier starts payment in app.
2. App starts EMV transaction via MagTek SDK wrapper.
3. Customer taps/inserts/swipes card and may enter PIN on device.
4. SDK returns secure payment payload (encrypted/tokens/EMV data).
5. App sends payload to backend over HTTPS.
6. Backend calls Forte and returns approved/declined result.
7. App completes device-side flow and shows outcome/receipt.

---

## 2) Hard Security Boundaries (MUST)

- Never expose Forte API keys/secrets in Android code, resources, or logs.
- Never send Forte credentials from backend to Android.
- Never log PAN, track data, PIN, PIN block, CVV, EMV keys, auth tokens, or secrets.
- Never store raw PAN/track/PIN/CVV in local storage, caches, or analytics.
- Use TLS/HTTPS for all network calls.
- Assume PCI DSS constraints apply to all payment-related code.
- Treat EMV and card artifacts as sensitive; minimize retention and scope.

If a suggestion conflicts with these boundaries, reject it and propose a compliant alternative.

---

## 3) Architecture Rules (MUST)

Use MVVM + Clean Architecture + Repository pattern.

Required direction:
`UI (Compose) -> ViewModel -> UseCase -> Repository -> DataSource (SDK/Network)`

Rules:
- No business logic in `Activity`/`Fragment`/Composable screen functions.
- UI never calls SDK directly.
- UI never calls Retrofit/HTTP directly.
- ViewModel exposes immutable UI state (`StateFlow`).
- Domain layer is SDK/network agnostic.
- Data layer maps external DTOs to internal/domain models.

---

## 4) Package / Module Conventions

Prefer this package structure inside `app/src/main/java/com/arrow/tappaymentdemo/`:

- `presentation/` (screens, ui state, viewmodels)
- `domain/` (models, repository interfaces, use cases)
- `data/` (repository impl, remote/local datasources, mappers)
- `sdk/magtek/` (MagTek wrapper and callback adapters)
- `network/` (backend API client, request/response DTOs)
- `di/` (Koin modules)
- `core/` (result wrappers, errors, common utils)

Do not leak SDK types outside `sdk/magtek` and mapping boundaries.

---

## 5) MagTek Integration Rules

MagTek SDK responsibilities:
- Device discovery and connection management (USB/BLE/WebSocket/MQTT as configured)
- Card read flow (tap/dip/swipe)
- EMV processing and secure card artifacts
- PIN entry on terminal hardware
- Device prompts/status display

Android app responsibilities:
- Start/cancel transaction
- Observe SDK/device state
- Forward secure transaction payload to backend
- Render progress, approval/decline, retries, and receipt

Implementation constraints:
- Wrap SDK behind interfaces (example: `MagTekGateway`, `DeviceManager`, `TransactionController`).
- Keep one owner of SDK lifecycle (singleton manager via DI).
- Do not parse/implement EMV cryptography manually.
- Handle connection method differences via strategy abstractions, not `if` sprawl.

---

## 6) Forte / Backend Integration Rules

- Android calls only your backend endpoints.
- Backend calls Forte REST APIs (`/payments`, capture, void, refund, etc.).
- Android request payload should include only needed transaction context and secure device output.
- Backend is source of truth for authorization outcome and transaction IDs.

Expected backend-facing app capabilities:
- `POST /payments`
- `POST /payments/{id}/capture` (if auth/capture)
- `POST /payments/{id}/void`
- `POST /payments/{id}/refund`
- `GET /payments/{id}`
- `GET /payments/history`

Never generate client code that talks directly to Forte endpoints.

---

## 7) State Modeling (Use Sealed Types)

Use explicit state machines for deterministic flows.

`ConnectionState` examples:
- `Disconnected`
- `Connecting`
- `Connected`
- `Reconnecting`
- `Error`

`TransactionState` examples:
- `Idle`
- `WaitingForCard`
- `ReadingCard`
- `PinEntry`
- `Authorizing`
- `Approved`
- `Declined`
- `Cancelled`
- `Timeout`
- `DeviceError`
- `NetworkError`

Prefer one-way data flow and immutable state updates.

---

## 8) Coroutines + Threading Rules

- Use structured concurrency only (`viewModelScope`, `CoroutineScope` injected where needed).
- Never use `GlobalScope`.
- Run I/O on `Dispatchers.IO`.
- Keep UI-safe updates on main thread.
- Make cancellation explicit for payment flow interruptions.

---

## 9) Dependency Injection

Use Koin for all dependency wiring.

Inject:
- ViewModels
- Use cases
- Repositories
- SDK wrapper/manager
- API clients and mappers

Do not instantiate concrete dependencies directly in UI classes.

---

## 10) Error Handling + Retry

Always model and handle:
- Device disconnected (USB/BLE/WebSocket/MQTT)
- Card removed / card read failure
- PIN timeout/cancel
- EMV/SDK callback errors
- Network timeout/offline
- 4xx/5xx backend errors
- Malformed/empty backend responses

Rules:
- Convert low-level exceptions into typed domain errors.
- Provide user-safe messages (no sensitive internals).
- Add retry paths with backoff where appropriate.
- Always support cancel and safe rollback.

---

## 11) Logging + Observability

Use structured logs (Timber recommended).

Log:
- lifecycle milestones (device connected/disconnected, payment started/completed)
- callback transitions
- backend request correlation IDs (if available)
- non-sensitive error categories

Never log sensitive values.

---

## 12) Coding Standards

- Prefer small, focused classes and interfaces.
- Follow SOLID, DRY, and composition over inheritance.
- Use immutable data classes and explicit mappers.
- Avoid God classes and large multi-responsibility ViewModels.
- Prefer descriptive names (`StartTransactionUseCase`, `PaymentRepositoryImpl`, `DeviceConnectionState`).
- Generated code must compile and align with existing project setup.

Do not generate placeholder `TODO` blocks unless explicitly requested.

---

## 13) Testing Expectations

When adding features, generate tests where practical:
- Unit tests for use cases and ViewModels
- Repository tests with mocked data sources
- SDK wrapper tests via fakes/mocks
- Error-path tests for timeouts/disconnects/declines

Avoid hardcoded sleep-based timing tests.

---

## 14) UI/UX Expectations (Compose + Material 3)

- Keep screens responsive and state-driven.
- Show clear progress for each payment phase.
- Provide actionable retry/cancel controls.
- Handle terminal disconnects gracefully.
- Keep copy concise for cashier workflows.

---

## 15) Implementation Do/Do Not

Do:
- Extend existing architecture first.
- Create interfaces at boundaries.
- Map external models to domain/UI models.
- Keep payment flow deterministic and auditable.

Do not:
- Bypass layers.
- Call Forte directly from app.
- Expose MagTek SDK internals widely.
- Log/store sensitive payment data.
- Block main thread during payment operations.

---

## 16) Copilot Output Checklist (Apply Before Finalizing Code)

For any generated code, verify:
1. Architecture layering is respected.
2. No direct Forte call from Android app.
3. No sensitive data leakage in logs/storage.
4. SDK is wrapped and not leaked through UI/domain layers.
5. State is modeled with `StateFlow` + sealed classes.
6. Error paths are typed and user-safe.
7. Code compiles with this repository's Kotlin/Gradle setup.
8. Tests are added/updated for main logic and failure paths.

---

## 17) Preferred Prompting Pattern for This Repo

When asked to implement a feature, Copilot should:
1. Identify affected layers (`presentation`, `domain`, `data`, `sdk`, `network`, `di`).
2. Propose interfaces/contracts first.
3. Implement domain logic and repository mapping.
4. Add ViewModel state/events/effects.
5. Wire DI modules.
6. Add/update tests.
7. Provide a short integration note.

This instruction file is authoritative for generated code behavior in this repository.

