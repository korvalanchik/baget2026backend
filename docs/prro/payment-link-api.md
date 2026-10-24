# PRRO payment-link API (first backend increment)

This increment binds one saved Baget payment request to at most one local PRRO operation. The key is the idempotent `customer_payment_requests.request_key`, not an individual `customer_transactions.id`: one real payment can be split into a `PAYMENT` row and an `ADVANCE` row when an invoice is overpaid.

## Endpoints

- `GET /api/prro/payments/{paymentRequestKey}?branchNo={branchNo}` — returns the operation state or `NOT_PREPARED`.
- `POST /api/prro/payments/{paymentRequestKey}/prepare` with JSON `{"branchNo": 1}` — creates the local `PREPARED` operation or returns the already-linked operation.

Both endpoints require an authenticated user permitted to accept payments and access to the customer and branch.

## Eligibility rules

- The key must refer to a saved payment request in the requested branch.
- The request's actual new-money `amount` must be positive. A request that only allocates previously received advance money is rejected.
- The saved result must contain one or more active `PAYMENT` / `ADVANCE` transactions. Their total and their incoming ledger-entry total must equal the actual new-money amount in the request.
- The incoming ledger entries must point to one cash account. The payment request is the fiscalization unit, so split ledger rows for a single real payment do not create separate receipts.
- Exactly one enabled PRRO device must be configured for that branch, and it must have an active shift with a recorded signer key identifier.
- `prro_payment_operation_links` uses the payment request key as its primary key, preventing the same payment request from being linked to multiple PRRO operations.

An `ADVANCE_ALLOCATION` transaction is deliberately excluded: applying previously received advance money is not a new incoming payment and must not create another receipt.

## Safety boundary

The endpoint only reserves a local operation and records the link. It does **not** build receipt items, sign the XML, or submit anything to DPS. A `PREPARED` response must not be shown as a fiscalized receipt. The UI must not offer a real “Фіскалізувати чек” action until the signed payload and the current DPS protocol have been verified against an official fixture and a concrete signer adapter exists.

This first increment is not sufficient to enable production fiscalization. In particular, receipt itemization/tax mapping, signer integration, exact signed payload/hash-chain rules, and reconciliation UX remain separate work.
