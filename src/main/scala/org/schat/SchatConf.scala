package org.schat

import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap

class SchatConf(loadDefaults :Boolean) extends Cloneable with Logging {

    import SchatConf._

    def this() = this(true)

    private [schat] val settings = new HashMap[String, String]()

    if (loadDefaults) {
         //System.getProperties.asScala need import scala.collection.JavaConverters._
         for((k, v) <- System.getProperties.asScala if k.startsWith("Schat.")) {
                settings(k) = v
         }
    }
  
    def getInt( key: String, defaultValue: Int):Int = {
          settings.get(key).map(_.toInt).getOrElse(defaultValue)
    }
        
}

private [schat] object SchatConf {
}
