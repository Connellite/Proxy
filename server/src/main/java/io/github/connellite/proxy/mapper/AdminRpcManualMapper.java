package io.github.connellite.proxy.mapper;

import io.github.connellite.proxy.client.rpc.dto.EncryptionDto;
import io.github.connellite.proxy.client.rpc.dto.SettingsDto;
import io.github.connellite.proxy.dto.AppSettings;
import io.github.connellite.proxy.dto.EncryptionForm;
import io.github.connellite.proxy.proxy.ProxyServerManager;
import io.github.connellite.proxy.util.LocalBindAddresses;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * Non-MapStruct mappings that depend on runtime state or have non-trivial field rules.
 */
@Component
@RequiredArgsConstructor
public class AdminRpcManualMapper {

    private final ProxyServerManager proxyServerManager;

    public SettingsDto toSettingsDto(AppSettings settings) {
        SettingsDto dto = new SettingsDto();
        dto.setHttpEnabled(settings.isHttpEnabled());
        dto.setHttpBindHost(settings.getHttpBindHost());
        dto.setHttpPort(settings.getHttpPort());
        dto.setSocksEnabled(settings.isSocksEnabled());
        dto.setSocksBindHost(settings.getSocksBindHost());
        dto.setSocksPort(settings.getSocksPort());
        dto.setSshEnabled(settings.isSshEnabled());
        dto.setSshBindHost(settings.getSshBindHost());
        dto.setSshPort(settings.getSshPort());
        dto.setHttpAuthRequired(settings.isHttpAuthRequired());
        dto.setSocksAuthRequired(settings.isSocksAuthRequired());
        dto.setSocksUdpEnabled(settings.isSocksUdpEnabled());
        dto.setOutboundTtl(settings.getOutboundTtl());
        dto.setHttpRunning(proxyServerManager.isHttpRunning());
        dto.setHttpsRunning(proxyServerManager.isHttpsRunning());
        dto.setSocksRunning(proxyServerManager.isSocksRunning());
        dto.setSshRunning(proxyServerManager.isSshRunning());
        dto.setLastError(proxyServerManager.getLastError());
        dto.setBindHostOptions(new ArrayList<>(LocalBindAddresses.optionsIncluding(
                settings.getHttpBindHost(), settings.getSocksBindHost(), settings.getSshBindHost())));
        return dto;
    }

    public EncryptionDto toEncryptionDto(AppSettings settings) {
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

    public void applyEncryption(AppSettings settings, EncryptionDto form) {
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
}
