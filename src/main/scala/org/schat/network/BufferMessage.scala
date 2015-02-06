package org.schat.network

import java.nio.ByteBuffer
import scala.collection.mutable.ArrayBuffer


private[schat]
class BufferMessage( id_ : Int, 
                     val buffers: ArrayBuffer[ByteBuffer], 
                     var ackId: Int) extends Message(Message.BUFFER_MESSAGE, id_) {
      def size = {1}
      def getChunkForSending(maxChunkSize: Int): Option[MessageChunk] = {null}
      def getChunkForReceiving(chunkSize: Int): Option[MessageChunk] = {null}

}

