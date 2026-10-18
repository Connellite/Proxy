package io.github.connellite.proxy.mapper;

import io.github.connellite.proxy.client.rpc.dto.UserFormDto;
import io.github.connellite.proxy.dto.ProxyUserForm;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProxyUserFormMapper {

    ProxyUserForm toForm(UserFormDto dto);
}
