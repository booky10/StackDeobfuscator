package dev.booky.stackdeobf.util;
// Created by booky10 in StackDeobfuscator (22:33 14.04.23)

import dev.booky.stackdeobf.mappings.CachedMappings;
import org.apache.logging.log4j.core.impl.ExtendedStackTraceElement;
import org.apache.logging.log4j.core.impl.ThrowableProxy;

import java.lang.reflect.Field;

final class Log4jRemapUtil {

    private static final Field PROXY_NAME = getField(ThrowableProxy.class, "name");
    private static final Field PROXY_MESSAGE = getField(ThrowableProxy.class, "message");
    private static final Field PROXY_LOCALIZED_MESSAGE = getField(ThrowableProxy.class, "localizedMessage");
    private static final Field EXT_STACK_ELEMENT = getField(ExtendedStackTraceElement.class, "stackTraceElement");

    private static Field getField(Class<?> clazz, String name) {
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    static void remapThrowableProxy(CachedMappings mappings, ThrowableProxy proxy) throws IllegalAccessException {
        // remap throwable classname
        if (proxy.getName() != null && proxy.getName().startsWith("net.minecraft.class_")) {
            PROXY_NAME.set(proxy, mappings.remapClasses(proxy.getName()));
        }

        // remap throwable message
        if (proxy.getMessage() != null) {
            PROXY_MESSAGE.set(proxy, mappings.remapString(proxy.getMessage()));
        }
        if (proxy.getLocalizedMessage() != null) {
            PROXY_LOCALIZED_MESSAGE.set(proxy, mappings.remapString(proxy.getLocalizedMessage()));
        }

        // remap throwable stack trace
        for (ExtendedStackTraceElement extElement : proxy.getExtendedStackTrace()) {
            StackTraceElement element = extElement.getStackTraceElement();
            element = mappings.remapStackTrace(element);
            EXT_STACK_ELEMENT.set(extElement, element);
        }

        // remap cause + suppressed throwables
        if (proxy.getCauseProxy() != null) {
            remapThrowableProxy(mappings, proxy.getCauseProxy());
        }
        for (ThrowableProxy suppressed : proxy.getSuppressedProxies()) {
            remapThrowableProxy(mappings, suppressed);
        }
    }
}
