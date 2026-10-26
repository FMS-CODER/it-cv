package com.quanxiaoha.ai.robot.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 模型调用守卫：包裹流式大模型调用，提供三件事：
 *
 * <ol>
 *   <li><b>首 token 超时保护</b>：等待首个 token 的时间超过阈值即判为瞬时故障，触发重试；
 *       一旦拿到首 token 则不再施加超时，避免正常的长回答被误杀。</li>
 *   <li><b>有条件的自动重试</b>：仅当「尚未输出任何 token」且「错误属于瞬时类型（限流/超时/网络）」时才重试。
 *       这一点很关键——流式一旦已经吐出内容再重试，会把内容重复输出给前端。</li>
 *   <li><b>优雅兜底</b>：重试耗尽后不再向 SSE 抛异常。已有部分输出则温和结束并保留内容；
 *       完全没有输出则下发一条兜底提示，避免前端出现空白。</li>
 * </ol>
 *
 * @param <T> 流式元素类型（对话场景为 AIResponse，简历场景为 JSON 字符串）
 */
@Service
@Slf4j
public class ModelCallGuard {

    /** 重试次数（含首次尝试），配置为 3 表示最多尝试 3 次 */
    @Value("${agent.model.retry.max-attempts:3}")
    private int maxAttempts;

    /** 重试退避基数（毫秒），按指数退避 */
    @Value("${agent.model.retry.backoff-ms:500}")
    private long backoffMs;

    /** 首 token 等待超时（毫秒） */
    @Value("${agent.model.first-token-timeout-ms:30000}")
    private long firstTokenTimeoutMs;

    /**
     * 包裹一次流式模型调用。
     *
     * @param callSupplier      模型调用工厂，每次重试都会重新调用（需保证可重复订阅）
     * @param hasContent        判定某个元素是否代表「已产出内容」，用于决定是否还能重试
     * @param noContentFallback 完全无输出时的兜底元素；传 null 表示不下发兜底
     */
    public <T> Flux<T> guard(Supplier<Flux<T>> callSupplier,
                             Predicate<T> hasContent,
                             T noContentFallback) {
        AtomicBoolean firstTokenEmitted = new AtomicBoolean(false);

        // defer：每次（重）订阅都重新发起一次模型调用，并重置「首 token」标记
        Flux<T> attempt = Flux.defer(() -> {
            firstTokenEmitted.set(false);
            return callSupplier.get().doOnNext(item -> {
                if (item != null && hasContent.test(item)) {
                    firstTokenEmitted.set(true);
                }
            });
        });

        return attempt
                // 首 token 超时保护：首元素到达前受时间约束，到达后不再超时
                .timeout(Mono.delay(Duration.ofMillis(firstTokenTimeoutMs)), item -> Mono.never())
                .retryWhen(Retry.backoff(Math.max(0, maxAttempts - 1), Duration.ofMillis(backoffMs))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter(ex -> !firstTokenEmitted.get() && isTransient(ex))
                        .doBeforeRetry(signal -> log.warn("模型调用瞬时失败，准备第 {} 次重试，原因：{}",
                                signal.totalRetries() + 1,
                                signal.failure() == null ? "" : signal.failure().getMessage())))
                .onErrorResume(ex -> {
                    boolean hasOutput = firstTokenEmitted.get();
                    log.warn("模型调用最终失败：hasOutput={}，原因：{}", hasOutput, ex.getMessage());
                    if (hasOutput || noContentFallback == null) {
                        // 已有部分内容：温和结束，保留已输出内容
                        return Flux.empty();
                    }
                    // 无任何内容：下发兜底提示，避免用户看到空白
                    return Flux.just(noContentFallback);
                });
    }

    /** 判定是否为可重试的瞬时错误：限流、超时、连接类、5xx 网关错误 */
    public boolean isTransient(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof IOException || cause instanceof TimeoutException) {
                return true;
            }
            String msg = cause.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("429") || lower.contains("rate limit")
                        || lower.contains("timeout") || lower.contains("timed out")
                        || lower.contains("502") || lower.contains("503") || lower.contains("504")
                        || lower.contains("connection reset") || lower.contains("temporarily unavailable")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}
