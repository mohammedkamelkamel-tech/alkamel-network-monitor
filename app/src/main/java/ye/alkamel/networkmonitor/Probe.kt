package ye.alkamel.networkmonitor

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ProbeResult(val online:Boolean,val latency:Long)

object Probe{
 private val ports=intArrayOf(80,443,8080,8000,8443,81)

 fun check(ip:String):ProbeResult{
  val started=System.currentTimeMillis()

  // Try the common web-management ports in parallel. One reachable service is enough.
  val pool=Executors.newFixedThreadPool(ports.size)
  try{
   val tasks=ports.map{port->Callable<Boolean>{tcp(ip,port)}}
   val futures=tasks.map{pool.submit(it)}
   var ok=false
   futures.forEach{f->try{if(f.get(1200,TimeUnit.MILLISECONDS))ok=true}catch(_:Exception){}}
   if(ok)return ProbeResult(true,System.currentTimeMillis()-started)
  }finally{pool.shutdownNow()}

  // A web page may accept connections intermittently, so make one direct HTTP/HTTPS attempt.
  for(scheme in arrayOf("http","https")){
   try{
    val c=(URL("$scheme://$ip/").openConnection() as HttpURLConnection).apply{
     connectTimeout=1200
     readTimeout=1200
     instanceFollowRedirects=false
     requestMethod="HEAD"
    }
    val code=c.responseCode
    c.disconnect()
    if(code in 100..599)return ProbeResult(true,System.currentTimeMillis()-started)
   }catch(_:Exception){}
  }

  // Repeat the TCP test once before declaring the device offline.
  for(port in ports){
   if(tcp(ip,port))return ProbeResult(true,System.currentTimeMillis()-started)
  }

  return ProbeResult(false,-1)
 }

 private fun tcp(ip:String,port:Int):Boolean{
  return try{
   Socket().use{it.connect(InetSocketAddress(ip,port),1000)}
   true
  }catch(_:Exception){false}
 }
}