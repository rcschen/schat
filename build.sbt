
name := "schat"

version := "0.0.0"

scalaVersion :="2.10.5"

libraryDependencies ++= {
       val slf4jVersion = "1.7.5"
       val guavaVersion = "14.0.1"
       val akkaVersion = "2.3.9"
       Seq("log4j" % "log4j" % "1.2.17" % "compile->default",
           "org.slf4j" % "slf4j-api" % slf4jVersion % "compile->default",
           "org.slf4j" % "slf4j-log4j12" % slf4jVersion % "compile->default",
           "com.google.guava" % "guava" % guavaVersion % "compile->default",
           "com.typesafe.akka" % "akka-actor_2.10" % akkaVersion % "compile->default")
}

