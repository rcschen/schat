package org.schat.util

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.Await
import akka.actor.{ActorRef, ActorSystem, ExtendedActorSystem}
import akka.pattern.ask

import com.typesafe.config.ConfigFactory
import org.apache.log4j.{Level, Logger}


import org.schat.{Logging, SchatConf, SchatEnv}

private[schat] object AkkaUtils extends Logging {
       def createActorSystem(name: String,
                             host: String,
                             port: Int,
                             conf: SchatConf): (ActorSystem, Int) = {
           val startService: Int =>(ActorSystem, Int) = { actualPort =>
               doCreateActorSystem(name, host, actualPort, conf)
           }
           Utils.startServiceOnPort(port, startService, name)
       }
       def doCreateActorSystem( name: String,
                                host: String,
                                port: Int,
                                conf: SchatConf )= {
           logInfo("********"+port)

           val akkaThreads = conf.getInt("schat.akka.threads", 4)
           val akkaBatchSize = conf.getInt("schat.akka.batchSize", 15)
           val akkaTimeout = conf.getInt("schat.akka.timeout", 100)
           val akkaFrameSize = maxFrameSizeBytes(conf)
           val akkaLogLifecycleEvents = conf.getBoolean("schat.akka.logLifecycleEvents", false)
           val lifecycleEvents = if (akkaLogLifecycleEvents) "on" else "off"
           if (!akkaLogLifecycleEvents) {
               Option(Logger.getLogger("akka.remote.EndpointWriter")).map(l=>l.setLevel(Level.FATAL))
           }
           val logAkkaConfig = if (conf.getBoolean("schat.akka.logAkkaConfig", false)) "on" else "off"
           val akkaHeartBeatPauses = conf.getInt("schat.akka.heartbeat.pauses", 600)
           val akkaFailureDetector = conf.getDouble("schat.akka.failure-detector.threshold", 300.0)
           val akkaHeartBeatInterval = conf.getInt("schat.akka.heartbeat.interval", 1000)

           val akkaConf = ConfigFactory.parseMap(conf.getAkkaConf.toMap[String, String]).withFallback(
                          ConfigFactory.parseString(
             s"""
             |akka.daemonic = on
             |akka.loggers = [""akka.event.slf4j.Slf4jLogger""]
             |akka.stdout-loglevel = "ERROR"
             |akka.jvm-exit-on-fatal-error = off
             |akka.remote.require-cookie = off
             |akka.remote.secure-cookie = "" 
             |akka.remote.transport-failure-detector.heartbeat-interval = $akkaHeartBeatInterval s
             |akka.remote.transport-failure-detector.acceptable-heartbeat-pause = $akkaHeartBeatPauses s
             |akka.remote.transport-failure-detector.threshold = $akkaFailureDetector
             |akka.actor.provider = "akka.remote.RemoteActorRefProvider"
             |akka.remote.netty.tcp.transport-class = "akka.remote.transport.netty.NettyTransport"
             |akka.remote.netty.tcp.hostname = "$host"
             |akka.remote.netty.tcp.port = $port
             |akka.remote.netty.tcp.tcp-nodelay = on
             |akka.remote.netty.tcp.connection-timeout = $akkaTimeout s
             |akka.remote.netty.tcp.maximum-frame-size = ${akkaFrameSize}B
             |akka.remote.netty.tcp.execution-pool-size = $akkaThreads
             |akka.actor.default-dispatcher.throughput = $akkaBatchSize
             |akka.log-config-on-start = $logAkkaConfig
             |akka.remote.log-remote-lifecycle-events = $lifecycleEvents
             |akka.log-dead-letters = $lifecycleEvents
             |akka.log-dead-letters-during-shutdown = $lifecycleEvents
             """.stripMargin))
           logInfo("********"+akkaConf)
           val actorSystem = ActorSystem(name, akkaConf)
           logInfo("!!!!!!!!"+akkaConf)

           val provider = actorSystem.asInstanceOf[ExtendedActorSystem].provider
           val boundPort = provider.getDefaultAddress.port.get
           (actorSystem, boundPort)

       }
       def maxFrameSizeBytes(conf: SchatConf): Int = {
           conf.getInt("schat.akka.frameSize", 10) * 1024 * 1024
       }

       def makeDriverRef(name: String, conf: SchatConf, actorSystem: ActorSystem): ActorRef = {
           val driverActorSystemName = SchatEnv.driverActorSystemName
           var driverHost: String = conf.get("schat.driver.host", "spark1")
           var driverPort: Int = conf.getInt("schat.driver.port", 7077)
           Utils.checkHost(driverHost, "Excepted hostname")
           var url = s"akka.tcp://$driverActorSystemName@$driverHost:$driverPort/user/$name"
           val timeout = AkkaUtils.lookupTimeout(conf)
           logInfo(s"Connecting to $name: $url")
           Await.result(actorSystem.actorSelection(url).resolveOne(timeout), timeout)
       } 

       def lookupTimeout(conf: SchatConf): FiniteDuration = {
           Duration.create(conf.getLong("schat.akka.lookupTimeout", 30), "seconds")
       }
       def askTimeout(conf: SchatConf): FiniteDuration = {
           Duration.create(conf.getLong("spark.akka.askTimeout", 30), "seconds")
       }

       def numRetries(conf: SchatConf): Int = {
           conf.getInt("spark.akka.num.retries", 3)
       }

       def retryWaitMs(conf: SchatConf): Int = {
           conf.getInt("spark.akka.retry.wait", 3000)
       }
       def askWithReply[T] ( message: Any,
                           actor: ActorRef,
                           retryAttempts: Int,
                           retryInterval: Int,
                           timeout: FiniteDuration ): T = {
          if (actor == null) {
              throw new Exception("Error sending message as driverActor is null [messate: "+  message+ " ]")
          }
          var attempts = 0
          var lastException: Exception = null
          while( attempts < retryAttempts ) {
                 attempts += 1
                 try {
                        var future = actor.ask(message)(timeout) // require import akka.pattern.ask
                        var result = Await.result(future ,timeout)
                        if (result == null) {
                             throw new Exception("Actor returned null")
                        }
                        return result.asInstanceOf[T]

                 } catch {
                        case ie: InterruptedException => throw ie
                        case e: Exception => {
                                              lastException = e
                                              logWarning("Error sending message in " + attempts + " attempts", e)
                        }
                 }
                 Thread.sleep( retryInterval )
          } 
          throw new Exception("Error sending message [ message="+ message +"]")
       } 

}
