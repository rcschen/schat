package org.schat.deploy

import akka.actor._
import org.schat.{Logging, SchatConf}
import org.schat.util.AkkaUtils

class ChatManagerSlave(chatManagerSlaveActor: ActorRef, conf: SchatConf) extends Logging {
      private val AKKA_RETRY_ATTEMPTS: Int = AkkaUtils.numRetries(conf)
      private val AKKA_RETRY_INTERVAL_MS: Int = AkkaUtils.retryWaitMs(conf)
      val timeout = AkkaUtils.askTimeout(conf)

      private def askExecutorWithReply[T] (message: Any): T = {
              AkkaUtils.askWithReply(message, chatManagerSlaveActor,AKKA_RETRY_ATTEMPTS, AKKA_RETRY_INTERVAL_MS, timeout ) 
      } 
}
