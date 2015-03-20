package org.schat.starter

import org.schat.{Logging, SchatConf, SchatEnv}
import org.schat.util.Utils

private [schat] class SchatClient(conf: SchatConf, env:SchatEnv) extends Logging{

}

object SchatClient {
       def main(args: Array[String]) {
           Utils.checkHostPort( args(0), "Invalid Server Address") 
           val serverIP = args(0).split(":")(0)
           val serverPort = args(0).split(":")(1)

           Utils.checkHostPort( args(1), "Invalid Client Address") 
           val clientIP = args(1).split(":")(0)
           val clientPort = args(1).split(":")(1)


           val conf = new SchatConf(true)
           conf.set("schat.driver.host",serverIP)
           conf.set("schat.driver.port",serverPort)

           val env = SchatEnv.create( conf, clientIP, clientPort.toInt, false)
           new SchatClient(conf, env)
           Thread.currentThread.join()
       }
}

