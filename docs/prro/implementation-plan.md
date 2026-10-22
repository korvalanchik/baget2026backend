# PRRO module implementation notes

Official sources:
- API: https://cabinet.tax.gov.ua/help/api.html
- DPS API and protocol document index (Electronic Cabinet version 25.02.2025): https://tax.gov.ua/baneryi/programni-rro/opis-ari-fiskalnogo-servera/opis-ari-elektronniy-kabinet/
- DPS notes that local document numbering is continuous per PRRO, separate from the fiscal numbering assigned by the fiscal server: https://zir.tax.gov.ua/main/bz/view/?id=42290&src=ques

## Foundation
- gRPC/proto3 contract and generated Java stubs are configured in Gradle.
- The transport client is isolated from fiscal business logic.
- The default endpoint is the DPS developer endpoint and PRRO is disabled by default.

## Operation persistence (V94)
- `prro_devices` holds the registered PRRO identity, environment, enabled flag, active shift key, and next local number.
- `prro_operations` holds an idempotency key, request fingerprint, shift key, local number, state, signed-document hash, fiscal document id, and DPS response metadata.
- Device row locking serializes local-number allocation for one PRRO.
- Local document numbers are continuous per PRRO; opening a shift is the protocol exception with local number 0. Opening a new shift must not reset the regular local-number sequence.
- Local numbers are unique within a shift in storage, allowing the special opening number 0 for each shift.
- A repeated idempotency key with the same request hash returns the existing operation. Reusing it with a different hash is a conflict.
- State transitions are explicit. An ambiguous network result is `UNKNOWN_RESULT` and must be reconciled before any further submission.
- A shift close only clears the active shift after a confirmed fiscalized response. A rejected shift opening clears the prepared active-shift marker; an ambiguous result keeps it for reconciliation.

## Security and production blockers
- The module does not store private keys or sign XML yet.
- Signed XML must not be sent until the current official XML protocol and signature/MAC rules are implemented and tested.
- Never retry a timed-out submission blindly. Use DPS status / last-check reconciliation.
- Production endpoint selection must remain an explicit deployment decision, not a client-controlled field.
- Branch authorization and authenticated cashier identity must be wired into protected API endpoints before enabling UI actions.
- Test receipts sent to `cabinet.tax.gov.ua:9443` are not fiscal receipts.

## Next implementation step
Implement the XML document builder and signing adapter only after reviewing the current official protocol document. Then add tests with official sample documents and test-server responses.
