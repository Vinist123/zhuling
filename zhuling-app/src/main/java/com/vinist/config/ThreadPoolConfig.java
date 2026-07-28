package com.vinist.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@EnableAsync
@Configuration
@EnableConfigurationProperties(ThreadPoolConfigProperties.class)
public class ThreadPoolConfig {

    @Bean
    @ConditionalOnMissingBean(ThreadPoolExecutor.class)
    public ThreadPoolExecutor threadPoolExecutor(ThreadPoolConfigProperties properties) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        // 实例化策略
        RejectedExecutionHandler handler;
        switch (properties.getPolicy()){
            case "AbortPolicy":
                handler = new ThreadPoolExecutor.AbortPolicy();
                break;
            case "DiscardPolicy":
                handler = new ThreadPoolExecutor.DiscardPolicy();
                break;
            case "DiscardOldestPolicy":
                handler = new ThreadPoolExecutor.DiscardOldestPolicy();
                break;
            case "CallerRunsPolicy":
                handler = new ThreadPoolExecutor.CallerRunsPolicy();
                break;
            default:
                handler = new ThreadPoolExecutor.AbortPolicy();
                break;
        }
        // 创建线程池
        return new ThreadPoolExecutor(properties.getCorePoolSize(),
                properties.getMaxPoolSize(),
                properties.getKeepAliveTime(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getBlockQueueSize()),
                Executors.defaultThreadFactory(),
                handler);
    }

    /**
     * 阻塞仓储专用执行器
     * 
     * <p>用途：隔离 MyBatis-Plus 等阻塞式 JDBC 调用，防止阻塞 Reactor 事件线程。
     * 所有 RepositoryImpl 中的阻塞数据库访问必须通过此执行器调度。
     * 
     * <p>使用方式：
     * <pre>{@code
     * // 在 RepositoryImpl 中注入此 Bean
     * @Resource
     * private Executor blockingRepositoryExecutor;
     * 
     * // 阻塞调用包装
     * Mono.fromCallable(() -> messageDao.selectList(wrapper))
     *     .subscribeOn(Schedulers.fromExecutor(blockingRepositoryExecutor));
     * }</pre>
     */
    @Bean("blockingRepositoryExecutor")
    public Executor blockingRepositoryExecutor() {
        return new ThreadPoolExecutor(
                20,                           // 核心线程数
                50,                           // 最大线程数
                60L,                          // 空闲超时
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200), // 队列容量
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "blocking-repo-" + counter.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()  // 队列满时由调用线程执行，背压保护
        );
    }

}
