package com.jujiu.agent.config;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/29 15:14
 */
@Component
public class ThreadPoolShutdownHook {
    @Autowired
    private ExecutorService chatExecutor;

    @PreDestroy
    public void shutdown() {
        // 服务停止时，等待正在执行的任务完成
        chatExecutor.shutdown();
        try {
            if (!chatExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                chatExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            chatExecutor.shutdownNow();
        }
    }
}
