package com.harmony.store.storage;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;

@Slf4j
@Service
public class StorageService {

    private static final String BUCKET          = "mmanja-com-online-store";
    private static final String PRODUCT_IMG_DIR = "static/images/products";

    private final MinioClient client;

    public StorageService(
            @Value("${app.minio.endpoint:localhost}") String endpoint,
            @Value("${app.minio.port:9000}") int port,
            @Value("${app.minio.access-key:minioadmin}") String accessKey,
            @Value("${app.minio.secret-key:minioadmin}") String secretKey,
            @Value("${app.minio.use-ssl:false}") boolean useSsl) {
        this.client = MinioClient.builder()
                .endpoint(endpoint, port, useSsl)
                .credentials(accessKey, secretKey)
                .build();
    }

    @PostConstruct
    public void init() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
                log.info("Created MinIO bucket: {}", BUCKET);
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO bucket: {}", e.getMessage());
        }
    }

    // ── Upload product image ──────────────────────────────────────────────────

    public String uploadProductImage(String productId, MultipartFile file) {
        String ext        = getExtension(file.getOriginalFilename());
        String objectName = PRODUCT_IMG_DIR + "/" + productId + ext;

        try (InputStream stream = file.getInputStream()) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(BUCKET)
                    .object(objectName)
                    .stream(stream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.info("Uploaded product image: {}", objectName);
        } catch (Exception e) {
            log.error("Failed to upload product image: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Image upload failed: " + e.getMessage());
        }

        return "/" + objectName;
    }

    // ── Delete product image ──────────────────────────────────────────────────

    public void deleteProductImage(String imageUrl) {
        if (imageUrl == null) return;
        String objectName = imageUrl.replaceFirst("^/", "");
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(BUCKET)
                    .object(objectName)
                    .build());
            log.info("Deleted product image: {}", objectName);
        } catch (Exception e) {
            log.warn("Could not delete image {}: {}", objectName, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }
}
