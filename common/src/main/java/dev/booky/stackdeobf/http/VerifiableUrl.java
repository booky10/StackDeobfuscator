package dev.booky.stackdeobf.http;
// Created by booky10 in StackDeobfuscator (01:57 10.06.23)

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class VerifiableUrl {

    private static final Logger LOGGER = LogManager.getLogger("StackDeobfuscator");

    private final URI uri;
    private final HashType hashType;
    private final HashCode hashCode;

    public VerifiableUrl(URI uri, HashType hashType, String hash) {
        this(uri, hashType, HashCode.fromString(hash));
    }

    public VerifiableUrl(URI uri, HashType hashType, byte[] hash) {
        this(uri, hashType, HashCode.fromBytes(hash));
    }

    public VerifiableUrl(URI uri, HashType hashType, HashCode hashCode) {
        this.uri = uri;
        this.hashType = hashType;
        this.hashCode = hashCode;
    }

    public static CompletableFuture<VerifiableUrl> resolve(URI url, HashType hashType, Executor executor) {
        LOGGER.info("Downloading {} hash for {}...", hashType, url);
        URI hashUrl = URI.create(url + hashType.getExtension());
        return HttpUtil.getAsync(hashUrl, executor)
                .thenApply(hashStrBytes -> {
                    // the hash file contains the hash bytes as hex, so this has to be
                    // converted to a string and then parsed to bytes again
                    String hashStr = new String(hashStrBytes);
                    return new VerifiableUrl(url, hashType, hashStr);
                });
    }

    public static CompletableFuture<VerifiableUrl> resolveByMd5Header(URI url, Executor executor) {
        HttpRequest request = HttpRequest.newBuilder(url)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        HashType hashType = HashType.MD5;

        LOGGER.info("Downloading {} hash for {}...", hashType, url);
        return HttpUtil.getAsyncRaw(request, executor).thenApply(resp -> {
            byte[] bodyBytes = resp.getRespBody();

            String message = "Received {} bytes ({}) with status {} from {} {} in {}ms";
            Object[] args = {bodyBytes.length, FileUtils.byteCountToDisplaySize(bodyBytes.length),
                    resp.getRespCode(), request.method(), url, resp.getDurationMs()};

            if (!HttpUtil.isSuccess(resp.getRespCode())) {
                LOGGER.error(message, args);
                throw new FailedHttpRequestException(resp.getResponse());
            }
            LOGGER.info(message, args);

            // sent by piston-meta server, base64-encoded md5 hash bytes of the response body
            Optional<String> optMd5Hash = resp.getResponse().headers().firstValue("content-md5");
            optMd5Hash.orElseThrow(() -> new FailedUrlVerificationException("No expected 'content-md5'"
                    + " header found for " + url));

            byte[] md5Bytes = Base64.getDecoder().decode(optMd5Hash.get());
            return new VerifiableUrl(url, hashType, md5Bytes);
        });
    }

    public CompletableFuture<byte[]> get(Executor executor) {
        return HttpUtil.getAsync(this.uri, executor).thenApply(bytes -> {
            HashCode hashCode = this.hashType.hash(bytes);
            LOGGER.info("Verifying {} hash {} for {}...",
                    this.hashType, hashCode, this.uri);

            if (!hashCode.equals(this.hashCode)) {
                throw new FailedUrlVerificationException(this.hashType + " hash " + hashCode + " doesn't match "
                        + this.hashCode + " for " + this.uri);
            }
            return bytes;
        });
    }

    public URI getUrl() {
        return this.uri;
    }

    public enum HashType {

        @SuppressWarnings("deprecation")
        MD5(".md5", Hashing.md5()),
        @SuppressWarnings("deprecation")
        SHA1(".sha1", Hashing.sha1()),
        SHA256(".sha256", Hashing.sha256()),
        SHA512(".sha512", Hashing.sha512());

        private final String extension;
        private final HashFunction hashFunction;

        HashType(String extension, HashFunction hashFunction) {
            this.extension = extension;
            this.hashFunction = hashFunction;
        }

        public HashCode hash(byte[] bytes) {
            return this.hashFunction.hashBytes(bytes);
        }

        public String getExtension() {
            return this.extension;
        }
    }
}
