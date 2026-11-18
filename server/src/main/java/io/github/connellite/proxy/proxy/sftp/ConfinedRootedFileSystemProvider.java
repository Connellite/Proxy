package io.github.connellite.proxy.proxy.sftp;

import org.apache.sshd.common.file.root.RootedFileSystemProvider;
import org.apache.sshd.common.file.root.RootedPath;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * {@link RootedFileSystemProvider} that also refuses symbolic links and Windows junctions placed in the
 * SFTP folder (by someone with server access) that point outside it. SFTP clients can only create
 * links that stay inside the root; links already on disk are followed by the stock provider.
 * <ul>
 *   <li>Every path: the nearest existing parent directory must resolve inside the root, so nothing
 *       can be reached <em>through</em> an outside link.</li>
 *   <li>Opening, listing or changing attributes of a path: the path itself must resolve inside the root.
 *       Listing a folder that contains such a link still works; the link just cannot be used.</li>
 * </ul>
 */
final class ConfinedRootedFileSystemProvider extends RootedFileSystemProvider {

    @Override
    protected Path resolveLocalPath(RootedPath path) {
        Path resolved = super.resolveLocalPath(path);
        Path root = path.getFileSystem().getRoot();
        Path ancestor = resolved.getParent();
        while (ancestor != null && ancestor.startsWith(root) && !Files.exists(ancestor)) {
            ancestor = ancestor.getParent();
        }
        if (ancestor != null && ancestor.startsWith(root) && !resolvesInside(ancestor, root)) {
            throw new InvalidPathException(path.toString(), "Not under root");
        }
        return resolved;
    }

    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        ensureInside(path, (Object[]) options);
        return super.newInputStream(path, options);
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        ensureInside(path, (Object[]) options);
        return super.newOutputStream(path, options);
    }

    @Override
    public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        ensureInside(path, (Object[]) options.toArray(new OpenOption[0]));
        return super.newFileChannel(path, options, attrs);
    }

    @Override
    public AsynchronousFileChannel newAsynchronousFileChannel(
            Path path, Set<? extends OpenOption> options, ExecutorService executor, FileAttribute<?>... attrs)
            throws IOException {
        ensureInside(path, (Object[]) options.toArray(new OpenOption[0]));
        return super.newAsynchronousFileChannel(path, options, executor, attrs);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        ensureInside(path, (Object[]) options.toArray(new OpenOption[0]));
        return super.newByteChannel(path, options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        ensureInside(dir);
        return super.newDirectoryStream(dir, filter);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        ensureInside(path, (Object[]) options);
        super.setAttribute(path, attribute, value, options);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        ensureInside(source, (Object[]) options);
        super.copy(source, target, options);
    }

    private void ensureInside(Path path, Object... options) throws IOException {
        if (options != null && Arrays.asList(options).contains(LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Path local = unroot(path);
        Path root = ((RootedPath) path).getFileSystem().getRoot();
        if (Files.exists(local) && !resolvesInside(local, root)) {
            throw new AccessDeniedException(path.toString(), null, "Link points outside the SFTP folder");
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean resolvesInside(Path local, Path root) {
        try {
            return local.toRealPath().startsWith(root.toRealPath());
        } catch (IOException ex) {
            return false;
        }
    }
}
