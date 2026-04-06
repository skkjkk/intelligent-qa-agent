package com.jujiu.agent.service.kb.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.dto.request.UploadDocumentRequest;
import com.jujiu.agent.model.dto.response.DocumentProcessStatusResponse;
import com.jujiu.agent.model.dto.response.KbDocumentResponse;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.model.entity.KbDocumentProcessLog;
import com.jujiu.agent.model.enums.KbDocumentStatus;
import com.jujiu.agent.model.enums.KbProcessStage;
import com.jujiu.agent.model.enums.KbProcessStatus;
import com.jujiu.agent.model.event.DocumentProcessEvent;
import com.jujiu.agent.mq.DocumentProcessProducer;
import com.jujiu.agent.repository.KbDocumentProcessLogRepository;
import com.jujiu.agent.repository.KbDocumentRepository;
import com.jujiu.agent.service.kb.DocumentService;
import com.jujiu.agent.storage.MinioFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 文档服务与去重/入库实现。
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 14:36
 */
@Service
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    // 支持的文件类型
    private static final List<String> SUPPORTED_FILE_TYPES = Arrays.asList("txt", "pdf", "docx", "html", "md");
    private final MinioFileService minioFileService;
    private final KbDocumentRepository kbDocumentRepository;
    private final KbDocumentProcessLogRepository kbDocumentProcessLogRepository;
    private final DocumentProcessProducer documentProcessProducer;

    public DocumentServiceImpl(MinioFileService minioFileService, 
                               KbDocumentRepository kbDocumentRepository, 
                               KbDocumentProcessLogRepository kbDocumentProcessLogRepository, 
                               DocumentProcessProducer documentProcessProducer) {
        this.minioFileService = minioFileService;
        this.kbDocumentRepository = kbDocumentRepository;
        this.kbDocumentProcessLogRepository = kbDocumentProcessLogRepository;
        this.documentProcessProducer = documentProcessProducer;
    }


    /**
     * 上传文档
     * 处理用户上传文档的完整流程，包括文件类型校验、内容哈希计算、重复检测、
     * 文件存储、数据库记录创建以及触发异步处理流程。整个操作在事务中执行，确保数据一致性。
     *
     * @param userId 当前登录用户 ID，用于标识文档所有者
     * @param file 上传的文件对象，包含文件内容和元数据
     * @param request 上传文档请求对象，包含知识库 ID、标题、可见性等配置信息
     * @return Long 上传成功后的文档 ID
     * @throws BusinessException 当文件类型不支持、文档重复或上传失败时抛出
     */
    @Transactional
    @Override
    public Long uploadDocument(Long userId, MultipartFile file, UploadDocumentRequest request) {
        // 获取原始文件名
        String originalFilename = file.getOriginalFilename();
        // 验证文件类型是否被支持
        String type = validateFileType(originalFilename);
        // 读取文件内容为字节数组
        byte[] fileBytes = readFileBytes(file);
        // 计算文件内容的哈希值，用于去重检测
        String contentHash = computeContentHash(fileBytes);
            
        // 获取知识库 ID，默认为 1
        Long kbId = request.getKbId() == null ? 1L : request.getKbId();
        // 检查同一知识库中是否存在相同内容的文档
        checkDuplicateDocument(kbId, contentHash);
            
        // 生成唯一的对象存储名称并上传到 MinIO
        String objectName = minioFileService.generateObjectName(originalFilename);
        minioFileService.uploadFile(fileBytes, objectName, "application/octet-stream");
            
        // 构建文档实体并保存到数据库
        KbDocument document = buildDocument(userId, request, file, type, objectName, contentHash, originalFilename);
        kbDocumentRepository.insert(document);
    
        // 记录上传成功的处理日志
        insertUploadLog(document.getId());
            
        // 发送 Kafka 事件，触发后续的文档解析和分块处理流程
        DocumentProcessEvent event = buildProcessEvent(document.getId(), userId);
        documentProcessProducer.send(event);
            
        log.info("文档上传成功，documentId={}", document.getId());
        return document.getId();
    }

    /**
     * 构建文档处理事件
     * 创建用于触发异步文档处理流程的事件对象，包含文档 ID、用户 ID、时间戳等关键信息。
     * 该事件将被发送到 Kafka，由消费者执行后续的解析和分块处理。
     *
     * @param documentId 文档 ID，标识需要处理的文档
     * @param userId 用户 ID，标识文档的所有者
     * @return DocumentProcessEvent 构建完成的文档处理事件对象
     */
    private DocumentProcessEvent buildProcessEvent(Long documentId, Long userId) {
        return DocumentProcessEvent.builder()
                .documentId(documentId)
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .build();
    }
    
    /**
     * 插入上传日志
     * 记录文档上传成功的处理日志，包括处理阶段、状态、时间等信息。
     * 日志用于追踪文档处理历史和故障排查。
     *
     * @param documentId 文档 ID，标识需要记录日志的文档
     */
    private void insertUploadLog(Long documentId) {
        // 构建上传成功的处理日志对象
        KbDocumentProcessLog processLog = KbDocumentProcessLog.builder()
                .documentId(documentId)
                .stage(KbProcessStage.UPLOAD.name())
                .status(KbProcessStatus.SUCCESS.name())
                .message("文件上传成功")
                .retryCount(0)
                .startedAt(LocalDateTime.now())
                .endedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        // 保存日志到数据库
        kbDocumentProcessLogRepository.insert(processLog);
    }
    
    
    /**
     * 构建文档实体对象
     * 根据上传请求和文件信息创建 KbDocument 对象，设置默认的初始状态和元数据。
     * 标题为空时使用原始文件名，状态初始化为处理中。
     *
     * @param userId 当前登录用户 ID，用于标识文档所有者
     * @param request 上传文档请求对象，包含知识库 ID、标题、可见性等信息
     * @param file 上传的文件对象，用于获取文件大小
     * @param type 文件类型（扩展名），如 pdf、docx、txt 等
     * @param objectName MinIO 中的对象存储路径
     * @param contentHash 文件内容的哈希值，用于去重检测
     * @param originalFilename 原始文件名
     * @return KbDocument 构建完成的文档实体对象
     */
    private KbDocument buildDocument(Long userId, 
                                     UploadDocumentRequest request,
                                     MultipartFile file, 
                                     String type, 
                                     String objectName, 
                                     String contentHash,
                                     String originalFilename) {

        return KbDocument.builder()
                .kbId(request.getKbId() == null ? 1L : request.getKbId())
                .title(request.getTitle() == null ? originalFilename : request.getTitle())
                .fileType(type)
                .filePath(objectName)
                .fileName(originalFilename)
                .fileSize(file.getSize())
                .contentHash(contentHash)
                .status(KbDocumentStatus.PROCESSING.name())
                .parseStatus(KbProcessStatus.PENDING.name())
                .indexStatus(KbProcessStatus.PENDING.name())
                .chunkCount(0)
                .ownerUserId(userId)
                .visibility(request.getVisibility() == null ? "PRIVATE" : request.getVisibility())
                .enabled(1)
                .deleted(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 检查文档是否重复
     * 根据知识库 ID 和文件内容哈希值查询数据库，判断同一知识库中是否已存在相同内容的文档。
     * 如果存在则抛出文档重复异常，阻止重复上传。
     *
     * @param kbId 知识库 ID，用于限定检查范围
     * @param contentHash 文件内容哈希值，用于唯一标识文件内容
     * @throws BusinessException 当文档已存在时抛出，错误码为 DOCUMENT_DUPLICATE
     */
    private void checkDuplicateDocument(Long kbId, String contentHash) {
        // 查询数据库中是否存在相同内容的文档
        KbDocument existing = kbDocumentRepository.selectOne(
                new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getKbId, kbId)
                        .eq(KbDocument::getContentHash, contentHash)
                        .eq(KbDocument::getDeleted, 0)
                        .last("LIMIT 1")
        );
        // 如果存在未删除的重复文档，抛出异常
        if (existing != null) {
            throw new BusinessException(ResultCode.DOCUMENT_DUPLICATE, "文档已存在");
        }
    }
    
    /**
     * 验证文件类型
     * 从文件名中提取文件类型，并检查是否在支持的文件类型列表中。
     * 如果不支持则抛出业务异常，阻止后续上传操作。
     *
     * @param fileName 原始文件名，用于提取文件扩展名
     * @return String 验证通过的文件类型（小写扩展名）
     * @throws BusinessException 当文件类型不被支持时抛出
     */
    private String validateFileType(String fileName){
        // 从文件名提取文件类型
        String type = extractFileType(fileName);
        // 检查文件类型是否被支持
        if (!SUPPORTED_FILE_TYPES.contains(type)) {
            throw new BusinessException(ResultCode.UNSUPPORTED_DOCUMENT_TYPE, "不支持的文档类型：" + type);
        }
        return type;
    }
    
    /**
     * 读取文件字节数组
     * 从上传的文件对象中读取完整的文件内容，转换为字节数组供后续处理使用。
     * 如果读取失败则抛出文件读取异常。
     *
     * @param file 上传的文件对象，包含文件内容和元数据
     * @return byte[] 文件的完整字节数组
     * @throws BusinessException 当文件读取失败时抛出，错误码为 FILE_READ_ERROR
     */
    private byte[] readFileBytes(MultipartFile file) {
        byte[] fileBytes;
        try {
            // 从 MultipartFile 中读取字节数组
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FILE_READ_ERROR, "读取文件内容失败");
        }
        return fileBytes;
    }
    
            
    /**
     * 获取文档详情
     * 根据文档 ID 查询文档信息，并校验当前用户是否有访问权限。
     * 只有文档存在且属于当前用户时才返回详细信息，否则抛出异常。
     *
     * @param userId 当前登录用户 ID，用于权限校验
     * @param documentId 文档 ID，需要查询的文档标识
     * @return KbDocumentResponse 文档详情响应对象，包含完整的文档元数据
     * @throws BusinessException 当文档不存在或用户无权访问时抛出
     */
    @Override
    public KbDocumentResponse getDocument(Long userId, Long documentId) {
        // 根据文档 ID 查询数据库
        KbDocument document = kbDocumentRepository.selectById(documentId);
        // 校验文档是否存在且属于当前用户
        if (document != null && userId.equals(document.getOwnerUserId()) && document.getDeleted() == 0) {
            // 转换为响应对象返回
            return KbDocumentResponse.builder()
                    .id(document.getId())
                    .title(document.getTitle())
                    .fileName(document.getFileName())
                    .fileType(document.getFileType())
                    .fileSize(document.getFileSize())
                    .status(document.getStatus())
                    .parseStatus(document.getParseStatus())
                    .indexStatus(document.getIndexStatus())
                    .chunkCount(document.getChunkCount())
                    .visibility(document.getVisibility())
                    .createdAt(document.getCreatedAt())
                    .build();
        } else {
            log.warn("文档不存在或无权访问，documentId={}, userId={}", documentId, userId);
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
    }

    /**
     * 查询文档列表
     * 根据用户 ID 和知识库 ID 查询文档列表，支持按知识库筛选。
     * 返回指定用户的所有未删除文档，按创建时间倒序排列。
     *
     * @param userId 当前登录用户 ID，用于过滤文档所有者
     * @param kbId 知识库 ID，可选参数，为 null 时返回所有知识库的文档
     * @return List<KbDocumentResponse> 文档列表响应对象，如果无数据返回空列表
     * @throws BusinessException 当 userId 为 null 时抛出参数校验异常
     */
    @Override
    public List<KbDocumentResponse> listDocuments(Long userId, Long kbId) {
        // 校验用户 ID 不为空
        if (userId == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "用户 ID 不能为空");
        }
            
        log.info("查询文档列表，userId={}, kbId={}", userId, kbId);
            
        // 构建查询条件
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getOwnerUserId, userId)
                .eq(KbDocument::getDeleted, 0)
                .orderByDesc(KbDocument::getCreatedAt);
            
        // 如果指定了知识库 ID，添加筛选条件
        if (kbId != null) {
            wrapper.eq(KbDocument::getKbId, kbId);
        }
    
        // 执行查询
        List<KbDocument> documents = kbDocumentRepository.selectList(wrapper);
            
        log.info("查询文档列表结果，userId={}, kbId={}, count={}", userId, kbId, documents.size());
            
        // 转换为响应对象并返回（空列表时直接返回，不抛异常）
        return documents.stream()
                .map(document -> KbDocumentResponse.builder()
                        .id(document.getId())
                        .title(document.getTitle())
                        .fileName(document.getFileName())
                        .fileType(document.getFileType())
                        .fileSize(document.getFileSize())
                        .status(document.getStatus())
                        .parseStatus(document.getParseStatus())
                        .indexStatus(document.getIndexStatus())
                        .chunkCount(document.getChunkCount())
                        .visibility(document.getVisibility())
                        .createdAt(document.getCreatedAt())
                        .build())
                .toList();
    }

    /**
     * 删除文档
     * 执行逻辑删除操作，包括在数据库中标记文档为已删除。
     * 删除前会校验文档是否存在、是否属于当前用户以及是否已被删除，确保操作的安全性。
     * 整个操作在事务中执行，保证文件删除和数据库更新的一致性。
     *
     * @param userId 当前登录用户 ID，用于权限校验
     * @param documentId 需要删除的文档 ID
     * @throws BusinessException 当文档不存在、无权访问或已被删除时抛出
     */
    @Transactional
    @Override
    public void deleteDocument(Long userId, Long documentId) {
        // 根据文档 ID 查询数据库
        KbDocument document = kbDocumentRepository.selectById(documentId);
    
        // 校验文档是否存在、属于当前用户且未被删除
        if (document == null || !userId.equals(document.getOwnerUserId()) || document.getDeleted() == 1) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        
        // 逻辑删除文档，标记为已删除并更新时间戳
        document.setDeleted(1);
        document.setUpdatedAt(LocalDateTime.now());
        kbDocumentRepository.updateById(document);
    }

    /**
     * 获取文档处理状态
     * 查询文档的当前处理状态，包括上传、解析、索引等各个阶段的进度信息。
     * 用于前端展示文档处理的实时状态，让用户了解文档是否已完成处理。
     *
     * @param userId 当前登录用户 ID，用于权限校验
     * @param documentId 需要查询状态的文档 ID
     * @return DocumentProcessStatusResponse 文档处理状态响应对象，包含各阶段状态和分块数量
     * @throws BusinessException 当文档不存在、无权访问或已被删除时抛出
     */
    @Override
    public DocumentProcessStatusResponse getDocumentStatus(Long userId, Long documentId) {
        // 根据文档 ID 查询数据库
        KbDocument document = kbDocumentRepository.selectById(documentId);
        // 校验文档是否存在、属于当前用户且未被删除
        if (document == null || !userId.equals(document.getOwnerUserId()) || document.getDeleted() == 1) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        // 构建并返回处理状态响应对象
        return DocumentProcessStatusResponse.builder()
                .documentId(document.getId())
                .status(document.getStatus())
                .parseStatus(document.getParseStatus())
                .indexStatus(document.getIndexStatus())
                .chunkCount(document.getChunkCount())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    /**
     * 提取文件后缀。
     *
     * @param filename 原始文件名
     * @return 小写后缀，如 txt、pdf
     */
    private String extractFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 计算文件内容哈希。
     *
     * <p>首版采用 MD5，足够满足去重需求。
     *
     * @param fileBytes 上传文件
     * @return 十六进制哈希字符串
     */
    private String computeContentHash(byte[] fileBytes) {
        try {
            return DigestUtil.md5Hex(fileBytes);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.FILE_HASH_ERROR, "文件哈希计算失败: " + e.getMessage());
        }
    }
}
