package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (17:38 18.12.22)

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.rewrite.RewriteAppender;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.util.ArrayList;
import java.util.List;

public class StackDeobfMod implements ModInitializer {

    @Override
    public void onInitialize() {
        // inspired by https://github.com/natanfudge/Not-Enough-Crashes/blob/613f6393a37ac7b987d71e8ad5dbd3f4588eb6c4/fabric/src/main/java/fudge/notenoughcrashes/fabric/DeobfuscatingRewritePolicy.java
        Logger rootLogger = (Logger) LogManager.getRootLogger();
        LoggerConfig loggerConfig = rootLogger.get();

        // Remove appender refs from config
        List<AppenderRef> appenderRefs = new ArrayList<>(loggerConfig.getAppenderRefs());
        for (AppenderRef appenderRef : appenderRefs) {
            loggerConfig.removeAppender(appenderRef.getRef());
        }

        RewritePolicy policy = source -> {
            if (source.getThrown() != null) {
                MappingUtil.mapThrowable(source.getThrown());
            }
            return source;
        };

        // Create the RewriteAppender, which wraps the appenders
        RewriteAppender rewriteAppender = RewriteAppender.createAppender("StackDeobfuscatorAppender",
                "true", appenderRefs.toArray(AppenderRef[]::new), rootLogger.getContext().getConfiguration(),
                policy, null);
        rewriteAppender.start();

        // Add the new appender
        loggerConfig.addAppender(rewriteAppender, null, null);
    }
}
