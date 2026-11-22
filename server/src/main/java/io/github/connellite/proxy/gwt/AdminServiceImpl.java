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
import io.github.connellite.proxy.client.rpc.dto.DirectoryListingDto;
import io.github.connellite.proxy.client.rpc.dto.EncryptionDto;
import io.github.connellite.proxy.client.rpc.dto.HttpStripHeaderRowDto;
import io.github.connellite.proxy.client.rpc.dto.HttpStripHeadersPageDto;
import io.github.connellite.proxy.client.rpc.dto.PasswordChangeDto;
import io.github.connellite.proxy.client.rpc.dto.RelayAllowRowDto;
import io.github.connellite.proxy.client.rpc.dto.RelayPageDto;
import io.github.connellite.proxy.client.rpc.dto.RelayQueryRowDto;
import io.github.connellite.proxy.client.rpc.dto.RelayRouteRowDto;
import io.github.connellite.proxy.client.rpc.dto.SettingsDto;
import io.github.connellite.proxy.client.rpc.dto.SftpPageDto;
import io.github.connellite.proxy.client.rpc.dto.SftpUserRowDto;
import io.github.connellite.proxy.client.rpc.dto.SshAccessDto;
import io.github.connellite.proxy.client.rpc.dto.SshIssuedKeyDto;
import io.github.connellite.proxy.client.rpc.dto.SshUserKeyRowDto;
import io.github.connellite.proxy.client.rpc.dto.TlsStatusDto;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxiesPageDto;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyFormDto;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyRowDto;
import io.github.connellite.proxy.client.rpc.dto.UserFormDto;
import io.github.connellite.proxy.client.rpc.dto.UserRowDto;
import io.github.connellite.proxy.client.rpc.dto.UsersPageDto;
import io.github.connellite.proxy.dto.AppSettings;
import io.github.connellite.proxy.dto.DirectoryListing;
import io.github.connellite.proxy.dto.IssuedSshKey;
import io.github.connellite.proxy.dto.UserThroughput;
import io.github.connellite.proxy.mapper.AdminRpcManualMapper;
import io.github.connellite.proxy.mapper.AppSettingsMapper;
import io.github.connellite.proxy.mapper.PasswordChangeFormMapper;
import io.github.connellite.proxy.mapper.ProxyUserFormMapper;
import io.github.connellite.proxy.mapper.TlsStatusMapper;
import io.github.connellite.proxy.mapper.UpstreamProxyFormMapper;
import io.github.connellite.proxy.model.HttpStripHeader;
import io.github.connellite.proxy.model.SftpAccess;
import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.model.RelayDnsAllow;
import io.github.connellite.proxy.model.RelayRoute;
import io.github.connellite.proxy.model.RelayRouteMode;
import io.github.connellite.proxy.model.RelayTcpAllow;
import io.github.connellite.proxy.model.SshUserKey;
import io.github.connellite.proxy.model.UpstreamProxy;
import io.github.connellite.proxy.model.UpstreamProxyType;
import io.github.connellite.proxy.proxy.ProxyServerManager;
import io.github.connellite.proxy.proxy.http.ProxyTlsService;
import io.github.connellite.proxy.service.HttpStripHeaderService;
import io.github.connellite.proxy.relay.HostPort;
import io.github.connellite.proxy.relay.RelayAllowService;
import io.github.connellite.proxy.relay.RelayRouteService;
import io.github.connellite.proxy.relay.dns.RelayQueryLog;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.github.connellite.proxy.service.ProxyUserService;
import io.github.connellite.proxy.service.ServerDirectoryService;
import io.github.connellite.proxy.service.SftpAccessService;
import io.github.connellite.proxy.service.SshUserKeyService;
import io.github.connellite.proxy.service.SettingsService;
import io.github.connellite.proxy.service.TrafficStatsService;
import io.github.connellite.proxy.service.UpstreamProxyService;
import io.github.connellite.proxy.util.LocalBindAddresses;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminServiceImpl extends RemoteServiceServlet implements AdminService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter QUERY_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProxyUserService userService;
    private final SshUserKeyService sshUserKeyService;
    private final ServerDirectoryService serverDirectoryService;
    private final SftpAccessService sftpAccessService;
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
    private final RelayRouteService relayRouteService;
    private final RelayAllowService relayAllowService;
    private final RelayQueryLog relayQueryLog;

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
    public SshAccessDto getSshAccess(String userId) throws AdminRpcException {
        try {
            ProxyUser user = userService.getRequired(userId);
            SshAccessDto dto = new SshAccessDto();
            dto.setUserId(user.getId());
            dto.setSshPasswordEnabled(user.isSshPasswordEnabled());
            for (SshUserKey key : sshUserKeyService.list(user.getId())) {
                SshUserKeyRowDto row = new SshUserKeyRowDto();
                row.setId(key.getId());
                row.setComment(key.getComment());
                row.setFingerprint(key.getFingerprint());
                row.setCreatedAt(formatInstant(key.getCreatedAt()));
                dto.getKeys().add(row);
            }
            return dto;
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to load SSH access", ex);
        }
    }

    @Override
    public void setSshPasswordEnabled(String userId, boolean enabled) throws AdminRpcException {
        try {
            userService.setSshPasswordEnabled(userId, enabled);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to update SSH password", ex);
        }
    }

    @Override
    public SshIssuedKeyDto issueSshKey(String userId, String comment, String passphrase) throws AdminRpcException {
        try {
            IssuedSshKey issued = sshUserKeyService.issue(userId, comment, passphrase);
            SshIssuedKeyDto dto = new SshIssuedKeyDto();
            dto.setId(issued.id());
            dto.setFingerprint(issued.fingerprint());
            dto.setPublicKey(issued.publicKey());
            dto.setPrivateKey(issued.privateKey());
            dto.setEncrypted(issued.encrypted());
            return dto;
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to issue SSH key", ex);
        }
    }

    @Override
    public void revokeSshKey(String userId, long keyId) throws AdminRpcException {
        try {
            sshUserKeyService.revoke(userId, keyId);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to revoke SSH key", ex);
        }
    }

    @Override
    public SftpPageDto getSftpAccess() {
        AppSettings settings = settingsService.get();
        SftpPageDto page = new SftpPageDto();
        page.setSshEnabled(settings.isSshEnabled());
        page.setSshRunning(proxyServerManager.isSshRunning());
        page.setSshPort(settings.getSshPort());
        Map<String, SftpAccess> access = new HashMap<>();
        for (SftpAccess row : sftpAccessService.findAll()) {
            access.put(row.getUserId(), row);
        }
        for (ProxyUser user : userService.findAll()) {
            SftpUserRowDto row = new SftpUserRowDto();
            row.setUserId(user.getId());
            boolean overQuota = trafficStatsService.isOverTrafficLimit(user.getId(), user.getTrafficLimitBytes());
            row.setUsable(user.isUsable() && !overQuota && user.hasUserRole());
            SftpAccess granted = access.get(user.getId());
            row.setRoot(granted == null ? null : granted.getRootPath());
            row.setReadOnly(granted != null && granted.isReadOnly());
            page.getUsers().add(row);
        }
        return page;
    }

    @Override
    public void saveSftpAccess(String userId, String path, boolean readOnly) throws AdminRpcException {
        try {
            sftpAccessService.save(userId, path, readOnly);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to save SFTP access", ex);
        }
    }

    @Override
    public void removeSftpAccess(String userId) throws AdminRpcException {
        try {
            sftpAccessService.remove(userId);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to remove SFTP access", ex);
        }
    }

    @Override
    public DirectoryListingDto listServerDirectories(String path) throws AdminRpcException {
        try {
            DirectoryListing listing = serverDirectoryService.list(path);
            DirectoryListingDto dto = new DirectoryListingDto();
            dto.setPath(listing.path());
            dto.setParent(listing.parent());
            dto.getDirectories().addAll(listing.directories());
            return dto;
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to list server folders", ex);
        }
    }

    @Override
    public String createServerDirectory(String parentPath, String name) throws AdminRpcException {
        try {
            return serverDirectoryService.create(parentPath, name);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to create folder", ex);
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

    @Override
    public RelayPageDto getRelayPage() {
        AppSettings settings = settingsService.get();
        RelayPageDto page = new RelayPageDto();
        page.setDnsEnabled(settings.isRelayDnsEnabled());
        page.setDnsBindHost(settings.getRelayDnsBindHost());
        page.setDnsPort(settings.getRelayDnsPort());
        page.setTcpEnabled(settings.isRelayTcpEnabled());
        page.setTcpBindHost(settings.getRelayTcpBindHost());
        page.setTcpPort(settings.getRelayTcpPort());
        page.setTcpUseUpstream(settings.isRelayTcpUseUpstream());
        page.setDohEnabled(settings.isRelayDohEnabled());
        page.setDohBindHost(settings.getRelayDohBindHost());
        page.setDohPort(settings.getRelayDohPort());
        page.setDoh3Enabled(settings.isRelayDoh3Enabled());
        page.setDoh3BindHost(settings.getRelayDoh3BindHost());
        page.setDoh3Port(settings.getRelayDoh3Port());
        page.setDotEnabled(settings.isRelayDotEnabled());
        page.setDotBindHost(settings.getRelayDotBindHost());
        page.setDotPort(settings.getRelayDotPort());
        page.setDoqEnabled(settings.isRelayDoqEnabled());
        page.setDoqBindHost(settings.getRelayDoqBindHost());
        page.setDoqPort(settings.getRelayDoqPort());
        page.setAdvertisedAddress(settings.getRelayAdvertisedAddress() == null ? "" : settings.getRelayAdvertisedAddress());
        page.setUpstreamResolver(settings.getRelayUpstreamResolver());
        page.setBindHostOptions(new ArrayList<>(LocalBindAddresses.optionsIncluding(
                settings.getRelayDnsBindHost(), settings.getRelayTcpBindHost(),
                settings.getRelayDohBindHost(), settings.getRelayDoh3BindHost(),
                settings.getRelayDotBindHost(), settings.getRelayDoqBindHost())));
        for (RelayDnsAllow allow : relayAllowService.dnsAllows()) {
            page.getDnsAllows().add(allowRow(allow.getId(), allow.getCidr()));
        }
        for (RelayTcpAllow allow : relayAllowService.tcpAllows()) {
            page.getTcpAllows().add(allowRow(allow.getId(), allow.getCidr()));
        }
        for (RelayRoute route : relayRouteService.findAll()) {
            page.getRoutes().add(toRow(route));
        }
        return page;
    }

    @Override
    public void saveRelaySettings(RelayPageDto form) throws AdminRpcException {
        try {
            AppSettings settings = new AppSettings();
            settings.setRelayDnsEnabled(form.isDnsEnabled());
            settings.setRelayDnsBindHost(form.getDnsBindHost());
            settings.setRelayDnsPort(form.getDnsPort());
            settings.setRelayTcpEnabled(form.isTcpEnabled());
            settings.setRelayTcpBindHost(form.getTcpBindHost());
            settings.setRelayTcpPort(form.getTcpPort());
            settings.setRelayTcpUseUpstream(form.isTcpUseUpstream());
            settings.setRelayDohEnabled(form.isDohEnabled());
            settings.setRelayDohBindHost(form.getDohBindHost());
            settings.setRelayDohPort(form.getDohPort());
            settings.setRelayDoh3Enabled(form.isDoh3Enabled());
            settings.setRelayDoh3BindHost(form.getDoh3BindHost());
            settings.setRelayDoh3Port(form.getDoh3Port());
            settings.setRelayDotEnabled(form.isDotEnabled());
            settings.setRelayDotBindHost(form.getDotBindHost());
            settings.setRelayDotPort(form.getDotPort());
            settings.setRelayDoqEnabled(form.isDoqEnabled());
            settings.setRelayDoqBindHost(form.getDoqBindHost());
            settings.setRelayDoqPort(form.getDoqPort());
            settings.setRelayAdvertisedAddress(form.getAdvertisedAddress());
            settings.setRelayUpstreamResolver(form.getUpstreamResolver());
            relayRouteService.saveSettings(settings);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to save relay settings", ex);
        }
    }

    @Override
    public void saveRelayRoute(RelayRouteRowDto row) throws AdminRpcException {
        try {
            RelayRoute route = new RelayRoute();
            route.setId(row.getId());
            route.setName(row.getName());
            route.setMode(RelayRouteMode.valueOf(row.getMode()));
            route.setUpstreamHost(row.getUpstreamHost());
            route.setUpstreamPort(portOrNull(row.getUpstreamPort()));
            route.setAnswerAddress(row.getAnswerAddress());
            route.setEnabled(row.isEnabled());
            relayRouteService.saveRoute(route);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to save relay route", ex);
        }
    }

    @Override
    public void saveRelayDnsAllow(String cidr) throws AdminRpcException {
        try {
            relayAllowService.saveDns(cidr);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to save DNS allowlist entry", ex);
        }
    }

    @Override
    public void deleteRelayDnsAllow(long id) throws AdminRpcException {
        try {
            relayAllowService.deleteDns(id);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to delete DNS allowlist entry", ex);
        }
    }

    @Override
    public void saveRelayTcpAllow(String cidr) throws AdminRpcException {
        try {
            relayAllowService.saveTcp(cidr);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to save TCP allowlist entry", ex);
        }
    }

    @Override
    public void deleteRelayTcpAllow(long id) throws AdminRpcException {
        try {
            relayAllowService.deleteTcp(id);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to delete TCP allowlist entry", ex);
        }
    }

    private static RelayAllowRowDto allowRow(Long id, String cidr) {
        RelayAllowRowDto row = new RelayAllowRowDto();
        row.setId(id);
        row.setCidr(cidr);
        return row;
    }

    @Override
    public void deleteRelayRoute(long id) throws AdminRpcException {
        try {
            relayRouteService.delete(id);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to delete relay route", ex);
        }
    }

    @Override
    public List<RelayQueryRowDto> getRelayQueryLog() {
        List<RelayQueryRowDto> rows = new ArrayList<>();
        for (RelayQueryLog.Entry entry : relayQueryLog.recent()) {
            RelayQueryRowDto row = new RelayQueryRowDto();
            row.setTime(QUERY_TIME_FMT.format(entry.at().atZone(appZoneId)));
            row.setClient(entry.client());
            row.setName(entry.name());
            row.setType(entry.type());
            row.setOutcome(entry.outcome());
            row.setAnswer(entry.answer());
            row.setElapsed(entry.millis() + " ms");
            rows.add(row);
        }
        return rows;
    }

    private static RelayRouteRowDto toRow(RelayRoute route) {
        RelayRouteRowDto row = new RelayRouteRowDto();
        row.setId(route.getId());
        row.setName(route.getName());
        row.setMode(route.getMode() == null ? RelayRouteMode.RELAY.name() : route.getMode().name());
        row.setUpstreamHost(route.getUpstreamHost() == null ? "" : route.getUpstreamHost());
        row.setUpstreamPort(route.getUpstreamPort() == null ? "" : Integer.toString(route.getUpstreamPort()));
        row.setAnswerAddress(route.getAnswerAddress() == null ? "" : route.getAnswerAddress());
        row.setEnabled(route.isEnabled());
        return row;
    }

    private static Integer portOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return HostPort.parsePort(raw);
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
