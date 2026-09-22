package com.example.baget.customer;

import com.example.baget.ledger.LedgerCategory;
import com.example.baget.ledger.LedgerDirection;

public enum CustomerTransactionType {

    PAYMENT(LedgerCategory.PAYMENT_RECEIVED, LedgerDirection.IN),
    ADVANCE(LedgerCategory.CUSTOMER_ADVANCE, LedgerDirection.IN),
    REFUND(LedgerCategory.CUSTOMER_REFUND, LedgerDirection.OUT),
    INVOICE(LedgerCategory.INVOICE_ISSUED, LedgerDirection.OUT),
    INVOICE_MERGE_IN(LedgerCategory.INVOICE_MERGE_IN, LedgerDirection.IN),
    INVOICE_MERGE_OUT(LedgerCategory.INVOICE_MERGE_OUT, LedgerDirection.OUT),
    ADVANCE_ALLOCATION(LedgerCategory.CUSTOMER_ADVANCE,LedgerDirection.OUT);
    private final LedgerCategory ledgerCategory;
    private final LedgerDirection direction;

    CustomerTransactionType(LedgerCategory ledgerCategory,
                            LedgerDirection direction) {
        this.ledgerCategory = ledgerCategory;
        this.direction = direction;
    }

    public LedgerCategory getLedgerCategory() {
        return ledgerCategory;
    }

    public LedgerDirection getDirection() {
        return direction;
    }

}

//public enum CustomerTransactionType {
//    INVOICE,         // нарахування боргу
//    PAYMENT,         // оплата
//    ALLOCATION,      // рознесення оплати по замовленнях
//    ADVANCE,         // аванс (передплата)
//    ADVANCE_APPLIED, // застосування авансу
//    REFUND,          // повернення клієнту
//    ADJUSTMENT       // ручна корекція
//}
