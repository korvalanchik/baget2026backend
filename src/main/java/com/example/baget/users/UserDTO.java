package com.example.baget.users;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private Long telegramId;
    private List<String> roles; // Список ідентифікаторів ролей

    public UserDTO(Long id, String username) {
        this.id = id;
        this.username = username;
    }
}
