package net.tabor.seedcity.client;

import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.tabor.seedcity.entity.CollectorEntity;

/** A damped hanging weight, stepped once per client tick, never per render pass. */
public final class CollectorLanternMotion {
    private static final WeakHashMap<CollectorEntity,Motion> STATES=new WeakHashMap<>();
    private static final class Motion {
        double x,z,vx,vz,phase;float pitch,roll,oldPitch,oldRoll,pv,rv,lastAttack;
        Motion(CollectorEntity e){x=e.getX();z=e.getZ();}
    }
    private CollectorLanternMotion(){}
    public static void tick(Minecraft client) {
        if(client.level==null){STATES.clear();return;}
        if(client.isPaused())return;
        STATES.keySet().removeIf(e->e.isRemoved()||e.level()!=client.level);
        for(var e:client.level.entitiesForRendering()) {
            if(!(e instanceof CollectorEntity mob))continue;
            var m=STATES.computeIfAbsent(mob,Motion::new);
            double vx=mob.getX()-m.x,vz=mob.getZ()-m.z;
            if(vx*vx+vz*vz>4){STATES.put(mob,new Motion(mob));continue;}
            double yaw=Math.toRadians(mob.yBodyRot),sin=Math.sin(yaw),cos=Math.cos(yaw);
            double forward=-(vx-m.vx)*sin+(vz-m.vz)*cos;
            double side=(vx-m.vx)*cos+(vz-m.vz)*sin;
            double speed=Math.hypot(vx,vz);m.phase+=speed*7;
            float attack=mob.getAttackAnim(1);
            float impulse=attack>0&&m.lastAttack==0?.035F:0;m.lastAttack=attack;
            m.oldPitch=m.pitch;m.oldRoll=m.roll;
            m.pv+=(float)(-m.pitch*.18-m.pv*.22+forward*.7+Math.sin(m.phase)*Math.min(speed,.2)*.10)+impulse;
            m.rv+=(float)(-m.roll*.21-m.rv*.25+side*.45+Math.sin(m.phase*.5)*Math.min(speed,.2)*.04);
            m.pitch=Mth.clamp(m.pitch+m.pv,-.22F,.22F);m.roll=Mth.clamp(m.roll+m.rv,-.07F,.07F);
            m.x=mob.getX();m.z=mob.getZ();m.vx=vx;m.vz=vz;
        }
    }
    public static void extract(CollectorEntity e,CollectorRenderState state,float partial) {
        state.lanternPitch=state.lanternRoll=0;
        var m=STATES.get(e);if(m==null)return;
        state.lanternPitch=Mth.lerp(partial,m.oldPitch,m.pitch);
        state.lanternRoll=Mth.lerp(partial,m.oldRoll,m.roll);
    }
}
