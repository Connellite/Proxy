package io.github.connellite.proxy.client.rpc;

import com.google.gwt.user.client.rpc.AsyncCallback;
import io.github.connellite.proxy.client.rpc.dto.DashboardDto;
import io.github.connellite.proxy.client.rpc.dto.EncryptionDto;
import io.github.connellite.proxy.client.rpc.dto.PasswordChangeDto;
import io.github.connellite.proxy.client.rpc.dto.SettingsDto;
import io.github.connellite.proxy.client.rpc.dto.TlsStatusDto;
import io.github.connellite.proxy.client.rpc.dto.UserFormDto;
import io.github.connellite.proxy.client.rpc.dto.UsersPageDto;

public interface AdminServiceAsync {

    void getDashboard(AsyncCallback<DashboardDto> callback);

    void getUsers(AsyncCallback<UsersPageDto> callback);

    void getUserForm(Long id, AsyncCallback<UserFormDto> callback);

    void createUser(UserFormDto form, AsyncCallback<Void> callback);

    void updateUser(UserFormDto form, AsyncCallback<Void> callback);

    void setUserEnabled(long id, boolean enabled, AsyncCallback<Void> callback);

    void resetUserTraffic(long id, AsyncCallback<Void> callback);

    void deleteUser(long id, AsyncCallback<Void> callback);

    void getSettings(AsyncCallback<SettingsDto> callback);

    void saveSettings(SettingsDto form, AsyncCallback<Void> callback);

    void restartProxy(AsyncCallback<Void> callback);

    void changePassword(PasswordChangeDto form, AsyncCallback<Void> callback);

    void getEncryption(AsyncCallback<EncryptionDto> callback);

    void previewEncryption(EncryptionDto form, AsyncCallback<TlsStatusDto> callback);

    void saveEncryption(EncryptionDto form, AsyncCallback<Void> callback);
}
