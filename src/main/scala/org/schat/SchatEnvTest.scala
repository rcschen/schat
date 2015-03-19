package org.schat
import scala.collection.GenTraversableOnce

object SchatEnvTest {
       def main(args: Array[String]) {
           println("HHHHere We goooooo!!")
           val conf = new SchatConf(true)  
           conf.set("schat.driver.host",args(0))
           conf.set("schat.driver.port",args(1))
           if(args(2)=="true") {
              SchatEnv.create(conf, args(0), args(1).toInt, true)
           } else {
              SchatEnv.create(conf, args(3), args(4).toInt, false)
           }
           println("---------------------")
           while(true){}

       }       
}
