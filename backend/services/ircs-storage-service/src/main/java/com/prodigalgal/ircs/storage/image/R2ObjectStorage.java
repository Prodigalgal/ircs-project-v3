package com.prodigalgal.ircs.storage.image;

import com.prodigalgal.ircs.common.outbound.OutboundHttpException;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundProxy;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import jakarta.annotation.PreDestroy;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.http.apache5.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@Slf4j
public class R2ObjectStorage {

    private static final int MAX_ROUTED_CLIENTS = 17; // At most 16 configured proxy endpoints and the internal/direct route.
    private final boolean enabled;
    private final String bucketName;
    private final String accountId;
    private final String accessKey;
    private final String secretKey;
    private final String endpoint;
    private final PublicOutboundProxyPool proxyPool;
    private final R2S3ClientPool clients = new R2S3ClientPool(MAX_ROUTED_CLIENTS);

    public R2ObjectStorage(
            PublicOutboundProxyPool proxyPool,
            @Value("${app.storage.r2.enabled:false}") boolean enabled,
            @Value("${app.storage.r2.bucket-name:}") String bucketName,
            @Value("${app.storage.r2.account-id:}") String accountId,
            @Value("${app.storage.r2.access-key:}") String accessKey,
            @Value("${app.storage.r2.secret-key:}") String secretKey,
            @Value("${app.storage.r2.endpoint:}") String endpoint) {
        this.enabled = enabled;
        this.bucketName = bucketName;
        this.accountId = accountId;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.endpoint = endpoint;
        this.proxyPool = proxyPool;
    }

    public boolean isActive() {
        return enabled
                && StringUtils.hasText(bucketName)
                && StringUtils.hasText(accessKey)
                && StringUtils.hasText(secretKey)
                && (StringUtils.hasText(endpoint) || StringUtils.hasText(accountId));
    }

    public boolean exists(String storagePath) {
        if (!StringUtils.hasText(storagePath) || !isActive()) {
            return false;
        }
        try {
            return withClient(client -> {
                client.headObject(HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(extractKey(storagePath))
                        .build());
                return true;
            });
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) return false;
            throw ex;
        }
    }

    public void store(byte[] data, String storagePath, String contentType) {
        if (!isActive()) {
            throw new IllegalStateException("R2 storage is disabled or incomplete");
        }
        withClient(client -> {
            client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(extractKey(storagePath))
                            .contentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream")
                            .build(),
                    RequestBody.fromBytes(data));
            log.info("Stored R2 storage object: {}", storagePath);
            return null;
        });
    }

    void deleteCover(String bucket, String key) {
        if (!isActive() || !bucketName.equals(bucket)) {
            throw new IllegalStateException("The registered media bucket is unavailable");
        }
        withClient(client -> client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(extractKey(key)).build()));
    }

    private <T> T withClient(Function<S3Client, T> operation) {
        URI target = targetEndpoint();
        OutboundProxy proxy = selectProxy(target);
        try {
            return clients.execute(proxy, () -> buildClient(target, proxy), operation);
        } catch (SdkClientException ex) {
            if (proxy.enabled()) proxyPool.recordFailure(proxy);
            throw ex;
        } catch (S3Exception ex) {
            if (proxy.enabled() && Set.of(502, 503, 504).contains(ex.statusCode())) proxyPool.recordFailure(proxy);
            throw ex;
        }
    }

    private OutboundProxy selectProxy(URI target) {
        if (isInternalEndpoint(target)) return OutboundProxy.disabled();
        try {
            return proxyPool.appliesTo(OutboundHttpRequest.get(target, OutboundHttpPolicy.publicFetch(Duration.ofSeconds(60))))
                    ? proxyPool.select() : OutboundProxy.disabled();
        } catch (OutboundHttpException ex) {
            throw new IllegalStateException("R2 public proxy route is unavailable; direct fallback is disabled", ex);
        }
    }

    private URI targetEndpoint() {
        URI target = URI.create(StringUtils.hasText(endpoint) ? endpoint : "https://" + accountId + ".r2.cloudflarestorage.com");
        if (target.getHost() == null || target.getUserInfo() != null
                || !("https".equalsIgnoreCase(target.getScheme()) || "http".equalsIgnoreCase(target.getScheme()))) {
            throw new IllegalStateException("R2 endpoint must be an HTTP(S) URL without user info");
        }
        return target;
    }

    // This is an administrator-configured storage endpoint, never an untrusted image URL.
    private static boolean isInternalEndpoint(URI target) {
        String host = target.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".svc")
                || host.endsWith(".cluster.local") || (!host.contains(".") && !host.contains(":"))) return true;
        try {
            InetAddress address = InetAddress.ofLiteral(host);
            return address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || (address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private S3Client buildClient(URI targetEndpoint, OutboundProxy proxy) {
        var proxyConfiguration = ProxyConfiguration.builder()
                .useSystemPropertyValues(false)
                .useEnvironmentVariableValues(false)
                .nonProxyHosts(Set.of());
        if (proxy.enabled()) {
            try {
                proxyConfiguration.endpoint(new URI("http", null, proxy.host(), proxy.port(), null, null, null));
            } catch (java.net.URISyntaxException ex) {
                throw new IllegalStateException("Invalid R2 proxy gateway", ex);
            }
        }
        return S3Client.builder()
                .endpointOverride(targetEndpoint)
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .httpClientBuilder(Apache5HttpClient.builder()
                        .proxyConfiguration(proxyConfiguration.build())
                        .maxConnections(16)
                        .connectionTimeout(Duration.ofSeconds(5))
                        .connectionAcquisitionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(30))
                        .connectionMaxIdleTime(Duration.ofSeconds(60)))
                .overrideConfiguration(configuration -> configuration
                        .apiCallTimeout(Duration.ofSeconds(60))
                        .apiCallAttemptTimeout(Duration.ofSeconds(30)))
                .build();
    }

    @PreDestroy
    public void close() {
        clients.close();
    }

    private String extractKey(String storagePath) {
        if (storagePath.startsWith("http")) {
            int coversIndex = storagePath.lastIndexOf("/covers/");
            if (coversIndex >= 0) {
                return storagePath.substring(coversIndex + 1);
            }
            return storagePath.substring(storagePath.lastIndexOf('/') + 1);
        }
        return storagePath;
    }
}
