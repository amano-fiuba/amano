package com.amano.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.amano.backend.support.AbstractIntegrationIT;

class BackendApplicationIT extends AbstractIntegrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndDatabaseResponds() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

        assertThat(result).isEqualTo(1);
    }
}
