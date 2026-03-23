-- =============================================
-- 智能问答Agent系统 - 数据库初始化脚本
-- 创建时间：2026-03-20
-- 数据库表数量：7个
-- =============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS intelligent_qa_agent
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE intelligent_qa_agent;

-- =============================================
-- 1. 用户表（user）
-- =============================================
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
  `email` VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
  `nickname` VARCHAR(50) COMMENT '昵称',
  `role` VARCHAR(20) DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1-正常，0-禁用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- =============================================
-- 2. 会话表（session）
-- =============================================
DROP TABLE IF EXISTS `session`;
CREATE TABLE `session` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '会话ID',
  `session_id` VARCHAR(50) NOT NULL UNIQUE COMMENT '会话唯一标识',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `title` VARCHAR(200) COMMENT '会话标题',
  `message_count` INT DEFAULT 0 COMMENT '消息数量',
  `last_message` TEXT COMMENT '最后一条消息',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1-活跃，0-已关闭',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_updated_at` (`updated_at`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- =============================================
-- 3. 消息表（message）
-- =============================================
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
  `message_id` VARCHAR(50) NOT NULL UNIQUE COMMENT '消息唯一标识',
  `session_id` VARCHAR(50) NOT NULL COMMENT '会话ID',
  `role` VARCHAR(20) NOT NULL COMMENT '角色：user/assistant/system',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `tokens` INT COMMENT 'Token数量',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX `idx_created_at` (`created_at`),
  FOREIGN KEY (`session_id`) REFERENCES `session`(`session_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- =============================================
-- 4. 工具表（tool）
-- =============================================
DROP TABLE IF EXISTS `tool`;
CREATE TABLE `tool` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工具ID',
  `tool_name` VARCHAR(50) NOT NULL UNIQUE COMMENT '工具名称',
  `display_name` VARCHAR(100) NOT NULL COMMENT '显示名称',
  `description` TEXT COMMENT '工具描述',
  `parameters` JSON COMMENT '参数定义（JSON格式）',
  `class_name` VARCHAR(255) COMMENT '实现类名',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具表';

-- =============================================
-- 5. 工作流表（workflow）
-- =============================================
DROP TABLE IF EXISTS `workflow`;
CREATE TABLE `workflow` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工作流ID',
  `workflow_id` VARCHAR(50) NOT NULL UNIQUE COMMENT '工作流唯一标识',
  `user_id` BIGINT NOT NULL COMMENT '创建者ID',
  `name` VARCHAR(100) NOT NULL COMMENT '工作流名称',
  `description` TEXT COMMENT '工作流描述',
  `definition` JSON NOT NULL COMMENT '工作流定义（JSON格式）',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_user_id` (`user_id`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流表';

-- =============================================
-- 6. 登录日志表（login_log）
-- =============================================
DROP TABLE IF EXISTS `login_log`;
CREATE TABLE `login_log` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `ip` VARCHAR(50) COMMENT 'IP地址',
  `user_agent` VARCHAR(255) COMMENT '用户代理',
  `status` TINYINT COMMENT '登录状态：1-成功，0-失败',
  `login_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_login_time` (`login_time`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

-- =============================================
-- 7. 密码重置令牌表（password_reset_token）
-- =============================================
DROP TABLE IF EXISTS `password_reset_token`;
CREATE TABLE `password_reset_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '关联用户ID',
  `token` VARCHAR(255) NOT NULL COMMENT '加密或随机生成的重置令牌',
  `expiry_time` DATETIME NOT NULL COMMENT '令牌失效的具体时间点',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-未使用, 1-已使用, 2-已失效',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  INDEX `idx_user_id` (`user_id`),
  CONSTRAINT `fk_pwd_reset_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户密码重置令牌表';

-- =============================================
-- 初始化数据
-- =============================================

-- 插入默认管理员（密码：admin123，使用BCrypt加密）
INSERT INTO `user` (username, password, email, nickname, role)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'admin@example.com', '管理员', 'ADMIN');

-- 插入默认工具
INSERT INTO `tool` (tool_name, display_name, description, parameters, class_name)
VALUES
('weather', '天气查询', '查询指定城市的天气信息',
 '{"city":{"type":"string","required":true,"description":"城市名称"}}',
 'com.jujiu.agent.tool.WeatherTool'),
('calculator', '计算器', '执行数学计算',
 '{"expression":{"type":"string","required":true,"description":"数学表达式"}}',
 'com.jujiu.agent.tool.CalculatorTool');

-- =============================================
-- 完成
-- =============================================
SELECT '数据库初始化完成！' AS message;
SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = 'intelligent_qa_agent';