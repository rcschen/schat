package org.schat.network

import java.net.InetSocketAddress

private[schat] class ConnectionId(host: String, port: Int) {
  assert (port > 0)
  def getSocketAddress() = new InetSocketAddress(host, port)
  def connectionId = host + "_" + port
} 
