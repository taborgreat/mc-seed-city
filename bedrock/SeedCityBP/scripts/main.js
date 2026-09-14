import { world, system, BlockPermutation, ItemStack, EquipmentSlot } from '@minecraft/server';

// Only replace air. Persist owned light locations so reloads can clean them up.
let owned = null;
system.runInterval(() => {
    if (owned === null) {
        try { owned = JSON.parse(world.getDynamicProperty('seedcity:lamp_positions') || '[]'); }
        catch { owned = []; }
    }
    const next = [], wanted = new Set();
    for (const id of ['overworld','nether','the_end']) {
        const dim = world.getDimension(id);
        for (const mob of dim.getEntities({type:'seedcity:collector'})) {
            const p = mob.location;
            const pos = {x:Math.floor(p.x),y:Math.floor(p.y+1.35),z:Math.floor(p.z)};
            const key = `${id}:${pos.x},${pos.y},${pos.z}`;
            wanted.add(key);
            try {
                const block = dim.getBlock(pos);
                if (block?.isAir) {
                    block.setPermutation(BlockPermutation.resolve('minecraft:light_block_12'));
                    next.push({id,pos,key});
                }
            } catch (e) { /* A mob at an unloaded chunk edge is retried next interval. */ }
        }
    }
    for (const entry of owned) {
        if (wanted.has(entry.key)) { if (!next.some(x=>x.key===entry.key)) next.push(entry); continue; }
        try {
            const b=world.getDimension(entry.id).getBlock(entry.pos);
            if (!b) { next.push(entry); continue; }
            if (b.typeId==='minecraft:light_block_12') b.setType('minecraft:air');
        } catch { next.push(entry); }
    }
    owned=next;
    world.setDynamicProperty('seedcity:lamp_positions',JSON.stringify(owned));
},5);

// Native equipment slots remain real item stacks; preserve an axe chosen by Tabor.
system.runInterval(()=>{
    for (const id of ['overworld','nether','the_end']) {
        for (const mob of world.getDimension(id).getEntities({type:'seedcity:collector'})) {
            mob.runCommand('execute unless entity @s[hasitem={item=minecraft:iron_pickaxe,location=slot.weapon.mainhand}] unless entity @s[hasitem={item=minecraft:iron_axe,location=slot.weapon.mainhand}] run replaceitem entity @s slot.weapon.mainhand 0 minecraft:iron_pickaxe');
        }
    }
},20);

// A new visitor receives the six real Creative spawn eggs once in the showcase.
world.afterEvents.playerSpawn.subscribe(({player,initialSpawn})=>{
    if (!initialSpawn || player.getDynamicProperty('seedcity:showcase_eggs')) return;
    if (!world.getDynamicProperty('seedcity:is_showcase')) return;
    system.run(()=>{
        const bag=player.getComponent('minecraft:inventory')?.container;
        if (!bag) return;
        for (const n of ['builder','warden','courier','collector','sentinel','redstone_rat']) bag.addItem(new ItemStack(`seedcity:${n}_spawn_egg`,16));
        player.setDynamicProperty('seedcity:showcase_eggs',true);
    });
});

system.afterEvents.scriptEventReceive.subscribe(({id})=>{
    if (id==='seedcity:showcase') world.setDynamicProperty('seedcity:is_showcase',true);
    if (id!=='seedcity:validate') return;
    const dim=world.getDimension('overworld');
    for (const n of ['builder','warden','courier','collector','sentinel','redstone_rat']) {
        console.warn(`[Seed City QA] ${n}: ${dim.getEntities({type:`seedcity:${n}`}).length} entities; egg ${new ItemStack(`seedcity:${n}_spawn_egg`).typeId}`);
    }
    const collector=dim.getEntities({type:'seedcity:collector'})[0];
    if (collector) {
        const p=collector.location;
        console.warn(`[Seed City QA] Collectors holding iron pickaxes: ${dim.runCommand('testfor @e[type=seedcity:collector,hasitem={item=minecraft:iron_pickaxe,location=slot.weapon.mainhand}]').successCount}; light: ${dim.getBlock({x:Math.floor(p.x),y:Math.floor(p.y+1.35),z:Math.floor(p.z)})?.typeId}`);
    }
    const rat=dim.getEntities({type:'seedcity:redstone_rat'}).find(e=>e.location.z>39 && e.location.z<60);
    const creeper=dim.getEntities({type:'minecraft:creeper'})[0];
    if(rat && creeper) {
        rat.triggerEvent('seedcity:pose_idle');rat.teleport({x:13,y:65,z:49});
        creeper.teleport({x:16,y:65,z:49});
        system.runTimeout(()=>{
            const r=rat.location,c=creeper.location;
            console.warn(`[Seed City QA] Creeper separation after 8 seconds: ${Math.hypot(r.x-c.x,r.z-c.z).toFixed(2)} blocks (started at 3)`);
        },160);
    }
});
