package com.example.baget;

import com.example.baget.passwordrecoverytoken.PasswordRecoveryToken;
import com.example.baget.passwordrecoverytoken.PasswordRecoveryTokenRepository;
import com.example.baget.users.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsersServiceTest {

    @Mock
    private UsersRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordRecoveryTokenRepository passwordRecoveryTokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsersService usersService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetUsers() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(pageable)).thenReturn(Page.empty());

        // Act
        Page<UserDTO> usersPage = usersService.getUsers(pageable);

        // Assert
        assertNotNull(usersPage);
        verify(userRepository, times(1)).findAll(pageable);
    }

    @Test
    void testRegisterNewUser_UsernameAlreadyTaken() {
        // Arrange
        UserPassRecoveryDTO userDTO = new UserPassRecoveryDTO();
        userDTO.setUsername("testUser");
        userDTO.setEmail("test@example.com");

        when(userRepository.existsByUsername(userDTO.getUsername())).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                usersService.registerNewUser(userDTO));

        assertEquals("Username is already taken", exception.getMessage());
    }

    @Test
    void testSendPasswordRecoveryEmail() {
        // Arrange
        String email = "test@example.com";
        OffsetDateTime clientDateTime = OffsetDateTime.now();

        User user = new User();
        user.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(java.util.Optional.of(user));

        // Act
        usersService.sendPasswordRecoveryEmail(email, clientDateTime);

        // Assert
        verify(passwordRecoveryTokenRepository, times(1)).save(any());
        verify(emailService, times(1)).sendEmail(eq(email), anyString(), anyString());
    }
    @Test
    public void testResetPassword_Success() {
        // Arrange
        String token = "test-token";
        String newPassword = "new-password";
        OffsetDateTime clientDateTime = OffsetDateTime.now();

        User user = new User();
        user.setPassword("old-password");

        PasswordRecoveryToken recoveryToken = new PasswordRecoveryToken();
        recoveryToken.setToken(token);
        recoveryToken.setUser(user);
        recoveryToken.setExpiryDate(clientDateTime.plusMinutes(10));

        when(passwordRecoveryTokenRepository.findByToken(token)).thenReturn(Optional.of(recoveryToken));

        // Act
        usersService.resetPassword(token, newPassword, clientDateTime);

        // Assert
        verify(passwordEncoder).encode(newPassword);
        verify(userRepository).save(user);
        verify(passwordRecoveryTokenRepository).delete(recoveryToken);
        verify(passwordRecoveryTokenRepository).deleteAllExpiredTokens(clientDateTime);
    }

    @Test
    public void testResetPassword_TokenExpired() {
        // Arrange
        String token = "expired-token";
        String newPassword = "new-password";
        OffsetDateTime clientDateTime = OffsetDateTime.now();

        PasswordRecoveryToken recoveryToken = new PasswordRecoveryToken();
        recoveryToken.setToken(token);
        recoveryToken.setExpiryDate(clientDateTime.minusMinutes(10)); // Expired

        when(passwordRecoveryTokenRepository.findByToken(token)).thenReturn(Optional.of(recoveryToken));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                usersService.resetPassword(token, newPassword, clientDateTime));

        verify(passwordRecoveryTokenRepository, never()).delete(recoveryToken);
        verify(userRepository, never()).save(any(User.class));
    }
    @Test
    public void testRegisterNewUser_Success() {
        // Arrange
        UserPassRecoveryDTO userPassRecoveryDto = new UserPassRecoveryDTO();
        userPassRecoveryDto.setUsername("testUser");
        userPassRecoveryDto.setEmail("test@example.com");
        userPassRecoveryDto.setPassword("password123");

        when(userRepository.existsByUsername("testUser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
//        when(roleRepository.findAllById(userPassRecoveryDto.getRoles())).thenReturn(new ArrayList<>());

        // Act
        usersService.registerNewUser(userPassRecoveryDto);

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("testUser", savedUser.getUsername());
        assertEquals("test@example.com", savedUser.getEmail());
        verify(passwordEncoder).encode("password123");
    }

    @Test
    public void testRegisterNewUser_UsernameAlreadyExists() {
        // Arrange
        UserPassRecoveryDTO userPassRecoveryDto = new UserPassRecoveryDTO();
        userPassRecoveryDto.setUsername("testUser");

        when(userRepository.existsByUsername("testUser")).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> usersService.registerNewUser(userPassRecoveryDto));

        verify(userRepository, never()).save(any(User.class));
    }

}
