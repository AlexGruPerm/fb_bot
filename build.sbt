name := "fb_bot_project"

ThisBuild / organization := "yakushev"
ThisBuild / version      := "0.4.0"
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
  .disablePlugins(AssemblyPlugin)
  .aggregate(
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
    name := "tbot",
    commonSettings,
    libraryDependencies ++= dependenciesTbot.deps
  )
  .dependsOn(db)

lazy val fbparser = (project in file("fbparser"))
  .settings(
    assembly / assemblyJarName := "fbparser.jar",
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
  val zio  = "2.0.0-RC6"
  val zioSttp = "3.6.2"
  val Circe = "0.14.2"
  val circeOptics = "0.14.1"
  val slf4jvers = "2.0.0"
  val logbackvers = "1.4.0"//"1.2.3"
}

lazy val dependenciesFbParser =
  new {
    val zio = "dev.zio" %% "zio" % VersFbp.zio
    val zio_logging = "dev.zio" %% "zio-logging" % VersFbp.zio

    val zio_sttp       = "com.softwaremill.sttp.client3" %% "zio" % VersFbp.zioSttp
    val zio_sttp_async = "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % VersFbp.zioSttp
    val zio_sttp_circe = "com.softwaremill.sttp.client3" %% "circe" % VersFbp.zioSttp

    //val tsConfig = List("com.typesafe" % "config" % "1.4.2")

    val zioDep = List(zio, zio_logging, zio_sttp, zio_sttp_async, zio_sttp_circe)

    val circe_libs = Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser",
      "io.circe" %% "circe-literal"
    ).map(_ % VersFbp.Circe) ++ Seq("io.circe" %% "circe-optics" % VersFbp.circeOptics)

    // https://mvnrepository.com/artifact/org.slf4j/slf4j-api
    //val slf4j = "org.slf4j" % "slf4j-api" % VersFbp.slf4jvers
    val logback = "ch.qos.logback" % "logback-classic" % VersFbp.logbackvers

    val deps = zioDep ++
      //tsConfig ++
      circe_libs ++
      List(logback/*,slf4j*/)
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


  db / assembly / assemblyMergeStrategy := {
    case PathList("module-info.class") => MergeStrategy.discard
    case x if x.endsWith("/module-info.class") => MergeStrategy.discard
    case PathList("META-INF", xs @ _*)         => MergeStrategy.discard
    case "reference.conf" => MergeStrategy.concat
    case _ => MergeStrategy.first
  }

  tbot / assembly / assemblyMergeStrategy := {
    case PathList("module-info.class") => MergeStrategy.discard
    case x if x.endsWith("/module-info.class") => MergeStrategy.discard
    case PathList("META-INF", xs @ _*)         => MergeStrategy.discard
    case "reference.conf" => MergeStrategy.concat
    case _ => MergeStrategy.first
  }

  fbparser / assembly / assemblyMergeStrategy := {
    case PathList("module-info.class") => MergeStrategy.discard
    case x if x.endsWith("/module-info.class") => MergeStrategy.discard
    case PathList("META-INF", xs @ _*)         => MergeStrategy.discard
    case "reference.conf" => MergeStrategy.concat
    case _ => MergeStrategy.first
  }

/*
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "plugin.properties" => MergeStrategy.last
  case "log4j.properties" => MergeStrategy.last
  case "logback.xml" => MergeStrategy.last
  case "resources/logback.xml" => MergeStrategy.last
  case "resources/application.conf" => MergeStrategy.last
  case "resources/reference.conf" => MergeStrategy.last
  case "application.conf" => MergeStrategy.last
  case PathList("application.conf") => MergeStrategy.concat
  case PathList("reference.conf") => MergeStrategy.concat
  case "resources/control.conf" => MergeStrategy.discard
  case "control.conf" => MergeStrategy.discard
  case "resources/mtspredbot.pem" => MergeStrategy.discard
  case "YOURPUBLIC.pem" => MergeStrategy.discard
  case x => MergeStrategy.first
}
*/