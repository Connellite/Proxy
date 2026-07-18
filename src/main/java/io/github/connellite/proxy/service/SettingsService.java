package io.github.connellite.proxy.service;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.domain.AppSettings;
import io.github.connellite.proxy.repository.AppSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final AppSettingsRepository repository;
    private final ProxyProperties properties;

    @Transactional(readOnly = true)
    public AppSettings get() {
        return repository.findById(AppSettings.SINGLETON_ID).orElseGet(this::defaultsFromProperties);
    }

    @Transactional
    public AppSettings save(AppSettings settings) {
        settings.setId(AppSettings.SINGLETON_ID);
        return repository.save(settings);
    }

    @Transactional
    public AppSettings ensureInitialized() {
        return repository.findById(AppSettings.SINGLETON_ID).orElseGet(() -> repository.save(defaultsFromProperties()));
    }

    private AppSettings defaultsFromProperties() {
        AppSettings settings = new AppSettings();
        settings.setId(AppSettings.SINGLETON_ID);
        settings.setHttpEnabled(properties.getHttp().isEnabled());
        settings.setHttpBindHost(properties.getHttp().getBindHost());
        settings.setHttpPort(properties.getHttp().getPort());
        settings.setSocksEnabled(properties.getSocks5().isEnabled());
        settings.setSocksBindHost(properties.getSocks5().getBindHost());
        settings.setSocksPort(properties.getSocks5().getPort());
        settings.setAuthRequired(properties.isAuthRequired());
        return settings;
    }
}
