package com.example.baget.invoices;

import java.util.List;

public record MergeInvoicesRequest (

    Long branchNo,           // 🔥 номер філії, в якій створюється консолідований рахунок

    List<Long> invoiceIds,   // 🔥 що об'єднуємо

    Long payerId,            // 🔥 хто платить (optional)

    String note,             // 🔥 коментар (optional)

    boolean applyAdvance    // 🔥 чи списувати аванс

){}