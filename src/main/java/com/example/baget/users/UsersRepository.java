package com.example.baget.users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface UsersRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String userName);
    boolean existsByEmail(String email);
    Optional<User> findByTelegramId(Long telegramId);
    @Query("SELECT u.id, u.username FROM User u")
    List<Object[]> findAllUserIdAndUsername();

    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN u.roles r
        JOIN u.allowedBranches b
        WHERE r.name = :roleName
          AND b.name = :branchName
    """)
    List<User> findUsersByRoleAndBranch(
            @Param("roleName") String roleName,
            @Param("branchName") String branchName
    );

//    boolean userBelongsToBranch(Optional<User> user, Long branchId);

}
