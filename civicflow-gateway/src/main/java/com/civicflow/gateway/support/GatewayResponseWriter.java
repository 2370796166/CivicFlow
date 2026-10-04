package com.civicflow.gateway.support;

import com.civicflow.common.api.ApiResponse;
import com.civicflow.common.api.GlobalErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayResponseWriter {
    private final ObjectMapper objectMapper;

    public GatewayResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(
            ServerWebExchange exchange, HttpStatus status, GlobalErrorCode errorCode) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes =
                    objectMapper.writeValueAsBytes(
                            ApiResponse.failure(
                                    errorCode, null, GatewayRequestContext.requestId(exchange)));
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }
}
