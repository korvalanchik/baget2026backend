package com.example.baget.orders;

import com.example.baget.branch.Branch;
import com.example.baget.customer.Customer;
import com.example.baget.items.Items;
import com.example.baget.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


@Entity
@Table(name = "orders")
@Getter
@Setter
public class Orders {

    @Id
    @Column(name = "OrderNo", nullable = false, updatable = false)
    private Long orderNo;

    @Column(name="calculation_revision", insertable=false, updatable=false)
    private long calculationRevision;

    @OneToMany(mappedBy="order", fetch=FetchType.LAZY)
    private List<com.example.baget.orderproduct.OrderProduct> products;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "CustNo", nullable = false)
    private Customer customer;

    @OneToMany(mappedBy = "order", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Items> items;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "BranchNo")
    private Branch branch;

    @Column(name = "SaleDate")
    private OffsetDateTime saleDate;

    @Column(name = "ShipDate")
    private OffsetDateTime shipDate;

    @ManyToOne(fetch = FetchType.EAGER) // відносини без FK
    @JoinColumn(name = "EmpNo", referencedColumnName = "id")
    private User employee;

    @Column(name = "ReceivedDate")
    private OffsetDateTime receivedDate;

    @Column(name = "ClientReceivedDate")
    private OffsetDateTime clientReceivedDate;

    @Column(name = "ShipToAddr2", length = 30)
    private String shipToAddr2;

    @Column(name = "ShipToCity", length = 15)
    private String shipToCity;

    @Column(name = "ShipToState", length = 20)
    private String shipToState;

    @Column(name = "ShipToZip", length = 10)
    private String shipToZip;

    @Column(name = "ShipToCountry", length = 20)
    private String shipToCountry;

    @Column(name = "ShipToPhone", length = 15)
    private String shipToPhone;

    @Column(name = "ShipVia", length = 7)
    private String shipVia;

    @Column(name = "Po", length = 15)
    private String po;

    @Column(name = "Terms", length = 6)
    private String terms;

    @Column(name = "PaymentMethod", length = 7)
    private String paymentMethod;

    @Column(name = "ItemsTotal")
    private BigDecimal itemsTotal;

    @Column(name = "TaxRate")
    private BigDecimal taxRate;

    @Column(name = "Freight")
    private BigDecimal freight;

    @Column(name = "AmountPaid")
    private BigDecimal amountPaid;

    @Column(name = "AmountDueN")
    private BigDecimal amountDueN;

    @Column(name="production_total", insertable=false, updatable=false)
    private BigDecimal productionTotal;

    @Column(name = "Income")
    private BigDecimal income;

    @Column(name = "TotalCost")
    private BigDecimal totalCost;

    @Column(name = "PriceLevel")
    private Integer priceLevel;

    @Column(name = "StatusOrder")
    private Integer statusOrder;

    @Column(name = "RahFacNo")
    private Long rahFacNo;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "InvoiceId")
//    private Invoice invoice;

    @Column(name = "Notice")
    private String notice;

    @Column(name = "PublicId", unique = true, nullable = false, length = 36)
    private String publicId;

    @PrePersist
    public void prePersist() {
        if (publicId == null || publicId.isEmpty()) {
            publicId = UUID.randomUUID().toString();
        }
   }

}
