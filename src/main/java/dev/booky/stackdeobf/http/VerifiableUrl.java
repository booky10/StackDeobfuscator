package dev.booky.stackdeobf.http;
// Created by booky10 in StackDeobfuscator (01:57 10.06.23)

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import dev.booky.stackdeobf.util.CompatUtil;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class VerifiableUrl {

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
        CompatUtil.LOGGER.info("Downloading {} hash for {}...", hashType, url);
        URI hashUrl = URI.create(url + hashType.getExtension());
        return HttpUtil.getAsync(hashUrl, executor).thenApply(hashStrBytes -> {
            // the hash file contains the hash bytes as hex, so this has to be
            // converted to a string and then parsed to bytes again
            String hashStr = new String(hashStrBytes);
            return new VerifiableUrl(url, hashType, hashStr);
        });
    }

    void verifyHash(byte[] bytes) throws FailedUrlVerificationException {
        HashCode hashCode = this.hashType.hash(bytes);
        CompatUtil.LOGGER.info("Verifying {} hash {} for {}...",
                this.hashType, hashCode, this.uri);

        if (!hashCode.equals(this.hashCode)) {
            throw new FailedUrlVerificationException(this.hashType + " hash " + hashCode + " doesn't match "
                    + this.hashCode + " for " + this.uri);
        }
    }

    URI getUrl() {
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
