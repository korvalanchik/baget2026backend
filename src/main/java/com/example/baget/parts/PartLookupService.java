package com.example.baget.parts;

import com.example.baget.common.cache.AbstractLookupCacheService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PartLookupService extends AbstractLookupCacheService<Long, PartsDTO> {
    private final PartsRepository partsRepository;

    public PartLookupService(PartsRepository partsRepository) {
        this.partsRepository = partsRepository;
    }

    @Override
    protected String cacheName() {
        return "Parts";
    }

    @Override
    protected PartsDTO loadById(Long id) {
        return partsRepository.findByPartNo(id)
                .map(part -> new PartsDTO(part.getPartNo(), part.getDescription()))
                .orElse(null);
    }

    @Override
    protected List<PartsDTO> loadAll() {
        return partsRepository.findAll().stream()
                .map(part -> new PartsDTO(part.getPartNo(), part.getDescription()))
                .toList();
    }

    public Map<Long, String> loadMap() {
        return findAll().stream()
                .collect(Collectors.toMap(PartsDTO::getPartNo, PartsDTO::getDescription));
    }

}
