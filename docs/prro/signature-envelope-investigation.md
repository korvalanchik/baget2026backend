# PRRO signature-envelope investigation

## Purpose

Determine what the configured signing provider actually emits for the same PRRO XML input in detached and attached modes. This is an offline diagnostic procedure only; it does **not** establish which format the DPS Electronic Cabinet gRPC API accepts.

The public API describes `Check.check_sign` as carrying a signed XML document, but the exact CMS packaging contract remains unresolved. Do not connect either experimental output to real fiscalization until DPS confirms the payload contract or an authoritative test fixture establishes it.

## Controlled comparison

Use one fixed, non-production XML byte sequence for both variants. Do not regenerate or re-encode the XML between runs.

1. Save the exact input bytes as `check.xml.bin`. Record its SHA-256 and byte length.
2. Use the provider's supported signing API to create:
   - Variant A: detached signing, with the same certificate-inclusion setting as Variant B.
   - Variant B: attached signing, with the same certificate-inclusion setting as Variant A.
3. Save outputs as `check-detached.der` and `check-attached.der`. Do not commit the output files, certificates, private-key material, PINs, or real customer data.
4. Record provider/library version, API method and parameters, signing certificate's public serial/fingerprint, output byte length, and output SHA-256. Do not record secrets.
5. Confirm whether each output is parseable as a DER CMS ContentInfo. Inspect whether SignedData contains encapsulated content (`encapContentInfo.eContent`) or omits it. This distinguishes attached from detached packaging structurally; it does not prove DPS compatibility.
6. Where the provider's algorithm is supported by the local verification tool, verify the signature and compare extracted content byte-for-byte with `check.xml.bin`. If the tool does not support the provider's algorithm, report that limitation rather than treating a parse failure as proof the format is wrong.

## Optional OpenSSL inspection

For CMS formats and algorithms supported by the installed OpenSSL build, inspect the ASN.1/CMS structure:

```sh
openssl cms -cmsout -print -inform DER -in check-attached.der
openssl cms -cmsout -print -inform DER -in check-detached.der
```

For an attached CMS signature, attempt to verify and extract its embedded content:

```sh
openssl cms -verify -binary -inform DER -in check-attached.der -noverify -out extracted.xml.bin
```

For a detached signature, the original content is needed for verification:

```sh
openssl cms -verify -binary -inform DER -in check-detached.der -content check.xml.bin -noverify -out verified.xml.bin
```

These commands are diagnostic examples, not a mandated DPS workflow. OpenSSL may not support the signature algorithm used by a Ukrainian QES provider. `-noverify` skips certificate-chain validation; it does not mean the command should be treated as proof of signer trust. A successful CMS parse alone does not validate the signature or certificate.

## Results table

Record results without attaching signature bytes:

| Check | Detached variant | Attached variant |
|---|---|---|
| Input XML SHA-256 | same fixed value | same fixed value |
| CMS structure parsed | yes/no + tool/version | yes/no + tool/version |
| Encapsulated content present | yes/no/unknown | yes/no/unknown |
| Signature cryptographically verified | yes/no/unsupported | yes/no/unsupported |
| Extracted content matches input byte-for-byte | yes/no/not applicable | yes/no/not applicable |
| Provider/version and safe certificate identifier | record locally | record locally |
| DPS test endpoint result (if authorized/available) | response code/message | response code/message |

## Decision gate

- Structural inspection identifies the packaging produced by each signing mode; it does not select the mode for DPS.
- A generic CMS verifier accepting one variant does not prove that `sendChkV2` accepts it.
- Do not infer the gRPC contract from the separate Unified Window API or from the `.p7s` filename extension.
- Before enabling submission, obtain an authoritative DPS answer or fixture covering the exact bytes expected in `Check.check_sign`, including XML encoding/canonicalization, MAC, previous-Check hash, signature/container format, and certificate/signature requirements.
- Never send real fiscal documents to the developer endpoint as if they were production receipts. Never retry an ambiguous submission without reconciliation.
