package com.niro.web.service;

import com.niro.web.dto.UnboxRecordOcrResultDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 开箱记录 OCR 服务
 */
public interface UnboxRecordOcrService {

    UnboxRecordOcrResultDTO recognize(MultipartFile file);
}
