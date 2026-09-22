package com.example.baget.buh;

import com.example.baget.util.InvoiceServiceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionResource {

    private final TransactionService transactionService;
    private final InvoiceServiceUtil invoiceServiceUtil;

    @GetMapping("/{orderNo}")
    public List<TransactionDTO> getTransactions(@PathVariable Long orderNo) {
        return transactionService.getTransactionsByOrder(orderNo);
    }

    @GetMapping("/transaction-types")
    public List<TransactionTypeProjection> getTransactionTypes() {
        return transactionService.getAllTransactionTypes();
    }

    @GetMapping("/{orderNo}/transaction-info")
    public TransactionInfoDTO getTransactionInfo(@PathVariable Long orderNo) {
        return transactionService.getTransactionInfo(orderNo);
    }

    @PostMapping("/{orderNo}")
    public TransactionDTO createTransaction(@RequestBody TransactionDTO dto) {
        return transactionService.createTransaction(dto);
    }

    @PostMapping("/collective-invoice")
    public ResponseEntity<Long> createCollectiveInvoice(@RequestBody List<Long> orderNos) {
        return ResponseEntity.ok(transactionService.createCollectiveInvoice(orderNos));
    }

    @GetMapping("/collective-invoice/{invoiceNo}")
    public ResponseEntity<TransactionCollectiveInvoiceDTO> getCollectiveInvoice(@PathVariable Long invoiceNo) {
        return ResponseEntity.ok(transactionService.getCollectiveInvoice(invoiceNo));
    }

    @GetMapping("/history/invoice/{invoiceNo}")
    public ResponseEntity<List<TransactionHistoryView>> getTransactionsHistoryByInvoice(
            @PathVariable Long invoiceNo) {
        return ResponseEntity.ok(transactionService.getTransactionsHistoryByInvoice(invoiceNo));
    }

    @PostMapping("/invoice")
    public ResponseEntity<List<TransactionDTO>> createInvoiceTransaction(
            @RequestBody TransactionInvoiceRequest request) {

        List<TransactionDTO> txId = transactionService.createInvoiceTransactions(request.invoiceNo(), request.amount());
        return ResponseEntity.ok(txId);
    }

    @GetMapping("/{invoiceNo}/pdf")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable Long invoiceNo) throws IOException {
        byte[] contents = invoiceServiceUtil.generateInvoicePdf(invoiceNo);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=invoice_" + invoiceNo + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(contents);
    }

    @PostMapping("/payments")
    public List<TransactionDTO> createBatchPayments(@RequestBody List<Long> orderNos) {
        return transactionService.createBatchPayments(orderNos);
    }

    @PostMapping("/{transactionId}/complete")
    public void completeTransaction(@PathVariable Long transactionId) {
        transactionService.completeTransaction(transactionId);
    }

    @PostMapping("/{transactionId}/cancel")
    public void cancelTransaction(@PathVariable Long transactionId) {
        transactionService.cancelTransaction(transactionId);
    }

}
