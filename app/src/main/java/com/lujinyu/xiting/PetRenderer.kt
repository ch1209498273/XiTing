package com.lujinyu.xiting

import android.graphics.*
import kotlin.math.*

/** One normalized vector character for the panel, bubble, gallery, widgets and posters.
 * Paths/shaders are built once; effects use recycled geometry. No bitmap dependency. */
internal class PetRenderer {
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
    private val rect=RectF()
    private val scratch=Path()
    private val body=arrayOf(
        shape("M120 63 C130 85 157 87 161 114 C164 140 150 160 121 161 C92 162 78 145 79 124 C80 103 102 96 106 81 C110 70 115 64 120 63 Z"),
        shape("M120 70 C151 70 172 91 172 121 C172 153 152 171 120 171 C88 171 68 153 68 122 C68 90 89 70 120 70 Z"),
        shape("M73 100 C74 80 93 70 111 77 C121 59 149 65 155 87 C178 87 190 103 185 124 C185 150 164 164 139 163 L97 163 C70 164 54 149 56 129 C54 114 61 104 73 100 Z"),
        shape("M75 91 C73 67 99 54 117 64 C135 51 161 65 163 87 C185 103 174 126 162 138 C152 149 151 168 163 174 C144 185 122 174 118 163 C105 157 87 160 77 147 C56 137 56 108 75 91 Z"),
        shape("M76 78 C65 52 77 42 96 68 C111 63 128 63 144 68 C162 42 176 52 164 79 C181 95 185 119 176 142 C172 156 169 171 154 179 Q140 183 136 173 Q120 181 104 173 Q99 183 85 178 C72 170 69 154 64 143 C56 118 60 96 76 78 Z")
    )
    private val tuft=shape("M104 73 Q109 54 122 62 Q133 65 129 75 Z")
    private val hands=shape("M79 140 Q64 124 65 143 Q66 153 82 152 Z M161 140 Q176 124 175 143 Q174 153 158 152 Z")
    private val bolt=shape("M0 0 L-5 10 L1 9 L-3 19 L10 5 L4 6 L7 0 Z")
    private val mainBolt=shape("M115 151 L109 172 L120 169 L114 188 L136 160 L124 162 L128 151 Z")
    private val band=shape("M68 136 Q112 154 169 130 Q157 153 125 154 Q99 155 76 147 Z")
    private val tailBand=shape("M145 146 Q173 155 193 137 Q188 165 155 164 Z")
    private val crown=shape("M102 69 Q99 58 97 50 L111 56 L120 41 L130 56 L144 50 L139 69 Z")
    private val wing=shape("M82 113 Q56 76 27 83 Q37 105 47 111 Q30 107 29 118 Q47 143 80 142 Z")
    private val wingLines=shape("M43 100 Q61 111 77 126 M42 120 Q61 127 77 131")
    private val star=shape("M0 -1 Q.24 -.24 1 0 Q.24 .24 0 1 Q-.24 .24 -1 0 Q-.24 -.24 0 -1 Z")
    private var gradient: Shader?=null
    private var keyStage=-1;private var keyHue=Float.NaN;private var keySat=Float.NaN;private var keySleepy=false
    private var light=0;private var base=0;private var deep=0
    private val hsv=FloatArray(3)
    private fun color(h:Float,s:Float,v:Float):Int { hsv[0]=h;hsv[1]=s;hsv[2]=v;return Color.HSVToColor(hsv) }
    private fun palette(stage:Int,hue:Float,sat:Float,sleepy:Boolean) {
        if(stage==keyStage && hue==keyHue && sat==keySat && sleepy==keySleepy)return
        keyStage=stage;keyHue=hue;keySat=sat;keySleepy=sleepy
        val h=PetPalette.hueFor(stage,hue)
        light=if(sleepy) 0xFFD9DCE3.toInt() else color(h,(.24f*sat).coerceIn(.12f,.6f),.99f)
        base=if(sleepy) 0xFF9299AB.toInt() else color(h,(.48f*sat).coerceIn(.25f,.8f),.84f)
        deep=if(sleepy) 0xFF6A7283.toInt() else color(h,(.58f*sat).coerceIn(.3f,.9f),.64f)
        gradient=LinearGradient(82f,68f,158f,180f,light,base,Shader.TileMode.CLAMP)
    }
    private fun path(c:Canvas,p:Path,color:Int,alpha:Int=255) { fill.shader=null;fill.color=color;fill.alpha=alpha.coerceIn(0,255);c.drawPath(p,fill) }
    private fun line(c:Canvas,p:Path,color:Int,w:Float,alpha:Int=255) { stroke.color=color;stroke.alpha=alpha.coerceIn(0,255);stroke.strokeWidth=w;c.drawPath(p,stroke) }
    private fun oval(c:Canvas,x:Float,y:Float,rx:Float,ry:Float,color:Int,alpha:Int=255) {
        fill.shader=null;fill.color=color;fill.alpha=alpha.coerceIn(0,255);rect.set(x-rx,y-ry,x+rx,y+ry);c.drawOval(rect,fill)
    }
    private fun star(c:Canvas,x:Float,y:Float,r:Float,color:Int,alpha:Int) { c.save();c.translate(x,y);c.scale(r,r);path(c,star,color,alpha);c.restore() }

    fun draw(c:Canvas,stage:Int,cx:Float,cy:Float,r:Float,time:Float,hue:Float,sat:Float,
        sleepy:Boolean=false,static:Boolean=false,lite:Boolean=false,action:PetAction=PetAction.NONE,
        elapsed:Long=-1,strength:Float=1f,charge:Float=0f) {
        val s=stage.coerceIn(0,4);palette(s,hue,sat,sleepy)
        val t=if(static) 0f else time
        val f=PetMotion.frame(action,elapsed,strength)
        val breathing=if(static) 0f else sin(t*(if(sleepy) 1.1f else 1.7f)+s*.4f)
        c.save();c.translate(cx,cy);c.scale(r/75f,r/75f);c.translate(-120f,-120f)
        oval(c,120f,201f,42f,5f,0xFF34334D.toInt(),18)
        if(!static && !lite) effects(c,s,t,sleepy,f.effect,false,charge)
        c.save();c.translate(120f,120f-f.lift+(if(static)0f else breathing*2f))
        c.rotate(f.tilt)
        c.scale(f.scaleX-.008f*breathing+.08f*charge,f.scaleY+.014f*breathing-.08f*charge)
        c.translate(-120f,-120f)
        if(s==4) wings(c,t,static,sleepy,f.effect,action==PetAction.WINGS && elapsed in 0 until PetMotion.duration(action))
        fill.shader=gradient;fill.alpha=255;c.drawPath(body[s],fill);fill.shader=null
        line(c,body[s],base,1.3f)
        c.save();c.rotate(-30f,96f,96f);oval(c,96f,96f,13f,4f,Color.WHITE,60);c.restore()
        when(s) {
            1 -> { path(c,tuft,base);path(c,hands,base) }
            2 -> path(c,mainBolt,0xFFE0B854.toInt())
            3 -> {path(c,band,deep);path(c,tailBand,base)}
            4 -> {path(c,crown,0xFFE7C46C.toInt());line(c,crown,0xFFCBA454.toInt(),1.5f);oval(c,120f,59f,3f,3f,0xFFFFF5CF.toInt())}
        }
        val blink=!static && ((t+s*.23f)%3.7f in 3.2f..3.34f)
        face(c,sleepy || blink,f.effect>.08f)
        c.restore()
        if(!static && !lite) effects(c,s,t,sleepy,f.effect,true,charge)
        c.restore()
    }
    private fun face(c:Canvas,closed:Boolean,happy:Boolean) {
        val ink=0xFF34334D.toInt()
        oval(c,91f,133f,7f,3.5f,0xFFE28E94.toInt(),95);oval(c,149f,133f,7f,3.5f,0xFFE28E94.toInt(),95)
        if(closed||happy) {
            scratch.reset()
            for(i in 0..1) {
                val x=102f+i*36f
                scratch.moveTo(x-5f,119f);scratch.quadTo(x,if(happy)109f else 123f,x+5f,119f)
            };line(c,scratch,ink,3.5f)
        } else {
            oval(c,102f,118f,5.5f,8f,ink);oval(c,138f,118f,5.5f,8f,ink)
            oval(c,100.5f,115.5f,1.7f,1.7f,Color.WHITE);oval(c,136.5f,115.5f,1.7f,1.7f,Color.WHITE)
        }
        scratch.reset();scratch.moveTo(115f,132f);scratch.quadTo(120f,if(happy)140f else 137f,125f,132f);line(c,scratch,ink,2.8f)
    }
    private fun wings(c:Canvas,t:Float,static:Boolean,sleepy:Boolean,power:Float,spread:Boolean) {
        val flap=if(static||sleepy) 0f else sin(t*(if(spread)5f else 1.8f))*(7f+power*14f)
        for(side in 0..1) {
            c.save();if(side==1){c.translate(240f,0f);c.scale(-1f,1f)}
            c.rotate(flap-5f,77f,121f);path(c,wing,base);line(c,wingLines,light,3f);c.restore()
        }
    }
    private fun effects(c:Canvas,s:Int,t:Float,sleepy:Boolean,power:Float,front:Boolean,charge:Float) {
        val intensity=if(sleepy).12f else .65f+power*.35f
        when(s) {
            0 -> if(front) for(j in 0..4) {
                val k=(t*.32f+power*.3f+j*.21f)%1f
                star(c,120f+sin(j*2.3f+k*2f)*(40f+power*20f),135f-k*85f,2.5f+sin(k*3.14159f)*2f,0xFFDDA73B.toInt(),(255*intensity*sin(k*3.14159f)).toInt())
            }
            1 -> if(!front) for(j in 0..1) {
                val k=(t*.38f+power*.4f+j*.5f)%1f;val rad=55f+25f*k+charge*7f
                rect.set(120-rad,122-rad*.84f,120+rad,122+rad*.84f)
                stroke.color=base;stroke.alpha=(155*intensity*(1-k)).toInt();stroke.strokeWidth=2.5f;c.drawOval(rect,stroke)
            }
            2 -> if(front) for(j in 0..2) {
                val k=(t*.7f+j*.33f)%1f;c.save();c.translate(85f+j*34f,161f+k*29f);c.scale(.65f+power*.3f,.65f+power*.3f)
                path(c,bolt,0xFFE1B552.toInt(),(255*intensity*sin(k*3.14159f)).toInt());c.restore()
            }
            3 -> {
                val j=if(front)1 else 0;val rx=85f-j*10f
                c.save();c.rotate(sin(t*1.1f+j)*8f-12f+j*24f,120f,130f)
                rect.set(120-rx,105f+j*23f,120+rx,143f+j*23f)
                stroke.color=if(front)deep else base;stroke.strokeWidth=3.5f;stroke.alpha=(200*intensity).toInt()
                c.drawArc(rect,t*40f+power*160f+j*140f,235f,false,stroke);c.restore()
            }
            4 -> if(!front) for(j in 0..6) {
                val a=t*.33f+j*6.283185f/7f;val rad=80f+5f*sin(t+j)
                star(c,120+cos(a)*rad,118+sin(a)*rad*.92f,4f+2f*sin(t*1.4f+j).pow(2),0xFFD7B460.toInt(),(220*intensity).toInt())
            }
        }
    }
    /** Controlled normalized source, parsed once during renderer creation. */
    private fun shape(data:String):Path {
        val tokens=Regex("[MLQCZ]|-?(?:\\d*\\.)?\\d+").findAll(data).map {it.value}.toList()
        var i=0;val p=Path()
        fun number()=tokens[i++].toFloat()
        while(i<tokens.size) when(tokens[i++]) {
            "M" -> p.moveTo(number(),number())
            "L" -> p.lineTo(number(),number())
            "Q" -> p.quadTo(number(),number(),number(),number())
            "C" -> p.cubicTo(number(),number(),number(),number(),number(),number())
            "Z" -> p.close()
        }
        return p
    }
}
