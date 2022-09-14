package app

import com.typesafe.config.{Config, ConfigFactory}
import common.{AppConfig, DbConfig}
import org.slf4j.LoggerFactory
import service.{DbConnection, PgConnectionImpl}
import services.{FbDownloader, FbDownloaderImpl}
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
import zio.{Schedule, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}

import java.io
import java.io.File
import java.time.Duration
import zio.{Clock, Console, Layer, RLayer, Schedule, Scope, ULayer, URLayer, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer, durationInt}

object MainApp extends ZIOAppDefault{

  val parserEffect :ZIO[DbConnection with SttpClient with FbDownloader, Throwable, Unit] =
    for {
      //console <- ZIO.console
      fbdown <- ZIO.service[FbDownloader]
      fbUrl =         //"https://line05w.bk6bba-resources.com/line/desktop/topEvents3?place=live&sysId=1&lang=ru&salt=33kcb6w4ydl56iud3p&supertop=4&scopeMarket=1600"
        //"https://line06w.bk6bba-resources.com/line/desktop/topEvents3?place=live&sysId=1&lang=ru&salt=1jy2797i10bl58mhxjm&supertop=4&scopeMarket=1600"
        //todo: here we need new parser with new c.c.
        //"https://line53w.bk6bba-resources.com/events/list?lang=ru&version=8639286626&scopeMarket=1600"
        //"https://line32w.bk6bba-resources.com/line/desktop/topEvents3?place=live&sysId=1&lang=ru&salt=10i7oc9ftkdl59yfmc1&supertop=4&scopeMarket=1600"
        "https://line06w.bk6bba-resources.com/line/desktop/topEvents3?place=live&sysId=1&lang=ru&salt=7u4qrf8pq08l5a08288&supertop=4&scopeMarket=1600"

      logicFb <- fbdown.getUrlContent(fbUrl).repeat(Schedule.spaced(60.seconds)).forkDaemon


      // todo: may be combine in chain, if first save something then execute second effect
      logSaveAdv <- fbdown.checkAdvice.repeat(Schedule.spaced(30.seconds)).forkDaemon

      _ <- logicFb.join
      _ <- logSaveAdv.join

    } yield ()

  val log = LoggerFactory.getLogger(getClass.getName)

  val args :List[String] = List("fbparser\\src\\main\\resources\\control.conf")

  val config :AppConfig = try {
    if (args.isEmpty) {
      log.info("There is no external config file.")
      //ConfigFactory.load()
      throw new Exception("There is no external config file.")
    } else {
      val configFilename :String = System.getProperty("user.dir")+File.separator+args.head
      log.info("There is external config file, path="+configFilename)
      val fileConfig :Config = ConfigFactory.parseFile(new io.File(configFilename))
      ConfigHelper.getConfig(fileConfig)
    }
  } catch {
    case e:Exception =>
      log.error("ConfigFactory.load - cause:"+e.getCause+" msg:"+e.getMessage)
      throw e
  }

  /*
  in run parameter
  fbparser\src\main\resources\control.conf
  */
  val mainApp: ZIO[Any, Throwable, Unit] = parserEffect.provide(
    ZLayer.succeed(config.dbConf),
    PgConnectionImpl.layer,
    AsyncHttpClientZioBackend.layer(),
    FbDownloaderImpl.layer
  )

  /**
   * https://zio.github.io/zio-logging/docs/overview/overview_index.html#slf4j-bridge
   * import zio.logging.slf4j.Slf4jBridge
   * program.provideCustom(Slf4jBridge.initialize)
   */
  def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    mainApp.exitCode
  }
}