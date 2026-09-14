"""Reproducible, explicit showcase function. Only run in a new disposable world."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
POSES={'builder':['idle','walk','fly','build','carry','look'],
       'warden':['idle','walk','repair','fly','look'],
       'courier':['idle','walk','look'],
       'collector':['idle','walk','mine','chop','look'],
       'sentinel':['sleep','alert','idle','walk','run','attack','look'],
       'redstone_rat':['idle','walk','look']}
NAMES={'warden':'Rectifier','redstone_rat':'Redstone Rat'}
commands=[]
def cmd(s):commands.append(s)
def fill(x,y,z,X,Y,Z,b):cmd(f'fill {x} {y} {z} {X} {Y} {Z} minecraft:{b}')
def label(x,y,z,text,size=1):
    cmd(f'summon minecraft:text_display {x} {y} {z} '+"{Tags:[\"seedcity_showcase\"],billboard:\"center\",background:1073741824,line_width:220,text:"+json.dumps(text)+f',transformation:{{scale:[{size}f,{size}f,{size}f]}}'+'}')
cmd('# WARNING: replaces blocks x=-3..82, y=63..81, z=-3..65. Fresh showcase worlds only.')
cmd('kill @e[tag=seedcity_showcase]')
cmd('kill @e[tag=seedcity_showcase_roam]')
cmd('difficulty normal');cmd('time set noon');cmd('weather clear')
cmd('gamerule minecraft:advance_time false');cmd('gamerule minecraft:spawn_mobs false');cmd('gamerule minecraft:mob_griefing false')
cmd('gamemode creative @a')
for x in range(-3,83,16):
    fill(x,63,-3,min(82,x+15),64,65,'smooth_stone')
    fill(x,65,-3,min(82,x+15),81,65,'air')
fill(-3,65,-3,82,65,-3,'polished_deepslate');fill(-3,65,65,82,65,65,'polished_deepslate')
fill(-3,65,-3,-3,65,65,'polished_deepslate');fill(82,65,-3,82,65,65,'polished_deepslate')
fill(1,64,2,32,64,32,'grass_block')
for z in [2,32]:fill(1,65,z,32,69,z,'glass')
for x in [1,32]:fill(x,65,2,x,69,32,'glass')
label(16,72,16,'FREE ROAM / Six city companions',1.1)
for i,name in enumerate(POSES):
    x=7+(i%3)*8;z=10+(i//3)*12;y=67 if name=='builder' else 65
    title=NAMES.get(name,name.title())
    alert=',Alertness:7' if name=='sentinel' else ''
    cmd(f'summon seedcity:{name} {x} {y} {z} '+ '{PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase_roam"],CustomName:'+json.dumps(title)+alert+'}')
# Live avoidance cage is wide enough for the native six-block avoidance range.
fill(1,64,39,32,64,60,'polished_andesite')
for z in [39,60]:fill(1,65,z,32,69,z,'glass')
for x in [1,32]:fill(x,65,39,x,69,60,'glass')
fill(1,70,39,32,70,60,'glass')
cmd('summon seedcity:redstone_rat 13 65 49 {PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"Redstone Rat"}')
cmd('summon minecraft:creeper 16 65 49 {PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:"Creeper"}')
label(16,72,49,'LIVE CREEPER AVOIDANCE / Observe from outside',1.0)
for row,(name,poses) in enumerate(POSES.items()):
    z=5+row*10
    label(39,69,z,NAMES.get(name,name.title()),.85)
    for col,pose in enumerate(poses):
        x=43+col*5
        fill(x-1,65,z-1,x+1,65,z+1,'polished_deepslate')
        cmd(f'setblock {x} 65 {z} minecraft:sea_lantern')
        extra=',NoGravity:1b' if name=='builder' else ''
        cmd(f'summon seedcity:{name} {x} 66 {z} '+'{NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:["seedcity_showcase"],CustomName:'+json.dumps('SC:'+pose)+',Rotation:[180f,0f]'+extra+'}')
        if pose=='chop':cmd(f'item replace entity @e[type=seedcity:collector,sort=nearest,limit=1,x={x},y=66,z={z}] weapon.mainhand with minecraft:iron_axe')
        label(x,65.3,z-2,pose.upper(),.65)
label(39,72,-1,'mob showcase V1',1.8)
label(39,70,-1,'Free roam | Animation library | Creeper avoidance',.85)
cmd('setworldspawn 36 65 0')
cmd('tp @a 36 65 0 0 0')
cmd('setblock 36 65 2 minecraft:barrel')
label(36,67,2,'CREATIVE SPAWN EGGS',.7)
for slot,name in enumerate(POSES):
    egg='rectifier' if name=='warden' else name
    cmd(f'give @a seedcity:{egg}_spawn_egg 16')
    cmd(f'item replace block 36 65 2 container.{slot} with seedcity:{egg}_spawn_egg 16')
out=ROOT/'src/main/resources/data/seedcity/function/showcase'
out.mkdir(parents=True,exist_ok=True)
(out/'build.mcfunction').write_text('\n'.join(commands)+'\n')
print(f'{len(commands)} showcase commands; {sum(map(len,POSES.values()))} animation exhibits')
