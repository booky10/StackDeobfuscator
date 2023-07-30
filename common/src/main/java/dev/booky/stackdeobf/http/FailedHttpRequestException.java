package dev.booky.stackdeobf.http;
// Created by booky10 in StackDeobfuscator (18:16 29.03.23)

import org.jetbrains.annotations.ApiStatus;

public class FailedHttpRequestException extends RuntimeException {

    @ApiStatus.Internal
    public FailedHttpRequestException(String message) {
        super(message);
    }
}
