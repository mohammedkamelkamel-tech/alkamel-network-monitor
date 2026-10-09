package ye.alkamel.networkmonitor

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import java.util.concurrent.Executors

class MainActivity:Activity(){
 private lateinit var adapter:DeviceAdapter
 private lateinit var summary:TextView
 private lateinit var monitor:Button
 private lateinit var offlineFilter:Button
 private var offlineOnly=false
 private val PICK_BACKUP_FOLDER=701
 private val ex=Executors.newFixedThreadPool(16)

 override fun onCreate(b:Bundle?){
  super.onCreate(b);setContentView(R.layout.activity_main)
  summary=findViewById(R.id.summary);monitor=findViewById(R.id.monitor);offlineFilter=findViewById(R.id.offlineFilter)
  adapter=DeviceAdapter(this,displayDevices(),::edit,::toggle,::history,::openWeb)
  findViewById<ListView>(R.id.deviceList).adapter=adapter
  findViewById<Button>(R.id.scan).setOnClickListener{scan()}
  findViewById<Button>(R.id.addRange).setOnClickListener{range()}
  findViewById<Button>(R.id.addDevice).setOnClickListener{dialog(null)}
  findViewById<Button>(R.id.backup).setOnClickListener{chooseBackupFolder()}
  monitor.setOnClickListener{toggleMonitor()}
  offlineFilter.setOnClickListener{
   offlineOnly=!offlineOnly
   refresh()
  }
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),10)
  ui()
 }

 override fun onResume(){super.onResume();refresh()}

 private fun chooseBackupFolder(){
  val i=Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
  startActivityForResult(i,PICK_BACKUP_FOLDER)
 }

 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==PICK_BACKUP_FOLDER&&resultCode==RESULT_OK){
   val uri=data?.data ?: return
   val flags=data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
   try{contentResolver.takePersistableUriPermission(uri,flags)}catch(_:Exception){}
   BackupManager.saveFolder(this,uri)
   if(BackupManager.createBackup(this))toast("تم حفظ النسخة الاحتياطية وتفعيل النسخ كل 24 ساعة")
   else toast("تعذر إنشاء النسخة. تحقق من صلاحية مجلد الحفظ")
  }
 }

 private fun displayDevices():List<Device>{
  val all=AppStore.devices(this)
  return if(offlineOnly) all.filter{it.enabled&&!it.online} else all
 }

 private fun refresh(){
  adapter.replace(displayDevices())
  ui()
 }

 private fun toggleMonitor(){
  if(AppStore.monitoring(this)){
   stopService(Intent(this,MonitorService::class.java))
   AppStore.setMonitoring(this,false)
  }else{
   AppStore.setMonitoring(this,true)
   if(Build.VERSION.SDK_INT>=26)startForegroundService(Intent(this,MonitorService::class.java))else startService(Intent(this,MonitorService::class.java))
  }
  ui()
 }

 private fun scan(){
  val ds=AppStore.devices(this).filter{it.enabled}
  if(ds.isEmpty()){toast("لا توجد أجهزة مفعلة")}else{
   toast("جاري الفحص...")
   ds.forEach{d->ex.execute{
    val r=Probe.check(d.ip)
    AppStore.updateProbeResult(this,d.id,r.online,r.latency)
    runOnUiThread{refresh()}
   }}
  }
 }

 private fun range(){
  val v=EditText(this);v.hint="10.0.0.2-10.0.0.100"
  AlertDialog.Builder(this).setTitle("إضافة نطاق IP").setView(v).setPositiveButton("التالي"){_,_->
   val p=v.text.toString().trim().split("-")
   if(p.size!=2)toast("صيغة غير صحيحة")else{
    val a=AppStore.ipToLong(p[0].trim());val z=AppStore.ipToLong(p[1].trim())
    if(a==null||z==null||z<a||z-a>500)toast("النطاق غير صحيح")else{
     val g=EditText(this);g.hint="اسم المجموعة"
     AlertDialog.Builder(this).setTitle("المجموعة").setView(g).setPositiveButton("إضافة"){_,_->
      val ds=AppStore.devices(this);var n=0
      for(x in a..z){
       val ip=AppStore.longToIp(x)
       if(ds.none{it.ip==ip}){ds.add(Device(System.currentTimeMillis().toString()+x,"AP-"+ip.substringAfterLast('.'),ip,g.text.toString().trim(),"","",""));n++}
      }
      AppStore.saveDevices(this,ds);refresh();toast("تمت إضافة "+n+" جهاز")
     }.setNegativeButton("إلغاء",null).show()
    }
   }
  }.setNegativeButton("إلغاء",null).show()
 }

 private fun dialog(e:Device?){
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,0,24,0)}
  val fs=listOf("اسم الجهاز","IP","المجموعة / النطاق","المكان","الشخص","ملاحظات").map{h:String->EditText(this).apply{hint=h}}
  fs[0].setText(e?.name?:"");fs[1].setText(e?.ip?:"");fs[2].setText(e?.group?:"");fs[3].setText(e?.place?:"");fs[4].setText(e?.person?:"");fs[5].setText(e?.note?:"")
  fs.forEach{box.addView(it)}
  AlertDialog.Builder(this).setTitle(if(e==null)"إضافة جهاز" else "تعديل الجهاز").setView(box).setPositiveButton("حفظ"){_,_->
   val ip=fs[1].text.toString().trim()
   val valid=AppStore.ipToLong(ip)!=null
   val ds=AppStore.devices(this)
   val duplicate=ds.any{it.ip==ip&&it.id!=e?.id}
   if(!valid)toast("IP غير صحيح")else if(duplicate)toast("IP موجود مسبقًا")else{
    val d=if(e==null)Device(System.currentTimeMillis().toString(),"",ip,"","","","") else e.copy()
    d.name=fs[0].text.toString().trim().ifBlank{"AP-"+ip.substringAfterLast('.')}
    d.ip=ip
    d.group=fs[2].text.toString().trim()
    d.place=fs[3].text.toString().trim()
    d.person=fs[4].text.toString().trim()
    d.note=fs[5].text.toString().trim()
    if(e==null)ds.add(d) else {
     val i=ds.indexOfFirst{it.id==d.id}
     if(i>=0)ds[i]=d
    }
    AppStore.saveDevices(this,ds)
    refresh()
    toast("تم حفظ بيانات الجهاز")
   }
  }.setNegativeButton("إلغاء",null).show()
 }

 private fun edit(d:Device)=dialog(d)

 private fun toggle(d:Device){
  d.enabled=!d.enabled
  if(!d.enabled){d.online=false;d.outageStart=0;d.notified=false}
  AppStore.updateDevice(this,d)
  refresh()
 }

 private fun history(d:Device){
  val h=AppStore.history(this,d.id)
  if(h.isEmpty())toast("لا يوجد سجل انقطاع")else{
   val s=h.joinToString("\n\n"){"بدأ: "+java.util.Date(it.start)+"\nعاد: "+java.util.Date(it.end)+"\nالمدة: "+duration(it.duration)}
   AlertDialog.Builder(this).setTitle("سجل "+d.name).setMessage(s).setPositiveButton("إغلاق",null).show()
  }
 }

 private fun openWeb(d:Device){startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("http://"+d.ip)))}
 private fun duration(ms:Long):String{val t=ms/1000;return (t/3600).toString()+"س "+((t%3600)/60)+"د "+(t%60)+"ث"}

 private fun ui(){
  val d=AppStore.devices(this)
  summary.text="🟢 "+d.count{it.enabled&&it.online}+" متصل   🔴 "+d.count{it.enabled&&!it.online}+" غير متصل   ⚪ "+d.count{!it.enabled}+" موقوف   📡 "+d.size+" جهاز"
  if(AppStore.monitoring(this)){
   monitor.text="🟢 المراقبة الخلفية: تعمل"
   monitor.setTextColor(Color.WHITE)
   monitor.setBackgroundColor(Color.rgb(46,125,50))
  }else{
   monitor.text="🔴 المراقبة الخلفية: متوقفة"
   monitor.setTextColor(Color.WHITE)
   monitor.setBackgroundColor(Color.rgb(198,40,40))
  }
  offlineFilter.text=if(offlineOnly)"📋 عرض كل الأجهزة" else "🔴 غير المتصل فقط"
 }

 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
 override fun onDestroy(){ex.shutdownNow();super.onDestroy()}
}

class DeviceAdapter(private val c:Context,private var ds:List<Device>,private val edit:(Device)->Unit,private val toggle:(Device)->Unit,private val hist:(Device)->Unit,private val web:(Device)->Unit):BaseAdapter(){
 fun replace(x:List<Device>){ds=x;notifyDataSetChanged()}
 override fun getCount()=ds.size
 override fun getItem(p:Int)=ds[p]
 override fun getItemId(p:Int)=p.toLong()
 override fun getView(p:Int,v:View?,parent:ViewGroup):View{
  val d=ds[p]
  val box=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(12,12,12,12)}
  val t=TextView(c).apply{textSize=18f;text=(if(!d.enabled)"⚪"else if(d.online)"🟢"else"🔴")+" "+d.name+" — "+d.ip}
  val details=mutableListOf<String>()
  if(d.group.isNotBlank())details.add("📁 "+d.group)
  if(d.place.isNotBlank())details.add("📍 المكان: "+d.place)
  if(d.person.isNotBlank())details.add("👤 "+d.person)
  if(d.note.isNotBlank())details.add("📝 الملاحظات: "+d.note)
  val i=TextView(c).apply{text=details.joinToString("   ");textSize=14f}
  val b=LinearLayout(c)
  fun add(s:String,f:()->Unit){b.addView(Button(c).apply{text=s;setOnClickListener{f()}})}
  add("تعديل"){edit(d)};add(if(d.enabled)"تعطيل"else"تفعيل"){toggle(d)};add("السجل"){hist(d)};add("فتح"){web(d)}
  box.addView(t);if(details.isNotEmpty())box.addView(i);box.addView(b);return box
 }
}