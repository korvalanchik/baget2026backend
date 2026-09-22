package com.example.baget.customer;

import com.example.baget.invoices.Invoice;
import com.example.baget.invoices.InvoiceFinanceService;
import com.example.baget.invoices.InvoiceRepository;
import com.example.baget.ledger.LedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerPaymentService {

    private final CustomerTransactionRepository customerTxRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final LedgerRepository ledgerRepository;
    private final InvoiceFinanceService invoiceFinanceService;

    public List<CustomerPaymentDTO> getPaymentsByInvoice(Long invoiceId) {
        return customerTxRepository.findPaymentsByInvoiceId(invoiceId);
    }

    public CustomerFinanceDTO getCustomerFinance(Long customerId) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow();

        BigDecimal balance =
                ledgerRepository.getCustomerBalance(customerId);

        BigDecimal availableAdvance =
                customerTxRepository.calculateAvailableAdvance(customerId);

        List<Invoice> openInvoices =
                invoiceRepository.findActiveInvoices(customerId);

        List<CustomerInvoiceDTO> invoices = openInvoices.stream()
                .map(this::toInvoiceDto)
                .filter(dto -> dto.debt().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        BigDecimal totalDebt = invoices.stream()
                .map(CustomerInvoiceDTO::debt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CustomerLedgerDTO> ledger =
                ledgerRepository.findCustomerLedger(customerId);

        return new CustomerFinanceDTO(
                customer.getCompany(),
                customer.getMobile(),
                balance,
                availableAdvance,
                totalDebt,
                invoices,
                ledger
        );
    }

    private CustomerInvoiceDTO toInvoiceDto(Invoice invoice) {

        BigDecimal debt =
                invoiceFinanceService.calculateInvoiceDebt(invoice);

        BigDecimal paid =
                invoice.getTotalAmount().subtract(debt);

        return new CustomerInvoiceDTO(
                invoice.getId(),
                invoice.getInvoiceNo(),
                invoice.getTotalAmount(),
                paid,
                debt,
                invoice.getCreatedAt()
        );
    }

}

