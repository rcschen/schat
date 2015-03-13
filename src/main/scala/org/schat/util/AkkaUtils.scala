package org.schat.util

import akka.actor.{ActorRef, ActorSystem}


import org.schat.{Logging, SchatConf, SchatEnv}

private[schat] object AkkaUtils extends Logging {
       def createActorSystem(name: String,
                             host: String,
                             port: Int,
                             conf: SchatConf): (ActorSystem, Int) = {(null, 0)}
       def makeDriverRef(name: String, conf: SchatConf, actorSystem: ActorSystem): ActorRef = {null} 
}
