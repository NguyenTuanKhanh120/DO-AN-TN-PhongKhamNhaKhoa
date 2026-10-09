package com.dentalclinic.common.util;

import java.sql.SQLException;

public final class PostgreSqlErrorUtils {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private PostgreSqlErrorUtils() {
    }

    public static boolean isUniqueViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
