package org.schat.network

import java.nio.ByteBuffer
import org.schat.SchatConf

private[schat] object ReceiveHandler {
      def receivingOnCallBack( msg: Message, id: ConnectionManagerId) = {
           println("-----123---------------------------------------------------------------"+msg.size)
           println("Received [" + msg + "] from [" + id + "] at " + System.currentTimeMillis+":")
           val messageChunk =  msg.getChunkForReceiving(msg.size).getOrElse(null) 
           if (messageChunk != null) {
                val messageBuffer = messageChunk.buffer
                try {
                    //messageBuffer.flipi
                    println("REMAINNNNNNNNNNN"+messageBuffer.remaining)

                    while(messageBuffer.remaining > 0) {
                           print(messageBuffer.get().toChar)
                    }
                } catch {
                    case e: Exception => println("!!!!!buffer exception!!!!"+ e)
                } 
            }
            println()
            println("--------------------------------------------------------------------")
            val buffer = ByteBuffer.wrap("response".getBytes("utf-8"))
            Some(Message.createBufferMessage(buffer, msg.id))
      }
}

