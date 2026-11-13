package com.prodigalgal.ircs.contentsafety;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentItem;
import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentModelResult;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@RegisterReflectionForBinding({AdultAssessmentItem.class, AdultAssessmentModelResult.class})
class DecisionQuotaConfiguration {
    @Bean
    DecisionQuotaStore decisionQuotaStore(StringRedisTemplate redis) {
        return new DistributedDecisionQuotaStore(redis, Clock.systemUTC(), DistributedDecisionQuotaStore.PREFIX);
    }

    @Bean
    DecisionQueueStore decisionQueueStore(StringRedisTemplate redis, ObjectMapper mapper, DecisionSettings settings) {
        return new DistributedDecisionQueueStore(redis, mapper, settings, DistributedDecisionQueueStore.PREFIX);
    }
}
