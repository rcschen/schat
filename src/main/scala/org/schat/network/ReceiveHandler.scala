package org.schat.network

import java.nio.ByteBuffer
import org.schat.SchatConf

private[schat] object ReceiveHandler {
      def receivingOnCallBack( msg: Message, id: ConnectionManagerId) = {
           print("--------------------------------------------------------------------")
           print("Received [" + msg + "] from [" + id + "] at " + System.currentTimeMillis+":")
           val messageChunk =  msg.getChunkForReceiving(msg.size).getOrElse(null) 
           if (messageChunk != null) {
                val messageBuffer = messageChunk.buffer
                try {
                    //messageBuffer.flip
                    while(messageBuffer.remaining > 0) {
                           print(messageBuffer.get().toChar)
                    }
                    println()
                } catch {
                    case e: Exception => println("!!!!!buffer exception!!!!"+ e)
                } 
            }
            print("--------------------------------------------------------------------")
            val buffer = ByteBuffer.wrap("response".getBytes("utf-8"))
            Some(Message.createBufferMessage(buffer, msg.id))
      }
}

