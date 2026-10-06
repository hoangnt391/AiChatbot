import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/services.dart';

void main()=>runApp(const AiChatBotApp());

class AiChatBotApp extends StatelessWidget{
 const AiChatBotApp({super.key});
 Widget build(BuildContext c)=>MaterialApp(debugShowCheckedModeBanner:false,title:'AiChatBot',theme:ThemeData(useMaterial3:true,colorSchemeSeed:Colors.blue),home:const HomePage());
}

class Store{
 static Future<SharedPreferences> get p=>SharedPreferences.getInstance();
 static Future<String> key()async=>(await p).getString('openai_key')??'';
 static Future<void> saveKey(String v)async=>(await p).setString('openai_key',v);
 static Future<List<Map<String,dynamic>>> history()async{
  final s=(await p).getString('history'); if(s==null)return[];
  return List<Map<String,dynamic>>.from(jsonDecode(s));
 }
 static Future<void> add(String role,String content)async{
  final h=await history(); h.add({'role':role,'content':content});
  await (await p).setString('history',jsonEncode(h.length>40?h.sublist(h.length-40):h));
 }
}

class OpenAI{
 static Future<String> reply(String incoming,{String platform='Messenger'})async{
  final key=await Store.key(); if(key.isEmpty)throw Exception('Chưa nhập OpenAI API key.');
  final h=await Store.history();
  final msgs=<Map<String,String>>[
   {'role':'system','content':'Bạn là người Việt đang nhắn tin thật. Hãy soạn câu trả lời ngắn, tự nhiên, đời thường, đúng ngữ cảnh. Không nói mình là AI. Chỉ trả về câu có thể gửi.'},
   ...h.map((e)=>{'role':e['role'] as String,'content':e['content'] as String}),
   {'role':'user','content':'Nền tảng: $platform\nTin nhắn mới: $incoming'}
  ];
  final r=await http.post(Uri.parse('https://api.openai.com/v1/chat/completions'),headers:{'Authorization':'Bearer $key','Content-Type':'application/json'},body:jsonEncode({'model':'gpt-4o-mini','messages':msgs,'temperature':0.8,'max_tokens':180}));
  if(r.statusCode<200||r.statusCode>=300)throw Exception('OpenAI ${r.statusCode}: ${r.body}');
  final a=(jsonDecode(r.body)['choices'][0]['message']['content']??'').toString().trim();
  await Store.add('user',incoming); await Store.add('assistant',a); return a;
 }
}

class HomePage extends StatefulWidget{const HomePage({super.key});State<HomePage> createState()=>_HomePageState();}
class _HomePageState extends State<HomePage>{
 final keyCtrl=TextEditingController(); static const ch=MethodChannel('aichatbot/native'); bool on=false;
 void initState(){super.initState();Store.key().then((v)=>keyCtrl.text=v);}
 Future<void> access()async{await ch.invokeMethod('openAccessibility');}
 Widget build(BuildContext c)=>Scaffold(appBar:AppBar(title:const Text('AiChatBot')),body:ListView(padding:const EdgeInsets.all(18),children:[
  const Text('AiChatBot',style:TextStyle(fontSize:28,fontWeight:FontWeight.bold)),
  const SizedBox(height:8),const Text('Gợi ý trả lời tự nhiên cho Messenger và Zalo.'),
  const SizedBox(height:24),TextField(controller:keyCtrl,decoration:const InputDecoration(labelText:'OpenAI API key',border:OutlineInputBorder())),
  const SizedBox(height:10),FilledButton(onPressed:()async{await Store.saveKey(keyCtrl.text.trim());if(mounted)ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('Đã lưu API key')));},child:const Text('Lưu API key')),
  const SizedBox(height:18),FilledButton.icon(onPressed:access,icon:const Icon(Icons.accessibility_new),label:const Text('Mở cài đặt Trợ năng')),
  const SizedBox(height:10),SwitchListTile(value:on,onChanged:(v)=>setState(()=>on=v),title:const Text('Bong bóng trợ lý'),subtitle:const Text('Cần cấp quyền hiển thị trên ứng dụng khác')),
  const SizedBox(height:20),const Text('Luồng xử lý',style:TextStyle(fontSize:18,fontWeight:FontWeight.bold)),
  const Text('• Đọc tin nhắn mới bằng Accessibility Service\n• Giữ lịch sử hội thoại cục bộ\n• Gọi GPT-4o-mini tạo câu trả lời\n• Hiển thị gợi ý trong bong bóng\n• Sao chép / Sửa / Đóng')
 ]));
}
