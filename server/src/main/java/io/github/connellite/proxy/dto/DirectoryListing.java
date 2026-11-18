package io.github.connellite.proxy.dto;

import java.util.List;

/**
 * One level of the server file system for the admin folder picker.
 *
 * @param path        absolute directory shown, or {@code null} for the list of file system roots
 * @param parent      parent directory, or {@code null} at a root (go back to the roots list)
 * @param directories absolute paths of the subdirectories (or of the roots)
 */
public record DirectoryListing(String path, String parent, List<String> directories) {
}
