package dev.booky.stackdeobf.http;
// Created by booky10 in StackDeobfuscator (01:57 10.06.23)

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

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
        return resolve(url, hashType, executor, 0);
    }

    public static CompletableFuture<VerifiableUrl> resolve(URI url, HashType hashType, Executor executor, int depth) {
        if (hashType == HashType.DUMMY) {
            VerifiableUrl dummyUrl = new VerifiableUrl(url, HashType.DUMMY, HashResult.emptyResult());
            return CompletableFuture.completedFuture(dummyUrl);
        }

        LOGGER.info("Downloading {} hash for {}...", hashType, url);
        URI hashUrl = URI.create(url + hashType.getExtension());
        return HttpUtil.getAsync(hashUrl, executor, depth)
                .thenApply(resp -> {
                    // the hash file contains the hash bytes as hex, so this has to be
                    // converted to a string and then parsed to bytes again
                    String hashStr = new String(resp.getBody());
                    return new VerifiableUrl(url, hashType, hashStr);
                });
    }

    public static CompletableFuture<VerifiableUrl> resolveByMd5Header(URI url, Executor executor) {
        return resolveByMd5Header(url, executor, 0);
    }

    public static CompletableFuture<VerifiableUrl> resolveByMd5Header(URI url, Executor executor, int depth) {
        HttpRequest request = HttpRequest.newBuilder(url)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        HashType hashType = HashType.MD5;

        LOGGER.info("Downloading {} hash for {}... (try #{})", hashType, url, depth);
        return HttpUtil.getAsync(request, executor, depth).thenCompose(resp -> {
            // sent by piston-meta server, base64-encoded md5 hash bytes of the response body
            Optional<String> optMd5Hash = resp.getResponse().headers().firstValue("content-md5");
            if (optMd5Hash.isEmpty()) {
                LOGGER.error("No expected 'content-md5' header found for {}; retrying...", url);
                return resolveByMd5Header(url, executor, depth + 1);
            }

            byte[] md5Bytes = Base64.getDecoder().decode(optMd5Hash.get());
            return CompletableFuture.completedFuture(new VerifiableUrl(url, hashType, md5Bytes));
        });
    }

    public CompletableFuture<HttpResponseContainer> get(Executor executor) {
        return get(executor, 0);
    }

    public CompletableFuture<HttpResponseContainer> get(Executor executor, int depth) {
        return HttpUtil.getAsync(this.uri, executor, depth).thenCompose(resp -> {
            if (this.hashType == HashType.DUMMY) {
                return CompletableFuture.completedFuture(resp);
            }

            HashResult hashResult = this.hashType.hash(resp.getBody());
            LOGGER.info("Verifying {} hash {} for {}...",
                    this.hashType, hashResult, this.uri);

            if (!hashResult.equals(this.hashResult)) {
                LOGGER.error("{} hash {} doesn't match {} for {}; retrying...",
                        this.hashType, hashResult, this.hashResult, this.uri);
                return get(executor, depth + 1);
            }
            return CompletableFuture.completedFuture(resp);
        });
    }

    public URI getUrl() {
        return this.uri;
    }

    public enum HashType {

        MD5(".md5", "MD5"),
        SHA1(".sha1", "SHA-1"),
        SHA256(".sha256", "SHA-256"),
        SHA512(".sha512", "SHA-512"),
        DUMMY(null, null);

        private final @Nullable String extension;
        private final @Nullable MessageDigest digester;

        HashType(@Nullable String extension, @Nullable String algoName) {
            if ((extension == null) != (algoName == null)) {
                throw new IllegalArgumentException("Can't create half-valid hash type");
            }
            this.extension = extension;

            if (algoName != null) {
                try {
                    this.digester = MessageDigest.getInstance(algoName);
                } catch (NoSuchAlgorithmException exception) {
                    throw new IllegalStateException("Illegal algorithm provided: " + algoName, exception);
                }
            } else {
                this.digester = null;
            }
        }

        public HashResult hash(byte[] bytes) {
            if (this.digester == null) {
                throw new UnsupportedOperationException();
            }
            byte[] resultBytes;
            synchronized (this.digester) {
                resultBytes = this.digester.digest(bytes);
            }
            return new HashResult(resultBytes);
        }

        public String getExtension() {
            if (this.extension == null) {
                throw new UnsupportedOperationException();
            }
            return this.extension;
        }
    }

    public record HashResult(byte[] bytes) {

        private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

        public HashResult(String string) {
            this(decode(string));
        }

        public static HashResult emptyResult() {
            return new HashResult(new byte[0]);
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

        public boolean isEmpty() {
            return this.bytes.length == 0;
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
