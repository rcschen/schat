package org.schat.network

import java.nio.ByteBuffer
import scala.collection.mutable.ArrayBuffer
import org.schat.Logging

private[schat] class BufferMessage( id_ : Int, 
                     val buffers: ArrayBuffer[ByteBuffer], 
                     var ackId: Int) extends Message(Message.BUFFER_MESSAGE, id_) with Logging {
      val initialSize = currentSize()
      var gotChunkForSendingOnce = false

      def size = initialSize

      def currentSize() = {
          if (buffers == null || buffers.isEmpty) {
              0
          } else {
              buffers.map(_.remaining).reduceLeft(_+_)
          }
      }
      def flip() {
          buffers.foreach(_.flip)
      }
      def hasAckId() = (ackId !=0 )
      def isCompletelyReceived() = !buffers(0).hasRemaining
      def getChunkForSending(maxChunkSize: Int): Option[MessageChunk] = {
          if ( maxChunkSize <=0 ) {
               throw new Exception("Max chunk size is "+maxChunkSize)
          }
          val security = if (isSecurityNeg) 1 else 0
          if (size == 0 && !gotChunkForSendingOnce) {
              val newChunk = new MessageChunk( new MessageChunkHeader(typ, id, 0, 0, ackId, hasError, security, senderAddress), null)
              gotChunkForSendingOnce = true
              return Some(newChunk)
          }
          while(!buffers.isEmpty) {
              val buffer = buffers(0)
              if (buffer.remaining == 0) {
                  logInfo("buffer is not remaining, BlockManager.dispose(buffer)")
                  buffers-=buffer
              } else {
                  val newBuffer = if (buffer.remaining <= maxChunkSize) {
                      buffer.duplicate()
                  } else {
                      buffer.slice().limit(maxChunkSize).asInstanceOf[ByteBuffer]
                  }
                  buffer.position(buffer.position+newBuffer.remaining)
                 
                  val newChunk = new MessageChunk( new MessageChunkHeader(typ, id, size, newBuffer.remaining, ackId, hasError, security, senderAddress), newBuffer )
                 
                  gotChunkForSendingOnce = true
                  return Some(newChunk)
              }
          }
          None
      }
      def getChunkForReceiving(chunkSize: Int): Option[MessageChunk] = {
          if (buffers.size > 1) {
              throw new Exception("Attempting to get chunk from message with multiple data buffers")
          }
          val buffer = buffers(0)
          val security = if(isSecurityNeg) 1 else 0
          if (buffer.remaining > 0) {
              if (buffer.remaining < chunkSize) {
                  throw new Exception("Not enough space in data buffer for receiving chunk")
              }
              val newBuffer = buffer.slice().limit(chunkSize).asInstanceOf[ByteBuffer]
              buffer.position(buffer.position+newBuffer.remaining)
              val newChunk = new MessageChunk( new MessageChunkHeader( typ, id, size, newBuffer.remaining, ackId, hasError, security, senderAddress), newBuffer)
              return Some(newChunk)
          }
          None 
      }

}

