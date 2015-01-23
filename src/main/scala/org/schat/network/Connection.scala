package org.schat.network

import java.nio._
import java.nio.channels._
import org.schat.Logging
private [schat] abstract class Connection(val channel:SocketChannel,
                                          val selector:Selector,
                                          val socketRemoteConnectionManagerId: ConnectionManagerId, 
                                          val connectionId: ConnectionId ) extends Logging {

}

