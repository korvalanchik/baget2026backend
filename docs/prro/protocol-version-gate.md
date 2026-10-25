# PRRO XML protocol version decision

## Decision

The implementation target agreed for Baget is the DPS Electronic Cabinet fiscal-server API documentation version **25.02.2025** and its linked XML check/Z-report transfer protocol:

- API and protocol index: https://tax.gov.ua/baneryi/programni-rro/opis-ari-fiskalnogo-servera/opis-ari-elektronniy-kabinet/
- XML check/Z-report protocol: https://tax.gov.ua/data/files/530962.docx
- API description: https://tax.gov.ua/data/files/262576.docx

The selected transport remains the Electronic Cabinet gRPC/proto3 API, using `sendChkV2`. The XML builder/canonicalizer prototype was developed from the linked XML document identified in the implementation notes as version **2.2.3, revision 3.1**, dated 18 February 2025.

## Documentation discrepancy to keep visible

The general API help page https://cabinet.tax.gov.ua/help/api.html also references the older XML protocol 2.1.7. That reference is recorded as a documentation discrepancy; it does **not** replace the explicit project decision to implement against the API documentation package dated 25.02.2025.

Do not silently mix XML rules from protocol 2.1.7, the 25.02.2025 Electronic Cabinet package, or the separate Unified Window API. If a specific signing or payload rule is not explicit in the selected package, keep it marked unresolved and seek an authoritative fixture/clarification rather than guessing.

## Confirmed gRPC facts

The Electronic Cabinet API describes `sendChkV2`, signed XML in `Check.check_sign`, and a previous-Check XML hash chain (except connection ping). The developer endpoint `cabinet.tax.gov.ua:9443` does not issue fiscal receipts.

These transport facts alone do not define every byte of the signed payload, MAC algorithm, or the exact interaction between MAC, previous-Check hash, and QES. Those details must be verified against the selected 25.02.2025 documentation and an authoritative test vector.

## Required safeguards

- Treat the current `DAT` builder/canonicalizer as experimental until its output is verified against the selected protocol's complete rules and examples.
- Do not connect unsigned builder output directly to `PrroSigningService` or `PrroSubmissionService`.
- Do not enable the cashier-facing fiscalization action until signing and payload construction are verified end to end.
- Do not infer that the separate Unified Window API, its `CHECK`/`ZREP` schemas, or CAdES-E-T examples define the gRPC `Check.check_sign` contract.
- Keep the no-blind-retry behavior for `UNKNOWN_RESULT`.

## Next verification step

Use the 25.02.2025 API description and its linked XML protocol as the implementation baseline. Verify receipt/Z-report structure, MAC, encoding, canonicalization, and previous-Check hash handling against the full document and authoritative fixtures. Then implement the concrete signing/hash-chain adapter and test the exact bytes sent in `Check.check_sign`.
