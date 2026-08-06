package com.crm.order.order.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import org.springframework.util.StreamUtils;

/**
 * Buffers the whole request body into memory ONCE, up front, so it can be read twice:
 * here (to compute the idempotency hash) and again downstream by Spring MVC's
 * {@code @RequestBody} deserialization. {@link jakarta.servlet.http.HttpServletRequest}'s
 * input stream is otherwise single-read-only, which is exactly why this class exists —
 * {@code ContentCachingRequestWrapper}'s lazy caching only works if something reads the
 * stream exactly once for both purposes, which is not this filter's shape.
 *
 * <p>Only ever installed on {@code POST /api/orders} ({@link IdempotencyKeyFilter}),
 * whose bodies are small hand-typed baskets — buffering the whole thing is cheap and
 * unconditionally safe here in a way it would not be for an arbitrary upload endpoint.
 */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.body = StreamUtils.copyToByteArray(request.getInputStream());
    }

    byte[] cachedBody() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream source = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return source.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Synchronous, in-memory source — nothing to notify.
            }

            @Override
            public int read() {
                return source.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
    }
}
