package com.example.baget.order_counter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "temp_order_counter")
public class TempOrderCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "last_temp_order_no")
    private Long lastTempOrderNo;

}
