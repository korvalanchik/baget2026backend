# PRRO XML protocol version gate

## Decision

The selected transport is the DPS Electronic Cabinet gRPC/proto3 API:
https://cabinet.tax.gov.ua/help/api.html

That API page explicitly links the XML check/Z-report protocol **2.1.7**:
https://tax.gov.ua/data/material/000/311/399207/SZZD_RRO_Protokol_peredach_nformats_2_1_7.doc

The current prototype's `PrroXmlDocumentBuilder` and `PrroXmlCanonicalizer` were developed from a different reviewed document, **2.2.3, revision 3.1**, dated 18 February 2025:
https://tax.gov.ua/data/files/530962.docx

The available sources do not establish that 2.2.3/revision 3.1 supersedes or is compatible with the version linked by the selected gRPC API. This is an unresolved compatibility blocker, not a cosmetic documentation difference.

## What is confirmed about the gRPC API

The official API page specifies `sendChkV2`, with signed XML carried in `Check.check_sign`. It describes SHA-256 hexadecimal hashing of the preceding Check XML and says the first check's MAC is not populated. The developer endpoint `cabinet.tax.gov.ua:9443` does not issue fiscal receipts.

These facts do not specify a complete reproducible signed-payload fixture or resolve the protocol-version mismatch.

## Required safeguards

Until the version relationship is verified against an authoritative DPS specification or fixture:

- Treat the existing `DAT` builder/canonicalizer as experimental protocol utilities.
- Do not connect their output to `PrroSigningService` or `PrroSubmissionService`.
- Do not enable a cashier-facing fiscalization action.
- Do not infer that the separate Unified Window API, its `CHECK`/`ZREP` XML schemas, or CAdES-E-T examples define the gRPC `Check.check_sign` contract.
- Keep the current no-blind-retry behavior for `UNKNOWN_RESULT`.

## Next verification step

Compare the API-linked 2.1.7 document with the reviewed 2.2.3/revision 3.1 document, recording differences in receipt/Z-report structure, MAC, encoding, canonicalization, and previous-Check hash handling. Then obtain or create tests from an authoritative fixture that fixes the exact bytes to hash and sign. Only after that should a concrete signing adapter be implemented.
