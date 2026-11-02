# PRRO shift-open preparation

## Endpoint

`POST /api/prro/shift/open/prepare`

This endpoint prepares an online shift-opening `SERVICECHK` envelope for signing in the cashier's IIT SignWidget. It does not sign the XML and does not call `sendChkV2`.

Request:

```json
{
  "branchNo": 8,
  "fiscalNumber": "4000318883",
  "rroSignatureBase64": "<IIT signature of the PRRO fiscal number>",
  "signerKeyId": "<identifier of the certificate selected in IIT>",
  "idempotencyKey": "<unique key for this open-shift attempt>"
}
```

The fiscal-number signature is used for `infoRro`, `statusRro`, and `lastChk`. The backend checks branch access, the configured device, DPS registration/status, online state, and that DPS reports the shift closed.

The taxpayer number (`TN`) is taken from `RroInfoResponse.tins`. The supplied `signerKeyId` must match a serial in the operator list returned by `infoRro`; otherwise preparation is rejected. The `DI` packet identifier is the persisted PRRO operation ID. The service uses local number `0`, document type `SERVICECHK`, and the shift-open command `C T="108"`.

## Hash chain

The service reads the previous document from `CheckResponse.data_sign`. If the response contains bytes, the builder hashes those exact bytes with SHA-256 and places the lowercase hexadecimal digest in `MAC`. The bytes are not decoded, normalized, or serialized again. An empty `data_sign` with an `OK` response is treated as the first document and produces an empty MAC.

The prepared unsigned XML, its SHA-256, the previous-document SHA-256, and the DPS `date_time` are persisted with the operation. Retries using the same idempotency key receive the same stored XML; they do not rebuild it with a new timestamp or a different previous document.

Response includes `signingInputBase64`, the exact Windows-1251 XML bytes the browser must decode and pass to IIT SignWidget. After signing, the exact payload returned by IIT must be preserved byte-for-byte for the eventual `Check.check_sign`.

## Signing identity

`signerKeyId` is supplied by the browser from the certificate selected in IIT and is bound to the prepared shift. The backend checks that the identifier appears in the operator list returned by DPS `infoRro`. This does not cryptographically prove that the browser later signs with that key; DPS validates the actual signature and registered cashier certificate when the signed document is submitted.

## Safety boundary / next step

This stage only prepares the XML. It does not mark the shift open and does not send anything to DPS. The next stage must accept the exact IIT-signed payload, validate the operation and signer binding, persist the payload, and submit a `Check` with `local_number=0`, `check_type=SERVICECHK`, and the same `date_time` returned here.

A non-OK `lastChk` response is treated as an error; it is not interpreted as proof that no previous document exists. The empty-first-document behavior must be confirmed against the DPS developer endpoint before production use.
