package com.jujiu.agent.module.kb.infrastructure.parser;

import java.io.InputStream;
import java.util.List;

/**
 * 文档解析器接口。
 *
 * <p>定义将各类文档格式（PDF、Word、Excel、PPT 等）提取为纯文本的统一契约。
 * 实现类必须保证：
 * <ul>
 *   <li>输入为原始文件流，输出为纯文本字符串</li>
 *   <li>不在本层执行分块或向量化操作</li>
 *   <li>解析过程中出现的任何异常均转换为业务异常抛出</li>
 * </ul>
 *
 * <p>当前接口支持：
 * <ul>
 *   <li>声明支持的文件类型列表</li>
 *   <li>声明解析器优先级</li>
 *   <li>通过默认方法提供统一 supports 判断</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1
 */
public interface DocumentParser {
    /**
     * 将文件流解析为纯文本。
     *
     * @param inputStream 原始文件输入流（由调用方负责打开，由本方法负责关闭）
     * @param fileName    原始文件名，用于异常信息定位
     * @return 提取后的纯文本
     */
    String parse(InputStream inputStream, String fileName);
    
    /**
     * 返回当前解析器支持的文件类型列表。
     *
     * @return 支持的文件类型列表
     */
    List<String> supportedTypes();
    /**
     * 返回当前解析器优先级。
     *
     * <p>数值越大优先级越高。
     * 专用解析器应高于通用兜底解析器。
     *
     * @return 解析器优先级
     */
    default int order() {
        return 0;
    }

    /**
     * 判断是否支持该文件类型。
     *
     * @param fileType 文件类型小写后缀，如 {@code txt}、{@code pdf}
     * @return true 表示支持
     */
    default boolean supports(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            return false;
        }
        return supportedTypes().contains(fileType.toLowerCase());
    }
}
