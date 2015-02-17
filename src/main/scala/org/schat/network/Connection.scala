package org.schat.network

import java.net._
import java.nio._
import java.nio.channels._
import scala.collection.mutable.{ArrayBuffer, HashMap, Queue}
import org.schat.Logging

private [schat] abstract class Connection(val channel:SocketChannel,
                                          val selector:Selector,
                                          val socketRemoteConnectionManagerId: ConnectionManagerId, 
                                          val connectionId: ConnectionId ) extends Logging {
        def this(channel_ : SocketChannel, selector_ : Selector, id_ : ConnectionId) {
            this( channel_, 
                  selector_,
                  ConnectionManagerId.fromSocketAddress( channel_.socket.getRemoteSocketAddress.asInstanceOf[InetSocketAddress] ),
                  id_ ) 
        }

        var onCloseCallback: Connection => Unit = null
        var onExceptionCallback: (Connection, Exception) => Unit = null
        var onKeyInterestChangeCallback: (Connection, Int) => Unit = null
        @volatile private var closed = false

        channel.configureBlocking(false)
        channel.socket.setTcpNoDelay(true)
        channel.socket.setReuseAddress(true)
        channel.socket.setKeepAlive(true)
   
        def changeInterestForRead(): Boolean
        def registerInterest(): Unit
        def unregisterInterest(): Unit
        def remoteAddress = getRemoteAddress()
        def getRemoteAddress()= channel.socket.getRemoteSocketAddress().asInstanceOf[InetSocketAddress]
        def key() = channel.keyFor(selector)
        def onKeyInterestChange(callback: (Connection, Int) => Unit ) {
            onKeyInterestChangeCallback = callback
        }
        def onException(callback: (Connection, Exception) => Unit) {
            onExceptionCallback = callback
        }
        def onClose(callback: Connection => Unit) {
            onCloseCallback = callback
        }
        def changeConnectionKeyInterest(ops: Int) {
            if (onKeyInterestChangeCallback != null ) {
                onKeyInterestChangeCallback(this, ops)
            } else {
                throw new Exception("OnKeyInterestChangeCallback not registered")
            }
        }
        def getRemoteConnectionManagerId(): ConnectionManagerId = socketRemoteConnectionManagerId
     
        def callOnExceptionCallback(e: Exception) {
            if (onExceptionCallback != null ) {
                onExceptionCallback(this, e)
            } else {
                logError("Error in connection to " + getRemoteConnectionManagerId(), e)
            }
        }
    
        def callOnCloseCallback() {
            if (onCloseCallback != null) {
              onCloseCallback(this)
            } else {
              logWarning("Connection to " + getRemoteConnectionManagerId()) 
            }
        } 

        
        def read(): Boolean = { 
            throw new UnsupportedOperationException(
            "Cannot read on connection of type " + this.getClass.toString)
        }
     
        def close() {
            closed = true
            val k = key()
            if (k != null) {
                k.cancel()
            }
            channel.close()
            //disposeSasl()
            callOnCloseCallback()
        }
}    

private [schat] class SendingConnection(val address: InetSocketAddress, 
                                        selector_ : Selector, 
                                        remoteId_ : ConnectionManagerId, 
                                        id_ : ConnectionId)  extends Connection(SocketChannel.open, 
                                                                                selector_, 
                                                                                remoteId_, 
                                                                                id_) {
        private class Outbox {
            val messages = new Queue[Message]()
            val defaultChunkSize = 65536
            var nextMessageToBeUsed = 0
            def addMessage(message: Message) {
                messages.synchronized {
                       messages.enqueue(message)
                       logInfo("Add ["+ message +"] to outbox for sending to [" + getRemoteConnectionManagerId() +"]")
                }
            }
            def getChunk():Option[ MessageChunk ] = {
                messages.synchronized {
                     while( !messages.isEmpty ) {
                             val message = messages.dequeue()
                             val chunk = message.getChunkForSending(defaultChunkSize)
                             if ( chunk.isDefined ) {
                                  messages.enqueue(message)
                                  nextMessageToBeUsed = nextMessageToBeUsed + 1
                                  if ( !message.started ) {
                                       logDebug( "Starting to send [" + message + "] to [" + getRemoteConnectionManagerId() + "]" )
                                       message.started = true
                                       message.startTime = System.currentTimeMillis
                                  }
                                  return chunk
                             } else {
                                   message.finishTime = System.currentTimeMillis
                                   logDebug("Finished sending [" + message + "] to [" + getRemoteConnectionManagerId() + "] in "  + message.timeTaken )
                             }
                     }
                }
                None
            }
        }
        private val outbox = new Outbox()
        private var needForceReregister = false

        def changeInterestForRead(): Boolean = true
        def registerInterest(): Unit={}
        def unregisterInterest(): Unit={}

        def send( message:Message ) {
            outbox.synchronized { 
                outbox.addMessage(message)
                needForceReregister = true
            }
            if (channel.isConnected) { 
                registerInterest()
            }
        }
   
}

private [schat] class ReceivingConnection( channel_  : SocketChannel,
                                           selector_ : Selector,
                                           id_       : ConnectionId ) extends Connection ( channel_, selector_, id_){

        var onReceiveCallback : (Connection, Message) =>Unit  = null
        var currentChunk: MessageChunk = null
        class Inbox() {
              val messages = new HashMap[Int, BufferMessage]()

              def getChunk(header: MessageChunkHeader) : Option[MessageChunk] = {
                  def createNewMessage: BufferMessage = {
                      val newMessage = Message.create(header).asInstanceOf[BufferMessage]
                      newMessage.startTime = System.currentTimeMillis
                      newMessage.isSecurityNeg = header.securityNeg == 1
                      messages += ((newMessage.id, newMessage))
                      newMessage
                  }
                  val message = messages.getOrElseUpdate(header.id, createNewMessage)
                  message.getChunkForReceiving(header.chunkSize) 
              }

        }
        val inbox = new Inbox()

        channel.register(selector, SelectionKey.OP_READ) 
        
        def onReceive( callback: (Connection, Message ) => Unit) {
            onReceiveCallback = callback
        }
        override def changeInterestForRead(): Boolean = true
        override def registerInterest() {
              changeConnectionKeyInterest(SelectionKey.OP_READ)
        }
        override def unregisterInterest() {
              changeConnectionKeyInterest(0)
        }
 
        val headerBuffer: ByteBuffer = ByteBuffer.allocate(MessageChunkHeader.HEADER_SIZE)
        override def read(): Boolean = {
              try {
                while(true) {
                    if (currentChunk == null ) {
                        val headerBytesRead = channel.read(headerBuffer)
                        if (headerBytesRead == -1) {
                            close()
                            return false
                        }
                        if (headerBuffer.remaining > 0) {
                            return true
                        }
                        headerBuffer.flip
                        if (headerBuffer.remaining != MessageChunkHeader.HEADER_SIZE) {
                            throw new Exception("Unexcepted ("+headerBuffer.remaining+") in the header")
                        }
                        val header = MessageChunkHeader.create( headerBuffer )
                        headerBuffer.clear()
                        //processConnectionManagerId(header)
                        header.typ match {
                               case Message.BUFFER_MESSAGE => {}
                               case _=> throw new Exception("Message of unknown type received")
                        }
                        
                        val bytesRead = channel.read(currentChunk.buffer)
                        if (bytesRead == 0) {
                             return true
                        } else if(bytesRead == -1) {
                             close()
                             return false
                        } 
                        if (currentChunk.buffer.remaining == 0) {
                        } 
 
                    }
                }
              } catch {
                case e: Exception => {
                     logWarning("Error reading from connection to " + getRemoteConnectionManagerId(), e)
                     callOnExceptionCallback(e)
                     close()
                     return false
         
                }
              } 
              true       
        }
      
} 
