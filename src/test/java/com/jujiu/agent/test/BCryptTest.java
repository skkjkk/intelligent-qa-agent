package com.jujiu.agent.test;

import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * BCrypt 密码加密测试工具
 */
public class BCryptTest {
    
    public static void main(String[] args) {
        // 生成用于数据库的测试密码
        String testPassword = "123456";
        String dbPassword = BCrypt.hashpw(testPassword, BCrypt.gensalt());
        
        System.out.println("=== 数据库测试数据 ===\n");
        System.out.println("-- 执行以下 SQL 更新数据库密码 --");
        System.out.println("UPDATE user SET password = '" + dbPassword + "' WHERE username = 'admin';");
        System.out.println("\n-- 然后用密码 \"123456\" 测试登录 --");
        System.out.println("\n加密后的密码：" + dbPassword);
        
        // 验证一下
        System.out.println("\n验证结果：" + BCrypt.checkpw(testPassword, dbPassword));
    }
}
