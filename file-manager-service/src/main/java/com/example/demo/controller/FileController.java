package com.example.demo.controller;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.service.FileService;

@RestController
@CrossOrigin(origins = "*")
public class FileController {

    @Autowired
    private FileService fileService;

    // ── Health ────────────────────────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("File Manager Service OK");
    }

    // ── Upload image ──────────────────────────────────────────────────────
    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("error", "Only image files are allowed"));
        }

        try {
            String filename = fileService.saveFile(file);
            String publicUrl = fileService.getFileUrl(filename);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "filename", filename,
                            "originalName", file.getOriginalFilename(),
                            "size", file.getSize(),
                            "url", publicUrl          // full HTTPS Spaces URL
                    ));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to store file: " + e.getMessage()));
        }
    }

    // ── Retrieve image — redirect to Spaces public URL ────────────────────
    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Void> getFile(@PathVariable String filename) {
        String url = fileService.getFileUrl(filename);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }

    // ── List files ────────────────────────────────────────────────────────
    @GetMapping("/files")
    public ResponseEntity<?> listFiles() {
        try {
            return ResponseEntity.ok(fileService.listFiles());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── Delete file ───────────────────────────────────────────────────────
    @DeleteMapping("/files/{filename:.+}")
    public ResponseEntity<?> deleteFile(@PathVariable String filename) {
        boolean deleted = fileService.deleteFile(filename);
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("message", "File deleted: " + filename));
    }
}