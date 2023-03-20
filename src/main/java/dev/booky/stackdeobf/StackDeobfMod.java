package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (17:38 18.12.22)

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class StackDeobfMod implements ModInitializer {

    @Override
    public void onInitialize() {
        CachedMappings.init();

        Logger rootLogger = (Logger) LogManager.getRootLogger();
        rootLogger.addFilter(new AbstractFilter() {
            @Override
            public Result filter(LogEvent event) {
                if (event.getThrown() != null) {
                    rootLogger.logIfEnabled(event.getLoggerFqcn(), event.getLevel(), event.getMarker(), event.getMessage(), null);

                    try (StringWriter strWriter = new StringWriter()) {
                        try (PrintWriter writer = new PrintWriter(strWriter)) {
                            RemappingUtil.remapThrowable(event.getThrown()).printStackTrace(writer);
                        }
                        rootLogger.logIfEnabled(event.getLoggerFqcn(), event.getLevel(), event.getMarker(), strWriter.toString());
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                    return Result.DENY;
                }
                return Result.NEUTRAL;
            }
        });
    }
}
