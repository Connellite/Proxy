package io.github.connellite.proxy.proxy.ssh;

import org.apache.sshd.common.Factory;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory;
import org.apache.sshd.common.util.threads.CloseableExecutorService;
import org.apache.sshd.common.util.threads.NoCloseExecutor;
import org.apache.sshd.common.util.threads.ThreadUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Avoids {@code IllegalStateException: Executor has been shut down} on Windows when Apache
 * Mina SSHD stops its NIO2 acceptor (mina-sshd#409 / JDK-7056546).
 * <p>
 * JDK {@code AsynchronousChannelGroup.shutdownNow()} closes channels then shuts down the
 * group's executor. On Windows, {@code AcceptTask.failed} still tries to run on that executor
 * afterwards. SSHD wraps the pool in {@link NoCloseExecutor}, which throws instead of
 * discarding — this subclass discards and also stops the real pool when the group closes it.
 */
final class ShutdownSafeNio2Executor extends NoCloseExecutor {

    private ShutdownSafeNio2Executor(CloseableExecutorService delegate) {
        super(delegate);
    }

    static Nio2ServiceFactoryFactory serviceFactoryFactory() {
        Factory<CloseableExecutorService> executors = () -> {
            int workers = Math.max(2, Runtime.getRuntime().availableProcessors() + 1);
            return new ShutdownSafeNio2Executor(ThreadUtils.newFixedThreadPool("proxy-sshd-nio2", workers));
        };
        return new Nio2ServiceFactoryFactory(executors);
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown()) {
            return;
        }
        try {
            executor.execute(command);
        } catch (RejectedExecutionException ignored) {
            // pool already terminated
        }
    }

    @Override
    public CloseFuture close(boolean immediately) {
        CloseFuture future = super.close(immediately);
        executor.shutdownNow();
        return future;
    }

    @Override
    public List<Runnable> shutdownNow() {
        close(true);
        return Collections.emptyList();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }
}
