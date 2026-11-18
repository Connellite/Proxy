package io.github.connellite.proxy.service;

import io.github.connellite.proxy.dto.DirectoryListing;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists server directories for the admin folder picker (SFTP roots). Only reachable through the
 * admin RPC, which requires {@code ROLE_ADMIN}. Folders the server process cannot open are left out
 * (see {@link #isBrowsable(Path)}), so the picker never offers a folder that fails on expand or over SFTP.
 */
@Service
public class ServerDirectoryService {

    /** Guards the RPC payload for huge directories. */
    private static final int MAX_ENTRIES = 2000;

    public DirectoryListing list(String path) {
        String trimmed = StringUtils.trimToNull(path);
        if (trimmed == null) {
            return roots();
        }
        Path dir = existingAbsoluteDirectory(trimmed);

        List<String> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, ServerDirectoryService::isBrowsable)) {
            for (Path child : stream) {
                children.add(child.toString());
                if (children.size() >= MAX_ENTRIES) {
                    break;
                }
            }
        } catch (AccessDeniedException ex) {
            throw new IllegalArgumentException("Access denied: " + dir);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Cannot read folder " + dir + ": " + ex.getMessage());
        }
        children.sort(String.CASE_INSENSITIVE_ORDER);
        Path parent = dir.getParent();
        return new DirectoryListing(dir.toString(), parent == null ? null : parent.toString(), children);
    }

    /**
     * A directory the server process can list and enter: read permission to list it and, on POSIX,
     * execute (search) permission to open its entries. Follows symbolic links.
     */
    public static boolean isBrowsable(Path path) {
        return Files.isDirectory(path) && Files.isReadable(path) && Files.isExecutable(path);
    }

    /**
     * Creates {@code name} directly inside {@code parentPath} and returns the new folder's absolute path.
     * The name must be a single path segment.
     */
    public String create(String parentPath, String name) {
        String trimmedName = StringUtils.trimToNull(name);
        if (trimmedName == null) {
            throw new IllegalArgumentException("Folder name is required");
        }
        if (trimmedName.equals(".") || trimmedName.equals("..") || trimmedName.length() > 255
                || StringUtils.containsAny(trimmedName, '/', '\\', ':', '*', '?', '"', '<', '>', '|')
                || trimmedName.chars().anyMatch(ch -> ch < 0x20)) {
            throw new IllegalArgumentException("Invalid folder name: " + trimmedName);
        }
        Path parent = existingAbsoluteDirectory(parentPath);
        Path child = parent.resolve(trimmedName).normalize();
        if (!parent.equals(child.getParent())) {
            throw new IllegalArgumentException("Invalid folder name: " + trimmedName);
        }
        try {
            Files.createDirectory(child);
        } catch (FileAlreadyExistsException ex) {
            throw new IllegalArgumentException("Folder already exists: " + child);
        } catch (AccessDeniedException ex) {
            throw new IllegalArgumentException("Access denied: " + parent);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Cannot create folder " + child + ": " + ex.getMessage());
        }
        return child.toString();
    }

    private static Path existingAbsoluteDirectory(String path) {
        String trimmed = StringUtils.trimToNull(path);
        if (trimmed == null) {
            throw new IllegalArgumentException("Folder path is required");
        }
        Path dir;
        try {
            dir = Path.of(trimmed);
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Invalid folder path: " + trimmed);
        }
        if (!dir.isAbsolute()) {
            throw new IllegalArgumentException("Folder path must be absolute: " + trimmed);
        }
        dir = dir.normalize();
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Folder does not exist: " + dir);
        }
        return dir;
    }

    private static DirectoryListing roots() {
        List<String> roots = new ArrayList<>();
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            // Skips e.g. Windows drives without media and roots the process cannot read.
            if (isBrowsable(root)) {
                roots.add(root.toString());
            }
        }
        roots.sort(String.CASE_INSENSITIVE_ORDER);
        return new DirectoryListing(null, null, roots);
    }
}
