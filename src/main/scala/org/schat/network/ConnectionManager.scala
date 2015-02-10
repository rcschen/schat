package org.schat.network

import java.nio._
import java.nio.channels._
import java.nio.channels.spi._
import java.net._
import java.util.concurrent.{LinkedBlockingDeque, TimeUnit, ThreadPoolExecutor}
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.mutable.{HashMap, SynchronizedMap}
import scala.collection.mutable.SynchronizedQueue
import scala.collection.mutable.HashSet
import scala.concurrent.{Future}
import scala.collection.JavaConverters._

import org.schat.util.Utils
import org.schat.{Logging, SchatConf}

private[schat] class ConnectionManager(
        port: Int,
        conf: SchatConf,
        name: String="Default name") extends Logging {

   private val idCount: AtomicInteger = new AtomicInteger(1)
   private val selector = SelectorProvider.provider.openSelector()
   private val connectionsByKey = new HashMap[ SelectionKey, Connection ] with SynchronizedMap[ SelectionKey, Connection ]
   private val keyInterestChangeRequests = new SynchronizedQueue[(SelectionKey, Int)]
   private var onReceiveCallback: (BufferMessage, ConnectionManagerId) => Option[Message] = null

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

   Utils.startServiceOnPort[ServerSocketChannel] (port, startService, name) 
   serverChannel.register(selector, SelectionKey.OP_ACCEPT)
   val id = new ConnectionManagerId(Utils.localHostName, serverChannel.socket.getLocalPort)

   private val selectorThread = new Thread("connection-manager-thread") {
       override def run() = ConnectionManager.this.run()
   }

   logInfo("selector Daemon is override")
   selectorThread.setDaemon(true)
   selectorThread.start()
   logInfo("selector Daemon is started")
  
   def run()  {
       try {
            logInfo("!!!Start to run selectorThread daemon!!!"+ selectorThread.isInterrupted)
            while( !selectorThread.isInterrupted ) {    

                logInfo("!!!Connection Manager Daemon is started already!!!")
                val selectedKeysCount = try {
                       selector.select()
                } catch {
                       case e: CancelledKeyException => {
                               val allKeys = selector.keys().iterator()
                               while(allKeys.hasNext) {
                                     val key = allKeys.next()
                                     try{
                                            if ( !key.isValid ) {
                                                 logInfo("Key not valid? "+ key)
                                                 throw new CancelledKeyException()
                                            }  
                                     } catch {
                                            case e: CancelledKeyException => {
                                                    logInfo("key already cancelled ? " + key, e)
                                                    triggerForceCloseByException(key, e)
                                            }
                                            case e: Exception => {
                                                    logError("Exception processing key " + key, e)
                                                    triggerForceCloseByException(key, e)
                                            }
                                     }
                               }
                       }
                       0
                }

                logInfo("selectedKeysCount is " +selectedKeysCount)

                if (selectedKeysCount == 0) {
                   logDebug("Selector selected " + selectedKeysCount + " of " + selector.keys.size + " keys")
                }

                if (selectorThread.isInterrupted) {
                   logInfo("Selector thread was interrupted!")
                   return
                }
    
                if ( 0 != selectedKeysCount ) {
                       val selectedKeys = selector.selectedKeys().iterator()
                       while ( selectedKeys.hasNext )  {
                               val key = selectedKeys.next
                               selectedKeys.remove()
                               try {
                                   if (key.isValid) {
                                         if( key.isAcceptable ) {
                                             logInfo("-in daemon--5-1---key->isAcceptable")
                                             acceptConnection(key)
                                         } else 
                                         if( key.isConnectable) {
                                             logInfo("-in daemon--5-2----key->isConnectable")
                                             triggerConnect(key)
                                         } else
                                         if( key.isReadable) {
                                             logInfo("-in daemon--5-3---key->isReadable")
                                             triggerRead(key)
                                         } else
                                         if( key.isWritable) {
                                             logInfo("-in daemon--5-4---key->isWriteable")
                                             triggerWrite(key)
                                         }
                                   } else {
                                         logInfo("Key not valid ? " + key)
                                         throw new CancelledKeyException()
                                   }
                               } catch {
                                     case e: CancelledKeyException => {
                                             logInfo("key already cancelled ? " + key, e)
                                             triggerForceCloseByException(key, e)
                                     }
                                     case e: Exception => {
                                             logError("Exception processing key " + key, e)
                                             triggerForceCloseByException(key, e)
                                     }

                               }                    
                       }
 
                }

                                                
            }
       } catch {
            case e: Exception => logError("Error in select loop", e)
       }
    }
   def acceptConnection ( key: SelectionKey ) {
       val sc = key.channel.asInstanceOf[ServerSocketChannel] 
       var newChannel = sc.accept()
       logInfo("here is newChannel "+newChannel)
       while(newChannel != null ) {
            logInfo("In accapt loop: "+ newChannel)
            try {
                 val newConnectionId = new ConnectionId(id, idCount.getAndIncrement.intValue )
                 val newConnection   = new ReceivingConnection(newChannel, selector, newConnectionId)
                 newConnection.onReceive(receiveMessage)
                 addListeners(newConnection)
                 addConnection(newConnection)
                 logInfo("Accepted connection from [" + newConnection.remoteAddress + "]")
            } catch {
                 case e: Exception => logError("Error in accept loop", e)
            }
            newChannel = sc.accept()
       }
   }

   def addListeners( connection: Connection ) {
       connection.onKeyInterestChange(changeConnectionKeyInterest)
       connection.onException(handleConnectionError)
       connection.onClose(removeConnection)
   }

   def changeConnectionKeyInterest(connection: Connection, ops: Int) {
       keyInterestChangeRequests += ((connection.key, ops))
       wakeupSelector()
   }  

   def handleConnectionError( connection: Connection, e:Exception) {}
   def removeConnection(connection: Connection) {} 
   def wakeupSelector() {
       selector.wakeup()
   }

   def addConnection( connection: Connection) {
       logInfo("connections :"+ connectionsByKey)
       connectionsByKey += (( connection.key, connection))
   }
   
   def receiveMessage(connection: Connection, message: Message) {} 
   def triggerConnect (key : SelectionKey ) {}

   private val readRunnableStarted: HashSet[SelectionKey] = new HashSet[SelectionKey]()
   def triggerRead ( key: SelectionKey ) {
       logInfo("Start to trigger Read" + key)
       val conn = connectionsByKey.getOrElse(key, null)
       if ( conn == null ) return

       readRunnableStarted.synchronized {
           if ( conn.changeInterestForRead() ) conn.unregisterInterest()
           if ( readRunnableStarted.contains(key) ) {
                return
           }
           readRunnableStarted += key
       }
     
       handleReadWriteExecutor.execute ( new Runnable {
             override def run() {
                  var register: Boolean =false
                  try {
                    register = conn.read()
                  } finally {
                       readRunnableStarted.synchronized {
                              readRunnableStarted -= key
                              if ( register && conn.changeInterestForRead() ) {
                                   conn.registerInterest()
                              }
                       }
                  }
             }
       })

   }
   def triggerWrite ( key: SelectionKey ) {}
  
 
   private def triggerForceCloseByException( key: SelectionKey, e: Exception) { 
           logDebug(" triggerForceCloseByException is triggered "+ key + " e:"+e)
           // to be done 
   }
   def onReceiveMessage(callback: (Message, ConnectionManagerId) => Option[Message]) {
       onReceiveCallback = callback
   }
   def sendMessageReliably(connectionManagerId: ConnectionManagerId, 
                           message: Message) : Future[Message] = {
       null       
   }

   while(true){
     //selector.wakeup()
   }
}
