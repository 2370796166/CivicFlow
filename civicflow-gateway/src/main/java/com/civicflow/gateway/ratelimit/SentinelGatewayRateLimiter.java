package com.civicflow.gateway.ratelimit;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.civicflow.gateway.config.GatewayProperties;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SentinelGatewayRateLimiter implements GatewayRateLimiter {
    private final GatewayProperties properties;

    public SentinelGatewayRateLimiter(GatewayProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void configureRules() {
        GatewayProperties.RateLimit limits = properties.getRateLimit();
        validate(limits);
        List<ParamFlowRule> rules = new ArrayList<>(ParamFlowRuleManager.getRules());
        rules.removeIf(
                rule ->
                        LOGIN_RESOURCE.equals(rule.getResource())
                                || RESERVATION_RESOURCE.equals(rule.getResource()));
        rules.add(rule(LOGIN_RESOURCE, limits.getLoginPermits(), limits.getLoginWindow()));
        rules.add(
                rule(
                        RESERVATION_RESOURCE,
                        limits.getReservationPermits(),
                        limits.getReservationWindow()));
        ParamFlowRuleManager.loadRules(rules);
    }

    @Override
    public boolean tryAcquire(String resource, String key) {
        try (Entry ignored = SphU.entry(resource, EntryType.IN, 1, key)) {
            return true;
        } catch (BlockException exception) {
            return false;
        }
    }

    private static ParamFlowRule rule(String resource, int permits, Duration window) {
        return new ParamFlowRule(resource)
                .setParamIdx(0)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT)
                .setCount(permits)
                .setDurationInSec(Math.max(1, window.toSeconds()));
    }

    private static void validate(GatewayProperties.RateLimit limits) {
        if (limits.getLoginPermits() <= 0
                || limits.getReservationPermits() <= 0
                || limits.getLoginWindow().isZero()
                || limits.getLoginWindow().isNegative()
                || limits.getReservationWindow().isZero()
                || limits.getReservationWindow().isNegative()
                || limits.getMaxReservationBodyBytes() <= 0) {
            throw new IllegalStateException("Gateway rate-limit values must be positive");
        }
    }
}
