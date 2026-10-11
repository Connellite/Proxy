package io.github.connellite.proxy.service;

import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.model.SshUserKey;
#if SPRING_BOOT_3
import jakarta.persistence.EntityManagerFactory;
#else
import javax.persistence.EntityManagerFactory;
#endif
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Drops second-level cache rows that bulk SQL does not refresh.
 * Runs after commit so a concurrent read cannot put the pre-update row back.
 */
@Service
@RequiredArgsConstructor
public class CacheService {

    private final EntityManagerFactory entityManagerFactory;

    public void evictUsersAfterCommit(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        afterCommit(() -> {
            for (String userId : userIds) {
                entityManagerFactory.getCache().evict(ProxyUser.class, userId);
            }
        });
    }

    public void evictSshKeysAfterCommit() {
        afterCommit(() -> entityManagerFactory.getCache().evict(SshUserKey.class));
    }

    private static void afterCommit(Runnable evict) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }
}
