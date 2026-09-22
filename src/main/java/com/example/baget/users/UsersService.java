package com.example.baget.users;

import com.example.baget.passwordrecoverytoken.PasswordRecoveryToken;
import com.example.baget.passwordrecoverytoken.PasswordRecoveryTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UsersService {
    private final UsersRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordRecoveryTokenRepository passwordRecoveryTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    // Ін'єкція через конструктор
    @Autowired
    public UsersService(UsersRepository userRepository,
                        RoleRepository roleRepository, PasswordRecoveryTokenRepository passwordRecoveryTokenRepository,
                        EmailService emailService,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordRecoveryTokenRepository = passwordRecoveryTokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    public Page<UserDTO> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(users -> mapToDTO(users, new UserDTO()));
    }

    public void delete(final Long userNo) {
        userRepository.deleteById(userNo);
    }

    private UserDTO mapToDTO(final User user, final UserDTO userDTO) {
        userDTO.setId(user.getId());
        userDTO.setUsername(user.getUsername());
        userDTO.setEmail(user.getEmail());
        userDTO.setRoles(
                user.getRoles()
                    .stream()
                    .map(Role::getName)
                    .collect(Collectors.toList())
        );
        return userDTO;
    }

    @Transactional
    public void registerNewUser(UserPassRecoveryDTO userPassRecoveryDto) {
        // Перевірка існування користувача за username або email
        if (userRepository.existsByUsername(userPassRecoveryDto.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepository.existsByEmail(userPassRecoveryDto.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        // Отримання ролей за їх ідентифікаторами
        List<Role> roles = roleRepository.findAllByNameIn(userPassRecoveryDto.getRoles());
        // Створення нового користувача
        User user = new User();
        user.setUsername(userPassRecoveryDto.getUsername());
        user.setEmail(userPassRecoveryDto.getEmail());
        user.setPassword(passwordEncoder.encode(userPassRecoveryDto.getPassword()));
        user.setRoles(roles);
        // Збереження користувача
        userRepository.save(user);
    }

    public void sendPasswordRecoveryEmail(String email, OffsetDateTime clientDateTime) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = UUID.randomUUID().toString(); // Генеруємо токен для відновлення
        String recoveryLink = "https://ramarnya.vercel.app/auth/passreset.html?token=" + token;

        // Логіка для збереження токену у БД (для подальшої валідації)
        PasswordRecoveryToken recoveryToken = new PasswordRecoveryToken();
        recoveryToken.setUser(user);
        recoveryToken.setToken(token);
        recoveryToken.setExpiryDate(clientDateTime.plusMinutes(15)); // Токен діє 15 хвилин
        passwordRecoveryTokenRepository.save(recoveryToken);

        // Надсилання email з посиланням для відновлення паролю
        emailService.sendEmail(email,"Password Recovery", "Натисни на посилання для встановлення нового паролю: " + recoveryLink);
    }

    @Transactional
    public void resetPassword(String token, String newPassword, OffsetDateTime clientDateTime) {
        PasswordRecoveryToken recoveryToken = passwordRecoveryTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (recoveryToken.isExpired(clientDateTime)) {
            throw new IllegalArgumentException("Token has expired");
        }

        User user = recoveryToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Видалити токен після успішної зміни паролю
        passwordRecoveryTokenRepository.delete(recoveryToken);
        // Видалити всі прострочені токени
        passwordRecoveryTokenRepository.deleteAllExpiredTokens(clientDateTime);
    }

    public boolean validateTelegramId(Long telegramId) {
        // Знайти користувача за Telegram ID в базі даних
        Optional<User> userOptional = userRepository.findByTelegramId(telegramId);

        // Якщо користувач знайдений і Telegram ID валідний
        return userOptional.isPresent();

    }
}
