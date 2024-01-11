package org.example.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "sso.cas.server")
public class CasProperties {
    /**
     * cas服务登录地址全路径
     */
    private String loginUrl;
    /**
     * cas服务登出地址全路径
     */
    private String logoutUrl;
    /**
     * cas服务地址前缀
     */
    private String urlPrefix;
    /**
     * 客户端所在服务请求地址，用作回调客户端接口使用
     */
    private String serverName;
    /**
     * 前后端分离架构只能拦截到后端服务接口，跳转也是到后端接口，所以此处配置该参数直接跳转到前端页面
     */
    private String redirectUrl;
    /**
     * 过滤白名单，多配置以英文逗号分隔
     */
    private String ignoreUrls;

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    public String getLogoutUrl() {
        return logoutUrl;
    }

    public void setLogoutUrl(String logoutUrl) {
        this.logoutUrl = logoutUrl;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public String getIgnoreUrls() {
        return ignoreUrls;
    }

    public void setIgnoreUrls(String ignoreUrls) {
        this.ignoreUrls = ignoreUrls;
    }
}