package com.jujiu.agent.parser;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档解析器工厂。
 *
 * <p>根据文件类型自动路由到对应的 {@link DocumentParser} 实现。
 * 当前实现支持：
 * <ul>
 *     <li>自动发现所有 Spring 管理的解析器</li>
 *     <li>按文件类型建立解析器映射</li>
 *     <li>同一文件类型支持多个解析器</li>
 *     <li>按优先级选择最优解析器</li>
 * </ul>
 *
 * <p>设计目标：
 * <ul>
 *     <li>专用解析器优先</li>
 *     <li>通用兜底解析器作为 fallback</li>
 *     <li>新增格式时无需修改工厂硬编码逻辑</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 12:09
 */
@Component
public class DocumentParserFactory {
    /**
     * 文件类型到解析器列表的映射。
     */
    private final Map<String, List<DocumentParser>> parserMap = new ConcurrentHashMap<>();

    public DocumentParserFactory(List<DocumentParser> parsers) {
        for (DocumentParser parser : parsers) {
            for (String fileType : parser.supportedTypes()) {
                String normalizedType = normalizeType(fileType);
                parserMap.computeIfAbsent(normalizedType,
                        key -> new ArrayList<>()).add(parser);
            }
        }
        parserMap.values().forEach(parserList ->
                parserList.sort(Comparator.comparingInt(DocumentParser::order).reversed()));
    }
    

    /**
     * 获取对应文件类型的解析器。
     *
     * @param fileType 文件类型，如 txt、md、pdf
     * @return 解析器实例
     * @throws BusinessException 不支持的文件类型时抛出
     */
    public DocumentParser getParser(String fileType) {
        String normalizedType = normalizeType(fileType);
        List<DocumentParser> parsers = parserMap.get(normalizedType);
        if (parsers == null || parsers.isEmpty()) {
            throw new BusinessException(ResultCode.UNSUPPORTED_DOCUMENT_TYPE, "不支持的文档类型：" + fileType);
        }
        return parsers.get(0);
    }

    /**
     * 标准化文件类型。
     *
     * @param fileType 原始文件类型
     * @return 标准化后的文件类型
     */
    private String normalizeType(String fileType) {
        return fileType == null ? "" : fileType.trim().toLowerCase();
    }
}
