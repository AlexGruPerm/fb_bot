name := "fb_bot_project"

ThisBuild / organization := "yakushev"
ThisBuild / version      := "0.6.0"
ThisBuild / scalaVersion := "2.12.15"

/**
* projects:
*   fbparser - parser of fonbet data, save into db using db, transitive depends on common through db
*   tbot     - telegram bot that send recomendations to users, transitive depends on common through db
*   db        - library for work with postgres db, depends on common
*   common   - common case classes and code
*/
lazy val global = project
  .in(file("."))
  .settings(commonSettings)
  //.disablePlugins(AssemblyPlugin)
  .aggregate(
    common,
    db,
    fbparser,
    tbot
  )

lazy val common = (project in file("common"))
  .settings(
    name := "common",
    commonSettings,
    libraryDependencies ++= dependenciesCommon.tsConfig
  )

lazy val db = (project in file("db"))
  .settings(
    name := "db",
    commonSettings,
    libraryDependencies ++= dependenciesPg.deps
  )
  .dependsOn(common)

lazy val tbot = (project in file("tbot"))
  .settings(
    assembly / assemblyJarName := "tbot.jar",
    mainClass / run := Some("app.Bot"),
    name := "tbot",
    commonSettings,
    libraryDependencies ++= dependenciesTbot.deps
  )
  .dependsOn(db)

lazy val fbparser = (project in file("fbparser"))
  .settings(
    assembly / assemblyJarName := "fbparser.jar",
    mainClass / run := Some("app.Parser"),
    name := "fbparser",
    commonSettings,
    libraryDependencies ++= dependenciesFbParser.deps
  )
 .dependsOn(db)

//********************************************************************************
// Dependencies for project common.

lazy val dependenciesCommon =
  new {
    val tsConfig = List("com.typesafe" % "config" % "1.4.2")
  }


//********************************************************************************
// Dependencies for project db.

val VersPg = new {
  val zio  = "2.0.0-RC6"
  val pg   = "42.4.0"
}

lazy val dependenciesPg =
  new {
    val zio = "dev.zio" %% "zio" % VersPg.zio
    val zio_logging = "dev.zio" %% "zio-logging" % VersPg.zio
    val zioDep = List(zio, zio_logging)
    val pg_driver = Seq("org.postgresql" % "postgresql" % VersPg.pg)
    val deps = pg_driver ++ zioDep
  }

//********************************************************************************
// Dependencies for project fbparser.

val VersFbp = new {
  val zio  = "2.0.0-RC6" // todo: update to 2.0.2
  val zioSttp = "3.8.0" //"3.6.2"  // todo: update to 3.8.0
  val Circe = "0.14.2"
  val circeOptics = "0.14.1"
  val slf4jvers = "2.0.0"
  val logbackvers = "1.2.3"//"1.4.1"
  //val zioLog = "2.1.1"

  //val zioLogSlf4j = "2.1.1"
  val zioLogSlf4j = "0.4.0"
}

lazy val dependenciesFbParser =
  new {
    val logback = "ch.qos.logback" % "logback-classic" % VersFbp.logbackvers

    val zio = "dev.zio" %% "zio" % VersFbp.zio
    val zio_logging = "dev.zio" %% "zio-logging" % VersFbp.zio

    val zio_logg_slf4j    =  "dev.zio" % "zio-logging-slf4j_2.12" % VersFbp.zioLogSlf4j
    //val zio_logg_slf4j    =  "dev.zio" %% "zio-logging-slf4j" % "2.0.1"//"2.1.1"
                           //"dev.zio" %% "zio-logging-slf4j" % VersFbp.zioLogSlf4j //.zio


    val zio_sttp       = "com.softwaremill.sttp.client3" %% "zio" % VersFbp.zioSttp
    val zio_sttp_async = "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % VersFbp.zioSttp
    val zio_sttp_circe = "com.softwaremill.sttp.client3" %% "circe" % VersFbp.zioSttp

    val zioDep = List(zio, zio_logging,zio_logg_slf4j, zio_sttp, zio_sttp_async, zio_sttp_circe)

    val circe_libs = Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser",
      "io.circe" %% "circe-literal"
    ).map(_ % VersFbp.Circe) ++ Seq("io.circe" %% "circe-optics" % VersFbp.circeOptics)

    val deps =
      List(logback) ++
      zioDep ++
      circe_libs
  }

//********************************************************************************
// Dependencies for project tbot.

val VersTbot = new {
  val zio            = "2.0.0"
  val zioTsConf      = "3.0.1"
  val zhttp          = "2.0.0-RC10"
  val zioInteropCats = "22.0.0.0"
  val sttp           = "3.7.4"
  val bot4s          = "5.6.0"
}

lazy val dependenciesTbot =
  new {
    val zio = "dev.zio" %% "zio" % VersTbot.zio
    val zhttp = "io.d11" %% "zhttp" % VersTbot.zhttp
    val ZioIoCats = "dev.zio" %% "zio-interop-cats" % VersTbot.zioInteropCats
    val zio_config_typesafe = "dev.zio" %% "zio-config-typesafe" % VersTbot.zioTsConf

    val zioDep = List(zio, zhttp, ZioIoCats, zio_config_typesafe)

    val zio_sttp = "com.softwaremill.sttp.client3" %% "zio" % VersTbot.sttp
    val sttp_client_backend_zio = "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % VersTbot.sttp

    val sttpDep = List(zio_sttp, sttp_client_backend_zio)

    val bot4s_core = "com.bot4s" %% "telegram-core" %  VersTbot.bot4s
    val bot4s_akka = "com.bot4s" %% "telegram-akka" %  VersTbot.bot4s

    val bot4slibs = List(bot4s_core,bot4s_akka)

    val deps = zioDep ++
      bot4slibs ++
      sttpDep
  }

//********************************************************************************

  lazy val compilerOptions = Seq(
          "-deprecation",
          "-encoding", "utf-8",
          "-explaintypes",
          "-feature",
          "-unchecked",
          "-language:postfixOps",
          "-language:higherKinds",
          "-language:implicitConversions",
          "-Xcheckinit",
          "-Xfatal-warnings",
          "-Ywarn-unused:params,-implicits"
  )

  lazy val commonSettings = Seq(
    scalacOptions ++= compilerOptions,
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      Resolver.sonatypeRepo("snapshots"),
      Resolver.sonatypeRepo("public"),
      Resolver.sonatypeRepo("releases"),
      Resolver.DefaultMavenRepository,
      Resolver.mavenLocal,
      Resolver.bintrayRepo("websudos", "oss-releases")
    )
  )

  addCompilerPlugin("org.scalamacros" %% "paradise" % "2.1.1" cross CrossVersion.full)

  common / assembly / assemblyMergeStrategy := {
    case "module-info.class"|"resources/control.conf"|"resources/mts.p12"|"resources/mts.pem" => MergeStrategy.discard
    case "plugin.properties" => MergeStrategy.last
    case "log4j.properties" => MergeStrategy.last
    case "logback.xml" => MergeStrategy.last
    case "resources/logback.xml" => MergeStrategy.last
    case "resources/application.conf" => MergeStrategy.last
    case "resources/reference.conf" => MergeStrategy.last
    case "application.conf" => MergeStrategy.last
    case PathList("application.conf") => MergeStrategy.concat
    case PathList("reference.conf") => MergeStrategy.concat
    //case "resources/control.conf" => MergeStrategy.discard
    case "reference.conf" => MergeStrategy.concat
    case "control.conf" => MergeStrategy.discard
    case x if x.contains("io.netty.versions.properties") => MergeStrategy.discard
    case PathList("META-INF", "services", xs @ _*) => MergeStrategy.first
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
    //case "module-info.class"           => MergeStrategy.discard
    case x => MergeStrategy.first
  }

  db / assembly / assemblyMergeStrategy := {
    case "module-info.class"|"resources/control.conf"|"resources/mts.p12"|"resources/mts.pem" => MergeStrategy.discard
    case "plugin.properties" => MergeStrategy.last
    case "log4j.properties" => MergeStrategy.last
    case "logback.xml" => MergeStrategy.last
    case "resources/logback.xml" => MergeStrategy.last
    case "resources/application.conf" => MergeStrategy.last
    case "resources/reference.conf" => MergeStrategy.last
    case "application.conf" => MergeStrategy.last
    case PathList("application.conf") => MergeStrategy.concat
    case PathList("reference.conf") => MergeStrategy.concat
    //case "resources/control.conf" => MergeStrategy.discard
    case "reference.conf" => MergeStrategy.concat
    case "control.conf" => MergeStrategy.discard
    case x if x.contains("io.netty.versions.properties") => MergeStrategy.discard
    case PathList("META-INF", "services", xs @ _*) => MergeStrategy.first
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
    //case "module-info.class"           => MergeStrategy.discard
    case x => MergeStrategy.first
  }

  tbot / assembly / assemblyMergeStrategy := {
    case "module-info.class"|"resources/control.conf"|"resources/mts.p12"|"resources/mts.pem" => MergeStrategy.discard
    case "plugin.properties" => MergeStrategy.last
    case "log4j.properties" => MergeStrategy.last
    case "logback.xml" => MergeStrategy.last
    case "resources/logback.xml" => MergeStrategy.last
    case "resources/application.conf" => MergeStrategy.last
    case "resources/reference.conf" => MergeStrategy.last
    case "application.conf" => MergeStrategy.last
    case PathList("application.conf") => MergeStrategy.concat
    case PathList("reference.conf") => MergeStrategy.concat
    //case "resources/control.conf" => MergeStrategy.discard
    case "reference.conf" => MergeStrategy.concat
    case "control.conf" => MergeStrategy.discard
    case x if x.contains("io.netty.versions.properties") => MergeStrategy.discard
    case PathList("META-INF", "services", xs @ _*) => MergeStrategy.first
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
    //case "module-info.class"           => MergeStrategy.discard
    case x => MergeStrategy.first
  }

  fbparser / assembly / assemblyMergeStrategy := {
    case "module-info.class"|"resources/control.conf"|"resources/mts.p12"|"resources/mts.pem" => MergeStrategy.discard
    case "plugin.properties" => MergeStrategy.last
    case "log4j.properties" => MergeStrategy.last
    case "logback.xml" => MergeStrategy.last
    case "resources/logback.xml" => MergeStrategy.last
    case "resources/application.conf" => MergeStrategy.last
    case "resources/reference.conf" => MergeStrategy.last
    case "application.conf" => MergeStrategy.last
    case PathList("application.conf") => MergeStrategy.concat
    case PathList("reference.conf") => MergeStrategy.concat
    //case "resources/control.conf" => MergeStrategy.discard
    case "reference.conf" => MergeStrategy.concat
    case "control.conf" => MergeStrategy.discard
    case x if x.contains("io.netty.versions.properties") => MergeStrategy.discard
    case PathList("META-INF", "services", xs @ _*) => MergeStrategy.first
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
    //case "module-info.class"           => MergeStrategy.discard
    case x => MergeStrategy.first
  }
