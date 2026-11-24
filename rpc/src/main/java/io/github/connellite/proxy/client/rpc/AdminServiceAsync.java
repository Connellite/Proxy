package io.github.connellite.proxy.client.rpc;

import com.google.gwt.user.client.rpc.AsyncCallback;
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
import io.github.connellite.proxy.client.rpc.dto.UserConnectionRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SettingsGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SftpPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SshAccessGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SshIssuedKeyGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.TlsStatusGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxiesPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyFormGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UserFormGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UsersPageGWTDTO;

import java.util.List;

public interface AdminServiceAsync {

    void getDashboardProxy(AsyncCallback<DashboardProxyGWTDTO> callback);

    void getDashboardDns(AsyncCallback<DashboardDnsGWTDTO> callback);

    void getUsers(AsyncCallback<UsersPageGWTDTO> callback);

    void getUserForm(String id, AsyncCallback<UserFormGWTDTO> callback);

    void createUser(UserFormGWTDTO form, AsyncCallback<Void> callback);

    void updateUser(UserFormGWTDTO form, AsyncCallback<Void> callback);

    void setUserEnabled(String id, boolean enabled, AsyncCallback<Void> callback);

    void resetUserTraffic(String id, AsyncCallback<Void> callback);

    void deleteUser(String id, AsyncCallback<Void> callback);

    void getSshAccess(String userId, AsyncCallback<SshAccessGWTDTO> callback);

    void setSshPasswordEnabled(String userId, boolean enabled, AsyncCallback<Void> callback);

    void issueSshKey(String userId, String comment, String passphrase, AsyncCallback<SshIssuedKeyGWTDTO> callback);

    void revokeSshKey(String userId, long keyId, AsyncCallback<Void> callback);

    void getSftpAccess(AsyncCallback<SftpPageGWTDTO> callback);

    void saveSftpAccess(String userId, String path, boolean readOnly, AsyncCallback<Void> callback);

    void removeSftpAccess(String userId, AsyncCallback<Void> callback);

    void listServerDirectories(String path, AsyncCallback<DirectoryListingGWTDTO> callback);

    void createServerDirectory(String parentPath, String name, AsyncCallback<String> callback);

    void getUpstreamProxies(AsyncCallback<UpstreamProxiesPageGWTDTO> callback);

    void getUpstreamProxyForm(Long id, AsyncCallback<UpstreamProxyFormGWTDTO> callback);

    void createUpstreamProxy(UpstreamProxyFormGWTDTO form, AsyncCallback<Void> callback);

    void updateUpstreamProxy(UpstreamProxyFormGWTDTO form, AsyncCallback<Void> callback);

    void deleteUpstreamProxy(long id, AsyncCallback<Void> callback);

    void selectUpstreamProxy(long id, AsyncCallback<Void> callback);

    void clearUpstreamProxySelection(AsyncCallback<Void> callback);

    void getHttpStripHeaders(AsyncCallback<HttpStripHeadersPageGWTDTO> callback);

    void addHttpStripHeader(String name, AsyncCallback<Void> callback);

    void deleteHttpStripHeader(long id, AsyncCallback<Void> callback);

    void getSettings(AsyncCallback<SettingsGWTDTO> callback);

    void saveSettings(SettingsGWTDTO form, AsyncCallback<Void> callback);

    void restartProxy(AsyncCallback<Void> callback);

    void changePassword(PasswordChangeGWTDTO form, AsyncCallback<Void> callback);

    void getEncryption(AsyncCallback<EncryptionGWTDTO> callback);

    void previewEncryption(EncryptionGWTDTO form, AsyncCallback<TlsStatusGWTDTO> callback);

    void saveEncryption(EncryptionGWTDTO form, AsyncCallback<Void> callback);

    void getRelayPage(AsyncCallback<RelayPageGWTDTO> callback);

    void saveRelaySettings(RelayPageGWTDTO form, AsyncCallback<Void> callback);

    void saveRelayRoute(RelayRouteRowGWTDTO row, AsyncCallback<Void> callback);

    void deleteRelayRoute(long id, AsyncCallback<Void> callback);

    void saveRelayDnsAllow(String cidr, AsyncCallback<Void> callback);

    void deleteRelayDnsAllow(long id, AsyncCallback<Void> callback);

    void saveRelayTcpAllow(String cidr, AsyncCallback<Void> callback);

    void deleteRelayTcpAllow(long id, AsyncCallback<Void> callback);

    void saveProxyAllow(String cidr, AsyncCallback<Void> callback);

    void deleteProxyAllow(long id, AsyncCallback<Void> callback);

    void getRelayQueryLog(AsyncCallback<List<RelayQueryRowGWTDTO>> callback);

    void getRelayTcpSessionLog(AsyncCallback<List<RelayTcpSessionRowGWTDTO>> callback);

    void getUserConnectionLog(AsyncCallback<List<UserConnectionRowGWTDTO>> callback);
}
