package com.civicflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GatewayApplicationTest.TestJwtConfiguration.class)
class GatewayApplicationTest {

    @Autowired private WebTestClient webTestClient;
    @Autowired private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void startsAndExposesHealthEndpoint() {
        webTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void protectsNonWhitelistedEndpoints() {
        webTestClient
                .get()
                .uri("/api/v1/user/me")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("AUTH_401_UNAUTHORIZED");
    }

    @Test
    void rejectsRoleMismatch() {
        webTestClient
                .get()
                .uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("AUTH_403_FORBIDDEN");
    }

    @Test
    void loginIsWhitelistedAndFailsClosedWhenAuthHasNoInstance() {
        webTestClient
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"loginName\":\"someone\",\"password\":\"not-logged\"}")
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("COMMON_503_DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void rejectsCredentialedCorsFromUnknownOrigin() {
        webTestClient
                .options()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectHeader()
                .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void configuresOnlyTheFourPublicServiceRoutes() {
        Map<String, URI> routes =
                routeDefinitionLocator
                        .getRouteDefinitions()
                        .collectMap(RouteDefinition::getId, RouteDefinition::getUri)
                        .block();

        assertThat(routes)
                .containsEntry("civicflow-auth", URI.create("lb://civicflow-auth"))
                .containsEntry("civicflow-resource", URI.create("lb://civicflow-resource"))
                .containsEntry("civicflow-appointment", URI.create("lb://civicflow-appointment"))
                .containsEntry("civicflow-queue", URI.create("lb://civicflow-queue"));
        assertThat(routes).hasSize(4);
        assertThat(
                        routeDefinitionLocator.getRouteDefinitions().collectList().block().stream()
                                .filter(route -> "civicflow-queue".equals(route.getId()))
                                .flatMap(route -> route.getPredicates().stream())
                                .flatMap(predicate -> predicate.getArgs().values().stream()))
                .anyMatch(value -> value.contains("/api/v1/staff/check-ins"));
        assertThat(
                        routeDefinitionLocator.getRouteDefinitions().collectList().block().stream()
                                .filter(route -> "civicflow-appointment".equals(route.getId()))
                                .flatMap(route -> route.getPredicates().stream())
                                .flatMap(predicate -> predicate.getArgs().values().stream()))
                .anyMatch(value -> value.contains("/api/v1/admin/stock/slots/**"));
        assertThat(
                        routeDefinitionLocator.getRouteDefinitions().collectList().block().stream()
                                .flatMap(route -> route.getPredicates().stream())
                                .flatMap(predicate -> predicate.getArgs().values().stream()))
                .noneMatch(value -> value.contains("/internal/"));
    }

    @TestConfiguration
    static class TestJwtConfiguration {
        @Bean
        @Primary
        ReactiveJwtDecoder testJwtDecoder() {
            return token -> {
                Instant now = Instant.now();
                Jwt jwt =
                        Jwt.withTokenValue(token)
                                .header("alg", "RS256")
                                .header("kid", "test-key")
                                .issuer("https://auth.civicflow.local")
                                .subject("1001")
                                .audience(List.of("civicflow-api"))
                                .issuedAt(now.minusSeconds(1))
                                .notBefore(now.minusSeconds(1))
                                .expiresAt(now.plusSeconds(60))
                                .claim("jti", "test-jti")
                                .claim("roles", List.of("USER"))
                                .claim("tokenVersion", 1)
                                .claim("kid", "test-key")
                                .build();
                return Mono.just(jwt);
            };
        }
    }
}
