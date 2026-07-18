package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.domain.AdminAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, String> {
}
