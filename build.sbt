
name := "schat"

version := "0.0.0"

scalaVersion :="2.11.1"

libraryDependencies ++= {
       val slf4jVersion = "1.7.5"
       Seq("log4j" % "log4j" % "1.2.17" % "compile->default",
           "org.slf4j" % "slf4j-api" % slf4jVersion % "compile->default",
           "org.slf4j" % "slf4j-log4j12" % slf4jVersion % "compile->default")
}

