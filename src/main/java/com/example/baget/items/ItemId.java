package com.example.baget.items;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Objects;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ItemId implements Serializable {

    @Column(name = "OrderNo")
    @NotNull
    private Long orderNo;

    @Column(name = "ItemNo")
    @NotNull
    private Long itemNo;

}
