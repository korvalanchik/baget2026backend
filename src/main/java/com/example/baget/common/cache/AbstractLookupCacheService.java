package com.example.baget.common.cache;

import org.springframework.cache.annotation.Cacheable;

import java.util.List;

public abstract class AbstractLookupCacheService<ID, DTO> implements LookupCacheService<ID, DTO> {

    @Override
    @Cacheable(value = "#{cacheName()}", key = "#id")
    public DTO findById(ID id) {
        return loadById(id);
    }

    @Override
    @Cacheable(value = "#{cacheName()}")
    public List<DTO> findAll() {
        return loadAll();
    }

    protected abstract String cacheName();

    protected abstract DTO loadById(ID id);

    protected abstract List<DTO> loadAll();
}
