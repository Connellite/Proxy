package io.github.connellite.proxy.service;

import io.github.connellite.proxy.dto.DirectoryListing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerDirectoryServiceTest {

    private final ServerDirectoryService service = new ServerDirectoryService();

    @TempDir
    Path tempDir;

    @Test
    void listsOnlySubdirectoriesSorted() throws IOException {
        Files.createDirectories(tempDir.resolve("beta"));
        Files.createDirectories(tempDir.resolve("Alpha"));
        Files.writeString(tempDir.resolve("file.txt"), "x");

        DirectoryListing listing = service.list(tempDir.toString());

        assertThat(listing.path()).isEqualTo(tempDir.normalize().toString());
        assertThat(listing.directories()).containsExactly(
                tempDir.resolve("Alpha").toString(), tempDir.resolve("beta").toString());
    }

    @Test
    void blankPathListsRoots() {
        DirectoryListing listing = service.list(" ");
        assertThat(listing.path()).isNull();
        assertThat(listing.directories()).isNotEmpty();
    }

    @Test
    void createsSubfolder() {
        String created = service.create(tempDir.toString(), " share ");
        assertThat(created).isEqualTo(tempDir.resolve("share").toString());
        assertThat(Files.isDirectory(tempDir.resolve("share"))).isTrue();
    }

    @Test
    void rejectsNamesThatLeaveTheParent() {
        for (String name : new String[] {"..", ".", "a/b", "a\\b", "..\\x", "c:", ""}) {
            assertThatThrownBy(() -> service.create(tempDir.toString(), name))
                    .as(name)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsExistingFolder() throws IOException {
        Files.createDirectories(tempDir.resolve("taken"));
        assertThatThrownBy(() -> service.create(tempDir.toString(), "taken"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }
}
