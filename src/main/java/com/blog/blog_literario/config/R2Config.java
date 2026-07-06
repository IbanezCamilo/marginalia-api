package com.blog.blog_literario.config;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.blog.blog_literario.config.properties.R2Properties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Builds the {@link S3Client} used to talk to Cloudflare R2 over its S3-compatible API.
 *
 * <p>Only instantiated when {@code storage.active=r2}, so neither the local profile nor
 * the test slices create an S3 client (or require R2 credentials).
 */
@Configuration
@ConditionalOnProperty(name = "storage.active", havingValue = "r2")
public class R2Config {

    /**
     * R2 requires a few non-default settings versus plain AWS S3:
     * <ul>
     *   <li>{@code region("auto")} — R2 rejects real AWS regions.</li>
     *   <li>{@code chunkedEncodingEnabled(false)} — the SDK's default chunked transfer
     *       encoding breaks the request signature against R2 and yields a
     *       {@code 403 SignatureDoesNotMatch} on {@code PutObject}.</li>
     *   <li>{@code pathStyleAccessEnabled(true)} — R2 uses path-style bucket addressing.</li>
     * </ul>
     */
    @Bean
    public S3Client r2Client(R2Properties props) {
        AwsBasicCredentials credentials =
                AwsBasicCredentials.create(props.accessKeyId(), props.secretAccessKey());

        S3Configuration serviceConfig = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false) // required for R2
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(
                        "https://" + props.accountId() + ".r2.cloudflarestorage.com"))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of("auto")) // R2 requires "auto"
                .serviceConfiguration(serviceConfig)
                .build();
    }
}
