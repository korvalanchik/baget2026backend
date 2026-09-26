package com.example.baget.orders;

import com.example.baget.branch.Branch;
import com.example.baget.branch.BranchRepository;
import com.example.baget.customer.Customer;
import com.example.baget.customer.CustomerDTO;
import com.example.baget.customer.CustomerRepository;
import com.example.baget.customer.CustomerService;
import com.example.baget.items.*;
import com.example.baget.status.Status;
import com.example.baget.status.StatusRepository;
import com.example.baget.users.Role;
import com.example.baget.users.User;
import com.example.baget.users.UsersRepository;
import com.example.baget.util.NotFoundException;
import com.example.baget.util.TransactionException;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class OrdersService {

    private final com.example.baget.orderproduct.LegacyOrderGuard legacyGuard;
    private final OrdersRepository ordersRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final UsersRepository userRepository;
    private final BranchRepository branchRepository;
    private final ItemsRepository itemsRepository;
    private final ItemsService itemsService;
    private final StatusRepository statusRepository;

    private static final Set<String> EDITABLE_STATUSES = Set.of("Не вказано", "Прийнято");
    public OrdersService(final OrdersRepository ordersRepository, CustomerRepository customerRepository,
                         CustomerService customerService, UsersRepository userRepository,
                         BranchRepository branchRepository,
                         ItemsRepository itemsRepository, ItemsService itemsService, StatusRepository statusRepository, com.example.baget.orderproduct.LegacyOrderGuard legacyGuard) {
        this.legacyGuard = legacyGuard;
        this.ordersRepository = ordersRepository;
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.itemsRepository = itemsRepository;
        this.itemsService = itemsService;
        this.statusRepository = statusRepository;
    }

    public List<OrdersDTO> findAll() {
        final List<Orders> orders = ordersRepository.findAll(Sort.by("orderNo"));
        return orders.stream()
                .map(order -> mapToDTO(order, new OrdersDTO()))
                .toList();
    }

    public Page<OrdersDTO> findAll(Pageable pageable) {
        Page<Orders> ordersPage = ordersRepository.findAll(pageable);
        return ordersPage.map(orders -> mapToDTO(orders, new OrdersDTO()));
    }

    public OrdersDTO get(final Long orderNo) {
        return ordersRepository.findById(orderNo)
                .map(orders -> mapToDTO(orders, new OrdersDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public Page<? extends OrderProjections.BaseOrdersView> getOrders(Pageable pageable, String requestedBranchName) {
        UserAndRoles userAndRoles = getUserAndRoles();

        Set<String> allowedBranches = userAndRoles.user().getAllowedBranches()
                .stream()
                .map(Branch::getName)
                .collect(Collectors.toSet());

        if (userAndRoles.userRoles().contains("ROLE_ADMIN") || userAndRoles.userRoles().contains("ROLE_FOREMAN")) {
            return ordersRepository.findAllAdminBy(pageable);
        }

        if (userAndRoles.userRoles().contains("ROLE_COUNTER")) {
            return ordersRepository.findByBranch_NameIn(allowedBranches, pageable);
        }

        // USER — лише для конкретної філії
        if (!allowedBranches.contains(requestedBranchName)) {
            throw new AccessDeniedException("Немає доступу до філіалу: " + requestedBranchName);
        }

        return ordersRepository.findByBranch_Name(requestedBranchName, pageable);
    }

    private UserAndRoles getUserAndRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Set<String> userRoles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        return new UserAndRoles(user, userRoles);
    }

    private record UserAndRoles(User user, Set<String> userRoles) {
    }


    public Page<OrderSummaryView> getOrderSummaries(Pageable pageable) {
        UserAndRoles result = getUserAndRoles();

        if (result.userRoles().contains("ROLE_ADMIN")) return ordersRepository.findAllSummaryBy(pageable);
        if (result.userRoles().contains("ROLE_FOREMAN")) {
            var branches = result.user().getAllowedBranches().stream().map(Branch::getName).toList();
            return ordersRepository.findSummaryByBranch_NameIn(branches, pageable);
        }
        throw new TransactionException("Обмежено права доступу");
    }


    @Transactional
    public Long create(final OrdersDTO ordersDTO) {
        legacyGuard.check(ordersDTO.getOrderNo());
        if (ordersDTO.getOrderNo()!=null && ordersRepository.existsById(ordersDTO.getOrderNo())) throw new TransactionException("Номер замовлення вже використано");
        String username = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            username =  authentication.getName(); // Це і є username
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Orders order = new Orders();
        order.setOrderNo(ordersDTO.getOrderNo());
        mapToEntity(ordersDTO, order);
        order.setEmployee(user);
//        order.setEmpNo(user.getId()); // Присвоюємо userId як empNo
        ordersRepository.save(order);
        saveItems(1L, order, ordersDTO);
        return order.getOrderNo();
    }

    @Transactional
    public void update(final Long orderNo, final OrdersDTO ordersDTO) {
        legacyGuard.check(orderNo);

        final Orders existingOrder = ordersRepository.findById(orderNo)
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (isNotAdmin()) {
            checkOrderEditable(existingOrder);
        }

        mapToEntity(ordersDTO, existingOrder);
        updateItems(existingOrder, ordersDTO);

        ordersRepository.save(existingOrder);
    }

    @Transactional
    public void delete(final Long orderNo) {
        legacyGuard.check(orderNo);

        final Orders existingOrder = ordersRepository.findById(orderNo)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (isNotAdmin()) {
            checkOrderEditable(existingOrder);
        }
        ordersRepository.delete(existingOrder);
    }

    private OrdersDTO mapToDTO(final Orders entity, final OrdersDTO dto) {
        dto.setOrderNo(entity.getOrderNo());
        dto.setCustNo(entity.getCustomer().getCustNo());
        dto.setCompany(entity.getCustomer().getCompany());
        dto.setPhone(entity.getCustomer().getMobile());
        dto.setItems(itemsService.findByOrderNo(entity.getOrderNo()));
        dto.setBranchName(entity.getBranch().getName());
        dto.setSaleDate(entity.getSaleDate());
        dto.setShipDate(entity.getShipDate());

        User user = entity.getEmployee();
        if (user != null) {
            dto.setEmpNo(user.getUsername()); // записуємо username в DTO
        } else {
            dto.setEmpNo(null);
        }
        dto.setReceivedDate(entity.getReceivedDate());
        dto.setClientReceivedDate(entity.getClientReceivedDate());
        dto.setShipToAddr2(entity.getShipToAddr2());
        dto.setShipToCity(entity.getShipToCity());
        dto.setShipToState(entity.getShipToState());
        dto.setShipToZip(entity.getShipToZip());
        dto.setShipToCountry(entity.getShipToCountry());
        dto.setShipToPhone(entity.getShipToPhone());
        dto.setShipVia(entity.getShipVia());
        dto.setPo(entity.getPo());
        dto.setTerms(entity.getTerms());
        dto.setPaymentMethod(entity.getPaymentMethod());
        dto.setItemsTotal(entity.getItemsTotal());
        dto.setTaxRate(entity.getTaxRate());
        dto.setFreight(entity.getFreight());
        dto.setAmountPaid(entity.getAmountPaid());
        dto.setAmountDueN(entity.getAmountDueN());
        dto.setIncome(entity.getIncome());
        dto.setTotalCost(entity.getTotalCost());
        dto.setPriceLevel(entity.getPriceLevel());
        dto.setStatusOrder(entity.getStatusOrder());
        dto.setCalculationRevision(entity.getCalculationRevision());
        dto.setRahFacNo(entity.getRahFacNo());
        dto.setNotice(entity.getNotice());
        return dto;
    }

    private void mapToEntity(final OrdersDTO dto, final Orders entity) {
        if (dto.getCustNo() != null) {
            // Якщо вказано ID клієнта — шукаємо в базі
            Customer customer = customerRepository.findById(dto.getCustNo())
                    .orElseThrow(() -> new NotFoundException("Customer not found"));
            entity.setCustomer(customer);

        } else if (dto.getPhone() != null) {
            String phone = dto.getPhone().trim();

            Customer customer = !phone.isEmpty()
                    ? customerRepository.findFirstByMobileContainingOrderByCustNoAsc(phone)
                    .orElseGet(() -> createWithAnonymousCustomer(dto))
                    : createWithAnonymousCustomer(dto);

            entity.setCustomer(customer);
        }

        if (dto.getBranchName() != null) {
            Branch branch = branchRepository.findByName(dto.getBranchName())
                    .orElseThrow(() -> new RuntimeException("Branch not found: " + dto.getBranchName()));
            entity.setBranch(branch);
        }

        if (dto.getSaleDate() != null) entity.setSaleDate(dto.getSaleDate());
        if (dto.getShipDate() != null) entity.setShipDate(dto.getShipDate());
        if (dto.getReceivedDate() != null) entity.setReceivedDate(dto.getReceivedDate());
        if (dto.getClientReceivedDate() != null) entity.setClientReceivedDate(dto.getClientReceivedDate());

        if (dto.getShipToAddr2() != null) entity.setShipToAddr2(dto.getShipToAddr2());
        if (dto.getShipToCity() != null) entity.setShipToCity(dto.getShipToCity());
        if (dto.getShipToState() != null) entity.setShipToState(dto.getShipToState());
        if (dto.getShipToZip() != null) entity.setShipToZip(dto.getShipToZip());
        if (dto.getShipToCountry() != null) entity.setShipToCountry(dto.getShipToCountry());
        if (dto.getShipToPhone() != null) entity.setShipToPhone(dto.getShipToPhone());
        if (dto.getShipVia() != null) entity.setShipVia(dto.getShipVia());
        if (dto.getPo() != null) entity.setPo(dto.getPo());
        if (dto.getTerms() != null) entity.setTerms(dto.getTerms());
        if (dto.getPaymentMethod() != null) entity.setPaymentMethod(dto.getPaymentMethod());
        if (dto.getItemsTotal() != null) entity.setItemsTotal(dto.getItemsTotal());
        if (dto.getTaxRate() != null) entity.setTaxRate(dto.getTaxRate());
        if (dto.getFreight() != null) entity.setFreight(dto.getFreight());
        if (dto.getAmountPaid() != null) entity.setAmountPaid(dto.getAmountPaid());
        if (dto.getAmountDueN() != null) entity.setAmountDueN(dto.getAmountDueN());
        if (dto.getIncome() != null) entity.setIncome(dto.getIncome());
        if (dto.getTotalCost() != null) entity.setTotalCost(dto.getTotalCost());
        if (dto.getPriceLevel() != null) entity.setPriceLevel(dto.getPriceLevel());
        if (dto.getStatusOrder() != null) entity.setStatusOrder(dto.getStatusOrder());
        if (dto.getRahFacNo() != null) entity.setRahFacNo(dto.getRahFacNo());
        if (dto.getNotice() != null) entity.setNotice(dto.getNotice());
    }

    private Customer createWithAnonymousCustomer(OrdersDTO ordersDTO) {
        CustomerDTO customerDTO = new CustomerDTO();

        // Якщо клієнт не дав назву компанії і телефону
        if ((ordersDTO.getPhone() == null || ordersDTO.getPhone().trim().isEmpty())
                && (ordersDTO.getCompany() == null || ordersDTO.getCompany().trim().isEmpty())) {

            // шукаємо останнього "інкогніто" клієнта
            String prefix = "Інкогніто-";
            Optional<Customer> lastIncognito = customerRepository
                    .findTopByCompanyStartingWithOrderByCustNoDesc(prefix);

            int nextNumber = 1;
            if (lastIncognito.isPresent()) {
                String lastCompany = lastIncognito.get().getCompany();
                try {
                    nextNumber = Integer.parseInt(lastCompany.replace(prefix, "")) + 1;
                } catch (NumberFormatException ignored) {
                }
            }

            customerDTO.setCompany(prefix + nextNumber);
        } else {
            // Якщо назва компанії вказана — зберігаємо її
            customerDTO.setCompany(ordersDTO.getCompany());
        }

        customerDTO.setMobile(ordersDTO.getPhone());
        customerDTO.setAddr1(ordersDTO.getAddr1());

        Long newCustNo = customerService.create(customerDTO);
        return customerRepository.findById(newCustNo)
                .orElseThrow(() -> new RuntimeException("Newly created customer not found"));
    }



    private void saveItems(Long itemNo, final Orders orders, final OrdersDTO ordersDTO) {
        if(ordersDTO.getItems() != null) {
            for (ItemsDTO itemDTO : ordersDTO.getItems()) {
                Items item = new Items();
                ItemId itemId = new ItemId(orders.getOrderNo(), itemNo);
                item.setId(itemId);
                item.setOrder(orders);
                item.setPartNo(itemDTO.getPartNo());
                item.setProfilWidth(itemDTO.getProfilWidth());
                item.setEstimationWidth(itemDTO.getEstimationWidth());
                item.setEstimationHeight(itemDTO.getEstimationHeight());
                item.setWidth(itemDTO.getWidth());
                item.setHeight(itemDTO.getHeight());
                item.setQty(itemDTO.getQty());
                item.setQuantity(itemDTO.getQuantity());
                item.setSellPrice(itemDTO.getSellPrice());
                item.setResellerPrice(itemDTO.getResellerPrice());
                item.setDiscount(itemDTO.getDiscount());
                item.setOnHand(itemDTO.getOnHand());
                item.setCost(itemDTO.getCost());
                item.setSum(itemDTO.getSum());

                itemsRepository.save(item);
                itemNo++;
            }
        }
    }

    private void updateItems(final Orders existingOrder, final OrdersDTO dto) {
        if (dto.getItems() != null) {
            Long maxItemNo = itemsRepository.findMaxItemNoByOrderNo(existingOrder.getOrderNo());
            Long nextItemNo = (maxItemNo != null ? maxItemNo : 0L) + 1;
            deleteAllItemsByOrderNo(existingOrder.getOrderNo());
            if (!dto.getItems().isEmpty()) {
                saveItems(nextItemNo, existingOrder, dto);
            }
        }
    }

    @Transactional
    public void deleteAllItemsByOrderNo(Long orderNo) {
        legacyGuard.check(orderNo);
        itemsRepository.deleteByOrderOrderNo(orderNo);
    }

    private void checkOrderEditable(Orders order) {
        Status status = statusRepository.findByStatusNo(order.getStatusOrder())
                .orElseThrow(() -> new IllegalStateException("Status not found"));
        if (!EDITABLE_STATUSES.contains(status.getStatusName())) {
            throw new TransactionException(
                    "Замовлення зі статусом " + status.getStatusName() + " не можна редагувати чи видалити"
            );
        }
    }

    private boolean isNotAdmin() {
        Authentication auth = SecurityContextHolder
                .getContext()
                .getAuthentication();

        return auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

}
