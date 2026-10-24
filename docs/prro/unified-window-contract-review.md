# Unified Window PRRO API: reviewed contract and scope boundary

## Sources reviewed

- **Fiscal Server API description**, version 08.08.2025: https://tax.gov.ua/data/files/578893.docx
- **API change log**, latest entry 17.08.2026: https://tax.gov.ua/data/files/704001.docx
- XSD files supplied for review: `check01.xsd`, `ticket01.xsd`, and `zrep01.xsd`.
- Signed examples supplied for review: a signed Z-report and a signed `TransactionsRegistrarState` request.

The change log says the XSDs were updated and adds optional `IPNBUYER` to the check structure in the 17.08.2026 entry. The checked-in API description itself is marked version 08.08.2025, so its prose version and the more recent schema/update artifacts should not be represented as one release without qualification.

## Contract confirmed from the Unified Window API description

### Transport and content types

The document describes an HTTP POST API with these paths:

- `/doc`: send a signed XML fiscal document.
- `/pck`: send an offline-document package.
- `/cmd`: execute commands.

The documented fiscal-server URLs are `https://fs.tax.gov.ua:8643/fs` and `http://fs.tax.gov.ua:8609/fs`.

For signed requests the API describes `Content-Type: application/octet-stream`; unsigned command requests use `application/json` or `application/json; charset=UTF-8`. XML text is encoded as Windows-1251. Compression, when used, must be described by `Content-Encoding` as `gzip` or `deflate`.

The command API also documents `SendDocument`, which wraps a signed document as Base64 in a JSON request and returns a Base64 `Ticket`. That command flow must be distinguished from posting the signed XML document directly to `/doc`.

### Signature container

The API description requires DSTU 4145-2002 and specifies CAdES-E-T. Signed content is embedded in the CMS message; the signer certificate must be included. The specification excludes issuer certificates, CRLs, and online certificate-check results from the signed message. For the time stamp, it specifies a signature-time-stamp and disallows a content-time-stamp; it says the time stamp is not mandatory for offline documents.

The provided `Z-звіт.xml.signed` is structurally a DER CMS SignedData object with encapsulated XML. Its embedded document has root `ZREP` and validates against the supplied `zrep01.xsd`. The supplied signed state request also has encapsulated JSON, whose command is `TransactionsRegistrarState`. These observations confirm the examples' container and payload shapes; they are not a claim that this environment independently verified the DSTU cryptographic signatures or certificate trust.

The API describes a maximum request payload of 200 KB before compression. Offline document packages contain individually signed documents and are themselves signed; a package may contain at most 100 documents.

### Querying state and reconciling documents

The signed `TransactionsRegistrarState` command returns, among other fields, shift state and ID, current/next local numbering, last fiscal number, signer identity, online/offline capability, and offline-session state. The response only includes some `Offline…` fields when the command is signed by the shift-opening signer, a senior user, or a manager.

The API also documents `DocumentInfoByLocalNum` and check/Z-report lookup commands. A command error may be returned as plain text by default; adding `resultAsJson=true` to the `/cmd` URL makes command errors return HTTP 200 with JSON `ErrorCode` and `ErrorMessage`. Implementations must inspect that response body rather than interpreting HTTP 200 alone as command success.

## Important boundary: this is not the selected gRPC contract

The active Baget implementation targets the **Electronic Cabinet gRPC/proto3 API** documented at https://cabinet.tax.gov.ua/help/api.html. It submits a signed payload through `Check.check_sign` and builds the separate `DAT` protocol payload described by https://tax.gov.ua/data/files/530962.docx.

The supplied Unified Window examples use a different transport and legacy document structure: the example root is `ZREP` and the companion schema is `zrep01.xsd`; check documents use `CHECK` / `check01.xsd`. The current Baget builder instead creates `DAT` containing `C` or `Z` protocol elements. The Unified Window CMS sample, its HTTP endpoints, and its XSDs therefore must **not** be wired into the existing gRPC `sendChkV2` flow as a shortcut.

These artifacts are useful for a separately scoped Unified Window adapter, but they do not settle the gRPC API's specific previous-Check XML hash chain, MAC construction, or exact signed-payload representation. Those remain blockers for a concrete signer on the current transport.

## Recommended implementation boundary

1. Keep the current gRPC path isolated and unchanged until its own signed-payload fixture/specification is complete.
2. If Unified Window support is required, implement it as a separate transport/protocol adapter with its own DTOs, `CHECK`/`ZREP` XML builders, XSD validation, CAdES-E-T signing provider, response-ticket parser, and reconciliation rules.
3. Do not put the signed user-provided samples, embedded certificate data, signatures, or taxpayer-specific payloads into the repository. Convert only sanitized, non-sensitive structural facts into regression tests.
4. Keep submission ambiguity fail-closed: a timeout or connection drop does not justify blindly resending a fiscal document.
