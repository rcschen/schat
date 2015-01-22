package org.schat

class LogTest() extends Logging {
  println("start LogTest")
  def showLog() {
   logInfo("print Log ok")
   println("should print log!!")
  }
}

object LogTest extends Logging {
 def main(args:Array[String]){
   logInfo("object print log ok")
   val test = new LogTest()
   test.showLog()
   println("!!!!!!!!!")
 }
}

