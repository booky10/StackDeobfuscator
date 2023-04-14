package dev.booky.stackdeobf.mappings;
// Created by booky10 in StackDeobfuscator (17:43 17.12.22)

import dev.booky.stackdeobf.util.CompatUtil;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RemappingUtil {

    static final Pattern CLASS_PATTERN = Pattern.compile("(net\\.minecraft\\.)?class_(\\d+)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("method_(\\d+)");
    private static final Pattern FIELD_PATTERN = Pattern.compile("field_(\\d+)");

    private RemappingUtil() {
    }

    public static void injectLogFilter(org.apache.logging.log4j.core.Logger logger, boolean rewriteMessages) {
        CompatUtil.LOGGER.info("Injecting into root logger...");
        logger.addFilter(new AbstractFilter() {
            @Override
            public Result filter(LogEvent event) {
                // TODO: this is a horribly hacky way to rewrite messages, find something better

                if (event.getThrown() == null) {
                    if (!rewriteMessages) {
                        return Result.NEUTRAL;
                    }

                    String message = event.getMessage().getFormattedMessage();
                    String remappedMessage = RemappingUtil.remapString(message);

                    if (message.equals(remappedMessage)) {
                        // didn't change anything, continue
                        return Result.NEUTRAL;
                    }

                    logger.logIfEnabled(event.getLoggerFqcn(), event.getLevel(), event.getMarker(), remappedMessage);

                    // cancel the underlying event, this needs
                    // to be rewritten
                    return Result.DENY;
                }

                // we need to manually print out the stacktrace, because
                // log4j also builds it manually, resulting
                // in every logged exception being a "RemappedThrowable"
                try (StringWriter strWriter = new StringWriter()) {
                    try (PrintWriter writer = new PrintWriter(strWriter)) {
                        RemappingUtil.remapThrowable(event.getThrown()).printStackTrace(writer);
                    }

                    // this message doesn't need to be remapped if every message should be remapped,
                    // as this is handled by the above logic already
                    logger.logIfEnabled(event.getLoggerFqcn(), event.getLevel(), event.getMarker(), event.getMessage(), null);

                    logger.logIfEnabled(event.getLoggerFqcn(), event.getLevel(), event.getMarker(), strWriter.toString());
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }

                // cancel the underlying log event
                return Result.DENY;
            }
        });
    }

    private static String remapClasses(String string) {
        return CLASS_PATTERN.matcher(string).replaceAll(result -> {
            int classId = Integer.parseInt(result.group(2));
            String className = CachedMappings.remapClass(classId);
            if (className == null) {
                return result.group();
            }

            if (result.group(1) != null) {
                // a package has been specified, don't remove it
                return className;
            }

            // no package in original string, remove it
            int packageIndex = className.lastIndexOf('.');
            if (packageIndex != -1) {
                className = className.substring(packageIndex + 1);
            }
            return className;
        });
    }

    private static String remapMethods(String string) {
        return METHOD_PATTERN.matcher(string).replaceAll(result -> {
            int methodId = Integer.parseInt(result.group(1));
            String methodName = CachedMappings.remapMethod(methodId);
            return methodName == null ? result.group() : Matcher.quoteReplacement(methodName);
        });
    }

    private static String remapFields(String string) {
        return FIELD_PATTERN.matcher(string).replaceAll(result -> {
            int fieldId = Integer.parseInt(result.group(1));
            String fieldName = CachedMappings.remapField(fieldId);
            return fieldName == null ? result.group() : fieldName;
        });
    }

    public static String remapString(String string) {
        if (string.contains("class_")) {
            string = remapClasses(string);
        }

        if (string.contains("method_")) {
            string = remapMethods(string);
        }

        if (string.contains("field_")) {
            string = remapFields(string);
        }

        return string;
    }

    public static Throwable remapThrowable(Throwable throwable) {
        if (throwable instanceof RemappedThrowable) {
            return throwable;
        }

        StackTraceElement[] stackTrace = throwable.getStackTrace();
        remapStackTraceElements(stackTrace);

        Throwable cause = throwable.getCause();
        if (cause != null) {
            cause = remapThrowable(cause);
        }

        String message = throwable.getMessage();
        if (message != null) {
            message = remapString(message);
        }

        String throwableName = throwable.getClass().getName();
        if (throwableName.startsWith("net.minecraft.class_")) {
            throwableName = remapClasses(throwableName);
        }

        Throwable remapped = new RemappedThrowable(message, cause, throwableName);
        remapped.setStackTrace(stackTrace);
        for (Throwable suppressed : throwable.getSuppressed()) {
            remapped.addSuppressed(remapThrowable(suppressed));
        }
        return remapped;
    }

    public static void remapStackTraceElements(StackTraceElement[] elements) {
        for (int i = 0; i < elements.length; i++) {
            elements[i] = remapStackTraceElement(elements[i]);
        }
    }

    public static StackTraceElement remapStackTraceElement(StackTraceElement element) {
        String className = element.getClassName();
        boolean remappedClass = false;
        if (className.startsWith("net.minecraft.class_")) {
            className = remapClasses(className);
            remappedClass = true;
        }

        String fileName = element.getFileName();
        if (fileName != null && fileName.startsWith("class_")) {
            fileName = remapClasses(fileName);
        }

        String methodName = element.getMethodName();
        if (methodName.startsWith("method_")) {
            methodName = remapMethods(methodName);
        }

        return new StackTraceElement(remappedClass ? "MC" : null, element.getModuleName(), element.getModuleVersion(),
                className, methodName, fileName, element.getLineNumber());
    }
}

