"""Rebuild the Builder's shared geometry, UV atlas, Java mesh and Blockbench source.

Requires Python 3 and Pillow. No network or AI image generation. Cuboids and their
UVs are authored here so the preview, editable model and Minecraft mesh agree.
Run from any directory: python tools/build_builder_assets.py
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
ART = ROOT / 'art/builder'
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

group('body', [0, 10, 0])
group('head', [0, 6, 0], 'body')
# Group pivots below are in absolute model space; the exporter makes them local.
group('right_arm', [-6, 8, 0], 'body')
group('left_arm', [6, 8, 0], 'body')
group('right_leg', [-2.4, 16, 0], 'body')
group('left_leg', [2.4, 16, 0], 'body')
group('cargo', [-6, 15, -4], 'right_arm')

box('neck_joint', 'body', [-2, 5, -2], [4, 3, 4], 'joint')
box('torso', 'body', [-4, 8, -2.5], [8, 7, 5], 'copper')
box('chest_plate', 'body', [-3, 8, -3.5], [6, 5, 1], 'stone')
box('chest_inset', 'body', [-2, 9, -3.8], [4, 3, 1], 'patina')
box('chest_rune', 'body', [-.5, 10, -4], [1, 1, 1], 'cyan')
box('back_plate', 'body', [-3, 8, 2.5], [6, 5, 1], 'stone')
box('belt', 'body', [-4.5, 14, -3], [9, 2, 6], 'leather')
box('buckle', 'body', [-1.5, 14, -4], [3, 2, 1], 'brass')
box('buckle_center', 'body', [-.5, 14.5, -4.2], [1, 1, 1], 'joint')
box('hip_joint', 'body', [-3.5, 16, -2], [7, 1, 4], 'joint')
box('strap_front', 'body', [2, 7, -4], [1, 7, 1], 'leather')
box('strap_back', 'body', [2, 7, 3], [1, 7, 1], 'leather')
box('strap_over_shoulder', 'body', [2, 7, -3], [1, 1, 6], 'leather')
box('strap_clasp', 'body', [1.5, 9, -4.3], [2, 2, 1], 'brass')
box('pouch_left', 'body', [2, 15, -4], [3, 3, 2], 'leather')
box('pouch_right', 'body', [-5, 15, -4], [3, 3, 2], 'leather')
box('pouch_flap_left', 'body', [2, 15, -4.3], [3, 1, 1], 'brass')
box('pouch_flap_right', 'body', [-5, 15, -4.3], [3, 1, 1], 'brass')
box('hammer_handle', 'body', [-5, 12, 2], [1, 6, 1], 'wood')
box('hammer_head', 'body', [-6, 11, 1.5], [3, 2, 2], 'stone')

box('head_shell', 'head', [-3.5, 0, -3], [7, 6, 6], 'stone')
box('brow', 'head', [-4, 0, -3.5], [8, 2, 7], 'stone')
box('visor_recess', 'head', [-3, 2, -3.15], [6, 3, 1], 'joint')
box('right_eye', 'head', [-2, 2.5, -3.3], [1, 2, 1], 'eye')
box('left_eye', 'head', [1, 2.5, -3.3], [1, 2, 1], 'eye')
box('nose_bridge', 'head', [-.5, 2, -3.6], [1, 3, 1], 'stone')
box('jaw', 'head', [-3.5, 5, -3.5], [7, 1, 6], 'stone')
box('right_temple', 'head', [-4, 2, -.5], [1, 3, 3], 'copper')
box('left_temple', 'head', [3, 2, -.5], [1, 3, 3], 'copper')

for side, sign in [('right', -1), ('left', 1)]:
    x = sign * 6
    part = side + '_arm'
    box(side+'_shoulder', part, [x-2, 7, -2.5], [4, 4, 5], 'copper')
    box(side+'_shoulder_band', part, [x-2, 10, -2.6], [4, 1, 5], 'patina')
    box(side+'_elbow', part, [x-1, 11, -1.5], [2, 2, 3], 'joint')
    box(side+'_forearm', part, [x-1.5, 13, -2], [3, 3, 4], 'stone')
    box(side+'_cuff', part, [x-1.5, 15, -2.2], [3, 1, 4], 'brass')
    box(side+'_hand', part, [x-1.5, 16, -2], [3, 2, 3], 'stone')
    x = sign * 2.4
    part = side+'_leg'
    box(side+'_thigh', part, [x-1.5, 16, -1.5], [3, 3, 3], 'copper')
    box(side+'_knee', part, [x-1.5, 19, -2], [3, 2, 3], 'joint')
    box(side+'_shin', part, [x-1.5, 20, -2], [3, 3, 4], 'stone')
    box(side+'_boot', part, [x-2, 23, -3], [4, 1, 5], 'stone')
    box(side+'_boot_cuff', part, [x-1.5, 20, -2.2], [3, 1, 4], 'brass')

box('carried_stone', 'cargo', [-8.5, 12.5, -6.5], [5, 5, 5], 'rough_stone')

# Separate armor/decorative shells from the faces beneath them. Deformation keeps
# the authored box UVs intact and is exported identically to all three viewers.
shells = {
    'strap_front': .04, 'strap_back': .04, 'strap_clasp': .08,
    'belt': .04, 'pouch_flap_left': .04, 'pouch_flap_right': .04,
    'brow': .04, 'jaw': .04, 'nose_bridge': .04,
}
for side in ('right', 'left'):
    for suffix, amount in (('shoulder_band', .04), ('cuff', .04),
                           ('shin', .04), ('boot_cuff', .08)):
        shells[f'{side}_{suffix}'] = amount
for c in cubes:
    c['inflate'] = shells.get(c['name'], 0)

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
    'stone': (115,119,114), 'rough_stone': (106,112,110),
    'joint': (44,49,47), 'copper': (111,111,81), 'patina': (77,119,106),
    'leather': (102,65,40), 'brass': (146,109,68), 'wood': (89,61,36),
    'cyan': (69,225,235), 'eye': (80,240,248),
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
                    patch = math.sin((px+u)*1.35)+math.cos((py+v)*1.13)
                    color = (72,113,99) if patch>.1 else (133,100,72)
                if px == 0 or py == 0: delta += 12
                if px == fw-1 or py == fh-1: delta -= 13
                if mat == 'eye':
                    color = (177,255,255) if px == 0 else (46,203,225)
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
            draw.rectangle((fx,fy,fx+fw-1,fy+fh-1),fill=(24,151,180,255))
            draw.rectangle((fx+1,fy+1,fx+fw-2,fy+fh-2),fill=(89,240,248,255))
            draw.line([(fx+1,fy+1),(fx+1,fy+fh-3)],fill=(206,255,255,255))
image.save(TEX/'builder.png')
image.save(ART/'builder.png')
glow = Image.new('RGBA', image.size, (0,0,0,0))
for c in cubes:
    if c['material'] in ('eye', 'cyan'):
        for u,v,w,h in c['faces'].values():
            for y in range(v*DENSITY,(v+h)*DENSITY):
                for x in range(u*DENSITY,(u+w)*DENSITY):
                    pixel=image.getpixel((x,y))
                    glow.putpixel((x,y),pixel)
glow.save(TEX/'builder_glow.png')
glow.save(ART/'builder_glow.png')
spec = dict(textureWidth=256,textureHeight=256,groups=groups,cubes=cubes)
(ART/'builder.geometry.json').write_text(json.dumps(spec,indent=2)+'\n')

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

/** Generated by tools/build_builder_assets.py; edit the source, then regenerate. */
public final class BuilderMesh {
    private BuilderMesh() {}
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
''' + '\n'.join(lines) + '''
        return LayerDefinition.create(mesh, 256, 256);
    }
}
'''
(JAVA/'BuilderMesh.java').write_text(java)

def uid(name): return str(uuid.uuid5(uuid.NAMESPACE_URL,'seedcity/builder/'+name))
elements=[]
for c in cubes:
    lo,hi = bounds(c)
    x,y,z = lo; w,h,d = [b-a for a,b in zip(lo,hi)]
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
    x,y,z=g['pivot']
    return dict(name=g['name'],uuid=uid('group/'+g['name']),origin=[x,24-y,z],
        rotation=[0,0,0],export=True,isOpen=True,visibility=True,
        children=[uid(c['name']) for c in cubes if c['part']==g['name']] +
                 [outline(child) for child in groups if child['parent']==g['name']])
data='data:image/png;base64,'+base64.b64encode((ART/'builder.png').read_bytes()).decode()
bb=dict(meta={'format_version':'4.10','model_format':'free','box_uv':False},
    name='Seed City Builder',model_identifier='seedcity:builder',
    resolution={'width':256,'height':256},elements=elements,
    outliner=[outline(g) for g in groups if not g['parent']],
    textures=[{'name':'builder.png','uuid':uid('texture'),'id':'0','source':data,
               'width':1024,'height':1024,'uv_width':256,'uv_height':256}])
(ART/'builder.bbmodel').write_text(json.dumps(bb,indent=2)+'\n')
preview = (ART/'preview.template.html').read_text(encoding='utf-8').split('<script>')[0]
vendor = ROOT/'tools/vendor/three-0.180.0'
core_uri = 'data:text/javascript;base64,'+base64.b64encode((vendor/'three.core.js').read_bytes()).decode()
module = (vendor/'three.module.js').read_text(encoding='utf-8').replace('./three.core.js',core_uri)
module_uri = 'data:text/javascript;base64,'+base64.b64encode(module.encode()).decode()
glow_uri = 'data:image/png;base64,'+base64.b64encode((ART/'builder_glow.png').read_bytes()).decode()
runtime = (ART/'preview.js').read_text(encoding='utf-8').replace('__GEOMETRY__',json.dumps(spec)).replace('__TEXTURE__',data).replace('__GLOW__',glow_uri).replace('__THREE__',module_uri)
preview += '<script type="module">'+runtime+'</script></html>'
(ART/'builder-preview.html').write_text(preview,encoding='utf-8')
print(f'Builder: {len(cubes)} cuboids, {len(groups)} parts, atlas used through row {cursor_y+row_h}/256')
