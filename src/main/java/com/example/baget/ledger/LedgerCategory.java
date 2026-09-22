package com.example.baget.ledger;

public enum LedgerCategory {

    // 💰 CASH INFLOW
    PAYMENT_RECEIVED,          // гроші від клієнта
    CUSTOMER_ADVANCE,          // аванс від клієнта

    // 💸 CASH OUTFLOW
    CUSTOMER_REFUND,           // повернення клієнту
    MATERIAL_PURCHASE,         // закупка матеріалів
    OVERHEAD_EXPENSE,          // витрати

    // 📄 NON-CASH / ACCOUNTING
    INVOICE_ISSUED,            // створення боргу
    INVOICE_REVENUE,           // дохід
    PAYMENT_ALLOCATION,        // алокація платежу
    APPLY_ADVANCE_TO_INVOICE,  // списання авансу

    // 🔁 TRANSFERS
    INTERNAL_TRANSFER_IN,
    INTERNAL_TRANSFER_OUT,

    // 🔄 COMPLEX OPS
    INVOICE_MERGE_IN,
    INVOICE_MERGE_OUT,

    // ⚖️ MANUAL
    ADJUSTMENT
}
//public enum LedgerCategory {
//    CUSTOMER_PAYMENT,
//    CUSTOMER_ADVANCE,
//    CUSTOMER_REFUND,
//    INVOICE_ISSUED,
//    INVOICE_REVENUE,
//    PAYMENT_RECEIVED,
//    PAYMENT_ALLOCATION,
//    APPLY_ADVANCE_TO_INVOICE,
//    INVOICE_MERGE_IN,
//    INVOICE_MERGE_OUT,
//    MATERIAL_PURCHASE,
//    OVERHEAD_EXPENSE,
//    INTERNAL_TRANSFER_IN,
//    INTERNAL_TRANSFER_OUT,
//    ADJUSTMENT
//}

