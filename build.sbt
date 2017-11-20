name := """play-java"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.11"

libraryDependencies += javaJdbc
libraryDependencies += cache
libraryDependencies += javaWs



// https://mvnrepository.com/artifact/org.kurento/kurento-client
libraryDependencies += ("org.kurento" % "kurento-client" % "6.5.0")

// https://mvnrepository.com/artifact/org.kurento/kurento-utils-js
libraryDependencies += "org.kurento" % "kurento-utils-js" % "6.6.1"

// https://mvnrepository.com/artifact/org.webjars/webjars-locator
libraryDependencies += "org.webjars" % "webjars-locator" % "0.32"

// https://mvnrepository.com/artifact/org.webjars.bower/bootstrap
libraryDependencies += "org.webjars.bower" % "bootstrap" % "3.3.7"

// https://mvnrepository.com/artifact/org.webjars.bower/demo-console
libraryDependencies += "org.webjars.bower" % "demo-console" % "1.5.1"

// https://mvnrepository.com/artifact/org.webjars.bower/adapter.js
libraryDependencies += "org.webjars.bower" % "adapter.js" % "3.1.6"

// https://mvnrepository.com/artifact/org.webjars.bower/jquery
libraryDependencies += "org.webjars.bower" % "jquery" % "3.1.1"

// https://mvnrepository.com/artifact/org.webjars.bower/ekko-lightbox
libraryDependencies += "org.webjars.bower" % "ekko-lightbox" % "4.0.2"

libraryDependencies += "org.eclipse.jetty" % "jetty-util" % "9.3.0.M2"

libraryDependencies += "org.eclipse.jetty" % "jetty-io" % "9.3.0.M2"