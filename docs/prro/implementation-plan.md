
# PRRO module implementation notes

Official sources:
- API: https://cabinet.tax.gov.ua/help/api.html
- DPS API and protocol document index (Electronic Cabinet version 25.02.2025): https://tax.gov.ua/baneryi/programni-rro/opis-ari-fiskalnogo-servera/opis-ari-elektronniy-kabinet/
- XML check and Z-report transfer protocol: https://tax.gov.ua/data/files/530962.docx
- Older protocol link referenced in the API description: https://tax.gov.ua/data/material/000/311/399207/SZZD_RRO_Protokol_peredach_nformats_2_1_7.doc
- DPS notes that local document numbering is continuous per PRRO, separate from fiscal numbering: https://zir.tax.gov.ua/main/bz/view/?id=42290&src=ques

## Foundation
- gRPC/proto3 contract and generated Java stubs are configured in Gradle.
- The transport client is isolated from fiscal business logic.
- Every gRPC call uses a configurable deadline (`BAGET_PRRO_RPC_DEADLINE_MILLIS`, default 15000 ms). A submission timeout is treated as an ambiguous outcome by `PrroSubmissionService` and must be reconciled before any retry.
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

## Shift signer identity

The Electronic Cabinet API documentation states that all checks after a shift is opened must be signed with the same signature. Its `infoRro` response exposes operator information, including the operator's public key identifier in `serial`.

- Migration V95 adds `active_signer_key_id` to `prro_devices` and `signer_key_id` to `prro_operations`. These are identifiers only; they must never contain a private key, password, or signature bytes.
- Preparing a shift-opening operation binds the active shift to the supplied signer key identifier. Further operation preparation rejects a different identifier for that active shift.
- A rejected shift opening and a successfully fiscalized shift close clear the active shift and signer binding. An ambiguous shift-opening result keeps the binding until reconciliation.
- If an already-active shift has no recorded signer identifier (for example, because it predates this migration), new operations fail closed until the shift is reconciled. The service does not guess the signer from historical operations.
- This first pass requires the same signer for shift close as well. The API documents a senior-cashier exception for closing a shift; support for that exception is deferred until the integration can validate senior-cashier authorization from trusted DPS operator data.

This enforces signer identity at the persistence boundary, but does not perform QES signing and does not prove that the supplied key identifier matches the certificate used by a signer adapter. That binding must be checked when the signer integration is introduced.

## XML protocol and canonicalization (version 2.2.3, revision 3.1)

The reviewed protocol document is dated 18 February 2025. For general-purpose receipt packets it describes a `DAT` element containing a `C` receipt, a packet timestamp `TS`, and receipt operations such as `P` (sale item), `M` (payment), and `E` (receipt closing). A Z-report uses a `Z` element within `DAT`. The protocol represents monetary values in minor units (amount in UAH multiplied by 100) and quantities in thousandths (quantity multiplied by 1000).

Appendix A documents canonicalization rules: remove formatting whitespace between tags, expand empty elements to explicit opening/closing tags, normalize spacing in tag syntax, and sort attributes alphabetically. `PrroXmlCanonicalizer` implements these representation-level transformations and is tested against the Appendix A receipt example.
- Ambiguity to resolve before signing: rule 3's prose mentions whitespace in start-tags, while its example also normalizes runs of spaces/tabs in text content. The current utility follows the example; this behavior remains isolated and must be confirmed before it is used to produce signed XML.

- The protocol's XML examples declare `windows-1251`. `PrroXmlCanonicalizer.canonicalizeToWindows1251` now exposes the canonical root element as explicit Windows-1251 bytes and rejects unrepresentable characters rather than substituting replacement bytes. This is a byte-encoding utility for deterministic protocol tests only; it does not establish that those bytes alone are the final `Check.check_sign` payload, nor does it calculate MAC, a previous-Check hash, or QES.

## XML document builder
- `PrroXmlDocumentBuilder` creates canonical, unsigned `DAT` XML payloads for general-purpose sale/return checks (`CHK`) and Z-reports (`ZREPORT`).
- CHK support currently includes `C`, `P`, `M`, `E`, packet `TS`, single-tax closing attributes, and multiple-tax child `TX` elements. ZREPORT support includes `Z`, `TXS`, payment turnover `M`, cash movement `IO`, check counts `NC`, and packet `TS`.
- The input API uses `BigDecimal` values in UAH and natural quantity units. The builder converts money to integer kopecks and quantity to thousandths without rounding; values exceeding the protocol precision are rejected.
- The builder supports only the general-purpose device format (`DT=0`, `V=1`). Specialized RRO formats and less-common receipt operations are intentionally not implemented yet.
- The caller must provide a unique packet identifier `DI` for each PRRO and the fiscal receipt number `NO` separately. These are not inferred from the internal operation local number.
- The output is a canonical, unsigned `DAT` element only. It does not include a `MAC` sibling and is not a complete signed document for `Check.check_sign`; do not send it directly to DPS.

## Signing orchestration boundary

- `PrroDocumentSigner` is an adapter interface for a future signing implementation. Its result contains the exact bytes to pass to `Check.check_sign` and the key ID derived from the actual signing certificate/key.
- `PrroSigningService` accepts only a `PREPARED` operation with a bound signer ID, invokes the adapter, checks that its reported key ID matches the operation's shift binding, computes SHA-256 over the exact returned bytes, and transitions the operation to `SIGNED`.
- The service returns the same signed bytes whose SHA-256 was recorded. The caller must use those exact bytes when building the gRPC `Check`; reserializing or modifying them afterward will cause the submission hash check to fail.
- The signer contract deliberately does not prescribe the MAC, previous-document hash representation, or QES/CMS/XMLDSig container. No concrete signer implementation is registered yet, and Spring only creates the service if both PRRO is enabled and a signer adapter bean exists.
- The adapter must derive its reported key ID from the certificate/key actually used, not echo a configured string. This is an explicit trust boundary and will need provider-specific verification tests.

This orchestration layer does not create the signing input, calculate the previous-document hash, add a MAC, or transmit to DPS. It is infrastructure for connecting those parts after the official payload contract is verified.

## Remaining signing/hash-chain boundary
The separate PRRO gRPC API description says `Check.check_sign` must carry a signed XML document and that each Check XML (except connection ping) includes the hash of the preceding Check XML. The supplied information-transfer document describes `MAC` as a Base64 value calculated for `DAT`, but the reviewed passages do not define the cryptographic MAC algorithm or fully specify how the gRPC API's previous-document hash is encoded together with the QES-signed payload. These are not interchangeable assumptions.

Therefore `PrroXmlDocumentBuilder` and `PrroXmlCanonicalizer` are not connected to `PrroSubmissionService`: neither creates a MAC, hash-chain field, QES signature, nor the final gRPC payload. Those steps must follow an explicitly verified API/signature contract and fixture tests.

## Submission orchestration
- `PrroSubmissionService` accepts only an already signed protocol document; it does not construct XML or perform QES signing.
- Before transmission it verifies that the operation is in `SIGNED`, the local number and document type match, and the SHA-256 hash of `check_sign` matches the stored signed-document hash.
- Submission uses `sendChkV2`. A positive DPS response is stored as `FISCALIZED`; a defined rejection is stored as `REJECTED`.
- gRPC transport failures and undefined DPS status become `UNKNOWN_RESULT`. Do not blindly retry; reconcile with DPS first.

## Unknown-result inspection
- `PrroUnknownResultInspectionService` performs read-only `statusRro` and `lastChk` queries for an operation in `UNKNOWN_RESULT`.
- Before transitioning an operation to `SENDING`, `PrroOperationService` locks the PRRO device row and performs a locking read of active operations. A prior local number in `PREPARED`/`SIGNED` blocks newer sends; any `SENDING` or `UNKNOWN_RESULT` operation blocks all additional sends for that device until it is reconciled. This protects the per-device document chain from concurrent/out-of-order submission across application instances. It does not calculate the protocol's previous-document hash.
- The caller must supply a signed `CheckRequest`; this module does not keep the PRRO private key.
- Inspection results are evidence for reconciliation, not an automatic decision. A last-check result by itself does not prove that the target local operation was accepted, so the service does not mutate operation state or retry submissions.

## Security and production blockers
- The module does not store private keys or sign XML yet.
- XML structure for additional receipt operations, document hash chaining, date-time rules, and QES signature encoding must be implemented only after reviewing the complete current official XML protocol and API signing contract.
- Never retry a timed-out submission blindly. Use DPS status / last-check reconciliation.
- Production endpoint selection must remain an explicit deployment decision, not a client-controlled field.
- Branch authorization and authenticated cashier identity must be wired into protected API endpoints before enabling UI actions.
- Test receipts sent to `cabinet.tax.gov.ua:9443` are not fiscal receipts.

## Next implementation step
Obtain an official fixture or a DPS-provided test vector that specifies the exact signed payload, previous-Check hash encoding, and any MAC requirements. Then implement the provider-specific signer and previous-document hash-chain adapters, test the complete signed bytes against the fixture, and only afterward connect XML generation to submission orchestration.
