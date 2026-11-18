package io.github.connellite.proxy.util;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;

import java.util.List;

/**
 * Switches the attached Windows console to UTF-8 (code page 65001), the same effect as
 * {@code chcp 65001}. Only linked into a Windows native image; the JVM never calls it.
 */
@CLibrary("kernel32")
@CContext(WindowsConsoleUtf8.Directives.class)
public final class WindowsConsoleUtf8 {

    static final int CP_UTF8 = 65001;

    private WindowsConsoleUtf8() {
    }

    public static void enable() {
        SetConsoleOutputCP(CP_UTF8);
        SetConsoleCP(CP_UTF8);
    }

    @CFunction
    private static native int SetConsoleOutputCP(int codePage);

    @CFunction
    private static native int SetConsoleCP(int codePage);

    static final class Directives implements CContext.Directives {

        @Override
        public boolean isInConfiguration() {
            return Platform.includedIn(Platform.WINDOWS.class);
        }

        @Override
        public List<String> getMacroDefinitions() {
            // Keep the header parse small: the full windows.h pulls in Winsock and breaks libclang.
            return List.of("WIN32_LEAN_AND_MEAN");
        }

        @Override
        public List<String> getHeaderFiles() {
            return List.of("<windows.h>");
        }
    }
}
