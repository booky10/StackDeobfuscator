package dev.booky.stackdeobf.mappings;
// Created by booky10 in StackDeobfuscator (17:43 17.12.22)

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;

public final class RemappingUtil {

    private static final Pattern CLASS_PATTERN = Pattern.compile("(net.minecraft.)?class_(\\d+)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("method_(\\d+)");
    private static final Pattern FIELD_PATTERN = Pattern.compile("field_(\\d+)");

    private RemappingUtil() {
    }

    public static void injectLogFilter(Logger logger) {
        logger.addFilter(new AbstractFilter() {
            @Override
            public Result filter(LogEvent event) {
                if (event.getThrown() == null) {
                    return Result.NEUTRAL;
                }

                // we need to manually print out the stacktrace, because
                // log4j also builds it manually, resulting
                // in every logged exception being a "RemappedThrowable"
                try (StringWriter strWriter = new StringWriter()) {
                    try (PrintWriter writer = new PrintWriter(strWriter)) {
                        RemappingUtil.remapThrowable(event.getThrown()).printStackTrace(writer);
                    }

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

    public static String remapString(String string) {
        if (string.contains("class_")) {
            string = CLASS_PATTERN.matcher(string).replaceAll(result -> {
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

        if (string.contains("method_")) {
            string = METHOD_PATTERN.matcher(string).replaceAll(result -> {
                int methodId = Integer.parseInt(result.group(1));
                String methodName = CachedMappings.remapMethod(methodId);
                return methodName == null ? result.group() : methodName;
            });
        }

        if (string.contains("field_")) {
            string = FIELD_PATTERN.matcher(string).replaceAll(result -> {
                int fieldId = Integer.parseInt(result.group(1));
                String fieldName = CachedMappings.remapField(fieldId);
                return fieldName == null ? result.group() : fieldName;
            });
        }

        return string;
    }

    public static Throwable remapThrowable(Throwable throwable) {
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

        String throwableName;
        if (throwable instanceof RemappedThrowable) {
            throwableName = ((RemappedThrowable) throwable).getClassName();
        } else {
            throwableName = throwable.getClass().getName();
            throwableName = remapString(throwableName);
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
        // class name remapping
        String className = element.getClassName();
        if (className.startsWith("net.minecraft.class_")) { // intermediary name
            int classId = Integer.parseInt(className.substring("net.minecraft.class_".length()));
            String remappedClassName = CachedMappings.remapClass(classId);
            if (remappedClassName != null) {
                className = remappedClassName;
            }
        }

        // method name remapping
        String methodName = element.getMethodName();
        if (methodName.startsWith("method_")) { // intermediary name
            int methodId = Integer.parseInt(methodName.substring("method_".length()));
            String remappedMethodName = CachedMappings.remapMethod(methodId);
            if (remappedMethodName != null) {
                methodName = remappedMethodName;
            }
        }

        // file name remapping
        String rawFileName = element.getFileName();
        if (rawFileName != null && rawFileName.startsWith("class_")) { // intermediary name
            int fileTypeSeparator = rawFileName.indexOf('.');
            String fileType = "", fileName;
            if (fileTypeSeparator != -1) {
                fileType = rawFileName.substring(fileTypeSeparator);
                fileName = rawFileName.substring(0, fileTypeSeparator);
            } else {
                fileName = rawFileName;
            }

            int classId = Integer.parseInt(fileName.substring("class_".length()));
            String remappedClassName = CachedMappings.remapClass(classId);
            if (remappedClassName != null) {
                int lastPackageIndex = remappedClassName.lastIndexOf('.');
                if (lastPackageIndex != -1) {
                    remappedClassName = remappedClassName.substring(lastPackageIndex + 1);
                }
                rawFileName = remappedClassName + fileType;
            }
        }

        return new StackTraceElement(null /*dropped on purpose*/, element.getModuleName(), element.getModuleVersion(),
                className, methodName, rawFileName, element.getLineNumber());
    }
}

