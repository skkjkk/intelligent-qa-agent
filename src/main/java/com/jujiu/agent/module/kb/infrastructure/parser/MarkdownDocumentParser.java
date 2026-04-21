package com.jujiu.agent.module.kb.infrastructure.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;


/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 13:41
 */
@Component
@Slf4j
public class MarkdownDocumentParser implements DocumentParser {
    
    private final TxtDocumentParser txtParser;

    // 构造函数注入 TxtDocumentParser
    public MarkdownDocumentParser(TxtDocumentParser txtParser) {
        this.txtParser = txtParser;
    }
    @Override
    public String parse(InputStream inputStream, String fileName) {
        log.info("开始解析 markdown 文件: {}", fileName);
        String text = txtParser.parse(inputStream, fileName);
        log.info("markdown 文件解析完成: {}", fileName);
        return text;
    }

    @Override
    public List<String> supportedTypes() {
        return List.of("md");
    }

    @Override
    public boolean supports(String fileType) {
        return "md".equalsIgnoreCase(fileType);
    }

    @Override
    public int order() {
        return 100;
    }
}
