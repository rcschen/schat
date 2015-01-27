package org.schat.network

import java.net._

private [schat] class ConnectionManagerId(host: String, port:Int){
  assert (port > 0)
  def toSocketAddress = new InetSocketAddress(host, port)
}

