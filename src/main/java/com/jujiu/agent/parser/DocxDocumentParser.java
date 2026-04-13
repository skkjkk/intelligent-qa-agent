package com.jujiu.agent.parser;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 13:47
 */
@Component
@Slf4j
public class DocxDocumentParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream, String fileName) {
        // 1. 创建 XWPFDocument 对象
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            log.info("开始解析 docx 文件: {}", fileName);
            // 2. 创建 XWPFWordExtractor 对象
            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            // 3. 获取文本内容
            String text = extractor.getText();
            log.info("docx 文件解析完成: {}", fileName);
            return text;
        } catch (Exception e) {
            log.error("docx解析失败: {}", fileName, e);
            throw new BusinessException(ResultCode.DOCUMENT_PARSE_ERROR, "docx解析失败: " + e.getMessage());
        }
    }

    @Override
    public List<String> supportedTypes() {
        return List.of("docx");
    }

    @Override
    public boolean supports(String fileType) {
        return "docx".equalsIgnoreCase(fileType);
    }

    @Override
    public int order() {
        return 100;
    }
}
