package app

import com.typesafe.config.ConfigFactory
import common.BotConfig
import service.{FbBotZio, FbBotZioImpl}
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

  val configBot: ZIO[String, Throwable, BotConfig] =
    for {
      configParam <- ZIO.service[String]
      configFilename: String = System.getProperty("user.dir") + File.separator + configParam
      fileConfig = ConfigFactory.parseFile(new io.File(configFilename))
      appConfig = ConfigHelper.getConfig(fileConfig)
    } yield appConfig.botConfig

  val botEffect: ZIO[BotConfig with FbBotZio, Throwable, Unit] =
    for {
      bot <- ZIO.service[FbBotZio]
      _ <- bot.startBot
    } yield ()

  val MainApp: ZIO[BotConfig, Throwable, Unit] = for {
    _ <- ZIO.logInfo("Begin MainApp")
    conf <- ZIO.service[BotConfig]
    _ <- botEffect.provide(
      ZLayer.succeed(conf),
      FbBotZioImpl.layer
    )
  } yield ()

  def botConfigZLayer(confParam: ZIOAppArgs): ZLayer[Any, Throwable, BotConfig] =
    ZLayer {
      for {
        botConfig <- confParam.getArgs.toList match {
          case List(configFile) => configBot.provide(ZLayer.succeed(configFile))
          case _ => ZIO.fail(new Exception("There is no config file in input parameter. "))
        }
        _ <- ZIO.logInfo(s"BotConfig = ${botConfig}")
      } yield botConfig
    }

  override def run: URIO[ZIOAppArgs, ExitCode] =
    for {
      _ <- ZIO.logInfo("Begin run")
      args <- ZIO.service[ZIOAppArgs]
      botConfig = botConfigZLayer(args)
      res <- MainApp.provide(botConfig).exitCode
    } yield res

}
