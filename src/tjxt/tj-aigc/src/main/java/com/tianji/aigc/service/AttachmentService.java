package com.tianji.aigc.service;

import com.tianji.aigc.attachment.AttachmentContext;
import com.tianji.aigc.vo.AttachmentUploadVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AttachmentService {

    List<AttachmentUploadVO> upload(List<MultipartFile> files);

    AttachmentContext buildContext(List<String> attachmentIds, String question);
}
