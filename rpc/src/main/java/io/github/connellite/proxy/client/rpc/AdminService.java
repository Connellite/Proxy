package io.github.connellite.proxy.client.rpc;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;
import io.github.connellite.proxy.client.rpc.dto.DashboardDnsGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.DashboardProxyGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.DirectoryListingGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.EncryptionGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.HttpStripHeadersPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.PasswordChangeGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayQueryRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayRouteRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayTcpSessionRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SettingsGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SftpPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SshAccessGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SshIssuedKeyGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.TlsStatusGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxiesPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyFormGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UserConnectionRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UserFormGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UsersPageGWTDTO;

import java.util.List;

@RemoteServiceRelativePath("rpc/admin")
public interface AdminService extends RemoteService {

    DashboardProxyGWTDTO getDashboardProxy();

    DashboardDnsGWTDTO getDashboardDns();

    UsersPageGWTDTO getUsers();

    UserFormGWTDTO getUserForm(String id);

    void createUser(UserFormGWTDTO form) throws AdminRpcException;

    void updateUser(UserFormGWTDTO form) throws AdminRpcException;

    void setUserEnabled(String id, boolean enabled);

    void resetUserTraffic(String id);

    void deleteUser(String id) throws AdminRpcException;

    SshAccessGWTDTO getSshAccess(String userId) throws AdminRpcException;

    void setSshPasswordEnabled(String userId, boolean enabled) throws AdminRpcException;

    SshIssuedKeyGWTDTO issueSshKey(String userId, String comment, String passphrase) throws AdminRpcException;

    void revokeSshKey(String userId, long keyId) throws AdminRpcException;

    SftpPageGWTDTO getSftpAccess();

    /** Grants or changes SFTP access. A blank {@code path} turns SFTP off for the user. */
    void saveSftpAccess(String userId, String path, boolean readOnly) throws AdminRpcException;

    void removeSftpAccess(String userId) throws AdminRpcException;

    /** {@code null} path lists file system roots. */
    DirectoryListingGWTDTO listServerDirectories(String path) throws AdminRpcException;

    /** Creates a subfolder {@code name} in {@code parentPath}; returns its absolute path. */
    String createServerDirectory(String parentPath, String name) throws AdminRpcException;

    UpstreamProxiesPageGWTDTO getUpstreamProxies();

    UpstreamProxyFormGWTDTO getUpstreamProxyForm(Long id);

    void createUpstreamProxy(UpstreamProxyFormGWTDTO form) throws AdminRpcException;

    void updateUpstreamProxy(UpstreamProxyFormGWTDTO form) throws AdminRpcException;

    void deleteUpstreamProxy(long id);

    void selectUpstreamProxy(long id) throws AdminRpcException;

    void clearUpstreamProxySelection();

    HttpStripHeadersPageGWTDTO getHttpStripHeaders();

    void addHttpStripHeader(String name) throws AdminRpcException;

    void deleteHttpStripHeader(long id) throws AdminRpcException;

    SettingsGWTDTO getSettings();

    void saveSettings(SettingsGWTDTO form) throws AdminRpcException;

    void restartProxy();

    void changePassword(PasswordChangeGWTDTO form) throws AdminRpcException;

    EncryptionGWTDTO getEncryption();

    TlsStatusGWTDTO previewEncryption(EncryptionGWTDTO form) throws AdminRpcException;

    void saveEncryption(EncryptionGWTDTO form) throws AdminRpcException;

    RelayPageGWTDTO getRelayPage();

    void saveRelaySettings(RelayPageGWTDTO form) throws AdminRpcException;

    void saveRelayRoute(RelayRouteRowGWTDTO row) throws AdminRpcException;

    void deleteRelayRoute(long id) throws AdminRpcException;

    void saveRelayDnsAllow(String cidr) throws AdminRpcException;

    void deleteRelayDnsAllow(long id) throws AdminRpcException;

    void saveRelayTcpAllow(String cidr) throws AdminRpcException;

    void deleteRelayTcpAllow(long id) throws AdminRpcException;

    List<RelayQueryRowGWTDTO> getRelayQueryLog();

    List<RelayTcpSessionRowGWTDTO> getRelayTcpSessionLog();

    List<UserConnectionRowGWTDTO> getUserConnectionLog();
}
