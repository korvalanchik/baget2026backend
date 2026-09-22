package com.example.baget.customer;

import com.example.baget.branch.Branch;
import com.example.baget.invoices.InvoiceRepository;
import com.example.baget.users.User;
import com.example.baget.users.UsersRepository;
import com.example.baget.util.NotFoundException;
import com.example.baget.util.TransactionException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UsersRepository usersRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerMapper customerMapper;

    public List<CustomerDTO> findAll() {
        final List<Customer> customers = customerRepository.findAll(Sort.by("custNo"));
        return customers.stream()
                .map(customerMapper::mapToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CustomerDTO> getCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable)
                .map(customerMapper::mapToDTO); // Перетворення у DTO, якщо потрібно
    }

    public CustomerDTO get(final Long custNo) {
        return customerRepository.findById(custNo)
                .map(customerMapper::mapToDTO)
                .orElseThrow(NotFoundException::new);
    }

//    @CacheEvict(value = "CustomerPhonePrefix", allEntries = true)
    public Long create(final CustomerDTO customerDTO) {
        final Customer customer = new Customer();
        customerMapper.mapToEntity(customerDTO, customer);
        return customerRepository.save(customer).getCustNo();
    }

    @CacheEvict(value = "CustomerPhonePrefix", key = "#customerDTO.phone")
    public void update(final Long custNo, final CustomerDTO customerDTO) {
        final Customer customer = customerRepository.findById(custNo)
                .orElseThrow(NotFoundException::new);
        customerMapper.mapToEntity(customerDTO, customer);
        customerRepository.save(customer);
    }

    public void delete(final Long custNo) {
        customerRepository.deleteById(custNo);
    }


    @Cacheable(value = "CustomerPhonePrefix", key = "#prefix")
    public List<CustomerDTO> findByPhonePrefix(String prefix) {
        return customerRepository.findByMobileContaining(prefix)
                .stream()
                .map(customer -> new CustomerDTO(customer.getCustNo(), customer.getCompany(), customer.getAddr1(), customer.getMobile(), customer.getComment()))
                .collect(Collectors.toList());

    }

    public List<CustomerBalanceProjection> getClientsForManager(String username, Long branchNo, boolean debtOnly, LocalDate date) {
        User user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        Set<Long> allowedBranchNos = user.getAllowedBranches()
                .stream()
                .map(Branch::getBranchNo)
                .collect(Collectors.toSet());

        if (branchNo != null && !allowedBranchNos.contains(branchNo)) {
            throw new AccessDeniedException("Branch not allowed");
        }

        Collection<Long> branchesToUse =
                (branchNo != null) ? List.of(branchNo) : allowedBranchNos;

//        OffsetDateTime dateEnd = (date != null) ? date.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC) : null;

        return customerRepository.findClientBalances(branchesToUse);
    }


    public List<CustomerInvoiceDTO> getInvoicesByCustomer(Long custNo) {
        return invoiceRepository.findInvoicesByCustomer(custNo);
    }

    public List<CustomerSelectDTO> getCustomersForInvoice() {
        return customerRepository.findAllBy().stream()
                .map(c -> new CustomerSelectDTO(c.getCustNo(), c.getCompany()))
                .toList();
    }

    public CustomerDashboardDTO.Response getDashboard(Long branchNo, boolean debtOnly, LocalDate date, Authentication authentication) {
        String username = authentication.getName();

        User user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        Set<Long> allowedBranches = user.getAllowedBranches()
                .stream()
                .map(Branch::getBranchNo)
                .collect(Collectors.toSet());

        if (branchNo != null && !allowedBranches.contains(branchNo)) {
            throw new TransactionException("Цю філію не дозволено");
        }
        if (allowedBranches.isEmpty()) {
            throw new TransactionException("Немає дозволених філій");
        }

        Set<Long> effectiveBranches = (branchNo != null)
                ? Set.of(branchNo)
                : allowedBranches;

        List<CustomerDashboardRow> rows = customerRepository.getDashboard(effectiveBranches);

        List<CustomerDashboardDTO.WithoutInvoice> withoutInvoice = new ArrayList<>();
        List<CustomerDashboardDTO.Debtor> debtors = new ArrayList<>();
        List<CustomerDashboardDTO.Payer> payers = new ArrayList<>();

        for (CustomerDashboardRow r : rows) {

            // 🟢 1. без інвойсів
            if (r.getPendingOrders() != null && r.getPendingOrders() > 0) {
                withoutInvoice.add(new CustomerDashboardDTO.WithoutInvoice(
                        r.getCustomerId(),
                        r.getCompany(),
                        r.getMobile(),
                        r.getPendingOrders(),
                        r.getBalance()
                ));
            }

            // 🔴 2. боржники
            if (r.getBalance() != null && r.getBalance().compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(new CustomerDashboardDTO.Debtor(
                        r.getCustomerId(),
                        r.getCompany(),
                        r.getMobile(),
                        r.getBalance(),
                        r.getInvoiceCount(),
                        r.getLastPaymentDate()
                ));
            }

            // 🔵 3. платники
            if (r.getConsolidatedInvoices() != null && r.getConsolidatedInvoices() > 0) {
                payers.add(new CustomerDashboardDTO.Payer(
                        r.getCustomerId(),
                        r.getCompany(),
                        r.getMobile(),
                        r.getConsolidatedInvoices(),
                        r.getTotalTurnover()
                ));
            }
        }

        return new CustomerDashboardDTO.Response(
                withoutInvoice,
                debtors,
                payers
        );
    }}
