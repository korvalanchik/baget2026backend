# PRRO shift-open XML envelope

This component wraps the shift-opening `DAT` element in the current Electronic Cabinet gRPC API packet:

- `RQ V="1"`
- shift-open `DAT` with `C T="108"`
- `MAC ID=""`
- XML declaration and Windows-1251 byte encoding

## Previous-check MAC

The official Electronic Cabinet API description states that MAC is the lowercase hexadecimal SHA-256 hash of the previous check. For the first check ever created on a PRRO, MAC is empty. Shift opening uses local number 0 and is sent through `sendChkV2` as `SERVICECHK`.

The builder accepts the exact previous-check XML bytes as returned by the DPS `lastChk` response. It deliberately does not decode, normalize, or reserialize those bytes before hashing. The caller must pass `null` only after confirming that no previous check exists; an empty byte array is rejected.

## Safety boundary

The builder returns unsigned XML bytes. It does not establish that a particular IIT SignWidget/CMS packaging is accepted in `Check.check_sign`, does not sign the payload, and does not submit it to DPS. The resulting bytes must be tested against the DPS developer endpoint before a cashier-facing open-shift endpoint or production fiscalization is enabled.

Never retry an ambiguous `sendChkV2` result without reconciliation.
