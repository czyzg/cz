package com.cz.tika;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    @Autowired
    private TikaParseService tikaParseService;

    /**
     * 解析上传的文档，返回文本和元数据
     *
     * POST /api/document/parse
     * Content-Type: multipart/form-data
     */
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ParseResult> parseDocument(@RequestParam("file") MultipartFile file) {
        ParseResult result = tikaParseService.parseFile(file);

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 仅检测文件的 MIME 类型
     *
     * POST /api/document/detect
     * Content-Type: multipart/form-data
     */
    @PostMapping(value = "/detect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> detectMimeType(@RequestParam("file") MultipartFile file) {
        try {
            String mimeType = tikaParseService.detectMimeType(file);

            Map<String, String> response = new HashMap<>();
            response.put("filename", file.getOriginalFilename());
            response.put("mimeType", mimeType);
            response.put("size", String.valueOf(file.getSize()));

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "无法检测文件类型: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
