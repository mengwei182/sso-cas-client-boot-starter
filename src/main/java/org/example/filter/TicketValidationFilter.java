package org.example.filter;

import org.apache.commons.lang3.StringUtils;
import org.jasig.cas.client.Protocol;
import org.jasig.cas.client.configuration.ConfigurationKeys;
import org.jasig.cas.client.util.AbstractCasFilter;
import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.util.ReflectUtils;
import org.jasig.cas.client.validation.Assertion;
import org.jasig.cas.client.validation.TicketValidationException;
import org.jasig.cas.client.validation.TicketValidator;
import org.jasig.cas.client.validation.json.Cas30JsonServiceTicketValidator;

import javax.net.ssl.HostnameVerifier;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class TicketValidationFilter extends AbstractCasFilter {

    /**
     * The TicketValidator we will use to validate tickets.
     */
    private TicketValidator ticketValidator;

    /**
     * 客户端回调地址
     */
    private String redirectUrl;

    /**
     * Specify whether the filter should redirect the user agent after a
     * successful validation to remove the ticket parameter from the query
     * string.
     */
    private boolean redirectAfterValidation = true;

    /**
     * Determines whether an exception is thrown when there is a ticket validation failure.
     */
    private boolean exceptionOnValidationFailure = false;

    /**
     * Specify whether the Assertion should be stored in a session
     * attribute {@link AbstractCasFilter#CONST_CAS_ASSERTION}.
     */
    private boolean useSession = true;

    public TicketValidationFilter() {
        this(Protocol.CAS3);
    }

    public TicketValidationFilter(final Protocol protocol) {
        super(protocol);
    }

    /**
     * Template method to return the appropriate validator.
     *
     * @param filterConfig the FilterConfiguration that may be needed to construct a validator.
     * @return the ticket validator.
     */
    protected TicketValidator getTicketValidator(final FilterConfig filterConfig) {
        return this.ticketValidator;
    }

    /**
     * Gets the ssl config to use for HTTPS connections
     * if one is configured for this filter.
     *
     * @return Properties that can contains key/trust info for Client Side Certificates
     */
    protected Properties getSSLConfig() {
        final Properties properties = new Properties();
        final String fileName = getString(ConfigurationKeys.SSL_CONFIG_FILE);

        if (fileName != null) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(fileName);
                properties.load(fis);
                logger.trace("Loaded {} entries from {}", properties.size(), fileName);
            } catch (final IOException ioe) {
                logger.error(ioe.getMessage(), ioe);
            } finally {
                CommonUtils.closeQuietly(fis);
            }
        }
        return properties;
    }

    /**
     * Gets the configured {@link HostnameVerifier} to use for HTTPS connections
     * if one is configured for this filter.
     *
     * @return Instance of specified host name verifier or null if none specified.
     */
    protected HostnameVerifier getHostnameVerifier() {
        final Class<? extends HostnameVerifier> className = getClass(ConfigurationKeys.HOSTNAME_VERIFIER);
        final String config = getString(ConfigurationKeys.HOSTNAME_VERIFIER_CONFIG);
        if (className != null) {
            if (config != null) {
                return ReflectUtils.newInstance(className, config);
            } else {
                return ReflectUtils.newInstance(className);
            }
        }
        return null;
    }

    @Override
    protected void initInternal(final FilterConfig filterConfig) throws ServletException {
        setExceptionOnValidationFailure(getBoolean(ConfigurationKeys.EXCEPTION_ON_VALIDATION_FAILURE));
        setRedirectAfterValidation(getBoolean(ConfigurationKeys.REDIRECT_AFTER_VALIDATION));
        setUseSession(getBoolean(ConfigurationKeys.USE_SESSION));
        this.redirectUrl = filterConfig.getInitParameter("redirectUrl");

        if (!this.useSession && this.redirectAfterValidation) {
            logger.warn("redirectAfterValidation parameter may not be true when useSession parameter is false. Resetting it to false in order to prevent infinite redirects.");
            setRedirectAfterValidation(false);
        }

        setTicketValidator(new Cas30JsonServiceTicketValidator(filterConfig.getInitParameter("casServerUrlPrefix")));
        super.initInternal(filterConfig);
    }

    @Override
    public void init() {
        super.init();
        CommonUtils.assertNotNull(this.ticketValidator, "ticketValidator cannot be null.");
    }

    /**
     * Pre-process the request before the normal filter process starts.  This could be useful for pre-empting code.
     *
     * @param servletRequest The servlet request.
     * @param servletResponse The servlet response.
     * @param filterChain the filter chain.
     * @return true if processing should continue, false otherwise.
     * @throws IOException if there is an I/O problem
     * @throws ServletException if there is a servlet problem.
     */
    protected boolean preFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain) throws IOException, ServletException {
        return true;
    }

    /**
     * Template method that gets executed if ticket validation succeeds.  Override if you want additional behavior to occur
     * if ticket validation succeeds.  This method is called after all ValidationFilter processing required for a successful authentication
     * occurs.
     *
     * @param request the HttpServletRequest.
     * @param response the HttpServletResponse.
     * @param assertion the successful Assertion from the server.
     */
    protected void onSuccessfulValidation(final HttpServletRequest request, final HttpServletResponse response, final Assertion assertion) {
        // nothing to do here.
    }

    /**
     * Template method that gets executed if validation fails.  This method is called right after the exception is caught from the ticket validator
     * but before any of the processing of the exception occurs.
     *
     * @param request the HttpServletRequest.
     * @param response the HttpServletResponse.
     */
    protected void onFailedValidation(final HttpServletRequest request, final HttpServletResponse response) {
        // nothing to do here.
    }

    @Override
    public final void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain) throws IOException, ServletException {
        if (!preFilter(servletRequest, servletResponse, filterChain)) {
            return;
        }

        final HttpServletRequest request = (HttpServletRequest) servletRequest;
        final HttpServletResponse response = (HttpServletResponse) servletResponse;
        final String ticket = retrieveTicketFromRequest(request);

        if (CommonUtils.isNotBlank(ticket)) {
            logger.debug("Attempting to validate ticket: {}", ticket);

            try {
                final Assertion assertion = this.ticketValidator.validate(ticket, constructServiceUrl(request, response));

                logger.debug("Successfully authenticated user: {}", assertion.getPrincipal().getName());

                request.setAttribute(CONST_CAS_ASSERTION, assertion);

                if (this.useSession) {
                    request.getSession().setAttribute(CONST_CAS_ASSERTION, assertion);
                }
                onSuccessfulValidation(request, response, assertion);

                if (this.redirectAfterValidation) {
                    // 前端主动携带了跳转地址则优先跳转到该地址
                    String redirectUrl1 = request.getParameter("redirectUrl");
                    if (!StringUtils.isEmpty(redirectUrl1)) {
                        this.redirectUrl = redirectUrl1;
                    }
                    logger.debug("Redirecting after successful ticket validation.");
                    if (StringUtils.isNotEmpty(this.redirectUrl)) {
                        response.sendRedirect(this.redirectUrl);
                    } else {
                        response.sendRedirect(constructServiceUrl(request, response));
                    }
                    return;
                }
            } catch (final TicketValidationException e) {
                logger.debug(e.getMessage(), e);

                onFailedValidation(request, response);

                if (this.exceptionOnValidationFailure) {
                    throw new ServletException(e);
                }

                response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());

                return;
            }
        }

        filterChain.doFilter(request, response);

    }

    public final void setTicketValidator(final TicketValidator ticketValidator) {
        this.ticketValidator = ticketValidator;
    }

    public final void setRedirectAfterValidation(final boolean redirectAfterValidation) {
        this.redirectAfterValidation = redirectAfterValidation;
    }

    public final void setExceptionOnValidationFailure(final boolean exceptionOnValidationFailure) {
        this.exceptionOnValidationFailure = exceptionOnValidationFailure;
    }

    public final void setUseSession(final boolean useSession) {
        this.useSession = useSession;
    }
}