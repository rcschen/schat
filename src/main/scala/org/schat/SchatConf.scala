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
    def getAll: Array[(String, String)] = settings.clone().toArray

    def getAkkaConf: Seq[(String, String)] = getAll.filter{ case (k, _) => isAkkaConf(k)}        
    def set(key: String, value: String): SchatConf = {
         if (key == null) {
             throw new NullPointerException("null key")
         }
         if (value == null) {
             throw new NullPointerException("null value")
         }
         settings(key) = value
         this
    } 
}

private [schat] object SchatConf {
        def isAkkaConf(name: String): Boolean = name.startsWith("akka.")
}
