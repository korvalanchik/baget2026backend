# PRRO module implementation notes

Official sources:
- API: https://cabinet.tax.gov.ua/help/api.html
- API and protocol document index (version 25.02.2025): https://tax.gov.ua/baneryi/programni-rro/opis-ari-fiskalnogo-servera/opis-ari-elektronniy-kabinet/
- XML check and Z-report transfer protocol: https://tax.gov.ua/data/files/530962.docx
- Older protocol link referenced in the API description: https://tax.gov.ua/data/material/000/311/399207/SZZD_RRO_Protokol_peredach_nformats_2_1_7.doc
- DPS note on continuous local numbering: https://zir.tax.gov.ua/main/bz/view/?id=42290&src=ques

## Foundation
- gRPC/proto3 contract and generated Java stubs are configured in Gradle.
- The transport client is isolated from fiscal business logic.
- The default endpoint is the DPS developer endpoint and PRRO is disabled by default.

## Operation persistence
- `prro_devices` holds the registered PRRO identity, environment, enabled flag, active shift key, and next local number.
- `prro_operations` stores idempotency key, request fingerprint, shift key, local number, state, signed-document hash, fiscal document id, and DPS response metadata.
- Device row locking serializes local-number allocation for one PRRO.
- Local document numbers are continuous per PRRO; opening a shift is the protocol exception with local number 0. Opening a new shift must not reset the regular local-number sequence.
- A repeated idempotency key with the same request hash returns the existing operation. Reusing it with a different hash is a conflict.
- An ambiguous network result is `UNKNOWN_RESULT` and must be inspected before further action.

## Submission orchestration
- `PrroSubmissionService` accepts only an already signed protocol document.
- It verifies operation state, local number, document type, and SHA-256 hash before calling `sendChkV2`.
- Confirmed DPS success is stored as `FISCALIZED`; a defined rejection is stored as `REJECTED`.
- gRPC transport failures and undefined DPS status become `UNKNOWN_RESULT`. Do not blindly retry.

## Unknown-result inspection
- `PrroUnknownResultInspectionService` performs read-only `statusRro` and `lastChk` queries for an operation in `UNKNOWN_RESULT`.
- The caller must supply a signed `CheckRequest`; this module does not keep the RRO private key.
- Inspection results are evidence for reconciliation, not an automatic decision. A last-check result by itself does not prove that the target local operation was accepted, so the service does not mutate operation state or retry submissions.

## Security and production blockers
- XML construction, previous-document hash chaining, and QES signature creation are not implemented until the complete official protocol is reviewed.
- Never retry a timed-out submission blindly.
- Production endpoint selection must remain an explicit deployment decision.
- Branch authorization and authenticated cashier identity must be wired into protected API endpoints before enabling UI actions.
- Test receipts sent to `cabinet.tax.gov.ua:9443` are not fiscal receipts.

## Next implementation step
Review the complete protocol and official examples before implementing XML generation, previous-document hash chaining, and QES signing. Then add protocol-fixture tests and a test-server integration test.
