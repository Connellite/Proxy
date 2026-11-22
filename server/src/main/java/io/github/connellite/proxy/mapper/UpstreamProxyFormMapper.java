package io.github.connellite.proxy.mapper;

import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyFormGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyTypeGWT;
import io.github.connellite.proxy.dto.UpstreamProxyForm;
import io.github.connellite.proxy.model.UpstreamProxyType;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface UpstreamProxyFormMapper {

    @Mapping(target = "type", source = "type", qualifiedByName = "parseUpstreamType")
    @Mapping(target = "updatePassword", source = ".", qualifiedByName = "resolveUpdatePassword")
    UpstreamProxyForm toForm(UpstreamProxyFormGWTDTO dto, @Context boolean creating);

    @Named("parseUpstreamType")
    default UpstreamProxyType parseUpstreamType(UpstreamProxyTypeGWT type) {
        UpstreamProxyTypeGWT wire = type == null ? UpstreamProxyTypeGWT.HTTP : type;
        try {
            return UpstreamProxyType.valueOf(wire.name());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Type must be HTTP, HTTPS, SOCKS5 or SSH");
        }
    }

    @Named("resolveUpdatePassword")
    default boolean resolveUpdatePassword(UpstreamProxyFormGWTDTO dto, @Context boolean creating) {
        return creating || StringUtils.isNotBlank(dto.getPassword());
    }
}
