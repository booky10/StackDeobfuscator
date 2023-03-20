package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (17:38 18.12.22)

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.io.IOException;

public class StackDeobfMod implements ModInitializer {

    @Override
    public void onInitialize() {
        MappingUtil.init();

        Logger rootLogger = (Logger) LogManager.getRootLogger();
        rootLogger.addFilter(new AbstractFilter() {
            @Override
            public Result filter(LogEvent event) {
                if (event.getThrown() != null) {
                    try {
                        MappingUtil.mapThrowable(event.getThrown());
                    } catch (IOException exception) {
                        event.getThrown().addSuppressed(exception);
                    }
                }
                return Result.NEUTRAL;
            }
        });
    }
}
