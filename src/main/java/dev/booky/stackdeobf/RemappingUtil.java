package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (17:43 17.12.22)

import java.util.regex.Pattern;

public final class RemappingUtil {

    private static final Pattern CLASS_PATTERN = Pattern.compile("(net.minecraft.)?class_(\\d+)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("method_(\\d+)");
    private static final Pattern FIELD_PATTERN = Pattern.compile("field_(\\d+)");

    private RemappingUtil() {
    }

    public static String remapString(String string) {
        if (string.contains("class_")) {
            string = CLASS_PATTERN.matcher(string).replaceAll(result -> {
                int classId = Integer.parseInt(result.group(2));
                return CachedMappings.remapClass(classId);
            });
        }

        if (string.contains("method_")) {
            string = METHOD_PATTERN.matcher(string).replaceAll(result -> {
                int methodId = Integer.parseInt(result.group(1));
                return CachedMappings.remapMethod(methodId);
            });
        }

        if (string.contains("field_")) {
            string = FIELD_PATTERN.matcher(string).replaceAll(result -> {
                int fieldId = Integer.parseInt(result.group(1));
                return CachedMappings.remapField(fieldId);
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

