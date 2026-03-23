package com.cz.tika;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Setter
@Getter
public class ParseResult {

    /**
     * 是否解析成功
     */
    private boolean success;

    /**
     * 检测到的 MIME 类型
     */
    private String mimeType;

    /**
     * 提取的文本内容
     */
    private String content;

    /**
     * 提取的元数据
     */
    private Map<String, String> metadata;

    /**
     * 文本长度（字符数）
     */
    private int contentLength;

    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;

    // 静态工厂方法
    public static ParseResult success(String mimeType, String content, Map<String, String> metadata) {
        ParseResult result = new ParseResult();
        result.setSuccess(true);
        result.setMimeType(mimeType);
        result.setContent(content);
        result.setContentLength(content != null ? content.length() : 0);
        result.setMetadata(metadata);
        return result;
    }

    public static ParseResult failure(String errorMessage) {
        ParseResult result = new ParseResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
