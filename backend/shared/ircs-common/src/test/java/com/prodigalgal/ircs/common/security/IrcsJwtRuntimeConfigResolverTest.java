package com.prodigalgal.ircs.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.config.RuntimeConfigValueSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

class IrcsJwtRuntimeConfigResolverTest {

    @Test
    void envOverridesFallback() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_IDENTITY_JWT_SECRET", "env-secret")
                .withProperty("APP_IDENTITY_JWT_IAT_FLOOR", "123");

        IrcsJwtRuntimeConfigResolver resolver = new IrcsJwtRuntimeConfigResolver(environment, null, null, null);

        assertThat(resolver.jwtSecret()).isEqualTo("env-secret");
        assertThat(resolver.iatFloorSeconds()).isEqualTo(123);
    }

    @Test
    void databaseValuesBorrowAndReturnManagedConnections() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getString(1)).thenReturn("pooled-secret", "456");

        IrcsJwtRuntimeConfigResolver resolver = new IrcsJwtRuntimeConfigResolver(new MockEnvironment(), dataSource);

        assertThat(resolver.jwtSecret()).isEqualTo("pooled-secret");
        assertThat(resolver.iatFloorSeconds()).isEqualTo(456);
        verify(dataSource, times(2)).getConnection();
        verify(connection, times(2)).close();
        verify(statement, times(2)).close();
        verify(rows, times(2)).close();
    }

    @Test
    void failedDatabaseLookupReturnsManagedConnection() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(new SQLException("lookup failed"));

        IrcsJwtRuntimeConfigResolver resolver = new IrcsJwtRuntimeConfigResolver(new MockEnvironment(), dataSource);

        assertThat(resolver.jwtSecret()).isEmpty();
        verify(connection).close();
        verify(statement).close();
    }

    @Test
    void cacheHitsDoNotBorrowDatabaseConnections() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        RuntimeConfigValueSource source = mock(RuntimeConfigValueSource.class);
        when(source.findValue("security.jwt.secret")).thenReturn(Optional.of("cached-secret"));
        when(source.findValue("security.jwt.iat-floor")).thenReturn(Optional.of("789"));
        IrcsJwtRuntimeConfigResolver resolver = new IrcsJwtRuntimeConfigResolver(new MockEnvironment(), dataSource, source);

        assertThat(resolver.jwtSecret()).isEqualTo("cached-secret");
        assertThat(resolver.iatFloorSeconds()).isEqualTo(789);
        verify(dataSource, never()).getConnection();
    }

    @Test
    void unavailableRevocationSourceDoesNotResetTheFloorOrRetryTheDatabase() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        RuntimeConfigValueSource source = mock(RuntimeConfigValueSource.class);
        when(source.findValue("security.jwt.iat-floor")).thenThrow(new IllegalStateException("pool unavailable"));
        IrcsJwtRuntimeConfigResolver resolver = new IrcsJwtRuntimeConfigResolver(new MockEnvironment(), dataSource, source);

        assertThatThrownBy(resolver::iatFloorSeconds).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
        verify(dataSource, never()).getConnection();
    }
}
