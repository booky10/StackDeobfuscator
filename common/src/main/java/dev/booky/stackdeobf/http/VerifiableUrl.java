package dev.booky.stackdeobf.http;
// Created by booky10 in StackDeobfuscator (01:57 10.06.23)

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class VerifiableUrl {

    private static final Logger LOGGER = LogManager.getLogger("StackDeobfuscator");

    private final URI uri;
    private final HashType hashType;
    private final HashResult hashResult;

    public VerifiableUrl(URI uri, HashType hashType, String hash) {
        this(uri, hashType, new HashResult(hash));
    }

    public VerifiableUrl(URI uri, HashType hashType, byte[] hash) {
        this(uri, hashType, new HashResult(hash));
    }

    public VerifiableUrl(URI uri, HashType hashType, HashResult hashResult) {
        this.uri = uri;
        this.hashType = hashType;
        this.hashResult = hashResult;
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
            HashResult hashResult = this.hashType.hash(bytes);
            LOGGER.info("Verifying {} hash {} for {}...",
                    this.hashType, hashResult, this.uri);

            if (!hashResult.equals(this.hashResult)) {
                throw new FailedUrlVerificationException(this.hashType + " hash " + hashResult + " doesn't match "
                        + this.hashResult + " for " + this.uri);
            }
            return bytes;
        });
    }

    public URI getUrl() {
        return this.uri;
    }

    public enum HashType {

        MD5(".md5", "MD5"),
        SHA1(".sha1", "SHA-1"),
        SHA256(".sha256", "SHA-256"),
        SHA512(".sha512", "SHA-512");

        private final String extension;
        private final MessageDigest digester;

        HashType(String extension, String algoName) {
            this.extension = extension;

            try {
                this.digester = MessageDigest.getInstance(algoName);
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("Illegal algorithm provided: " + algoName, exception);
            }
        }

        public HashResult hash(byte[] bytes) {
            byte[] resultBytes;
            synchronized (this.digester) {
                resultBytes = this.digester.digest(bytes);
            }
            return new HashResult(resultBytes);
        }

        public String getExtension() {
            return this.extension;
        }
    }

    public record HashResult(byte[] bytes) {

        private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

        public HashResult(String string) {
            this(decode(string));
        }

        private static byte[] decode(String string) {
            if (string.length() % 2 != 0) {
                throw new IllegalArgumentException("Illegal hash string: " + string);
            }

            byte[] bytes = new byte[string.length() / 2];
            for (int i = 0; i < string.length(); i += 2) {
                int b = Character.digit(string.charAt(i), 16) << 4;
                b |= Character.digit(string.charAt(i + 1), 16);
                bytes[i / 2] = (byte) b;
            }
            return bytes;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof HashResult that)) return false;
            return Arrays.equals(this.bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(this.bytes);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(2 * this.bytes.length);
            for (byte eightBits : this.bytes) {
                builder.append(HEX_DIGITS[(eightBits >> 4) & 0xF]);
                builder.append(HEX_DIGITS[eightBits & 0xF]);
            }
            return builder.toString();
        }
    }
}
