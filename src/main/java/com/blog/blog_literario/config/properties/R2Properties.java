package com.blog.blog_literario.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code storage.r2.*} from {@code application.properties}.
 *
 * <p>Only used when {@code storage.active=r2}. The credentials come from a
 * bucket-scoped Cloudflare R2 API token (never an Admin token) and must be
 * supplied as environment variables, never committed to the repository.
 *
 * @param accountId       Cloudflare account ID; forms the S3 endpoint host
 *                        {@code https://<accountId>.r2.cloudflarestorage.com}
 * @param accessKeyId     R2 token Access Key ID
 * @param secretAccessKey R2 token Secret Access Key
 * @param bucketName      target bucket (e.g. {@code marginalia-media})
 * @param publicBaseUrl   custom-domain base URL used to build public image URLs
 */
@ConfigurationProperties(prefix = "storage.r2")
public record R2Properties(
        String accountId,
        String accessKeyId,
        String secretAccessKey,
        String bucketName,
        String publicBaseUrl
) {}
