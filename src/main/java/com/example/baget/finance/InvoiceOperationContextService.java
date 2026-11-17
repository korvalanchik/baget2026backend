package com.example.baget.finance;

import com.example.baget.branch.Branch;
import com.example.baget.branch.BranchRepository;
import com.example.baget.customer.Customer;
import com.example.baget.customer.CustomerRepository;
import com.example.baget.invoices.Invoice;
import com.example.baget.invoices.InvoicePaymentRequest;
import com.example.baget.invoices.InvoiceRepository;
import com.example.baget.ledger.LedgerCategory;
import com.example.baget.ledger.LedgerDirection;
import com.example.baget.ledger.LedgerEntry;
import com.example.baget.ledger.LedgerRepository;
import com.example.baget.users.User;
import com.example.baget.util.TransactionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceOperationContextService {
    private final BranchRepository branchRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final LedgerRepository ledgerRepository;

    private static final List<LedgerCategory> INVOICE_OWNERSHIP_CATEGORIES = List.of(
            LedgerCategory.INVOICE_ISSUED,
            LedgerCategory.INVOICE_MERGE_OUT
    );

    public InvoiceOperationContext resolveContext(InvoicePaymentRequest request, User user) {
        return resolveContext(request,user,false);
    }

    public InvoiceOperationContext resolveContext(InvoicePaymentRequest request, User user, boolean bankPayment) {

        if (request.invoiceId() != null) {

            Invoice invoice = invoiceRepository.findById(request.invoiceId())
                    .orElseThrow(() -> new TransactionException("Інвойс не знайдено"));

            Branch branch = resolveInvoiceBranch(invoice.getId());
            if(request.branchNo()!=null && !request.branchNo().equals(branch.getBranchNo()))
                throw new TransactionException("Філія платежу не відповідає філії-власнику рахунку");
            validateBranchAccess(user, branch.getBranchNo(), bankPayment && isAccountant(user));

            return new InvoiceOperationContext(
                    user,
                    branch,
                    invoice.getCustomer(),
                    invoice.getEffectivePayer(),
                    invoice
            );
        }

        if (request.branchNo() == null || request.customerId() == null) {
            throw new TransactionException("Для авансу потрібні branch + customer");
        }

        validateBranchAccess(user, request.branchNo());

        Branch branch = branchRepository.findById(request.branchNo())
                .orElseThrow(() -> new TransactionException("Філія не знайдена"));

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new TransactionException("Клієнт не знайдений"));

        return new InvoiceOperationContext(user, branch, customer, customer, null);
    }

    private boolean isAccountant(User user) {
        return user.getRoles().stream().anyMatch(r->"ROLE_ACCOUNTANT".equals(r.getName()));
    }

    private void validateBranchAccess(User user, Long branchNo) {
        validateBranchAccess(user,branchNo,false);
    }

    private void validateBranchAccess(User user, Long branchNo, boolean allowAccountantCrossBranch) {

        boolean allowed = user.getRoles().stream().anyMatch(r->r.getName().equals("ROLE_ADMIN"))
                || (allowAccountantCrossBranch && isAccountant(user))
                || user.getAllowedBranches().stream().anyMatch(b -> b.getBranchNo().equals(branchNo));

        if (!allowed) {
            throw new TransactionException("Немає доступу до філії: " + branchNo);
        }
    }

    private Branch resolveInvoiceBranch(Long invoiceId) {
        return ledgerRepository
                .findTopByInvoiceIdAndDirectionAndCategoryInOrderByCreatedAtDescIdDesc(
                        invoiceId,
                        LedgerDirection.OUT,
                        INVOICE_OWNERSHIP_CATEGORIES
                )
                .map(LedgerEntry::getBranch)
                .orElseThrow(() -> new TransactionException(
                        "Не знайдено ownership OUT для інвойсу " + invoiceId
                ));
    }
}
