package com.jujiu.agent.module.kb.infrastructure.parser;

import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * 基于 Apache Tika 的通用文档解析器。
 *
 * <p>用于为多种文档格式提供统一的文本提取能力，
 * 作为专用解析器之外的通用兜底方案。
 *
 * <p>当前解析器支持：
 * <ul>
 *     <li>Office 文档</li>
 *     <li>富文本与结构化文本</li>
 *     <li>部分开放文档格式</li>
 * </ul>
 *
 * <p>注意：
 * <ul>
 *     <li>该解析器定位为 fallback parser</li>
 *     <li>优先级应低于 PDF / DOCX / HTML / Markdown / TXT 等专用解析器</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/12
 */
@Component
@Slf4j
public class TikaDocumentParser implements DocumentParser {

    private final Tika tika = new Tika();

    /**
     * 使用 Tika 解析文档内容。
     *
     * @param inputStream 原始文件输入流
     * @param fileName 原始文件名
     * @return 提取后的纯文本
     */
    @Override
    public String parse(InputStream inputStream, String fileName) {
        try {
            String text = tika.parseToString(inputStream);
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            log.error("[KB][PARSER][TIKA] 文档解析失败 - fileName={}", fileName, e);
            throw new BusinessException(ResultCode.FILE_READ_ERROR, "文档解析失败：" + fileName);
        }
    }

    /**
     * 返回支持的文件类型列表。
     *
     * @return 支持的文件类型列表
     */
    @Override
    public List<String> supportedTypes() {
        return List.of(
                "txt",
                "md",
                "pdf",
                "doc",
                "docx",
                "xls",
                "xlsx",
                "ppt",
                "pptx",
                "rtf",
                "xml",
                "csv",
                "html",
                "odt",
                "ods",
                "odp",
                "epub"
        );
    }

    /**
     * 返回解析器优先级。
     *
     * @return 优先级
     */
    @Override
    public int order() {
        return 10;
    }
}
