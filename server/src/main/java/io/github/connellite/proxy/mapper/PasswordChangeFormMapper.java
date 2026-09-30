package io.github.connellite.proxy.mapper;

import io.github.connellite.proxy.client.rpc.dto.PasswordChangeDto;
import io.github.connellite.proxy.dto.PasswordChangeForm;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PasswordChangeFormMapper {

    PasswordChangeForm toForm(PasswordChangeDto dto);
}
