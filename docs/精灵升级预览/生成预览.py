from pathlib import Path
import math, json, subprocess, shutil
from PIL import Image, ImageDraw, ImageFont

OUT=Path(__file__).resolve().parent
NODE='C:/Users/MR/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe'
SHARP='C:/Users/MR/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp'
NAMES=['电火花','电球','雷云精灵','风暴之灵','雷霆之王']
COLORS=[('#FFEAB1','#E8AC37'),('#D3C5FF','#8F70D6'),('#B5E8FA','#4DA3D1'),('#B6E3D4','#48A08C'),('#C8C2FA','#7964C7')]
EFFECTS=['火星闪动','充能波纹','云间闪电','风暴旋流','王冠星辉']
SIZES=[.65,.80,.93,1.04,1.12]

def star(x,y,r,color,opacity=1):
    return f'<path d="M0 {-r} Q{r*.24} {-r*.24} {r} 0 Q{r*.24} {r*.24} 0 {r} Q{-r*.24} {r*.24} {-r} 0 Q{-r*.24} {-r*.24} 0 {-r}Z" transform="translate({x:.2f} {y:.2f})" fill="{color}" opacity="{opacity:.3f}"/>'

def effects(stage,t,sleepy,happy):
    back='';front='';power=.15 if sleepy else (1.2 if happy else .8)
    if stage==0:
        for j in range(5):
            k=(t*.32+j*.21)%1
            x=120+math.sin(j*2.3+k*2)*48;y=135-k*80
            front+=star(x,y,2.4+math.sin(k*math.pi)*2,'#DDA73B',power*math.sin(k*math.pi))
    elif stage==1:
        for j in range(2):
            k=(t*.38+j*.5)%1;r=55+25*k
            back+=f'<ellipse cx="120" cy="122" rx="{r}" ry="{r*.84}" fill="none" stroke="#A28ACE" stroke-width="{2.6-k}" opacity="{power*(1-k)*.55}"/>'
        a=t*1.8;front+=star(120+math.cos(a)*65,120+math.sin(a)*54,5,'#B394EF',power)
    elif stage==2:
        for j in range(3):
            k=(t*.7+j*.33)%1;x=85+j*34;y=161+k*29
            front+=f'<path d="M0 0 L-5 10 L1 9 L-3 19 L10 5 L4 6 L7 0Z" transform="translate({x} {y}) scale(.65)" fill="#E1B552" opacity="{power*math.sin(k*math.pi):.3f}"/>'
        back+=f'<path d="M65 109 Q53 {90+math.sin(t*2)*6} 70 79 M164 79 Q190 {91+math.sin(t*2)*6} 183 112" fill="none" stroke="#89C9E4" stroke-width="3" opacity="{power*.6}" stroke-linecap="round"/>'
    elif stage==3:
        for j in range(2):
            tilt=math.sin(t*1.1+j)*8-12+j*24
            path=f'<ellipse cx="120" cy="{124+j*23}" rx="{85-j*10}" ry="{19-j*2}" fill="none" stroke="{["#73BCA5","#419A88"][j]}" stroke-width="{4-j}" stroke-dasharray="98 34 32 180" stroke-dashoffset="{-t*65-j*85}" opacity="{power*.78}" transform="rotate({tilt} 120 130)"/>'
            if j==0:back+=path
            else:front+=path
        a=t*2.4;front+=f'<circle cx="{120+math.cos(a)*81}" cy="{128+math.sin(a)*21}" r="3" fill="#98DDCA" opacity="{power}"/>'
    else:
        for j in range(7):
            a=t*.33+j*math.tau/7;r=80+5*math.sin(t+j)
            x=120+math.cos(a)*r;y=118+math.sin(a)*r*.92
            back+=star(x,y,4+2*math.sin(t*1.4+j)**2,'#D7B460',power*.8)
        back+=f'<ellipse cx="120" cy="194" rx="{58+math.sin(t*1.6)*4}" ry="9" fill="none" stroke="#D5B45D" stroke-width="2" opacity="{power*.32}"/>'
    return back,front

def mascot(stage,t=0,action='idle',action_time=0):
    light,base=COLORS[stage]
    phase=t*math.pi/2+stage*.5
    dy=math.sin(phase)*3
    sy=1+math.sin(phase)*.014;sx=1-math.sin(phase)*.008;angle=math.sin(phase*.7)*1.3
    happy=False;sleepy=action=='sleep';hearts=''
    if action=='poke':
        q=action_time
        happy=True
        if q<.16:
            k=math.sin(q/.16*math.pi/2);sx=1+.12*k;sy=1-.12*k;dy+=5*k
        else:
            r=q-.16;bounce=math.sin(r*13)*math.exp(-r*4)
            sy+=.10*bounce;sx-=.065*bounce;dy-=8*math.sin(min(r/.7,1)*math.pi);angle+=6*math.sin(r*8)*math.exp(-r*3)
        for j in range(3):
            k=(q-.18-j*.13)/.8
            if 0<k<1:
                x=94+j*24+math.sin(k*3+j)*8;y=91-k*45
                hearts+=f'<path d="M0 4 C-12 -4 -8 -14 0 -7 C8 -14 12 -4 0 4Z" fill="#D9748D" opacity="{math.sin(k*math.pi):.3f}" transform="translate({x} {y}) scale(.7)"/>'
    if sleepy:dy=2+math.sin(t*1.1)*1.5;sy=1+math.sin(t*1.1)*.018
    blink=abs((t+stage*.23)%3.7-3.2)<.07
    # Rounded, closed silhouettes; all identities share face proportions and lighting.
    paths=[
      'M120 63 C130 85 157 87 161 114 C164 140 150 160 121 161 C92 162 78 145 79 124 C80 103 102 96 106 81 C110 70 115 64 120 63Z',
      'M120 70 C151 70 172 91 172 121 C172 153 152 171 120 171 C88 171 68 153 68 122 C68 90 89 70 120 70Z',
      'M73 100 C74 80 93 70 111 77 C121 59 149 65 155 87 C178 87 190 103 185 124 C185 150 164 164 139 163 L97 163 C70 164 54 149 56 129 C54 114 61 104 73 100Z',
      'M75 91 C73 67 99 54 117 64 C135 51 161 65 163 87 C185 103 174 126 162 138 C152 149 151 168 163 174 C144 185 122 174 118 163 C105 157 87 160 77 147 C56 137 56 108 75 91Z',
      'M76 78 C65 52 77 42 96 68 C111 63 128 63 144 68 C162 42 176 52 164 79 C181 95 185 119 176 142 C172 156 169 171 154 179 Q140 183 136 173 Q120 181 104 173 Q99 183 85 178 C72 170 69 154 64 143 C56 118 60 96 76 78Z'
    ]
    decor=''
    if stage==1:
        decor='<path d="M104 73 Q109 54 122 62 Q133 65 129 75" fill="#C1ACF5"/><path d="M79 140 Q64 124 65 143 Q66 153 82 152 M161 140 Q176 124 175 143 Q174 153 158 152" fill="#A689DB"/>'
    if stage==2:
        decor='<path d="M115 151 L109 172 L120 169 L114 188 L136 160 L124 162 L128 151Z" fill="#ECC45D"/>'
    if stage==3:
        decor='<path d="M68 136 Q112 154 169 130 Q157 153 125 154 Q99 155 76 147Z" fill="#2D8173"/><path d="M145 146 Q173 155 193 137 Q188 165 155 164Z" fill="#73BFA6"/><path d="M97 82 Q119 65 144 82" fill="none" stroke="#D5EFE4" stroke-width="5" stroke-linecap="round"/>'
    if stage==4:
        decor='<path d="M102 69 Q99 58 97 50 L111 56 L120 41 L130 56 L144 50 L139 69Z" fill="#E7C46C" stroke="#CBA454" stroke-width="2" stroke-linejoin="round"/><circle cx="120" cy="59" r="3" fill="#FFF5CF"/>'
    wings=''
    if stage==4:
        flap=math.sin(t*1.8)*7 if not sleepy else 0
        wings=f'<g transform="rotate({flap-5} 77 121)"><path d="M82 113 Q56 76 27 83 Q37 105 47 111 Q30 107 29 118 Q47 143 80 142Z" fill="#C0B0ED" stroke="#9B83D0" stroke-width="1.5"/><path d="M43 100 Q61 111 77 126 M42 120 Q61 127 77 131" fill="none" stroke="#E2D7F7" stroke-width="3" stroke-linecap="round"/></g><g transform="rotate({5-flap} 163 121)"><path d="M158 113 Q184 76 213 83 Q203 105 193 111 Q210 107 211 118 Q193 143 160 142Z" fill="#C0B0ED" stroke="#9B83D0" stroke-width="1.5"/><path d="M197 100 Q179 111 163 126 M198 120 Q179 127 163 131" fill="none" stroke="#E2D7F7" stroke-width="3" stroke-linecap="round"/></g>'
    back,front=effects(stage,t,sleepy,happy)
    eyes=''
    if sleepy or blink:
        eyes='<path d="M96 118 Q102 122 108 118 M132 118 Q138 122 144 118" fill="none" stroke="#34334D" stroke-width="4" stroke-linecap="round"/>'
    elif happy:
        eyes='<path d="M96 120 Q102 108 108 120 M132 120 Q138 108 144 120" fill="none" stroke="#34334D" stroke-width="4" stroke-linecap="round"/>'
    else:
        eyes='<ellipse cx="102" cy="118" rx="5.5" ry="8" fill="#34334D"/><ellipse cx="138" cy="118" rx="5.5" ry="8" fill="#34334D"/><circle cx="100.5" cy="115.5" r="1.8" fill="white"/><circle cx="136.5" cy="115.5" r="1.8" fill="white"/>'
    smile='M114 133 Q120 140 126 133' if happy else ('M116 134 Q120 131 124 134' if sleepy else 'M115 132 Q120 137 125 132')
    gid=f'b{stage}'
    return f'''<defs><linearGradient id="{gid}" x1=".2" y1="0" x2=".8" y2="1"><stop stop-color="{light}"/><stop offset="1" stop-color="{base}"/></linearGradient></defs>
    <ellipse cx="120" cy="224" rx="{SIZES[stage]*(42+dy*.6):.2f}" ry="6" fill="#34334D" opacity=".08"/>
    <g transform="translate(120 155) scale({SIZES[stage]}) translate(-120 -120)">{back}
    <g transform="translate(120 {120+dy}) rotate({angle}) scale({sx} {sy}) translate(-120 -120)">
    {wings}
    <path d="{paths[stage]}" fill="url(#{gid})" stroke="{base}" stroke-width="1.5" stroke-linejoin="round"/>
    <ellipse cx="96" cy="96" rx="14" ry="5" transform="rotate(-30 96 96)" fill="white" opacity=".24"/>
    {decor}<ellipse cx="91" cy="133" rx="8" ry="4" fill="#E28E94" opacity=".40"/><ellipse cx="149" cy="133" rx="8" ry="4" fill="#E28E94" opacity=".40"/>{eyes}
    <path d="{smile}" fill="none" stroke="#34334D" stroke-width="3" stroke-linecap="round"/></g>{front}{hearts}</g>'''

def row(t=0,action='idle',action_time=0,width=1200,height=270):
    parts=[]
    for i in range(5):parts.append(f'<g transform="translate({i*240} 0)">{mascot(i,t,action,action_time)}</g>')
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 1200 270"><rect width="1200" height="270" fill="#F7F4EF"/>'+''.join(parts)+'</svg>'

TMP=OUT/'渲染中间文件';TMP.mkdir(exist_ok=True)
inputs=[]
for i in range(96):
    t=i/16
    action='poke' if 2<=t<3.25 else ('sleep' if 4<=t<5.4 else 'idle')
    at=t-2 if action=='poke' else t
    s=TMP/f'{i:03d}.svg';s.write_text(row(t,action,at),encoding='utf-8');inputs.append(str(s))
manifest=TMP/'manifest.json';manifest.write_text(json.dumps(inputs),encoding='utf-8')
code='const fs=require("fs"),s=require(process.argv[1]);const p=JSON.parse(fs.readFileSync(process.argv[2],"utf8"));(async()=>{for(let i=0;i<p.length;i+=8)await Promise.all(p.slice(i,i+8).map(f=>s(f).png().toFile(f.replace(/svg$/,"png"))))})().catch(e=>{console.error(e);process.exit(1)})'
subprocess.run([NODE,'-e',code,SHARP,str(manifest)],check=True)
fnt=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',23)
bold=ImageFont.truetype('C:/Windows/Fonts/msyhbd.ttc',36)
frames=[]
for i,s in enumerate(inputs):
    frame=Image.new('RGB',(1200,430),'#F7F4EF');frame.paste(Image.open(Path(s).with_suffix('.png')),(0,45))
    d=ImageDraw.Draw(frame)
    state='摸头：身体回弹 + 各阶专属特效' if 32<=i<52 else ('困倦：慢呼吸，特效收弱' if 64<=i<87 else '五阶进化：轮廓、体型与专属特效逐级变化')
    d.text((28,16),state,font=fnt,fill='#625773')
    for j,n in enumerate(NAMES):
        label=f'Lv.{j+1}  {n}';w=d.textlength(label,font=fnt);d.text((j*240+120-w/2,307),label,font=fnt,fill='#34334D')
        w=d.textlength(EFFECTS[j],font=fnt);d.text((j*240+120-w/2,348),EFFECTS[j],font=fnt,fill='#7D7488')
    d.text((28,397),'设计动态样片 · 非 App 实机录屏',font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',17),fill='#7D7488')
    frames.append(frame)
frames[0].save(OUT/'五阶动态样片.gif',save_all=True,append_images=frames[1:],duration=[60,60,60,70]*24,loop=0,optimize=False,disposal=2)
cover=Image.new('RGB',(1200,900),'#F7F4EF');d=ImageDraw.Draw(cover)
d.text((48,38),'听剧精灵 · 看得见的五阶进化',font=bold,fill='#34334D')
d.text((48,100),'小火种 → 能量团 → 雷云 → 风暴灵 → 双翼雷王',font=fnt,fill='#7D7488')
cover.paste(frames[12].crop((0,45,1200,385)),(0,170))
d.text((48,565),'每一阶，都有自己的本领',font=bold,fill='#34334D')
for y,s in [(632,'成长差异：体型逐级增大，新增手臂、云形、旋流尾和双翼'),(680,'专属特效：火星 / 充能波纹 / 云间闪电 / 风暴旋流 / 金色星辉'),(728,'互动变化：摸头回弹时，专属特效加强；困倦时特效收弱'),(776,'小头像保留轮廓与标志结构，大图再展示完整特效')]:d.text((48,y),s,font=fnt,fill='#625773')
d.text((48,850),'视觉与动作预览；不代表已经接入 Android 或完成性能验证。',font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',18),fill='#7D7488')
cover.save(OUT/'五阶造型提案.png')
frames[38].save(OUT/'摸头关键帧.png')
(OUT/'五阶造型.svg').write_text(row(),encoding='utf-8')
# Only delete the dedicated intermediate directory made by this generator.
assert TMP.resolve().parent==OUT.resolve() and TMP.name=='渲染中间文件'
shutil.rmtree(TMP)
print(json.dumps({'frames':96,'gif':str(OUT/'五阶动态样片.gif'),'png':str(OUT/'五阶造型提案.png')},ensure_ascii=False))
