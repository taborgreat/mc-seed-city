# WARNING: replaces blocks x=-3..82, y=63..81, z=-3..65. Fresh showcase worlds only.
kill @e[tag=seedcity_showcase]
kill @e[tag=seedcity_showcase_roam]
difficulty normal
time set noon
weather clear
gamerule minecraft:advance_time false
gamerule minecraft:spawn_mobs false
gamerule minecraft:mob_griefing false
gamemode creative @a
fill -3 63 -3 12 64 65 minecraft:smooth_stone
fill -3 65 -3 12 81 65 minecraft:air
fill 13 63 -3 28 64 65 minecraft:smooth_stone
fill 13 65 -3 28 81 65 minecraft:air
fill 29 63 -3 44 64 65 minecraft:smooth_stone
fill 29 65 -3 44 81 65 minecraft:air
fill 45 63 -3 60 64 65 minecraft:smooth_stone
fill 45 65 -3 60 81 65 minecraft:air
fill 61 63 -3 76 64 65 minecraft:smooth_stone
fill 61 65 -3 76 81 65 minecraft:air
fill 77 63 -3 82 64 65 minecraft:smooth_stone
fill 77 65 -3 82 81 65 minecraft:air
fill -3 65 -3 82 65 -3 minecraft:polished_deepslate
fill -3 65 65 82 65 65 minecraft:polished_deepslate
fill -3 65 -3 -3 65 65 minecraft:polished_deepslate
fill 82 65 -3 82 65 65 minecraft:polished_deepslate
fill 1 64 2 32 64 32 minecraft:grass_block
fill 1 65 2 32 69 2 minecraft:glass
fill 1 65 32 32 69 32 minecraft:glass
fill 1 65 2 1 69 32 minecraft:glass
fill 32 65 2 32 69 32 minecraft:glass
summon minecraft:text_display 16 72 16 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"FREE ROAM / Six city companions",transformation:{scale:[1.1f,1.1f,1.1f]}}
summon seedcity:builder 7 67 10 {PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase_roam"],CustomName:"Builder"}
summon seedcity:warden 15 65 10 {PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase_roam"],CustomName:"Rectifier"}
summon seedcity:courier 23 65 10 {PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase_roam"],CustomName:"Courier"}
summon seedcity:collector 7 65 22 {PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase_roam"],CustomName:"Collector"}
summon seedcity:sentinel 15 65 22 {PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase_roam"],CustomName:"Sentinel",Alertness:7}
summon seedcity:redstone_rat 23 65 22 {PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase_roam"],CustomName:"Redstone Rat"}
fill 1 64 39 32 64 60 minecraft:polished_andesite
fill 1 65 39 32 69 39 minecraft:glass
fill 1 65 60 32 69 60 minecraft:glass
fill 1 65 39 1 69 60 minecraft:glass
fill 32 65 39 32 69 60 minecraft:glass
fill 1 70 39 32 70 60 minecraft:glass
summon seedcity:redstone_rat 13 65 49 {PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"Redstone Rat"}
summon minecraft:creeper 16 65 49 {PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"Creeper"}
summon minecraft:text_display 16 72 49 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"LIVE CREEPER AVOIDANCE / Observe from outside",transformation:{scale:[1.0f,1.0f,1.0f]}}
summon minecraft:text_display 39 69 5 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"Builder",transformation:{scale:[0.85f,0.85f,0.85f]}}
fill 42 65 4 44 65 6 minecraft:polished_deepslate
setblock 43 65 5 minecraft:sea_lantern
summon seedcity:builder 43 66 5 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:idle",Rotation:[180f,0f],NoGravity:1b}
summon minecraft:text_display 43 65.3 3 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"IDLE",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 47 65 4 49 65 6 minecraft:polished_deepslate
setblock 48 65 5 minecraft:sea_lantern
summon seedcity:builder 48 66 5 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:walk",Rotation:[180f,0f],NoGravity:1b}
summon minecraft:text_display 48 65.3 3 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"WALK",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 52 65 4 54 65 6 minecraft:polished_deepslate
setblock 53 65 5 minecraft:sea_lantern
summon seedcity:builder 53 66 5 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:fly",Rotation:[180f,0f],NoGravity:1b}
summon minecraft:text_display 53 65.3 3 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"FLY",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 57 65 4 59 65 6 minecraft:polished_deepslate
setblock 58 65 5 minecraft:sea_lantern
summon seedcity:builder 58 66 5 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:build",Rotation:[180f,0f],NoGravity:1b}
summon minecraft:text_display 58 65.3 3 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"BUILD",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 62 65 4 64 65 6 minecraft:polished_deepslate
setblock 63 65 5 minecraft:sea_lantern
summon seedcity:builder 63 66 5 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:carry",Rotation:[180f,0f],NoGravity:1b}
summon minecraft:text_display 63 65.3 3 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"CARRY",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 67 65 4 69 65 6 minecraft:polished_deepslate
setblock 68 65 5 minecraft:sea_lantern
summon seedcity:builder 68 66 5 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:look",Rotation:[180f,0f],NoGravity:1b}
summon minecraft:text_display 68 65.3 3 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"LOOK",transformation:{scale:[0.65f,0.65f,0.65f]}}
summon minecraft:text_display 39 69 15 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"Rectifier",transformation:{scale:[0.85f,0.85f,0.85f]}}
fill 42 65 14 44 65 16 minecraft:polished_deepslate
setblock 43 65 15 minecraft:sea_lantern
summon seedcity:warden 43 66 15 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:idle",Rotation:[180f,0f]}
summon minecraft:text_display 43 65.3 13 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"IDLE",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 47 65 14 49 65 16 minecraft:polished_deepslate
setblock 48 65 15 minecraft:sea_lantern
summon seedcity:warden 48 66 15 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:walk",Rotation:[180f,0f]}
summon minecraft:text_display 48 65.3 13 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"WALK",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 52 65 14 54 65 16 minecraft:polished_deepslate
setblock 53 65 15 minecraft:sea_lantern
summon seedcity:warden 53 66 15 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:repair",Rotation:[180f,0f]}
summon minecraft:text_display 53 65.3 13 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"REPAIR",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 57 65 14 59 65 16 minecraft:polished_deepslate
setblock 58 65 15 minecraft:sea_lantern
summon seedcity:warden 58 66 15 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:fly",Rotation:[180f,0f]}
summon minecraft:text_display 58 65.3 13 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"FLY",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 62 65 14 64 65 16 minecraft:polished_deepslate
setblock 63 65 15 minecraft:sea_lantern
summon seedcity:warden 63 66 15 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:look",Rotation:[180f,0f]}
summon minecraft:text_display 63 65.3 13 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"LOOK",transformation:{scale:[0.65f,0.65f,0.65f]}}
summon minecraft:text_display 39 69 25 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"Courier",transformation:{scale:[0.85f,0.85f,0.85f]}}
fill 42 65 24 44 65 26 minecraft:polished_deepslate
setblock 43 65 25 minecraft:sea_lantern
summon seedcity:courier 43 66 25 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:idle",Rotation:[180f,0f]}
summon minecraft:text_display 43 65.3 23 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"IDLE",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 47 65 24 49 65 26 minecraft:polished_deepslate
setblock 48 65 25 minecraft:sea_lantern
summon seedcity:courier 48 66 25 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:walk",Rotation:[180f,0f]}
summon minecraft:text_display 48 65.3 23 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"WALK",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 52 65 24 54 65 26 minecraft:polished_deepslate
setblock 53 65 25 minecraft:sea_lantern
summon seedcity:courier 53 66 25 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:look",Rotation:[180f,0f]}
summon minecraft:text_display 53 65.3 23 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"LOOK",transformation:{scale:[0.65f,0.65f,0.65f]}}
summon minecraft:text_display 39 69 35 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"Collector",transformation:{scale:[0.85f,0.85f,0.85f]}}
fill 42 65 34 44 65 36 minecraft:polished_deepslate
setblock 43 65 35 minecraft:sea_lantern
summon seedcity:collector 43 66 35 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:idle",Rotation:[180f,0f]}
summon minecraft:text_display 43 65.3 33 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"IDLE",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 47 65 34 49 65 36 minecraft:polished_deepslate
setblock 48 65 35 minecraft:sea_lantern
summon seedcity:collector 48 66 35 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:walk",Rotation:[180f,0f]}
summon minecraft:text_display 48 65.3 33 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"WALK",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 52 65 34 54 65 36 minecraft:polished_deepslate
setblock 53 65 35 minecraft:sea_lantern
summon seedcity:collector 53 66 35 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:mine",Rotation:[180f,0f]}
summon minecraft:text_display 53 65.3 33 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"MINE",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 57 65 34 59 65 36 minecraft:polished_deepslate
setblock 58 65 35 minecraft:sea_lantern
summon seedcity:collector 58 66 35 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:chop",Rotation:[180f,0f]}
item replace entity @e[type=seedcity:collector,sort=nearest,limit=1,x=58,y=66,z=35] weapon.mainhand with minecraft:iron_axe
summon minecraft:text_display 58 65.3 33 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"CHOP",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 62 65 34 64 65 36 minecraft:polished_deepslate
setblock 63 65 35 minecraft:sea_lantern
summon seedcity:collector 63 66 35 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:look",Rotation:[180f,0f]}
summon minecraft:text_display 63 65.3 33 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"LOOK",transformation:{scale:[0.65f,0.65f,0.65f]}}
summon minecraft:text_display 39 69 45 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"Sentinel",transformation:{scale:[0.85f,0.85f,0.85f]}}
fill 42 65 44 44 65 46 minecraft:polished_deepslate
setblock 43 65 45 minecraft:sea_lantern
summon seedcity:sentinel 43 66 45 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:sleep",Rotation:[180f,0f]}
summon minecraft:text_display 43 65.3 43 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"SLEEP",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 47 65 44 49 65 46 minecraft:polished_deepslate
setblock 48 65 45 minecraft:sea_lantern
summon seedcity:sentinel 48 66 45 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:alert",Rotation:[180f,0f]}
summon minecraft:text_display 48 65.3 43 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"ALERT",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 52 65 44 54 65 46 minecraft:polished_deepslate
setblock 53 65 45 minecraft:sea_lantern
summon seedcity:sentinel 53 66 45 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:idle",Rotation:[180f,0f]}
summon minecraft:text_display 53 65.3 43 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"IDLE",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 57 65 44 59 65 46 minecraft:polished_deepslate
setblock 58 65 45 minecraft:sea_lantern
summon seedcity:sentinel 58 66 45 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:walk",Rotation:[180f,0f]}
summon minecraft:text_display 58 65.3 43 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"WALK",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 62 65 44 64 65 46 minecraft:polished_deepslate
setblock 63 65 45 minecraft:sea_lantern
summon seedcity:sentinel 63 66 45 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:run",Rotation:[180f,0f]}
summon minecraft:text_display 63 65.3 43 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"RUN",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 67 65 44 69 65 46 minecraft:polished_deepslate
setblock 68 65 45 minecraft:sea_lantern
summon seedcity:sentinel 68 66 45 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:attack",Rotation:[180f,0f]}
summon minecraft:text_display 68 65.3 43 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"ATTACK",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 72 65 44 74 65 46 minecraft:polished_deepslate
setblock 73 65 45 minecraft:sea_lantern
summon seedcity:sentinel 73 66 45 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:look",Rotation:[180f,0f]}
summon minecraft:text_display 73 65.3 43 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"LOOK",transformation:{scale:[0.65f,0.65f,0.65f]}}
summon minecraft:text_display 39 69 55 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"Redstone Rat",transformation:{scale:[0.85f,0.85f,0.85f]}}
fill 42 65 54 44 65 56 minecraft:polished_deepslate
setblock 43 65 55 minecraft:sea_lantern
summon seedcity:redstone_rat 43 66 55 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:idle",Rotation:[180f,0f]}
summon minecraft:text_display 43 65.3 53 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"IDLE",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 47 65 54 49 65 56 minecraft:polished_deepslate
setblock 48 65 55 minecraft:sea_lantern
summon seedcity:redstone_rat 48 66 55 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:walk",Rotation:[180f,0f]}
summon minecraft:text_display 48 65.3 53 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"WALK",transformation:{scale:[0.65f,0.65f,0.65f]}}
fill 52 65 54 54 65 56 minecraft:polished_deepslate
setblock 53 65 55 minecraft:sea_lantern
summon seedcity:redstone_rat 53 66 55 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"SC:look",Rotation:[180f,0f]}
summon minecraft:text_display 53 65.3 53 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"LOOK",transformation:{scale:[0.65f,0.65f,0.65f]}}
summon minecraft:text_display 39 72 -1 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"mob showcase V1",transformation:{scale:[1.8f,1.8f,1.8f]}}
summon minecraft:text_display 39 70 -1 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"Free roam | Animation library | Creeper avoidance",transformation:{scale:[0.85f,0.85f,0.85f]}}
setworldspawn 36 65 0
tp @a 36 65 0 0 0
setblock 36 65 2 minecraft:barrel
summon minecraft:text_display 36 67 2 {Tags:["seedcity_showcase"],billboard:"center",background:1073741824,line_width:220,text:"CREATIVE SPAWN EGGS",transformation:{scale:[0.7f,0.7f,0.7f]}}
give @a seedcity:builder_spawn_egg 16
item replace block 36 65 2 container.0 with seedcity:builder_spawn_egg 16
give @a seedcity:rectifier_spawn_egg 16
item replace block 36 65 2 container.1 with seedcity:rectifier_spawn_egg 16
give @a seedcity:courier_spawn_egg 16
item replace block 36 65 2 container.2 with seedcity:courier_spawn_egg 16
give @a seedcity:collector_spawn_egg 16
item replace block 36 65 2 container.3 with seedcity:collector_spawn_egg 16
give @a seedcity:sentinel_spawn_egg 16
item replace block 36 65 2 container.4 with seedcity:sentinel_spawn_egg 16
give @a seedcity:redstone_rat_spawn_egg 16
item replace block 36 65 2 container.5 with seedcity:redstone_rat_spawn_egg 16
