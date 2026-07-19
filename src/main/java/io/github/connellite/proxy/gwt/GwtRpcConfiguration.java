package io.github.connellite.proxy.gwt;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GwtRpcConfiguration {

    @Bean
    public ServletRegistrationBean<AdminServiceImpl> adminRpcServlet(AdminServiceImpl adminService) {
        ServletRegistrationBean<AdminServiceImpl> registration =
                new ServletRegistrationBean<>(adminService, "/proxyAdmin/rpc/admin");
        registration.setName("adminRpc");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
