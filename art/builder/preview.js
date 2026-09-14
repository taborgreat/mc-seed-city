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
const play=document.getElementById('playing'),spin=document.getElementById('turntable'),night=document.getElementById('night');
let yaw=-.45,pitch=.15,zoom=1,pose='carry',time=0,previous=0,drag=null;
document.querySelectorAll('[data-pose]').forEach(b=>b.onclick=()=>{pose=b.dataset.pose;document.querySelectorAll('[data-pose]').forEach(x=>x.classList.toggle('active',x===b));document.getElementById('poseLabel').textContent={idle:'Hovering · waiting for a task',carry:'Flying · carrying materials',build:'Building from the blueprint',walk:'Walking cycle · optional animation'}[pose]});
document.querySelectorAll('[data-angle]').forEach(b=>b.onclick=()=>{yaw=+b.dataset.angle;pitch=.15;spin.checked=false});
canvas.onpointerdown=e=>{drag=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId)};
canvas.onpointerup=()=>drag=null;canvas.onpointercancel=()=>drag=null;
canvas.onpointermove=e=>{if(drag){yaw-=(e.clientX-drag[0])*.009;pitch=Math.max(-.65,Math.min(.85,pitch+(e.clientY-drag[1])*.006));drag=[e.clientX,e.clientY]}};
canvas.addEventListener('wheel',e=>{e.preventDefault();zoom=Math.max(.65,Math.min(1.5,zoom-e.deltaY*.0007))},{passive:false});
function save(blob,name){const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
document.getElementById('saveViewer').onclick=()=>save(new Blob([standaloneDocument],{type:'text/html'}),'Seed-City-Builder.html');
document.getElementById('saveImage').onclick=()=>canvas.toBlob(b=>{if(b)save(b,'Builder-'+pose+'-preview.png')});
function frame(now){const dt=Math.min((now-previous)/1000,.05);previous=now;if(play.checked)time+=dt*20;if(spin.checked&&!drag)yaw+=dt*.3;
 const w=canvas.clientWidth,h=canvas.clientHeight;renderer.setSize(w,h,false);let half=Math.max(16,18*h/w)/zoom;camera.left=-half*w/h;camera.right=half*w/h;camera.top=half;camera.bottom=-half;camera.updateProjectionMatrix();camera.position.set(Math.sin(yaw)*55,12-Math.sin(pitch)*55,-Math.cos(yaw)*55);camera.lookAt(1.5,12,0);
 const bob=Math.sin(time*.1),flight=pose==='carry'?1:0;
 groups.body.position.y=10+bob*.22;groups.body.rotation.x=flight*.08;
 groups.head.rotation.x=pose==='build'?.16:0;
 groups.right_arm.rotation.set(pose==='carry'?-.32:.03+bob*.035,0,.04);groups.body.rotation.y=0;groups.head.rotation.y=0;
 if(pose==='build'){const p=(time%6)/6,sweep=Math.sin(Math.sqrt(p)*Math.PI*2)*.16,stroke=Math.sin((1-Math.pow(1-p,4))*Math.PI);groups.body.rotation.y=sweep;groups.head.rotation.y=-sweep;groups.right_arm.rotation.set(-.12-stroke*1.2-Math.sin(p*Math.PI)*(.7-groups.head.rotation.x)*.75,sweep*2,.04-Math.sin(p*Math.PI)*.4);}
 groups.left_arm.rotation.set(.03+bob*.035,0,-.05);
 groups.right_leg.rotation.x=.08+flight*.22+bob*.04;groups.left_leg.rotation.x=.08+flight*.22-bob*.04;
 groups.cargo.visible=pose==='carry'||pose==='build';
 const walk=pose==='walk';grid.position.y=walk?24:25;shadow.position.y=grid.position.y-.1;
 if(walk){const stride=Math.sin(time*.32)*.55;groups.right_leg.rotation.x=stride;groups.left_leg.rotation.x=-stride;groups.body.rotation.x=0;groups.body.position.y=18-8*Math.cos(stride)-3*Math.abs(Math.sin(stride));groups.right_arm.rotation.x=-stride*.65;groups.left_arm.rotation.x=stride*.65;}
 ambient.intensity=night.checked?.10:1.6;sun.intensity=night.checked?.16:2.4;fill.intensity=night.checked?.10:.45;
 renderer.render(scene,camera);requestAnimationFrame(frame)}
requestAnimationFrame(frame);
