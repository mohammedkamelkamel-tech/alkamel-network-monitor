package ye.alkamel.networkmonitor
import java.net.InetSocketAddress
import java.net.Socket
data class ProbeResult(val online:Boolean,val latency:Long)
object Probe{fun check(ip:String):ProbeResult{val t=System.currentTimeMillis();for(p in intArrayOf(80,443,8080)){try{Socket().use{it.connect(InetSocketAddress(ip,p),700);return ProbeResult(true,System.currentTimeMillis()-t)}}catch(_:Exception){}};return ProbeResult(false,-1)}}