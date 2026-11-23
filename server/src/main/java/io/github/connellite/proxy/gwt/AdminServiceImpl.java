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
import io.github.connellite.proxy.client.rpc.dto.DashboardDnsGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.DashboardProxyGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.DirectoryListingGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.EncryptionGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.HttpStripHeaderRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.HttpStripHeadersPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.PasswordChangeGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayAllowRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayQueryRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayRouteModeGWT;
import io.github.connellite.proxy.client.rpc.dto.RelayRouteRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayTcpSessionRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SettingsGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SftpPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SftpUserRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SshAccessGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SshIssuedKeyGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SshUserKeyRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.TlsStatusGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxiesPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyFormGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyTypeGWT;
import io.github.connellite.proxy.client.rpc.dto.UserFormGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UserRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UsersPageGWTDTO;
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
import io.github.connellite.proxy.dns.relay.HostPort;
import io.github.connellite.proxy.dns.relay.RelayAllowService;
import io.github.connellite.proxy.dns.relay.RelayRouteService;
import io.github.connellite.proxy.dns.server.DnsServer;
import io.github.connellite.proxy.dns.server.Doh3Server;
import io.github.connellite.proxy.dns.server.DohServer;
import io.github.connellite.proxy.dns.server.DoqServer;
import io.github.connellite.proxy.dns.server.DotServer;
import io.github.connellite.proxy.dns.server.QueryLog;
import io.github.connellite.proxy.dns.tcp.TcpServer;
import io.github.connellite.proxy.dns.tcp.TcpSessionLog;
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
import java.time.Instant;
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
    private final QueryLog relayQueryLog;
    private final TcpSessionLog relayTcpSessionLog;
    private final DnsServer relayDnsServer;
    private final TcpServer relayTcpServer;
    private final DohServer relayDohServer;
    private final Doh3Server relayDoh3Server;
    private final DotServer relayDotServer;
    private final DoqServer relayDoqServer;

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
    public DashboardProxyGWTDTO getDashboardProxy() {
        AppSettings settings = settingsService.get();
        List<ProxyUser> users = userService.findAll();
        DashboardProxyGWTDTO dto = new DashboardProxyGWTDTO();
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
    public DashboardDnsGWTDTO getDashboardDns() {
        AppSettings settings = settingsService.get();
        List<RelayRoute> routes = relayRouteService.findAll();
        DashboardDnsGWTDTO dto = new DashboardDnsGWTDTO();
        dto.setDnsRunning(relayDnsServer.isRunning());
        dto.setTcpRunning(relayTcpServer.isRunning());
        dto.setDohRunning(relayDohServer.isRunning());
        dto.setDoh3Running(relayDoh3Server.isRunning());
        dto.setDotRunning(relayDotServer.isRunning());
        dto.setDoqRunning(relayDoqServer.isRunning());
        dto.setDnsBind(bindLabel(dto.isDnsRunning(), settings.getRelayDnsBindHost(), settings.getRelayDnsPort()));
        dto.setTcpBind(bindLabel(dto.isTcpRunning(), settings.getRelayTcpBindHost(), settings.getRelayTcpPort()));
        dto.setDohBind(bindLabel(dto.isDohRunning(), settings.getRelayDohBindHost(), settings.getRelayDohPort()));
        dto.setDoh3Bind(bindLabel(dto.isDoh3Running(), settings.getRelayDoh3BindHost(), settings.getRelayDoh3Port()));
        dto.setDotBind(bindLabel(dto.isDotRunning(), settings.getRelayDotBindHost(), settings.getRelayDotPort()));
        dto.setDoqBind(bindLabel(dto.isDoqRunning(), settings.getRelayDoqBindHost(), settings.getRelayDoqPort()));
        dto.setDnsPort(settings.getRelayDnsPort());
        dto.setTcpPort(settings.getRelayTcpPort());
        dto.setDohPort(settings.getRelayDohPort());
        dto.setDoh3Port(settings.getRelayDoh3Port());
        dto.setDotPort(settings.getRelayDotPort());
        dto.setDoqPort(settings.getRelayDoqPort());
        dto.setRouteCount(routes.size());
        dto.setEnabledRouteCount((int) routes.stream().filter(RelayRoute::isEnabled).count());
        dto.setAdvertisedAddress(settings.getRelayAdvertisedAddress() == null ? "" : settings.getRelayAdvertisedAddress());
        dto.setUpstreamResolver(settings.getRelayUpstreamResolver() == null ? "" : settings.getRelayUpstreamResolver());
        dto.setTcpUseUpstream(settings.isRelayTcpUseUpstream());
        String publicHost = dnsPublicHost(settings);
        dto.setDnsEndpoint(dnsPlainEndpoint(publicHost, settings.getRelayDnsPort()));
        dto.setDohEndpoint(dnsUrlEndpoint("https", publicHost, settings.getRelayDohPort(), 443, "/dns-query"));
        dto.setDoh3Endpoint(dnsUrlEndpoint("h3", publicHost, settings.getRelayDoh3Port(), 443, "/dns-query"));
        dto.setDotEndpoint(dnsUrlEndpoint("tls", publicHost, settings.getRelayDotPort(), 853, null));
        dto.setDoqEndpoint(dnsUrlEndpoint("quic", publicHost, settings.getRelayDoqPort(), 853, null));
        return dto;
    }

    @Override
    public UsersPageGWTDTO getUsers() {
        UsersPageGWTDTO page = new UsersPageGWTDTO();
        page.setUsers(new ArrayList<>());
        for (ProxyUser user : userService.findAll()) {
            UserThroughput speed = trafficStatsService.throughputFor(user.getId());
            UserRowGWTDTO row = new UserRowGWTDTO();
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
    public UserFormGWTDTO getUserForm(String id) {
        UserFormGWTDTO form = new UserFormGWTDTO();
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
    public void createUser(UserFormGWTDTO form) throws AdminRpcException {
        try {
            userService.create(proxyUserFormMapper.toForm(form));
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to create user", ex);
        }
    }

    @Override
    public void updateUser(UserFormGWTDTO form) throws AdminRpcException {
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
    public SshAccessGWTDTO getSshAccess(String userId) throws AdminRpcException {
        try {
            ProxyUser user = userService.getRequired(userId);
            SshAccessGWTDTO dto = new SshAccessGWTDTO();
            dto.setUserId(user.getId());
            dto.setSshPasswordEnabled(user.isSshPasswordEnabled());
            for (SshUserKey key : sshUserKeyService.list(user.getId())) {
                SshUserKeyRowGWTDTO row = new SshUserKeyRowGWTDTO();
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
    public SshIssuedKeyGWTDTO issueSshKey(String userId, String comment, String passphrase) throws AdminRpcException {
        try {
            IssuedSshKey issued = sshUserKeyService.issue(userId, comment, passphrase);
            SshIssuedKeyGWTDTO dto = new SshIssuedKeyGWTDTO();
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
    public SftpPageGWTDTO getSftpAccess() {
        AppSettings settings = settingsService.get();
        SftpPageGWTDTO page = new SftpPageGWTDTO();
        page.setSshEnabled(settings.isSshEnabled());
        page.setSshRunning(proxyServerManager.isSshRunning());
        page.setSshPort(settings.getSshPort());
        Map<String, SftpAccess> access = new HashMap<>();
        for (SftpAccess row : sftpAccessService.findAll()) {
            access.put(row.getUserId(), row);
        }
        for (ProxyUser user : userService.findAll()) {
            SftpUserRowGWTDTO row = new SftpUserRowGWTDTO();
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
    public DirectoryListingGWTDTO listServerDirectories(String path) throws AdminRpcException {
        try {
            DirectoryListing listing = serverDirectoryService.list(path);
            DirectoryListingGWTDTO dto = new DirectoryListingGWTDTO();
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
    public UpstreamProxiesPageGWTDTO getUpstreamProxies() {
        UpstreamProxiesPageGWTDTO page = new UpstreamProxiesPageGWTDTO();
        page.setProxies(new ArrayList<>());
        for (UpstreamProxy proxy : upstreamProxyService.findAll()) {
            UpstreamProxyRowGWTDTO row = new UpstreamProxyRowGWTDTO();
            row.setId(proxy.getId());
            row.setName(proxy.getName());
            row.setType(toGwtType(proxy.getType()));
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
    public UpstreamProxyFormGWTDTO getUpstreamProxyForm(Long id) {
        UpstreamProxyFormGWTDTO form = new UpstreamProxyFormGWTDTO();
        if (id == null) {
            form.setCreating(true);
            form.setType(UpstreamProxyTypeGWT.HTTP);
            form.setPort(UpstreamProxyTypeGWT.HTTP.defaultPort());
            return form;
        }
        UpstreamProxy proxy = upstreamProxyService.getRequired(id);
        form.setCreating(false);
        form.setId(proxy.getId());
        form.setName(proxy.getName());
        form.setType(toGwtType(proxy.getType()));
        form.setHost(proxy.getHost());
        form.setPort(proxy.getPort());
        form.setUsername(proxy.getUsername());
        form.setPasswordSaved(StringUtils.isNotBlank(proxy.getPassword()));
        return form;
    }

    @Override
    public void createUpstreamProxy(UpstreamProxyFormGWTDTO form) throws AdminRpcException {
        try {
            upstreamProxyService.create(upstreamProxyFormMapper.toForm(form, true));
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to create upstream proxy", ex);
        }
    }

    @Override
    public void updateUpstreamProxy(UpstreamProxyFormGWTDTO form) throws AdminRpcException {
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
    public HttpStripHeadersPageGWTDTO getHttpStripHeaders() {
        HttpStripHeadersPageGWTDTO page = new HttpStripHeadersPageGWTDTO();
        page.setHeaders(new ArrayList<>());
        for (HttpStripHeader header : stripHeaderService.findAll()) {
            HttpStripHeaderRowGWTDTO row = new HttpStripHeaderRowGWTDTO();
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
    public SettingsGWTDTO getSettings() {
        return adminRpcManualMapper.toSettingsGWTDTO(settingsService.get());
    }

    @Override
    public void saveSettings(SettingsGWTDTO form) throws AdminRpcException {
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
    public void changePassword(PasswordChangeGWTDTO form) throws AdminRpcException {
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
    public EncryptionGWTDTO getEncryption() {
        AppSettings settings = settingsService.get();
        EncryptionGWTDTO dto = adminRpcManualMapper.toEncryptionGWTDTO(settings);
        dto.setTlsStatus(tlsStatusMapper.toDto(tlsService.status(settings), appZoneId));
        return dto;
    }

    @Override
    public TlsStatusGWTDTO previewEncryption(EncryptionGWTDTO form) throws AdminRpcException {
        try {
            AppSettings preview = appSettingsMapper.copy(settingsService.get());
            adminRpcManualMapper.applyEncryption(preview, form);
            return tlsStatusMapper.toDto(tlsService.status(preview), appZoneId);
        } catch (RuntimeException ex) {
            throw toRpcException("Failed to preview encryption", ex);
        }
    }

    @Override
    public void saveEncryption(EncryptionGWTDTO form) throws AdminRpcException {
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
    public RelayPageGWTDTO getRelayPage() {
        AppSettings settings = settingsService.get();
        RelayPageGWTDTO page = new RelayPageGWTDTO();
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
    public void saveRelaySettings(RelayPageGWTDTO form) throws AdminRpcException {
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
    public void saveRelayRoute(RelayRouteRowGWTDTO row) throws AdminRpcException {
        try {
            RelayRoute route = new RelayRoute();
            route.setId(row.getId());
            route.setName(row.getName());
            RelayRouteModeGWT mode = row.getMode() == null ? RelayRouteModeGWT.RELAY : row.getMode();
            route.setMode(RelayRouteMode.valueOf(mode.name()));
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

    private static RelayAllowRowGWTDTO allowRow(Long id, String cidr) {
        RelayAllowRowGWTDTO row = new RelayAllowRowGWTDTO();
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
    public List<RelayQueryRowGWTDTO> getRelayQueryLog() {
        List<RelayQueryRowGWTDTO> rows = new ArrayList<>();
        for (QueryLog.Entry entry : relayQueryLog.recent()) {
            RelayQueryRowGWTDTO row = new RelayQueryRowGWTDTO();
            row.setTime(QUERY_TIME_FMT.format(entry.at().atZone(appZoneId)));
            row.setClient(entry.client());
            row.setProtocol(entry.protocol());
            row.setName(entry.name());
            row.setType(entry.type());
            row.setOutcome(entry.outcome());
            row.setAnswer(entry.answer());
            row.setElapsed(entry.millis() + " ms");
            rows.add(row);
        }
        return rows;
    }

    @Override
    public List<RelayTcpSessionRowGWTDTO> getRelayTcpSessionLog() {
        List<RelayTcpSessionRowGWTDTO> rows = new ArrayList<>();
        for (TcpSessionLog.Entry entry : relayTcpSessionLog.recent()) {
            RelayTcpSessionRowGWTDTO row = new RelayTcpSessionRowGWTDTO();
            row.setTime(QUERY_TIME_FMT.format(entry.at().atZone(appZoneId)));
            row.setClient(entry.client());
            row.setHostname(entry.hostname());
            row.setOutcome(entry.outcome());
            row.setElapsed(entry.millis() + " ms");
            rows.add(row);
        }
        return rows;
    }

    private static RelayRouteRowGWTDTO toRow(RelayRoute route) {
        RelayRouteRowGWTDTO row = new RelayRouteRowGWTDTO();
        row.setId(route.getId());
        row.setName(route.getName());
        row.setMode(toGwtMode(route.getMode()));
        row.setUpstreamHost(route.getUpstreamHost() == null ? "" : route.getUpstreamHost());
        row.setUpstreamPort(route.getUpstreamPort() == null ? "" : Integer.toString(route.getUpstreamPort()));
        row.setAnswerAddress(route.getAnswerAddress() == null ? "" : route.getAnswerAddress());
        row.setEnabled(route.isEnabled());
        return row;
    }

    private static RelayRouteModeGWT toGwtMode(RelayRouteMode mode) {
        if (mode == null) {
            return RelayRouteModeGWT.RELAY;
        }
        return RelayRouteModeGWT.valueOf(mode.name());
    }

    private static UpstreamProxyTypeGWT toGwtType(UpstreamProxyType type) {
        if (type == null) {
            return UpstreamProxyTypeGWT.HTTP;
        }
        return UpstreamProxyTypeGWT.valueOf(type.name());
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

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return null;
        }
        return DATE_TIME_FMT.format(instant.atZone(appZoneId));
    }

    private static String bindLabel(boolean running, String host, int port) {
        return running ? host + ":" + port : "stopped";
    }

    private static String dnsPublicHost(AppSettings settings) {
        String serverName = settings.getHttpsServerName();
        if (serverName != null && !serverName.isBlank()) {
            return serverName.trim();
        }
        String advertised = settings.getRelayAdvertisedAddress();
        if (advertised != null && !advertised.isBlank()) {
            return advertised.trim();
        }
        return "";
    }

    private static String dnsPlainEndpoint(String host, int port) {
        if (host == null || host.isEmpty()) {
            return "";
        }
        return dnsAuthority(host, port, 53);
    }

    private static String dnsUrlEndpoint(String scheme, String host, int port, int defaultPort, String path) {
        if (host == null || host.isEmpty()) {
            return "";
        }
        String authority = dnsAuthority(host, port, defaultPort);
        if (path == null || path.isEmpty()) {
            return scheme + "://" + authority;
        }
        return scheme + "://" + authority + path;
    }

    private static String dnsAuthority(String host, int port, int defaultPort) {
        String bracketed = host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
        if (port == defaultPort) {
            return bracketed;
        }
        return bracketed + ":" + port;
    }
}
