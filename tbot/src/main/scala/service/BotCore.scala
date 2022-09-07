package service

import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.cats.TelegramBot
import com.bot4s.telegram.methods.SetWebhook
import com.bot4s.telegram.models.{InputFile, Message, Update}
import com.bot4s.telegram.models.UpdateType.Filters.{InlineUpdates, MessageUpdates}
import com.bot4s.telegram.models.UpdateType.UpdateType
import common.BotConfig
import io.netty.handler.ssl.SslContextBuilder
import org.asynchttpclient.Dsl.asyncHttpClient
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zhttp.service.server.ServerSSLHandler.ServerSSLOptions
import zhttp.http._
import zhttp.service.Server
import zhttp.service.server.ServerChannelFactory
import zhttp.service.EventLoopGroup
import zhttp.http.{Http, Method, Request, Response}
import zio.{Ref, Schedule, Scope, Task, ZIO}
import zio.interop.catz._

import java.io.{File, FileInputStream, IOException, InputStream}
import java.net.http.HttpClient
import java.security.KeyStore
import javax.net.ssl.{KeyManagerFactory, TrustManagerFactory}
import java.time.Duration
import java.nio.file.Files
import java.nio.file.Paths

abstract class FbBot(val conf: BotConfig)
  extends TelegramBot[Task](conf.token, AsyncHttpClientZioBackend.usingClient(zio.Runtime.default, asyncHttpClient()))

class telegramBotZio(val config :BotConfig, private val started: Ref.Synchronized[Boolean])
  extends FbBot(config)
    with Commands[Task]
{
  val certPathStr :String = config.pubcertpath

  //def certificate: Option[InputFile] = Some(InputFile(new File(certPathStr).toPath))

  def certificate: Option[InputFile] = Some(
    InputFile("certificate", Files.readAllBytes(Paths.get(certPathStr)))
  )

  val port :Int = config.webhook_port
  val webhookUrl = config.webhookUrl

  val password :Array[Char] = config.keyStorePassword.toCharArray

  override def allowedUpdates: Option[Seq[UpdateType]] =
    Some(MessageUpdates ++ InlineUpdates)

  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keystore: InputStream = new FileInputStream(config.p12certpath)

  ks.load(keystore, password)

  val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
  keyManagerFactory.init(ks, password)

  val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  import com.bot4s.telegram.marshalling._

  private def callback: Http[Any,Throwable,Request,Response] = Http.collectZIO[Request]{
    case req @ Method.POST -> !! =>
      for {
        _ <- ZIO.logInfo("call callback")
        body    <- req.bodyAsString
        _ <- ZIO.logInfo(s"body = [$body]")
        update  <- ZIO.attempt(fromJson[Update](body))
        _ <- ZIO.logInfo(s"update = [$update]")
        handler <- receiveUpdate(update, None).catchAll{
          case ex: Throwable => ZIO.logError(s"receiveUpdate exception ${ex.getMessage} - ${ex.getCause}")
        }
        _ <- ZIO.logInfo(s"handler = [${handler}]")
      } yield Response.ok
  }

  val sslContext: io.netty.handler.ssl.SslContext = SslContextBuilder.
    forServer(keyManagerFactory)
    .build()

  val sslOptions: ServerSSLOptions = ServerSSLOptions(sslContext)

  private def server: Server[Any,Throwable] =
    Server.port(8443) ++ Server.app(callback) ++ Server.ssl(sslOptions)

  def startBot: ZIO[Any,Throwable,Unit] = started.updateZIO { isStarted =>
    //todo: move nested f.c. into separated function
    for {
      _ <- ZIO.logInfo(s"isStarted = $isStarted")
      _ <- ZIO.when(isStarted)(ZIO.fail(new Exception("Bot already started")))
      _ <- ZIO.when(!isStarted)(ZIO.logInfo(s"Bot not started yet, starting it .... webhookUrl=${webhookUrl}"))
      response <- request(SetWebhook(url = webhookUrl, certificate = certificate, allowedUpdates = allowedUpdates)).flatMap {
        case true =>
          ZIO.logInfo("SetWebhook success.") *>
            ZIO.succeed(true)
        case false =>
          ZIO.logError("Failed to set webhook")
          throw new RuntimeException("Failed to set webhook")
      }.catchAllDefect(ex => ZIO.logError(s"SetWebhook exception ${ex.getMessage} - ${ex.getCause}") *> ZIO.succeed(false)) //+++
    } yield response
  }

  override def run(): ZIO[Any, Throwable, Unit] =
    for {
      srv <- server.withSsl(sslOptions).make
        .flatMap(start => ZIO.logInfo(s"Server started on ${start.port} ") *> ZIO.never)
        .catchAllDefect(ex => ZIO.logError(s"Server error ${ex.getMessage} - ${ex.getCause}"))  //+++
        .provide(ServerChannelFactory.auto, EventLoopGroup.auto(1), Scope.default).forkDaemon

      startedBefore <- started.get
      _ <- ZIO.logInfo(s"started = [$startedBefore] BEFORE updateZIO")

      cln <- startBot.forkDaemon

      startedAfter <- started.get
      _ <- ZIO.logInfo(s"started = [$startedAfter] AFTER updateZIO")

      _ <- srv.join
      _ <- cln.join
    } yield ()

  onCommand("/hello") { implicit msg =>
    onCommandLog(msg) *>
      reply("hello command").ignore
  }

  onCommand("/start") { implicit msg =>
    onCommandLog(msg) *>
      reply("start command!").ignore
  }

  onCommand("/begin") { implicit msg =>
    onCommandLog(msg) *>
      reply("begin command!").ignore
  }

  def onCommandLog(msg :Message) :ZIO[Any,IOException,Unit] = {
    for {
      console <- ZIO.console
      _ <- console.printLine(" Command ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ ")
      /** USER */
      _ <- console.printLine(" User :")
      _ <- console.printLine(" ID = " + msg.from.map(u => u.id).getOrElse(0))
      _ <- console.printLine(s" is bot ${msg.from.map(u => u.isBot)}")
      _ <- console.printLine(" FIRSTNAME = " + msg.from.map(u => u.firstName).getOrElse(" "))
      _ <- console.printLine(" LASTNAME = " + msg.from.map(u => u.lastName.getOrElse(" ")).getOrElse(" "))
      _ <- console.printLine(" USERNAME = " + msg.from.map(u => u.username.getOrElse(" ")).getOrElse(" "))
      _ <- console.printLine(" USERID   = " + msg.from.map(u => u.id).getOrElse(" "))
      _ <- console.printLine(" LANG     = " + msg.from.map(u => u.languageCode.getOrElse(" ")).getOrElse(" "))
      _ <- console.printLine(" isBot    = " + msg.from.map(u => u.isBot).getOrElse(" "))
      //todo: запретить ботам создавать чаты с этим ботом.
      /** ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */
      _ <- console.printLine(" LOC latitude     = " + msg.location.map(l => l.latitude))
      _ <- console.printLine(" LOC longitude    = " + msg.location.map(l => l.longitude))
      _ <- console.printLine(" isChat           = " + msg.chat.chatId.isChat)
      _ <- console.printLine("  chat(id)        = " + msg.chat.id)
      _ <- console.printLine("  linkedChatId    = " + msg.chat.linkedChatId)
      _ <- console.printLine(" isChannel        = " + msg.chat.chatId.isChannel)
      /** ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */
      _ <- console.printLine(" msg date        = " + msg.date)
      _ <- console.printLine(" messageId = " + msg.messageId)
      _ <- console.printLine(" text = " + msg.text.mkString(","))
      _ <- console.printLine(" ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ ")
    } yield ()
  }

}