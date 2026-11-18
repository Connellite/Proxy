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
 *   <li>Every path: the nearest existing ancestor is resolved, and the not-yet-created suffix is
 *       appended to that real path. Later operations use this path, so a symlink swapped in the
 *       prefix is not followed. The last name is kept, so lstat and unlink still see a link.</li>
 *   <li>Opening, listing or changing attributes of a path: the path itself must resolve inside the root.
 *       Listing a folder that contains such a link still works; the link just cannot be used.</li>
 * </ul>
 */
final class ConfinedRootedFileSystemProvider extends RootedFileSystemProvider {

    @Override
    protected Path resolveLocalPath(RootedPath path) {
        Path resolved = super.resolveLocalPath(path);
        Path root = path.getFileSystem().getRoot();
        Path parent = resolved.getParent();
        if (parent == null || !parent.startsWith(root)) {
            return resolved;
        }
        Path realParent = confine(parent, root, path);
        if (realParent == null) {
            return resolved;
        }
        Path name = resolved.getFileName();
        Path confined = name == null ? realParent : realParent.resolve(name);
        // The rooted provider re-roots local paths only when they still start with the
        // configured root. toRealPath can rewrite that prefix (8.3 names, junctions).
        return confined.startsWith(root) ? confined : resolved;
    }

    /**
     * Real path of {@code start}, or of its nearest existing ancestor plus the missing suffix.
     * Existence is tested without following links, so a symlink is not skipped and then used later.
     * Returns {@code null} when the real path leaves the configured root string: the caller keeps
     * the original path after the inside check. {@code toRealPath} still walks one component at a
     * time; the JDK has no atomic beneath-root open.
     */
    private static Path confine(Path start, Path root, RootedPath rooted) {
        Path realRoot;
        try {
            realRoot = root.toRealPath();
        } catch (IOException ex) {
            throw new InvalidPathException(rooted.toString(), "Not under root");
        }
        Path missing = null;
        for (Path cursor = start; cursor != null && cursor.startsWith(root); cursor = cursor.getParent()) {
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
                Path real = realInside(cursor, realRoot, rooted, missing);
                return real.startsWith(root) ? real : null;
            }
            Path name = cursor.getFileName();
            if (name != null) {
                missing = missing == null ? name : name.resolve(missing);
            }
            if (cursor.equals(root)) {
                break;
            }
        }
        throw new InvalidPathException(rooted.toString(), "Not under root");
    }

    private static Path realInside(Path existing, Path realRoot, RootedPath rooted, Path missing) {
        Path real;
        try {
            real = existing.toRealPath();
        } catch (IOException ex) {
            throw new InvalidPathException(rooted.toString(), "Not under root");
        }
        if (!real.startsWith(realRoot)) {
            throw new InvalidPathException(rooted.toString(), "Not under root");
        }
        return missing == null ? real : real.resolve(missing);
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
    public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        ensureInside(path, (Object[]) options.toArray(new OpenOption[0]));
        return super.newFileChannel(path, options, attrs);
    }

    @Override
    public AsynchronousFileChannel newAsynchronousFileChannel(
            Path path, Set<? extends OpenOption> options, ExecutorService executor, FileAttribute<?>... attrs) throws IOException {
        ensureInside(path, (Object[]) options.toArray(new OpenOption[0]));
        return super.newAsynchronousFileChannel(path, options, executor, attrs);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
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
        ensureCopyMove(source, target, options);
        super.copy(source, target, options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        ensureCopyMove(source, target, options);
        super.move(source, target, options);
    }

    /**
     * {@link LinkOption#NOFOLLOW_LINKS} applies to the source only. The target is still
     * followed when it already exists, so it is checked even when that option is set.
     */
    private void ensureCopyMove(Path source, Path target, CopyOption... options) throws IOException {
        if (options == null || !Arrays.asList(options).contains(LinkOption.NOFOLLOW_LINKS)) {
            ensureInside(source);
        }
        ensureInside(target);
    }

    private void ensureInside(Path path, Object... options) throws IOException {
        // Callers pass NOFOLLOW_LINKS only for open and setAttribute, which do not follow
        // the final link. copy and move handle that option themselves.
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
