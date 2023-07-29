package dev.booky.stackdeobf.http;
// Created by booky10 in StackDeobfuscator (02:09 10.06.23)

public class FailedUrlVerificationException extends RuntimeException {

    public FailedUrlVerificationException() {
    }

    public FailedUrlVerificationException(String message) {
        super(message);
    }

    public FailedUrlVerificationException(String message, Throwable cause) {
        super(message, cause);
    }

    public FailedUrlVerificationException(Throwable cause) {
        super(cause);
    }
}
