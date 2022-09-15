package app

import com.typesafe.config.ConfigFactory
import common.{AppConfig, ConfigHelper}
import service.{FbBotZio, FbBotZioImpl, PgConnectionImpl}
import zio.{ExitCode, URIO, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}

import scala.reflect.io.File
import java.io


/**
 * scala compiler server - VM options
 * -server -Xss2m
 * -XX:+UseParallelGC
 * -XX:MaxInlineLevel=20
 * --add-opens java.base/jdk.internal.misc=ALL-UNNAMED
 * -Dio.netty.tryReflectionSetAccessible=true
 * --illegal-access=warn
 *
 */
object Main extends ZIOAppDefault {

  val configBot: ZIO[String, Throwable, AppConfig] =
    for {
      configParam <- ZIO.service[String]
      configFilename: String = System.getProperty("user.dir") + File.separator + configParam
      fileConfig = ConfigFactory.parseFile(new io.File(configFilename))
      appConfig = ConfigHelper.getConfig(fileConfig)
    } yield appConfig

  val botEffect: ZIO[AppConfig with FbBotZio, Throwable, Unit] =
    for {
      bot <- ZIO.service[FbBotZio]
      _ <- bot.startBot
    } yield ()

  val MainApp: ZIO[AppConfig, Throwable, Unit] = for {
    _ <- ZIO.logInfo("Begin MainApp")
    conf <- ZIO.service[AppConfig]
    _ <- botEffect.provide(
      ZLayer.succeed(conf),
      ZLayer.succeed(conf.dbConf),
      ZLayer.succeed(conf.botConfig),
      PgConnectionImpl.layer,
      FbBotZioImpl.layer
    )
  } yield ()

  def botConfigZLayer(confParam: ZIOAppArgs): ZLayer[Any, Throwable, AppConfig] =
    ZLayer {
      for {
        appConfig <- confParam.getArgs.toList match {
          case List(configFile) => configBot.provide(ZLayer.succeed(configFile))
          case _ => ZIO.fail(new Exception("There is no config file in input parameter. "))
        }
        _ <- ZIO.logInfo(s"BotConfig = ${appConfig}")
      } yield appConfig
    }

  override def run: URIO[ZIOAppArgs, ExitCode] =
    for {
      _ <- ZIO.logInfo("Begin run")
      args <- ZIO.service[ZIOAppArgs]
      botConfig = botConfigZLayer(args)
      res <- MainApp.provide(botConfig).exitCode
    } yield res

}
