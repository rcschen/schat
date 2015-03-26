package org.schat.deploy
import akka.actor.{ActorSystem, Props}

private[schat] class ChatManager(chatManagerMaster: ChatManagerMaster, actorSystem:  ActorSystem) {
       private val slaveActor = actorSystem.actorOf(
               Props(new ChatManagerSlaveActor(this)), "ChatManagerSlaveActor"
       )
       private val slave = new ChatManagerSlave(slaveActor)  
}
