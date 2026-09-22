package com.example.baget.branch;

import com.example.baget.common.cache.AbstractLookupCacheService;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@CacheConfig(cacheNames = "branches")
public class BranchCacheService extends AbstractLookupCacheService<Long, BranchDTO> {

    private final BranchRepository branchRepository;

    public BranchCacheService(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    @Override
    protected String cacheName() {
        return "branches";
    }

    @Override
    protected BranchDTO loadById(Long id) {
        return branchRepository.findById(id)
                .map(branch -> new BranchDTO(branch.getBranchNo(), branch.getName()))
                .orElse(null);
    }

    @Override
    protected List<BranchDTO> loadAll() {
        return branchRepository.findAll()
                .stream()
                .map(branch -> new BranchDTO(branch.getBranchNo(), branch.getName()))
                .collect(Collectors.toList());
    }

    public Map<Long, String> loadMap() {
        return findAll().stream()
                .collect(Collectors.toMap(BranchDTO::getBranchNo, BranchDTO::getName));
    }
}
