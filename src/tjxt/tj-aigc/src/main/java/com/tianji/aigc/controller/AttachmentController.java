package com.tianji.aigc.controller;

import com.tianji.aigc.service.AttachmentService;
import com.tianji.aigc.vo.AttachmentUploadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/attachment")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping("/upload")
    public List<AttachmentUploadVO> upload(@RequestParam("files") MultipartFile[] files) {
        return attachmentService.upload(Arrays.asList(files));
    }
}
