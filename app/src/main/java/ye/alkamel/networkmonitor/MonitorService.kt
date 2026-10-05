package ye.alkamel.networkmonitor
import android.app.*
import android.content.*
import android.os.*
import java.util.concurrent.Executors
class MonitorService:Service(){
 private val h=Handler(Looper.getMainLooper());private val ex=Executors.newFixedThreadPool(16)
 private val tick=object:Runnable{override fun run(){checkAll();h.postDelayed(this,60000)}}
 override fun onCreate(){super.onCreate();channels();val n=Notification.Builder(this,"monitor").setSmallIcon(android.R.drawable.ic_menu_search).setContentTitle("مراقبة شبكة الكامل").setContentText("المراقبة الخلفية مفعلة").setOngoing(true).setPriority(Notification.PRIORITY_LOW).build();startForeground(100,n);h.post(tick)}
 private fun checkAll(){val now=System.currentTimeMillis();AppStore.devices(this).filter{it.enabled}.forEach{d->ex.execute{val r=Probe.check(d.ip);val ds=AppStore.devices(this);val i=ds.indexOfFirst{it.id==d.id};if(i<0)return@execute;val x=ds[i];if(r.online){if(x.outageStart>0L&&now-x.outageStart>=120000L)AppStore.addHistory(this,x.id,x.outageStart,now);x.online=true;x.latency=r.latency;x.outageStart=0;x.notified=false}else{x.online=false;x.latency=-1;if(x.outageStart==0L)x.outageStart=now;if(!x.notified&&now-x.outageStart>=120000L){notifyOutage(x);x.notified=true}};AppStore.saveDevices(this,ds)}}}
 private fun notifyOutage(d:Device){getSystemService(NotificationManager::class.java).notify(("outage_"+d.id).hashCode(),Notification.Builder(this,"alerts").setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("انقطاع جهاز الشبكة").setContentText(d.name+" — "+d.ip+" غير متصل منذ دقيقتين").setAutoCancel(true).build())}
 private fun channels(){if(Build.VERSION.SDK_INT>=26){val n=getSystemService(NotificationManager::class.java);n.createNotificationChannel(NotificationChannel("monitor","المراقبة الخلفية",NotificationManager.IMPORTANCE_LOW));n.createNotificationChannel(NotificationChannel("alerts","تنبيهات الانقطاع",NotificationManager.IMPORTANCE_HIGH))}}
 override fun onDestroy(){h.removeCallbacksAndMessages(null);ex.shutdownNow();super.onDestroy()}
 override fun onBind(i:Intent?)=null
}