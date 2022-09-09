package service

import common.BotConfig
import zio._

//1. trait
abstract class FbBotZio(conf: BotConfig, conn: DbConnection) {

  def startBot: ZIO[Any, Nothing, Unit] =
    for {
      _ <- ZIO.logInfo("Begin start bot")
      _ <- Ref.Synchronized.make(false).flatMap { started => new telegramBotZio(conf, conn, started).run()
      }.catchAllDefect {
        case ex: Throwable => ZIO.logError(s" Exception FbBotZio.runBot ${ex.getMessage} - ${ex.getCause} ")
      }
        .catchAll {
          case ex: Throwable => ZIO.logError(s" Exception FbBotZio.runBot ${ex.getMessage} - ${ex.getCause} ")
        }
      _ <- ZIO.logInfo("End bot")
    } yield ()

}

//3. Service implementations (classes) should accept all dependencies in constructor
case class FbBotZioImpl(conf: BotConfig,conn : DbConnection) extends FbBotZio(conf, conn) {
  super.startBot
}

//4. converting service implementation into ZLayer
object FbBotZioImpl {
  val layer: ZLayer[BotConfig with DbConnection, Nothing, FbBotZioImpl] = //ZLayer.succeed(FbBotZioImpl)
    ZLayer {
      for {
        conf <- ZIO.service[BotConfig]
        conn <- ZIO.service[DbConnection]
      } yield FbBotZioImpl(conf,conn)
    }

}
