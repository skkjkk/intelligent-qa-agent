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
-- 8. 知识库文档表（kb_document）
-- =============================================
DROP TABLE IF EXISTS `kb_document`;
CREATE TABLE `kb_document` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `kb_id` BIGINT NOT NULL DEFAULT 1 COMMENT '知识库ID',
  `title` VARCHAR(255) NOT NULL COMMENT '文档标题',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `file_type` VARCHAR(32) NOT NULL COMMENT '文件类型：txt/md/pdf/docx/html',
                               `file_path` VARCHAR(512) NOT NULL COMMENT '对象存储路径',
                               `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小',
                               `content_hash` VARCHAR(64) NOT NULL COMMENT '内容哈希',
                               `status` VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT '文档状态',
                               `parse_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '解析状态',
                               `index_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '索引状态',
                               `chunk_count` INT NOT NULL DEFAULT 0 COMMENT '分块数量',
                               `owner_user_id` BIGINT NOT NULL COMMENT '所属用户ID',
                               `visibility` VARCHAR(32) NOT NULL DEFAULT 'PRIVATE' COMMENT '可见性',
                               `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
                               `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
                               `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  COMMENT '更新时间',
                                UNIQUE KEY `uk_kb_document_hash` (`kb_id`, `owner_user_id`, `content_hash`, `deleted`)
                               KEY `idx_kb_document_owner` (`owner_user_id`),
                               KEY `idx_kb_document_status` (`status`),
                               KEY `idx_kb_document_created_at` (`created_at`),
                               CONSTRAINT `fk_kb_document_user` FOREIGN KEY (`owner_user_id`) REFERENCES `user` (`id`)
                                   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- =============================================
-- 9. 知识库文档分块表（kb_chunk）
-- =============================================
DROP TABLE IF EXISTS `kb_chunk`;
CREATE TABLE `kb_chunk` (
                            `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                            `document_id` BIGINT NOT NULL COMMENT '所属文档ID',
                            `chunk_index` INT NOT NULL COMMENT '分块序号',
                            `content` MEDIUMTEXT NOT NULL COMMENT '分块内容',
                            `summary` VARCHAR(1000) COMMENT '分块摘要',
                            `char_count` INT NOT NULL DEFAULT 0 COMMENT '字符数',
                            `token_count` INT NOT NULL DEFAULT 0 COMMENT 'token数',
                            `keywords` VARCHAR(1000) COMMENT '关键词',
                            `section_title` VARCHAR(255) COMMENT '章节标题',
                            `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
                            `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  COMMENT '更新时间',
                            UNIQUE KEY `uk_kb_chunk_doc_idx` (`document_id`, `chunk_index`),
                            KEY `idx_kb_chunk_document` (`document_id`),
                            CONSTRAINT `fk_kb_chunk_document` FOREIGN KEY (`document_id`) REFERENCES `kb_document`
                                (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档分块表';


-- =============================================
-- 10. 知识库标签表（kb_tag）
-- =============================================
DROP TABLE IF EXISTS `kb_tag`;
CREATE TABLE `kb_tag` (
                          `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                          `kb_id` BIGINT NOT NULL DEFAULT 1 COMMENT '知识库ID',
                          `name` VARCHAR(64) NOT NULL COMMENT '标签名',
                          `color` VARCHAR(32) COMMENT '标签颜色',
                          `created_by` BIGINT NOT NULL COMMENT '创建人',
                          `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  COMMENT '更新时间',
                          UNIQUE KEY `uk_kb_tag_name` (`kb_id`, `name`),
                          CONSTRAINT `fk_kb_tag_user` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON
                              DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库标签表';

-- =============================================
-- 11. 文档标签关联表（kb_document_tag）
-- =============================================
DROP TABLE IF EXISTS `kb_document_tag`;
CREATE TABLE `kb_document_tag` (
                                   `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                   `document_id` BIGINT NOT NULL COMMENT '文档ID',
                                   `tag_id` BIGINT NOT NULL COMMENT '标签ID',
                                   `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   UNIQUE KEY `uk_kb_document_tag` (`document_id`, `tag_id`),
                                   CONSTRAINT `fk_kb_document_tag_document` FOREIGN KEY (`document_id`) REFERENCES
                                       `kb_document` (`id`) ON DELETE CASCADE,
                                   CONSTRAINT `fk_kb_document_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `kb_tag` (`id`)
                                       ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档标签关联表';

-- =============================================
-- 12. 知识库查询日志表（kb_query_log）
-- =============================================
DROP TABLE IF EXISTS `kb_query_log`;
CREATE TABLE `kb_query_log` (
                                `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                `kb_id` BIGINT NOT NULL DEFAULT 1 COMMENT '知识库ID',
                                `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                `session_id` VARCHAR(64) COMMENT '会话ID',
                                `query_source` VARCHAR(32) NOT NULL COMMENT '来源：KB_API/CHAT_ENHANCE/TOOL_CALL',
                                `question` TEXT NOT NULL COMMENT '用户问题',
                                `rewritten_question` TEXT COMMENT '改写后的问题',
                                `answer` MEDIUMTEXT COMMENT '模型答案',
                                `retrieval_top_k` INT NOT NULL DEFAULT 5 COMMENT '检索TopK',
                                `retrieval_mode` VARCHAR(32) NOT NULL DEFAULT 'HYBRID' COMMENT '检索模式',
                                `cited_chunk_ids` JSON COMMENT '引用chunk ID列表',
                                `prompt_tokens` INT NOT NULL DEFAULT 0 COMMENT '输入token',
                                `completion_tokens` INT NOT NULL DEFAULT 0 COMMENT '输出token',
                                `total_tokens` INT NOT NULL DEFAULT 0 COMMENT '总token',
                                `latency_ms` INT NOT NULL DEFAULT 0 COMMENT '耗时ms',
                                `status` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态',
                                `error_message` VARCHAR(1000) COMMENT '错误信息',
                                `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                KEY `idx_kb_query_log_user` (`user_id`),
                                KEY `idx_kb_query_log_session` (`session_id`),
                                KEY `idx_kb_query_log_created_at` (`created_at`),
                                CONSTRAINT `fk_kb_query_log_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON
                                    DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库查询日志表';

-- =============================================
-- 13. 知识库查询反馈表（kb_query_feedback）
-- =============================================
DROP TABLE IF EXISTS `kb_query_feedback`;
CREATE TABLE `kb_query_feedback` (
                                     `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                     `query_log_id` BIGINT NOT NULL COMMENT '查询日志ID',
                                     `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                     `helpful` TINYINT NOT NULL COMMENT '是否有帮助',
                                     `rating` TINYINT COMMENT '评分1-5',
                                     `feedback_content` VARCHAR(1000) COMMENT '反馈内容',
                                     `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     KEY `idx_kb_query_feedback_user` (`user_id`),
                                     CONSTRAINT `fk_kb_query_feedback_log` FOREIGN KEY (`query_log_id`) REFERENCES
                                         `kb_query_log` (`id`) ON DELETE CASCADE,
                                     CONSTRAINT `fk_kb_query_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
                                         ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库查询反馈表';

-- =============================================
-- 14. 文档访问控制表（kb_document_acl）
-- =============================================
DROP TABLE IF EXISTS `kb_document_acl`;
CREATE TABLE `kb_document_acl` (
                                   `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                   `document_id` BIGINT NOT NULL COMMENT '文档ID',
                                   `principal_type` VARCHAR(32) NOT NULL COMMENT '主体类型',
                                   `principal_id` VARCHAR(64) NOT NULL COMMENT '主体ID',
                                   `permission` VARCHAR(32) NOT NULL COMMENT '权限：READ/rebuildFailedIndexes 是做什么的',
                                   `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   UNIQUE KEY `uk_kb_document_acl` (`document_id`, `principal_type`, `principal_id`,
                                       `permission`),
                                   CONSTRAINT `fk_kb_document_acl_document` FOREIGN KEY (`document_id`) REFERENCES
                                       `kb_document` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档ACL表';

-- =============================================
-- 15. 文档处理日志表（kb_document_process_log）
-- =============================================
DROP TABLE IF EXISTS `kb_document_process_log`;
CREATE TABLE `kb_document_process_log` (
                                           `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                           `document_id` BIGINT NOT NULL COMMENT '文档ID',
                                           `stage` VARCHAR(32) NOT NULL COMMENT '阶段',
                                           `status` VARCHAR(32) NOT NULL COMMENT '状态',
                                           `message` VARCHAR(1000) COMMENT '阶段说明',
                                           `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
                                           `started_at` DATETIME COMMENT '开始时间',
                                           `ended_at` DATETIME COMMENT '结束时间',
                                           `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                           KEY `idx_kb_document_process_log_doc` (`document_id`),
                                           KEY `idx_kb_document_process_log_stage` (`stage`),
                                           CONSTRAINT `fk_kb_document_process_log_document` FOREIGN KEY (`document_id`) REFERENCES
                                               `kb_document` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档处理日志表';

-- =============================================
-- 16. 检索轨迹表（kb_retrieval_trace）
-- =============================================
DROP TABLE IF EXISTS `kb_retrieval_trace`;
CREATE TABLE `kb_retrieval_trace` (
                                      `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                      `query_log_id` BIGINT NOT NULL COMMENT '查询日志ID',
                                      `chunk_id` BIGINT NOT NULL COMMENT '命中文档块ID',
                                      `score` DECIMAL(10,6) NOT NULL DEFAULT 0 COMMENT '得分',
                                      `rank_no` INT NOT NULL COMMENT '排序号',
                                      `retrieval_type` VARCHAR(32) NOT NULL COMMENT '检索类型',
                                      `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      KEY `idx_kb_retrieval_trace_log` (`query_log_id`),
                                      CONSTRAINT `fk_kb_retrieval_trace_log` FOREIGN KEY (`query_log_id`) REFERENCES
                                          `kb_query_log` (`id`) ON DELETE CASCADE,
                                      CONSTRAINT `fk_kb_retrieval_trace_chunk` FOREIGN KEY (`chunk_id`) REFERENCES `kb_chunk`
                                          (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检索轨迹表';

-- =============================================
-- 17. 文档 ACL 审计日志表（kb_document_acl_audit_log）
-- =============================================
DROP TABLE IF EXISTS `kb_document_acl_audit_log`;
CREATE TABLE `kb_document_acl_audit_log` (
                                             `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                             `document_id` BIGINT NOT NULL COMMENT '文档ID',
                                             `operator_user_id` BIGINT NOT NULL COMMENT '操作人用户ID',
                                             `action` VARCHAR(32) NOT NULL COMMENT '动作：GRANT/REVOKE/ACCESS_DENIED',
                                             `principal_type` VARCHAR(32) COMMENT '被授权主体类型',
                                             `principal_id` VARCHAR(64) COMMENT '被授权主体ID',
                                             `permission` VARCHAR(32) COMMENT '权限：READ/rebuildFailedIndexes 是做什么的/SHARE',
                                             `reason` VARCHAR(255) COMMENT '原因或补充说明',
                                             `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                             KEY `idx_acl_audit_doc` (`document_id`),
                                             KEY `idx_acl_audit_operator` (`operator_user_id`),
                                             KEY `idx_acl_audit_action` (`action`),
                                             KEY `idx_acl_audit_created_at` (`created_at`),
                                             CONSTRAINT `fk_acl_audit_document` FOREIGN KEY (`document_id`) REFERENCES `kb_document` (`id`) ON DELETE CASCADE,
                                             CONSTRAINT `fk_acl_audit_user` FOREIGN KEY (`operator_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档ACL审计日志表';

-- =============================================
-- 18. 知识库用户组表（kb_group）
-- =============================================
DROP TABLE IF EXISTS `kb_group`;
CREATE TABLE `kb_group` (
                            `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                            `name` VARCHAR(100) NOT NULL COMMENT '组名称',
                            `code` VARCHAR(64) NOT NULL COMMENT '组编码',
                            `created_by` BIGINT NOT NULL COMMENT '创建人',
                            `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            UNIQUE KEY `uk_kb_group_code` (`code`),
                            KEY `idx_kb_group_created_by` (`created_by`),
                            CONSTRAINT `fk_kb_group_user` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库用户组表';

-- =============================================
-- 19. 知识库用户组成员表（kb_group_member）
-- =============================================
DROP TABLE IF EXISTS `kb_group_member`;
CREATE TABLE `kb_group_member` (
                                   `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                   `group_id` BIGINT NOT NULL COMMENT '组ID',
                                   `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                   `role` VARCHAR(32) NOT NULL DEFAULT 'MEMBER' COMMENT '成员角色：OWNER/MEMBER',
                                   `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   UNIQUE KEY `uk_kb_group_member` (`group_id`, `user_id`),
                                   KEY `idx_kb_group_member_user` (`user_id`),
                                   CONSTRAINT `fk_kb_group_member_group` FOREIGN KEY (`group_id`) REFERENCES `kb_group` (`id`) ON DELETE CASCADE,
                                   CONSTRAINT `fk_kb_group_member_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库用户组成员表';

-- =============================================
-- 20. 文档共享组关联表（kb_document_group）
-- =============================================
DROP TABLE IF EXISTS `kb_document_group`;
CREATE TABLE `kb_document_group` (
                                     `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                     `document_id` BIGINT NOT NULL COMMENT '文档ID',
                                     `group_id` BIGINT NOT NULL COMMENT '组ID',
                                     `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     UNIQUE KEY `uk_kb_document_group` (`document_id`, `group_id`),
                                     KEY `idx_kb_document_group_group` (`group_id`),
                                     CONSTRAINT `fk_kb_document_group_document` FOREIGN KEY (`document_id`) REFERENCES `kb_document` (`id`) ON DELETE CASCADE,
                                     CONSTRAINT `fk_kb_document_group_group` FOREIGN KEY (`group_id`) REFERENCES `kb_group` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档共享组关联表';

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
