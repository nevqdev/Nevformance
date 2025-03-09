package net.nevq.nevformance.mixin;

import net.nevq.nevformance.Nevformance;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin hooks into the server tick method to measure accurate tick times
 * and detect lag spikes.
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerTickMixin {

    // Store the start time of the current tick
    private long tickStartTime = 0;

    // Track consecutive slow ticks
    private int consecutiveSlowTicks = 0;

    // Threshold for slow ticks (in milliseconds)
    private static final double SLOW_TICK_THRESHOLD = 50.0; // 50ms = 20 TPS

    /**
     * Called at the start of the server tick method
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickStart(CallbackInfo ci) {
        tickStartTime = System.nanoTime();
    }

    /**
     * Called at the end of the server tick method
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void onTickEnd(CallbackInfo ci) {
        // Calculate tick duration
        long tickEndTime = System.nanoTime();
        long tickDurationNanos = tickEndTime - tickStartTime;
        double tickDurationMs = tickDurationNanos / 1_000_000.0;

        // Check if this was a slow tick
        boolean isSlowTick = tickDurationMs > SLOW_TICK_THRESHOLD;

        // Update consecutive slow ticks counter
        if (isSlowTick) {
            consecutiveSlowTicks++;
        } else {
            // Reset only if we've had a few good ticks in a row
            // This prevents the counter from resetting due to a single good tick
            // in the middle of a lag spike
            if (consecutiveSlowTicks > 0) {
                consecutiveSlowTicks--;
            }
        }

        // Log warnings for sustained lag
        if (consecutiveSlowTicks >= 20) { // 1 second of consecutive slow ticks
            // Only log every 20 ticks to avoid spam
            if (consecutiveSlowTicks % 20 == 0) {
                Nevformance.LOGGER.warn("Server experiencing sustained lag: {} consecutive slow ticks",
                        consecutiveSlowTicks);
            }
        }

        // Record extreme lag spikes for analysis
        if (tickDurationMs > 500) { // Over 500ms is a very noticeable lag spike
            Nevformance.LOGGER.warn("Major lag spike detected: {}ms", String.format("%.2f", tickDurationMs));
            // Could add additional diagnostics here - memory dump, thread analysis, etc.
        }
    }
}