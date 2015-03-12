package org.schat.deploy

import akka.actor.{Actor, ActorRef}

import org.schat.Logging
import org.schat.util.ActorLogReceive
 
class ChatManagerMasterActor(test:Int) extends Actor with ActorLogReceive with Logging {
  override def receiveWithLogging =  {
       case other => println("donothing")
  }
}
