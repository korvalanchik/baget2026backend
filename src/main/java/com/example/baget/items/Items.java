package com.example.baget.items;

import com.example.baget.orders.Orders;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


@Table(name = "items")
@Entity
@Getter
@Setter
public class Items {

    @EmbeddedId
    @NotNull
    private ItemId id;

    @ManyToOne
    @MapsId("orderNo")
    @JoinColumn(name = "OrderNo", nullable = false)
    @ToString.Exclude
    private Orders order;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="order_product_id")
    private com.example.baget.orderproduct.OrderProduct orderProduct;

    @Column(name="actual_consumption", precision=24, scale=6)
    private java.math.BigDecimal actualConsumption;

    @Column(name="actual_amount", precision=24, scale=2)
    private java.math.BigDecimal actualAmount;

    @Column(name="planned_consumption", precision=24, scale=6)
    private java.math.BigDecimal plannedConsumption;
    @Column(name="calculated_amount", precision=24, scale=2)
    private java.math.BigDecimal calculatedAmount;
    @Column(name="calculation_role") private String calculationRole;
    @Column(name="calculation_unit") private String calculationUnit;

    @Column(name = "PartNo")
    private Long partNo;

    @Column(name = "ProfilWidth")
    private Double profilWidth;

    @Column(name = "EstimationWidth")
    private Double estimationWidth;

    @Column(name = "EstimationHeight")
    private Double estimationHeight;

    @Column(name = "Width")
    private Double width;

    @Column(name = "Height")
    private Double height;

    @Column(name = "Qty")
    private Double qty;

    @Column(name = "Quantity")
    private Double quantity;

    @Column(name = "SellPrice")
    private Double sellPrice;

    @Column(name = "ResellerPrice")
    private Double resellerPrice;

    @Column(name = "Discount")
    private Double discount;

    @Column(name = "OnHand")
    private Double onHand;

    @Column(name = "Cost")
    private Double cost;

    @Column(name = "Sum")
    private Double sum;

}
