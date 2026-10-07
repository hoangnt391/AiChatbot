package com.hoangnt.aichatbot

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object AshnaWebClient {
 private const val URL="https://app.ashna.ai/chat?agent=gpt-6.1-sol"
 private val main=Handler(Looper.getMainLooper())
 private var web:WebView?=null
 @Volatile private var busy=false

 fun requestReply(context:Context,incoming:String):String{
  if(!context.getSharedPreferences("aichatbot",Context.MODE_PRIVATE).getBoolean("ashna_login_confirmed",false))
   error("Chưa đăng nhập Ashna. Hãy đăng nhập Ashna một lần.")
  val future=CompletableFuture<String>()
  main.post{
   if(busy){future.completeExceptionally(IllegalStateException("Ashna đang xử lý lượt trước"));return@post}
   busy=true; ensure(context.applicationContext)
   val v=web?:run{busy=false;future.completeExceptionally(IllegalStateException("Không tạo được Ashna WebView"));return@post}
   v.webViewClient=object:WebViewClient(){
    override fun onPageFinished(view:WebView,url:String){CookieManager.getInstance().flush();main.postDelayed({submit(view,incoming,future,0)},700)}
   }
   v.loadUrl(URL)
  }
  return try{future.get(40,TimeUnit.SECONDS)}finally{main.post{busy=false}}
 }

 private fun ensure(context:Context){
  if(web!=null)return
  web=WebView(context).apply{
   settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.databaseEnabled=true
   CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(this,true)
  }
 }

 private fun submit(v:WebView,incoming:String,f:CompletableFuture<String>,attempt:Int){
  if(f.isDone)return
  if(attempt>20){f.completeExceptionally(IllegalStateException("Không tìm thấy ô nhập Ashna"));return}
  val prompt="Đóng vai người đang nhắn tin. Trả lời ngắn gọn 1-2 câu, tự nhiên, không giải thích, chỉ xuất nội dung có thể gửi ngay. Tin nhắn mới: "+incoming
  val js="(function(){var vis=function(e){if(!e)return false;var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!='none'&&s.visibility!='hidden'};"+
   "var body=document.body?document.body.innerText:'';if(/sign in|log in|đăng nhập/i.test(body))return 'LOGIN';"+
   "var a=[].slice.call(document.querySelectorAll('textarea,input,[contenteditable=\\\"true\\\"]')).filter(function(e){return vis(e)&&!e.disabled&&e.type!='hidden'});"+
   "var input=a.sort(function(x,y){return y.getBoundingClientRect().bottom-x.getBoundingClientRect().bottom})[0];if(!input)return 'NO_INPUT';"+
   "var baseline=body,q="+quote(prompt)+";input.focus();if(input.isContentEditable)input.innerText=q;else input.value=q;"+
   "input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));"+
   "var bs=[].slice.call(document.querySelectorAll('button,[role=button],input[type=submit]')).filter(vis);"+
   "var b=bs.find(function(x){var z=((x.innerText||'')+' '+(x.getAttribute('aria-label')||'')+' '+(x.getAttribute('title')||'')).toLowerCase();return /send|gửi|submit|arrow.?up|paper.?plane/.test(z)});"+
   "if(b)b.click();else input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));"+
   "return JSON.stringify({state:'SENT',baseline:baseline})})();"
  v.evaluateJavascript(js){raw->
   val state=decode(raw)
   when{
    state=="LOGIN"->f.completeExceptionally(IllegalStateException("Phiên Ashna đã hết. Hãy đăng nhập lại."))
    state.startsWith("{")->{
     val baseline=runCatching{org.json.JSONObject(state).optString("baseline")}.getOrDefault("")
     main.postDelayed({poll(v,incoming,prompt,baseline,f,0,"")},1600)
    }
    else->main.postDelayed({submit(v,incoming,f,attempt+1)},500)
   }
  }
 }

 private fun poll(v:WebView,incoming:String,prompt:String,baseline:String,f:CompletableFuture<String>,attempt:Int,previous:String){
  if(f.isDone)return
  if(attempt>30){f.completeExceptionally(IllegalStateException("Ashna không trả về câu trả lời"));return}
  val js="(function(){var old={};"+quote(baseline)+".split(/\\\\n+/).forEach(function(x){x=x.trim();if(x)old[x]=1});"+
   "var q="+quote(prompt)+",original="+quote(incoming)+",out=[];"+
   "var bad=/^(send|gửi|new chat|chat|settings|sign in|log in|copy|regenerate|stop|retry|thinking|thought|generating)$/i;"+
   "var nodes=[].slice.call(document.querySelectorAll('[data-message-author-role=assistant],[data-role=assistant],[role=article],[data-message-id],[data-testid*=message],[class*=message],[class*=Message]'));"+
   "nodes.forEach(function(n){var t=(n.innerText||n.textContent||'').trim();if(t&&t!==q&&t!==original&&!old[t]&&!bad.test(t)&&t.length>1&&t.length<4000)out.push(t)});"+
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
