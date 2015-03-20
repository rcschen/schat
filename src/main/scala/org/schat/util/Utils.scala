package org.schat.util
import java.net._
import scala.collection.JavaConversions._
import java.util.concurrent.ThreadFactory
import com.google.common.util.concurrent.ThreadFactoryBuilder

import org.schat.Logging

private[schat] object Utils extends Logging {
     private val portMaxRetries = 100

     private val daeminThreadFactoryBuilder: ThreadFactoryBuilder = 
             new ThreadFactoryBuilder().setDaemon(true)

     def namedThreadFactory(prefix: String):ThreadFactory = {
          daeminThreadFactoryBuilder.setNameFormat(prefix + "-%d").build()
     }

     private var customHostname: Option[String] = None
    
     lazy val localIpAddress: String = findLocalIpAddress()
     lazy val localIpAddressHostname: String = getAddressHostname(localIpAddress)

     def localHostName(): String = {
         customHostname.getOrElse(localIpAddressHostname)
     }

     def setCustomHostname(hostname:String){
         customHostname = Some(hostname)
     }

     def getAddressHostname(address: String): String = {
         //use ip address(address) to look up the host name
         InetAddress.getByName(address).getHostName
     }

     def findLocalIpAddress(): String = {
         val defaultIpOverride = System.getenv("SCHAT_LOCAL_IP") 
         if (defaultIpOverride != null) {
            defaultIpOverride
         } else {
           val address = InetAddress.getLocalHost
           if (address.isLoopbackAddress) {
              for (ni <- NetworkInterface.getNetworkInterfaces) { 
                      //getNetworkInterfaces needs import scala.collection.JavaConversions._
                 for (addr <- ni.getInetAddresses if ! addr.isLinkLocalAddress &&
                                                   ! addr.isLoopbackAddress &&
                                                   ! addr.isInstanceOf[Inet4Address]) {
                     return addr.getHostAddress
                 }
              }
           }
           address.getHostAddress
         }
     }

     def startServiceOnPort[T] ( startPort: Int,
                                 startService: Int =>(T, Int),
                                 serviceName: String ="",
                                 maxRetries:Int = portMaxRetries ): (T, Int) = {
         val serviceString = if (serviceName.isEmpty) "" else s" '$serviceName'"
         for ( offset <- 0 to maxRetries ) {
             val tryPort = if ( startPort == 0 ) startPort else (startPort + offset) % 65535
             try {
                val (service, port) = startService(tryPort)
                logInfo(s" Successfully started service $serviceString on port $port.")
                return (service, port)
             } catch {
                case e: Exception => if (tryPort >= maxRetries) {
                     logError(s"Service $serviceString is fialed to bind on port $tryPort")
                     throw e
                }
                logWarning(s"Service $serviceString could not bind on port $tryPort. "+ s"Attempting port ${tryPort + 1}.")
             }
         }
         return (null.asInstanceOf[T], -1)
     }
   
     def checkHost(host: String, message: String = "") {
         assert(host.indexOf(':') == -1, message)
     }

     def checkHostPort(hostPort: String, message: String = "") {
         assert(hostPort.indexOf(':') != -1, message)
     }

}
