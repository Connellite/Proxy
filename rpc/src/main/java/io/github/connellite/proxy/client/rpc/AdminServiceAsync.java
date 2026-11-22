package io.github.connellite.proxy.client.rpc;

import com.google.gwt.user.client.rpc.AsyncCallback;
import io.github.connellite.proxy.client.rpc.dto.DashboardDto;
import io.github.connellite.proxy.client.rpc.dto.DirectoryListingDto;
import io.github.connellite.proxy.client.rpc.dto.EncryptionDto;
import io.github.connellite.proxy.client.rpc.dto.HttpStripHeadersPageDto;
import io.github.connellite.proxy.client.rpc.dto.PasswordChangeDto;
import io.github.connellite.proxy.client.rpc.dto.RelayPageDto;
import io.github.connellite.proxy.client.rpc.dto.RelayQueryRowDto;
import io.github.connellite.proxy.client.rpc.dto.RelayRouteRowDto;
import io.github.connellite.proxy.client.rpc.dto.SettingsDto;
import io.github.connellite.proxy.client.rpc.dto.SftpPageDto;
import io.github.connellite.proxy.client.rpc.dto.SshAccessDto;
import io.github.connellite.proxy.client.rpc.dto.SshIssuedKeyDto;
import io.github.connellite.proxy.client.rpc.dto.TlsStatusDto;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxiesPageDto;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyFormDto;
import io.github.connellite.proxy.client.rpc.dto.UserFormDto;
import io.github.connellite.proxy.client.rpc.dto.UsersPageDto;

import java.util.List;

public interface AdminServiceAsync {

    void getDashboard(AsyncCallback<DashboardDto> callback);

    void getUsers(AsyncCallback<UsersPageDto> callback);

    void getUserForm(String id, AsyncCallback<UserFormDto> callback);

    void createUser(UserFormDto form, AsyncCallback<Void> callback);

    void updateUser(UserFormDto form, AsyncCallback<Void> callback);

    void setUserEnabled(String id, boolean enabled, AsyncCallback<Void> callback);

    void resetUserTraffic(String id, AsyncCallback<Void> callback);

    void deleteUser(String id, AsyncCallback<Void> callback);

    void getSshAccess(String userId, AsyncCallback<SshAccessDto> callback);

    void setSshPasswordEnabled(String userId, boolean enabled, AsyncCallback<Void> callback);

    void issueSshKey(String userId, String comment, String passphrase, AsyncCallback<SshIssuedKeyDto> callback);

    void revokeSshKey(String userId, long keyId, AsyncCallback<Void> callback);

    void getSftpAccess(AsyncCallback<SftpPageDto> callback);

    void saveSftpAccess(String userId, String path, boolean readOnly, AsyncCallback<Void> callback);

    void removeSftpAccess(String userId, AsyncCallback<Void> callback);

    void listServerDirectories(String path, AsyncCallback<DirectoryListingDto> callback);

    void createServerDirectory(String parentPath, String name, AsyncCallback<String> callback);

    void getUpstreamProxies(AsyncCallback<UpstreamProxiesPageDto> callback);

    void getUpstreamProxyForm(Long id, AsyncCallback<UpstreamProxyFormDto> callback);

    void createUpstreamProxy(UpstreamProxyFormDto form, AsyncCallback<Void> callback);

    void updateUpstreamProxy(UpstreamProxyFormDto form, AsyncCallback<Void> callback);

    void deleteUpstreamProxy(long id, AsyncCallback<Void> callback);

    void selectUpstreamProxy(long id, AsyncCallback<Void> callback);

    void clearUpstreamProxySelection(AsyncCallback<Void> callback);

    void getHttpStripHeaders(AsyncCallback<HttpStripHeadersPageDto> callback);

    void addHttpStripHeader(String name, AsyncCallback<Void> callback);

    void deleteHttpStripHeader(long id, AsyncCallback<Void> callback);

    void getSettings(AsyncCallback<SettingsDto> callback);

    void saveSettings(SettingsDto form, AsyncCallback<Void> callback);

    void restartProxy(AsyncCallback<Void> callback);

    void changePassword(PasswordChangeDto form, AsyncCallback<Void> callback);

    void getEncryption(AsyncCallback<EncryptionDto> callback);

    void previewEncryption(EncryptionDto form, AsyncCallback<TlsStatusDto> callback);

    void saveEncryption(EncryptionDto form, AsyncCallback<Void> callback);

    void getRelayPage(AsyncCallback<RelayPageDto> callback);

    void saveRelaySettings(RelayPageDto form, AsyncCallback<Void> callback);

    void saveRelayRoute(RelayRouteRowDto row, AsyncCallback<Void> callback);

    void deleteRelayRoute(long id, AsyncCallback<Void> callback);

    void getRelayQueryLog(AsyncCallback<List<RelayQueryRowDto>> callback);
}
