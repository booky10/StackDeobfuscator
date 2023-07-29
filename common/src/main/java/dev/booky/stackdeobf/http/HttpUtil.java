package dev.booky.stackdeobf.http;
// Created by booky10 in StackDeobfuscator (18:10 29.03.23)

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class HttpUtil {

    private static final Logger LOGGER = LogManager.getLogger("StackDeobfuscator");
    private static final Map<Executor, HttpClient> HTTP = new WeakHashMap<>();

    private HttpUtil() {
    }

    static boolean isSuccess(int code) {
        return code >= 200 && code <= 299;
    }

    private static HttpClient getHttpClient(Executor executor) {
        synchronized (HTTP) {
            return HTTP.computeIfAbsent(executor, $ -> HttpClient.newBuilder().executor(executor).build());
        }
    }

    public static CompletableFuture<byte[]> getAsync(URI url, Executor executor) {
        HttpRequest request = HttpRequest.newBuilder(url).build();
        return getAsync(request, executor);
    }

    public static CompletableFuture<byte[]> getAsync(HttpRequest request, Executor executor) {
        return getAsyncRaw(request, executor).thenApply(resp -> {
            byte[] bodyBytes = resp.getRespBody();

            String message = "Received {} bytes ({}) with status {} from {} {} in {}ms";
            Object[] args = {bodyBytes.length, FileUtils.byteCountToDisplaySize(bodyBytes.length),
                    resp.getRespCode(), request.method(), request.uri(), resp.getDurationMs()};

            if (!isSuccess(resp.getRespCode())) {
                LOGGER.error(message, args);
                throw new FailedHttpRequestException(resp.getResponse());
            }
            LOGGER.info(message, args);
            return bodyBytes;
        });
    }

    static CompletableFuture<RawHttpResponse> getAsyncRaw(HttpRequest request, Executor executor) {
        HttpResponse.BodyHandler<byte[]> handler = HttpResponse.BodyHandlers.ofByteArray();

        LOGGER.info("Requesting {}...", request.uri());
        long start = System.currentTimeMillis();

        return getHttpClient(executor)
                .sendAsync(request, handler)
                .thenApplyAsync(resp -> {
                    long timeDiff = System.currentTimeMillis() - start;
                    return new RawHttpResponse(request, resp, timeDiff);
                }, executor);
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

        public byte[] getRespBody() {
            return this.response.body();
        }

        public int getRespCode() {
            return this.response.statusCode();
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
    }
}
