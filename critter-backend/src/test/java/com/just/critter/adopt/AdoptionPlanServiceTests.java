package com.just.critter.adopt;

import java.util.List;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionPlan;
import com.just.critter.adopt.repository.AdoptionPlanRepository;
import com.just.critter.adopt.service.AdoptionPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdoptionPlanServiceTests {

    @Test
    @DisplayName("列表只包含启用方案，停用方案不对外暴露")
    void listEnabledPlansExcludesDisabledPlans() {
        AdoptionPlanRepository repository = mock(AdoptionPlanRepository.class);
        AdoptionPlan enabled = new AdoptionPlan(
                1L, "测试月度方案", AdoptionPeriod.MONTH, 3000, "测试权益", true);
        AdoptionPlan disabled = new AdoptionPlan(
                2L, "测试季度方案", AdoptionPeriod.QUARTER, 9000, "测试权益", false);
        when(repository.findAll()).thenReturn(List.of(enabled, disabled));
        AdoptionPlanService service = new AdoptionPlanService(repository);

        List<AdoptionPlan> result = service.listEnabledPlans();

        assertThat(result).containsExactly(enabled);
    }
}
