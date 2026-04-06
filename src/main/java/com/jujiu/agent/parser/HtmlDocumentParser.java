package com.jujiu.agent.parser;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 13:53
 */

@Component
@Slf4j
public class HtmlDocumentParser implements DocumentParser {
    @Override
    public String parse(InputStream inputStream, String fileName) {
        try (inputStream){
            log.info("开始解析 html 文件: {}", fileName);
            Document doc = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), "");

            String text = doc.body().text();

            log.info("html 文件解析完成: {}", fileName);
            
            return text;
        } catch (Exception e) {
            log.error("html解析失败: {}", fileName, e);

            throw new BusinessException(ResultCode.DOCUMENT_PARSE_ERROR, "html解析失败: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(String fileType) {
        return "html".equalsIgnoreCase(fileType);
    }
}
