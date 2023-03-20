package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (17:43 17.12.22)

public final class RemappingUtil {

    private RemappingUtil() {
    }

    public static void remapThrowable(Throwable throwable) {
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        remapStackTraceElements(stackTrace);
        throwable.setStackTrace(stackTrace);

        if (throwable.getCause() != null) {
            remapThrowable(throwable.getCause());
        }

        for (Throwable suppressed : throwable.getSuppressed()) {
            remapThrowable(suppressed);
        }
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

        return new StackTraceElement(element.getClassLoaderName(), element.getModuleName(), element.getModuleVersion(),
                className, methodName, rawFileName, element.getLineNumber());
    }
}

