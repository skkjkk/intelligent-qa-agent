package com.jujiu.agent.parser;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 纯文本解析器。
 *
 * <p>支持 {@code txt} 格式。按 UTF-8 编码读取全部内容后按行拼接。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 13:24
 */
@Component
@Slf4j
public class TxtDocumentParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream, String fileName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            log.info("开始解析 txt 文件: {}", fileName);
            String text = reader.lines().collect(Collectors.joining("\n"));
            log.info("txt 文件解析完成: {}", fileName);
            return text;
        } catch (Exception e) {
            log.error("TXT解析失败: {}", fileName, e);
            throw new BusinessException(ResultCode.DOCUMENT_PARSE_ERROR, "TXT解析失败: " + e.getMessage());
        }
    }

    @Override
    public List<String> supportedTypes() {
        return List.of("txt");
    }

    @Override
    public boolean supports(String fileType) {
        return "txt".equalsIgnoreCase(fileType);
    }

    @Override
    public int order() {
        return 100;
    }
}
