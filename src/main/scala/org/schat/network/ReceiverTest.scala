
package org.schat.network

import java.nio.ByteBuffer
import org.schat.SchatConf

private[schat] object ReceiverTest {
  def main(args: Array[String]) {
    val conf = new SchatConf
    val manager = new ConnectionManager(9999, conf, null)
    println("Started connection manager with id = " + manager.id)

    manager.onReceiveMessage((msg: Message, id: ConnectionManagerId) => {
      println("----------------------------------------------------------------")

      println("Received [" + msg + "] from [" + id + "] at " + System.currentTimeMillis)
      println( "message Size ::"+msg.size)
      val messageChunk =  msg.getChunkForReceiving(msg.size).getOrElse(null) 
      println("??????"+messageChunk)
      if (messageChunk != null) {
          val messageBuffer = messageChunk.buffer
          try {
              //messageBuffer.flip
              println("REMAINNNNNNNNNNN"+messageBuffer.remaining)
              while(messageBuffer.remaining > 0) {
                    println("-------GET-->---->"+messageBuffer.get())
              }
          } catch {
              case e: Exception => println("!!!!!buffer exception!!!!"+ e)
          } 
      }
      val buffer = ByteBuffer.wrap("response".getBytes("utf-8"))
      Some(Message.createBufferMessage(buffer, msg.id))
    })
    println("start to be in standBy")
    Thread.currentThread.join()
  }
}

