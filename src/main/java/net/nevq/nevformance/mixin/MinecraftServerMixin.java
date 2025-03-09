package net.nevq.nevformance.mixin;

import net.nevq.nevformance.Nevformance;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into the Minecraft server tick method to capture performance metrics
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    // Store the start time of the current tick
    private long tickStartTime = 0;

    /**
     * Inject at the start of the server tick method to record the start time
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickStart(CallbackInfo ci) {
        tickStartTime = System.nanoTime();
    }

    /**
     * Inject at the end of the server tick method to calculate the tick duration
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void onTickEnd(CallbackInfo ci) {
        long tickEndTime = System.nanoTime();
        long tickDurationNanos = tickEndTime - tickStartTime;

        // Convert to milliseconds for easier human reading
        double tickDurationMs = tickDurationNanos / 1_000_000.0;

        // You can add more detailed metrics here as needed
        // For now, just log the tick duration
        if (Nevformance.getInstance() != null) {
            // Record tick duration metric
            // We'll let the MetricsManager handle the actual recording elsewhere
            // This is just a demonstration of how mixins can capture timing data
        }
    }
}