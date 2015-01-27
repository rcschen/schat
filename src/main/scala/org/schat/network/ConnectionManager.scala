package org.schat.network

import java.nio._
import java.nio.channels._
import java.nio.channels.spi._
import java.net._
import java.util.concurrent.{LinkedBlockingDeque, TimeUnit, ThreadPoolExecutor}

import org.schat.util.Utils
import org.schat.{Logging, SchatConf}

private[schat] class ConnectionManager(
        port: Int,
        conf: SchatConf,
        name: String="Default name") extends Logging {

   private val selector = SelectorProvider.provider.openSelector()

   private val handleMessageExecutor = new ThreadPoolExecutor(
           conf.getInt("schart.network.connection.handler.threads.min", 20),
           conf.getInt("schart.network.connection.handler.threads.max", 60), 
           conf.getInt("schart.network.connection.handler.threads.keeplive", 60),
           TimeUnit.SECONDS,
           new LinkedBlockingDeque[Runnable](),
           Utils.namedThreadFactory("handle-message-executor"))

   private val handleReadWriteExecutor = new ThreadPoolExecutor(
           conf.getInt("schart.network.connection.handler.threads.min", 4),
           conf.getInt("schart.network.connection.handler.threads.max", 32), 
           conf.getInt("schart.network.connection.handler.threads.keeplive", 60),
           TimeUnit.SECONDS,
           new LinkedBlockingDeque[Runnable](),
           Utils.namedThreadFactory("handle-read-write-executor"))

   private val handleConnectExecutor = new ThreadPoolExecutor(
           conf.getInt("schart.network.connection.handler.threads.min", 1),
           conf.getInt("schart.network.connection.handler.threads.max", 8), 
           conf.getInt("schart.network.connection.handler.threads.keeplive", 60),
           TimeUnit.SECONDS,
           new LinkedBlockingDeque[Runnable](),
           Utils.namedThreadFactory("handle-connect-executor"))

   private val serverChannel = ServerSocketChannel.open()


   serverChannel.configureBlocking(false)
   serverChannel.socket.setReuseAddress(true)
   serverChannel.socket.setReceiveBufferSize(256 * 1024)
  
   private def startService(port: Int): (ServerSocketChannel, Int) = {
       serverChannel.socket.bind(new InetSocketAddress(port))
       (serverChannel, serverChannel.socket.getLocalPort)
   }
   
   serverChannel.register(selector, SelectionKey.OP_ACCEPT)
   val id = new ConnectionManagerId(Utils.localHostName, serverChannel.socket.getLocalPort)

   private val selectorThread = new Thread("connection-manager-thread") {
       override def run() = ConnectionManager.this.run()
   }
   selectorThread.setDaemon(true)
   selectorThread.start()
   
   def run() = {}
}
