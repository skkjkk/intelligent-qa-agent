package com.jujiu.agent.service.kb.embedding;


import com.jujiu.agent.service.kb.EmbeddingService;

/**
 * 嵌入场景枚举
 * 
 * <p>用于区分不同类型文本的嵌入（embedding）场景，
 * 不同场景可能需要使用不同的嵌入模型或处理策略。
 * 
 * <p>主要用途：
 * <ul>
 *     <li>QUERY: 用于用户查询文本的嵌入，通常需要考虑查询的特殊性</li>
 *     <li>DOCUMENT: 用于文档内容的嵌入，通常需要处理更长的文本内容</li>
 * </ul>
 * @author Jujiu
 * @since 1.0.0
 * @see EmbeddingService
 */
public enum EmbeddingScene {
    /**
     * 查询场景
     * 
     * <p>用于用户输入的查询文本嵌入。
     * 特点：
     * <ul>
     *     <li>文本通常较短，包含用户的问题或搜索意图</li>
     *     <li>可能包含口语化表达或不完整的句子</li>
     *     <li>需要与文档嵌入进行语义匹配</li>
     *     <li>可能使用专门针对查询优化的嵌入模型</li>
 * </ul>
     */
    QUERY,
    
    /**
     * 文档场景
     * 
     * <p>用于知识库文档内容的嵌入。
     * 特点：
     * <ul>
     *     <li>文本通常较长，包含完整的段落或章节内容</li>
     *     <li>结构相对正式，语法完整</li>
     *     <li>需要支持高效的相似度检索</li>
     *     <li>可能使用专门针对文档优化的嵌入模型</li>
 *     <li>通常会被切分成多个chunk进行嵌入</li>
 * </ul>
     */
    DOCUMENT
}