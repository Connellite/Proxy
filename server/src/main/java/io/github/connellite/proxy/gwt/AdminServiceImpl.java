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
import io.github.connellite.proxy.client.rpc.dto.DashboardDto;
import io.github.connellite.proxy.client.rpc.dto.EncryptionDto;
import io.github.connellite.proxy.client.rpc.dto.HttpStripHeaderRowDto;
import io.github.connellite.proxy.client.rpc.dto.HttpStripHeadersPageDto;
import io.github.connellite.proxy.client.rpc.dto.PasswordChangeDto;
import io.github.connellite.proxy.client.rpc.dto.SettingsDto;
import io.github.connellite.proxy.client.rpc.dto.TlsStatusDto;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxiesPageDto;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyFormDto;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyRowDto;
import io.github.connellite.proxy.client.rpc.dto.UserFormDto;
import io.github.connellite.proxy.client.rpc.dto.UserRowDto;
import io.github.connellite.proxy.client.rpc.dto.UsersPageDto;
import io.github.connellite.proxy.dto.AppSettings;
import io.github.connellite.proxy.dto.UserThroughput;
import io.github.connellite.proxy.mapper.AdminRpcManualMapper;
import io.github.connellite.proxy.mapper.AppSettingsMapper;
import io.github.connellite.proxy.mapper.PasswordChangeFormMapper;
import io.github.connellite.proxy.mapper.ProxyUserFormMapper;
import io.github.connellite.proxy.mapper.TlsStatusMapper;
import io.github.connellite.proxy.mapper.UpstreamProxyFormMapper;
import io.github.connellite.proxy.model.HttpStripHeader;
import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.model.UpstreamProxy;
import io.github.connellite.proxy.model.UpstreamProxyType;
import io.github.connellite.proxy.proxy.ProxyServerManager;
import io.github.connellite.proxy.proxy.http.ProxyTlsService;
import io.github.connellite.proxy.service.HttpStripHeaderService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.github.connellite.proxy.service.ProxyUserService;
import io.github.connellite.proxy.service.SettingsService;
import io.github.connellite.proxy.service.TrafficStatsService;
import io.github.connellite.proxy.service.UpstreamProxyService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class AdminServiceImpl extends RemoteServiceServlet implements AdminService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProxyUserService userService;
    private final UpstreamProxyService upstreamProxyService;
    private final HttpStripHeaderService stripHeaderService;
    private final SettingsService settingsService;
    private final ProxyServerManager proxyServerManager;
    private final ProxyMetrics proxyMetrics;
    private final TrafficStatsService trafficStatsService;
    private final ProxyTlsService tlsService;
    private final ProxyUserFormMapper proxyUserFormMapper;
    private final PasswordChangeFormMapper passwordChangeFormMapper;
    private final UpstreamProxyFormMapper upstreamProxyFormMapper;
    private final AppSettingsMapper appSettingsMapper;
    private final TlsStatusMapper tlsStatusMapper;
    private final AdminRpcManualMapper adminRpcManualMapper;
    private final ZoneId appZoneId;

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
        dto.setSshRunning(proxyServerManager.isSshRunning());
        dto.setHttpBind(bindLabel(dto.isHttpRunning(), settings.getHttpBindHost(), settings.getHttpPort()));
        dto.setHttpsBind(bindLabel(dto.isHttpsRunning(), settings.getHttpsBindHost(), settings.getHttpsPort()));
        dto.setSocksBind(bindLabel(dto.isSocksRunning(), settings.getSocksBindHost(), settings.getSocksPort()));
        dto.setSshBind(bindLabel(dto.isSshRunning(), settings.getSshBindHost(), settings.getSshPort()));
        dto.setHttpPort(settings.getHttpPort());
        dto.setHttpsPort(settings.getHttpsPort());
        dto.setSocksPort(settings.getSocksPort());
        dto.setSshPort(settings.getSshPort());
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
        page.setUsers(new ArrayList<>());
        for (ProxyUser user : userService.findAll()) {
            UserThroughput speed = trafficStatsService.throughputFor(user.getId());
            UserRowDto row = new UserRowDto();
            row.setId(user.getId());
            row.setEnabled(user.isEnabled());
            row.setExpired(user.isExpired());
            row.setRoleAdmin(user.hasAdminRole());
            row.setRoleUser(user.hasUserRole());
            row.setBootstrapAdmin(userService.isBootstrapAdmin(user.getId()));
            row.setMaxConnections(user.getMaxConnections());
            row.setTrafficLimitBytes(user.getTrafficLimitBytes());
            row.setSpeedLimitUpBps(user.getSpeedLimitUpBps());
            row.setSpeedLimitDownBps(user.getSpeedLimitDownBps());
            boolean overQuota = trafficStatsService.isOverTrafficLimit(user.getId(), user.getTrafficLimitBytes());
            row.setTrafficLimitExceeded(overQuota);
            row.setUsable(user.isUsable() && !overQuota && user.hasUserRole());
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
    public UserFormDto getUserForm(String id) {
        UserFormDto form = new UserFormDto();
        if (id == null) {
            form.setCreating(true);
            form.setEnabled(true);
            form.setRoleAdmin(false);
            form.setRoleUser(true);
            form.setBootstrapAdmin(false);
            form.setMaxConnections(0);
            form.setTrafficLimitBytes(-1);
            form.setSpeedLimitUpBps(-1);
            form.setSpeedLimitDownBps(-1);
            return form;
        }
        ProxyUser user = userService.getRequired(id);
        form.setCreating(false);
        form.setId(user.getId());
        form.setEnabled(user.isEnabled());
        form.setRoleAdmin(user.hasAdminRole());
        form.setRoleUser(user.hasUserRole());
        form.setBootstrapAdmin(userService.isBootstrapAdmin(user.getId()));
        form.setMaxConnections(user.getMaxConnections());
        form.setTrafficLimitBytes(user.getTrafficLimitBytes());
        form.setSpeedLimitUpBps(user.getSpeedLimitUpBps());
        form.setSpeedLimitDownBps(user.getSpeedLimitDownBps());
        if (user.getExpiresAt() != null) {
            form.setExpiresAt(DATE_FMT.format(user.getExpiresAt().atZone(appZoneId).toLocalDate()));
        }
        return form;
    }

    @Override
    public void createUser(UserFormDto form) throws AdminRpcException {
        try {
            userService.create(proxyUserFormMapper.toForm(form));
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
            userService.update(form.getId(), proxyUserFormMapper.toForm(form));
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to update user", ex);
        }
    }

    @Override
    public void setUserEnabled(String id, boolean enabled) {
        userService.setEnabled(id, enabled);
    }

    @Override
    public void resetUserTraffic(String id) {
        userService.resetTraffic(id);
    }

    @Override
    public void deleteUser(String id) throws AdminRpcException {
        try {
            userService.delete(id);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to delete user", ex);
        }
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
            upstreamProxyService.create(upstreamProxyFormMapper.toForm(form, true));
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
            upstreamProxyService.update(form.getId(), upstreamProxyFormMapper.toForm(form, false));
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
    public HttpStripHeadersPageDto getHttpStripHeaders() {
        HttpStripHeadersPageDto page = new HttpStripHeadersPageDto();
        page.setHeaders(new ArrayList<>());
        for (HttpStripHeader header : stripHeaderService.findAll()) {
            HttpStripHeaderRowDto row = new HttpStripHeaderRowDto();
            row.setId(header.getId());
            row.setName(header.getName());
            page.getHeaders().add(row);
        }
        return page;
    }

    @Override
    public void addHttpStripHeader(String name) throws AdminRpcException {
        try {
            stripHeaderService.add(name);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to add strip header", ex);
        }
    }

    @Override
    public void deleteHttpStripHeader(long id) throws AdminRpcException {
        try {
            stripHeaderService.delete(id);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to delete strip header", ex);
        }
    }

    @Override
    public SettingsDto getSettings() {
        return adminRpcManualMapper.toSettingsDto(settingsService.get());
    }

    @Override
    public void saveSettings(SettingsDto form) throws AdminRpcException {
        try {
            AppSettings settings = settingsService.get();
            appSettingsMapper.apply(settings, form);
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
        try {
            userService.changePassword(auth.getName(), passwordChangeFormMapper.toForm(form));
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to change password", ex);
        }
    }

    @Override
    public EncryptionDto getEncryption() {
        AppSettings settings = settingsService.get();
        EncryptionDto dto = adminRpcManualMapper.toEncryptionDto(settings);
        dto.setTlsStatus(tlsStatusMapper.toDto(tlsService.status(settings), appZoneId));
        return dto;
    }

    @Override
    public TlsStatusDto previewEncryption(EncryptionDto form) throws AdminRpcException {
        try {
            AppSettings preview = appSettingsMapper.copy(settingsService.get());
            adminRpcManualMapper.applyEncryption(preview, form);
            return tlsStatusMapper.toDto(tlsService.status(preview), appZoneId);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to preview encryption", ex);
        }
    }

    @Override
    public void saveEncryption(EncryptionDto form) throws AdminRpcException {
        try {
            AppSettings settings = settingsService.get();
            adminRpcManualMapper.applyEncryption(settings, form);
            tlsService.validateSettingsOrThrow(settings);
            settingsService.save(settings);
            proxyServerManager.restart();
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to save encryption settings", ex);
        }
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
