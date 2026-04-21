package com.jujiu.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智能问答Agent系统启动类
 *
 * @author 居九
 * @since 2026-03-19
 */
@SpringBootApplication
@MapperScan({"com.jujiu.agent.module.auth.infrastructure.mapper",
        "com.jujiu.agent.module.kb.infrastructure.mapper",
        "com.jujiu.agent.module.tool.infrastructure.mapper",
        "com.jujiu.agent.module.chat.infrastructure.mapper"
})
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║                                                    ║");
        System.out.println("║          🚀  Intelligent QA Agent  🚀             ║");
        System.out.println("║                                                    ║");
        System.out.println("║            Starting up...                          ║");
        System.out.println("║                                                    ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        System.out.println("智能问答Agent系统启动成功！");
        System.out.println("Swagger UI: http://localhost:8080/swagger-ui.html");
    }
}
