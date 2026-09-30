package io.github.connellite.proxy.mapper;

import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyFormDto;
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
    UpstreamProxyForm toForm(UpstreamProxyFormDto dto, @Context boolean creating);

    @Named("parseUpstreamType")
    default UpstreamProxyType parseUpstreamType(String type) {
        try {
            return UpstreamProxyType.valueOf(StringUtils.isBlank(type) ? "HTTP" : type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Type must be HTTP, SOCKS5 or SSH");
        }
    }

    @Named("resolveUpdatePassword")
    default boolean resolveUpdatePassword(UpstreamProxyFormDto dto, @Context boolean creating) {
        return creating || StringUtils.isNotBlank(dto.getPassword());
    }
}
