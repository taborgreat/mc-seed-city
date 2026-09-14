package net.tabor.seedcity.client;

import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.tabor.seedcity.entity.WardenEntity;

/** Uses Minecraft 26.2's actual avatar cloak lag; all state is client-only. */
public final class RectifierCapeMotion {
    private static final WeakHashMap<WardenEntity, ClientAvatarState> STATES = new WeakHashMap<>();
    private RectifierCapeMotion() {}

    private static ClientAvatarState create(WardenEntity entity) {
        var state = new ClientAvatarState();
        // Trigger vanilla's teleport reset on every axis so a spawn starts settled.
        state.tick(entity.position().add(100,100,100), Vec3.ZERO);
        state.tick(entity.position(), Vec3.ZERO);
        return state;
    }

    public static void tick(Minecraft client) {
        if (client.level == null) { STATES.clear(); return; }
        if (client.isPaused()) return;
        STATES.keySet().removeIf(e -> e.isRemoved() || e.level() != client.level);
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof WardenEntity rectifier)) continue;
            var state = STATES.computeIfAbsent(rectifier, RectifierCapeMotion::create);
            state.tick(rectifier.position(), rectifier.getDeltaMovement());
            float horizontal = (float)Math.hypot(rectifier.getX()-rectifier.xo, rectifier.getZ()-rectifier.zo);
            state.addWalkDistance(horizontal*.6F);
            state.updateBob(rectifier.onGround() ? Math.min(horizontal,.1F) : 0);
        }
    }

    public static void extract(WardenEntity entity, RectifierRenderState result, float partialTicks) {
        var state = STATES.computeIfAbsent(entity, RectifierCapeMotion::create);
        double dx = state.getInterpolatedCloakX(partialTicks)-Mth.lerp(partialTicks,entity.xo,entity.getX());
        double dy = state.getInterpolatedCloakY(partialTicks)-Mth.lerp(partialTicks,entity.yo,entity.getY());
        double dz = state.getInterpolatedCloakZ(partialTicks)-Mth.lerp(partialTicks,entity.zo,entity.getZ());
        double yaw = Math.toRadians(Mth.rotLerp(partialTicks,entity.yBodyRotO,entity.yBodyRot));
        double sin = Math.sin(yaw), minusCos = -Math.cos(yaw);
        // Same factors and clamps as AvatarRenderer.extractCapeState (no Elytra).
        result.capeFlap=Mth.clamp((float)dy*10,-6,32)
                +(float)Math.sin(state.getInterpolatedWalkDistance(partialTicks)*6)*32*state.getInterpolatedBob(partialTicks);
        result.capeLean=Mth.clamp((float)(dx*sin+dz*minusCos)*100,0,150);
        result.capeSide=Mth.clamp((float)(dx*minusCos-dz*sin)*100,-20,20);
    }
}
