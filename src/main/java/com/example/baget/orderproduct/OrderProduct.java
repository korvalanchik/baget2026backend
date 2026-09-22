package com.example.baget.orderproduct;

import com.example.baget.orders.Orders;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;

@Entity @Table(name="order_products") @Getter @Setter
public class OrderProduct {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="order_no") private Orders order;
    @Column(name="client_product_id", nullable=false) private String clientProductId;
    private long revision;
    private boolean active;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="json", nullable=false) private JsonNode configuration;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="json", nullable=false) private JsonNode calculation;
    @Column(precision=24,scale=2) private BigDecimal total;
}
