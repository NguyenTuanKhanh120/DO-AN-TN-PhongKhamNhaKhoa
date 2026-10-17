package com.dentalclinic.user.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAccountLockRepository {

    private static final long ADMIN_STATUS_LOCK_KEY = 4_919_420_660_221_761_860L;

    private final JdbcTemplate jdbcTemplate;

    public AdminAccountLockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquireTransactionLock() {
        jdbcTemplate.execute("select pg_advisory_xact_lock(" + ADMIN_STATUS_LOCK_KEY + ")");
    }
}
