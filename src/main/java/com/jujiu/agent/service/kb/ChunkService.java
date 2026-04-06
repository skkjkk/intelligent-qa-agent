package com.jujiu.agent.service.kb;

import com.jujiu.agent.model.entity.KbChunk;

import java.util.List;

/**
 * 文档分块服务。
 *
 * <p>负责将纯文本按固定策略切分为 {@link KbChunk}，
 * 并持久化到数据库。首版策略为固定长度滑动窗口分块。
 *
 * @author 17644
 * @since 2026/4/1
 */
public interface ChunkService {
    /**
     * 对文档内容进行分块并保存。
     *
     * <p>分块策略：
     * <ul>
     *   <li>默认块大小 500 字符</li>
     *   <li>重叠大小 50 字符</li>
     *   <li>顺序分块，索引从 0 开始</li>
     * </ul>
     *
     * @param documentId 文档ID
     * @param content    解析后的纯文本
     * @return 生成的 chunk 列表
     */

    List<KbChunk> split(Long documentId, String content);

    void saveChunks(List<KbChunk> chunks);

    default List<KbChunk> splitAndSave(Long documentId, String content) {
        List<KbChunk> chunks = split(documentId, content);
        saveChunks(chunks);
        return chunks;
    }
}
