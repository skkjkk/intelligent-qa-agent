package com.jujiu.agent.parser;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档解析器工厂。
 *
 * <p>根据文件类型自动路由到对应的 {@link DocumentParser} 实现。
 * 采用自动发现机制：所有实现 {@code DocumentParser} 的 Spring Bean
 * 启动时自动注册，无需修改本类即可支持新格式。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 12:09
 */
@Component
public class DocumentParserFactory {

    private final Map<String, DocumentParser> parserMap = new ConcurrentHashMap<>();

    public DocumentParserFactory(List<DocumentParser> parsers) {
        parsers.forEach(parser -> {
                    if (parser.supports("txt")) {
                        parserMap.put("txt", parser);
                    }
                    if (parser.supports("md")) {
                        parserMap.put("md", parser);
                    }
                    if (parser.supports("pdf")) {
                        parserMap.put("pdf", parser);
                    }
                    if (parser.supports("docx")) {
                        parserMap.put("docx", parser);
                    }
                    if (parser.supports("html")) {
                        parserMap.put("html", parser);
                    }
                }
        );
       
    }

    /**
     * 获取对应文件类型的解析器。
     *
     * @param fileType 文件类型，如 txt、md、pdf
     * @return 解析器实例
     * @throws BusinessException 不支持的文件类型时抛出
     */
    public DocumentParser getParser(String fileType) {
        // 1. 转小写
        String type;
        type = fileType == null ? "" : fileType.toLowerCase();
        // 2. 从 map 取
        DocumentParser parser = parserMap.get(type);
        // 3. 取不到抛 BusinessException
        if (parser == null) {
            throw new BusinessException(ResultCode.UNSUPPORTED_DOCUMENT_TYPE, "不支持的文档类型：" + fileType);
        }
        return parser;
    }
}
