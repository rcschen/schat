package org.schat.starter

import org.schat.{Logging, SchatConf, SchatEnv}
import org.schat.util.Utils

private [schat] class SchatServer(conf: SchatConf, env:SchatEnv) extends Logging{

}

object SchatServer {
       def main(args: Array[String]) {
           Utils.checkHostPort( args(0), "Invalid Address") 
           val serverIP = args(0).split(":")(0)
           val serverPort = args(0).split(":")(1)
           val conf = new SchatConf(true)
           val env = SchatEnv.create( conf, serverIP, serverPort.toInt, true)
           new SchatServer(conf, env)
           Thread.currentThread.join()
       }
}

