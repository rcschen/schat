package org.schat.network

import java.net._
import java.nio._
import java.nio.channels._
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
        def read(): Boolean = { 
            throw new UnsupportedOperationException(
            "Cannot read on connection of type " + this.getClass.toString)
        }
    
}

private [schat] class ReceivingConnection( channel_  : SocketChannel,
                                           selector_ : Selector,
                                           id_       : ConnectionId ) extends Connection ( channel_, selector_, id_){
        channel.register(selector, SelectionKey.OP_READ) 
        var onReceiveCallback : (Connection, Message) =>Unit  = null
         
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
        override def read(): Boolean = {
           true       
        }
      
} 
