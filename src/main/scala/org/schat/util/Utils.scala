package org.schat.util
import java.net._
import scala.collection.JavaConversions._
import java.util.concurrent.ThreadFactory
import com.google.common.util.concurrent.ThreadFactoryBuilder

import org.schat.Logging

private[schat] object Utils extends Logging {
   
     private val daeminThreadFactoryBuilder: ThreadFactoryBuilder = 
             new ThreadFactoryBuilder().setDaemon(true)

     private var customHostname: Option[String] = None

     lazy val localIpAddress: String = findLocalIpAddress()
     lazy val localIpAddressHostname: String = getAddressHostname(localIpAddress)

     def namedThreadFactory(prefix: String):ThreadFactory = {
          daeminThreadFactoryBuilder.setNameFormat(prefix + "-%d").build()
     }

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
}
