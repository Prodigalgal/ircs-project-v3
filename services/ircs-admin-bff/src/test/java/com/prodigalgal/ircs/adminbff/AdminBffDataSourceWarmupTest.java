package com.prodigalgal.ircs.adminbff;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class AdminBffDataSourceWarmupTest {

    @Test
    void opensAndValidatesConnectionWhenEnabled() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        AdminBffDataSourceWarmup warmup = new AdminBffDataSourceWarmup(dataSource);
        setEnabled(warmup, true);

        warmup.warmup();

        verify(dataSource).getConnection();
        verify(connection).isValid(2);
        verify(connection).close();
    }

    @Test
    void skipsConnectionWhenDisabled() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        AdminBffDataSourceWarmup warmup = new AdminBffDataSourceWarmup(dataSource);
        setEnabled(warmup, false);

        warmup.warmup();

        verify(dataSource, never()).getConnection();
    }

    private static void setEnabled(AdminBffDataSourceWarmup warmup, boolean enabled) throws Exception {
        Field field = AdminBffDataSourceWarmup.class.getDeclaredField("enabled");
        field.setAccessible(true);
        field.set(warmup, enabled);
    }
}
