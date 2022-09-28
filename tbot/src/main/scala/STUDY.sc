import zio.ZIOAppDefault
import zio.Unsafe.unsafe
import zio._

object Bot extends ZIOAppDefault {

  val MainApp: ZIO[Any, Throwable, Unit] = for {
    _ <- Console.printLine("MainApp")
  } yield ()

  def run: URIO[ZIOAppArgs, ExitCode] =
    for {
      _ <- Console.printLine("run").orDie
      res <- MainApp.exitCode
    } yield res

}

unsafe{ implicit u =>
  Runtime.default.unsafe.run(Bot.run.provide(ZIOAppArgs.empty))
}
