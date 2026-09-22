package com.example.baget.passwordrecoverytoken;

import com.example.baget.users.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "password_recovery_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordRecoveryToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @Column(name = "expiry_date", nullable = false)
    private OffsetDateTime expiryDate;

    public boolean isExpired(OffsetDateTime currentDateTime) {
        return currentDateTime.isAfter(expiryDate);
    }
}