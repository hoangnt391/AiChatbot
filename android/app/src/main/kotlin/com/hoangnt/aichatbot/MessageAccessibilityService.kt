package com.hoangnt.aichatbot
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent

class MessageAccessibilityService:AccessibilityService(){
 private var last=""
 override fun onAccessibilityEvent(e:AccessibilityEvent?){
  if(e==null)return
  val p=e.packageName?.toString()?:return
  if(p!="com.facebook.orca"&&p!="com.zing.zalo")return
  val root=rootInActiveWindow?:return
  val text=read(root).trim()
  if(text.isNotEmpty()&&text!=last){
   last=text
   val i=Intent(this,OverlayService::class.java).apply{action="NEW_MESSAGE";putExtra("platform",if(p=="com.zing.zalo")"Zalo" else "Messenger");putExtra("message",text.takeLast(1200))}
   startService(i)
  }
 }
 private fun read(n:AccessibilityNodeInfo):String{
  val s=StringBuilder();n.text?.toString()?.trim()?.takeIf{it.isNotEmpty()}?.let{s.append(it).append('\n')}
  for(i in 0 until n.childCount)n.getChild(i)?.let{c->s.append(read(c));c.recycle()}
  return s.toString()
 }
 override fun onInterrupt(){}
}
