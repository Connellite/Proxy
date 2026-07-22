package io.github.connellite.proxy.service;

import io.github.connellite.proxy.util.AdminServerPortStore;
import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.dto.AppSettings;
import io.github.connellite.proxy.model.ConfigEntry;
import io.github.connellite.proxy.repository.ConfigRepository;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final ConfigRepository repository;
    private final ProxyProperties properties;

    @Transactional(readOnly = true)
    public AppSettings get() {
        List<ConfigEntry> entries = repository.findAll();
        if (entries.isEmpty()) {
            return defaultsFromProperties();
        }
        return fromEntries(entries);
    }

    @Transactional
    public AppSettings save(AppSettings settings) {
        Map<String, String> existing = toMap(repository.findAll());
        // Do not clobber lifetime traffic flushed concurrently.
        if (existing.containsKey(ConfigEntry.BYTES_UP_TOTAL)) {
            settings.setBytesUpTotal(parseLong(existing.get(ConfigEntry.BYTES_UP_TOTAL), settings.getBytesUpTotal()));
        }
        if (existing.containsKey(ConfigEntry.BYTES_DOWN_TOTAL)) {
            settings.setBytesDownTotal(parseLong(existing.get(ConfigEntry.BYTES_DOWN_TOTAL), settings.getBytesDownTotal()));
        }
        if (!AdminServerPortStore.isValidPort(settings.getAdminServerPort())) {
            throw new IllegalArgumentException("Admin port must be between 1 and 65535");
        }
        repository.saveAll(toEntries(settings));
        return settings;
    }

    @Transactional
    public AppSettings ensureInitialized() {
        if (repository.count() > 0) {
            return fromEntries(repository.findAll());
        }
        AppSettings defaults = defaultsFromProperties();
        repository.saveAll(toEntries(defaults));
        return defaults;
    }

    private AppSettings defaultsFromProperties() {
        AppSettings settings = new AppSettings();
        settings.setHttpEnabled(properties.getHttp().isEnabled());
        settings.setHttpBindHost(properties.getHttp().getBindHost());
        settings.setHttpPort(properties.getHttp().getPort());
        settings.setHttpsEnabled(properties.getHttps().isEnabled());
        settings.setHttpsBindHost(properties.getHttps().getBindHost());
        settings.setHttpsPort(properties.getHttps().getPort());
        settings.setHttpsServerName(StringUtils.trimToNull(properties.getTls().getServerName()));
        settings.setHttpsCertificatePath(StringUtils.trimToNull(properties.getTls().getCertificatePath()));
        settings.setHttpsPrivateKeyPath(StringUtils.trimToNull(properties.getTls().getPrivateKeyPath()));
        settings.setSocksEnabled(properties.getSocks5().isEnabled());
        settings.setSocksBindHost(properties.getSocks5().getBindHost());
        settings.setSocksPort(properties.getSocks5().getPort());
        settings.setHttpAuthRequired(properties.isHttpAuthRequired());
        settings.setSocksAuthRequired(properties.isSocksAuthRequired());
        settings.setSocksUdpEnabled(properties.isSocksUdpEnabled());
        settings.setSshEnabled(properties.getSsh().isEnabled());
        settings.setSshBindHost(properties.getSsh().getBindHost());
        settings.setSshPort(properties.getSsh().getPort());
        settings.setAdminServerPort(AdminServerPortStore.DEFAULT_PORT);
        return settings;
    }

    private static AppSettings fromEntries(List<ConfigEntry> entries) {
        Map<String, String> map = toMap(entries);
        AppSettings settings = new AppSettings();
        settings.setHttpEnabled(parseBoolean(map.get(ConfigEntry.HTTP_ENABLED), settings.isHttpEnabled()));
        settings.setHttpBindHost(parseString(map.get(ConfigEntry.HTTP_BIND_HOST), settings.getHttpBindHost()));
        settings.setHttpPort(parseInt(map.get(ConfigEntry.HTTP_PORT), settings.getHttpPort()));
        settings.setHttpsEnabled(parseBoolean(map.get(ConfigEntry.HTTPS_ENABLED), settings.isHttpsEnabled()));
        settings.setHttpsBindHost(parseString(map.get(ConfigEntry.HTTPS_BIND_HOST), settings.getHttpsBindHost()));
        settings.setHttpsPort(parseInt(map.get(ConfigEntry.HTTPS_PORT), settings.getHttpsPort()));
        settings.setHttpsServerName(StringUtils.trimToNull(map.get(ConfigEntry.HTTPS_SERVER_NAME)));
        settings.setHttpsCertificateChain(StringUtils.trimToNull(map.get(ConfigEntry.HTTPS_CERTIFICATE_CHAIN)));
        settings.setHttpsCertificatePath(StringUtils.trimToNull(map.get(ConfigEntry.HTTPS_CERTIFICATE_PATH)));
        settings.setHttpsPrivateKey(StringUtils.trimToNull(map.get(ConfigEntry.HTTPS_PRIVATE_KEY)));
        settings.setHttpsPrivateKeyPath(StringUtils.trimToNull(map.get(ConfigEntry.HTTPS_PRIVATE_KEY_PATH)));
        settings.setSocksEnabled(parseBoolean(map.get(ConfigEntry.SOCKS_ENABLED), settings.isSocksEnabled()));
        settings.setSocksBindHost(parseString(map.get(ConfigEntry.SOCKS_BIND_HOST), settings.getSocksBindHost()));
        settings.setSocksPort(parseInt(map.get(ConfigEntry.SOCKS_PORT), settings.getSocksPort()));
        settings.setHttpAuthRequired(parseBoolean(map.get(ConfigEntry.HTTP_AUTH_REQUIRED), settings.isHttpAuthRequired()));
        settings.setSocksAuthRequired(parseBoolean(map.get(ConfigEntry.SOCKS_AUTH_REQUIRED), settings.isSocksAuthRequired()));
        settings.setSocksUdpEnabled(parseBoolean(map.get(ConfigEntry.SOCKS_UDP_ENABLED), settings.isSocksUdpEnabled()));
        settings.setSshEnabled(parseBoolean(map.get(ConfigEntry.SSH_ENABLED), settings.isSshEnabled()));
        settings.setSshBindHost(parseString(map.get(ConfigEntry.SSH_BIND_HOST), settings.getSshBindHost()));
        settings.setSshPort(parseInt(map.get(ConfigEntry.SSH_PORT), settings.getSshPort()));
        settings.setAdminServerPort(parseInt(map.get(ConfigEntry.ADMIN_SERVER_PORT), settings.getAdminServerPort()));
        settings.setBytesUpTotal(parseLong(map.get(ConfigEntry.BYTES_UP_TOTAL), 0L));
        settings.setBytesDownTotal(parseLong(map.get(ConfigEntry.BYTES_DOWN_TOTAL), 0L));
        return settings;
    }

    private static List<ConfigEntry> toEntries(AppSettings settings) {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigEntry.HTTP_ENABLED, Boolean.toString(settings.isHttpEnabled())));
        entries.add(new ConfigEntry(ConfigEntry.HTTP_BIND_HOST, settings.getHttpBindHost()));
        entries.add(new ConfigEntry(ConfigEntry.HTTP_PORT, Integer.toString(settings.getHttpPort())));
        entries.add(new ConfigEntry(ConfigEntry.HTTPS_ENABLED, Boolean.toString(settings.isHttpsEnabled())));
        entries.add(new ConfigEntry(ConfigEntry.HTTPS_BIND_HOST, settings.getHttpsBindHost()));
        entries.add(new ConfigEntry(ConfigEntry.HTTPS_PORT, Integer.toString(settings.getHttpsPort())));
        entries.add(new ConfigEntry(ConfigEntry.HTTPS_SERVER_NAME, settings.getHttpsServerName()));
        entries.add(new ConfigEntry(ConfigEntry.HTTPS_CERTIFICATE_CHAIN, settings.getHttpsCertificateChain()));
        entries.add(new ConfigEntry(ConfigEntry.HTTPS_CERTIFICATE_PATH, settings.getHttpsCertificatePath()));
        entries.add(new ConfigEntry(ConfigEntry.HTTPS_PRIVATE_KEY, settings.getHttpsPrivateKey()));
        entries.add(new ConfigEntry(ConfigEntry.HTTPS_PRIVATE_KEY_PATH, settings.getHttpsPrivateKeyPath()));
        entries.add(new ConfigEntry(ConfigEntry.SOCKS_ENABLED, Boolean.toString(settings.isSocksEnabled())));
        entries.add(new ConfigEntry(ConfigEntry.SOCKS_BIND_HOST, settings.getSocksBindHost()));
        entries.add(new ConfigEntry(ConfigEntry.SOCKS_PORT, Integer.toString(settings.getSocksPort())));
        entries.add(new ConfigEntry(ConfigEntry.HTTP_AUTH_REQUIRED, Boolean.toString(settings.isHttpAuthRequired())));
        entries.add(new ConfigEntry(ConfigEntry.SOCKS_AUTH_REQUIRED, Boolean.toString(settings.isSocksAuthRequired())));
        entries.add(new ConfigEntry(ConfigEntry.SOCKS_UDP_ENABLED, Boolean.toString(settings.isSocksUdpEnabled())));
        entries.add(new ConfigEntry(ConfigEntry.SSH_ENABLED, Boolean.toString(settings.isSshEnabled())));
        entries.add(new ConfigEntry(ConfigEntry.SSH_BIND_HOST, settings.getSshBindHost()));
        entries.add(new ConfigEntry(ConfigEntry.SSH_PORT, Integer.toString(settings.getSshPort())));
        entries.add(new ConfigEntry(ConfigEntry.ADMIN_SERVER_PORT, Integer.toString(settings.getAdminServerPort())));
        entries.add(new ConfigEntry(ConfigEntry.BYTES_UP_TOTAL, Long.toString(settings.getBytesUpTotal())));
        entries.add(new ConfigEntry(ConfigEntry.BYTES_DOWN_TOTAL, Long.toString(settings.getBytesDownTotal())));
        return entries;
    }

    private static Map<String, String> toMap(List<ConfigEntry> entries) {
        Map<String, String> map = new HashMap<>();
        for (ConfigEntry entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    private static int parseInt(String value, int defaultValue) {
        Integer parsed = Ints.tryParse(StringUtils.defaultString(value));
        return parsed != null ? parsed : defaultValue;
    }

    private static long parseLong(String value, long defaultValue) {
        Long parsed = Longs.tryParse(StringUtils.defaultString(value));
        return parsed != null ? parsed : defaultValue;
    }

    private static String parseString(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value;
    }
}
