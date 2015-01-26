package org.schat.util

import java.util.concurrent.ThreadFactory
import com.google.common.util.concurrent.ThreadFactoryBuilder

import org.schat.Logging

private[schat] object Utils extends Logging {
   
     private val daeminThreadFactoryBuilder: ThreadFactoryBuilder = 
             new ThreadFactoryBuilder().setDaemon(true)

     def namedThreadFactory(prefix: String):ThreadFactory = {
          daeminThreadFactoryBuilder.setNameFormat(prefix + "-%d").build()
     }

}
