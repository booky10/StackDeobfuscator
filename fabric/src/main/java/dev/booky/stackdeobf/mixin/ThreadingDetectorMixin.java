package dev.booky.stackdeobf.mixin;
// Created by booky10 in StackDeobfuscator (18:50 20.03.23)

import dev.booky.stackdeobf.StackDeobfMod;
import net.minecraft.util.ThreadingDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ThreadingDetector.class)
public class ThreadingDetectorMixin {

    @Redirect(
            method = "stackTrace",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Thread;getStackTrace()[Ljava/lang/StackTraceElement;"
            )
    )
    private static StackTraceElement[] redirStackTrace(Thread thread) {
        StackTraceElement[] stackTrace = thread.getStackTrace();
        StackDeobfMod.remap(stackTrace);
        return stackTrace;
    }
}
