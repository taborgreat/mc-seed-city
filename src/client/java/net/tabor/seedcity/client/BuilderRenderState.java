package net.tabor.seedcity.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** A client snapshot; no renderer reaches into the server's task queue. */
public final class BuilderRenderState extends LivingEntityRenderState {
    public boolean building;
    public boolean carrying;
    public float flightSpeed;
    public boolean grounded;
}
