package org.schat.network

import java.net.{InetSocketAddress, InetAddress}
import java.nio.ByteBuffer

private [schat] class MessageChunkHeader ( val typ: Long, 
                                           val id: Int,
                                           val totalSize: Int,
                                           val chunkSize: Int,
                                           val other: Int,
                                           val hasError: Boolean,
                                           val securityNeg: Int,
                                           val address: InetSocketAddress ) {

     lazy val buffer = {
              val ip = address.getAddress.getAddress()
              val port = address.getPort()
              ByteBuffer.allocate(MessageChunkHeader.HEADER_SIZE).
                         putLong(typ).
                         putInt(id).
                         putInt(totalSize).
                         putInt(chunkSize).
                         putInt(other).
                         put( if (hasError) 1.asInstanceOf[Byte] else 0.asInstanceOf[Byte]).
                         putInt(securityNeg).
                         putInt(ip.size).
                         put(ip).
                         putInt(port).
                         position(MessageChunkHeader.HEADER_SIZE).
                         flip
     }
   
     override def toString = ""+ this.getClass.getSimpleName + ":" + id + " of type " + typ + 
                             " and sizes " + totalSize + " / " + chunkSize + " bytes, securityNeg: " + 
                             securityNeg

}


private[schat] object MessageChunkHeader {
       val HEADER_SIZE = 45

       def create(buffer: ByteBuffer): MessageChunkHeader = {
           if (buffer.remaining != HEADER_SIZE) {
              throw new IllegalArgumentException("Cannot convert buffer data to Message")
           }
           val typ = buffer.getLong()
           val id = buffer.getInt()
           val totalSize = buffer.getInt()
           val chunkSize = buffer.getInt()
           val other = buffer.getInt()
           val hasError = buffer.get() != 0
           val securityNeg = buffer.getInt()
           val ipSize = buffer.getInt()
           val ipBytes = new Array[Byte](ipSize)
           buffer.get(ipBytes)
           val ip = InetAddress.getByAddress(ipBytes)
           val port = buffer.getInt()
           new MessageChunkHeader(typ, 
                                  id, 
                                  totalSize, 
                                  chunkSize, 
                                  other, 
                                  hasError, 
                                  securityNeg, 
                                  new InetSocketAddress(ip, port))
       }
}
