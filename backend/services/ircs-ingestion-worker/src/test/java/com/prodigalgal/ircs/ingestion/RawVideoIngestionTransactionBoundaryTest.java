package com.prodigalgal.ircs.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.ingestion.IngestionItem;
import com.prodigalgal.ircs.contracts.ingestion.IngestionVideoDTO;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RawVideoIngestionTransactionBoundaryTest {

    @Test
    void singleConnectionPoolIsFreeBeforeAnyDownstreamNetworkCall() {
        try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            var pool = context.getBean(HikariDataSource.class);
            var jdbc = new JdbcTemplate(pool);
            var repository = context.getBean(RawVideoIngestionRepository.class);
            var publisher = context.getBean(IngestionPublisher.class);
            var video = mock(IngestionVideoDTO.class);
            when(video.sourceHash()).thenReturn("source");
            when(video.dataHash()).thenReturn("version");
            when(video.playlists()).thenReturn(List.of());
            when(repository.findStateBySourceHash("source")).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
                return Optional.empty();
            });
            when(repository.upsertRawVideo(video, null)).thenReturn(UUID.randomUUID());
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
                assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
                return null;
            }).when(publisher).publishPlaylistSync(any());

            context.getBean(RawVideoIngestionService.class).ingest(new IngestionItem(video, false));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import({RawVideoIngestionTransactions.class, RawVideoIngestionService.class})
    static class TestConfiguration {
        @Bean(destroyMethod = "close")
        HikariDataSource dataSource() {
            var config = new HikariConfig();
            config.setJdbcUrl("jdbc:h2:mem:ingest-boundary-" + UUID.randomUUID());
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(250);
            return new HikariDataSource(config);
        }

        @Bean
        DataSourceTransactionManager transactionManager(HikariDataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        RawVideoIngestionRepository repository() {
            return mock(RawVideoIngestionRepository.class);
        }

        @Bean
        IngestionPublisher publisher() {
            return mock(IngestionPublisher.class);
        }
    }
}
