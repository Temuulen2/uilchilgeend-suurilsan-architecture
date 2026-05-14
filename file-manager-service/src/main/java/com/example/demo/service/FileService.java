package com.example.demo.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import jakarta.annotation.PostConstruct;

@Service
public class FileService {

    @Value("${spaces.access.key:}")
    private String accessKey;

    @Value("${spaces.secret.key:}")
    private String secretKey;

    @Value("${spaces.endpoint:https://sgp1.digitaloceanspaces.com}")
    private String endpoint;

    @Value("${spaces.region:sgp1}")
    private String region;

    @Value("${spaces.bucket:}")
    private String bucket;

    @Value("${spaces.cdn.base:}")
    private String cdnBase;

    private AmazonS3 s3Client;

    @PostConstruct
    public void init() {
        BasicAWSCredentials creds = new BasicAWSCredentials(accessKey, secretKey);
        s3Client = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
                .withCredentials(new AWSStaticCredentialsProvider(creds))
                .withPathStyleAccessEnabled(true)
                .build();
    }

    public String saveFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String filename = UUID.randomUUID().toString() + ext;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());

        PutObjectRequest putRequest = new PutObjectRequest(bucket, filename, file.getInputStream(), metadata)
                .withCannedAcl(CannedAccessControlList.PublicRead);

        s3Client.putObject(putRequest);
        return filename;
    }

    /** Returns the public HTTPS URL of the file on Spaces CDN */
    public String getFileUrl(String filename) {
        // Virtual-hosted style: https://{bucket}.{region}.digitaloceanspaces.com/{filename}
        return cdnBase.replaceAll("/$", "") + "/" + filename;
    }

    public List<String> listFiles() {
        try {
            ObjectListing listing = s3Client.listObjects(bucket);
            return listing.getObjectSummaries()
                    .stream()
                    .map(S3ObjectSummary::getKey)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Spaces list failed: " + e.getMessage(), e);
        }
    }

    public boolean deleteFile(String filename) {
        try {
            s3Client.deleteObject(bucket, filename);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}