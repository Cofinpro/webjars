package de.cofinpro.webjars.filters;

import io.undertow.servlet.spec.ServletOutputStreamImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.webjars.MultipleMatchesException;
import org.webjars.WebJarAssetLocator;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Created by David
 * Date: 01.05.2018 - 17:36.
 */
@RunWith(MockitoJUnitRunner.class)
public class WebJarFilterTest {
    @InjectMocks
    private WebJarFilter webJarFilter;

    @Mock
    private WebJarAssetLocator webJarAssetLocator;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private HttpServletResponse httpServletResponse;

    @Mock
    private FilterChain filterChain;

    @Mock
    private FilterConfig filterConfig;

    @Test
    public void init() {
        when(filterConfig.getInitParameter(WebJarFilter.WEB_INIT_PARAM_RESPONSE_SERVE_METHOD)).thenReturn("REDIRECT");
        webJarFilter.init(filterConfig);
        assertThat(webJarFilter.responseServeMethod, is(WebJarFilter.ResponseServeMethod.REDIRECT));
    }

    @Test
    public void init_withFallback() {
        when(filterConfig.getInitParameter(WebJarFilter.WEB_INIT_PARAM_RESPONSE_SERVE_METHOD)).thenReturn("invalid response method");
        webJarFilter.init(filterConfig);
        assertThat(webJarFilter.responseServeMethod, is(WebJarFilter.ResponseServeMethod.WRITE_BYTE_RESPONSE));
    }

    @Test
    public void doFilter_writeByteResponse_withMultipleMatches() throws IOException, ServletException {
        String requestUri = "/webjars/popper.js/popper.min.js";
        when(httpServletRequest.getRequestURI()).thenReturn(requestUri);
        List<String> matches = Arrays.asList(
                "META-INF/resources/webjars/popper.js/1.14.1/esm/popper.js",
                "META-INF/resources/webjars/popper.js/1.14.1/umd/popper.js",
                "META-INF/resources/webjars/popper.js/1.14.1/popper.js"
        );
        MultipleMatchesException multipleMatchesException = new MultipleMatchesException("a message..", matches);
        when(webJarAssetLocator.getFullPath("popper.js", "popper.min.js")).thenThrow(multipleMatchesException);

        ServletOutputStreamImpl outputStream = mock(ServletOutputStreamImpl.class);
        when(httpServletResponse.getOutputStream()).thenReturn(outputStream);
        webJarFilter.responseServeMethod = WebJarFilter.ResponseServeMethod.WRITE_BYTE_RESPONSE;
        webJarFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        verify(outputStream, times(9)).write(any(byte[].class), anyInt(), anyInt());
        verifyZeroInteractions(filterChain);
    }

    @Test
    public void doFilter_redirect() throws IOException, ServletException {
        when(webJarAssetLocator.getFullPath("jquery", "jquery.min.js")).thenReturn("META-INF/resources/webjars/jquery/3.0.0/jquery.min.js");
        webJarFilter.responseServeMethod = WebJarFilter.ResponseServeMethod.REDIRECT;
        when(httpServletRequest.getRequestURI()).thenReturn("/webjars/jquery/jquery.min.js");
        webJarFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        InOrder inOrder = inOrder(httpServletResponse, filterChain);
        inOrder.verify(httpServletResponse, times(1)).sendRedirect("/webjars/jquery/3.0.0/jquery.min.js");
        inOrder.verify(filterChain, times(1)).doFilter(httpServletRequest, httpServletResponse);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void doFilter_noInfiniteRedirections() throws IOException, ServletException {
        when(webJarAssetLocator.getFullPath("jquery", "jquery.min.js")).thenReturn("META-INF/resources/webjars/jquery/3.0.0/jquery.min.js");
        webJarFilter.responseServeMethod = WebJarFilter.ResponseServeMethod.REDIRECT;
        when(httpServletRequest.getRequestURI()).thenReturn("/webjars/jquery/3.0.0/jquery.min.js");
        webJarFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        verifyZeroInteractions(httpServletResponse);
        verify(filterChain, times(1)).doFilter(httpServletRequest, httpServletResponse);
    }


}