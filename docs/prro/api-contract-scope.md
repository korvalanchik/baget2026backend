# PRRO API contract scope

## Selected transport for this implementation

The Baget PRRO integration currently targets the fiscalization API documented by the DPS Electronic Cabinet:

- API description: https://cabinet.tax.gov.ua/help/api.html
- API description index (Electronic Cabinet version 25.02.2025): https://tax.gov.ua/baneryi/programni-rro/opis-ari-fiskalnogo-servera/opis-ari-elektronniy-kabinet/
- XML receipt and Z-report protocol linked from that index: https://tax.gov.ua/data/material/000/311/399207/SZZD_RRO_Protokol_peredach_nformats_2_1_7.doc

The Electronic Cabinet API page specifies gRPC with `proto3`. Use `sendChkV2` for check submission; the page identifies `sendChk` as the legacy method. The signed XML payload is carried in `Check.check_sign` (protobuf `bytes`). The production endpoints listed by DPS are `prro.tax.gov.ua:443` and the additional `prro2.tax.gov.ua:443`; the developer endpoint is `cabinet.tax.gov.ua:9443`. DPS explicitly warns that documents submitted to the developer endpoint are not fiscal receipts.

Read-only inspection methods relevant to reconciliation include `statusRro`, `infoRro`, and `lastChk`. A transport timeout or unknown response must not trigger a blind resend. `lastChk` alone does not prove that one particular local operation was accepted; reconciliation must correlate the available DPS evidence with the persisted operation.

## Separate DPS documentation branch: Unified Window

DPS also publishes a separate page titled “Опис АРІ фіскального сервера контролюючого органу (Єдине вікно подання електронної звітності)”, version 17.08.2026:

- https://tax.gov.ua/baneryi/programni-rro/opis-ari-fiskalnogo-servera/opis-ari--kontrolyuyuchogo-organu/
- API document: https://tax.gov.ua/data/files/578893.docx
- Update history: https://tax.gov.ua/data/files/704001.docx
- Examples archive: https://tax.gov.ua/data/files/583353.zip
- XSD archive: https://tax.gov.ua/data/files/704002.zip

These are separately published artifacts for a separately named API/documentation track. Do not assume that its interface definitions, example payloads, or XSD files are the contract for the Electronic Cabinet gRPC `Check` API. They may be compared as supporting evidence, but any shared or different behavior must be established from the documents rather than inferred from their similar subject matter.

## QES test vectors

The Central Certification Authority publishes test examples and technical specifications intended to check the compatibility of qualified electronic-signature formats, protocols, and interfaces:

- https://czo.gov.ua/testexamples

Those examples can validate the selected signing library's supported algorithms and signature/container formats. They are not, by themselves, a DPS PRRO fixture and do not specify how a signed XML document must be placed into `Check.check_sign`.

## Previous-Check hash: confirmed rule and remaining ambiguity

The current Electronic Cabinet API description states that each Check XML (except a connection ping) carries the hash of the preceding Check XML, calculated with SHA-256 and represented as a hexadecimal string. For the first check, the MAC value is not populated. The implementation must not confuse this hash chain with the legacy RRO protocol's Base64 MAC, or with the separate Unified Window API.

The remaining uncertainty is the exact byte sequence that DPS expects to be hashed and then signed as the full `Check.check_sign` payload: the docs identify the previous Check XML but do not provide a reproducible complete signed test vector that settles encoding, canonicalization, envelope, signature, and timestamp ordering. `PrroPreviousCheckHash` therefore hashes only the exact bytes supplied by its caller and deliberately does not choose or transform that representation.

## Contract items still requiring an authoritative fixture or explicit specification

Do not implement these using guesses or by substituting a generic XAdES/CAdES/PAdES example:

1. The exact previous-Check XML bytes that must be passed into the SHA-256 hash function and the corresponding value placed in the MAC element.
2. The exact QES signed-document representation expected in `Check.check_sign`, including XML envelope/canonicalization, character encoding, and timestamp behavior.
3. The exact source and normalization of the signer `publicKeyId` to compare against the operator identity reported by DPS.

The reviewed source pages establish that signed XML is submitted, the previous-Check hash is SHA-256 hex, and the shift is constrained to one signer (with a documented senior-cashier exception for shift closing). They do not alone provide a complete reproducible cryptographic test vector for the full submission payload.

## Acceptance criteria before enabling a concrete signer

- Keep private keys, key passwords/PINs, and certificate private material out of source control, the database, and application configuration.
- Build a provider-specific adapter only after recording the protocol version and obtaining an authoritative sample or explicit specification for the complete signed payload.
- Add deterministic tests for canonicalization, hash inputs, signature verification, signer-key identity binding, and exact output bytes.
- Confirm the complete payload in the DPS developer environment before considering any production endpoint.
- Preserve the current fail-closed behavior for unknown signing identities and the no-blind-retry policy for ambiguous submissions.
