package com.example.baget.branch;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/branches")
@RequiredArgsConstructor
public class BranchAdminController {

    private final BranchAdminService branchAdminService;

    @GetMapping
    public List<BranchAdminDTO> list() {
        return branchAdminService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BranchAdminDTO create(@Valid @RequestBody BranchAdminRequest request) {
        return branchAdminService.create(request);
    }

    @PutMapping("/{branchNo}")
    public BranchAdminDTO update(
            @PathVariable Long branchNo,
            @Valid @RequestBody BranchAdminRequest request
    ) {
        return branchAdminService.update(branchNo, request);
    }
}
