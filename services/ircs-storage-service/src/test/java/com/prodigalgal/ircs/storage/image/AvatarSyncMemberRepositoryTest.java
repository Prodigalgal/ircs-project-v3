package com.prodigalgal.ircs.storage.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class AvatarSyncMemberRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void claimLocalAvatarMemberIdsUsesRowLeaseAndSkipLocked() {
        UUID memberId = UUID.randomUUID();
        when(jdbcTemplate.query(
                        argThat(sql -> sql != null
                                && sql.contains("update members")
                                && sql.contains("avatar_sync_claimed_by")
                                && sql.contains("for update skip locked")
                                && sql.contains("returning m.id")),
                        any(PreparedStatementSetter.class),
                        any(RowMapper.class)))
                .thenReturn(List.of(memberId));
        AvatarSyncMemberRepository repository = new AvatarSyncMemberRepository(jdbcTemplate);

        List<UUID> claimed = repository.claimLocalAvatarMemberIds(
                "/assets",
                10,
                "storage-pod-1",
                Duration.ofMinutes(5));

        assertThat(claimed).containsExactly(memberId);
    }
}
