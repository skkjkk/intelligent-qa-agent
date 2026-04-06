package com.jujiu.agent.parser;

import java.io.InputStream;

/**
 * 文档解析器接口。
 *
 * <p>定义将各类文档格式（PDF、Word 等）提取为纯文本的统一契约。
 * 实现类必须保证：
 * <ul>
 *   <li>输入为原始文件流，输出为纯文本字符串</li>
 *   <li>不在本层执行分块或向量化操作</li>
 *   <li>解析过程中出现的任何异常均转换为业务异常抛出</li>
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
     * 判断是否支持该文件类型。
     *
     * @param fileType 文件类型小写后缀，如 {@code txt}、{@code pdf}
     * @return true 表示支持
     */
    boolean supports(String fileType);
}
