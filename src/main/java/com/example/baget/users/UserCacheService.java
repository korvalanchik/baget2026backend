package com.example.baget.users;

import com.example.baget.common.cache.AbstractLookupCacheService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserCacheService extends AbstractLookupCacheService<Long, UserDTO> {

    private final UsersRepository userRepository;

    public UserCacheService(UsersRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected String cacheName() {
        return "UserCache";
    }

    @Override
    protected List<UserDTO> loadAll() {
        return userRepository.findAll().stream()
                .map(user -> new UserDTO(user.getId(), user.getUsername()))
                .collect(Collectors.toList());
    }

    @Override
    protected UserDTO loadById(Long id) {
        return userRepository.findById(id)
                .map(user -> new UserDTO(user.getId(), user.getUsername()))
                .orElse(null);
    }

    public Map<Long, String> loadMap() {
        List<UserDTO> users = findAll();

        if (users == null) {
            return Collections.emptyMap();
        }

        return users.stream()
                .filter(user -> user.getId() != null && user.getUsername() != null)
                .collect(Collectors.toMap(
                        UserDTO::getId,
                        UserDTO::getUsername,
                        (existing, replacement) -> existing // уникнути дубліката ключів
                ));
    }

}
