# PRRO payment-link API (first backend increment)

This increment binds a previously recorded incoming Baget payment to at most one local PRRO operation.

## Endpoints

- `GET /api/prro/payments/{transactionId}?branchNo={branchNo}` — returns the operation state or `NOT_PREPARED`.
- `POST /api/prro/payments/{transactionId}/prepare` with JSON `{"branchNo": 1}` — creates the local `PREPARED` operation or returns the already-linked operation.

Both endpoints require an authenticated user permitted to accept payments and access to the customer and branch.

## Eligibility rules

- The source must be an active `customer_transactions` row of type `PAYMENT` or `ADVANCE`, with a positive amount.
- The transaction must have an incoming ledger entry tied to a configured cash account.
- The request branch must match the transaction branch.
- Exactly one enabled PRRO device must be configured for that branch, and it must have an active shift with a recorded signer key identifier.
- `prro_payment_operation_links` uses the customer transaction ID as its primary key, so a payment cannot be linked to multiple PRRO operations. The transaction row is locked before the link is checked/created.

An `ADVANCE_ALLOCATION` transaction is deliberately excluded: applying previously received advance money is not a new incoming payment and must not create another receipt.

## Safety boundary

The endpoint only reserves a local operation and records the link. It does **not** build receipt items, sign the XML, or submit anything to DPS. A `PREPARED` response must not be shown as a fiscalized receipt. The UI must not offer a real “Фіскалізувати чек” action until the signed payload and the current DPS protocol have been verified against an official fixture and a concrete signer adapter exists.

This first increment is not sufficient to enable production fiscalization. In particular, receipt itemization/tax mapping, signer integration, exact signed payload/hash-chain rules, and reconciliation UX remain separate work.
