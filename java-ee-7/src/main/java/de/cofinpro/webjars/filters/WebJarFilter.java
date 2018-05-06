package de.cofinpro.webjars.filters;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webjars.MultipleMatchesException;
import org.webjars.WebJarAssetLocator;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static de.cofinpro.webjars.filters.WebJarFilter.WEB_INIT_PARAM_RESPONSE_SERVE_METHOD;

/**
 * Created by David Olah (@olada)
 * Date: 01.05.2018 - 11:24.
 */
@WebFilter(
        filterName = "webjarFilter",
        urlPatterns = "/webjars/*",
        initParams = {
                @WebInitParam(
                        name = WEB_INIT_PARAM_RESPONSE_SERVE_METHOD,
                        description = "Defines how this filter shall handle the resolution of the filter",
                        value = "WRITE_BYTE_RESPONSE")
        }
)
public class WebJarFilter implements Filter {
    private Logger logger = LoggerFactory.getLogger(getClass());

    // name of web init param for response serve method
    static final String WEB_INIT_PARAM_RESPONSE_SERVE_METHOD = "responseServeMethod";

    @Inject
    private WebJarAssetLocator webJarAssetLocator;

    // Controls behavior of the filter
    ResponseServeMethod responseServeMethod;

    /**
     * Defines valid values for the init parameter "responseServeMethod"
     */
    enum ResponseServeMethod {
        WRITE_BYTE_RESPONSE,
        REDIRECT
    }

    @Override
    public void init(FilterConfig filterConfig) {
        initResponseServeMethod(filterConfig);
    }

    /**
     * Core filter method invoked by the container.
     * Behavior depends on the response server method: Either redirect to versioned webjar or
     * directly write content of versioned web jar into output stream.
     * @param servletRequest ServletRequest
     * @param servletResponse ServletResponse
     * @param filterChain Filter chain
     * @throws IOException IOException
     * @throws ServletException ServletException
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;

        String fullPathToWebjar = getFullPathToWebJar(httpServletRequest);
        if (ResponseServeMethod.REDIRECT.equals(responseServeMethod)) {
            redirectToVersionedWebjar(fullPathToWebjar, httpServletRequest, httpServletResponse);
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            writeWebjarContentToServletResponseOutputStream(fullPathToWebjar, servletResponse);
            // explicitly don't call the filter chain because we have written to the output stream
        }
    }

    /**
     * Redirects to the versioned webjar.
     * @param fullPathToWebjar Full-Path to webjar which was previously resolved using the webjar locator
     * @param httpServletRequest HttpServletRequest
     * @param httpServletResponse HttpServletResponse
     */
    private void redirectToVersionedWebjar(String fullPathToWebjar, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        String redirectLocation = fullPathToWebjar.replaceAll("^.*/webjars/", "/webjars/");
        boolean shouldRedirect = ! httpServletRequest.getRequestURI().contains(redirectLocation);
        if (shouldRedirect) {
            try {
                httpServletResponse.sendRedirect(redirectLocation);
            } catch (IOException e) {
                logger.error("An error occured during a redirect", e);
            }
        }
    }

    @Override
    public void destroy() {
        // nop
    }

    /**
     * Initializes the value for the response serve method by using the init parameter passed to the filter.
     * If the value does not adhere to one of the values of the ResponseServeMethod-enum, the fallback value of
     * <em>WRITE_BYTE_RESPONSE</em> is used.
     * @param filterConfig Configuration of the filter, passed in by the container
     */
    private void initResponseServeMethod(FilterConfig filterConfig) {
        String initParameterForResponseServeMethod = filterConfig.getInitParameter(WEB_INIT_PARAM_RESPONSE_SERVE_METHOD);
        Optional<String> responseServeMethodInitParameter = Optional.ofNullable(initParameterForResponseServeMethod);
        String enumNameToUse = responseServeMethodInitParameter.orElse(ResponseServeMethod.WRITE_BYTE_RESPONSE.name());
        try {
            responseServeMethod = ResponseServeMethod.valueOf(enumNameToUse);
        } catch (IllegalArgumentException e) {
            logger.warn("Could not find valid response serve method for value = {}. Falling back to {}.", initParameterForResponseServeMethod, ResponseServeMethod.WRITE_BYTE_RESPONSE.name());
            responseServeMethod = ResponseServeMethod.WRITE_BYTE_RESPONSE;
        }
    }

    /**
     * Gets full path to web jar using the {@link WebJarAssetLocator}
     *
     * @param httpRequest HttpServletRequest
     * @return full path to web jar
     */
    private String getFullPathToWebJar(HttpServletRequest httpRequest) {
        String requestUri = httpRequest.getRequestURI().replaceAll("^[/\\s]+", ""); // remove leading slashes and whitespaces
        String[] uriParts = requestUri.split("/");
        String fullPathToWebjar = requestUri;
        // The URI should be something along the lines of webjars/<webjar name>/<specific file>
        if (uriParts.length > 1) {
            String webjarId = uriParts[1]; // The first segment is always /webjars/, the second one is the id
            String filename = uriParts[uriParts.length - 1]; // filename should be the last segment
            logger.debug("Attempting to resolve webjar with webjarId = {} and filename = {}", webjarId, filename);
            try {
                // use the locator to get the full path
                fullPathToWebjar = webJarAssetLocator.getFullPath(webjarId, filename);
                logger.debug("Resolved webjar is: {}", fullPathToWebjar);
            } catch (MultipleMatchesException e) {
                // the locator throws an exception if it finds multiple files
                // if this is the case, we use the shortest path because additional files should be in subfolders which we probably don't care about
                List<String> matches = e.getMatches();
                logger.debug("Found multiple matches for webjar = {} and filename = {}. Matches are {}", webjarId, filename, matches);

                // when we have multiple matches, we favor the one with the shortest path
                Optional<String> pathWithShortestPath = matches.stream().min((item1, item2) -> item1.length() < item2.length() ? -1 : 1);
                if (pathWithShortestPath.isPresent()) {
                    fullPathToWebjar = pathWithShortestPath.get();
                    logger.debug("Using shortest path for webjar = {} and file = {}, which is {}", webjarId, filename, fullPathToWebjar);
                }
            }
        }
        return fullPathToWebjar;
    }

    /**
     * Write the content of a webjar to the output stream of the servlet response.
     *
     * @param fullPathToWebjar full path to the webjar file (META-INF/resources/....)
     * @param servletResponse  Servlet-Response object
     */
    private void writeWebjarContentToServletResponseOutputStream(String fullPathToWebjar, ServletResponse servletResponse) {
        try {
            InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(fullPathToWebjar);
            byte[] buffer = new byte[10240]; // buffer has size of 10 KB
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                servletResponse.getOutputStream().write(buffer, 0, len);
            }
            inputStream.close();
        } catch (IOException e) {
            logger.error("Could not write stream of " + fullPathToWebjar + " to servlet output stream", e);
        }
    }
}
