package com.amano.backend.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.currency.CachingCurrencyCatalogProvider;
import com.amano.backend.groupexpense.exchange.BcraExchangeRateClient;
import com.amano.backend.groupexpense.exchange.CachingExchangeRateProvider;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

/**
 * Shared integration-test base: a single PostgreSQL Testcontainer and a single
 * in-process GreenMail SMTP server started once per JVM, with a uniform
 * {@code @SpringBootTest} + {@code @AutoConfigureMockMvc} configuration so all
 * compatible IT classes reuse the same cached Spring {@code ApplicationContext}.
 *
 * <p>Before each test class runs, {@link #resetSharedState} drains every async
 * executor seen so far (one per cached context), purges GreenMail, clears the
 * singleton caches, truncates all application tables (restarting sequences) and
 * purges GreenMail again, so no mail or row from a previous class can leak in.
 *
 * <p>All five production cron jobs are disabled for tests (their methods are
 * invoked directly by the ITs that cover them), so a wall-clock coincidence
 * cannot mutate the database in the middle of a scenario.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c",
        "spring.mail.host=localhost",
        // Neutralize any MAIL_USERNAME/MAIL_PASSWORD exported in the developer shell:
        // GreenMail runs without AUTH, so inherited real credentials make every mail IT fail.
        "spring.mail.username=",
        "spring.mail.password=",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "spring.mail.properties.mail.smtp.auth=false",
        "app.base-url=http://localhost:5173",
        "verification.cron-cleanup=-",
        "recurrence.cron=-",
        "group-recurrence.cron=-",
        "overdue.cron=-",
        "reminders.cron=-",
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.minimum-idle=1"
})
@AutoConfigureMockMvc
public abstract class AbstractIntegrationIT {

    /** Timeout for the async drain: enough for local SMTP, short enough to diagnose. */
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

    /** Two consecutive idle observations separated by this pause define "quiescent". */
    private static final long QUIET_SAMPLE_MILLIS = 10;

    private static final int QUIET_SAMPLES = 2;

    private static final Duration NEGATIVE_MAIL_WINDOW = Duration.ofSeconds(1);

    @ServiceConnection
    protected static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    protected static final GreenMail greenMail = new GreenMail(
            new ServerSetup(0, "localhost", ServerSetup.PROTOCOL_SMTP));

    /**
     * The BCRA is never contacted from an IT: the conversion path is covered with
     * this double (the real client has its own unit tests). This keeps the suite
     * hermetic and avoids relying on the network.
     */
    @MockitoBean
    protected BcraExchangeRateClient bcraExchangeRateClient;

    /** Every executor discovered in a cached context, in discovery order. */
    private static final List<ExecutorHandle> KNOWN_EXECUTORS =
            Collections.synchronizedList(new ArrayList<>());

    /** Single source of truth for the reset; guarded by {@code TruncateCoverageIT}. */
    public static final List<String> TRUNCATED_TABLES = List.of(
            "notifications",
            "user_notification_preferences",
            "debt_whistles",
            "reminders",
            "payments",
            "debts",
            "group_expense_shares",
            "group_expenses",
            "group_activity",
            "group_photos",
            "group_permissions",
            "group_members",
            "simulated_profiles",
            "groups",
            "friendship_requests",
            "expenses",
            "incomes",
            "password_reset_tokens",
            "email_verification_tokens",
            "email_resend_limits",
            "user_avatars",
            "user_currencies",
            "users");

    private static final String TRUNCATE_SQL =
            "TRUNCATE TABLE " + String.join(", ", TRUNCATED_TABLES) + " RESTART IDENTITY CASCADE";

    static {
        postgres.start();
        greenMail.start();
        Runtime.getRuntime().addShutdownHook(new Thread(greenMail::stop));
    }

    @DynamicPropertySource
    static void sharedMailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
    }

    /**
     * Remembers the executor of every context that runs a test, so later class
     * boundaries can drain contexts that are still cached but no longer current.
     */
    @BeforeEach
    void rememberCurrentExecutors(ApplicationContext applicationContext, TestInfo testInfo) {
        registerExecutors(applicationContext, currentTestClass(testInfo));
    }

    @BeforeAll
    static void resetSharedState(
            @Autowired JdbcTemplate jdbcTemplate,
            @Autowired ApplicationContext applicationContext,
            TestInfo testInfo) throws Exception {
        String testClass = currentTestClass(testInfo);

        registerExecutors(applicationContext, testClass);
        awaitAsyncQuiescence(testClass);

        greenMail.purgeEmailFromAllMailboxes();
        clearSingletonCaches(applicationContext);
        truncateAll(jdbcTemplate, testClass);
        // Second purge: closes the window between the first purge and the TRUNCATE,
        // during which a late async delivery from the previous class could arrive.
        greenMail.purgeEmailFromAllMailboxes();
    }

    /**
     * Waits until every known executor has been idle for two consecutive
     * observations. Fails with a diagnostic instead of continuing silently.
     */
    private static void awaitAsyncQuiescence(String testClass) throws InterruptedException {
        List<ExecutorHandle> handles;
        synchronized (KNOWN_EXECUTORS) {
            handles = List.copyOf(KNOWN_EXECUTORS);
        }
        long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
        for (ExecutorHandle handle : handles) {
            ThreadPoolExecutor pool;
            try {
                pool = handle.executor().getThreadPoolExecutor();
            }
            catch (IllegalStateException neverInitialized) {
                continue; // no task can be running in an executor that never started
            }
            int lastActive = 0;
            int lastQueued = 0;
            int quietSamples = 0;
            while (System.nanoTime() < deadline) {
                lastActive = pool.getActiveCount();
                lastQueued = pool.getQueue().size();
                if (lastActive == 0 && lastQueued == 0) {
                    quietSamples++;
                    if (quietSamples >= QUIET_SAMPLES) {
                        break;
                    }
                }
                else {
                    quietSamples = 0;
                }
                Thread.sleep(QUIET_SAMPLE_MILLIS);
            }
            if (quietSamples < QUIET_SAMPLES) {
                throw new IllegalStateException("Las tareas @Async no terminaron en "
                        + DRAIN_TIMEOUT.toSeconds() + " s antes de " + testClass
                        + " (contexto=" + handle.contextId() + ", executor=" + handle.beanName()
                        + ", activas=" + lastActive + ", en cola=" + lastQueued + "). "
                        + "Un correo pendiente podría contaminar la siguiente prueba.");
            }
        }
    }

    private static void registerExecutors(ApplicationContext applicationContext, String testClass) {
        Map<String, ThreadPoolTaskExecutor> executors =
                applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class);
        if (executors.isEmpty()) {
            throw new IllegalStateException("No hay ningún ThreadPoolTaskExecutor en el contexto "
                    + applicationContext.getId() + " para " + testClass
                    + "; no se puede garantizar el drenaje de tareas @Async.");
        }
        executors.forEach((beanName, executor) -> {
            synchronized (KNOWN_EXECUTORS) {
                boolean alreadyKnown = KNOWN_EXECUTORS.stream()
                        .anyMatch(handle -> handle.executor() == executor);
                if (!alreadyKnown) {
                    KNOWN_EXECUTORS.add(new ExecutorHandle(applicationContext.getId(), beanName, executor));
                }
            }
        });
    }

    private static void clearSingletonCaches(ApplicationContext applicationContext) {
        applicationContext.getBeansOfType(CachingExchangeRateProvider.class).values()
                .forEach(CachingExchangeRateProvider::clear);
        applicationContext.getBeansOfType(CachingCurrencyCatalogProvider.class).values()
                .forEach(provider -> {
                    // The catalog cache has no production invalidation API; reset it
                    // from the test harness so no quote/catalog survives a class.
                    ReflectionTestUtils.setField(provider, "cached", null);
                    ReflectionTestUtils.setField(provider, "fetchedAt", null);
                });
    }

    private static void truncateAll(JdbcTemplate jdbcTemplate, String testClass) {
        try {
            jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
                truncateInTransaction(connection);
                return null;
            });
        }
        catch (RuntimeException failure) {
            throw new IllegalStateException("No se pudo limpiar la base de datos compartida antes de "
                    + testClass + ". Si el TRUNCATE se bloqueó, revisá transacciones residuales, "
                    + "hilos de concurrencia no unidos o jobs programados.", failure);
        }
    }

    private static void truncateInTransaction(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            // Fail fast with a clear PostgreSQL error instead of hanging the suite.
            statement.execute("SET LOCAL lock_timeout = '5s'");
            statement.execute("SET LOCAL statement_timeout = '30s'");
            statement.execute(TRUNCATE_SQL);
            connection.commit();
        }
        catch (SQLException failure) {
            connection.rollback();
            throw failure;
        }
        finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /**
     * Asserts that GreenMail receives no new message for the whole window. Call it
     * only after the expected messages have already arrived; it is a sustained
     * negative observation, not a single short sleep.
     */
    protected static void assertNoNewMessages(int expected, Duration window) throws InterruptedException {
        long deadline = System.nanoTime() + window.toNanos();
        while (System.nanoTime() < deadline) {
            int actual = greenMail.getReceivedMessages().length;
            if (actual != expected) {
                throw new AssertionError("Se esperaban exactamente " + expected
                        + " correo(s), pero llegó uno inesperado: total=" + actual);
            }
            Thread.sleep(10);
        }
        int actual = greenMail.getReceivedMessages().length;
        if (actual != expected) {
            throw new AssertionError("Se esperaban exactamente " + expected
                    + " correo(s), pero llegó uno inesperado: total=" + actual);
        }
    }

    protected static void assertNoNewMessages(int expected) throws InterruptedException {
        assertNoNewMessages(expected, NEGATIVE_MAIL_WINDOW);
    }

    private static String currentTestClass(TestInfo testInfo) {
        return testInfo.getTestClass().map(Class::getName).orElse("<clase desconocida>");
    }

    private record ExecutorHandle(String contextId, String beanName, ThreadPoolTaskExecutor executor) {
    }
}
