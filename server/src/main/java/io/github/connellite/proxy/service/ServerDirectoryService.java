package io.github.connellite.proxy.service;

import io.github.connellite.proxy.dto.DirectoryListing;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists server directories for the admin folder picker (SFTP roots). Only reachable through the
 * admin RPC, which requires {@code ROLE_ADMIN}.
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

        List<String> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, Files::isDirectory)) {
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

    private static DirectoryListing roots() {
        List<String> roots = new ArrayList<>();
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            roots.add(root.toString());
        }
        roots.sort(String.CASE_INSENSITIVE_ORDER);
        return new DirectoryListing(null, null, roots);
    }
}
