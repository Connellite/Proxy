package io.github.connellite.proxy.client.rpc;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;
import io.github.connellite.proxy.client.rpc.dto.DashboardDto;
import io.github.connellite.proxy.client.rpc.dto.EncryptionDto;
import io.github.connellite.proxy.client.rpc.dto.PasswordChangeDto;
import io.github.connellite.proxy.client.rpc.dto.SettingsDto;
import io.github.connellite.proxy.client.rpc.dto.TlsStatusDto;
import io.github.connellite.proxy.client.rpc.dto.UserFormDto;
import io.github.connellite.proxy.client.rpc.dto.UsersPageDto;

@RemoteServiceRelativePath("rpc/admin")
public interface AdminService extends RemoteService {

    DashboardDto getDashboard();

    UsersPageDto getUsers();

    UserFormDto getUserForm(Long id);

    void createUser(UserFormDto form) throws AdminRpcException;

    void updateUser(UserFormDto form) throws AdminRpcException;

    void setUserEnabled(long id, boolean enabled);

    void resetUserTraffic(long id);

    void deleteUser(long id);

    SettingsDto getSettings();

    void saveSettings(SettingsDto form) throws AdminRpcException;

    void restartProxy();

    void changePassword(PasswordChangeDto form) throws AdminRpcException;

    EncryptionDto getEncryption();

    TlsStatusDto previewEncryption(EncryptionDto form) throws AdminRpcException;

    void saveEncryption(EncryptionDto form) throws AdminRpcException;
}
