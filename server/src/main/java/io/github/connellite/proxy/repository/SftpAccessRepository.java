package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.model.SftpAccess;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SftpAccessRepository extends JpaRepository<SftpAccess, String> {
}
