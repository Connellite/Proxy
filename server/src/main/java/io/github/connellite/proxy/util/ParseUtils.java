package io.github.connellite.proxy.util;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public final class ParseUtils {

    public static boolean parseBoolean(String value, boolean defaultValue) {
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    public static int parseInt(String value, int defaultValue) {
        Integer parsed = Ints.tryParse(StringUtils.defaultString(value));
        return parsed != null ? parsed : defaultValue;
    }

    public static long parseLong(String value, long defaultValue) {
        Long parsed = Longs.tryParse(StringUtils.defaultString(value));
        return parsed != null ? parsed : defaultValue;
    }

    public static String parseString(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value;
    }
}
