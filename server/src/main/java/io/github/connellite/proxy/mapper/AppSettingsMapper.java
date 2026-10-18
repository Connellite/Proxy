package io.github.connellite.proxy.mapper;

import io.github.connellite.proxy.client.rpc.dto.SettingsDto;
import io.github.connellite.proxy.dto.AppSettings;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface AppSettingsMapper {

    AppSettings copy(AppSettings source);

    @Mapping(target = "httpBindHost", source = "httpBindHost", qualifiedByName = "trimToEmpty")
    @Mapping(target = "socksBindHost", source = "socksBindHost", qualifiedByName = "trimToEmpty")
    @Mapping(target = "sshBindHost", source = "sshBindHost", qualifiedByName = "trimToEmpty")
    void apply(@MappingTarget AppSettings settings, SettingsDto form);

    @Named("trimToEmpty")
    default String trimToEmpty(String value) {
        return StringUtils.trimToEmpty(value);
    }
}
