package dev.booky.stackdeobf.mixin;
// Created by booky10 in StackDeobfuscator (18:35 20.03.23)

import dev.booky.stackdeobf.RemappingUtil;
import net.minecraft.CrashReport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrashReport.class)
public class CrashReportMixin {

    @Mutable @Shadow @Final private Throwable exception;

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    public void postInit(String title, Throwable throwable, CallbackInfo ci) {
        this.exception = RemappingUtil.remapThrowable(throwable);
    }
}
