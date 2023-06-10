package dev.booky.stackdeobf.http;
// Created by booky10 in StackDeobfuscator (18:10 29.03.23)

import dev.booky.stackdeobf.util.CompatUtil;
import org.apache.commons.io.FileUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class HttpUtil {

    private static final Map<Executor, HttpClient> HTTP = new WeakHashMap<>();

    private HttpUtil() {
    }

    private static HttpClient getHttpClient(Executor executor) {
        synchronized (HTTP) {
            return HTTP.computeIfAbsent(executor, $ -> HttpClient.newBuilder().executor(executor).build());
        }
    }

    public static CompletableFuture<byte[]> getAsync(VerifiableUrl url, Executor executor) {
        return getAsync(url.getUrl(), executor).thenApply(bytes -> {
            url.verifyHash(bytes);
            return bytes;
        });
    }

    static CompletableFuture<byte[]> getAsync(URI url, Executor executor) {
        HttpRequest request = HttpRequest.newBuilder(url).build();
        return getAsyncRaw(request, executor).thenApply(resp -> {
            byte[] bodyBytes = resp.getRespBody();

            String message = "Received {} bytes ({}) with status {} from {} {} in {}ms";
            Object[] args = {bodyBytes.length, FileUtils.byteCountToDisplaySize(bodyBytes.length),
                    resp.getRespCode(), request.method(), url, resp.getDurationMs()};

            if (!isSuccess(resp.getRespCode())) {
                CompatUtil.LOGGER.error(message, args);
                throw new FailedHttpRequestException(resp.getResponse());
            }
            CompatUtil.LOGGER.info(message, args);
            return bodyBytes;
        });
    }

    static CompletableFuture<RawHttpResponse> getAsyncRaw(HttpRequest request, Executor executor) {
        HttpResponse.BodyHandler<byte[]> handler = HttpResponse.BodyHandlers.ofByteArray();

        CompatUtil.LOGGER.info("Requesting {}...", request.uri());
        long start = System.currentTimeMillis();

        return getHttpClient(executor)
                .sendAsync(request, handler)
                .thenApplyAsync(resp -> {
                    long timeDiff = System.currentTimeMillis() - start;
                    return new RawHttpResponse(request, resp, timeDiff);
                }, executor);
    }

    static boolean isSuccess(int code) {
        return code >= 200 && code <= 299;
    }

    static final class RawHttpResponse {

        private final HttpRequest request;
        private final HttpResponse<byte[]> response;
        private final long durationMs;

        public RawHttpResponse(HttpRequest request, HttpResponse<byte[]> response, long durationMs) {
            this.request = request;
            this.response = response;
            this.durationMs = durationMs;
        }

        public HttpRequest getRequest() {
            return this.request;
        }

        public HttpResponse<byte[]> getResponse() {
            return this.response;
        }

        public long getDurationMs() {
            return this.durationMs;
        }

        public byte[] getRespBody() {
            return this.response.body();
        }

        public int getRespCode() {
            return this.response.statusCode();
        }
    }
}
