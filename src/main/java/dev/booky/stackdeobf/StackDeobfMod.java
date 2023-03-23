package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (17:38 18.12.22)

import dev.booky.stackdeobf.mappings.CachedMappings;
import dev.booky.stackdeobf.mappings.RemappingUtil;
import dev.booky.stackdeobf.mappings.types.AbstractMappingType;
import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

public class StackDeobfMod implements ModInitializer {

    @Override
    public void onInitialize() {
        CachedMappings.init(AbstractMappingType.Type.YARN);
        RemappingUtil.injectLogFilter((Logger) LogManager.getRootLogger());
    }
}
