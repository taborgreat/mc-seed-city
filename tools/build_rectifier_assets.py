"""Rebuild the Rectifier's shared geometry, UV atlas, Java mesh and Blockbench source.

Requires Python 3 and Pillow. No network or AI image generation. Cuboids and their
UVs are authored here so the preview, editable model and Minecraft mesh agree.
Run from any directory: python tools/build_rectifier_assets.py
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
ART = ROOT / 'art/rectifier'
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

group('body', [0, -3, 0], None)
group('cape', [0, -13, 6], 'body')
group('tabard', [0, 1, -4.5], 'body')
group('head', [0, -14, 0], 'body')
group('right_arm', [-11, -10, 0], 'body')
group('left_arm', [11, -10, 0], 'body')
group('right_leg', [-5, 1, 0], 'body')
group('left_leg', [5, 1, 0], 'body')
group('right_forearm', [-11, 0, 0], 'right_arm')
group('lantern', [-11, 7, 0], 'right_forearm')
group('left_forearm', [11, 0, 0], 'left_arm')
group('hammer', [11, 7, -1], 'left_forearm')

box('neck', 'body', [-2, -16, -2], [4, 4, 4], 'joint')
box('torso', 'body', [-6, -14, -4], [12, 13, 8], 'stone')
box('chest_rim', 'body', [-4, -13, -5], [8, 9, 2], 'stone')
box('chest_socket', 'body', [-3, -12, -5.4], [6, 7, 1], 'leather')
box('core', 'body', [-2, -11, -5.65], [4, 5, 1], 'eye')
box('core_center', 'body', [-1, -10, -5.8], [2, 3, 1], 'cyan')
box('chest_left', 'body', [4, -14, -5.5], [3, 10, 3], 'stone')
box('chest_right', 'body', [-7, -14, -5.5], [3, 10, 3], 'stone')
box('back_plate', 'body', [-5, -13, 4], [10, 10, 1], 'stone')
box('back_spine', 'body', [-1, -12, 5], [2, 8, 1], 'joint')
box('belt', 'body', [-6, -1, -3.5], [12, 3, 7], 'joint')
box('buckle', 'body', [-2, -1.5, -5], [4, 3, 2], 'stone')
box('tabard_trim', 'tabard', [-2, 1, -4.5], [4, 14, 1], 'brass')
box('tabard', 'tabard', [-1.5, 1.5, -4.7], [3, 13, 1], 'cloth')
box('hip', 'body', [-5, 1, -3], [10, 2, 6], 'joint')
box('helmet', 'head', [-4.5, -23, -4], [9, 9, 8], 'stone')
box('crown', 'head', [-4.5, -23, -4.5], [9, 3, 9], 'stone')
box('face_shadow', 'head', [-3.5, -20, -4.2], [7, 4, 1], 'joint')
box('vertical_visor', 'head', [-0.5, -20, -4.4], [1, 5, 1], 'eye')
box('cheek_right', 'head', [-4, -19, -4.5], [3, 5, 1], 'stone')
box('cheek_left', 'head', [1, -19, -4.5], [3, 5, 1], 'stone')
box('right_shoulder', 'right_arm', [-14.5, -16, -3.5], [7, 8, 7], 'copper')
box('right_upper_arm', 'right_arm', [-13, -8, -2], [4, 6, 4], 'stone')
box('right_elbow', 'right_arm', [-12.5, -2, -1.5], [3, 2, 3], 'joint')
box('right_forearm', 'right_forearm', [-14, -1, -3], [6, 7, 6], 'stone')
box('right_hand', 'right_forearm', [-13, 5, -2], [4, 3, 4], 'stone')
box('right_thigh', 'right_leg', [-8, 1, -3], [6, 12, 6], 'stone')
box('right_knee', 'right_leg', [-7.5, 12, -2.5], [5, 2, 5], 'joint')
box('right_greave', 'right_leg', [-8.5, 14, -3.5], [7, 8, 7], 'stone')
box('right_greave_band', 'right_leg', [-8.5, 14, -3.5], [7, 3, 7], 'copper')
box('right_boot', 'right_leg', [-9, 22, -4.5], [8, 2, 8], 'stone')
box('left_shoulder', 'left_arm', [7.5, -16, -3.5], [7, 8, 7], 'copper')
box('left_upper_arm', 'left_arm', [9, -8, -2], [4, 6, 4], 'stone')
box('left_elbow', 'left_arm', [9.5, -2, -1.5], [3, 2, 3], 'joint')
box('left_forearm', 'left_forearm', [8, -1, -3], [6, 7, 6], 'stone')
box('left_hand', 'left_forearm', [9, 5, -2], [4, 3, 4], 'stone')
box('left_thigh', 'left_leg', [2, 1, -3], [6, 12, 6], 'stone')
box('left_knee', 'left_leg', [2.5, 12, -2.5], [5, 2, 5], 'joint')
box('left_greave', 'left_leg', [1.5, 14, -3.5], [7, 8, 7], 'stone')
box('left_greave_band', 'left_leg', [1.5, 14, -3.5], [7, 3, 7], 'copper')
box('left_boot', 'left_leg', [1, 22, -4.5], [8, 2, 8], 'stone')
box('lantern_handle', 'lantern', [-11.5, 7, -0.5], [1, 3, 1], 'joint')
box('lantern_cap', 'lantern', [-13.5, 10, -2.5], [5, 1, 5], 'brass')
box('lantern_light', 'lantern', [-12.5, 11, -1.5], [3, 4, 3], 'eye')
box('lantern_base', 'lantern', [-13.5, 15, -2.5], [5, 1, 5], 'brass')
box('lantern_post_-11.5_-2.5', 'lantern', [-13.5, 11, -2.5], [1, 4, 1], 'joint')
box('lantern_post_-11.5_1.5', 'lantern', [-13.5, 11, 1.5], [1, 4, 1], 'joint')
box('lantern_post_-7.5_-2.5', 'lantern', [-9.5, 11, -2.5], [1, 4, 1], 'joint')
box('lantern_post_-7.5_1.5', 'lantern', [-9.5, 11, 1.5], [1, 4, 1], 'joint')
# The 90-degree tool rotation puts z faces against the fist's y faces.
# Inset the shaft thickness so its face cannot coincide with the hand bottom.
box('hammer_handle', 'hammer', [10, 0, -2], [2, 16, 2], 'wood')
box('hammer_grip', 'hammer', [9.5, 3, -2.5], [3, 4, 3], 'leather')
box('hammer_collar', 'hammer', [9.5, 2, -2.5], [3, 1, 3], 'brass')
box('hammer_head', 'hammer', [8.5, -2, -5], [5, 4, 8], 'stone')
box('hammer_striking_left', 'hammer', [8.5, -2, -6], [5, 4, 1], 'joint')
box('hammer_striking_right', 'hammer', [8.5, -2, 3], [5, 4, 1], 'joint')
box('brow_lip', 'head', [-4.5, -20.5, -4.8], [9, 1, 1], 'stone')
box('visor_inner', 'head', [-1, -20, -4.3], [2, 5, 1], 'brass')
box('core_top_lip', 'body', [-4, -13.5, -5.7], [8, 1, 1], 'stone')
box('core_lower_lip', 'body', [-4, -4.5, -5.7], [8, 1, 1], 'stone')
box('chest_plate_inner_right', 'body', [-4.5, -13.5, -5.9], [1, 9, 1], 'stone')
box('chest_plate_inner_left', 'body', [3.5, -13.5, -5.9], [1, 9, 1], 'stone')
box('rib_right', 'body', [-6, -3, -4.5], [3, 2, 2], 'stone')
box('rib_left', 'body', [3, -3, -4.5], [3, 2, 2], 'stone')
box('waist_right', 'body', [-6, -0.5, -4.3], [3, 2, 1], 'stone')
box('waist_left', 'body', [3, -0.5, -4.3], [3, 2, 1], 'stone')
box('waist_pin_right', 'body', [-5, -0.2, -4.5], [1, 1, 1], 'brass')
box('waist_pin_left', 'body', [4, -0.2, -4.5], [1, 1, 1], 'brass')
box('belt_clasp_top', 'body', [-2, -2, -5.2], [4, 1, 1], 'stone')
box('cloth_tail', 'tabard', [-1.5, 15, -4.5], [3, 1, 1], 'brass')
box('right_upper_arm_plate', 'right_arm', [-13, -7, -2.5], [4, 4, 1], 'stone')
box('right_gauntlet_ridge', 'right_forearm', [-14, -1, -3.5], [6, 2, 1], 'stone')
box('right_shin_panel', 'right_leg', [-7.5, 17.5, -3.7], [5, 4, 1], 'stone')
box('right_ankle_trim', 'right_leg', [-8.5, 21, -3.6], [7, 1, 1], 'joint')
box('right_toe_cap', 'right_leg', [-9, 22, -4.7], [8, 1, 1], 'stone')
box('left_upper_arm_plate', 'left_arm', [9, -7, -2.5], [4, 4, 1], 'stone')
box('left_gauntlet_ridge', 'left_forearm', [8, -1, -3.5], [6, 2, 1], 'stone')
box('left_thumb', 'left_forearm', [12.5, 4, -2.7], [1, 3, 2], 'stone')
box('left_finger_0', 'left_forearm', [9.25, 6, -2.4], [1, 2, 1], 'joint')
box('left_finger_1', 'left_forearm', [10.5, 6, -2.4], [1, 2, 1], 'joint')
box('left_finger_2', 'left_forearm', [11.75, 6, -2.4], [1, 2, 1], 'joint')
box('left_shin_panel', 'left_leg', [2.5, 17.5, -3.7], [5, 4, 1], 'stone')
box('left_ankle_trim', 'left_leg', [1.5, 21, -3.6], [7, 1, 1], 'joint')
box('left_toe_cap', 'left_leg', [1, 22, -4.7], [8, 1, 1], 'stone')

box('cape_cloth', 'cape', [-6, -13, 6], [12, 28, 1], 'cloth')
box('cape_clasp_right', 'body', [-4.8, -13.5, 3.5], [2, 2, 4], 'brass')
box('cape_clasp_left', 'body', [2.8, -13.5, 3.5], [2, 2, 4], 'brass')

# Seat the shoulder assemblies into visible axles rather than leaving air at the torso.
for g in groups:
    if g['name'] in ('right_arm','right_forearm','lantern'): g['pivot'][0] += 1
    if g['name'] in ('left_arm','left_forearm','hammer'): g['pivot'][0] -= 1
for c in cubes:
    if c['part'] in ('right_arm','right_forearm','lantern'): c['pos'][0] += 1
    if c['part'] in ('left_arm','left_forearm','hammer'): c['pos'][0] -= 1
    if c['name']=='hip': c['pos']=[-3,1.2,-2.4]; c['size']=[6,2,5]
    # Adjacent greave materials meet at a seam, with no coincident overlay faces.
    if c['name'] in ('right_greave','left_greave'): c['pos'][1]=17; c['size'][1]=5
box('right_shoulder_axle','body',[-10.5,-11.5,-1.5],[5,3,3],'joint')
box('left_shoulder_axle','body',[5.5,-11.5,-1.5],[5,3,3],'joint')

shells = {'chest_socket': 0.02, 'chest_left': 0.04, 'chest_right': 0.04, 'belt': 0.04, 'buckle': 0.06, 'crown': 0.04, 'vertical_visor': 0.04, 'cheek_right': 0.06, 'cheek_left': 0.06, 'right_greave_band': 0.04, 'left_greave_band': 0.04, 'hammer_grip': 0.04, 'hammer_collar': 0.06, 'hammer_striking_left': 0.04, 'hammer_striking_right': 0.04, 'brow_lip': 0.03, 'visor_inner': 0.02, 'helmet_left_seam': 0.01, 'helmet_right_seam': 0.015, 'core_top_lip': 0.03, 'core_lower_lip': 0.05, 'chest_plate_inner_right': 0.01, 'chest_plate_inner_left': 0.015, 'rib_right': 0.015, 'rib_left': 0.025, 'waist_right': 0.035, 'waist_left': 0.045, 'waist_pin_right': 0.005, 'waist_pin_left': 0.008, 'belt_clasp_top': 0.02, 'cloth_tail': 0.01, 'right_shoulder_socket': 0.01, 'right_upper_arm_plate': 0.02, 'right_gauntlet_ridge': 0.03, 'right_thumb': 0.02, 'right_finger_0': 0.005, 'right_finger_1': 0.01, 'right_finger_2': 0.015, 'right_shin_panel': 0.01, 'right_ankle_trim': 0.03, 'right_toe_cap': 0.02, 'left_shoulder_socket': 0.01, 'left_upper_arm_plate': 0.02, 'left_gauntlet_ridge': 0.03, 'left_thumb': 0.02, 'left_finger_0': 0.005, 'left_finger_1': 0.01, 'left_finger_2': 0.015, 'left_shin_panel': 0.01, 'left_ankle_trim': 0.03, 'left_toe_cap': 0.02}
for c in cubes:
    c['inflate'] = -.04 if c['name']=='hammer_handle' else shells.get(c['name'], 0)
    if c['name'] in ('right_greave_band','left_greave_band'): c['inflate']=0

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
        if c['name']=='cape_cloth' and face in ('north','south'):
            # Circuit embroidery over the existing weathered red fabric.
            panel=image.crop((fx,fy,fx+fw,fy+fh))
            ink=ImageDraw.Draw(panel)
            gold=(174,116,60,255); light=(211,159,83,255); dark=(88,37,29,255)
            ink.rectangle((2,2,fw-3,fh-3),outline=gold,width=1)
            # Deliberately square PCB routes, paired around the central chip.
            for mirrored in (False,True):
                def route(points):
                    return [(fw-1-x if mirrored else x,y) for x,y in points]
                for points in ([(9,12),(9,30),(19,40),(19,47)],
                               [(15,18),(15,28),(22,35),(22,47)],
                               [(6,43),(12,43),(17,48)],
                               [(7,67),(12,67),(18,61)],
                               [(10,96),(10,84),(19,75),(19,63)],
                               [(17,103),(17,86),(22,81),(22,63)]):
                    pts=route(points)
                    ink.line(pts,fill=gold,width=2)
                    x,y=pts[0]
                    ink.rectangle((x-2,y-2,x+2,y+2),fill=light)
                    ink.point((x,y),fill=dark)
            ink.rectangle((17,48,30,62),fill=dark,outline=light,width=2)
            ink.rectangle((21,52,26,58),fill=gold)
            image.paste(panel,(fx,fy))
            draw=ImageDraw.Draw(image)
        elif mat=='cloth' and face in ('north','south'):
            # Stepped, worn gold embroidery copied in spirit from the reference tabard.
            target=(fx,fy,fw,fh)
            panel=Image.new('RGBA',(12,52))
            draw=ImageDraw.Draw(panel)
            fx,fy,fw,fh=0,0,12,52
            dark=(100,43,32,255); gold=(174,116,60,255); light=(193,143,78,255)
            draw.rectangle((fx,fy,fx+fw-1,fy+fh-1),fill=dark)
            draw.line((fx+1,fy,fx+1,fy+fh-1),fill=gold,width=1)
            draw.line((fx+fw-2,fy,fx+fw-2,fy+fh-1),fill=gold,width=1)
            for k,(xa,ya,xb,yb) in enumerate([(4,5,7,11),(5,11,8,17),(3,17,6,23),(5,23,8,29),(4,29,7,36),(3,36,6,42),(5,42,8,47)]):
                draw.rectangle((fx+xa,fy+ya,fx+xb,fy+min(yb,fh-2)),fill=gold if k%2 else light)
            draw.line((fx+2,fy+fh-3,fx+fw-3,fy+fh-3),fill=gold,width=2)
            tx,ty,tw,th=target
            image.paste(panel.resize((tw,th),Image.Resampling.NEAREST),(tx,ty))
            draw=ImageDraw.Draw(image)
image.save(TEX/'rectifier.png')
image.save(ART/'rectifier.png')
glow = Image.new('RGBA', image.size, (0,0,0,0))
for c in cubes:
    if c['material'] in ('eye', 'cyan'):
        for u,v,w,h in c['faces'].values():
            for y in range(v*DENSITY,(v+h)*DENSITY):
                for x in range(u*DENSITY,(u+w)*DENSITY):
                    pixel=image.getpixel((x,y))
                    glow.putpixel((x,y),pixel)
glow.save(TEX/'rectifier_glow.png')
glow.save(ART/'rectifier_glow.png')
spec = dict(textureWidth=256,textureHeight=256,groups=groups,cubes=cubes)
(ART/'rectifier.geometry.json').write_text(json.dumps(spec,indent=2)+'\n')

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

/** Generated by tools/build_rectifier_assets.py; edit the source, then regenerate. */
public final class RectifierMesh {
    private RectifierMesh() {}
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
''' + '\n'.join(lines) + '''
        return LayerDefinition.create(mesh, 256, 256);
    }
}
'''
(JAVA/'RectifierMesh.java').write_text(java)

def uid(name): return str(uuid.uuid5(uuid.NAMESPACE_URL,'seedcity/rectifier/'+name))
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
data='data:image/png;base64,'+base64.b64encode((ART/'rectifier.png').read_bytes()).decode()
bb=dict(meta={'format_version':'4.10','model_format':'free','box_uv':False},
    name='Seed City Rectifier',model_identifier='seedcity:rectifier',
    resolution={'width':256,'height':256},elements=elements,
    outliner=[outline(g) for g in groups if not g['parent']],
    textures=[{'name':'rectifier.png','uuid':uid('texture'),'id':'0','source':data,
               'width':1024,'height':1024,'uv_width':256,'uv_height':256}])
(ART/'rectifier.bbmodel').write_text(json.dumps(bb,indent=2)+'\n')
preview = (ART/'preview.template.html').read_text(encoding='utf-8').split('<script>')[0]
vendor = ROOT/'tools/vendor/three-0.180.0'
core_uri = 'data:text/javascript;base64,'+base64.b64encode((vendor/'three.core.js').read_bytes()).decode()
module = (vendor/'three.module.js').read_text(encoding='utf-8').replace('./three.core.js',core_uri)
module_uri = 'data:text/javascript;base64,'+base64.b64encode(module.encode()).decode()
glow_uri = 'data:image/png;base64,'+base64.b64encode((ART/'rectifier_glow.png').read_bytes()).decode()
runtime = (ART/'preview.js').read_text(encoding='utf-8').replace('__GEOMETRY__',json.dumps(spec)).replace('__TEXTURE__',data).replace('__GLOW__',glow_uri).replace('__THREE__',module_uri)
preview += '<script type="module">'+runtime+'</script></html>'
(ART/'rectifier-preview.html').write_text(preview,encoding='utf-8')
print(f'Rectifier: {len(cubes)} cuboids, {len(groups)} parts, atlas used through row {cursor_y+row_h}/256')
