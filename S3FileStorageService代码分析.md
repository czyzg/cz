# S3FileStorageService 代码分析

## 📋 类概述
这是一个 **AWS S3 文件存储服务实现类**，实现了 `FileStorageService` 接口。主要功能是处理文件的上传、下载和删除操作，采用了高效的流式处理和预签名URL机制。

---

## 🎯 核心功能模块

### 1️⃣ 多种上传方式（重载方法）

#### **方式一：上传 MultipartFile**
```java
upload(String bucketName, MultipartFile file)
```
- 用途：处理 HTTP 文件上传
- 工作流程：
  - 提取文件名和大小
  - 使用 Apache Tika 库自动检测文件类型
  - 调用底层流式上传方法
- **亮点**：每次调用 `getInputStream()` 都会返回新流，避免重复创建临时文件

#### **方式二：上传 InputStream**
```java
upload(String bucketName, InputStream content, long size, String originalFilename, String contentType)
```
- 用途：流式数据上传（数据库BLOB、网络流等）
- 特点：需要显式提供大小参数

#### **方式三：上传字节数组**
```java
upload(String bucketName, byte[] content, String originalFilename, String contentType)
```
- 用途：内存中的字节数据上传
- 优化：使用 `ByteArrayInputStream` 包装，避免额外拷贝

---

### 2️⃣ 流式上传核心实现（性能关键）

#### **预签名URL机制** ⭐⭐⭐
```java
private StoredFileDTO streamUploadToS3(...)
```

**为什么选择预签名URL而不用SDK的putObject？**

| 方案 | 内存占用 | 优点 | 缺点 |
|------|--------|------|------|
| **SDK putObject** | 高（整个payload缓冲） | 简单直接 | 大文件易OOM |
| **预签名URL** | 低（仅buffer大小） | 支持超大文件 | 实现复杂度高 |

**核心原理：**
1. S3Presigner 生成带签名的临时URL（鉴权信息在URL参数）
2. 使用 `HttpURLConnection.setFixedLengthStreamingMode()` 设置内容长度
3. 流式写入 HTTP 请求体
4. Payload 使用 `UNSIGNED-PAYLOAD`，无需预读计算SHA-256

**内存效率对比：**
```
SDK putObject:    [File] → [内存缓冲区] → [S3]
预签名URL:        [File] → [小buffer] ↻ → [HTTP PUT] → [S3]
```

#### **流式HTTP PUT上传**
```java
private void streamPutViaPresignedUrl(...)
```
- 设置 HTTP 连接参数（超时、流式模式）
- 添加预签名头部信息
- 使用 `InputStream.transferTo()` 高效转移数据
- 检验响应状态码，详细错误日志

---

### 3️⃣ 其他操作

#### **打开流读取**
```java
openStream(String url) → InputStream
```
- 直接返回S3对象流，支持边下载边处理

#### **删除文件**
```java
deleteByUrl(String url)
```
- 解析S3 URL获取 bucket/key
- 调用 SDK 删除接口

#### **可靠上传**
```java
reliableUpload(...)
```
- 使用 SDK 原生 `putObject()` 方法
- 适合小文件，实现简单

---

## 🔧 工具方法详解

### **S3 URL 解析**
```java
private S3Location parseS3Url(String url)
```
- 支持格式：`s3://bucket-name/object-key`
- 验证 scheme、bucket、key 有效性
- 返回 record 类型（Java 14+）

### **文件名处理**
```java
private String generateS3Key(String originalFilename)
```
- 提取文件后缀
- 使用 UUID 作为唯一标识
- 结果格式：`xxxxxxxxxxxxxxxxxxxxxxxx.pdf` 或 `yyyyyyyyyyyyyyyyyyyyyyyy`

### **文件类型检测**
```java
private String resolveContentType(String originalFilename, String contentType)
```
- 优先使用入参的 contentType
- 其次用 Tika 根据文件名检测
- 最后返回 null（S3会自动识别）

---

## 💡 技术亮点

### ✅ 内存优化
- **流式处理**：避免整个文件加载到内存
- **固定长度模式**：`setFixedLengthStreamingMode()` 确保真正的流式发送
- **Tika 前缀探测**：只读前几KB检测文件类型

### ✅ 错误处理
```java
int code = conn.getResponseCode();
if (code < 200 || code >= 300) {
    String errorBody = readErrorStream(conn);
    throw new IOException("S3 流式上传失败: HTTP " + code);
}
```
- 完整的HTTP状态检查
- 读取错误响应体详情

### ✅ 超时控制
```java
private static final int CONNECT_TIMEOUT_MS = 10_000;   // 10秒连接超时
private static final int READ_TIMEOUT_MS = 60_000;      // 60秒读取超时
```

### ✅ 安全验证
- 参数空值检查（使用 Hutool Assert）
- bucket 名称验证
- URL scheme 验证

---

## ⚠️ 可能的改进点

### 1. **断点续传支持**
```
当前：不支持。建议增加分块上传（Multipart Upload）逻辑
```

### 2. **重试机制**
```
当前：HTTP 请求失败直接抛异常。建议使用 Resilience4j 或 Retry4j
```

### 3. **性能监控**
```java
// 建议添加
StopWatch watch = new StopWatch();
watch.start();
streamPutViaPresignedUrl(...);
watch.stop();
log.info("Upload took {} ms", watch.getTotalTimeMillis());
```

### 4. **连接池配置**
```
当前：每次创建新 HttpURLConnection。建议使用 OkHttp 或 HttpClient 连接池
```

### 5. **异步上传支持**
```
当前：同步阻塞。可增加异步方法返回 CompletableFuture<StoredFileDTO>
```

---

## 📊 依赖分析

| 库 | 用途 | 版本范围 |
|----|----|---------|
| AWS SDK v2 | S3 客户端和预签名 | 2.40.x+ |
| Hutool | 参数验证工具 | 5.x |
| Apache Tika | 文件类型检测 | 2.x |
| Lombok | 代码生成 | 1.18.x |
| Spring | 依赖注入、Web | 6.x |

---

## 🚀 使用示例

### MultipartFile 上传
```java
@PostMapping("/upload")
public StoredFileDTO upload(
    @RequestParam String bucket,
    @RequestParam MultipartFile file) {
    return storageService.upload(bucket, file);
}
```

### InputStream 上传
```java
// 从网络流或数据库读取数据
try (InputStream is = dataSource.getInputStream()) {
    StoredFileDTO result = storageService.upload(
        "my-bucket", is, size, "document.pdf", "application/pdf"
    );
}
```

### 文件删除
```java
storageService.deleteByUrl("s3://my-bucket/xxxxxxx.pdf");
```

---

## 总结

这是一个**生产级别的S3文件存储实现**，核心优势是：
- ✅ **内存高效**：预签名URL + 流式上传
- ✅ **灵活多样**：支持3种上传方式
- ✅ **健壮稳定**：完整的验证和错误处理
- ⚠️  **可继续优化**：可添加断点续传、重试、异步等特性
