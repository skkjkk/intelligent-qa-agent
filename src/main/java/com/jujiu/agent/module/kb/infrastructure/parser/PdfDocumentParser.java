package com.jujiu.agent.module.kb.infrastructure.parser;

import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 13:44
 */
@Component
@Slf4j
public class PdfDocumentParser implements DocumentParser {
    @Override
    public String parse(InputStream inputStream, String fileName) {
        // 1. 加载 PDF 文档
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            log.info("开始解析 pdf 文件: {}", fileName);
            // 2. 提取文本内容
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("pdf 文件解析完成: {}", fileName);
            return text;
        } catch (Exception e) {
            // 抛 BusinessException
            log.error("pdf解析失败: {}", fileName, e);
            throw new BusinessException(ResultCode.DOCUMENT_PARSE_ERROR, "pdf解析失败: " + e.getMessage());
        }
    }

    @Override
    public List<String> supportedTypes() {
        return List.of("pdf");
    }

    @Override
    public boolean supports(String fileType) {
        return "pdf".equalsIgnoreCase(fileType);
    }

    @Override
    public int order() {
        return 100;
    }
}
