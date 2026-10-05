package ye.alkamel.networkmonitor
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import java.util.concurrent.Executors

class MainActivity:Activity(){
 private lateinit var adapter:DeviceAdapter
 private lateinit var summary:TextView
 private lateinit var monitor:Button
 private val ex=Executors.newFixedThreadPool(16)
 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main);summary=findViewById(R.id.summary);monitor=findViewById(R.id.monitor)
  adapter=DeviceAdapter(this,AppStore.devices(this),::edit,::toggle,::history,::openWeb);findViewById<ListView>(R.id.deviceList).adapter=adapter
  findViewById<Button>(R.id.scan).setOnClickListener{scan()};findViewById<Button>(R.id.addRange).setOnClickListener{range()};findViewById<Button>(R.id.addDevice).setOnClickListener{dialog(null)};monitor.setOnClickListener{toggleMonitor()}
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),10);ui()}
 override fun onResume(){super.onResume();adapter.replace(AppStore.devices(this));ui()}
 private fun toggleMonitor(){if(AppStore.monitoring(this)){stopService(Intent(this,MonitorService::class.java));AppStore.setMonitoring(this,false)}else{AppStore.setMonitoring(this,true);if(Build.VERSION.SDK_INT>=26)startForegroundService(Intent(this,MonitorService::class.java))else startService(Intent(this,MonitorService::class.java))};ui()}
 private fun scan(){val ds=AppStore.devices(this).filter{it.enabled};if(ds.isEmpty()){toast("لا توجد أجهزة مفعلة");return};toast("جاري الفحص...");ds.forEach{d->ex.execute{val r=Probe.check(d.ip);val a=AppStore.devices(this);val i=a.indexOfFirst{it.id==d.id};if(i>=0){a[i].online=r.online;a[i].latency=r.latency;AppStore.saveDevices(this,a)};runOnUiThread{adapter.replace(AppStore.devices(this));ui()}}}}
 private fun range(){val v=EditText(this);v.hint="10.0.0.2-10.0.0.100";AlertDialog.Builder(this).setTitle("إضافة نطاق IP").setView(v).setPositiveButton("التالي"){_,_->val p=v.text.toString().trim().split("-");if(p.size!=2){toast("صيغة غير صحيحة");return@setPositiveButton};val a=AppStore.ipToLong(p[0].trim());val z=AppStore.ipToLong(p[1].trim());if(a==null||z==null||z<a||z-a>500){toast("النطاق غير صحيح");return@setPositiveButton};val g=EditText(this);g.hint="اسم المجموعة";AlertDialog.Builder(this).setTitle("المجموعة").setView(g).setPositiveButton("إضافة"){_,_->val ds=AppStore.devices(this);var n=0;for(x in a..z){val ip=AppStore.longToIp(x);if(ds.none{it.ip==ip}){ds.add(Device(System.currentTimeMillis().toString()+x,"AP-"+ip.substringAfterLast('.'),ip,g.text.toString().trim(),"","",""));n++}};AppStore.saveDevices(this,ds);adapter.replace(ds);ui();toast("تمت إضافة "+n+" جهاز")}.setNegativeButton("إلغاء",null).show()}.setNegativeButton("إلغاء",null).show()}
 private fun dialog(e:Device?){val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,0,24,0)};val fs=listOf("اسم الجهاز","IP","المجموعة / النطاق","المكان","الشخص","ملاحظات").map{h: String -> EditText(this).apply{hint=h}};fs[0].setText(e?.name?:"");fs[1].setText(e?.ip?:"");fs[2].setText(e?.group?:"");fs[3].setText(e?.place?:"");fs[4].setText(e?.person?:"");fs[5].setText(e?.note?:"");fs.forEach{box.addView(it)}
  AlertDialog.Builder(this).setTitle(if(e==null)"إضافة جهاز" else "تعديل الجهاز").setView(box).setPositiveButton("حفظ"){_,_->val ip=fs[1].text.toString().trim();if(AppStore.ipToLong(ip)==null){toast("IP غير صحيح");return@setPositiveButton};val ds=AppStore.devices(this);if(ds.any{it.ip==ip&&it.id!=e?.id}){toast("IP موجود مسبقًا");return@setPositiveButton};val d=e?:Device(System.currentTimeMillis().toString(),"","","","","","");d.name=fs[0].text.toString().trim().ifBlank{"AP-"+ip.substringAfterLast('.')};d.ip=ip;d.group=fs[2].text.toString().trim();d.place=fs[3].text.toString().trim();d.person=fs[4].text.toString().trim();d.note=fs[5].text.toString().trim();if(e==null)ds.add(d);AppStore.saveDevices(this,ds);adapter.replace(ds);ui()}.setNegativeButton("إلغاء",null).show()}
 private fun edit(d:Device)=dialog(d)
 private fun toggle(d:Device){d.enabled=!d.enabled;AppStore.updateDevice(this,d);adapter.replace(AppStore.devices(this));ui()}
 private fun history(d:Device){val h=AppStore.history(this,d.id);if(h.isEmpty()){toast("لا يوجد سجل انقطاع");return};val s=h.joinToString("\n\n"){"بدأ: "+java.util.Date(it.start)+"\nعاد: "+java.util.Date(it.end)+"\nالمدة: "+duration(it.duration)};AlertDialog.Builder(this).setTitle("سجل "+d.name).setMessage(s).setPositiveButton("إغلاق",null).show()}
 private fun openWeb(d:Device){startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("http://"+d.ip)))}
 private fun duration(ms:Long):String{val t=ms/1000;return (t/3600).toString()+"س "+((t%3600)/60)+"د "+(t%60)+"ث"}
 private fun ui(){val d=AppStore.devices(this);summary.text="🟢 "+d.count{it.enabled&&it.online}+" متصل   🔴 "+d.count{it.enabled&&!it.online}+" غير متصل   ⚪ "+d.count{!it.enabled}+" موقوف   📡 "+d.size+" جهاز";monitor.text=if(AppStore.monitoring(this))"⏹ إيقاف المراقبة الخلفية" else "▶ تشغيل المراقبة الخلفية"}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
 override fun onDestroy(){ex.shutdownNow();super.onDestroy()}
}
class DeviceAdapter(private val c:Context,private var ds:List<Device>,private val edit:(Device)->Unit,private val toggle:(Device)->Unit,private val hist:(Device)->Unit,private val web:(Device)->Unit):BaseAdapter(){
 fun replace(x:List<Device>){ds=x;notifyDataSetChanged()}
 override fun getCount()=ds.size
 override fun getItem(p:Int)=ds[p]
 override fun getItemId(p:Int)=p.toLong()
 override fun getView(p:Int,v:View?,parent:ViewGroup):View{val d=ds[p];val box=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(12,12,12,12)};val t=TextView(c).apply{textSize=18f;text=(if(!d.enabled)"⚪"else if(d.online)"🟢"else"🔴")+" "+d.name+" — "+d.ip};val i=TextView(c).apply{text="📁 "+d.group+"   📍 "+d.place+"   👤 "+d.person+"\n📝 "+d.note};val b=LinearLayout(c);fun add(s:String,f:()->Unit){b.addView(Button(c).apply{text=s;setOnClickListener{f()}})};add("تعديل"){edit(d)};add(if(d.enabled)"تعطيل"else"تفعيل"){toggle(d)};add("السجل"){hist(d)};add("فتح"){web(d)};box.addView(t);box.addView(i);box.addView(b);return box}
}