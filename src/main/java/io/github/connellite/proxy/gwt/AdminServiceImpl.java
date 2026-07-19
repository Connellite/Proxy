package io.github.connellite.proxy.gwt;

#if SPRING_BOOT_3
import com.google.gwt.user.server.rpc.jakarta.RemoteServiceServlet;
import jakarta.servlet.http.HttpServletRequest;
#else

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

import javax.servlet.http.HttpServletRequest;
#endif
import com.google.gwt.user.server.rpc.SerializationPolicy;
import com.google.gwt.user.server.rpc.SerializationPolicyLoader;
import io.github.connellite.proxy.client.rpc.AdminRpcException;
import io.github.connellite.proxy.client.rpc.AdminService;
import io.github.connellite.proxy.client.rpc.dto.AdminRowDto;
import io.github.connellite.proxy.client.rpc.dto.DashboardDto;
import io.github.connellite.proxy.client.rpc.dto.EncryptionDto;
import io.github.connellite.proxy.client.rpc.dto.PasswordChangeDto;
import io.github.connellite.proxy.client.rpc.dto.SettingsDto;
import io.github.connellite.proxy.client.rpc.dto.TlsStatusDto;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxiesPageDto;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyFormDto;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyRowDto;
import io.github.connellite.proxy.client.rpc.dto.UserFormDto;
import io.github.connellite.proxy.client.rpc.dto.UserRowDto;
import io.github.connellite.proxy.client.rpc.dto.UsersPageDto;
import io.github.connellite.proxy.util.LocalBindAddresses;
import io.github.connellite.proxy.dto.AppSettings;
import io.github.connellite.proxy.dto.EncryptionForm;
import io.github.connellite.proxy.dto.PasswordChangeForm;
import io.github.connellite.proxy.dto.ProxyUserForm;
import io.github.connellite.proxy.dto.TlsStatus;
import io.github.connellite.proxy.dto.UpstreamProxyForm;
import io.github.connellite.proxy.dto.UserThroughput;
import io.github.connellite.proxy.model.AdminAccount;
import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.model.UpstreamProxy;
import io.github.connellite.proxy.model.UpstreamProxyType;
import io.github.connellite.proxy.proxy.ProxyServerManager;
import io.github.connellite.proxy.proxy.ProxyTlsService;
import io.github.connellite.proxy.security.AdminAccountService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.github.connellite.proxy.service.ProxyUserService;
import io.github.connellite.proxy.service.SettingsService;
import io.github.connellite.proxy.service.TrafficStatsService;
import io.github.connellite.proxy.service.UpstreamProxyService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AdminServiceImpl extends RemoteServiceServlet implements AdminService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProxyUserService userService;
    private final UpstreamProxyService upstreamProxyService;
    private final SettingsService settingsService;
    private final ProxyServerManager proxyServerManager;
    private final ProxyMetrics proxyMetrics;
    private final TrafficStatsService trafficStatsService;
    private final AdminAccountService adminAccountService;
    private final ProxyTlsService tlsService;
    private final ZoneId appZoneId;

    public AdminServiceImpl(ProxyUserService userService,
                            UpstreamProxyService upstreamProxyService,
                            SettingsService settingsService,
                            ProxyServerManager proxyServerManager,
                            ProxyMetrics proxyMetrics,
                            TrafficStatsService trafficStatsService,
                            AdminAccountService adminAccountService,
                            ProxyTlsService tlsService,
                            ZoneId appZoneId) {
        this.userService = userService;
        this.upstreamProxyService = upstreamProxyService;
        this.settingsService = settingsService;
        this.proxyServerManager = proxyServerManager;
        this.proxyMetrics = proxyMetrics;
        this.trafficStatsService = trafficStatsService;
        this.adminAccountService = adminAccountService;
        this.tlsService = tlsService;
        this.appZoneId = appZoneId;
    }

    /**
     * Spring Boot serves {@code *.gwt.rpc} from the classpath ({@code static/proxyAdmin/}),
     * not via {@code ServletContext#getResource}, so load the policy explicitly.
     */
    @Override
    protected SerializationPolicy doGetSerializationPolicy(HttpServletRequest request,
                                                           String moduleBaseURL,
                                                           String strongName) {
        String resourcePath = "static/proxyAdmin/" + strongName + ".gwt.rpc";
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (stream != null) {
                return SerializationPolicyLoader.loadFromStream(stream, null);
            }
        } catch (IOException | ParseException ex) {
            log.warn("Failed to load GWT serialization policy {}", resourcePath, ex);
        }
        SerializationPolicy fallback = super.doGetSerializationPolicy(request, moduleBaseURL, strongName);
        if (fallback == null) {
            log.warn("GWT serialization policy not found for strongName={} (looked for classpath:{})",
                    strongName, resourcePath);
        }
        return fallback;
    }

    @Override
    public DashboardDto getDashboard() {
        AppSettings settings = settingsService.get();
        List<ProxyUser> users = userService.findAll();
        DashboardDto dto = new DashboardDto();
        dto.setHttpRunning(proxyServerManager.isHttpRunning());
        dto.setHttpsRunning(proxyServerManager.isHttpsRunning());
        dto.setSocksRunning(proxyServerManager.isSocksRunning());
        dto.setHttpBind(bindLabel(dto.isHttpRunning(), settings.getHttpBindHost(), settings.getHttpPort()));
        dto.setHttpsBind(bindLabel(dto.isHttpsRunning(), settings.getHttpsBindHost(), settings.getHttpsPort()));
        dto.setSocksBind(bindLabel(dto.isSocksRunning(), settings.getSocksBindHost(), settings.getSocksPort()));
        dto.setHttpPort(settings.getHttpPort());
        dto.setHttpsPort(settings.getHttpsPort());
        dto.setSocksPort(settings.getSocksPort());
        dto.setActiveConnections(proxyMetrics.getActiveConnections());
        dto.setUserCount(users.size());
        dto.setEnabledUsers((int) users.stream().filter(ProxyUser::isUsable).count());
        dto.setLastError(proxyServerManager.getLastError());
        dto.setTotalBytesUp(proxyMetrics.getBytesUpTotal());
        dto.setTotalBytesDown(proxyMetrics.getBytesDownTotal());
        dto.setSessionBytesUp(proxyMetrics.getBytesUpSession());
        dto.setSessionBytesDown(proxyMetrics.getBytesDownSession());
        return dto;
    }

    @Override
    public UsersPageDto getUsers() {
        UsersPageDto page = new UsersPageDto();
        page.setAdmins(new ArrayList<>());
        for (AdminAccount admin : adminAccountService.findAll()) {
            AdminRowDto row = new AdminRowDto();
            row.setUsername(admin.getUsername());
            row.setUpdatedAt(formatInstant(admin.getUpdatedAt()));
            page.getAdmins().add(row);
        }
        page.setUsers(new ArrayList<>());
        for (ProxyUser user : userService.findAll()) {
            UserThroughput speed = trafficStatsService.throughputFor(user.getId());
            UserRowDto row = new UserRowDto();
            row.setId(user.getId());
            row.setUsername(user.getUsername());
            row.setEnabled(user.isEnabled());
            row.setUsable(user.isUsable());
            row.setExpired(user.isExpired());
            row.setMaxConnections(user.getMaxConnections());
            row.setExpiresAt(formatInstant(user.getExpiresAt()));
            row.setBytesUp(user.getBytesUp());
            row.setBytesDown(user.getBytesDown());
            row.setActiveConnections(userService.activeConnections(user.getId()));
            row.setUpBps(speed.upBytesPerSec());
            row.setDownBps(speed.downBytesPerSec());
            row.setLastUsedAt(formatInstant(user.getLastUsedAt()));
            page.getUsers().add(row);
        }
        return page;
    }

    @Override
    public UserFormDto getUserForm(Long id) {
        UserFormDto form = new UserFormDto();
        if (id == null) {
            form.setCreating(true);
            form.setEnabled(true);
            return form;
        }
        ProxyUser user = userService.getRequired(id);
        form.setCreating(false);
        form.setId(user.getId());
        form.setUsername(user.getUsername());
        form.setEnabled(user.isEnabled());
        form.setMaxConnections(user.getMaxConnections());
        if (user.getExpiresAt() != null) {
            form.setExpiresAt(DATE_FMT.format(user.getExpiresAt().atZone(appZoneId).toLocalDate()));
        }
        return form;
    }

    @Override
    public void createUser(UserFormDto form) throws AdminRpcException {
        try {
            userService.create(toProxyUserForm(form));
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to create user", ex);
        }
    }

    @Override
    public void updateUser(UserFormDto form) throws AdminRpcException {
        if (form.getId() == null) {
            throw new AdminRpcException("User id is required");
        }
        try {
            userService.update(form.getId(), toProxyUserForm(form));
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to update user", ex);
        }
    }

    @Override
    public void setUserEnabled(long id, boolean enabled) {
        userService.setEnabled(id, enabled);
    }

    @Override
    public void resetUserTraffic(long id) {
        userService.resetTraffic(id);
    }

    @Override
    public void deleteUser(long id) {
        userService.delete(id);
    }

    @Override
    public UpstreamProxiesPageDto getUpstreamProxies() {
        UpstreamProxiesPageDto page = new UpstreamProxiesPageDto();
        page.setProxies(new ArrayList<>());
        for (UpstreamProxy proxy : upstreamProxyService.findAll()) {
            UpstreamProxyRowDto row = new UpstreamProxyRowDto();
            row.setId(proxy.getId());
            row.setName(proxy.getName());
            row.setType(proxy.getType() == null ? UpstreamProxyType.HTTP.name() : proxy.getType().name());
            row.setHost(proxy.getHost());
            row.setPort(proxy.getPort());
            row.setUsername(proxy.getUsername());
            row.setSelected(proxy.isSelected());
            row.setAuthEnabled(proxy.hasAuth());
            page.getProxies().add(row);
            if (proxy.isSelected()) {
                page.setSelectedId(proxy.getId());
            }
        }
        return page;
    }

    @Override
    public UpstreamProxyFormDto getUpstreamProxyForm(Long id) {
        UpstreamProxyFormDto form = new UpstreamProxyFormDto();
        if (id == null) {
            form.setCreating(true);
            form.setType(UpstreamProxyType.HTTP.name());
            form.setPort(8080);
            return form;
        }
        UpstreamProxy proxy = upstreamProxyService.getRequired(id);
        form.setCreating(false);
        form.setId(proxy.getId());
        form.setName(proxy.getName());
        form.setType(proxy.getType() == null ? UpstreamProxyType.HTTP.name() : proxy.getType().name());
        form.setHost(proxy.getHost());
        form.setPort(proxy.getPort());
        form.setUsername(proxy.getUsername());
        form.setPasswordSaved(StringUtils.isNotBlank(proxy.getPassword()));
        return form;
    }

    @Override
    public void createUpstreamProxy(UpstreamProxyFormDto form) throws AdminRpcException {
        try {
            upstreamProxyService.create(toUpstreamForm(form, true));
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to create upstream proxy", ex);
        }
    }

    @Override
    public void updateUpstreamProxy(UpstreamProxyFormDto form) throws AdminRpcException {
        if (form.getId() == null) {
            throw new AdminRpcException("Upstream proxy id is required");
        }
        try {
            upstreamProxyService.update(form.getId(), toUpstreamForm(form, false));
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to update upstream proxy", ex);
        }
    }

    @Override
    public void deleteUpstreamProxy(long id) {
        upstreamProxyService.delete(id);
    }

    @Override
    public void selectUpstreamProxy(long id) throws AdminRpcException {
        try {
            upstreamProxyService.select(id);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to select upstream proxy", ex);
        }
    }

    @Override
    public void clearUpstreamProxySelection() {
        upstreamProxyService.clearSelection();
    }

    @Override
    public SettingsDto getSettings() {
        return toSettingsDto(settingsService.get());
    }

    @Override
    public void saveSettings(SettingsDto form) throws AdminRpcException {
        try {
            AppSettings settings = settingsService.get();
            applySettings(settings, form);
            settingsService.save(settings);
            proxyServerManager.restart();
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to save settings", ex);
        }
    }

    @Override
    public void restartProxy() {
        proxyServerManager.restart();
    }

    @Override
    public void changePassword(PasswordChangeDto form) throws AdminRpcException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AdminRpcException("Not authenticated");
        }
        PasswordChangeForm passwordForm = new PasswordChangeForm();
        passwordForm.setCurrentPassword(form.getCurrentPassword());
        passwordForm.setNewPassword(form.getNewPassword());
        passwordForm.setConfirmPassword(form.getConfirmPassword());
        try {
            adminAccountService.changePassword(auth.getName(), passwordForm);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to change password", ex);
        }
    }

    @Override
    public EncryptionDto getEncryption() {
        AppSettings settings = settingsService.get();
        EncryptionDto dto = toEncryptionDto(settings);
        dto.setTlsStatus(toTlsStatusDto(tlsService.status(settings)));
        return dto;
    }

    @Override
    public TlsStatusDto previewEncryption(EncryptionDto form) throws AdminRpcException {
        try {
            AppSettings preview = copySettings(settingsService.get());
            applyEncryption(preview, form);
            return toTlsStatusDto(tlsService.status(preview));
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to preview encryption", ex);
        }
    }

    @Override
    public void saveEncryption(EncryptionDto form) throws AdminRpcException {
        try {
            AppSettings settings = settingsService.get();
            applyEncryption(settings, form);
            tlsService.validateSettingsOrThrow(settings);
            settingsService.save(settings);
            proxyServerManager.restart();
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to save encryption settings", ex);
        }
    }

    private SettingsDto toSettingsDto(AppSettings settings) {
        SettingsDto dto = new SettingsDto();
        dto.setHttpEnabled(settings.isHttpEnabled());
        dto.setHttpBindHost(settings.getHttpBindHost());
        dto.setHttpPort(settings.getHttpPort());
        dto.setSocksEnabled(settings.isSocksEnabled());
        dto.setSocksBindHost(settings.getSocksBindHost());
        dto.setSocksPort(settings.getSocksPort());
        dto.setHttpAuthRequired(settings.isHttpAuthRequired());
        dto.setSocksAuthRequired(settings.isSocksAuthRequired());
        dto.setSocksUdpEnabled(settings.isSocksUdpEnabled());
        dto.setAdminServerPort(settings.getAdminServerPort());
        dto.setHttpRunning(proxyServerManager.isHttpRunning());
        dto.setHttpsRunning(proxyServerManager.isHttpsRunning());
        dto.setSocksRunning(proxyServerManager.isSocksRunning());
        dto.setLastError(proxyServerManager.getLastError());
        dto.setBindHostOptions(new ArrayList<>(LocalBindAddresses.optionsIncluding(settings.getHttpBindHost(), settings.getSocksBindHost())));
        return dto;
    }

    private EncryptionDto toEncryptionDto(AppSettings settings) {
        EncryptionDto dto = new EncryptionDto();
        dto.setHttpsEnabled(settings.isHttpsEnabled());
        dto.setHttpsBindHost(settings.getHttpsBindHost());
        dto.setHttpsPort(settings.getHttpsPort());
        dto.setServerName(settings.getHttpsServerName() != null ? settings.getHttpsServerName() : "");
        dto.setCertificateChain(settings.getHttpsCertificateChain());
        dto.setCertificatePath(settings.getHttpsCertificatePath());
        dto.setPrivateKey(null);
        dto.setPrivateKeyPath(settings.getHttpsPrivateKeyPath());
        dto.setPrivateKeySaved(StringUtils.isNotBlank(settings.getHttpsPrivateKey()));
        dto.setHttpsRunning(proxyServerManager.isHttpsRunning());
        dto.setLastError(proxyServerManager.getLastError());
        dto.setBindHostOptions(new ArrayList<>(LocalBindAddresses.optionsIncluding(settings.getHttpsBindHost())));
        return dto;
    }

    private static void applySettings(AppSettings settings, SettingsDto form) {
        settings.setHttpEnabled(form.isHttpEnabled());
        settings.setHttpBindHost(StringUtils.trimToEmpty(form.getHttpBindHost()));
        settings.setHttpPort(form.getHttpPort());
        settings.setSocksEnabled(form.isSocksEnabled());
        settings.setSocksBindHost(StringUtils.trimToEmpty(form.getSocksBindHost()));
        settings.setSocksPort(form.getSocksPort());
        settings.setHttpAuthRequired(form.isHttpAuthRequired());
        settings.setSocksAuthRequired(form.isSocksAuthRequired());
        settings.setSocksUdpEnabled(form.isSocksUdpEnabled());
        settings.setAdminServerPort(form.getAdminServerPort());
    }

    private static void applyEncryption(AppSettings settings, EncryptionDto form) {
        EncryptionForm bridge = new EncryptionForm();
        bridge.setHttpsEnabled(form.isHttpsEnabled());
        bridge.setHttpsBindHost(form.getHttpsBindHost());
        bridge.setHttpsPort(form.getHttpsPort());
        bridge.setServerName(form.getServerName());
        bridge.setCertificateChain(form.getCertificateChain());
        bridge.setCertificatePath(form.getCertificatePath());
        bridge.setPrivateKey(form.getPrivateKey());
        bridge.setPrivateKeyPath(form.getPrivateKeyPath());
        bridge.setPrivateKeySaved(form.isPrivateKeySaved());

        settings.setHttpsEnabled(bridge.isHttpsEnabled());
        settings.setHttpsBindHost(bridge.getHttpsBindHost().trim());
        settings.setHttpsPort(bridge.getHttpsPort());
        settings.setHttpsServerName(StringUtils.trimToNull(bridge.getServerName()));
        applyCertificateFields(settings, bridge);
        applyPrivateKeyFields(settings, bridge);
    }

    private static void applyCertificateFields(AppSettings settings, EncryptionForm form) {
        String chain = StringUtils.trimToNull(form.getCertificateChain());
        String path = StringUtils.trimToNull(form.getCertificatePath());
        if (chain != null && path != null) {
            throw new IllegalArgumentException("certificate data and file can't be set together");
        }
        if (path != null) {
            settings.setHttpsCertificatePath(path);
            settings.setHttpsCertificateChain(null);
        } else {
            settings.setHttpsCertificatePath(null);
            settings.setHttpsCertificateChain(chain);
        }
    }

    private static void applyPrivateKeyFields(AppSettings settings, EncryptionForm form) {
        String key = form.getPrivateKey();
        boolean keyProvided = StringUtils.isNotBlank(key);
        String path = StringUtils.trimToNull(form.getPrivateKeyPath());
        if (keyProvided && path != null) {
            throw new IllegalArgumentException("private key data and file can't be set together");
        }
        if (path != null) {
            settings.setHttpsPrivateKeyPath(path);
            settings.setHttpsPrivateKey(null);
        } else if (keyProvided) {
            settings.setHttpsPrivateKey(key.trim());
            settings.setHttpsPrivateKeyPath(null);
        } else if (form.isPrivateKeySaved()) {
            settings.setHttpsPrivateKeyPath(null);
        } else {
            settings.setHttpsPrivateKey(null);
            settings.setHttpsPrivateKeyPath(null);
        }
    }

    private static UpstreamProxyForm toUpstreamForm(UpstreamProxyFormDto form, boolean creating) {
        UpstreamProxyForm target = new UpstreamProxyForm();
        target.setName(form.getName());
        try {
            target.setType(UpstreamProxyType.valueOf(
                    StringUtils.isBlank(form.getType()) ? "HTTP" : form.getType().trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Type must be HTTP or SOCKS5");
        }
        target.setHost(form.getHost());
        target.setPort(form.getPort());
        target.setUsername(form.getUsername());
        target.setPassword(form.getPassword());
        target.setUpdatePassword(creating || StringUtils.isNotBlank(form.getPassword()));
        return target;
    }

    private static ProxyUserForm toProxyUserForm(UserFormDto form) {
        ProxyUserForm target = new ProxyUserForm();
        target.setId(form.getId());
        target.setUsername(form.getUsername());
        target.setPassword(form.getPassword());
        target.setEnabled(form.isEnabled());
        target.setMaxConnections(form.getMaxConnections());
        target.setExpiresAt(form.getExpiresAt());
        return target;
    }

    private TlsStatusDto toTlsStatusDto(TlsStatus status) {
        TlsStatusDto dto = new TlsStatusDto();
        dto.setUsingCustomCertificate(status.isUsingCustomCertificate());
        dto.setValidCert(status.isValidCert());
        dto.setValidKey(status.isValidKey());
        dto.setValidChain(status.isValidChain());
        dto.setValidPair(status.isValidPair());
        dto.setPrivateKeySaved(status.isPrivateKeySaved());
        dto.setKeyType(status.getKeyType());
        dto.setSubject(status.getSubject());
        dto.setIssuer(status.getIssuer());
        dto.setNotBefore(status.getNotBefore() == null
                ? null
                : DATE_TIME_FMT.format(status.getNotBefore().atZone(appZoneId)));
        dto.setNotAfter(status.getNotAfter() == null
                ? null
                : DATE_TIME_FMT.format(status.getNotAfter().atZone(appZoneId)));
        dto.setWarningValidation(status.getWarningValidation());
        dto.setDnsNames(status.getDnsNames() == null ? new ArrayList<>() : new ArrayList<>(status.getDnsNames()));
        return dto;
    }

    private static AppSettings copySettings(AppSettings source) {
        AppSettings copy = new AppSettings();
        copy.setHttpEnabled(source.isHttpEnabled());
        copy.setHttpBindHost(source.getHttpBindHost());
        copy.setHttpPort(source.getHttpPort());
        copy.setHttpsEnabled(source.isHttpsEnabled());
        copy.setHttpsBindHost(source.getHttpsBindHost());
        copy.setHttpsPort(source.getHttpsPort());
        copy.setHttpsServerName(source.getHttpsServerName());
        copy.setHttpsCertificateChain(source.getHttpsCertificateChain());
        copy.setHttpsCertificatePath(source.getHttpsCertificatePath());
        copy.setHttpsPrivateKey(source.getHttpsPrivateKey());
        copy.setHttpsPrivateKeyPath(source.getHttpsPrivateKeyPath());
        copy.setSocksEnabled(source.isSocksEnabled());
        copy.setSocksBindHost(source.getSocksBindHost());
        copy.setSocksPort(source.getSocksPort());
        copy.setHttpAuthRequired(source.isHttpAuthRequired());
        copy.setSocksAuthRequired(source.isSocksAuthRequired());
        copy.setSocksUdpEnabled(source.isSocksUdpEnabled());
        copy.setAdminServerPort(source.getAdminServerPort());
        copy.setBytesUpTotal(source.getBytesUpTotal());
        copy.setBytesDownTotal(source.getBytesDownTotal());
        return copy;
    }

    private AdminRpcException toRpcException(String action, Throwable ex) {
        log.error(action, ex);
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (StringUtils.isBlank(message)) {
            message = root.getClass().getSimpleName();
        }
        return new AdminRpcException(message);
    }

    private String formatInstant(java.time.Instant instant) {
        if (instant == null) {
            return null;
        }
        return DATE_TIME_FMT.format(instant.atZone(appZoneId));
    }

    private static String bindLabel(boolean running, String host, int port) {
        return running ? host + ":" + port : "stopped";
    }
}
