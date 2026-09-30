package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, String> {
}
