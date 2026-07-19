package io.github.connellite.proxy.service;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.model.AppSettings;
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
        repository.findById(AppSettings.SINGLETON_ID).ifPresent(existing -> {
            // Do not clobber lifetime traffic flushed concurrently.
            settings.setBytesUpTotal(existing.getBytesUpTotal());
            settings.setBytesDownTotal(existing.getBytesDownTotal());
        });
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
        settings.setHttpsEnabled(properties.getHttps().isEnabled());
        settings.setHttpsBindHost(properties.getHttps().getBindHost());
        settings.setHttpsPort(properties.getHttps().getPort());
        settings.setHttpsServerName(blankToNull(properties.getTls().getServerName()));
        settings.setHttpsCertificatePath(blankToNull(properties.getTls().getCertificatePath()));
        settings.setHttpsPrivateKeyPath(blankToNull(properties.getTls().getPrivateKeyPath()));
        settings.setSocksEnabled(properties.getSocks5().isEnabled());
        settings.setSocksBindHost(properties.getSocks5().getBindHost());
        settings.setSocksPort(properties.getSocks5().getPort());
        settings.setHttpAuthRequired(properties.isHttpAuthRequired());
        settings.setSocksAuthRequired(properties.isSocksAuthRequired());
        return settings;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
