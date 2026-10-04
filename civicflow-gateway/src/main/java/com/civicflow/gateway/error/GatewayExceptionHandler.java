package com.civicflow.gateway.error;

import com.civicflow.gateway.support.GatewayRequestContext;
import com.civicflow.gateway.support.GatewayResponseWriter;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayExceptionHandler implements WebExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    private final GatewayResponseWriter responseWriter;

    public GatewayExceptionHandler(GatewayResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        boolean dependencyFailure = isDependencyFailure(exception);
        HttpStatus status =
                dependencyFailure
                        ? HttpStatus.SERVICE_UNAVAILABLE
                        : HttpStatus.INTERNAL_SERVER_ERROR;
        GatewayErrorCode errorCode =
                dependencyFailure
                        ? GatewayErrorCode.DEPENDENCY_UNAVAILABLE
                        : GatewayErrorCode.INTERNAL_ERROR;
        LOGGER.warn(
                "Gateway request failed requestId={} method={} path={} type={}",
                GatewayRequestContext.requestId(exchange),
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(),
                exception.getClass().getSimpleName());
        return responseWriter.write(exchange, status, errorCode);
    }

    private static boolean isDependencyFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof NotFoundException
                    || current instanceof ConnectException
                    || current instanceof TimeoutException
                    || current instanceof WebClientRequestException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
