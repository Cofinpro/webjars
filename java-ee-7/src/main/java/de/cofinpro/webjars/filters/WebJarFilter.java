package de.cofinpro.webjars.filters;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webjars.MultipleMatchesException;
import org.webjars.WebJarAssetLocator;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Created by David
 * Date: 01.05.2018 - 11:24.
 */
@WebFilter(filterName = "webjarFilter", urlPatterns = "/*")
public class WebJarFilter implements Filter {
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    private WebJarAssetLocator webJarAssetLocator;

    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("Init");
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        String fullPathToWebjar = getFullPathToWebJar(httpRequest);
        writeWebjarContentToServletResponseOutputStream(fullPathToWebjar, servletResponse);
        // explicitly don't call the filter chain because we have written to the output stream
    }

    public void destroy() {
        // nop
    }

    /**
     * Gets full path to web jar using the {@link WebJarAssetLocator}
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
     * Write the content of a webjar to the output stream of the servlet response
     * @param fullPathToWebjar full path to the webjar file (META-INF/resources/....)
     * @param servletResponse Servlet-Response object
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
