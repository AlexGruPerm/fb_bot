package app

import com.typesafe.config.{Config, ConfigFactory}
import common.{AppConfig, ConfigHelper}
import org.slf4j.LoggerFactory
import service.{DbConnection, PgConnectionImpl}
import services.{FbDownloader, FbDownloaderImpl}
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend/*, SttpClient*/}
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.logging.LogFormat
//import zio.logging.backend.SLF4J
import zio.{Chunk, Clock, Console, ExitCode, Layer, RLayer, Schedule, Scope, Task, ULayer, URLayer, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer, durationInt}

import java.io
import java.io.File
import java.time.Duration

object Parser extends ZIOAppDefault {

  //private val logger = SLF4J.slf4j

  val log = LoggerFactory.getLogger(getClass.getName)

  //todo: rename it's not a bot
  val configBot: ZIO[String, Throwable, AppConfig] =
    for {
      configParam <- ZIO.service[String]
      configFilename: String = System.getProperty("user.dir") + File.separator + configParam
      fileConfig = ConfigFactory.parseFile(new io.File(configFilename))
      appConfig = ConfigHelper.getConfig(fileConfig)
    } yield appConfig

  val parserEffect: ZIO[AppConfig with DbConnection with SttpBackend[Task, Any]/*SttpClient*/ with FbDownloader, Throwable, Unit] =
    for {
      fbdown <- ZIO.service[FbDownloader]
      fbUrl = "https://line06w.bk6bba-resources.com/line/desktop/topEvents3?place=live&sysId=1&lang=ru&salt=7u4qrf8pq08l5a08288&supertop=4&scopeMarket=1600"


      logicFb <- fbdown.getUrlContent(fbUrl)
        .catchAll {
          ex: Throwable => ZIO.logError(s"Exception catchAll fbdown.getUrlContent ${ex.getMessage} - ${ex.getCause}")
        }
        .repeat(Schedule.spaced(60.seconds))
        .forkDaemon

      // todo: may be combine in chain, if first save something then execute second effect
      logSaveAdv <- fbdown.checkAdvice
        .catchAllDefect { ex: Throwable =>
          ZIO.logError(s"Fatal: fbdown.checkAdvice [${ex.getMessage}] [${ex.getCause}]")
        }
        .repeat(Schedule.spaced(30.seconds)).forkDaemon

      _ <- logicFb.join
      _ <- logSaveAdv.join

    } yield ()

  val MainApp: ZIO[AppConfig, Throwable, Unit] = for {
    _ <- ZIO.logInfo("Begin fbparser mainApp")
    conf <- ZIO.service[AppConfig]
    _ <- parserEffect.provide(
      ZLayer.succeed(conf),
      PgConnectionImpl.layer,
      AsyncHttpClientZioBackend.layer(),
      FbDownloaderImpl.layer
    ).catchAllDefect { ex =>
      ZIO.logError(s"Defect mainApp = ${ex.getMessage} - ${ex.getCause} - ${ex.getStackTrace.mkString("Array(", ", ", ")")}")
    }
  } yield ()

  def botConfigZLayer(confParam: ZIOAppArgs): ZIO[Any, Throwable, ZLayer[Any, Throwable, AppConfig]] = for {
    _ <- ZIO.fail(new Exception("Empty parameters."))
      .when(confParam.getArgs.isEmpty)
    appCfg = ZLayer {
      for {
        appConfig <- confParam.getArgs.toList match {
          case List(configFile) => configBot.provide(ZLayer.succeed(configFile))
          case _ => ZIO.fail(new Exception("Empty list of input parameters. "))
        }
      } yield appConfig
    }
  } yield appCfg


  def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = for {
    args <- ZIO.service[ZIOAppArgs]
    botCfg <- botConfigZLayer(args)
    res <- MainApp.provide(botCfg)/*.provide(logger)*/.exitCode
  } yield res

}

