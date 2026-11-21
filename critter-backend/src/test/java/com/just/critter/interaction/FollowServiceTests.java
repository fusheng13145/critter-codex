package com.just.critter.interaction;

import com.just.critter.interaction.repository.InteractionRepository;
import com.just.critter.interaction.service.FollowCommand;
import com.just.critter.interaction.service.FollowRecord;
import com.just.critter.interaction.service.FollowRemoval;
import com.just.critter.interaction.service.FollowService;
import com.just.critter.interaction.service.FollowView;
import com.just.critter.interaction.service.FollowWrite;
import com.just.critter.support.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FollowServiceTests {

    private static final long USER_ID = 1001L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T06:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private InteractionRepository repository;
    private FollowService service;

    @BeforeEach
    void setUp() {
        repository = mock(InteractionRepository.class);
        service = new FollowService(repository, CLOCK);
    }

    @Test
    @DisplayName("关注动物：首次创建返回 followId 与 newlyCreated=true")
    void followsAnimal() {
        when(repository.animalExists("CAT-2026-031")).thenReturn(true);
        when(repository.findFollow(USER_ID, "ANIMAL", "CAT-2026-031")).thenReturn(Optional.empty());
        when(repository.insertFollow(any())).thenReturn(new FollowRecord(
                12L, USER_ID, "ANIMAL", "CAT-2026-031",
                LocalDateTime.of(2026, 11, 21, 14, 0)));

        FollowView view = service.follow(new FollowCommand("ANIMAL", "CAT-2026-031"), USER_ID);

        assertThat(view.followId()).isEqualTo(12L);
        assertThat(view.newlyCreated()).isTrue();
        ArgumentCaptor<FollowWrite> write = ArgumentCaptor.forClass(FollowWrite.class);
        verify(repository).insertFollow(write.capture());
        assertThat(write.getValue().userId()).isEqualTo(USER_ID);
        assertThat(write.getValue().createdBy()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("重复关注幂等：返回已有 followId，不重复插入")
    void followIsIdempotent() {
        when(repository.animalExists("CAT-2026-031")).thenReturn(true);
        when(repository.findFollow(USER_ID, "ANIMAL", "CAT-2026-031"))
                .thenReturn(Optional.of(new FollowRecord(
                        12L, USER_ID, "ANIMAL", "CAT-2026-031",
                        LocalDateTime.of(2026, 11, 21, 14, 0))));

        FollowView view = service.follow(new FollowCommand("ANIMAL", "CAT-2026-031"), USER_ID);

        assertThat(view.followId()).isEqualTo(12L);
        assertThat(view.newlyCreated()).isFalse();
        verify(repository, never()).insertFollow(any());
    }

    @Test
    @DisplayName("关注项目：项目不存在返回 30410")
    void rejectsMissingProject() {
        when(repository.projectExists("99")).thenReturn(false);

        assertThatThrownBy(() -> service.follow(new FollowCommand("PROJECT", "99"), USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));
    }

    @Test
    @DisplayName("关注：业务类型非法返回 30303")
    void rejectsInvalidType() {
        assertThatThrownBy(() -> service.follow(new FollowCommand("NOTICE", "1"), USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30303));
    }

    @Test
    @DisplayName("取消关注：仅本人关系可删，删除 1 行返回 removed=true")
    void unfollowsOwnRelation() {
        when(repository.deleteFollow(12L, USER_ID)).thenReturn(1);

        FollowRemoval result = service.unfollow(12L, USER_ID);

        assertThat(result.followId()).isEqualTo(12L);
        assertThat(result.removed()).isTrue();
        verify(repository).deleteFollow(12L, USER_ID);
    }

    @Test
    @DisplayName("取消关注：关系不存在或不属于本人返回 30410")
    void rejectsMissingFollow() {
        when(repository.deleteFollow(12L, USER_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.unfollow(12L, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));
    }
}
