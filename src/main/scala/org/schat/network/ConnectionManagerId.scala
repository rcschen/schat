package org.schat.network

import java.net._

private [schat] case class ConnectionManagerId(host: String, port:Int){
  assert (port > 0)
  def toSocketAddress() = new InetSocketAddress(host, port)
}

private [schat] object ConnectionManagerId {
  def fromSocketAddress(socketAddress: InetSocketAddress): ConnectionManagerId = {
      new ConnectionManagerId(socketAddress.getHostName, socketAddress.getPort)
  }
}
