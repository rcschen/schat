package org.schat.network

import java.net._
import java.nio._
import java.nio.channels._
import java.nio.channels.spi._
import org.schat.Logging

object ConnectionTest extends Logging{
       def main( args: Array[String] ) {
                println(s"you will connect to $args(0): $args(1)")
                assert( args.length > 1 )
                try { 
                     val address = new InetSocketAddress(InetAddress.getByName(args(0)), args(1).toInt) 
                     var con = SocketChannel.open().connect(address)
                }catch {
                     case e: Exception => logInfo("can not create address" +e)     
                }

       }
}
