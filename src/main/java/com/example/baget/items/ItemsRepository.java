package com.example.baget.items;

import com.example.baget.orders.Orders;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface ItemsRepository extends JpaRepository<Items, ItemId> {
    List<Items> findByOrder(Orders order);
    List<Items> findByOrderOrderNo(Long orderNo);
    @Modifying
    @Transactional
    @Query("DELETE FROM Items i WHERE i.id.orderNo = :orderNo AND i.id.itemNo = :itemNo")
    void deleteItemByOrderNoAndItemNo(@Param("orderNo") Long orderNo, @Param("itemNo") Long itemNo);

    @Modifying
    @Transactional
    @Query("DELETE FROM Items i WHERE i.order.orderNo = :orderNo")
    void deleteByOrderOrderNo(@Param("orderNo") Long orderNo);

    @Query("SELECT COALESCE(MAX(i.id.itemNo), 0) FROM Items i WHERE i.order.orderNo = :orderNo")
    Long findMaxItemNoByOrderNo(@Param("orderNo") Long orderNo);

}
