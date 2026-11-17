package com.amano.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Guard for the shared reset: every user table of the current schema must be
 * listed in {@link AbstractIntegrationIT#TRUNCATED_TABLES}. Without this check a
 * future migration could add a table that is never truncated and silently leak
 * rows across test classes.
 */
class TruncateCoverageIT extends AbstractIntegrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void everyApplicationTableIsCoveredByTheSharedTruncate() {
        List<String> actual = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """, String.class);

        Set<String> missingFromTruncate = new LinkedHashSet<>(actual);
        missingFromTruncate.removeAll(AbstractIntegrationIT.TRUNCATED_TABLES);
        Set<String> listedButAbsent = new LinkedHashSet<>(AbstractIntegrationIT.TRUNCATED_TABLES);
        listedButAbsent.removeAll(actual);

        assertThat(missingFromTruncate)
                .as("Tablas de la base que no están en TRUNCATED_TABLES (se filtrarían entre clases)")
                .isEmpty();
        assertThat(listedButAbsent)
                .as("Tablas listadas en TRUNCATED_TABLES que ya no existen en el esquema")
                .isEmpty();
    }
}
