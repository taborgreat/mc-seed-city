import * as THREE from '__THREE__';
const standaloneDocument='<!doctype html>\n'+document.documentElement.outerHTML;
const spec=__GEOMETRY__;
const canvas=document.getElementById('view');
const renderer=new THREE.WebGLRenderer({canvas,antialias:true,alpha:true,preserveDrawingBuffer:true});
renderer.setPixelRatio(Math.min(devicePixelRatio,2));
renderer.outputColorSpace=THREE.SRGBColorSpace;
const scene=new THREE.Scene(),camera=new THREE.OrthographicCamera(-20,20,17,-17,.1,200);
camera.up.set(0,-1,0);
const texture=new THREE.TextureLoader().load('__TEXTURE__');
texture.colorSpace=THREE.SRGBColorSpace;texture.magFilter=THREE.NearestFilter;texture.minFilter=THREE.NearestFilter;
const emissive=new THREE.TextureLoader().load('__GLOW__');
emissive.colorSpace=THREE.SRGBColorSpace;emissive.magFilter=THREE.NearestFilter;emissive.minFilter=THREE.NearestFilter;
const material=new THREE.MeshLambertMaterial({map:texture,emissiveMap:emissive,emissive:0xffffff,emissiveIntensity:1,side:THREE.DoubleSide});
const ambient=new THREE.AmbientLight(0xc7e4ec,1.6);scene.add(ambient);
const sun=new THREE.DirectionalLight(0xffeccf,2.4);sun.position.set(-15,-30,-25);scene.add(sun);
const fill=new THREE.DirectionalLight(0x8dc5d2,.45);fill.position.set(20,0,15);scene.add(fill);
const groups={},specGroups=Object.fromEntries(spec.groups.map(g=>[g.name,g]));
for(const g of spec.groups){const obj=new THREE.Group();obj.name=g.name;obj.rotation.order='ZYX';const parent=g.parent?specGroups[g.parent].pivot:[0,0,0];obj.position.set(...g.pivot.map((v,i)=>v-parent[i]));(g.parent?groups[g.parent]:scene).add(obj);groups[g.name]=obj}
const faceDefs={north:{n:[0,0,-1],v:[[0,0,0],[1,0,0],[1,1,0],[0,1,0]]},south:{n:[0,0,1],v:[[1,0,1],[0,0,1],[0,1,1],[1,1,1]]},east:{n:[-1,0,0],v:[[0,0,1],[0,0,0],[0,1,0],[0,1,1]]},west:{n:[1,0,0],v:[[1,0,0],[1,0,1],[1,1,1],[1,1,0]]},up:{n:[0,-1,0],v:[[0,0,1],[1,0,1],[1,0,0],[0,0,0]]},down:{n:[0,1,0],v:[[0,1,0],[1,1,0],[1,1,1],[0,1,1]]}};
for(const c of spec.cubes){const pos=[],normals=[],uv=[];for(const[f,d]of Object.entries(faceDefs)){let[u,v,w,h]=c.faces[f];let tex=[[u,v],[u+w,v],[u+w,v+h],[u,v+h]];if(f==='down')tex=tex.map(([x,y])=>[x,2*v+h-y]);for(const j of [0,2,1,0,3,2]){pos.push(...d.v[j].map((x,i)=>c.pos[i]-(c.inflate||0)+x*(c.size[i]+2*(c.inflate||0))-specGroups[c.part].pivot[i]));normals.push(...d.n);uv.push(tex[j][0]/256,1-tex[j][1]/256)}}const geom=new THREE.BufferGeometry();geom.setAttribute('position',new THREE.Float32BufferAttribute(pos,3));geom.setAttribute('normal',new THREE.Float32BufferAttribute(normals,3));geom.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));const mesh=new THREE.Mesh(geom,material);mesh.name=c.name;groups[c.part].add(mesh)}
const grid=new THREE.GridHelper(96,24,0x49666c,0x294149);grid.position.y=25;scene.add(grid);
const shadowCanvas=document.createElement('canvas');shadowCanvas.width=128;shadowCanvas.height=128;const sh=shadowCanvas.getContext('2d'),gradient=sh.createRadialGradient(64,64,0,64,64,64);gradient.addColorStop(0,'#00000070');gradient.addColorStop(1,'#00000000');sh.fillStyle=gradient;sh.fillRect(0,0,128,128);const shadow=new THREE.Mesh(new THREE.PlaneGeometry(25,17),new THREE.MeshBasicMaterial({map:new THREE.CanvasTexture(shadowCanvas),transparent:true,depthWrite:false,side:THREE.DoubleSide}));shadow.rotation.x=Math.PI/2;shadow.position.set(0,24.9,0);scene.add(shadow);
groups.body.scale.setScalar(spec.modelScale);
const helmetLight=new THREE.PointLight(0xffd28a,0,65,2);helmetLight.position.set(0,-9,-6);groups.head.add(helmetLight);
const scaleMarker=new THREE.Group();scene.add(scaleMarker);
const stone=new THREE.MeshLambertMaterial({color:0x718082,transparent:true,opacity:.65});
for(const [x,y,sx,sy] of [[-13,8,2,32],[13,8,2,32],[0,-9,28,2]]){const m=new THREE.Mesh(new THREE.BoxGeometry(sx,sy,8),stone);m.position.set(x,y,8);scaleMarker.add(m);}
const play=document.getElementById('playing'),spin=document.getElementById('turntable'),night=document.getElementById('night');
let yaw=-.45,pitch=.15,zoom=1,pose='carry',time=0,previous=0,drag=null;
document.querySelectorAll('[data-pose]').forEach(b=>b.onclick=()=>{pose=b.dataset.pose;document.querySelectorAll('[data-pose]').forEach(x=>x.classList.toggle('active',x===b));document.getElementById('poseLabel').textContent={idle:'Resting · lamp on',carry:'Hauling supplies',mine:'Mining · real equipped tool'}[pose]});
document.querySelectorAll('[data-angle]').forEach(b=>b.onclick=()=>{yaw=+b.dataset.angle;pitch=.15;spin.checked=false});
canvas.onpointerdown=e=>{drag=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId)};
canvas.onpointerup=()=>drag=null;canvas.onpointercancel=()=>drag=null;
canvas.onpointermove=e=>{if(drag){yaw-=(e.clientX-drag[0])*.009;pitch=Math.max(-.65,Math.min(.85,pitch+(e.clientY-drag[1])*.006));drag=[e.clientX,e.clientY]}};
canvas.addEventListener('wheel',e=>{e.preventDefault();zoom=Math.max(.65,Math.min(1.5,zoom-e.deltaY*.0007))},{passive:false});
function save(blob,name){const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
document.getElementById('saveViewer').onclick=()=>save(new Blob([standaloneDocument],{type:'text/html'}),'Seed-City-Collector.html');
document.getElementById('saveImage').onclick=()=>canvas.toBlob(b=>{if(b)save(b,'Collector-'+pose+'-preview.png')});

const itemTextures={};
itemTextures.pickaxe=new THREE.TextureLoader().load('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQBAMAAADt3eJSAAAAHlBMVEUAAAD////Y2NjBwcGJZydoTh5ERERJNhUoHgsYGBhl7cYfAAAAAXRSTlMAQObYZgAAAEVJREFUeNpjwArS0hLANJuQsVopmJU509gDKllkCaHZI4wmgBklDZMnQAQYgAywAMOkCRABhokTIAIMnFABqBYoowPNZgA3CQ9j/SFa9AAAAABJRU5ErkJggg==');itemTextures.pickaxe.magFilter=THREE.NearestFilter;itemTextures.pickaxe.colorSpace=THREE.SRGBColorSpace;
itemTextures.axe=new THREE.TextureLoader().load('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAeUlEQVQ4y2NgoAdwcXH5D8Jka4YBkg2BaT548OD/GzdukGYAsmYY9jQT/Z/hJ0ecIRISEmADQDaDNE+bNg1Ma8hxE+8KkCEgDLIZ2RCQGNGGwJwNshnmFaINAGnuTFeHOxvmIpJtJjnuKdaM7OwhYjMIgDSSrZkSAABx5YE7nrv/FQAAAABJRU5ErkJggg==');itemTextures.axe.magFilter=THREE.NearestFilter;itemTextures.axe.colorSpace=THREE.SRGBColorSpace;
const itemMaterial=new THREE.MeshLambertMaterial({map:itemTextures.pickaxe,transparent:true,alphaTest:.5,side:THREE.DoubleSide});
const heldTool=new THREE.Mesh(new THREE.PlaneGeometry(13,13),itemMaterial);groups.right_arm.add(heldTool);heldTool.position.set(-.5,10,-3);heldTool.rotation.set(0,Math.PI/2,-.5);
document.getElementById('pickaxe').onclick=()=>itemMaterial.map=itemTextures.pickaxe;
document.getElementById('axe').onclick=()=>itemMaterial.map=itemTextures.axe;
let lampPitch=0,lampRoll=0,lampPV=0,lampRV=0,lampPhase=0,lastVelocity=0,lastAttack=0;
let gaitPos=0,gaitSpeed=0,walkVelocity=0,gaitAccumulator=0;
function frame(now){const dt=Math.min((now-previous)/1000,.05);previous=now;if(play.checked)time+=dt*20;if(spin.checked&&!drag)yaw+=dt*.3;
 const w=canvas.clientWidth,h=canvas.clientHeight;renderer.setSize(w,h,false);const half=Math.max(23,26*h/w)/zoom;camera.left=-half*w/h;camera.right=half*w/h;camera.top=half;camera.bottom=-half;camera.updateProjectionMatrix();camera.position.set(Math.sin(yaw)*70,8-Math.sin(pitch)*70,-Math.cos(yaw)*70);camera.lookAt(0,8,0);
 const moving=pose==='carry';
 if(play.checked){gaitAccumulator+=dt;while(gaitAccumulator>=.05){
   walkVelocity=walkVelocity*.546+(moving?.098:0);
   gaitSpeed+=(Math.min(walkVelocity*4,1)-gaitSpeed)*.4;
   gaitPos+=gaitSpeed;gaitAccumulator-=.05;
   lampPhase+=walkVelocity*7;
   const attack=pose==='mine'?(time%6)/6:0, impulse=attack>0&&(lastAttack===0||attack<lastAttack)?.035:0;
   lampPV+=-lampPitch*.18-lampPV*.22+(walkVelocity-lastVelocity)*.7+Math.sin(lampPhase)*Math.min(walkVelocity,.2)*.10+impulse;
   lampRV+=-lampRoll*.21-lampRV*.25+Math.sin(lampPhase*.5)*Math.min(walkVelocity,.2)*.04;
   lampPitch=Math.max(-.22,Math.min(.22,lampPitch+lampPV));lampRoll=Math.max(-.07,Math.min(.07,lampRoll+lampRV));
   lastVelocity=walkVelocity;lastAttack=attack;
 }}
 const gait=Math.cos(gaitPos*.6662)*gaitSpeed;
 groups.lantern.rotation.set(lampPitch,0,lampRoll);
 groups.body.position.y=24;groups.torso.rotation.y=0;
 groups.right_leg.rotation.x=gait*1.4;groups.left_leg.rotation.x=-gait*1.4;
 groups.right_arm.rotation.set(-gait,0,0);groups.left_arm.rotation.set(gait,0,0);
 groups.head.rotation.y=document.getElementById('looking').checked?Math.sin(time*.035)*.45:0;
 groups.head.rotation.x=pose==='mine'?.25:0;
 if(pose==='mine'){
   const p=(time%6)/6,twist=Math.sin(Math.sqrt(p)*Math.PI*2)*.2;
   groups.torso.rotation.y=twist;groups.head.rotation.y-=twist;groups.left_arm.rotation.x+=twist;
   groups.right_arm.rotation.x-=Math.sin((1-Math.pow(1-p,4))*Math.PI)*1.2+Math.sin(p*Math.PI)*(.7-groups.head.rotation.x)*.75;
   groups.right_arm.rotation.y=twist*2;groups.right_arm.rotation.z=-Math.sin(p*Math.PI)*.4;
 }
 grid.position.y=24;shadow.position.y=23.99;shadow.scale.set(.6,.6,.6);
 helmetLight.intensity=night.checked?500:0;
 ambient.intensity=night.checked?.10:1.6;sun.intensity=night.checked?.16:2.4;fill.intensity=night.checked?.10:.45;
 scaleMarker.visible=document.getElementById('scaleRef').checked;renderer.render(scene,camera);requestAnimationFrame(frame)}
requestAnimationFrame(frame);
