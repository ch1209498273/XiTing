package com.lujinyu.xiting

import android.content.Context
import android.media.*

/** Bundled offline speech. No network, TTS installation, media session or external player needed. */
internal class DemoAudioPlayer(private val context:Context):AutoCloseable {
 private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
 private var generation=0L
 private var player:MediaPlayer?=null
 private var focus:AudioFocusRequest?=null
 val isPlaying:Boolean get()=try{player?.isPlaying==true}catch(_:RuntimeException){false}
 fun start(onStarted:()->Unit,onComplete:()->Unit,onFailure:()->Unit):Boolean {
  close()
  val run=generation
  val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
  val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
   .setOnAudioFocusChangeListener { if(generation==run && it<0)onFailure() }.build()
  try {
   if(audio.requestAudioFocus(request)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return false
   focus=request
   val chinese=AppLocales.wrap(context).resources.configuration.locales[0].language=="zh"
   val media=MediaPlayer()
   player=media
   media.setAudioAttributes(attributes)
   context.resources.openRawResourceFd(if(chinese)R.raw.guide_sample_zh else R.raw.guide_sample_en).use { fd ->
    media.setDataSource(fd.fileDescriptor,fd.startOffset,fd.length)
   }
   media.setOnPreparedListener {
    if(generation==run && player===media)try {
     media.start()
     if(media.isPlaying)onStarted() else onFailure()
    }catch(_:RuntimeException){onFailure()}
   }
   media.setOnCompletionListener { if(generation==run && player===media)onComplete() }
   media.setOnErrorListener { _,_,_ -> if(generation==run && player===media)onFailure();true }
   media.prepareAsync()
   return true
  } catch(_:RuntimeException) {close();return false}
 }
 override fun close() {
  generation++
  val old=player;player=null
  try{old?.setOnPreparedListener(null);old?.setOnCompletionListener(null);old?.setOnErrorListener(null);old?.release()}catch(_:RuntimeException){}
  val request=focus;focus=null
  if(request!=null)try{audio.abandonAudioFocusRequest(request)}catch(_:RuntimeException){}
 }
}
