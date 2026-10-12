"""Generate the four gold achievement launcher vectors and true silhouette theme layers."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'app/src/main/res'
HEADER='<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">'
def path(d,fill='#FF694411',stroke=None,width=0,evenodd=False):
    return '<path android:pathData="'+d+'" android:fillColor="'+fill+'"'+(' android:fillType="evenOdd"' if evenodd else '')+(' android:strokeColor="'+stroke+'" android:strokeWidth="'+str(width)+'" android:strokeLineCap="round" android:strokeLineJoin="round"' if stroke else '')+'/>'
def circle(x,y,r,fill):return path(f'M{x-r},{y} a{r},{r} 0 1,1 {2*r},0 a{r},{r} 0 1,1 {-2*r},0',fill)
# Larger silhouettes with fewer details survive 32/48dp and theme tinting.
crown='M35,48 L43,56 L47,38 L54,48 L61,38 L65,56 L73,48 L70,68 L38,68 Z'
flame='M55,33 C68,44 75,58 65,69 C58,77 42,75 37,64 C33,55 41,47 45,43 C44,52 49,55 51,51 C55,46 55,40 55,33 Z'
inner='M55,51 C62,58 62,66 55,68 C48,67 48,59 55,51 Z'
hourglass='M40,38 L68,38 L68,42 L58,54 L68,66 L68,70 L40,70 L40,66 L50,54 L40,42 Z M46,42 L54,51 L62,42 Z M46,66 L62,66 L54,57 Z'
target='M54,36 a18,18 0 1,1 0,36 a18,18 0 1,1 0,-36 M54,41 a13,13 0 1,0 0,26 a13,13 0 1,0 0,-26 M54,47 a7,7 0 1,1 0,14 a7,7 0 1,1 0,-14'
shapes={'marathon':path(hourglass,evenodd=True),'week':path(flame)+path(inner,'#FFFFD875'),'c100':path(target,evenodd=True)+path('M75,35 L61,48','@android:color/transparent','#FF694411',4)+path('M59,44 L59,51 L66,50'),'crown':path(crown)+path('M41,65 L67,65','@android:color/transparent','#FFFFE6A8',2)}
mono={'marathon':path(hourglass,'#FFFFFFFF',evenodd=True),'week':path(flame+' '+inner,'#FFFFFFFF',evenodd=True),'c100':path(target,'#FFFFFFFF',evenodd=True)+path('M75,35 L61,48','@android:color/transparent','#FFFFFFFF',4)+path('M59,44 L59,51 L66,50','#FFFFFFFF'),'crown':path(crown,'#FFFFFFFF')}
common='<path android:pathData="M24.5,54 a29.5,29.5 0 1,1 59,0 a29.5,29.5 0 1,1 -59,0"><aapt:attr name="android:fillColor"><gradient android:type="linear" android:startX="33" android:startY="25" android:endX="75" android:endY="84"><item android:offset="0" android:color="#FFFFF0BC"/><item android:offset="0.5" android:color="#FFFFD367"/><item android:offset="1" android:color="#FFDEA128"/></gradient></aapt:attr></path>\n<path android:pathData="M24.5,54 a29.5,29.5 0 1,1 59,0 a29.5,29.5 0 1,1 -59,0" android:fillColor="@android:color/transparent" android:strokeColor="#FFF0BC4C" android:strokeWidth="1.5"/>\n<path android:pathData="M28,54 a26,26 0 1,1 52,0 a26,26 0 1,1 -52,0" android:fillColor="@android:color/transparent" android:strokeColor="#FFB97D18" android:strokeWidth="1"/>\n<path android:pathData="M31,44 A26,26 0 0,1 65,30" android:fillColor="@android:color/transparent" android:strokeColor="#FFFFF7D9" android:strokeWidth="2" android:strokeLineCap="round"/>\n'
for name,body in shapes.items():
    (ROOT/'drawable'/f'ic_{name}_foreground.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n'+HEADER+'\n'+common+body+'\n</vector>\n',encoding='utf-8')
    (ROOT/'drawable'/f'ic_{name}_monochrome.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n'+HEADER+'\n'+mono[name]+'\n</vector>\n',encoding='utf-8')
    mip='crown' if name=='crown' else name
    (ROOT/'mipmap-anydpi-v26'/f'ic_launcher_{mip}.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n <background android:drawable="@drawable/bg_achievement_icon"/>\n <foreground android:drawable="@drawable/ic_'+name+'_foreground"/>\n <monochrome android:drawable="@drawable/ic_'+name+'_monochrome"/>\n</adaptive-icon>\n',encoding='utf-8')
background=HEADER+path('M0,0 H108 V108 H0 Z','#FF172423')+'</vector>'
(ROOT/'drawable'/'bg_achievement_icon.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n'+background+'\n',encoding='utf-8')
print('Generated four adaptive gold icons and four dedicated theme silhouettes.')
