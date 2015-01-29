package org.schat.network

import org.schat._

object ConnectionManagerTest {
   def main(args: Array[String]) {
      new ConnectionManager(10, new SchatConf(true), "This is a test") 
   }
}
