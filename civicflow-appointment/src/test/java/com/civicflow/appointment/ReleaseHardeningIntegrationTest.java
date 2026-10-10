package com.civicflow.appointment;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.civicflow.appointment.client.ResourceSlotClient;
import com.civicflow.appointment.client.ResourceSlotSnapshot;
import com.civicflow.appointment.event.*;
import com.civicflow.appointment.service.*;
import com.civicflow.appointment.support.RedisStockRepository;
import com.civicflow.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.MountableFile;

/** Release gate: real MySQL, Redis, RabbitMQ and HTTP; only resource snapshots are stubbed. */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReleaseHardeningIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379);

    @Container
    static final GenericContainer<?> RABBIT =
            new GenericContainer<>("rabbitmq:4.1.8-management-alpine")
                    .withEnv("RABBITMQ_DEFAULT_USER", "release")
                    .withEnv("RABBITMQ_DEFAULT_PASS", "isolated-test-only")
                    .withExposedPorts(5672)
                    .waitingFor(
                            org.testcontainers.containers.wait.strategy.Wait.forLogMessage(
                                    ".*Server startup complete.*", 1));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "spring.datasource.url",
                () ->
                        MYSQL.getJdbcUrl()
                                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword);
        p.add("spring.flyway.enabled", () -> true);
        p.add("spring.sql.init.mode", () -> "never");
        p.add("spring.data.redis.host", REDIS::getHost);
        p.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        p.add("spring.data.redis.timeout", () -> "500ms");
        p.add("spring.rabbitmq.host", RABBIT::getHost);
        p.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        p.add("spring.rabbitmq.username", () -> "release");
        p.add("spring.rabbitmq.password", () -> "isolated-test-only");
        p.add("spring.rabbitmq.connection-timeout", () -> "1s");
        p.add("civicflow.appointment.reservation.publish-confirm-timeout", () -> "500ms");
    }

    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired ReservationWorkflowService workflow;
    @Autowired AppointmentCreationService creation;
    @Autowired ReservationCreateListener consumer;
    @Autowired ReservationEventPublisher publisher;
    @Autowired RedisStockRepository stock;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Autowired AppointmentStateService states;
    @Autowired MockMvc mvc;
    @MockitoBean ResourceSlotClient resource;
    @MockitoBean JwtDecoder jwtDecoder;
    @LocalServerPort int port;
    RSAKey key;
    final Map<Long, ResourceSlotSnapshot> snapshots = new ConcurrentHashMap<>();

    @BeforeEach
    void prepare() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("release-test").generate();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        when(jwtDecoder.decode(anyString()))
                .thenAnswer(call -> decoder.decode(call.getArgument(0)));
        when(resource.getSnapshot(anyLong()))
                .thenAnswer(
                        call -> ApiResponse.success(snapshots.get(call.getArgument(0)), "test"));
        listeners.getListenerContainers().forEach(c -> c.start());
    }

    ResourceSlotSnapshot slot(long id, int quota) {
        return slot(id, quota, LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1));
    }

    ResourceSlotSnapshot slot(long id, int quota, LocalDate serviceDate) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var s =
                new ResourceSlotSnapshot(
                        "" + id,
                        "20",
                        "" + id,
                        "Synthetic",
                        "Release gate",
                        serviceDate,
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0),
                        quota,
                        now.minusSeconds(60),
                        now.minusSeconds(60),
                        now.plusSeconds(172800),
                        now.plusSeconds(172800),
                        "OPEN",
                        1);
        snapshots.put(id, s);
        assertEquals(
                0,
                stock.initialize(
                                s.toStockSnapshot(), now.minusSeconds(120), now.plusSeconds(345600))
                        .code());
        return s;
    }

    String token(long user, boolean expired) throws Exception {
        Instant now = Instant.now();
        var claims =
                new JWTClaimsSet.Builder()
                        .subject("" + user)
                        .issueTime(Date.from(now.minusSeconds(300)))
                        .expirationTime(
                                Date.from(expired ? now.minusSeconds(120) : now.plusSeconds(900)))
                        .claim("roles", List.of("USER"))
                        .build();
        SignedJWT signed =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                        claims);
        signed.sign(new RSASSASigner(key));
        return signed.serialize();
    }

    int count(String sql, Object... args) {
        return db.queryForObject(sql, Integer.class, args);
    }

    String text(String sql, Object... args) {
        return db.queryForObject(sql, String.class, args);
    }

    void settled(long slot, int expected) {
        await().atMost(Duration.ofSeconds(45))
                .untilAsserted(
                        () ->
                                assertEquals(
                                        expected,
                                        count(
                                                "SELECT COUNT(*) FROM appointment_order WHERE slot_id=?",
                                                slot)));
    }

    ReservationRequestedEvent event(String reservation) throws Exception {
        return json.readValue(
                text(
                        "SELECT event_json FROM appointment_reservation_request WHERE reservation_id=?",
                        reservation),
                ReservationRequestedEvent.class);
    }

    @Test
    void thousandUsersAndHundredSameUserRequests() throws Exception {
        load(41001, 100, 1000, false);
        load(41002, 1, 100, true);
        load(41003, 1, 100, true);
    }

    void load(long id, int quota, int users, boolean sameUser) throws Exception {
        var snapshot = slot(id, quota);
        List<Map<String, String>> credentials = new ArrayList<>();
        String sameToken = token(id, false);
        for (int i = 0; i < users; i++)
            credentials.add(
                    Map.of(
                            "token",
                            sameUser ? sameToken : token(id * 10000 + i, false),
                            "key",
                            "load-" + id + "-" + (id == 41003 ? 0 : i)));
        Path output = Path.of("target", "release-hardening").toAbsolutePath();
        Files.createDirectories(output);
        Path fixture = Files.createTempFile("release-fixture-", ".json");
        Files.writeString(
                fixture,
                json.writeValueAsString(
                        Map.of(
                                "base",
                                "http://host.docker.internal:" + port,
                                "slotId",
                                "" + id,
                                "quota",
                                quota,
                                "users",
                                credentials,
                                "expectedAccepted",
                                id == 41003 ? users : quota)));
        List<Long> samples = new CopyOnWriteArrayList<>();
        var monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(
                () ->
                        samples.add(
                                stock.reconciliationSnapshot(snapshot.toStockSnapshot())
                                        .remaining()),
                0,
                20,
                TimeUnit.MILLISECONDS);
        try (var k6 =
                new GenericContainer<>("grafana/k6:0.52.0")
                        .withCreateContainerCmdModifier(c -> c.withEntrypoint("/bin/sh"))
                        .withCommand("-c", "sleep 300")
                        .withCopyFileToContainer(
                                MountableFile.forHostPath(fixture), "/tmp/fixture.json")
                        .withCopyFileToContainer(
                                MountableFile.forClasspathResource("load/reservation.js"),
                                "/tmp/load.js")) {
            k6.start();
            var result =
                    k6.execInContainer(
                            "k6", "run", "--summary-export=/tmp/summary.json", "/tmp/load.js");
            String summary = k6.execInContainer("cat", "/tmp/summary.json").getStdout();
            Files.writeString(output.resolve("k6-" + id + ".json"), summary);
            Files.writeString(
                    output.resolve("k6-" + id + ".txt"), result.getStdout() + result.getStderr());
            assertEquals(0, result.getExitCode(), result.getStdout() + result.getStderr());
        } finally {
            monitor.shutdownNow();
            Files.deleteIfExists(fixture);
        }
        settled(id, quota);
        assertEquals(
                quota,
                count(
                        "SELECT COUNT(*) FROM appointment_order WHERE slot_id=? AND status IN ('PENDING_CONFIRM','CONFIRMED','CHECKED_IN','SERVING')",
                        id));
        assertEquals(
                quota,
                count(
                        "SELECT COUNT(*) FROM appointment_reservation_request WHERE slot_id=? AND status='PERSISTED'",
                        id));
        assertEquals(quota, count("SELECT COUNT(*) FROM active_booking_guard WHERE item_id=?", id));
        assertEquals(
                quota,
                count("SELECT COUNT(DISTINCT user_id) FROM appointment_order WHERE slot_id=?", id));
        assertEquals(0, stock.reconciliationSnapshot(snapshot.toStockSnapshot()).remaining());
        assertFalse(samples.isEmpty());
        assertTrue(samples.stream().allMatch(v -> v >= 0 && v <= quota));
        Files.writeString(
                output.resolve("facts-" + id + ".json"),
                json.writeValueAsString(
                        Map.of(
                                "users",
                                users,
                                "quota",
                                quota,
                                "orders",
                                quota,
                                "minimumStock",
                                Collections.min(samples),
                                "samples",
                                samples.size(),
                                "remaining",
                                0)));
    }

    @Test
    void duplicateDeliveryConsumerRestartAckLossAndPoisonDlq() throws Exception {
        var s = slot(42001, 3);
        listeners.stop();
        var reservation =
                workflow.create(42001, 42001, "delivery", "trace").response().reservationId();
        var e = event(reservation);
        // Commit the real business transaction, then simulate a lost consumer ACK by closing the
        // channel.
        var connection = rabbit.getConnectionFactory().createConnection();
        var channel = connection.createChannel(false);
        var delivery = channel.basicGet(AppointmentMessaging.RESERVATION_CREATE_QUEUE, false);
        assertNotNull(delivery);
        creation.create(e, ReservationEventHash.compute(e));
        channel.close();
        connection.close();
        listeners.start();
        assertEquals(
                ReservationEventPublisher.Outcome.ACKNOWLEDGED, publisher.publish(e).outcome());
        settled(42001, 1);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertEquals(
                                        0,
                                        stock.reconciliationSnapshot(s.toStockSnapshot())
                                                .pendingCount()));
        assertEquals(
                1,
                count("SELECT COUNT(*) FROM message_consume_record WHERE event_id=?", e.eventId()));
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id=(SELECT CAST(id AS CHAR) FROM appointment_order WHERE reservation_id=?)",
                        reservation));
        assertEquals(2, stock.reconciliationSnapshot(s.toStockSnapshot()).remaining());
        rabbit.send(
                AppointmentMessaging.APPOINTMENT_EXCHANGE,
                AppointmentMessaging.RESERVATION_REQUESTED_ROUTING_KEY,
                new Message(
                        "{invalid-release-probe".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        new MessageProperties()));
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertNotNull(
                                        rabbit.receive(
                                                AppointmentMessaging.RESERVATION_CREATE_DLQ)));
    }

    @Test
    void brokerOutageRetainsReservationThenRecovers() throws Exception {
        var s = slot(43001, 1);
        assertEquals(0, RABBIT.execInContainer("rabbitmqctl", "stop_app").getExitCode());
        String reservation;
        try {
            var result = workflow.create(43001, 43001, "broker-down", "trace");
            assertTrue(result.dependencyUnavailable());
            reservation = result.response().reservationId();
            assertEquals(
                    "PUBLISH_UNKNOWN",
                    text(
                            "SELECT status FROM appointment_reservation_request WHERE reservation_id=?",
                            reservation));
            assertEquals(0, stock.reconciliationSnapshot(s.toStockSnapshot()).remaining());
            assertEquals(
                    0,
                    count(
                            "SELECT COUNT(*) FROM stock_release_record WHERE reservation_id=?",
                            reservation));
        } finally {
            assertEquals(0, RABBIT.execInContainer("rabbitmqctl", "start_app").getExitCode());
        }
        long request =
                db.queryForObject(
                        "SELECT id FROM appointment_reservation_request WHERE reservation_id=?",
                        Long.class,
                        reservation);
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> {
                            workflow.recover(request);
                            settled(43001, 1);
                        });
        assertEquals(0, stock.reconciliationSnapshot(s.toStockSnapshot()).remaining());
    }

    @Test
    void redisOutageFailsClosedAndCancellationReleaseRecovers() throws Exception {
        var s = slot(44001, 2);
        String reservation =
                workflow.create(44001, 44001, "redis-order", "trace").response().reservationId();
        settled(44001, 1);
        long order =
                db.queryForObject(
                        "SELECT id FROM appointment_order WHERE reservation_id=?",
                        Long.class,
                        reservation);
        var docker = REDIS.getDockerClient();
        docker.pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            mvc.perform(
                            post("/api/v1/user/appointments/reservations")
                                    .header("Authorization", "Bearer " + token(44002, false))
                                    .header("Idempotency-Key", "redis-down")
                                    .contentType("application/json")
                                    .content("{\"slotId\":44001}"))
                    .andExpect(status().isServiceUnavailable());
            states.cancel(44001, order, 0, "synthetic", "redis-cancel", "trace");
            assertEquals(
                    "CANCELLED", text("SELECT status FROM appointment_order WHERE id=?", order));
            assertEquals(
                    "FAILED",
                    text(
                            "SELECT status FROM stock_release_record WHERE reservation_id=?",
                            reservation));
        } finally {
            docker.unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
        var request = creation.findRequest(reservation);
        // Same durable release service used by the scheduled scanner, after dependency recovery.
        release.release(request, com.civicflow.appointment.enums.StockReleaseReason.USER_CANCELLED);
        release.release(request, com.civicflow.appointment.enums.StockReleaseReason.USER_CANCELLED);
        // A timed-out Redis command may still execute when the server resumes. Its CREATED
        // request must recover the same reservation, not be treated as a proven failed debit.
        long pending =
                db.queryForObject(
                        "SELECT id FROM appointment_reservation_request WHERE user_id=44002",
                        Long.class);
        workflow.recover(pending);
        settled(44001, 2);
        assertEquals(1, stock.reconciliationSnapshot(s.toStockSnapshot()).remaining());
        assertEquals(1, count("SELECT COUNT(*) FROM active_booking_guard WHERE item_id=44001"));
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM stock_release_record WHERE reservation_id=? AND status='SUCCEEDED'",
                        reservation));
    }

    @Autowired StockReleaseService release;
    @Autowired CheckInTokenService checkIn;

    @Test
    void realQrNonceRotationOwnershipAndReplayHaveOneClaim() throws Exception {
        slot(49001, 1, LocalDate.now(ZoneId.of("Asia/Shanghai")));
        String reservation =
                workflow.create(49001, 49001, "qr-probe", "trace").response().reservationId();
        settled(49001, 1);
        long order =
                db.queryForObject(
                        "SELECT id FROM appointment_order WHERE reservation_id=?",
                        Long.class,
                        reservation);
        states.confirm(49001, order, 0, "qr-confirm", "trace");
        String old = checkIn.issue(49001, order, 20).token();
        String current = checkIn.issue(49001, order, 20).token();
        assertThrows(
                com.civicflow.common.exception.BusinessException.class,
                () -> checkIn.claim(old, 49001, 20, UUID.randomUUID().toString(), "trace"));
        assertThrows(
                com.civicflow.common.exception.BusinessException.class,
                () -> checkIn.claim(current, 49002, 20, UUID.randomUUID().toString(), "trace"));
        assertThrows(
                com.civicflow.common.exception.BusinessException.class,
                () -> checkIn.claim(current, 49001, 21, UUID.randomUUID().toString(), "trace"));
        var first = checkIn.claim(current, 49001, 20, UUID.randomUUID().toString(), "trace");
        var pool = Executors.newFixedThreadPool(20);
        try {
            List<Future<String>> replays = new ArrayList<>();
            for (int i = 0; i < 100; i++)
                replays.add(
                        pool.submit(
                                () ->
                                        checkIn.claim(
                                                        current,
                                                        49001,
                                                        20,
                                                        UUID.randomUUID().toString(),
                                                        "trace")
                                                .claimId()));
            for (var replay : replays)
                assertEquals(first.claimId(), replay.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM appointment_operation_log WHERE appointment_id=? AND operation='CHECK_IN'",
                        order));
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id=? AND event_type='appointment.check-in.claimed'",
                        "" + order));
    }

    @Test
    void newServiceProcessRecoversPersistedPendingRequest() throws Exception {
        var s = slot(48001, 1);
        listeners.stop();
        // A process died after storing CREATED: durable snapshot exists, no Redis/MQ effects yet.
        var snapshot = s;
        long id = 48001;
        String reservation = UUID.randomUUID().toString();
        db.update(
                "INSERT INTO appointment_reservation_request "
                        + "(id,reservation_id,user_id,idempotency_key_hash,payload_hash,slot_id,outlet_id,item_id,"
                        + "service_date,slot_start_time,slot_end_time,outlet_name_snapshot,item_name_snapshot,"
                        + "total_quota,release_at,close_at,slot_status,slot_config_version,status,trace_id,next_recovery_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'CREATED','restart',CURRENT_TIMESTAMP(3))",
                id,
                reservation,
                id,
                com.civicflow.appointment.support.Digests.sha256("restart"),
                com.civicflow.appointment.support.Digests.sha256("slotId=" + id),
                id,
                20,
                id,
                snapshot.serviceDate(),
                snapshot.startTime(),
                snapshot.endTime(),
                snapshot.outletName(),
                snapshot.itemName(),
                1,
                java.sql.Timestamp.from(snapshot.releaseAt()),
                java.sql.Timestamp.from(snapshot.closeAt()),
                "OPEN",
                1);
        Path output = Path.of("target", "release-hardening").toAbsolutePath();
        Files.createDirectories(output);
        List<String> args =
                List.of(
                        "-cp",
                        System.getProperty("java.class.path"),
                        AppointmentApplication.class.getName(),
                        "--spring.profiles.active=test",
                        "--server.port=0",
                        "--spring.sql.init.mode=never",
                        "--spring.flyway.enabled=true",
                        "--spring.datasource.url="
                                + MYSQL.getJdbcUrl()
                                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
                        "--spring.datasource.username=" + MYSQL.getUsername(),
                        "--spring.datasource.password=" + MYSQL.getPassword(),
                        "--spring.data.redis.host=" + REDIS.getHost(),
                        "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                        "--spring.rabbitmq.host=" + RABBIT.getHost(),
                        "--spring.rabbitmq.port=" + RABBIT.getMappedPort(5672),
                        "--spring.rabbitmq.username=release",
                        "--spring.rabbitmq.password=isolated-test-only",
                        "--spring.rabbitmq.listener.simple.auto-startup=true",
                        "--civicflow.appointment.recovery.enabled=true",
                        "--civicflow.appointment.recovery.fixed-delay=1s");
        Path argfile = Files.createTempFile("release-restart-", ".args");
        Files.writeString(
                argfile,
                args.stream()
                        .map(a -> "\"" + a.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                        .collect(java.util.stream.Collectors.joining("\n")));
        Process process = null;
        try {
            process =
                    new ProcessBuilder(
                                    Path.of(System.getProperty("java.home"), "bin", "java")
                                            .toString(),
                                    "@" + argfile)
                            .redirectErrorStream(true)
                            .redirectOutput(output.resolve("restarted-service.log").toFile())
                            .start();
            Process started = process;
            await().atMost(Duration.ofSeconds(75))
                    .untilAsserted(
                            () -> {
                                assertTrue(
                                        started.isAlive(),
                                        "Restarted process exited; inspect restarted-service.log");
                                assertEquals(
                                        "PERSISTED",
                                        text(
                                                "SELECT status FROM appointment_reservation_request WHERE id=?",
                                                id));
                                assertEquals(
                                        1,
                                        count(
                                                "SELECT COUNT(*) FROM appointment_order WHERE slot_id=?",
                                                id));
                                assertEquals(
                                        0,
                                        stock.reconciliationSnapshot(s.toStockSnapshot())
                                                .remaining());
                            });
            Files.writeString(
                    output.resolve("restart.json"),
                    "{\"freshJvmRecovery\":true,\"orders\":1,\"remaining\":0}");
        } finally {
            if (process != null) {
                process.destroy();
                if (!process.waitFor(15, TimeUnit.SECONDS))
                    process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(argfile);
            listeners.start();
        }
    }

    @Autowired com.civicflow.appointment.config.AppointmentProperties configuration;

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(
            org.springframework.boot.test.system.OutputCaptureExtension.class)
    void rejectedMessageMustNotLeakBodyOrHeaders(
            org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("Authorization", "Bearer synthetic-header-secret-927");
        rabbit.send(
                AppointmentMessaging.APPOINTMENT_EXCHANGE,
                AppointmentMessaging.RESERVATION_REQUESTED_ROUTING_KEY,
                new Message(
                        "{\"token\":\"synthetic-body-secret-927\"}"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        properties));
        await().atMost(Duration.ofSeconds(15))
                .until(
                        () ->
                                output.getAll().contains("Retries exhausted")
                                        || output.getAll().contains("Message rejected"));
        assertFalse(
                output.getAll().contains("synthetic-body-secret-927"),
                "Rejected message body leaked to application log");
        assertFalse(
                output.getAll().contains("synthetic-header-secret-927"),
                "Rejected message header leaked to application log");
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertNotNull(
                                        rabbit.receive(
                                                AppointmentMessaging.RESERVATION_CREATE_DLQ)));
    }

    @Test
    void lostPublisherConfirmKeepsStockAndRedeliversSameEvent() throws Exception {
        var s = slot(46001, 1);
        listeners.stop();
        String reservation =
                workflow.create(46001, 46001, "confirm-lost", "trace").response().reservationId();
        var e = event(reservation);
        RabbitTemplate lostConfirm = mock(RabbitTemplate.class);
        doAnswer(
                        call -> {
                            // Broker receives the persistent message; only the publisher's
                            // completion signal is lost.
                            rabbit.send(
                                    call.getArgument(0),
                                    call.getArgument(1),
                                    call.getArgument(2),
                                    new org.springframework.amqp.rabbit.connection.CorrelationData(
                                            UUID.randomUUID().toString()));
                            return null;
                        })
                .when(lostConfirm)
                .send(
                        anyString(),
                        anyString(),
                        any(Message.class),
                        any(org.springframework.amqp.rabbit.connection.CorrelationData.class));
        var isolatedPublisher =
                new RabbitReservationEventPublisher(lostConfirm, json, configuration);
        assertEquals(
                ReservationEventPublisher.Outcome.UNKNOWN, isolatedPublisher.publish(e).outcome());
        assertEquals(0, stock.reconciliationSnapshot(s.toStockSnapshot()).remaining());
        assertEquals(
                0,
                count(
                        "SELECT COUNT(*) FROM stock_release_record WHERE reservation_id=?",
                        reservation));
        listeners.start();
        settled(46001, 1);
        assertEquals(
                ReservationEventPublisher.Outcome.ACKNOWLEDGED, publisher.publish(e).outcome());
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertEquals(
                                        0,
                                        stock.reconciliationSnapshot(s.toStockSnapshot())
                                                .pendingCount()));
        assertEquals(
                1,
                count("SELECT COUNT(*) FROM message_consume_record WHERE event_id=?", reservation));
    }

    @Test
    void earlyAndRepeatedTimeoutUsesRealTtlDlxAndSingleRelease() throws Exception {
        var s = slot(47001, 1);
        Duration previous = configuration.getReservation().getConfirmDeadline();
        String reservation;
        try {
            // Shorten only this isolated fixture's deadline; production remains five minutes.
            configuration.getReservation().setConfirmDeadline(Duration.ofSeconds(3));
            reservation =
                    workflow.create(47001, 47001, "early-timeout", "trace")
                            .response()
                            .reservationId();
            settled(47001, 1);
        } finally {
            configuration.getReservation().setConfirmDeadline(previous);
        }
        String payload =
                text(
                        "SELECT payload_json FROM outbox_event WHERE aggregate_id=(SELECT CAST(id AS CHAR) FROM appointment_order WHERE reservation_id=?)",
                        reservation);
        Message message =
                new Message(
                        payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        new MessageProperties());
        rabbit.send(
                AppointmentMessaging.TIMEOUT_EXCHANGE,
                AppointmentMessaging.TIMEOUT_CHECK_ROUTING_KEY,
                message);
        rabbit.send(
                AppointmentMessaging.TIMEOUT_EXCHANGE,
                AppointmentMessaging.TIMEOUT_CHECK_ROUTING_KEY,
                message);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> {
                            assertEquals(
                                    "EXPIRED",
                                    text(
                                            "SELECT status FROM appointment_order WHERE reservation_id=?",
                                            reservation));
                            assertEquals(
                                    1,
                                    stock.reconciliationSnapshot(s.toStockSnapshot()).remaining());
                        });
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM stock_release_record WHERE reservation_id=? AND status='SUCCEEDED'",
                        reservation));
        assertEquals(0, count("SELECT COUNT(*) FROM active_booking_guard WHERE item_id=47001"));
        // A late duplicate reservation after timeout must not resurrect an order or consume stock.
        publisher.publish(event(reservation));
        assertEquals(
                "EXPIRED",
                text("SELECT status FROM appointment_order WHERE reservation_id=?", reservation));
    }

    @Test
    void authenticatedInputAndOwnershipProbes() throws Exception {
        slot(45001, 2);
        slot(45002, 2);
        String user = token(45001, false), other = token(45002, false);
        String reservation =
                workflow.create(45001, 45001, "ownership", "trace").response().reservationId();
        settled(45001, 1);
        long order =
                db.queryForObject(
                        "SELECT id FROM appointment_order WHERE reservation_id=?",
                        Long.class,
                        reservation);
        String base = "/api/v1/user/appointments";
        mvc.perform(get(base).header("Authorization", "Bearer " + token(45001, true)))
                .andExpect(status().isUnauthorized());
        String[] parts = user.split("\\.");
        String tampered =
                parts[0]
                        + "."
                        + Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(
                                        "{\"sub\":\"45002\",\"roles\":[\"ADMIN\"]}".getBytes())
                        + "."
                        + parts[2];
        mvc.perform(get(base).header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get(base + "/" + order)
                                .header("Authorization", "Bearer " + other)
                                .header("X-User-Id", "45001"))
                .andExpect(status().isNotFound());
        for (String action : List.of("confirm", "cancel"))
            mvc.perform(
                            post(base + "/" + order + "/" + action)
                                    .header("Authorization", "Bearer " + other)
                                    .header("Idempotency-Key", "forbidden-" + action)
                                    .contentType("application/json")
                                    .content("{\"version\":0,\"reason\":\"test\"}"))
                    .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/admin/reconciliations")
                                .header("Idempotency-Key", "denied-admin")
                                .contentType("application/json")
                                .content("{\"slotId\":45001,\"repair\":false}")
                                .header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
        for (String query :
                List.of(
                        "?size=1000000",
                        "?status=NOT_A_STATE",
                        "?status=CONFIRMED%27%20OR%201=1--"))
            mvc.perform(
                            get(java.net.URI.create(base + query))
                                    .header("Authorization", "Bearer " + user))
                    .andExpect(status().isBadRequest());
        mvc.perform(
                        post(base + "/reservations")
                                .header("Authorization", "Bearer " + user)
                                .header("Idempotency-Key", "ownership")
                                .contentType("application/json")
                                .content("{\"slotId\":45002}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_409_IDEMPOTENCY_CONFLICT"));
        assertEquals(
                1, count("SELECT COUNT(*) FROM appointment_order WHERE slot_id IN (45001,45002)"));
    }
}
