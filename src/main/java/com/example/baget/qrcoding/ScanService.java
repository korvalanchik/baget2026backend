package com.example.baget.qrcoding;

import com.example.baget.orders.OrderProjections;
import com.example.baget.orders.Orders;
import com.example.baget.orders.OrdersRepository;
import com.example.baget.users.User;
import com.example.baget.users.UsersRepository;
import com.example.baget.util.NotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service

public class ScanService {
    private final OrdersRepository ordersRepository;
    private final UsersRepository userRepository;
    private final com.example.baget.orderproduct.ProductionService production;
    public ScanService(OrdersRepository ordersRepository, UsersRepository userRepository, com.example.baget.orderproduct.ProductionService production) {
        this.production=production;
        this.ordersRepository = ordersRepository;
        this.userRepository = userRepository;
    }

    public OrderProjections.OrderView scanOrder(String publicId, Principal principal) {
        Orders order = ordersRepository.findByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Order not found for publicId: " + publicId));

        // Гість — бачить лише публічну інформацію
        if (principal == null) {
            return ordersRepository.findPublicByOrderNo(order.getOrderNo())
                    .orElseThrow(() -> new NotFoundException("Public summary not found"));
        }

        // Авторизований користувач
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!hasAccessToBranch(user, order.getBranch().getBranchNo())) {
            throw new AccessDeniedException("No rights for this branch");
        }

        return ordersRepository.findPrivateByOrderNo(order.getOrderNo())
                .orElseThrow(() -> new NotFoundException("Private summary not found"));
    }

    @Transactional
    public void updateOrderStatus(String publicId, Integer newStatus, Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Login required");
        }

        Orders order = ordersRepository.findByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Order not found for publicId: " + publicId));

        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!hasAccessToBranch(user, order.getBranch().getBranchNo())) {
            throw new AccessDeniedException("No rights for this branch");
        }

        if (order.getCalculationRevision()>0) {
            if(!(principal instanceof org.springframework.security.core.Authentication auth)) throw new AccessDeniedException("Login required");
            production.transition(order.getOrderNo(),new com.example.baget.orderproduct.ProductionService.Transition(order.getCalculationRevision(),newStatus,null),auth);
            return;
        }
        order.setStatusOrder(newStatus);
    }

    private boolean hasAccessToBranch(User user, Long branchId) {
        return user.getAllowedBranches().stream()
                .anyMatch(b -> b.getBranchNo().equals(branchId));
    }

}
