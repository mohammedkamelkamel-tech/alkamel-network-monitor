package ye.alkamel.networkmonitor
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
data class Device(var id:String,var name:String,var ip:String,var group:String,var place:String,var person:String,var note:String,var enabled:Boolean=true,var online:Boolean=false,var latency:Long=-1,var outageStart:Long=0,var notified:Boolean=false)
data class Outage(val start:Long,val end:Long,val duration:Long)
object AppStore{
 private const val P="network_monitor";private const val D="devices";private const val H="history_";private const val M="monitoring"
 @Synchronized fun devices(c:Context):MutableList<Device>{val a=JSONArray(c.getSharedPreferences(P,0).getString(D,"[]"));val r=mutableListOf<Device>();for(i in 0 until a.length()){val o=a.getJSONObject(i);r.add(Device(o.optString("id"),o.optString("name"),o.optString("ip"),o.optString("group"),o.optString("place"),o.optString("person"),o.optString("note"),o.optBoolean("enabled",true),o.optBoolean("online",false),o.optLong("latency",-1),o.optLong("outageStart",0),o.optBoolean("notified",false)))};return r}
 @Synchronized fun saveDevices(c:Context,ds:List<Device>){val a=JSONArray();ds.forEach{d->a.put(JSONObject().apply{put("id",d.id);put("name",d.name);put("ip",d.ip);put("group",d.group);put("place",d.place);put("person",d.person);put("note",d.note);put("enabled",d.enabled);put("online",d.online);put("latency",d.latency);put("outageStart",d.outageStart);put("notified",d.notified)})};c.getSharedPreferences(P,0).edit().putString(D,a.toString()).apply()}
 fun updateDevice(c:Context,d:Device){val ds=devices(c);val i=ds.indexOfFirst{it.id==d.id};if(i>=0){ds[i]=d;saveDevices(c,ds)}}
 @Synchronized fun addHistory(c:Context,id:String,start:Long,end:Long){val p=c.getSharedPreferences(P,0);val a=JSONArray(p.getString(H+id,"[]"));a.put(JSONObject().apply{put("start",start);put("end",end);put("duration",end-start)});p.edit().putString(H+id,a.toString()).apply()}
 fun history(c:Context,id:String):List<Outage>{val a=JSONArray(c.getSharedPreferences(P,0).getString(H+id,"[]"));return(0 until a.length()).map{val o=a.getJSONObject(it);Outage(o.getLong("start"),o.getLong("end"),o.getLong("duration"))}.reversed()}
 fun monitoring(c:Context)=c.getSharedPreferences(P,0).getBoolean(M,false)
 fun setMonitoring(c:Context,v:Boolean){c.getSharedPreferences(P,0).edit().putBoolean(M,v).apply()}
 fun ipToLong(s:String):Long?=try{s.split('.').let{if(it.size==4&&it.all{p->p.toIntOrNull() in 0..255})it.fold(0L){a,v->(a shl 8)+v.toLong()}else null}}catch(_:Exception){null}
 fun longToIp(x:Long)=(x shr 24 and 255).toString()+"."+(x shr 16 and 255)+"."+(x shr 8 and 255)+"."+(x and 255)
}