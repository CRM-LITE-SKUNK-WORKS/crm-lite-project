package com.crm.observability.starter;

import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Propagates the CURRENT request's correlation id onto an outbound RestClient
 * call, the observability counterpart to
 * {@link com.crm.security.starter.BearerTokenPropagationInterceptor}: opt-in per
 * RestClient, never registered globally, so a downstream call that should NOT
 * carry it (there is no such case today, but the opt-in shape matches the
 * existing bearer-propagation precedent rather than assuming one).
 */
public class CorrelationIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!request.getHeaders().containsHeader(CorrelationIdFilter.HEADER)) {
            String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
            if (correlationId != null) {
                request.getHeaders().add(CorrelationIdFilter.HEADER, correlationId);
            }
        }
        return execution.execute(request, body);
    }
}
