package net.tabor.seedcity.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public final class RectifierRenderState extends LivingEntityRenderState {
    public boolean repairing;
    public boolean grounded;
    public float flightSpeed;
    public float capeLean, capeFlap, capeSide;
}
