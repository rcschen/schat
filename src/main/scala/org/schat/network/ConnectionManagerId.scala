package org.schat.network

import java.net._

private [schat] class ConnectionManagerId(host: String, port:Int){
  println("!!!!!!!"+port)
  assert (port > 0)
  def toSocketAddress = new InetSocketAddress(host, port)
}

