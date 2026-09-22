package com.example.baget.parts;

import com.example.baget.vendors.Vendors;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;


@Entity
@Table(name = "parts")
@Getter
@Setter
public class Parts {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long partNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VendorNo", nullable = true)
    private Vendors vendor;

    @Column(name = "\"description\"", length = 30)
    private String description;

    @Column
    private Double profilWidth;

    @Column
    private Double inQuality;

    @Column
    private Double onHand;

    @Column
    private Double onOrder;

    @Column
    private Double cost;

    @Column
    private Double listPrice;

    @Column
    private Double listPrice_1;

    @Column
    private Double listPrice_2;

    @Column
    private Integer noPercent;

    @Column
    private Double listPrice_3;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", length = 20)
    private UnitType unitType;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_method", length = 40)
    private CalculationMethod calculationMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "part_kind", length = 16)
    private PartKind partKind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_params", columnDefinition = "json")
    private Map<String, BigDecimal> calculationParams;

    @Version
    private Long version;

}
