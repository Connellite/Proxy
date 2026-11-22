package io.github.connellite.proxy.relay;

import io.github.connellite.proxy.model.RelayDnsAllow;
import io.github.connellite.proxy.model.RelayTcpAllow;
import io.github.connellite.proxy.repository.RelayDnsAllowRepository;
import io.github.connellite.proxy.repository.RelayTcpAllowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RelayAllowService {

    private final RelayDnsAllowRepository dnsRepository;
    private final RelayTcpAllowRepository tcpRepository;
    private final RelayCatalog catalog;

    @Transactional(readOnly = true)
    public List<RelayDnsAllow> dnsAllows() {
        return dnsRepository.findAllByOrderByCidrAsc();
    }

    @Transactional(readOnly = true)
    public List<RelayTcpAllow> tcpAllows() {
        return tcpRepository.findAllByOrderByCidrAsc();
    }

    @Transactional
    public void saveDns(String raw) {
        String cidr = RelayNetworks.canonical(raw);
        if (dnsRepository.existsByCidr(cidr)) {
            throw new IllegalArgumentException("Network already listed: " + cidr);
        }
        RelayDnsAllow row = new RelayDnsAllow();
        row.setCidr(cidr);
        dnsRepository.save(row);
        afterCommit();
    }

    @Transactional
    public void deleteDns(long id) {
        if (!dnsRepository.existsById(id)) {
            throw new IllegalArgumentException("Network not found");
        }
        dnsRepository.deleteById(id);
        afterCommit();
    }

    @Transactional
    public void saveTcp(String raw) {
        String cidr = RelayNetworks.canonical(raw);
        if (tcpRepository.existsByCidr(cidr)) {
            throw new IllegalArgumentException("Network already listed: " + cidr);
        }
        RelayTcpAllow row = new RelayTcpAllow();
        row.setCidr(cidr);
        tcpRepository.save(row);
        afterCommit();
    }

    @Transactional
    public void deleteTcp(long id) {
        if (!tcpRepository.existsById(id)) {
            throw new IllegalArgumentException("Network not found");
        }
        tcpRepository.deleteById(id);
        afterCommit();
    }

    private void afterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                catalog.reload();
            }
        });
    }
}
