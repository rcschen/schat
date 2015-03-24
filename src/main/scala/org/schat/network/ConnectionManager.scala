package org.schat.network

import java.io.IOException
import java.nio._
import java.nio.channels._
import java.nio.channels.spi._
import java.net._
import java.util.concurrent.{LinkedBlockingDeque, TimeUnit, ThreadPoolExecutor}
import java.util.concurrent.atomic.AtomicInteger
import java.util.{Timer, TimerTask}

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.collection.mutable.{ArrayBuffer, HashMap, SynchronizedMap}
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
   
   class MessageStatus( val message: Message,
                        val connectionManagerId: ConnectionManagerId,
                        completionHandler: MessageStatus => Unit ) {

         var ackMessage: Option[Message] = None
         def markDone(ackMessage: Option[Message]) {
             this.ackMessage = ackMessage
             completionHandler(this)
         }
   }

   private val ackTimeoutMonitor = new Timer("AckTimeoutMonitor", true)
   private val idCount: AtomicInteger = new AtomicInteger(1)
   private val registerRequests = new SynchronizedQueue[SendingConnection]
   private val connectionsByKey = new HashMap[ SelectionKey, Connection ] with SynchronizedMap[ SelectionKey, Connection ]
   private val keyInterestChangeRequests = new SynchronizedQueue[(SelectionKey, Int)]
   private var onReceiveCallback: (BufferMessage, ConnectionManagerId) => Option[Message] = null
   private val ackTimeout = conf.getInt("schat.core.connection.ack.wait.timeout", 60)
   private val messageStatuses = new HashMap[Int, MessageStatus]
   private val connectionsById = new HashMap[ConnectionManagerId, SendingConnection]
   private val writeRunnableStarted: HashSet[SelectionKey] = new HashSet[SelectionKey]()
   private val readRunnableStarted: HashSet[SelectionKey] = new HashSet[SelectionKey]()

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

   private val selectorThread = new Thread("connection-manager-thread") {
       override def run() = ConnectionManager.this.run()
   }

   private val serverChannel = ServerSocketChannel.open()

   serverChannel.configureBlocking(false)
   serverChannel.socket.setReuseAddress(true)
   serverChannel.socket.setReceiveBufferSize(256 * 1024)
  

   Utils.startServiceOnPort[ServerSocketChannel] (port, startService, name) 
   serverChannel.register(selector, SelectionKey.OP_ACCEPT)
   val id = new ConnectionManagerId(Utils.localHostName, serverChannel.socket.getLocalPort)
   selectorThread.setDaemon(true)
   selectorThread.start()
 
   private def startService(port: Int): (ServerSocketChannel, Int) = {
       // if port is 0, the bound port will be random number else it will be the specified port
       serverChannel.socket.bind(new InetSocketAddress(port))
       logInfo(">>>>>>>> "+port+" <<<<<<<<<"+"==vs==>"+serverChannel.socket.getLocalPort)
       (serverChannel, serverChannel.socket.getLocalPort)
   }

   def run()  {
       try {
            logInfo("!!!Start to run selectorThread daemon!!!1"+ selectorThread.isInterrupted)
            while( !selectorThread.isInterrupted ) {   
                logInfo("!!!daemon>>>>>>>>check whether registerRequests is Empty!!!2"+registerRequests)
                while( !registerRequests.isEmpty ) {
                     val conn : SendingConnection = registerRequests.dequeue()
                     addListeners(conn)
                     conn.connect()
                     addConnection(conn)
                } 

                logInfo("!!!daemon>>>>>>>>check registerRequests is NOT Empty!!!3")
                while(!keyInterestChangeRequests.isEmpty) {
                     val (key, ops) = keyInterestChangeRequests.dequeue()
                     try {
                       if (key.isValid) {
                          val connection = connectionsByKey.getOrElse(key, null)
                          if (connection != null) {
                              val lastOps = key.interestOps()
                              key.interestOps(ops)
                              logInfo("!!!daemon>>>>>>>>key is valid and change InterestOps!!!4")
                              if (isTraceEnabled()) {

                                 def intToOpStr(op: Int): String = {
                                     val opStrs = ArrayBuffer[String]()
                                     if ((op & SelectionKey.OP_READ) != 0) opStrs += "READ"
                                     if ((op & SelectionKey.OP_WRITE) != 0) opStrs += "WRITE"
                                     if ((op & SelectionKey.OP_CONNECT) != 0) opStrs += "CONNECT"
                                     if ((op & SelectionKey.OP_ACCEPT) != 0) opStrs += "ACCEPT"
                                     if (opStrs.size > 0) opStrs.reduceLeft(_ + " | " + _) else " "
                                 }

                                 logInfo("Changed key for connection to [" +
                                     connection.getRemoteConnectionManagerId()  + "] changed from [" +
                                     intToOpStr(lastOps) + "] to [" + intToOpStr(ops) + "]")
                              }
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
                val selectedKeysCount = try {
                       logInfo("!!!daemon>>>>>>>>selectedKeysCount starts to block!!!5")
                       logInfo("!!!Connection Manager Daemon is started already!!!before")
                       selector.select()
                       logInfo("!!!Connection Manager Daemon is started already!!!after")
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
                                                    logInfo("Exception processing key " + key, e)
                                                    triggerForceCloseByException(key, e)
                                            }
                                     }
                               }
                       }
                       0
                }

                if (selectedKeysCount == 0) {
                   logDebug("Selector selected " + 
                             selectedKeysCount + " of " + selector.keys.size + " keys")
                }

                if (selectorThread.isInterrupted) {
                   logInfo("Selector thread was interrupted!")
                   return
                }
                logInfo("!!!daemon>>>>>>>>ARE YOU READY?!!!6")
   
                if ( 0 != selectedKeysCount ) {
                       logInfo("!!!daemon>>>>>>>>start to run interest!!!7")
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
            logInfo("selector Daemon is started")
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

   def addConnection( connection: Connection) {
       logInfo("connections :"+ connectionsByKey)
       connectionsByKey += (( connection.key, connection))
   }

   def changeConnectionKeyInterest(connection: Connection, ops: Int) {
       keyInterestChangeRequests += ((connection.key, ops))
       logInfo("YYYYYYes 1 here to wake up Selector for "+ connection.key+" : " + ops)
       wakeupSelector()
   }  

   def handleConnectionError( connection: Connection, e:Exception) {
       logInfo("Handling connection error on connection to " +
       connection.getRemoteConnectionManagerId())
       removeConnection(connection)

   }
   def removeConnection(connection: Connection) {
       logInfo("RRRRRRRRRRRRRRRRRovvvvve hhhhherererererere---------111--"+connection.key)
    	    connectionsByKey -= connection.key
       logInfo("RRRRRRRRRRRRRRRRRovvvvve hhhhherererererere---------222--"+connectionsByKey)


    try {
      connection match {
        case sendingConnection: SendingConnection =>
          val sendingConnectionManagerId = sendingConnection.getRemoteConnectionManagerId()
          logInfo("Removing SendingConnection to " + sendingConnectionManagerId)

          connectionsById -= sendingConnectionManagerId
          //connectionsAwaitingSasl -= connection.connectionId

          messageStatuses.synchronized {
            messageStatuses.values.filter(_.connectionManagerId == sendingConnectionManagerId)
              .foreach(status => {
                logInfo("Notifying " + status)
                status.markDone(None)
              })

            messageStatuses.retain((i, status) => {
              status.connectionManagerId != sendingConnectionManagerId
            })
          }
        case receivingConnection: ReceivingConnection =>
          val remoteConnectionManagerId = receivingConnection.getRemoteConnectionManagerId()
          logInfo("Removing ReceivingConnection to " + remoteConnectionManagerId)

          val sendingConnectionOpt = connectionsById.get(remoteConnectionManagerId)
          if (!sendingConnectionOpt.isDefined) {
            logError(s"Corresponding SendingConnection to ${remoteConnectionManagerId} not found")
            return
          }

          val sendingConnection = sendingConnectionOpt.get
          connectionsById -= remoteConnectionManagerId
          sendingConnection.close()

          val sendingConnectionManagerId = sendingConnection.getRemoteConnectionManagerId()

          assert(sendingConnectionManagerId == remoteConnectionManagerId)

          messageStatuses.synchronized {
            for (s <- messageStatuses.values
                 if s.connectionManagerId == sendingConnectionManagerId) {
              logInfo("Notifying " + s)
              s.markDone(None)
            }

            messageStatuses.retain((i, status) => {
              status.connectionManagerId != sendingConnectionManagerId
            })
          }
        case _ => logError("Unsupported type of connection.")
      }
    } finally {
      // So that the selection keys can be removed.
      wakeupSelector()
    }

   } 
   def wakeupSelector() {
       logInfo("YYYYYYes 2 here to wake up Selector" )

       selector.wakeup()
   }

   
   def receiveMessage(connection: Connection, message: Message) {
       val connectionManagerId = ConnectionManagerId.fromSocketAddress(message.senderAddress)
       logInfo("--------Received [" + message + "] from [" + connectionManagerId + "]")
       val runnable = new Runnable() {
           val creationTime = System.currentTimeMillis
           def run() {
               handleMessage(connectionManagerId, message, connection)
           }
       }
       handleMessageExecutor.execute(runnable)
   } 
   
   def handleMessage( connectionManagerId: ConnectionManagerId,
                      message: Message,
                      connection: Connection ) {
       logInfo("Handling [" + message + "] from [" + connectionManagerId + "]")
       message match {
           case bufferMessage: BufferMessage => {
                if (bufferMessage.hasAckId()) {
                    messageStatuses.synchronized {
                         messageStatuses.get(bufferMessage.ackId) match {
                               case Some(status) => {
                                   messageStatuses -= bufferMessage.ackId
                                   status.markDone(Some(message))
                               }
                               case None => {
                                   logWarning(s"Could not find reference for received ack Message ${message.id}")
                               }
                         }
                    }
                } else {
                    var ackMessage : Option[Message] = None
                    try {
                         ackMessage = if(onReceiveCallback != null) {
                            onReceiveCallback(bufferMessage, connectionManagerId)
                         } else {
                            logInfo("Not calling back as callback is null")
                            None
                         }
                         if (ackMessage.isDefined) {
                             if (!ackMessage.get.isInstanceOf[BufferMessage]) {
                                 logInfo("Response to " + bufferMessage 
                                 + " is not a buffer message, it is of type "
                                 + ackMessage.get.getClass)
                             } else if (!ackMessage.get.asInstanceOf[BufferMessage].hasAckId) {
                                 logInfo("Response to " + bufferMessage + " does not have ack id set")
                                 ackMessage.get.asInstanceOf[BufferMessage].ackId = bufferMessage.id
                             }
                         } 
                    } catch {
                         case e: Exception => { 
                               logError(s"Exception was thrown while processing message", e)
                               val m = Message.createBufferMessage(bufferMessage.id)
                               m.hasError = true
                               ackMessage = Some(m)
                         }
                    } finally {
                         sendMessage(connectionManagerId, ackMessage.getOrElse{
                                 Message.createBufferMessage(bufferMessage.id)
                         })     
                    }
                } 
           } 
           case _ => throw new Exception("Unknown type message received")
       }

   }
   def triggerConnect (key : SelectionKey ) {
       
       val conn = connectionsByKey.getOrElse(key, null).asInstanceOf[SendingConnection]
       if (conn == null) {
           logDebug("No Such conection in selector:"+ key)
           return
       }

       conn.changeConnectionKeyInterest(0)

       handleConnectExecutor.execute( new Runnable {
             override def run() {
                   var tries: Int = 10
                   while( tries >= 0 ) {
                          if( conn.finishConnect(false) ) return
                          Thread.sleep(1)
                          tries -= 1
                   }
                   conn.finishConnect(true)

             }
       })

   }

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
   def triggerWrite ( key: SelectionKey ) {
       val conn = connectionsByKey.getOrElse(key, null)
       if (conn == null ) return

       writeRunnableStarted.synchronized {
           if (conn.changeInterestForWrite()) conn.unregisterInterest()
           if (writeRunnableStarted.contains(key)) return
           writeRunnableStarted += key
       }  

       handleReadWriteExecutor.execute( new Runnable {
            override def run() {
                  var register: Boolean = false
                  try {
                      register = conn.write()
                  } finally {
                      writeRunnableStarted.synchronized {
                            writeRunnableStarted -= key
                            val needReregister = register || conn.resetForceReregister()
                            if (needReregister && conn.changeInterestForWrite()) {
                               conn.registerInterest()
                            }
                      }
                  }
            }
       })
 
   }
  
 
   private def triggerForceCloseByException( key: SelectionKey, e: Exception) { 
           logDebug(" triggerForceCloseByException is triggered "+ key + " e:"+e)
           // to be done 
   }
   def onReceiveMessage(callback: (Message, ConnectionManagerId) => Option[Message]) {
       onReceiveCallback = callback
   }
   def sendMessageReliably(connectionManagerId: ConnectionManagerId, 
                           message: Message) : Future[Message] = {
       val promise = Promise[Message]()
       val timeoutTask = new TimerTask {
           override def run(): Unit ={
             messageStatuses.synchronized {
                messageStatuses.remove(message.id).foreach( s=> {
                         promise.failure( new IOException("sendMessageReliably failed because ack "+
                                          s"was not received within $ackTimeout sec"))
                })
             }
           }
       }
 
       val status = new MessageStatus(message, connectionManagerId, s => {
           timeoutTask.cancel()
           s.ackMessage match {
                case None => promise.failure(new IOException("sendMessageReliably failed without being ACK'd"))
                case Some(ackMessage) =>
                     if(ackMessage.hasError) {
                         new IOException("sendMessageReliably failed with ACK that signalled a remote error")
                     } else {
                         promise.success(ackMessage)
                     }
           }
       }) 
       messageStatuses.synchronized { 
           messageStatuses += ((message.id, status))
       }
       ackTimeoutMonitor.schedule(timeoutTask, ackTimeout * 1000)
       logInfo("SSSSendMessageReliably===")
       sendMessage(connectionManagerId, message)
       promise.future
   }

   private def sendMessage(connectionManagerId: ConnectionManagerId, message: Message) {
       def startNewConnection(): SendingConnection = {
           val inetSocketAddress = new InetSocketAddress( connectionManagerId.host,
                                                          connectionManagerId.port)
           val newConnectionId = new ConnectionId( id, idCount.getAndIncrement.intValue )
           val newConnection = new SendingConnection( inetSocketAddress, 
                                                      selector, 
                                                      connectionManagerId, 
                                                      newConnectionId )
           registerRequests.enqueue(newConnection)
           newConnection
       }
       val connection = connectionsById.getOrElseUpdate(connectionManagerId, startNewConnection())
       message.senderAddress = id.toSocketAddress()
       connection.send(message)
       wakeupSelector()
   }

}
