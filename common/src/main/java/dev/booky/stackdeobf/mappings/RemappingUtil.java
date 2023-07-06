package dev.booky.stackdeobf.mappings;
// Created by booky10 in StackDeobfuscator (17:43 17.12.22)

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RemappingUtil {

    private static final Pattern CLASS_PATTERN = Pattern.compile("(net\\.minecraft\\.|net/minecraft/)?class_(\\d+)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("method_(\\d+)");
    private static final Pattern FIELD_PATTERN = Pattern.compile("field_(\\d+)");

    private RemappingUtil() {
    }

    public static String remapClasses(CachedMappings mappings, String string) {
        return CLASS_PATTERN.matcher(string).replaceAll(result -> {
            int classId = Integer.parseInt(result.group(2));
            String className = mappings.remapClass(classId);
            if (className == null) {
                return Matcher.quoteReplacement(result.group());
            }

            String packageName = result.group(1);
            if (packageName != null) {
                // a package has been specified, don't remove it
                if (packageName.indexOf('.') == -1) {
                    // original package name contains "/" as package separator instead of "."
                    className = className.replace('.', '/');
                }
            } else {
                // no package in original string, remove it
                int packageIndex = className.lastIndexOf('.');
                if (packageIndex != -1) {
                    className = className.substring(packageIndex + 1);
                }
            }
            return Matcher.quoteReplacement(className);
        });
    }

    public static String remapMethods(CachedMappings mappings, String string) {
        return METHOD_PATTERN.matcher(string).replaceAll(result -> {
            int methodId = Integer.parseInt(result.group(1));
            String methodName = mappings.remapMethod(methodId);
            return Matcher.quoteReplacement(methodName == null ? result.group() : methodName);
        });
    }

    public static String remapFields(CachedMappings mappings, String string) {
        return FIELD_PATTERN.matcher(string).replaceAll(result -> {
            int fieldId = Integer.parseInt(result.group(1));
            String fieldName = mappings.remapField(fieldId);
            return Matcher.quoteReplacement(fieldName == null ? result.group() : fieldName);
        });
    }

    public static String remapString(CachedMappings mappings, String string) {
        if (string.contains("class_")) {
            string = remapClasses(mappings, string);
        }

        if (string.contains("method_")) {
            string = remapMethods(mappings, string);
        }

        if (string.contains("field_")) {
            string = remapFields(mappings, string);
        }

        return string;
    }

    public static Throwable remapThrowable(CachedMappings mappings, Throwable throwable) {
        if (throwable instanceof RemappedThrowable) {
            return throwable;
        }

        StackTraceElement[] stackTrace = throwable.getStackTrace();
        remapStackTrace(mappings, stackTrace);

        Throwable cause = throwable.getCause();
        if (cause != null) {
            cause = remapThrowable(mappings, cause);
        }

        String message = throwable.getMessage();
        if (message != null) {
            message = remapString(mappings, message);
        }

        String throwableName = throwable.getClass().getName();
        if (throwableName.startsWith("net.minecraft.class_")) {
            throwableName = remapClasses(mappings, throwableName);
        }

        Throwable remapped = new RemappedThrowable(message, cause, throwable, throwableName);
        remapped.setStackTrace(stackTrace);
        for (Throwable suppressed : throwable.getSuppressed()) {
            remapped.addSuppressed(remapThrowable(mappings, suppressed));
        }
        return remapped;
    }

    public static void remapStackTrace(CachedMappings mappings, StackTraceElement[] elements) {
        for (int i = 0; i < elements.length; i++) {
            elements[i] = remapStackTrace(mappings, elements[i]);
        }
    }

    public static StackTraceElement remapStackTrace(CachedMappings mappings, StackTraceElement element) {
        String className = element.getClassName();
        boolean remappedClass = false;
        if (className.startsWith("net.minecraft.class_")) {
            className = remapClasses(mappings, className);
            remappedClass = true;
        }

        String fileName = element.getFileName();
        if (fileName != null && fileName.startsWith("class_")) {
            fileName = remapClasses(mappings, fileName);
        }

        String methodName = element.getMethodName();
        if (methodName.startsWith("method_")) {
            methodName = remapMethods(mappings, methodName);
        }

        String classLoaderName = element.getClassLoaderName();
        if (remappedClass) {
            if (classLoaderName == null) {
                classLoaderName = "MC";
            } else {
                classLoaderName += "//MC";
            }
        }

        return new StackTraceElement(classLoaderName, element.getModuleName(), element.getModuleVersion(),
                className, methodName, fileName, element.getLineNumber());
    }
}

