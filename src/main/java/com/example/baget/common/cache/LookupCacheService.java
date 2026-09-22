package com.example.baget.common.cache;

import java.util.List;

public interface LookupCacheService<ID, DTO> {
    DTO findById(ID id);
    List<DTO> findAll();
}
