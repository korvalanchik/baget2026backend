package com.example.baget.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;


public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @NonNull
    Page<Customer> findAll(@NonNull Pageable pageable);
    List<CustomerSelectView> findAllBy();
    List<Customer> findByMobileContaining(String prefix);
    Optional<Customer> findFirstByMobileContainingOrderByCustNoAsc(String phone);
    Optional<Customer> findTopByCompanyStartingWithOrderByCustNoDesc(String prefix);

    @Query(value = """
        WITH base_orders AS (
            SELECT
                o.OrderNo,
                o.CustNo,
                o.BranchNo,
                o.StatusOrder
            FROM orders o
            WHERE o.BranchNo IN (:branches)
              AND (
                  o.StatusOrder < 3
                  AND o.OrderNo >= 41578
              )
        ),
        
        orders_without_invoice AS (
            SELECT
                bo.CustNo,
                COUNT(DISTINCT bo.OrderNo) AS pending_orders
            FROM base_orders bo
            LEFT JOIN invoice_orders io ON io.order_no = bo.OrderNo
            WHERE io.order_no IS NULL
            GROUP BY bo.CustNo
        ),
        
        invoice_stats AS (
            SELECT
                o
                .CustNo,
                COUNT(DISTINCT io.invoice_id) AS invoice_count
            FROM invoice_orders io
            JOIN orders o ON o.OrderNo = io.order_no
            WHERE o.BranchNo IN (:branches)
            GROUP BY o.CustNo
        ),
        
        ledger_balance AS (
            SELECT
                le.customer_id,
                SUM(
                    CASE
                        WHEN le.direction = 'IN' THEN le.amount
                        ELSE -le.amount
                    END
                ) AS balance,
                MAX(
                    CASE
                        WHEN le.direction = 'IN'
                        THEN le.created_at
                    END
                ) AS last_payment_date
            FROM ledger_entries le
            WHERE le.branch_id IN (:branches)
            GROUP BY le.customer_id
        )
  
        SELECT
            c.CustNo,
            c.Company,
            c.Mobile,
        
            COALESCE(lb.balance, 0) AS balance,
            lb.last_payment_date,
            COALESCE(owi.pending_orders, 0) AS pending_orders,
            COALESCE(inv.invoice_count, 0) AS invoice_count
        FROM customer c
        
        -- 🔹 тільки ті клієнти, що мають замовлення в цих філіях
        JOIN (
            SELECT DISTINCT CustNo FROM base_orders
        ) bo ON bo.CustNo = c.CustNo
        
        LEFT JOIN ledger_balance lb ON lb.customer_id = c.CustNo
        LEFT JOIN orders_without_invoice owi ON owi.CustNo = c.CustNo
        LEFT JOIN invoice_stats inv ON inv.CustNo = c.CustNo
        
        WHERE
            -- 🔥 головна умова
            COALESCE(owi.pending_orders, 0) > 0
            OR COALESCE(lb.balance, 0) <> 0
    """, nativeQuery = true)
    List<CustomerBalanceProjection> findClientBalances(
            @Param("branches") Collection<Long> branches
    );


    @Query(value = """
        WITH base_orders AS (
            SELECT
                o.OrderNo,
                o.CustNo,
                o.BranchNo,
                o.StatusOrder
            FROM orders o
            WHERE o.BranchNo IN (:allowedBranches)
        ),

        orders_without_invoice AS (
            SELECT
                bo.CustNo,
                COUNT(DISTINCT bo.OrderNo) AS pending_orders
            FROM base_orders bo
            LEFT JOIN invoice_orders io ON io.order_no = bo.OrderNo
            WHERE io.order_no IS NULL
              AND bo.StatusOrder < 4
              AND bo.OrderNo > 41665
            GROUP BY bo.CustNo
        ),
    
        invoice_stats AS (
            SELECT
                o.CustNo,
                COUNT(DISTINCT io.invoice_id) AS invoice_count
            FROM invoice_orders io
            JOIN orders o
                ON o.OrderNo = io.order_no
            JOIN invoices i
                ON i.id = io.invoice_id
            WHERE o.BranchNo IN (:allowedBranches)
              AND i.lifecycle = 'ACTIVE'
            GROUP BY o.CustNo
        ),
    
        ledger_balance AS (
            SELECT
                le.customer_id,
                SUM(
                    CASE
                        WHEN le.direction = 'IN' THEN le.amount
                        ELSE -le.amount
                    END
                ) AS balance,
    
                MAX(
                    CASE
                        WHEN le.direction = 'IN'
                        THEN le.created_at
                    END
                ) AS last_payment_date
    
            FROM ledger_entries le
            WHERE le.branch_id IN (:allowedBranches)
            GROUP BY le.customer_id
        ),
    
        payers AS (
            SELECT
                le.payer_id AS customer_id,
    
                COUNT(DISTINCT le.invoice_id) AS consolidated_invoices,
    
                SUM(le.amount) AS total_turnover
    
            FROM ledger_entries le
            WHERE le.category = 'INVOICE_MERGE_IN'
              AND le.payer_id IS NOT NULL
              AND le.payer_id <> le.customer_id
              AND le.branch_id IN (:allowedBranches)
    
            GROUP BY le.payer_id
        )
    
        SELECT
            c.CustNo AS customer_id,
            c.Company,
            c.Mobile,
    
            COALESCE(owi.pending_orders, 0) AS pending_orders,
    
            COALESCE(inv.invoice_count, 0) AS invoice_count,
    
            COALESCE(lb.balance, 0) AS balance,
    
            lb.last_payment_date,
    
            COALESCE(p.consolidated_invoices, 0) AS consolidated_invoices,
    
            COALESCE(p.total_turnover, 0) AS total_turnover
    
        FROM customer c
    
        LEFT JOIN orders_without_invoice owi
            ON owi.CustNo = c.CustNo
    
        LEFT JOIN invoice_stats inv
            ON inv.CustNo = c.CustNo
    
        LEFT JOIN ledger_balance lb
            ON lb.customer_id = c.CustNo
    
        LEFT JOIN payers p
            ON p.customer_id = c.CustNo

        WHERE EXISTS (
            SELECT 1
            FROM base_orders bo
            WHERE bo.CustNo = c.CustNo
        )
    
        ORDER BY c.Company
    """, nativeQuery = true)
    List<CustomerDashboardRow> getDashboard(@Param("allowedBranches") Set<Long> allowedBranches);
}
