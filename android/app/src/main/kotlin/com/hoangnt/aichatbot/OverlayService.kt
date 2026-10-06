package com.hoangnt.aichatbot
import android.app.Service
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.*
import android.content.ClipboardManager
import android.content.ClipData

class OverlayService:Service(){
 private lateinit var wm:WindowManager
 private var bubble:TextView?=null
 override fun onCreate(){super.onCreate();wm=getSystemService(WINDOW_SERVICE) as WindowManager;showBubble()}
 override fun onStartCommand(i:Intent?,f:Int,id:Int):Int{if(i?.action=="NEW_MESSAGE")showSuggestion(i.getStringExtra("message")?:"",i.getStringExtra("platform")?:"Messenger");return START_STICKY}
 private fun showBubble(){
  if(bubble!=null)return
  val b=TextView(this);b.text="AI";b.textSize=18f;b.setTextColor(Color.WHITE);b.gravity=Gravity.CENTER;b.setBackgroundColor(Color.rgb(30,110,220))
  val lp=WindowManager.LayoutParams(72,72,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT)
  var sx=0f;var sy=0f;var ox=0;var oy=0
  b.setOnTouchListener{_,e->when(e.action){MotionEvent.ACTION_DOWN->{sx=e.rawX;sy=e.rawY;ox=lp.x;oy=lp.y;true};MotionEvent.ACTION_MOVE->{lp.x=ox+(e.rawX-sx).toInt();lp.y=oy+(e.rawY-sy).toInt();wm.updateViewLayout(b,lp);true};MotionEvent.ACTION_UP->{showPanel();true};else->true}}
  bubble=b;wm.addView(b,lp)
 }
 private fun showPanel(){
  val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(22,22,22,22);box.setBackgroundColor(Color.WHITE)
  val title=TextView(this);title.text="AiChatBot";title.textSize=20f;box.addView(title)
  val msg=EditText(this);msg.hint="Tin nhắn";box.addView(msg)
  val answer=EditText(this);answer.hint="Gợi ý trả lời";answer.minLines=3;box.addView(answer)
  val row=LinearLayout(this);val copy=Button(this);copy.text="Sao chép";val edit=Button(this);edit.text="Sửa";val close=Button(this);close.text="Đóng"
  row.addView(copy,LinearLayout.LayoutParams(0,60,1f));row.addView(edit,LinearLayout.LayoutParams(0,60,1f));row.addView(close,LinearLayout.LayoutParams(0,60,1f));box.addView(row)
  copy.setOnClickListener{(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("AiChatBot",answer.text))}
  edit.setOnClickListener{answer.requestFocus()}
  close.setOnClickListener{wm.removeView(box)}
  val p=WindowManager.LayoutParams(700,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.CENTER
  wm.addView(box,p)
 }
 override fun onBind(i:Intent?):IBinder?=null
}
