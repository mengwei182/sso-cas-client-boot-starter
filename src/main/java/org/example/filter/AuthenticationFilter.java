package org.example.filter;

import com.google.gson.Gson;
import org.jasig.cas.client.Protocol;
import org.jasig.cas.client.authentication.*;
import org.jasig.cas.client.configuration.ConfigurationKeys;
import org.jasig.cas.client.util.AbstractCasFilter;
import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.util.ReflectUtils;
import org.jasig.cas.client.validation.Assertion;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AuthenticationFilter extends AbstractCasFilter {
    private static final Map<String, Class<? extends UrlPatternMatcherStrategy>> PATTERN_MATCHER_TYPES = new HashMap<String, Class<? extends UrlPatternMatcherStrategy>>();

    static {
        PATTERN_MATCHER_TYPES.put("CONTAINS", ContainsPatternUrlPatternMatcherStrategy.class);
        PATTERN_MATCHER_TYPES.put("REGEX", RegexUrlPatternMatcherStrategy.class);
        PATTERN_MATCHER_TYPES.put("FULL_REGEX", EntireRegionRegexUrlPatternMatcherStrategy.class);
        PATTERN_MATCHER_TYPES.put("EXACT", ExactUrlPatternMatcherStrategy.class);
    }

    /**
     * The URL to the CAS Server login.
     */
    private String casServerLoginUrl;
    /**
     * Whether to send the renew request or not.
     */
    private boolean renew = false;
    /**
     * Whether to send the gateway request or not.
     */
    private boolean gateway = false;
    /**
     * The method used by the CAS server to send the user back to the application.
     */
    private String method;
    private GatewayResolver gatewayStorage = new DefaultGatewayResolverImpl();
    private AuthenticationRedirectStrategy authenticationRedirectStrategy = new DefaultAuthenticationRedirectStrategy();
    private UrlPatternMatcherStrategy ignoreUrlPatternMatcherStrategyClass = null;

    public AuthenticationFilter() {
        this(Protocol.CAS2);
    }

    protected AuthenticationFilter(final Protocol protocol) {
        super(protocol);
    }

    @Override
    protected void initInternal(final FilterConfig filterConfig) throws ServletException {
        if (!isIgnoreInitConfiguration()) {
            super.initInternal(filterConfig);

            final String loginUrl = getString(ConfigurationKeys.CAS_SERVER_LOGIN_URL);
            if (loginUrl != null) {
                setCasServerLoginUrl(loginUrl);
            } else {
                setCasServerUrlPrefix(getString(ConfigurationKeys.CAS_SERVER_URL_PREFIX));
            }

            setRenew(getBoolean(ConfigurationKeys.RENEW));
            setGateway(getBoolean(ConfigurationKeys.GATEWAY));
            setMethod(getString(ConfigurationKeys.METHOD));

            final String ignorePattern = getString(ConfigurationKeys.IGNORE_PATTERN);
            final String ignoreUrlPatternType = getString(ConfigurationKeys.IGNORE_URL_PATTERN_TYPE);

            if (ignorePattern != null) {
                final Class<? extends UrlPatternMatcherStrategy> ignoreUrlMatcherClass = PATTERN_MATCHER_TYPES.get(ignoreUrlPatternType);
                if (ignoreUrlMatcherClass != null) {
                    this.ignoreUrlPatternMatcherStrategyClass = ReflectUtils.newInstance(ignoreUrlMatcherClass.getName());
                } else {
                    try {
                        logger.trace("Assuming {} is a qualified class name...", ignoreUrlPatternType);
                        this.ignoreUrlPatternMatcherStrategyClass = ReflectUtils.newInstance(ignoreUrlPatternType);
                    } catch (final IllegalArgumentException e) {
                        logger.error("Could not instantiate class [{}]", ignoreUrlPatternType, e);
                    }
                }
                if (this.ignoreUrlPatternMatcherStrategyClass != null) {
                    this.ignoreUrlPatternMatcherStrategyClass.setPattern(ignorePattern);
                }
            }

            final Class<? extends GatewayResolver> gatewayStorageClass = getClass(ConfigurationKeys.GATEWAY_STORAGE_CLASS);

            if (gatewayStorageClass != null) {
                setGatewayStorage(ReflectUtils.newInstance(gatewayStorageClass));
            }

            final Class<? extends AuthenticationRedirectStrategy> authenticationRedirectStrategyClass = getClass(ConfigurationKeys.AUTHENTICATION_REDIRECT_STRATEGY_CLASS);

            if (authenticationRedirectStrategyClass != null) {
                this.authenticationRedirectStrategy = ReflectUtils.newInstance(authenticationRedirectStrategyClass);
            }
        }
    }

    @Override
    public void init() {
        super.init();
        final String message = String.format("one of %s and %s must not be null.", ConfigurationKeys.CAS_SERVER_LOGIN_URL.getName(), ConfigurationKeys.CAS_SERVER_URL_PREFIX.getName());
        CommonUtils.assertNotNull(this.casServerLoginUrl, message);
    }

    @Override
    public final void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain) throws IOException, ServletException {
        final HttpServletRequest request = (HttpServletRequest) servletRequest;
        final HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (isRequestUrlExcluded(request)) {
            logger.debug("Request is ignored.");
            filterChain.doFilter(request, response);
            return;
        }

        final HttpSession session = request.getSession(false);
        final Assertion assertion = session != null ? (Assertion) session.getAttribute(CONST_CAS_ASSERTION) : null;

        if (assertion != null) {
            filterChain.doFilter(request, response);
            return;
        }

        final String serviceUrl = constructServiceUrl(request, response);
        final String ticket = retrieveTicketFromRequest(request);
        final boolean wasGatewayed = this.gateway && this.gatewayStorage.hasGatewayedAlready(request, serviceUrl);

        if (CommonUtils.isNotBlank(ticket) || wasGatewayed) {
            filterChain.doFilter(request, response);
            return;
        }

        final String modifiedServiceUrl;

        logger.debug("no ticket and no assertion found");
        if (this.gateway) {
            logger.debug("setting gateway attribute in session");
            modifiedServiceUrl = this.gatewayStorage.storeGatewayInformation(request, serviceUrl);
        } else {
            modifiedServiceUrl = serviceUrl;
        }

        logger.debug("Constructed service url: {}", modifiedServiceUrl);

        String urlToRedirectTo = CommonUtils.constructRedirectUrl(this.casServerLoginUrl, getProtocol().getServiceParameterName(), modifiedServiceUrl, this.renew, this.gateway, this.method);

        logger.debug("redirecting to \"{}\"", urlToRedirectTo);
        // 判断是否ajax请求
        if (isAjax(request)) {
            logger.info("this request is ajax:{}", request.getRequestURL().toString());
            response.setStatus(401);
            Map<String, Object> responseObject = new HashMap<>();
            responseObject.put("data", urlToRedirectTo);
            responseObject.put("message", "");
            responseObject.put("code", 401);
            response.getWriter().write(new Gson().toJson(responseObject));
        } else {
            this.authenticationRedirectStrategy.redirect(request, response, urlToRedirectTo);
        }
    }

    private boolean isAjax(HttpServletRequest request) {
        return request.getHeader("X-Requested-With") != null && "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    public final void setRenew(final boolean renew) {
        this.renew = renew;
    }

    public final void setGateway(final boolean gateway) {
        this.gateway = gateway;
    }

    public void setMethod(final String method) {
        this.method = method;
    }

    public final void setCasServerUrlPrefix(final String casServerUrlPrefix) {
        setCasServerLoginUrl(CommonUtils.addTrailingSlash(casServerUrlPrefix) + "login");
    }

    public final void setCasServerLoginUrl(final String casServerLoginUrl) {
        this.casServerLoginUrl = casServerLoginUrl;
    }

    public final void setGatewayStorage(final GatewayResolver gatewayStorage) {
        this.gatewayStorage = gatewayStorage;
    }

    private boolean isRequestUrlExcluded(final HttpServletRequest request) {
        if (this.ignoreUrlPatternMatcherStrategyClass == null) {
            return false;
        }
        final StringBuffer urlBuffer = request.getRequestURL();
        if (request.getQueryString() != null) {
            urlBuffer.append("?").append(request.getQueryString());
        }
        final String requestUri = urlBuffer.toString();
        return this.ignoreUrlPatternMatcherStrategyClass.matches(requestUri);
    }

    public final void setIgnoreUrlPatternMatcherStrategyClass(final UrlPatternMatcherStrategy ignoreUrlPatternMatcherStrategyClass) {
        this.ignoreUrlPatternMatcherStrategyClass = ignoreUrlPatternMatcherStrategyClass;
    }
}