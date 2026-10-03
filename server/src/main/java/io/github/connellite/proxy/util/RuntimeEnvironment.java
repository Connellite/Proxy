package io.github.connellite.proxy.util;

import lombok.experimental.UtilityClass;

import java.awt.GraphicsEnvironment;

@UtilityClass
public final class RuntimeEnvironment {

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static boolean isNativeImage() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
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
