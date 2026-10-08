package com.hoangnt.aichatbot

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.WindowManager
import android.view.Gravity
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object AshnaWebClient {
 private const val URL="https://app.ashna.ai/chat?agent=gpt-6.1-sol"
 private val main=Handler(Looper.getMainLooper())
 private var web:WebView?=null
 private var window:WindowManager?=null
 private var submitted=false
 @Volatile private var busy=false

 fun requestReply(context:Context,incoming:String):String{
  if(!context.getSharedPreferences("aichatbot",Context.MODE_PRIVATE).getBoolean("ashna_login_confirmed",false))
   error("Chưa đăng nhập Ashna. Hãy đăng nhập Ashna một lần.")
  val future=CompletableFuture<String>()
  main.post{
   if(busy){future.completeExceptionally(IllegalStateException("Ashna đang xử lý lượt trước"));return@post}
   busy=true
   submitted=false
   try { ensure(context.applicationContext) } catch(e:Exception) { busy=false; future.completeExceptionally(e); return@post }
   val v=web?:run{busy=false;future.completeExceptionally(IllegalStateException("Không tạo được Ashna WebView"));return@post}
   v.webViewClient=object:WebViewClient(){
    override fun onPageFinished(view:WebView,url:String){
     CookieManager.getInstance().flush()
     if(!future.isDone && !submitted) main.postDelayed({if(!submitted)submit(view,incoming,future,0)},1200)
    }
   }
   v.loadUrl(URL)
  }
  return try{future.get(40,TimeUnit.SECONDS)}finally{main.post{busy=false}}
 }

 private fun ensure(context:Context){
  if(web!=null)return
  if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M && !Settings.canDrawOverlays(context))
   throw IllegalStateException("Cần cấp quyền hiển thị trên ứng dụng khác cho Ashna")
  val view=WebView(context).apply{
   settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.databaseEnabled=true
   CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(this,true)
  }
  val wm=context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  val type=if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
  val params=WindowManager.LayoutParams(360,640,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply{
   gravity=Gravity.TOP or Gravity.START
   x=0;y=0;alpha=0.01f
  }
  wm.addView(view,params)
  window=wm
  web=view
 }

 private fun submit(v:WebView,incoming:String,f:CompletableFuture<String>,attempt:Int){
  if(f.isDone)return
  if(attempt>20){f.completeExceptionally(IllegalStateException("Không tìm thấy ô nhập Ashna. Kiểm tra mạng hoặc mở Ashna để xác nhận phiên."));return}
  val prompt="Đóng vai người đang nhắn tin. Trả lời ngắn gọn 1-2 câu, tự nhiên, không giải thích, chỉ xuất nội dung có thể gửi ngay. Tin nhắn mới: "+incoming
  val js="(function(){var vis=function(e){if(!e)return false;var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!='none'&&s.visibility!='hidden'};"+
   "var body=document.body?document.body.innerText:'';if(['login','sign-in','signin','auth'].some(function(p){return location.pathname.toLowerCase().split('/').indexOf(p)>=0})&&!document.querySelector('textarea,[contenteditable=\\\"true\\\"]'))return 'LOGIN';"+
   "var a=[].slice.call(document.querySelectorAll('textarea,input:not([type=hidden]),[contenteditable=\\\"true\\\"],[role=\\\"textbox\\\"]')).filter(function(e){return !e.disabled&&e.type!='hidden'&&e.getAttribute('aria-hidden')!='true'});"+
   "var input=a.sort(function(x,y){return y.getBoundingClientRect().bottom-x.getBoundingClientRect().bottom})[0];if(!input)return 'NO_INPUT';"+
   "var baseline=body,q="+quote(prompt)+";input.focus();if(input.isContentEditable)input.textContent=q;else{var setter=Object.getOwnPropertyDescriptor(input.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype,'value');if(setter&&setter.set)setter.set.call(input,q);else input.value=q;}"+
   "input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));"+
   "return JSON.stringify({state:'INPUT_READY',baseline:baseline})})();"
  v.evaluateJavascript(js){raw->
   val state=decode(raw)
   when{
    state=="LOGIN"->f.completeExceptionally(IllegalStateException("Ashna đang ở trang đăng nhập. Mở Ashna để kiểm tra phiên."))
    state.startsWith("{")->{
     submitted=true
     val baseline=runCatching{org.json.JSONObject(state).optString("baseline")}.getOrDefault("")
     main.postDelayed({clickSend(v,incoming,prompt,baseline,f)},600)
    }
    else->main.postDelayed({submit(v,incoming,f,attempt+1)},500)
   }
  }
 }

 private fun clickSend(v:WebView,incoming:String,prompt:String,baseline:String,f:CompletableFuture<String>){
  if(f.isDone)return
  val js="(function(){var i=[].slice.call(document.querySelectorAll('textarea,[contenteditable=true],[role=textbox]')).pop();if(!i)return 'NO_INPUT';"+
   "var value=(i.value||i.innerText||'').trim();if(!value)return 'EMPTY_INPUT';"+
   "var r=i.getBoundingClientRect();var buttons=[].slice.call(document.querySelectorAll('button,[role=button]')).filter(function(b){var z=b.getBoundingClientRect();return z.width>0&&z.height>0&&!b.disabled&&b.getAttribute('aria-disabled')!='true'});"+
   "var score=function(b){var z=b.getBoundingClientRect(),label=((b.getAttribute('aria-label')||'')+' '+(b.getAttribute('title')||'')+' '+(b.getAttribute('data-testid')||'')).toLowerCase();var n=/send|gửi|submit/.test(label)?300:0;if(/microphone|record|voice|mic/.test(label))n-=400;if(z.left>=r.left&&z.right<=r.right+90&&Math.abs(z.bottom-r.bottom)<110)n+=180;if(b.querySelector('svg'))n+=30;return n};"+
   "buttons.sort(function(a,b){return score(b)-score(a)});var b=buttons[0];if(b&&score(b)>=180){b.click();return 'CLICKED'}var form=i.closest('form');if(form&&form.requestSubmit){form.requestSubmit();return 'FORM_SENT'}return 'NO_SEND_BUTTON'})()"
  v.evaluateJavascript(js){raw->
   when(val state=decode(raw)){
    "CLICKED","FORM_SENT"->main.postDelayed({poll(v,incoming,prompt,baseline,f,0,"")},1400)
    else->f.completeExceptionally(IllegalStateException("Không gửi được câu hỏi Ashna: "+state))
   }
  }
 }

 private fun poll(v:WebView,incoming:String,prompt:String,baseline:String,f:CompletableFuture<String>,attempt:Int,previous:String){
  if(f.isDone)return
  if(attempt>30){
   val diagnostic="(function(){var i=document.querySelector('textarea,[contenteditable=true],[role=textbox]');var t=(document.body&&document.body.innerText||'').slice(-500);return JSON.stringify({url:location.pathname,input:i?(i.value||i.innerText||'').slice(0,80):'MISSING',tail:t})})()"
   v.evaluateJavascript(diagnostic){raw->
    val detail=decode(raw).take(650)
    android.util.Log.e("AshnaWebClient","Ashna timeout: "+detail)
    f.completeExceptionally(IllegalStateException("Ashna chưa có phản hồi đọc được. Chẩn đoán: "+detail))
   }
   return
  }
  val js="(function(){var before="+quote(baseline)+";var now=document.body?document.body.innerText:'';"+
   "var i=[].slice.call(document.querySelectorAll('textarea,[contenteditable=true],[role=textbox]')).pop();var q="+quote(prompt)+";"+
   "var selectors='[data-message-author-role=assistant],[data-role=assistant],[data-testid*=assistant],[class*=assistant],[class*=Assistant],article,[class*=markdown],[class*=prose]';"+
   "var nodes=[].slice.call(document.querySelectorAll(selectors));var out=[];nodes.forEach(function(n){var t=(n.innerText||'').trim();if(t.length>1&&t.length<2500&&!before.includes(t)&&!t.includes(q)&&!t.includes('What can I do for you today?')&&!t.includes('Hey, Hoàng Tiến! How can I help?'))out.push(t)});"+
   "if(!out.length&&now.length>before.length){var t=now.slice(before.length).trim();if(t.length>1&&!t.includes(q)&&t.length<2500)out.push(t)}"+
   "return JSON.stringify(out.slice(-5))})();"
  v.evaluateJavascript(js){raw->
   val a=runCatching{org.json.JSONArray(decode(raw))}.getOrNull();var candidate=""
   if(a!=null)for(i in 0 until a.length()){val x=clean(a.optString(i),incoming);if(x.isNotBlank())candidate=x}
   if(candidate.isNotBlank()&&candidate==previous)f.complete(candidate)
   else main.postDelayed({poll(v,incoming,prompt,baseline,f,attempt+1,candidate)},700)
  }
 }

 private fun clean(text:String,incoming:String)=text.replace(Regex("(?i)AshnaAI\\\\s+can\\\\s+make\\\\s+mistakes\\\\.?"),"").trim()
  .takeIf{it!=incoming&&!it.startsWith("Đóng vai người đang nhắn tin.")}.orEmpty()
 private fun quote(s:String)=org.json.JSONObject.quote(s)
 private fun decode(s:String)=runCatching{org.json.JSONTokener(s).nextValue()?.toString().orEmpty()}.getOrDefault(s.trim('"'))
}
