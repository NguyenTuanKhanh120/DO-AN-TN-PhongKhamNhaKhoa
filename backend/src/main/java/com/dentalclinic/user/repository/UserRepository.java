package com.dentalclinic.user.repository;

import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRoleAndStatus(Role role, UserStatus status);
}
