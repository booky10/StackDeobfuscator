package dev.booky.stackdeobf.compat;
// Created by booky10 in StackDeobfuscator (21:35 29.03.23)

import org.apache.logging.log4j.Logger;

public class Log4jLogger implements ILogger {

    private final Logger delegate;

    public Log4jLogger(Logger delegate) {
        this.delegate = delegate;
    }

    @Override
    public void info(String message, Object... args) {
        this.delegate.info(message, args);
    }

    @Override
    public void warn(String message, Object... args) {
        this.delegate.warn(message, args);
    }

    @Override
    public void error(String message, Object... args) {
        this.delegate.error(message, args);
    }
}
