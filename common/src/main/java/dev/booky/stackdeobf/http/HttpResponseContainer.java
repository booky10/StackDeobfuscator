package dev.booky.stackdeobf.http;
// Created by booky10 in StackDeobfuscator (21:47 30.07.23)

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpResponseContainer {

    private final HttpRequest request;
    private final HttpResponse<byte[]> response;
    private final Duration duration;

    public HttpResponseContainer(HttpRequest request, HttpResponse<byte[]> response, Duration duration) {
        this.request = request;
        this.response = response;
        this.duration = duration;
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
