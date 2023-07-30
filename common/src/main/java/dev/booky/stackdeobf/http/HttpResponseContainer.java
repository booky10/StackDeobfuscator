package dev.booky.stackdeobf.http;
// Created by booky10 in StackDeobfuscator (21:47 30.07.23)

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class HttpResponseContainer {

    private final HttpRequest request;
    private final HttpResponse<byte[]> response;
    private final Duration duration;
    private final Supplier<CompletableFuture<HttpResponseContainer>> retry;

    HttpResponseContainer(HttpRequest request, HttpResponse<byte[]> response, Duration duration,
                          Supplier<CompletableFuture<HttpResponseContainer>> retry) {
        this.request = request;
        this.response = response;
        this.duration = duration;
        this.retry = retry;
    }

    public CompletableFuture<HttpResponseContainer> retry() {
        return this.retry.get();
    }

    public byte[] getBody() {
        return this.response.body();
    }

    public int getStatuscode() {
        return this.response.statusCode();
    }

    public HttpRequest getRequest() {
        return this.request;
    }

    public HttpResponse<byte[]> getResponse() {
        return this.response;
    }

    public Duration getDuration() {
        return this.duration;
    }
}
