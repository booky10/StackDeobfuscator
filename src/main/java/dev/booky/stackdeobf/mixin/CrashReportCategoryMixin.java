package dev.booky.stackdeobf.mixin;
// Created by booky10 in StackDeobfuscator (18:46 20.03.23)

import dev.booky.stackdeobf.RemappingUtil;
import net.minecraft.CrashReportCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrashReportCategory.class)
public class CrashReportCategoryMixin {

    @Shadow private StackTraceElement[] stackTrace;

    @Inject(
            method = "fillInStackTrace",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/System;arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V",
                    shift = At.Shift.AFTER
            )
    )
    public void postStackTraceFill(int i, CallbackInfoReturnable<Integer> cir) {
        RemappingUtil.remapStackTraceElements(this.stackTrace);
    }
}
