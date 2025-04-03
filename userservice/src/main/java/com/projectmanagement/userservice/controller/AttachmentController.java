package com.projectmanagement.userservice.controller;

import com.projectmanagement.userservice.dto.AttachmentDto;
import com.projectmanagement.userservice.dto.MessageResponse;
import com.projectmanagement.userservice.entity.Attachment;
import com.projectmanagement.userservice.entity.User;
import com.projectmanagement.userservice.service.AttachmentService;
import com.projectmanagement.userservice.service.AuthService;
import com.projectmanagement.userservice.service.IssueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/attachments")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AttachmentController {

    @Autowired
    private AttachmentService attachmentService;
    
    @Autowired
    private IssueService issueService;
    
    @Autowired
    private AuthService authService;
    
    @GetMapping("/issue/{issueId}")
    @PreAuthorize("@securityService.canModifyIssue(#issueId, principal)")
    public ResponseEntity<?> getAttachmentsByIssue(@PathVariable Long issueId) {
        return issueService.getIssueById(issueId)
                .map(issue -> {
                    List<Attachment> attachments = attachmentService.getAttachmentsByIssue(issue);
                    List<AttachmentDto> attachmentDtos = attachments.stream()
                            .map(this::convertToDto)
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(attachmentDtos);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/upload")
    @PreAuthorize("@securityService.canModifyIssue(#issueId, principal)")
    public ResponseEntity<?> uploadAttachment(
            @RequestParam("file") MultipartFile file,
            @RequestParam("issueId") Long issueId) {
        
        User currentUser = authService.getCurrentUser();
        
        try {
            return issueService.getIssueById(issueId)
                    .map(issue -> {
                        try {
                            // Kiểm tra giới hạn dung lượng lưu trữ (nếu cần)
                            int maxStorageGB = currentUser.getSubscription().getMaxStorageGB();
                            if (maxStorageGB > 0 && file.getSize() > maxStorageGB * 1024L * 1024L * 1024L) {
                                return ResponseEntity.badRequest()
                                        .body(new MessageResponse("File size exceeds your storage limit"));
                            }
                            
                            Attachment attachment = attachmentService.saveAttachment(file, issue, currentUser);
                            return ResponseEntity.ok(convertToDto(attachment));
                        } catch (IOException e) {
                            return ResponseEntity.badRequest().body(new MessageResponse("Failed to upload file: " + e.getMessage()));
                        }
                    })
                    .orElse(ResponseEntity.badRequest().body(new MessageResponse("Issue not found")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse("Failed to upload file: " + e.getMessage()));
        }
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.canManageAttachment(#id, principal)")
    public ResponseEntity<?> deleteAttachment(@PathVariable Long id) {
        try {
            if (!attachmentService.getAttachmentById(id).isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            attachmentService.deleteAttachment(id);
            return ResponseEntity.ok(new MessageResponse("Attachment deleted successfully"));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(new MessageResponse("Failed to delete attachment: " + e.getMessage()));
        }
    }
    
    private AttachmentDto convertToDto(Attachment attachment) {
        AttachmentDto dto = new AttachmentDto();
        dto.setId(attachment.getId());
        dto.setFileName(attachment.getFileName());
        dto.setFileUrl(attachment.getFileUrl());
        dto.setFileSize(attachment.getFileSize());
        dto.setUploadDate(attachment.getUploadDate());
        dto.setIssueId(attachment.getIssue().getId());
        dto.setUploaderId(attachment.getUploader().getId());
        return dto;
    }
}