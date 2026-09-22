package com.example.baget.jwt;

import com.example.baget.users.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/auth")
public class AuthRestController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final UsersService userService;
    private final UserDetailsService userDetailsService;
    private final RoleRepository roleRepository;
    private final UsersRepository userRepository;
    private final TelegramAuthService telegramAuthService;

    // Ін'єкція через конструктор
    @Autowired
    public AuthRestController(AuthenticationManager authenticationManager,
                          JwtTokenUtil jwtTokenUtil,
                          UsersService userService,
                          UserDetailsService userDetailsService,
                          RoleRepository roleRepository,
                          UsersRepository userRepository,
                          TelegramAuthService telegramAuthService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.userService = userService;
        this.userDetailsService = userDetailsService;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.telegramAuthService = telegramAuthService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody UserPassRecoveryDTO userPassRecoveryDto) {
        try {
            userService.registerNewUser(userPassRecoveryDto);
            return ResponseEntity.ok("User registered successfully!");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> loginUser(@RequestBody AuthRequest authRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials!");
        }

        final UserDetails userDetails = userDetailsService.loadUserByUsername(authRequest.getUsername());
        final String jwt = jwtTokenUtil.generateToken(userDetails);

        return ResponseEntity.ok(new AuthResponse(jwt));
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String token) {
        try {
            // Видалити префікс "Bearer " з токену
            String jwtToken = token.substring(7);

            // Отримати ім'я користувача з токену
            String username = jwtTokenUtil.getUsernameFromToken(jwtToken);

            // Завантажити користувача з бази даних
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Перевірити, чи токен дійсний
            if (jwtTokenUtil.validateToken(jwtToken, userDetails)) {
                return ResponseEntity.ok("Token is valid");
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }
    }

    @PostMapping("/loginTelegram")
    public ResponseEntity<?> telegramLogin(@RequestBody TelegramAuthRequest request) {
        String initData = request.getInitData();
        return telegramAuthService.verifyTelegramLogin(initData);
    }

    // Метод для перевірки та прив'язки Telegram ID до існуючого акаунту
    @PostMapping("/associateTelegram")
    public ResponseEntity<?> associateTelegramId(@RequestBody TelegramAuthRequest request,
                                                 @RequestHeader("Authorization") String token) {
        String initData = request.getInitData();
        // Отримуємо токен та перевіряємо, чи він валідний
        String jwtToken = token.replace("Bearer ", "");
        String username = jwtTokenUtil.getUsernameFromToken(jwtToken);

        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid JWT token");
        }

        // Отримуємо Telegram ID з initDate
        Long telegramId = telegramAuthService.getTelegramId(initData);

        // Знаходимо користувача за ім'ям
        User user = userRepository.findByUsername(username).orElse(null);
//        User user = userRepository.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

//        User user = userOptional.get();

        // Якщо у користувача ще немає прив'язаного Telegram ID
        if (user.getTelegramId() == null) {
            user.setTelegramId(telegramId);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Telegram ID прив'язано"));
        }
        // Якщо користувач вже має прив'язаний Telegram ID
        if (user.getTelegramId().equals(telegramId)) {
            return ResponseEntity.ok(Map.of("message", "User already associated with his Telegram ID"));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Telegram ID вже прив'язано до іншого акаунту");
    }

    @PostMapping("/validateTelegram")
    public ResponseEntity<?> validateTelegram(@RequestHeader("Authorization") Long telegramId) {
        try {
            // Перевірка, чи існує користувач з таким Telegram ID
            boolean isValid = userService.validateTelegramId(telegramId);

            if (isValid) {
                return ResponseEntity.ok("Telegram ID is valid");
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Telegram ID");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Telegram ID");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser() {
        // Можна додати додаткові дії на стороні сервера, такі як інвалідовування токена
        // Але стандартно, клієнт просто видаляє токен зі своєї сторони

        // Наприклад, можна додати токен в blacklist на сервері
        // або встановити його строк дії як завершений, якщо у вас є такий механізм

        return ResponseEntity.ok("Logout successful");
    }

    @PostMapping("/password-recovery")
    public ResponseEntity<String> sendRecoveryLink(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email is required.");
        }
        String currentDateTimeWithTimezone = request.get("currentDateTime");
        OffsetDateTime clientDateTime = OffsetDateTime.parse(currentDateTimeWithTimezone);
        userService.sendPasswordRecoveryEmail(email, clientDateTime);
        return ResponseEntity.ok("Якщо цей email існує, посиляння буде відправлене на нього.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        String newPassword = request.get("password");
        String currentDateTimeWithTimezone = request.get("currentDateTime");
        OffsetDateTime clientDateTime = OffsetDateTime.parse(currentDateTimeWithTimezone);
        userService.resetPassword(token, newPassword, clientDateTime);
        return ResponseEntity.ok("Пароль успішно змінений.");
    }

    @GetMapping("/check-role")
    public ResponseEntity<List<String>> checkRoles(Principal principal) {
        List<String> roles = new ArrayList<>();

        if (principal != null) {
            // Отримуємо всі ролі користувача
            roles = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                    .stream()
                    .map(GrantedAuthority::getAuthority) // Отримуємо роль у вигляді рядка
                    .collect(Collectors.toList()); // Перетворюємо в список
        } else {
            roles.add("NOT AUTHORIZED");
        }
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/roles")
    public ResponseEntity<List<RoleDTO>> getAllRoles() {
        List<Role> roles = roleRepository.findAll();
        List<RoleDTO> roleDTOs = roles.stream()
                .map(role -> new RoleDTO(role.getId(), role.getName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(roleDTOs);
    }

}
