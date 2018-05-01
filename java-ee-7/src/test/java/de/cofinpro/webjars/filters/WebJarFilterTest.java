package de.cofinpro.webjars.filters;

import io.undertow.servlet.spec.ServletOutputStreamImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.webjars.MultipleMatchesException;
import org.webjars.WebJarAssetLocator;

import javax.servlet.FilterChain;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    public void doFilter_withMultipleMatches() throws IOException {
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
        webJarFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        verify(outputStream, times(9)).write(any(byte[].class), anyInt(), anyInt());
    }
}