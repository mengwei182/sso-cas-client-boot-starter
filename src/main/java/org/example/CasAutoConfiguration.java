package org.example;

import org.apache.commons.lang3.StringUtils;
import org.example.filter.AntPathMatcherStrategy;
import org.example.filter.AuthenticationFilter;
import org.example.filter.TicketValidationFilter;
import org.example.properties.CasProperties;
import org.jasig.cas.client.session.SingleSignOutFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import javax.annotation.Resource;
import javax.servlet.Filter;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lihui
 * @since 2024/1/10
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sso.cas", value = "enable", matchIfMissing = true)
public class CasAutoConfiguration {
    @Resource
    private CasProperties casProperties;

    @Bean
    public FilterRegistrationBean<Filter> authenticationFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthenticationFilter());
        registration.addUrlPatterns("/*");
        Map<String, String> initParameters = new HashMap<>();
        initParameters.put("casServerLoginUrl", casProperties.getLoginUrl());
        initParameters.put("casServerUrlPrefix", casProperties.getUrlPrefix());
        initParameters.put("serverName", casProperties.getServerName());
        initParameters.put("ignorePattern", StringUtils.join(casProperties.getIgnoreUrls(), ","));
        initParameters.put("ignoreUrlPatternType", AntPathMatcherStrategy.class.getName());
        registration.setInitParameters(initParameters);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<Filter> singleSignOutFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SingleSignOutFilter());
        registration.addUrlPatterns("/*");
        // 设置最高优先级，防止一些请求因为没有添加认证信息被过滤
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<Filter> ticketValidationFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TicketValidationFilter());
        registration.addUrlPatterns("/*");
        Map<String, String> initParameters = new HashMap<>();
        initParameters.put("casServerUrlPrefix", casProperties.getUrlPrefix());
        initParameters.put("serverName", casProperties.getServerName());
        initParameters.put("redirectUrl", casProperties.getRedirectUrl());
        registration.setInitParameters(initParameters);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<Filter> corsFilterRegistration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOrigin("*");
        configuration.addAllowedHeader("*");
        configuration.addAllowedMethod("*");
        configuration.setMaxAge(3600L);
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/*", configuration);
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CorsFilter(source));
        registration.addUrlPatterns("/*");
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }

    @Bean
    public CasProperties casProperties() {
        return new CasProperties();
    }
}