package io.github.connellite.proxy.mapper;

import io.github.connellite.proxy.client.rpc.dto.TlsStatusDto;
import io.github.connellite.proxy.dto.TlsStatus;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

@Mapper(componentModel = "spring")
public interface TlsStatusMapper {

    DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Mapping(target = "notBefore", ignore = true)
    @Mapping(target = "notAfter", ignore = true)
    @Mapping(target = "dnsNames", ignore = true)
    TlsStatusDto toDto(TlsStatus status, @Context ZoneId zoneId);

    @AfterMapping
    default void finish(TlsStatus status, @MappingTarget TlsStatusDto dto, @Context ZoneId zoneId) {
        dto.setNotBefore(formatInstant(status.getNotBefore(), zoneId));
        dto.setNotAfter(formatInstant(status.getNotAfter(), zoneId));
        dto.setDnsNames(status.getDnsNames() == null ? new ArrayList<>() : new ArrayList<>(status.getDnsNames()));
    }

    default String formatInstant(Instant instant, ZoneId zoneId) {
        if (instant == null) {
            return null;
        }
        return DATE_TIME_FMT.format(instant.atZone(zoneId));
    }
}
