package com.jujiu.agent.service.kb.embedding;

/**
 * 嵌入服务提供者客户端接口
 * 
 * <p>定义了文本嵌入（embedding）服务的统一接口，用于将文本转换为向量表示。
 * 不同实现可以对接不同的嵌入服务提供商（如OpenAI、HuggingFace等）。
 * 
 * <p>主要功能：
 * <ul>
 *     <li>支持不同场景的文本嵌入（查询场景、文档场景）</li>
 *     <li>支持多种嵌入模型</li>
 *     <li>返回标准化的向量表示</li>
 * </ul>
 * 
 * <p>使用场景：
 * <ul>
 *     <li>知识库文档的向量化存储</li>
 *     <li>用户查询的向量化匹配</li>
 *     <li>语义相似度计算</li>
 * </ul>
 */
public interface EmbeddingProviderClient {
    /**
     * 将文本转换为向量表示
     * 
     * <p>根据指定的嵌入场景和模型，将输入文本转换为向量数组。
     * 向量表示可用于后续的语义相似度计算、向量检索等操作。
     * 
     * @param text 需要嵌入的文本内容，不能为null或空字符串
     * @param scene 嵌入场景，区分查询场景和文档场景，不同场景可能使用不同的处理策略
     * @param model 嵌入模型标识，指定使用哪个具体的嵌入模型进行转换
     * @return 文本的向量表示，返回的float数组长度取决于具体的嵌入模型
     *         通常为384、768、1024等维度，向量值范围通常在[-1, 1]之间
     * @throws IllegalArgumentException 当text为null或空字符串时抛出
     * @throws EmbeddingException 当嵌入服务调用失败时抛出
     */
    float[] embed(String text, EmbeddingScene scene, String model);
}