package org.schat

import akka.actor._

import org.schat.Logging
import org.schat.util.AkkaUtils
import org.schat.deploy._

class SchatEnv () {}

object SchatEnv extends Logging {
        private val env = new ThreadLocal[SchatEnv]
        private var lastSetSchatEnv: SchatEnv = _
        def set(e: SchatEnv) {
            lastSetSchatEnv = e
            env.set(e)
        }
        def get: SchatEnv = {
            Option(env.get()).getOrElse(lastSetSchatEnv)
        }
        private[schat] val driverActorSystemName = "schatDriver"
        private[schat] val executorActorSystemName = "schatExecutor"

        private [schat] def create( conf: SchatConf,
                                    hostname: String,
                                    port: Int,
                                    isDriver:Boolean ) : SchatEnv = {

             val actorSystemName = if(isDriver) driverActorSystemName else executorActorSystemName
             val (actorSystem, boundPort) = AkkaUtils.createActorSystem( actorSystemName,
                                                                         hostname,
                                                                         port, 
                                                                         conf )
                                  
             if (isDriver) {
                 conf.set("spark.driver.port", boundPort.toString)
             }
             def registerOrLookup(name: String, newActor: => Actor): ActorRef = {
                 if(isDriver) {
                    actorSystem.actorOf(Props(newActor), name = name)
                 } else {
                    AkkaUtils.makeDriverRef(name, conf, actorSystem)
                 }
             }
             val schatManagerMaster = new ChatManagerMaster(registerOrLookup( 
                                                             "SchatManagerMaster",
                                                             new ChatManagerMasterActor()))
             val schatManager = new ChatManager( schatManagerMaster )
             new SchatEnv()
        } 
}
