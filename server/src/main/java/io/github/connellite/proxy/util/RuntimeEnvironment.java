package io.github.connellite.proxy.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.SystemUtils;
import org.graalvm.nativeimage.ImageInfo;

import java.awt.GraphicsEnvironment;

@UtilityClass
public final class RuntimeEnvironment {

    public static boolean isLinux() {
        return SystemUtils.IS_OS_LINUX;
    }

    public static boolean isWindows() {
        return SystemUtils.IS_OS_WINDOWS;
    }

    public static boolean isNativeImage() {
        return ImageInfo.inImageRuntimeCode();
    }

    public static boolean isHeadless() {
        try {
            return GraphicsEnvironment.isHeadless();
        } catch (Throwable ex) {
            // Catch Throwable (not Exception): AWT/JNI can throw Error subclasses
            // like NoSuchMethodError under GraalVM native — those would bypass catch (Exception)
            // and abort Spring Boot startup. Treat any AWT probe failure as headless.
            return true;
        }
    }
}
