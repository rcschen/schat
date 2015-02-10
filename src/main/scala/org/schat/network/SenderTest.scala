
package org.schat.network

import java.nio.ByteBuffer
import org.schat.SchatConf

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

private[schat] object SenderTest {
  def main(args: Array[String]) {

    if (args.length < 4) {
      println("Usage: SenderTest <target host> <target port>")
      System.exit(1)
    }

    val targetHost = args(0)
    val targetPort = args(1).toInt
    val targetConnectionManagerId = new ConnectionManagerId(targetHost, targetPort)
    val conf = new SchatConf
    val manager = new ConnectionManager(0, conf, null)
    println("Started connection manager with id = " + manager.id)

    manager.onReceiveMessage((msg: Message, id: ConnectionManagerId) => {
      println("==Received [" + msg + "] from [" + id + "]==")
      None
    })

    //val size =  100 * 1024  * 1024
    val size = args(3).toInt
    val buffer = ByteBuffer.allocate(size).put(Array.tabulate[Byte](size)(x => x.toByte))
    buffer.flip

    val targetServer = args(0)

    val count = args(2).toInt
    (0 until count).foreach(i => {
      println("*** the "+i+" times sending *****")
      val dataMessage = Message.createBufferMessage(buffer.duplicate)

      val startTime = System.currentTimeMillis
      println("########### Started timer at " + startTime + " ############# "+dataMessage) 
      val promise = manager.sendMessageReliably(targetConnectionManagerId, dataMessage)
      println("*** Already sent *****") 

      val responseStr: String = Try(Await.result(promise, Duration.Inf))
        .map { response =>
          val buffer = response.asInstanceOf[BufferMessage].buffers(0)
          new String(buffer.array, "utf-8")
        }.getOrElse("none")
      println("!!!!!!!!!!! Already sent regist responseStr and !!!!!!!!!") 

      val finishTime = System.currentTimeMillis
      val mb = size / 1024.0 / 1024.0
      val ms = finishTime - startTime
      // val resultStr = "Sent " + mb + " MB " + targetServer + " in " + ms + " ms at " + (mb / ms
      //  * 1000.0) + " MB/s"
      val resultStr = "Sent " + mb + " MB " + targetServer + " in " + ms + " ms (" +
        (mb / ms * 1000.0).toInt + "MB/s) | Response = " + responseStr
      println(resultStr)
    })
  }
}
