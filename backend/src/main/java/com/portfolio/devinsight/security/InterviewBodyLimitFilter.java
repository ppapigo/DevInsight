package com.portfolio.devinsight.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds JSON parsing memory, including chunked bodies without Content-Length. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class InterviewBodyLimitFilter extends OncePerRequestFilter {
  private static final int MAX_BYTES = 8192;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equals(request.getMethod())
        || !"/api/interview/ask".equals(request.getServletPath());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > MAX_BYTES) {
      reject(response);
      return;
    }
    byte[] bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
    if (bytes.length > MAX_BYTES) {
      reject(response);
      return;
    }
    chain.doFilter(
        new HttpServletRequestWrapper(request) {
          @Override
          public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
              @Override
              public int read() {
                return input.read();
              }

              @Override
              public int read(byte[] buffer, int offset, int length) {
                return input.read(buffer, offset, length);
              }

              @Override
              public boolean isFinished() {
                return input.available() == 0;
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setReadListener(ReadListener listener) {
                throw new IllegalStateException("Synchronous endpoint");
              }
            };
          }

          @Override
          public BufferedReader getReader() {
            return new BufferedReader(
                new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
          }

          @Override
          public int getContentLength() {
            return bytes.length;
          }

          @Override
          public long getContentLengthLong() {
            return bytes.length;
          }
        },
        response);
  }

  private void reject(HttpServletResponse response) throws IOException {
    response.setStatus(413);
    response.setContentType("application/json;charset=UTF-8");
    response
        .getWriter()
        .write("{\"code\":\"REQUEST_TOO_LARGE\",\"message\":\"질문 요청 본문은 8KB 이하만 허용합니다.\"}");
  }
}
