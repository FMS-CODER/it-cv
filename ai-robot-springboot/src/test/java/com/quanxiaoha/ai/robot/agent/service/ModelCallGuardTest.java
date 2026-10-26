package com.quanxiaoha.ai.robot.agent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 模型调用守卫单元测试：覆盖瞬时错误重试、首 token 后不重试、优雅兜底与错误分类 */
class ModelCallGuardTest {

    private ModelCallGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ModelCallGuard();
        ReflectionTestUtils.setField(guard, "maxAttempts", 3);
        ReflectionTestUtils.setField(guard, "backoffMs", 10L);
        ReflectionTestUtils.setField(guard, "firstTokenTimeoutMs", 5000L);
    }

    /** 首 token 之前的瞬时错误应重试，重试成功后输出正常 */
    @Test
    void retriesTransientErrorBeforeFirstToken() {
        AtomicInteger calls = new AtomicInteger();
        Flux<String> result = guard.guard(
                () -> calls.incrementAndGet() == 1
                        ? Flux.error(new IOException("connection reset"))
                        : Flux.just("hello"),
                s -> true,
                "fallback");
        List<String> out = result.collectList().block(Duration.ofSeconds(5));
        assertEquals(List.of("hello"), out);
        assertEquals(2, calls.get());
    }

    /** 已经输出过 token 后出错：不再重试，保留已输出内容并温和结束，避免重复输出 */
    @Test
    void doesNotRetryAfterFirstToken() {
        AtomicInteger calls = new AtomicInteger();
        Flux<String> result = guard.guard(
                () -> {
                    calls.incrementAndGet();
                    return Flux.concat(Flux.just("partial"), Flux.error(new IOException("broken pipe")));
                },
                s -> true,
                "fallback");
        List<String> out = result.collectList().block(Duration.ofSeconds(5));
        assertEquals(List.of("partial"), out);
        assertEquals(1, calls.get());
    }

    /** 持久性错误（非瞬时）且尚未输出内容：重试无效，下发兜底提示避免空白 */
    @Test
    void fallsBackWhenPermanentErrorBeforeToken() {
        Flux<String> result = guard.guard(
                () -> Flux.error(new IllegalStateException("401 unauthorized")),
                s -> true,
                "fallback");
        List<String> out = result.collectList().block(Duration.ofSeconds(5));
        assertEquals(List.of("fallback"), out);
    }

    /** 错误分类：瞬时 vs 持久 */
    @Test
    void classifiesTransientErrors() {
        assertTrue(guard.isTransient(new IOException("connection reset")));
        assertTrue(guard.isTransient(new RuntimeException("HTTP 429 too many requests")));
        assertTrue(guard.isTransient(new RuntimeException("503 Service Unavailable")));
        assertFalse(guard.isTransient(new IllegalStateException("401 unauthorized")));
    }
}
