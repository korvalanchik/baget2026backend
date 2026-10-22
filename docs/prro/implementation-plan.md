# PRRO integration with the State Tax Service (DPS)

## Goal

Implement a first-party PRRO module in the Baget Spring Boot backend, using the official DPS fiscal-server gRPC protocol. This is an integration project, not a replacement for the fiscal-server or the legally required registration/signing procedures.

Official protocol documentation: https://cabinet.tax.gov.ua/help/api.html

## Confirmed protocol facts

- Production endpoint documented by DPS: `prro.tax.gov.ua:443`.
- Alternate endpoint documented by DPS: `prro2.tax.gov.ua:443`.
- Developer/test endpoint: `cabinet.tax.gov.ua:9443`; receipts sent there are not fiscal receipts.
- Transport is gRPC with proto3.
- Use `ChkIncomeService.sendChkV2`; legacy `sendChk` is deprecated in the published documentation.
- A check/Z-report XML document must be signed and sent in `Check.check_sign`.
- The signed XML chain must include the previous document hash (except the connectivity ping); local check numbers and signed document order must be persisted.
- One signing key is used for all checks within a shift, according to the documentation.
- Shift opening is a service check with local number 0; shift closing sends a Z-report.
- The API exposes `ping`, `lastChk`, `statusRro`, and `infoRro` for connectivity/reconciliation/health checks.

## Backend design

Keep this module isolated under `com.example.baget.prro` and keep it separate from the existing ledger/payment source of truth.

Initial components planned:
- `PrroProperties`: endpoint and environment configuration; test endpoint is the safe default.
- `DpsPrroGrpcClient`: gRPC transport only; no receipt business logic.
- `PrroDocumentSigner`: signing abstraction, implemented only after supported key formats and signature requirements are verified.
- `PrroReceiptService`: orchestrates receipt lifecycle and validates payment linkage.
- `PrroShiftService`: explicit open/close shift operations and state checks.
- Persistent operation/receipt records with idempotency keys, local sequence numbers, signed payload hash, request/response status, fiscal number, and timestamps.
- Controller endpoints protected by existing Spring Security and branch authorization.

## Safety invariants

1. Never mark a receipt as fiscalized unless DPS returns a successful response and the response is persisted.
2. A timeout is an ambiguous result, not permission to resend blindly. Reconcile with `lastChk`/status and stored local sequence before retrying.
3. Never store private signing keys in source control or ordinary application configuration.
4. Do not log key material, signatures, or full signed receipt payloads.
5. Test and production endpoints must be explicitly separated. The test endpoint must be the default.
6. Do not send a receipt directly from the Vue browser; all DPS calls happen in the backend.
7. Existing Baget ledger/payment data remains the financial source of truth. PRRO records reference a payment/operation and do not replace ledger entries.

## Delivery phases

1. Protocol contract and architecture (this change).
2. Add Gradle protobuf/gRPC generation and a compile-time generated client.
3. Implement transport and mock-based tests for DPS responses/errors.
4. Verify the official XML protocol, hash-chain construction, signing/key storage, and shift semantics.
5. Add Flyway persistence and backend endpoints with authorization.
6. Add Vue cashier UI, receipt print/share, and reconciliation.
7. Run full Gradle tests and test only against the DPS developer endpoint until an authorized production rollout is approved.

## Open protocol checks before production

- Obtain the complete current XML protocol/XSD and exact signature/MAC construction rules.
- Verify the required signing provider/key container and whether signing can safely run in Cloud Run or needs a local signing agent.
- Confirm the legally required offline behavior, reserve-number workflow, receipt return/correction semantics, and fiscal receipt delivery requirements.
- Verify the registered RRO and cashier details in the taxpayer's own Electronic Cabinet.
