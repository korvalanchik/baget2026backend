# PRRO shift-open DAT builder

This change adds a narrow, unsigned XML builder for the DAT portion of a shift-opening service document.

## Confirmed fields

The stored Electronic Cabinet API description (262576.docx) gives the opening-shift example with DAT attributes FN, TN, empty ZN, DI, and V="1"; service document C with T="108"; and TS in YYYYMMDDHHMMSS format. It specifies sendChkV2, Check.Type.SERVICECHK, and local_number = 0.

The selected information-transfer protocol document 530962.docx is version 2.2.3, revision 3.1. Its generic packet format describes DAT and MAC as separate elements. The API-specific example uses an ID attribute for MAC and describes a previous-Check SHA-256 hash. These must not be conflated with the generic protocol's DI/NT MAC attributes.

## Explicit scope boundary

PrroShiftOpenDatBuilder returns only the unsigned DAT element. It is not a complete XML envelope and must not be passed to the signer or submitted to DPS as-is.

This PR intentionally does not construct the outer RQ/MAC envelope, decide which exact bytes enter the previous-Check SHA-256 hash, implement a concrete QES signer, select detached/attached signature packaging, call sendChkV2, or expose a cashier-facing open-shift endpoint.

The existing investigation documents record unresolved questions about the complete signed Check.check_sign byte contract and signer identity representation. Those remain release blockers for end-to-end shift opening. This isolated builder is a tested protocol component, not an enabled fiscalization workflow.
