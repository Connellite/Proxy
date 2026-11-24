package io.github.connellite.proxy.repository;

import io.github.connellite.proxy.model.ProxyAllow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProxyAllowRepository extends JpaRepository<ProxyAllow, Long> {

    List<ProxyAllow> findAllByOrderByCidrAsc();

    boolean existsByCidr(String cidr);
}
