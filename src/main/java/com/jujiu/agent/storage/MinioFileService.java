package com.jujiu.agent.storage;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import io.minio.*;
import io.minio.errors.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

/**
 *
 * 基于 MinIO 的文件存储服务。
 *
 * <p>负责知识库原始文件的上传、删除与读取。
 * 启动时自动校验并创建存储桶，屏蔽底层 SDK 细节。
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 10:23
 */
@Service
@Slf4j
public class MinioFileService {
    
    private final String bucketName;
    private final MinioClient minioClient;

    public MinioFileService(KnowledgeBaseProperties properties, MinioClient minioClient) {
        this.bucketName = properties.getMinio().getBucketName();
        this.minioClient = minioClient;
    }
    
    /**
     * 初始化 MinIO 存储桶
     * 检查指定的存储桶是否存在，若不存在则自动创建。
     * 该方法确保在进行文件操作前存储桶已经就绪，避免后续操作失败。
     */
    private void initializeBucket(){
       log.info("Initializing MinIO bucket... {}", bucketName);
        try {
            // 检查存储桶是否已存在
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );
            // 如果存储桶不存在则创建
            if (!exists) {
                try {
                    log.info("Bucket does not exist, creating... {}", bucketName);
                    minioClient.makeBucket(
                            MakeBucketArgs.builder()
                                    .bucket(bucketName)
                                    .build()
                    );
                } catch (io.minio.errors.ErrorResponseException e) {
                    String code = e.errorResponse().code();
                    if (!"BucketAlreadyOwnedByYou".equals(code) && !"BucketAlreadyExists".equals(code)) {
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO bucket: {}", bucketName, e);
            throw new BusinessException(ResultCode.MINIO_BUCKET_INIT_FAILED, "存储桶初始化失败：" + e.getMessage());
        }
            
    }

    /**
     * 上传文件到 MinIO
     * 将字节数组上传到 MinIO 存储桶，支持指定对象名称和内容类型。
     * 上传前会自动初始化存储桶，确保存储空间存在。
     *
     * @param data 文件数据的字节数组
     * @param objectName 对象名称，用于在 MinIO 中标识文件（建议使用 generateObjectName 生成）
     * @param contentType 文件的 MIME 类型，如 "application/pdf"、"image/png" 等
     * @return String 上传成功后的对象名称
     * @throws BusinessException 当 MinIO 初始化失败或上传失败时抛出
     */
    public String uploadFile(byte[] data, String objectName, String contentType) {
        // 初始化存储桶，确保存储空间存在
        initializeBucket();
            
        log.info("Uploading file: {}", objectName);
        try {
            // 构建并执行上传请求，将字节流写入 MinIO
            ObjectWriteResponse put = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(data), data.length, -1)
                            .contentType(contentType)
                            .build()
            );
    
            // 返回上传成功的对象名称
            return put.object();
        } catch (Exception e) {
            log.error("Failed to upload file: {}", objectName, e);
            throw new BusinessException(ResultCode.MINIO_FILE_UPLOAD_FAILED, "文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 删除文件。
     *
     * @param objectName 对象名
     */
    public void deleteFile(String objectName) {
        log.info("Deleting file: {}", objectName);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
        } catch (Exception e) {
            log.error("Failed to delete file: {}", objectName, e);
            throw new BusinessException(ResultCode.MINIO_FILE_DELETE_FAILED, "文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件输入流，供解析器读取。
     *
     * @param objectName 对象名
     * @return 文件输入流
     */
    public InputStream getObjectStream(String objectName) {
        log.info("Getting object stream: {}", objectName);
        try {
            // 1. 用 minioClient.getObject(...)
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to get object stream: {}", objectName, e);
            throw new BusinessException(ResultCode.MINIO_FILE_GET_FAILED, "文件获取失败: " + e.getMessage());
        } 
    }

    /**
     * 生成唯一对象名。
     *
     * <p>格式：{@code kb/{uuid}.{ext}}，避免原始文件名中的特殊字符导致兼容性问题。
     *
     * @param originalFilename 原始文件名
     * @return 安全的对象存储路径
     */
    public String generateObjectName(String originalFilename) {
        String ext = "";
        // 1. 获取文件扩展名
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        // 2. 生成并返回对象名
        return "kb/" + UUID.randomUUID() + ext;
    }
}
