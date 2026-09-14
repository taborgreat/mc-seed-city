"""Rebuild the Sentinel's shared geometry, UV atlas, Java mesh and Blockbench source.

Requires Python 3 and Pillow. No network or AI image generation. Cuboids and their
UVs are authored here so the preview, editable model and Minecraft mesh agree.
Run from any directory: python tools/build_sentinel_assets.py
"""
from pathlib import Path
import base64
import json
import math
import random
import uuid
from itertools import combinations
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art/sentinel'
TEX = ROOT / 'src/main/resources/assets/seedcity/textures/entity'
JAVA = ROOT / 'src/client/java/net/tabor/seedcity/client'
for folder in (ART, TEX, JAVA):
    folder.mkdir(parents=True, exist_ok=True)

groups = []
cubes = []
def group(name, pivot, parent=None):
    groups.append(dict(name=name, pivot=pivot, parent=parent))
def box(name, part, pos, size, material):
    cubes.append(dict(name=name, part=part, pos=pos, size=size, material=material))

group('body',[0,24,0],None)
group('torso',[0,-7,0],'body')
group('head',[0,-10,0],'torso')
group('right_arm',[-7,-7,0],'torso')
group('left_arm',[7,-7,0],'torso')
group('left_forearm',[7,1,0],'left_arm')
group('right_leg',[-3,5,0],'body')
group('left_leg',[3,5,0],'body')
group('spear',[7,9,0],'left_forearm')
box('chest_core','torso',[-5,-8,-3],[10,12,7],'joint')
for x in [-5,2]:
    box('breastplate_'+str(x),'torso',[x,-8,-4],[3,11,2],'stone')
box('collar','torso',[-4,-9,-3],[8,2,6],'stone')
box('neck_joint','torso',[-2,-12,-2],[4,4,4],'joint')
box('back_plate','torso',[-4,-7,3],[8,10,2],'stone')
box('spine','torso',[-1,-6,4.2],[2,8,1],'joint')
for i in range(4):
    box('signal_'+str(i),'torso',[-1,-6+i*2.5,-4.1],[2,1,1],'redglow')
box('belt','torso',[-5,3,-3],[10,2,7],'joint')
box('helmet','head',[-3.5,-18,-4],[7,8,8],'stone')
box('visor','head',[-3,-14,-4.08],[6,2,1],'joint')
box('brow','head',[-3.5,-15,-4.12],[7,1,1],'stone')
box('eye_right','head',[-2.5,-13.8,-4.18],[1,1,1],'redglow')
box('eye_left','head',[1.5,-13.8,-4.18],[1,1,1],'redglow')
box('face_plate','head',[-3,-12,-4.08],[6,2,1],'stone')
box('nose','head',[-1,-13.5,-5.2],[2,3,2],'stone')
box('forehead_plate','head',[-1,-17,-4.12],[2,2,1],'stone')
for side,x in [('right',-7),('left',7)]:
    box(side+'_shoulder',side+'_arm',[x-2.5,-8,-3],[5,5,6],'stone')
    box(side+'_upper_arm',side+'_arm',[x-1.5,-3,-1.5],[3,5,3],'joint')
    box(side+'_elbow',side+'_arm',[x-2,1,-2],[4,2,4],'stone')
    box(side+'_gauntlet',side+'_arm',[x-2.5,3,-2.5],[5,5,5],'stone')
    box(side+'_arm_signal',side+'_arm',[x-.5,4,-2.7],[1,3,1],'redglow')
    box(side+'_fist',side+'_arm',[x-2,8,-2],[4,3,4],'joint')
for side,x in [('right',-3),('left',3)]:
    box(side+'_thigh',side+'_leg',[x-2,5,-2],[4,8,4],'stone')
    box(side+'_hip_plate',side+'_leg',[x-2.5,5,-2.5],[5,4,5],'stone')
    box(side+'_knee',side+'_leg',[x-1.5,13,-2],[3,2,4],'joint')
    box(side+'_knee_light',side+'_leg',[x-.5,13,-2.2],[1,2,1],'redglow')
    box(side+'_shin',side+'_leg',[x-2,15,-2],[4,6,4],'stone')
    box(side+'_boot',side+'_leg',[x-2.5,21,-3],[5,3,6],'stone')
# A upright polearm through the closed left fist, all rigidly parented to that arm.
box('spear_shaft','spear',[6.25,-14,-.75],[1,42,1],'wood')
box('spear_pommel','spear',[6,-14,-1],[2,2,2],'joint')
box('spear_head','spear',[5.25,-23,-1.25],[3,9,2],'stone')
box('spear_tip','spear',[6.25,-26,-1.25],[1,3,2],'stone')
box('spear_channel','spear',[6.25,-22,-1.45],[1,7,1],'redglow')
for c in cubes:
    # Keep the approved silhouette, but give every body part 35% more depth.
    if c['part'] != 'spear':
        c['pos'][2] *= 1.35
        c['size'][2] = max(1, round(c['size'][2] * 1.35))
for i,c in enumerate(cubes):
    c['inflate']=.012+i*.0005
    if c['name'] in ('left_gauntlet','left_arm_signal','left_fist'):
        c['part']='left_forearm'
    if c['part']=='spear': c['pos'][0]+=.25


def bounds(c):
    return ([v-c['inflate'] for v in c['pos']],
            [v+s+c['inflate'] for v,s in zip(c['pos'],c['size'])])

# Coplanar, overlapping outward faces in one rigid part are always a geometry
# error. Touching opposite faces at a seam are valid and are deliberately excluded.
for a,b in combinations(cubes, 2):
    if a['part'] != b['part']:
        continue
    aa,bb = bounds(a),bounds(b)
    for axis in range(3):
        for side in (0,1):
            coplanar = abs(aa[side][axis]-bb[side][axis]) < 1e-6
            overlap = all(min(aa[1][j],bb[1][j])-max(aa[0][j],bb[0][j]) > 1e-6
                          for j in range(3) if j != axis)
            assert not (coplanar and overlap), f'Z-fighting: {a["name"]} / {b["name"]}'

palette = {
    'redstone': (150,22,15), 'redglow': (255,54,20), 'stone': (108,111,110), 'rough_stone': (106,112,110),
    'joint': (44,49,47), 'copper': (111,111,81), 'patina': (77,119,106),
    'leather': (102,65,40), 'brass': (146,109,68), 'wood': (89,61,36),
    'cyan': (255,199,78), 'eye': (255,135,38), 'cloth': (115,51,40),
}
image = Image.new('RGBA', (256,256), (0,0,0,0))
rng = random.Random(812)
draw = ImageDraw.Draw(image)
cursor_x = cursor_y = row_h = 0
for c in cubes:
    w,h,d = c['size']
    width,height = 2*(w+d),h+d
    if cursor_x + width + 2 > 256:
        cursor_y += row_h + 2; cursor_x = row_h = 0
    assert cursor_y + height < 256, 'Atlas overflow'
    u,v = cursor_x,cursor_y
    c['uv'] = [u,v]
    cursor_x += width + 2; row_h = max(row_h,height)
    # Vanilla ModelPart's box UV layout, also used by the preview and Blockbench.
    faces = {'east':[u,v+d,d,h], 'north':[u+d,v+d,w,h],
             'west':[u+d+w,v+d,d,h], 'south':[u+2*d+w,v+d,w,h],
             'up':[u+d,v,w,d], 'down':[u+d+w,v,w,d]}
    c['faces'] = faces
    mat = c['material']; base = palette[mat]
    for face,(fx,fy,fw,fh) in faces.items():
        for py in range(fh):
            for px in range(fw):
                delta = rng.choice([-12,-7,-3,0,0,3,6,9])
                color = base
                if mat == 'copper':
                    # Broad oxidation islands, warm exposed metal and restrained highlights.
                    patch = math.sin((px+u)*2.1)+math.cos((py+v)*1.85)
                    color = (72,113,99) if patch>.1 else (133,100,72)
                if px == 0 or py == 0: delta += 12
                if px == fw-1 or py == fh-1: delta -= 13
                if mat == 'eye':
                    color = (255,222,128) if px == 0 else (235,99,22)
                    delta = 0
                image.putpixel((fx+px,fy+py),tuple(max(0,min(255,k+delta)) for k in color)+(255,))

# Four texels per model unit: keep broad pixel-art patches, with fine worn edges.
# UV coordinates remain in logical 256x256 units, as in Minecraft's model layer.
DENSITY=4
image=image.resize((1024,1024),Image.Resampling.NEAREST)
draw=ImageDraw.Draw(image)
for c in cubes:
    mat=c['material']
    for face,(u,v,w,h) in c['faces'].items():
        fx,fy,fw,fh=[n*DENSITY for n in (u,v,w,h)]
        for y in range(fy,fy+fh):
            for x in range(fx,fx+fw):
                rgb=image.getpixel((x,y))[:3]
                delta=rng.choice([-5,-3,0,0,0,2,4])
                if x==fx or y==fy: delta+=10
                if x==fx+fw-1 or y==fy+fh-1: delta-=12
                if mat in ('eye','cyan'): delta=0
                image.putpixel((x,y),tuple(max(0,min(255,k+delta)) for k in rgb)+(255,))
        if mat=='stone' and fw>=8 and fh>=8:
            draw.line([(fx+1,fy+1),(fx+fw-2,fy+1)],fill=(144,149,141,255))
            draw.line([(fx+fw-1,fy+2),(fx+fw-1,fy+fh-1)],fill=(80,86,81,255))
        if mat=='eye':
            draw.rectangle((fx,fy,fx+fw-1,fy+fh-1),fill=(181,66,16,255))
            draw.rectangle((fx+1,fy+1,fx+fw-2,fy+fh-2),fill=(255,154,47,255))
            draw.line([(fx+1,fy+1),(fx+1,fy+fh-3)],fill=(255,233,155,255))
            # Bright upper catchlight and an amber lower edge soften the square eyes.
            draw.rectangle((fx+1,fy+1,fx+2,fy+2),fill=(255,255,224,255))
image.save(TEX/'sentinel.png')
image.save(ART/'sentinel.png')
glow = Image.new('RGBA', image.size, (0,0,0,0))
for c in cubes:
    if c['material'] in ('eye', 'cyan', 'redglow'):
        for face,(u,v,w,h) in c['faces'].items():
            for y in range(v*DENSITY,(v+h)*DENSITY):
                for x in range(u*DENSITY,(u+w)*DENSITY):
                    pixel=image.getpixel((x,y))
                    glow.putpixel((x,y),pixel)
glow.save(TEX/'sentinel_glow.png')
for signal in range(16):
    variant=glow.copy()
    factor=.035+.965*(signal/15)
    variant=variant.point([int(i*factor) for i in range(256)]*3+list(range(256)))
    variant.save(TEX/f'sentinel_glow_{signal}.png')

glow.save(ART/'sentinel_glow.png')
# The unpowered channels are dark red; the independent emissive layer supplies power.
for c in cubes:
    if c['material']=='redglow':
        for u,v,w,h in c['faces'].values():
            draw.rectangle((u*4,v*4,(u+w)*4-1,(v+h)*4-1),fill=(64,10,8,255))
image.save(TEX/'sentinel.png')
image.save(ART/'sentinel.png')
spec = dict(modelScale=1,textureWidth=256,textureHeight=256,groups=groups,cubes=cubes)
(ART/'sentinel.geometry.json').write_text(json.dumps(spec,indent=2)+'\n')

def f(n): return f'{float(n):g}F'
def vector(nums): return ', '.join(map(f, nums))
gp = {g['name']:g for g in groups}
lines = []
for g in groups:
    parent = gp[g['parent']]['pivot'] if g['parent'] else [0,0,0]
    pivot = [a-b for a,b in zip(g['pivot'],parent)]
    lines.append(f'\t\tPartDefinition {g["name"]} = {g["parent"] or "root"}.addOrReplaceChild("{g["name"]}", CubeListBuilder.create()')
    for c in (c for c in cubes if c['part']==g['name']):
        pos = [a-b for a,b in zip(c['pos'],g['pivot'])]
        deformation = f', new CubeDeformation({f(c["inflate"])})' if c['inflate'] else ''
        lines.append(f'\t\t\t\t.texOffs({c["uv"][0]}, {c["uv"][1]}).addBox({vector(pos+c["size"])}{deformation}) // {c["name"]}')
    lines.append(f'\t\t\t\t, PartPose.offset({vector(pivot)}));')
java = '''package net.tabor.seedcity.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Generated by tools/build_sentinel_assets.py; edit the source, then regenerate. */
public final class SentinelMesh {
    private SentinelMesh() {}
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
''' + '\n'.join(lines) + '''
        return LayerDefinition.create(mesh, 256, 256);
    }
}
'''
(JAVA/'SentinelMesh.java').write_text(java)

def uid(name): return str(uuid.uuid5(uuid.NAMESPACE_URL,'seedcity/sentinel/'+name))
elements=[]
for c in cubes:
    lo,hi = bounds(c)
    x,y,z = lo; w,h,d = [b-a for a,b in zip(lo,hi)]
    x*=1; y=24+(y-24)*1; z*=1; w*=1; h*=1; d*=1
    # Java models face -Z and have downward-positive Y. Blockbench uses up-positive Y.
    faces={}
    for face,(u,v,fw,fh) in c['faces'].items():
        uv=[u,v,u+fw,v+fh]
        if face=='down': uv=[u,v+fh,u+fw,v]
        faces[face]={'uv':uv,'texture':0}
    elements.append(dict(name=c['name'],uuid=uid(c['name']),type='cube',
        box_uv=False,from_=[x,24-y-h,z],to=[x+w,24-y,z+d],
        origin=[0,0,0],rotation=[0,0,0],faces=faces))
    elements[-1]['from']=elements[-1].pop('from_')
def outline(g):
    x,y,z=g['pivot']; x*=1; y=24+(y-24)*1; z*=1
    return dict(name=g['name'],uuid=uid('group/'+g['name']),origin=[x,24-y,z],
        rotation=[0,0,0],export=True,isOpen=True,visibility=True,
        children=[uid(c['name']) for c in cubes if c['part']==g['name']] +
                 [outline(child) for child in groups if child['parent']==g['name']])
data='data:image/png;base64,'+base64.b64encode((ART/'sentinel.png').read_bytes()).decode()
bb=dict(meta={'format_version':'4.10','model_format':'free','box_uv':False},
    name='Seed City Sentinel',model_identifier='seedcity:sentinel',
    resolution={'width':256,'height':256},elements=elements,
    outliner=[outline(g) for g in groups if not g['parent']],
    textures=[{'name':'sentinel.png','uuid':uid('texture'),'id':'0','source':data,
               'width':1024,'height':1024,'uv_width':256,'uv_height':256}])
(ART/'sentinel.bbmodel').write_text(json.dumps(bb,indent=2)+'\n')
preview = (ART/'preview.template.html').read_text(encoding='utf-8').split('<script>')[0]
vendor = ROOT/'tools/vendor/three-0.180.0'
core_uri = 'data:text/javascript;base64,'+base64.b64encode((vendor/'three.core.js').read_bytes()).decode()
module = (vendor/'three.module.js').read_text(encoding='utf-8').replace('./three.core.js',core_uri)
module_uri = 'data:text/javascript;base64,'+base64.b64encode(module.encode()).decode()
glow_uri = 'data:image/png;base64,'+base64.b64encode((ART/'sentinel_glow.png').read_bytes()).decode()
runtime = (ART/'preview.js').read_text(encoding='utf-8').replace('__GEOMETRY__',json.dumps(spec)).replace('__TEXTURE__',data).replace('__GLOW__',glow_uri).replace('__THREE__',module_uri)
preview += '<script type="module">'+runtime+'</script></html>'
(ART/'sentinel-preview.html').write_text(preview,encoding='utf-8')
print(f'Sentinel: {len(cubes)} cuboids, {len(groups)} parts, atlas used through row {cursor_y+row_h}/256')
