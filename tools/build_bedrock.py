"""Export approved cuboids to a separate Bedrock resource/behavior add-on."""
from pathlib import Path
import json, shutil, uuid, math
from build_showcase import POSES,NAMES
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'bedrock';BP=OUT/'SeedCityBP';RP=OUT/'SeedCityRP'
def put(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
def uid(name):return str(uuid.uuid5(uuid.NAMESPACE_URL,'https://github.com/Mcdrizzy/mc-seed-city/'+name))
for folder,typ in [(BP,'data'),(RP,'resources')]:
    put(folder/'manifest.json',{'format_version':2,'header':{'name':'Seed City Showcase '+typ,'description':'Six approved NPCs and mob showcase V1','uuid':uid(typ),'version':[1,0,0],'min_engine_version':[1,26,0]},'modules':[{'type':typ,'uuid':uid(typ+'module'),'version':[1,0,0]}],**({'dependencies':[{'uuid':uid('resources'),'version':[1,0,0]}]} if typ=='data' else {})})
langs=[]
for name,poses in POSES.items():
    art='rectifier' if name=='warden' else name.replace('_','-')
    tex='rectifier' if name=='warden' else name
    bb=json.loads(next((ROOT/'art'/art).glob('*.bbmodel')).read_text())
    cubes={c['uuid']:c for c in bb['elements']};bones=[]
    def visit(g,parent=None):
        b={'name':g['name'],'pivot':g['origin'],'cubes':[]}
        if parent:b['parent']=parent
        if any(g.get('rotation',[])):b['rotation']=g['rotation']
        for c in g['children']:
            if isinstance(c,dict):continue
            e=cubes[c];o=e['from'];size=[e['to'][i]-o[i] for i in range(3)]
            uv={f:{'uv':v['uv'][:2],'uv_size':[v['uv'][2]-v['uv'][0],v['uv'][3]-v['uv'][1]]} for f,v in e['faces'].items()}
            b['cubes'].append({'origin':o,'size':size,'uv':uv})
        bones.append(b)
        for c in g['children']:
            if isinstance(c,dict):visit(c,g['name'])
    for g in bb['outliner']:visit(g)
    if name=='collector':bones.append({'name':'rightitem','parent':'right_arm','pivot':[-5.6,8,0]})
    put(RP/f'models/entity/{name}.geo.json',{'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':f'geometry.seedcity.{name}','texture_width':256,'texture_height':256,'visible_bounds_width':5,'visible_bounds_height':5,'visible_bounds_offset':[0,1.5,0]},'bones':bones}]})
    for suffix in ['', '_glow']:
        src=ROOT/f'src/main/resources/assets/seedcity/textures/entity/{tex}{suffix}.png'
        dest=RP/f'textures/entity/{name}{suffix}.png';dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dest)
    poseidx={p:i+1 for i,p in enumerate(poses)}
    walk="(q.property('seedcity:pose') == %d"%poseidx['walk']
    if 'run' in poseidx:walk+=" || q.property('seedcity:pose') == %d"%poseidx['run']
    walk+=") ? 1.0 : (q.property('seedcity:pose') == 0 ? math.min(q.modified_move_speed * 3.0, 1.0) : 0.0)"
    animbones={}
    for leg,sign in [('right_leg',1),('left_leg',-1)]:animbones[leg]={'rotation':[f'math.sin(q.anim_time * 380) * {30*sign} * v.walk',0,0]}
    for arm,sign in [('right_arm',-1),('left_arm',1)]:animbones[arm]={'rotation':[f'math.sin(q.anim_time * 380) * {18*sign} * v.walk',0,0]}
    look=poseidx.get('look',-1)
    animbones['head']={'rotation':[f"q.property('seedcity:pose') == {look} ? 48 + math.sin(q.anim_time*45)*8 : -math.clamp(q.target_x_rotation,-60,30)",'-math.clamp(q.target_y_rotation,-35,35)',0]}
    if name=='builder':
        animbones['cargo']={'scale':f"q.property('seedcity:pose') == {poseidx['carry']} ? 1 : 0"}
        work=f"q.property('seedcity:pose') == {poseidx['build']}"
        animbones['right_arm']['rotation']=[f'({work}) ? 7+v.stroke*69+v.arc*23 : math.sin(q.anim_time*380)*-18*v.walk',f'({work}) ? v.sweep*18 : 0',f'({work}) ? v.arc*23-2 : -2']
        animbones['body']={'position':[0,"q.property('seedcity:pose') == 0 || q.property('seedcity:pose') == %d ? math.sin(q.anim_time*115)*0.22 : 0"%poseidx['fly'],0]}
        animbones['body']['rotation']=[0,f'({work}) ? v.sweep*9 : 0',0]
    if name=='warden':
        animbones['right_arm']['rotation'][0]='12.6+math.sin(q.anim_time*380)*-3*v.walk'
        animbones['right_forearm']={'rotation':[37.2,0,0]}
        animbones['lantern']={'rotation':['-49.8+math.sin(q.anim_time*380)*3*v.walk',0,0]}
        animbones['left_forearm']={'rotation':[40.1,0,0]}
        animbones['hammer']={'rotation':[-90,0,0]}
        work=f"q.property('seedcity:pose') == {poseidx['repair']}"
        animbones['left_arm']['rotation']=[f'({work}) ? 8.6+v.stroke*60+v.arc*17 : math.sin(q.anim_time*380)*18*v.walk',f'({work}) ? -v.sweep*14 : 0',f'({work}) ? 2-v.arc*14 : 2']
        animbones['body']={'rotation':[0,f'({work}) ? -v.sweep*7 : 0',0]}
        # Native hinged cloth: speed lifts the cape; both pieces retain independent sway.
        animbones['cape']={'rotation':['-3-v.walk*7-math.sin(q.anim_time*140)*(1+v.walk*3)',0,'math.sin(q.anim_time*110)*(1+v.walk)']}
        animbones['tabard']={'rotation':['4+v.walk*2+math.sin(q.anim_time*180)*(1.4+v.walk*3)',0,'math.sin(q.anim_time*103)*2.5']}
    if name=='collector':
        act=f"q.property('seedcity:pose') == {poseidx['mine']} || q.property('seedcity:pose') == {poseidx['chop']}"
        animbones['right_arm']['rotation']=[f'({act}) ? 45+math.sin(q.anim_time*1200)*40 : math.sin(q.anim_time*380)*-18*v.walk',f'({act}) ? math.sin(q.anim_time*1200)*15 : 0',f'({act}) ? math.sin(q.anim_time*1200)*-12 : 0']
        animbones['lantern']={'rotation':['math.sin(q.anim_time*240)*(2+v.walk*9)',0,'math.sin(q.anim_time*190)*(1+v.walk*5)']}
    if name=='sentinel':
        for leg,sign in [('right_leg',1),('left_leg',-1)]:
            animbones[leg]['rotation'][0]=f"math.sin(q.anim_time * (q.property('seedcity:pose') == {poseidx['run']} ? 330 : 275)) * {24*sign} * v.walk"
        animbones['left_forearm']={'rotation':[66,0,0]};animbones['spear']={'rotation':[-66,0,0]}
        animbones['left_arm']['rotation']=[f"q.property('seedcity:pose') == {poseidx['attack']} ? 10 + math.max(0,math.sin(q.anim_time*360))*55 : 0",0,0]
        animbones['head']['rotation'][0]=f"(q.property('seedcity:pose') == 0 && q.property('seedcity:signal') == 0) || q.property('seedcity:pose') == {poseidx['sleep']} ? -18 : "+animbones['head']['rotation'][0]
    if name=='redstone_rat':
        animbones={k:v for k,v in animbones.items() if k=='head'}
        animbones['head']['rotation'][1]='-math.clamp(q.target_y_rotation,-26,26)+math.cos(q.anim_time*500)*3*v.walk'
        for i in range(2,7):animbones[f'segment_{i}']={'rotation':[0,f'math.cos(q.anim_time*500-{i*37})*{4*(1+abs(i-2)*.35)}*(.08+v.walk*.92)',0],'position':[f'math.sin(q.anim_time*500-{i*37})*.14*v.walk',0,0]}
        for i in range(3):animbones[f'tail_{i}']={'rotation':[0,f'math.cos(q.anim_time*500-{258+i*29})*11*(.08+v.walk*.92)',0]}
        for side in ['left','right']:
            for end in ['front','back']:
                phase=180 if ((side=='left')==(end=='front')) else 0
                animbones[f'paw_{side}_{end}']={'position':[0,f'math.max(0,math.sin(q.anim_time*1000+{phase}))*.18*v.walk',f'math.cos(q.anim_time*1000+{phase})*.36*v.walk']}
    existing={b['name'] for b in bones};animbones={k:v for k,v in animbones.items() if k in existing}
    put(RP/f'animations/{name}.animation.json',{'format_version':'1.8.0','animations':{f'animation.seedcity.{name}':{'loop':True,'bones':animbones}}})
    colors={'builder':['59776D','53E7ED'],'warden':['56595C','FFB238'],'courier':['526D65','FFD46C'],'collector':['596E61','FFB33C'],'sentinel':['4B5055','E83830'],'redstone_rat':['604139','E83D32']}
    client={'identifier':f'seedcity:{name}','materials':{'default':'entity_alphatest','glow':'entity_emissive_alpha'},'textures':{'default':f'textures/entity/{name}','glow':f'textures/entity/{name}_glow'},'geometry':{'default':f'geometry.seedcity.{name}'},'animations':{'main':f'animation.seedcity.{name}'},'scripts':{'pre_animation':['v.walk = '+walk+';'],'animate':['main']},'render_controllers':['controller.render.seedcity','controller.render.seedcity_glow'],'spawn_egg':{'base_color':'#'+colors[name][0],'overlay_color':'#'+colors[name][1]},'enable_attachables':name=='collector'}
    if name in ('builder','warden'):
        client['scripts']['pre_animation'] += ['v.p = math.mod(q.anim_time,0.3)/0.3;', 'v.stroke = math.sin((1-math.pow(1-v.p,4))*180);', 'v.arc = math.sin(v.p*180);', 'v.sweep = math.sin(math.sqrt(v.p)*360);']
    if name=='sentinel':
        for i in range(16):
            shutil.copy2(ROOT/f'src/main/resources/assets/seedcity/textures/entity/sentinel_glow_{i}.png',RP/f'textures/entity/sentinel_glow_{i}.png')
            client['textures'][f'glow{i}']=f'textures/entity/sentinel_glow_{i}'
        signal=f"q.property('seedcity:pose') == {poseidx['sleep']} ? 0 : (q.property('seedcity:pose') == {poseidx['alert']} ? 7 : (q.property('seedcity:pose') > 0 ? 15 : q.property('seedcity:signal')))"
        put(RP/'render_controllers/sentinel.json',{'format_version':'1.8.0','render_controllers':{'controller.render.seedcity_sentinel_glow':{'arrays':{'textures':{'Array.glows':[f'Texture.glow{i}' for i in range(16)]}},'geometry':'Geometry.default','materials':[{'*':'Material.glow'}],'textures':['Array.glows['+signal+']']}}})
        client['render_controllers'][1]='controller.render.seedcity_sentinel_glow'
    put(RP/f'entity/{name}.entity.json',{'format_version':'1.10.0','minecraft:client_entity':{'description':client}})
    w,h={'builder':(.8,1.5),'warden':(1.4,2.95),'courier':(.48,.48),'collector':(.95,1.8),'sentinel':(1.1,2.63),'redstone_rat':(.55,.3)}[name]
    components={'minecraft:type_family':{'family':[name,'mob']+(['cat'] if name=='redstone_rat' else [])},'minecraft:health':{'value':30,'max':30},'minecraft:collision_box':{'width':w,'height':h},'minecraft:movement':{'value':.25 if name!='courier' else .30},'minecraft:movement.basic':{},'minecraft:navigation.walk':{'avoid_water':True,'can_path_over_water':False},'minecraft:jump.static':{},'minecraft:physics':{},'minecraft:pushable':{'is_pushable':True,'is_pushable_by_piston':True},'minecraft:persistent':{},'minecraft:nameable':{},'minecraft:behavior.random_stroll':{'priority':6,'speed_multiplier':1},'minecraft:behavior.look_at_player':{'priority':7,'look_distance':8},'minecraft:behavior.random_look_around':{'priority':8}}
    if name=='redstone_rat':
        components['minecraft:ambient_sound_interval']={'value':120,'range':60,'event_name':'ambient'}
    if name=='builder':
        for k in ['minecraft:movement.basic','minecraft:navigation.walk','minecraft:behavior.random_stroll']:components.pop(k)
        components.update({'minecraft:can_fly':{},'minecraft:flying_speed':{'value':.1},'minecraft:movement.hover':{},'minecraft:navigation.hover':{'can_path_from_air':True,'can_path_over_water':True,'avoid_water':True},'minecraft:physics':{'has_gravity':False},'minecraft:behavior.random_hover':{'priority':6,'hover_height':[1,3],'xz_dist':8,'y_dist':4,'interval':1}})
    groups={'exhibit':{'minecraft:movement':{'value':0},'minecraft:flying_speed':{'value':0},'minecraft:pushable':{'is_pushable':False,'is_pushable_by_piston':False},'minecraft:knockback_resistance':{'value':1},'minecraft:damage_sensor':{'triggers':[{'cause':'all','deals_damage':'no'}]}}}
    events={f'seedcity:pose_{p}':{'add':{'component_groups':['exhibit']},'set_property':{'seedcity:pose':i}} for p,i in poseidx.items()}
    if name=='sentinel':
        components['minecraft:knockback_resistance']={'value':.5}
        components['minecraft:movement']={'value':0}
        groups['active']={'minecraft:movement':{'value':.25}}
        groups['hostile']={'minecraft:attack':{'damage':4},'minecraft:behavior.nearest_attackable_target':{'priority':2,'must_see':True,'entity_types':[{'filters':{'test':'is_family','subject':'other','value':'player'},'max_dist':24}]},'minecraft:behavior.melee_attack':{'priority':3,'speed_multiplier':1,'track_target':True}}
        for i in range(16):events[f'seedcity:signal_{i}']={'remove':{'component_groups':['active','hostile']},'add':{'component_groups':(['active'] if i>0 else [])+(['hostile'] if i==15 else [])},'set_property':{'seedcity:signal':i}}
    if name=='collector':
        components['minecraft:equipment']={'table':'loot_tables/collector_tools.json'}
        put(BP/'loot_tables/collector_tools.json',{'pools':[{'rolls':1,'entries':[{'type':'item','name':'minecraft:iron_pickaxe','weight':1}]}]})
    properties={'seedcity:pose':{'type':'int','range':[0,16],'default':0,'client_sync':True}}
    if name=='sentinel':properties['seedcity:signal']={'type':'int','range':[0,15],'default':0,'client_sync':True}
    put(BP/f'entities/{name}.json',{'format_version':'1.26.0','minecraft:entity':{'description':{'identifier':f'seedcity:{name}','is_spawnable':True,'is_summonable':True,'properties':properties},'components':components,'component_groups':groups,'events':events}})
    title=NAMES.get(name,name.title());langs += [f'entity.seedcity:{name}.name={title}',f'item.spawn_egg.entity.seedcity:{name}.name={title} Spawn Egg']
put(RP/'render_controllers/mobs.json',{'format_version':'1.8.0','render_controllers':{f'controller.render.{n}':{'geometry':'Geometry.default','materials':[{'*':f'Material.{mat}'}],'textures':[f'Texture.{tex}']} for n,mat,tex in [('seedcity','default','default'),('seedcity_glow','glow','glow')]}})
put(RP/'texts/languages.json',['en_US']);(RP/'texts/en_US.lang').write_text('\n'.join(langs)+'\n')
print('Exported six Bedrock entities, Creative spawn eggs, geometry and looping animations.')
put(RP/'sounds.json',{'entity_sounds':{'entities':{'seedcity:redstone_rat':{'events':{'ambient':'mob.silverfish.say','hurt':'mob.silverfish.hit','death':'mob.silverfish.kill'},'volume':.25,'pitch':[.9,1.1]}}}})
manifest=json.loads((BP/'manifest.json').read_text())
manifest['modules'].append({'type':'script','language':'javascript','uuid':uid('script'),'version':[1,0,0],'entry':'scripts/main.js'})
manifest['dependencies'].append({'module_name':'@minecraft/server','version':'2.0.0'})
put(BP/'manifest.json',manifest)
