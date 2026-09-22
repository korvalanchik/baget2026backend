package com.example.baget.users;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/users")
public class UserResource {
    private final UsersService usersService;

    public UserResource(UsersService usersService) {
        this.usersService = usersService;
    }

    @GetMapping
    public Page<UserDTO> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return usersService.getUsers(pageable);
    }

    @DeleteMapping("/{userNo}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteUser(@PathVariable(name = "userNo") final Long userNo) {
        usersService.delete(userNo);
        return ResponseEntity.noContent().build();
    }
}
