package com.jujiu.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/29 15:08
 */
@Configuration
public class ThreadPoolConfig {

    @Bean(name = "chatExecutor")
    public ExecutorService chatExecutor() {
        // 线程计数器
        AtomicInteger atomicInteger = new AtomicInteger();
        return new ThreadPoolExecutor(
                // 核心线程数：同时处理 4 个聊天
                4,
                // 最大线程数：最多 20 个
                20,
                // 空闲线程存活时间
                60L, TimeUnit.SECONDS,
                // 等待队列：最多 100 个任务排队
                new LinkedBlockingQueue<>(100),
                // 线程工厂：创建线程的工厂
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        // 线程名：chat-stream-1
                        thread.setName("chat-stream-" + atomicInteger.incrementAndGet());
                        thread.setDaemon(true); // 守护线程，不阻止 JVM 退出
                        return thread;
                    }
                    
                },
                // 队列满时，由调用线程执行
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
