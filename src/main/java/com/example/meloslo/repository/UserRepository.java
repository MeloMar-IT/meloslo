package com.example.meloslo.repository;

import com.example.meloslo.model.Role;
import com.example.meloslo.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    /** Locks the matching rows until the surrounding transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<User> findByRole(Role role);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.password = :password, u.passwordChangeRequired = false where u.username = :username")
    int updatePassword(@Param("username") String username, @Param("password") String password);
}
