package io.github.connellite.proxy.config.hint;

#if SPRING_BOOT_3
import com.google.gwt.user.client.rpc.CustomFieldSerializer;
import com.google.gwt.user.client.rpc.core.java.lang.Boolean_CustomFieldSerializer;
import com.google.gwt.user.client.rpc.core.java.lang.Integer_CustomFieldSerializer;
import com.google.gwt.user.client.rpc.core.java.lang.Long_CustomFieldSerializer;
import com.google.gwt.user.client.rpc.core.java.lang.String_CustomFieldSerializer;
import com.google.gwt.user.client.rpc.core.java.util.ArrayList_CustomFieldSerializer;
import com.google.gwt.user.client.rpc.core.java.util.HashMap_CustomFieldSerializer;
import com.google.gwt.user.client.rpc.core.java.util.HashSet_CustomFieldSerializer;
import com.google.gwt.user.client.rpc.core.java.util.LinkedHashMap_CustomFieldSerializer;
import com.google.gwt.user.client.rpc.core.java.util.LinkedList_CustomFieldSerializer;
import com.google.gwt.user.client.rpc.core.java.util.Vector_CustomFieldSerializer;
import com.google.gwt.user.server.rpc.ServerCustomFieldSerializer;
import com.google.gwt.user.server.rpc.core.java.util.ArrayList_ServerCustomFieldSerializer;
import com.google.gwt.user.server.rpc.core.java.util.HashMap_ServerCustomFieldSerializer;
import com.google.gwt.user.server.rpc.core.java.util.HashSet_ServerCustomFieldSerializer;
import com.google.gwt.user.server.rpc.core.java.util.LinkedHashMap_ServerCustomFieldSerializer;
import com.google.gwt.user.server.rpc.core.java.util.LinkedList_ServerCustomFieldSerializer;
import com.google.gwt.user.server.rpc.core.java.util.Vector_ServerCustomFieldSerializer;
import io.github.connellite.proxy.client.rpc.AdminRpcException;
import io.github.connellite.proxy.client.rpc.AdminService;
import io.github.connellite.proxy.client.rpc.dto.AutoRefreshIntervalGWT;
import io.github.connellite.proxy.client.rpc.dto.DashboardDnsGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.DashboardProxyGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.DirectoryListingGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.EncryptionGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.HttpStripHeaderRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.HttpStripHeadersPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.PasswordChangeGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayAllowRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayQueryRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayRouteModeGWT;
import io.github.connellite.proxy.client.rpc.dto.RelayRouteRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.RelayTcpSessionRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SettingsGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SftpPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SftpUserRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SshAccessGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SshIssuedKeyGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.SshUserKeyRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.TlsStatusGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxiesPageGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyFormGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UpstreamProxyTypeGWT;
import io.github.connellite.proxy.client.rpc.dto.UserConnectionRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UserFormGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UserRowGWTDTO;
import io.github.connellite.proxy.client.rpc.dto.UsersPageGWTDTO;
import io.github.connellite.proxy.gwt.AdminServiceImpl;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Vector;

/**
 * GWT-RPC loads {@code *_CustomFieldSerializer} / {@code *_ServerCustomFieldSerializer}
 * via {@code Class.forName}. Without those classes registered, native images fall back to
 * reflecting into JDK collection fields and emit a payload the browser cannot deserialize.
 */
@Configuration
@ImportRuntimeHints(GwtRpcNativeConfiguration.Hints.class)
public class GwtRpcNativeConfiguration {

    static final class Hints implements RuntimeHintsRegistrar {

        private static final MemberCategory[] TYPE_CATEGORIES = {
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INTROSPECT_PUBLIC_METHODS,
                MemberCategory.INTROSPECT_DECLARED_METHODS
        };

        private static final Class<?>[] REFLECTION_TYPES = {
                AdminService.class,
                AdminServiceImpl.class,
                AdminRpcException.class,
                DashboardProxyGWTDTO.class,
                DashboardDnsGWTDTO.class,
                UsersPageGWTDTO.class,
                UserRowGWTDTO.class,
                UserFormGWTDTO.class,
                SettingsGWTDTO.class,
                PasswordChangeGWTDTO.class,
                EncryptionGWTDTO.class,
                TlsStatusGWTDTO.class,
                UpstreamProxiesPageGWTDTO.class,
                UpstreamProxyRowGWTDTO.class,
                UpstreamProxyFormGWTDTO.class,
                HttpStripHeadersPageGWTDTO.class,
                HttpStripHeaderRowGWTDTO.class,
                SshAccessGWTDTO.class,
                SshUserKeyRowGWTDTO.class,
                SshIssuedKeyGWTDTO.class,
                SftpPageGWTDTO.class,
                SftpUserRowGWTDTO.class,
                DirectoryListingGWTDTO.class,
                RelayPageGWTDTO.class,
                RelayRouteRowGWTDTO.class,
                RelayRouteModeGWT.class,
                RelayAllowRowGWTDTO.class,
                RelayQueryRowGWTDTO.class,
                RelayTcpSessionRowGWTDTO.class,
                UserConnectionRowGWTDTO.class,
                UpstreamProxyTypeGWT.class,
                AutoRefreshIntervalGWT.class,
                ArrayList.class,
                LinkedList.class,
                Vector.class,
                HashMap.class,
                LinkedHashMap.class,
                HashSet.class,
                String.class,
                Boolean.class,
                Integer.class,
                Long.class,
                String[].class,
                Throwable.class,
                Exception.class,
                RuntimeException.class,
                CustomFieldSerializer.class,
                ServerCustomFieldSerializer.class,
                // Client-side serializers (also probed by the server)
                ArrayList_CustomFieldSerializer.class,
                LinkedList_CustomFieldSerializer.class,
                Vector_CustomFieldSerializer.class,
                HashMap_CustomFieldSerializer.class,
                LinkedHashMap_CustomFieldSerializer.class,
                HashSet_CustomFieldSerializer.class,
                String_CustomFieldSerializer.class,
                Boolean_CustomFieldSerializer.class,
                Integer_CustomFieldSerializer.class,
                Long_CustomFieldSerializer.class,
                // Server serializers used when serializing RPC responses
                ArrayList_ServerCustomFieldSerializer.class,
                LinkedList_ServerCustomFieldSerializer.class,
                Vector_ServerCustomFieldSerializer.class,
                HashMap_ServerCustomFieldSerializer.class,
                LinkedHashMap_ServerCustomFieldSerializer.class,
                HashSet_ServerCustomFieldSerializer.class
        };

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            for (Class<?> type : REFLECTION_TYPES) {
                hints.reflection().registerType(type, TYPE_CATEGORIES);
            }
            // Nested Collections$Empty* serializers live under an awkward package name.
            registerTypeName(hints,
                    "com.google.gwt.user.client.rpc.core.java.util.Collections$EmptyList_CustomFieldSerializer");
            registerTypeName(hints,
                    "com.google.gwt.user.client.rpc.core.java.util.Collections$EmptyMap_CustomFieldSerializer");
            registerTypeName(hints,
                    "com.google.gwt.user.client.rpc.core.java.util.Collections$EmptySet_CustomFieldSerializer");
            registerTypeName(hints,
                    "com.google.gwt.user.client.rpc.core.java.util.Collections$SingletonList_CustomFieldSerializer");
            registerTypeName(hints,
                    "com.google.gwt.user.server.rpc.core.java.util.Collections$SingletonList_ServerCustomFieldSerializer");

            for (Method method : AdminService.class.getMethods()) {
                if (method.getDeclaringClass() == Object.class) {
                    continue;
                }
                hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
            }
            for (Method method : AdminServiceImpl.class.getDeclaredMethods()) {
                if (method.getName().startsWith("$") || method.getName().contains("jacoco")) {
                    continue;
                }
                hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
            }

            hints.resources().registerPattern("static/proxyAdmin/*");
            hints.resources().registerPattern("static/proxyAdmin/**");
            hints.resources().registerPattern("static/icons/*");
            hints.resources().registerPattern("static/icons/**");
        }

        private static void registerTypeName(RuntimeHints hints, String className) {
            try {
                Class<?> type = Class.forName(className);
                hints.reflection().registerType(type, TYPE_CATEGORIES);
            } catch (ClassNotFoundException ignored) {
                // optional nested serializers
            }
        }
    }
}
#else
/** No-op placeholder for Spring Boot 2 builds. */
public final class GwtRpcNativeConfiguration {
    private GwtRpcNativeConfiguration() {
    }
}
#endif
