package dev.booky.stackdeobf.compat;
// Created by booky10 in StackDeobfuscator (21:34 29.03.23)

public interface ILogger {

    void info(String message, Object... args);

    void warn(String message, Object... args);

    void error(String message, Object... args);
}
