package io.github.connellite.proxy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SftpAccessRootValidationTest {

    @TempDir
    Path tempDir;

    @Test
    void blankClearsFolder() {
        assertThat(SftpAccessService.normalizeRoot(null)).isNull();
        assertThat(SftpAccessService.normalizeRoot("   ")).isNull();
    }

    @Test
    void storesRealPathOfExistingDirectory() throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("share"));
        String stored = SftpAccessService.normalizeRoot("  " + dir.resolve("..").resolve("share") + "  ");
        assertThat(stored).isEqualTo(dir.toRealPath().toString());
    }

    @Test
    void rejectsRelativePath() {
        assertThatThrownBy(() -> SftpAccessService.normalizeRoot("share"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute");
    }

    @Test
    void rejectsMissingDirectory() {
        assertThatThrownBy(() -> SftpAccessService.normalizeRoot(tempDir.resolve("missing").toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void rejectsRegularFile() throws IOException {
        Path file = Files.writeString(tempDir.resolve("file.txt"), "x");
        assertThatThrownBy(() -> SftpAccessService.normalizeRoot(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a directory");
    }
}
