"""Rebuild the Collector's shared geometry, UV atlas, Java mesh and Blockbench source.

Requires Python 3 and Pillow. No network or AI image generation. Cuboids and their
UVs are authored here so the preview, editable model and Minecraft mesh agree.
Run from any directory: python tools/build_collector_assets.py
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
ART = ROOT / 'art/collector'
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
group('torso',[0,3,0],'body')
group('head',[0,3,0],'torso')
group('right_arm',[-7,4,0],'torso')
group('left_arm',[7,4,0],'torso')
group('right_leg',[-3,14,0],'body')
group('left_leg',[3,14,0],'body')
group('lantern',[7.5,0,9],'torso')
# Riveted mounting plate, supported arm and a separate swinging bail handle.
box('lantern_mount_plate','body',[4.6,-2,7.5],[1,4,3],'copper')
box('lantern_mount_rivet_top','body',[5.45,-1.6,8.5],[1,1,1],'brass')
box('lantern_mount_rivet_bottom','body',[5.45,.5,8.5],[1,1,1],'brass')
box('lantern_bracket_arm','body',[5,-.75,8.5],[3,1,1],'joint')
box('lantern_bracket_brace_lower','body',[5,.5,8.5],[1,1,1],'joint')
box('lantern_bracket_brace_upper','body',[5.8,-.05,8.5],[1,1,1],'joint')
# Same cap, glass, base and four corner posts as the Rectifier, scaled after UVs.
box('lantern_bail_top','lantern',[6,-.3,8.5],[3,1,1],'joint')
box('lantern_bail_left','lantern',[6,.5,8.5],[1,3,1],'joint')
box('lantern_bail_right','lantern',[8,.5,8.5],[1,3,1],'joint')
box('lantern_cap','lantern',[5,3,6.5],[5,1,5],'brass')
box('lantern_cap_crown','lantern',[6,2.5,7.5],[3,1,3],'joint')
box('lantern_light','lantern',[6,4,7.5],[3,4,3],'lantern_glass')
box('lantern_base','lantern',[5,8,6.5],[5,1,5],'brass')
for lx in (5,9):
    for lz in (6.5,10.5):
        box(f'lantern_post_{lx}_{lz}','lantern',[lx,4,lz],[1,4,1],'joint')
box('torso','body',[-5,3,-3],[10,11,7],'copper')
box('apron','body',[-4,5,-3.5],[8,7,1],'leather')
box('apron_center','body',[-3,6,-3.7],[6,4,1],'patina')
box('belt','body',[-5,12,-3.5],[10,2,8],'leather')
box('belt_buckle','body',[-1,12,-3.8],[2,2,1],'brass')
box('pack','body',[-5,-2,4],[10,15,5],'leather')
box('pack_rim','body',[-5,-3,4],[10,2,5],'copper')
box('pack_strap_right','body',[-4,-1,8.5],[1,14,1],'brass')
box('pack_strap_left','body',[3,-1,8.5],[1,14,1],'brass')
box('pack_bottom','body',[-5,12,4],[10,2,5],'copper')
box('stone_cargo','body',[-4,-8,4],[5,6,4],'rough_stone')
box('stone_cargo_top','body',[-3,-10,5],[3,2,3],'stone')
box('log_cargo','body',[1,-9,4],[3,7,4],'wood')
box('log_cut_end','body',[1,-9.1,4],[3,1,4],'brass')
box('log_cargo_short','body',[0,-5,7],[4,3,3],'wood')
box('helmet','head',[-4,-5,-4],[8,8,8],'copper')
box('helmet_crown','head',[-4,-5.1,-4],[8,2,8],'patina')
box('helmet_brim','head',[-4.5,-3.5,-4.5],[9,1,9],'copper')
# Quiet, continuous face plating; widely spaced flush eyes avoid a goggle/mask look.
box('face_inset','head',[-3.5,-2.5,-4.08],[7,4,1],'copper')
box('eye_right','head',[-3,-1.75,-4.16],[2,2,1],'eye')
box('eye_left','head',[1,-1.75,-4.16],[2,2,1],'eye')
box('lamp_bracket','head',[-2,-5,-4.7],[4,2,1],'joint')
box('lamp_rim','head',[-1.5,-5,-5],[3,2,1],'brass')
box('lamp_light','head',[-1,-5,-5.3],[2,2,1],'cyan')
for side,x in [('right',-7),('left',7)]:
    box(side+'_shoulder',side+'_arm',[x-2,3,-2],[4,4,5],'copper')
    box(side+'_upper_arm',side+'_arm',[x-1.5,7,-1.5],[3,4,3],'joint')
    box(side+'_gauntlet',side+'_arm',[x-2,10,-2],[4,5,4],'stone')
    box(side+'_hand',side+'_arm',[x-1.5,14,-1.5],[3,3,3],'stone')
for side,x in [('right',-3),('left',3)]:
    box(side+'_thigh',side+'_leg',[x-2,14,-2],[4,5,4],'joint')
    box(side+'_shin',side+'_leg',[x-2,18,-2],[4,4,4],'copper')
    box(side+'_boot',side+'_leg',[x-2.5,22,-3],[5,2,6],'leather')
for i,c in enumerate(cubes):
    c['inflate']=.012+i*.0005
    if c['part']=='body': c['part']='torso'

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
    'redstone': (150,22,15), 'redglow': (255,54,20), 'stone': (115,119,114), 'rough_stone': (106,112,110),
    'joint': (44,49,47), 'copper': (111,111,81), 'patina': (77,119,106),
    'leather': (102,65,40), 'brass': (146,109,68), 'wood': (89,61,36),
    'lantern_glass': (255,157,40), 'cyan': (255,199,78), 'eye': (255,135,38), 'cloth': (115,51,40),
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
                if mat == 'lantern_glass':
                    color=(255,230,139) if 0<px<fw-1 else (233,124,26)
                    delta=0
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
image.save(TEX/'collector.png')
image.save(ART/'collector.png')
glow = Image.new('RGBA', image.size, (0,0,0,0))
for c in cubes:
    if c['material'] in ('eye', 'cyan', 'redglow', 'lantern_glass'):
        for face,(u,v,w,h) in c['faces'].items():
            for y in range(v*DENSITY,(v+h)*DENSITY):
                for x in range(u*DENSITY,(u+w)*DENSITY):
                    pixel=image.getpixel((x,y))
                    glow.putpixel((x,y),pixel)
glow.save(TEX/'collector_glow.png')
glow.save(ART/'collector_glow.png')
# Keep full-size UVs for the familiar Rectifier pattern while shrinking the actual mesh.
for c in cubes:
    if c['part']=='lantern':
        c['pos']=[pivot+(v-pivot)*.7 for v,pivot in zip(c['pos'],[7.5,0,9])]
        c['size']=[v*.7 for v in c['size']]
        c['inflate']*=.7
spec = dict(modelScale=.8,textureWidth=256,textureHeight=256,groups=groups,cubes=cubes)
(ART/'collector.geometry.json').write_text(json.dumps(spec,indent=2)+'\n')

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
        scale=.7 if g['name']=='lantern' else 1
        # Vanilla computes UV faces from cube sizes. Scale the part, preserving source UVs.
        pos=[v/scale for v in pos]
        size=[round(v/scale,6) for v in c['size']]
        deformation = f', new CubeDeformation({f(c["inflate"]/scale)})' if c['inflate'] else ''
        lines.append(f'\t\t\t\t.texOffs({c["uv"][0]}, {c["uv"][1]}).addBox({vector(pos+size)}{deformation}) // {c["name"]}')
    scale_pose='.withScale(.7F)' if g['name']=='lantern' else ''
    lines.append(f'\t\t\t\t, PartPose.offset({vector(pivot)}){scale_pose});')
java = '''package net.tabor.seedcity.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Generated by tools/build_collector_assets.py; edit the source, then regenerate. */
public final class CollectorMesh {
    private CollectorMesh() {}
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
''' + '\n'.join(lines) + '''
        return LayerDefinition.create(mesh, 256, 256);
    }
}
'''
(JAVA/'CollectorMesh.java').write_text(java)

def uid(name): return str(uuid.uuid5(uuid.NAMESPACE_URL,'seedcity/collector/'+name))
elements=[]
for c in cubes:
    lo,hi = bounds(c)
    x,y,z = lo; w,h,d = [b-a for a,b in zip(lo,hi)]
    x*=.8; y=24+(y-24)*.8; z*=.8; w*=.8; h*=.8; d*=.8
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
    x,y,z=g['pivot']; x*=.8; y=24+(y-24)*.8; z*=.8
    return dict(name=g['name'],uuid=uid('group/'+g['name']),origin=[x,24-y,z],
        rotation=[0,0,0],export=True,isOpen=True,visibility=True,
        children=[uid(c['name']) for c in cubes if c['part']==g['name']] +
                 [outline(child) for child in groups if child['parent']==g['name']])
data='data:image/png;base64,'+base64.b64encode((ART/'collector.png').read_bytes()).decode()
bb=dict(meta={'format_version':'4.10','model_format':'free','box_uv':False},
    name='Seed City Collector',model_identifier='seedcity:collector',
    resolution={'width':256,'height':256},elements=elements,
    outliner=[outline(g) for g in groups if not g['parent']],
    textures=[{'name':'collector.png','uuid':uid('texture'),'id':'0','source':data,
               'width':1024,'height':1024,'uv_width':256,'uv_height':256}])
(ART/'collector.bbmodel').write_text(json.dumps(bb,indent=2)+'\n')
preview = (ART/'preview.template.html').read_text(encoding='utf-8').split('<script>')[0]
vendor = ROOT/'tools/vendor/three-0.180.0'
core_uri = 'data:text/javascript;base64,'+base64.b64encode((vendor/'three.core.js').read_bytes()).decode()
module = (vendor/'three.module.js').read_text(encoding='utf-8').replace('./three.core.js',core_uri)
module_uri = 'data:text/javascript;base64,'+base64.b64encode(module.encode()).decode()
glow_uri = 'data:image/png;base64,'+base64.b64encode((ART/'collector_glow.png').read_bytes()).decode()
runtime = (ART/'preview.js').read_text(encoding='utf-8').replace('__GEOMETRY__',json.dumps(spec)).replace('__TEXTURE__',data).replace('__GLOW__',glow_uri).replace('__THREE__',module_uri)
preview += '<script type="module">'+runtime+'</script></html>'
(ART/'collector-preview.html').write_text(preview,encoding='utf-8')
print(f'Collector: {len(cubes)} cuboids, {len(groups)} parts, atlas used through row {cursor_y+row_h}/256')
