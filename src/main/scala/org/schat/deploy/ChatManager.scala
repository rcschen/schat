package org.schat.deploy
import akka.actor.{ActorSystem, Props}
import org.schat.{Logging, SchatConf}
private[schat] class ChatManager(chatManagerMaster: ChatManagerMaster, actorSystem:  ActorSystem, conf: SchatConf) extends Logging {
       private val slaveActor = actorSystem.actorOf(
               Props(new ChatManagerSlaveActor(this)), "ChatManagerSlaveActor"
       )
       private val slave = new ChatManagerSlave(slaveActor, conf)  
}
