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
    def getOption(key:String):Option[String] = {
          settings.get(key)
    }  
    def getInt( key: String, defaultValue: Int):Int = {
        getOption(key).map(_.toInt).getOrElse(defaultValue)
    }
    def getLong(key:String, defaultValue: Long):Long= {
        getOption(key).map(_.toLong).getOrElse(defaultValue)
    }
    def getBoolean(key:String, defaultValue: Boolean): Boolean = {
        getOption(key).map(_.toBoolean).getOrElse(defaultValue)
    }
    def getDouble(key: String, defaultValue: Double): Double = {
        getOption(key).map(_.toDouble).getOrElse(defaultValue)
    }
    def getAll: Array[(String, String)] = settings.clone().toArray
    def get(key: String): String = {
        settings.getOrElse(key, throw new NoSuchElementException(key))
    }
    def get(key: String, defaultValue: String): String = {
        settings.getOrElse(key, defaultValue)
    }

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
