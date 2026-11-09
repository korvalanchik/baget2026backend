package com.example.baget.branch;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchAdminService {

    private final BranchRepository branchRepository;

    @Transactional(readOnly = true)
    public List<BranchAdminDTO> list() {
        return branchRepository.findAllByOrderByBranchNoAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public BranchAdminDTO create(BranchAdminRequest request) {
        String name = normalizeName(request.name());
        if (branchRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Філія з такою назвою вже існує.");
        }

        Branch branch = new Branch();
        branch.setName(name);
        branch.setAddress(normalizeAddress(request.address()));
        branch.setActive(request.active() == null || request.active());
        return toDto(branchRepository.save(branch));
    }

    @Transactional
    public BranchAdminDTO update(Long branchNo, BranchAdminRequest request) {
        Branch branch = branchRepository.findById(branchNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Філію не знайдено."));

        String name = normalizeName(request.name());
        if (branchRepository.existsByNameIgnoreCaseAndBranchNoNot(name, branchNo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Філія з такою назвою вже існує.");
        }

        branch.setName(name);
        branch.setAddress(normalizeAddress(request.address()));
        if (request.active() != null) {
            branch.setActive(request.active());
        }

        try {
            return toDto(branchRepository.save(branch));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Не вдалося зберегти філію: назва вже використовується.");
        }
    }

    private BranchAdminDTO toDto(Branch branch) {
        return new BranchAdminDTO(branch.getBranchNo(), branch.getName(), branch.getAddress(), branch.isActive());
    }

    private String normalizeName(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeAddress(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
