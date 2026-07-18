package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.domain.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingsRepository extends JpaRepository<AppSettings, String> {
}
