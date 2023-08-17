package services

import fb.{LiveEvent, LiveEventsResponse}
import io.circe.Json
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client3.{SttpBackend, UriContext, basicRequest}
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.{Clock, Console, Scope, Task, TaskLayer, ULayer, URIO, URLayer, ZIO, ZLayer}
import sttp.client3._
import sttp.client3.asynchttpclient.zio._
import zio._
import zio.Console
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import sttp.client3.circe._
import io.circe._
import io.circe.parser._
import io.circe.optics.JsonPath._
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import io.circe.optics.JsonPath
import io.circe.syntax._
import service.DbConnection
import sttp.model.StatusCode

import java.sql.Statement


/**
 *
 * The 3 Laws of ZIO Environment:
  1. Methods inside service definitions (traits) should NEVER use the environment
  2. Service implementations (classes) should accept all dependencies in constructor
  3. All other code ('business logic') should use the environment to consume services https://t.co/iSWzMhotOv
 *
 */
/**
 * Service pattern 2:
 * 1. define interface
 * 2. accessor method inside companion objects
 * 3. implementation of service interface
 * 4. converting service implementation into ZLayer
 */
  //1. service interface - read Json string from given url.
  trait FbDownloader {
    def getUrlContent(url: String): Task[Int]
    //Return Task[Count successful inserted advices]
    def checkAdvice: Task[Int]
  }

  //2. accessor method inside companion objects
  object FbDownloader {/*
    def download(url: String): ZIO[FbDownloader, Throwable, Int] = {
      ZIO.serviceWithZIO(_.getUrlContent(url))
      }
      */
  }


  //3. Service implementations (classes) should accept all dependencies in constructor
  case class FbDownloaderImpl(/*console: Console,*/ clock: Clock, client: SttpBackend[Task, Any]/*SttpClient*/, conn: DbConnection)
    extends FbDownloader {

    val _LiveEventsResponse = root.value.result.string

    def saveEventsScores(evs: Seq[LiveEvent]) :Task[Unit] = for {
      _ <- ZIO.logInfo/*console.printLine*/(s" Events count = ${evs.size}")
      idFbaLoad <- conn.save_fba_load
      _ <- ZIO.foreachDiscard(evs
        .filter(ei => ei.markets.nonEmpty && ei.timer.nonEmpty && ei.markets.exists(mf => mf.ident == "Results"))){ev =>
        conn.save_event(
          idFbaLoad,
          ev.id,
          ev.number,
          ev.competitionName,
          ev.skId,
          ev.skName,
          ev.timerSeconds.getOrElse(0L),
          ev.team1Id,
          ev.team1,
          ev.team2Id,
          ev.team2,
          ev.startTimeTimestamp,
          ev.eventName
        ).flatMap { idFbaEvent =>
          //scores insert
          ZIO.foreachDiscard(ev.markets.filter(mf => mf.ident == "Results" && mf.rows.nonEmpty && mf.rows.size >= 2)) {
            m =>
            (if (m.rows.nonEmpty &&
              m.rows.size >= 2 &&
              m.rows(0).cells.nonEmpty &&
              m.rows(0).cells.size >= 4 &&
              m.rows(1).cells.nonEmpty &&
              m.rows(1).cells.size >= 4 //todo: add more filter.
            ) {
              val r0 = m.rows(0)
              val r1 = m.rows(1)
              conn.save_score(
                idFbaEvent,
                r0.cells(1).caption.getOrElse("*"),
                r1.cells(1).value.getOrElse(0.0),
                ev.scores(0).head.c1,
                r0.cells(2).caption.getOrElse("*"),
                r1.cells(2).value.getOrElse(0.0),
                r1.cells(3).value.getOrElse(0.0),
                r0.cells(3).caption.getOrElse("*"),
                ev.scores(0).head.c2
              ).as(ZIO.unit)
            } else {
              ZIO.logInfo/*console.printLine*/("not interested!!!")
            })
          }.when(idFbaEvent!=0)
            .catchAllDefect{ex: Throwable => ZIO.logError(s"Exception save SCORE ${ex.getMessage} - ${ex.getCause}")}
        }.catchAllDefect{ex: Throwable => ZIO.logError(s"Exception save EVENT event_id = [${ev.id}] - ${ex.getMessage} - ${ex.getCause}")}
      }
    } yield ()

    override def getUrlContent(url: String): Task[Int] =
      for {
        time <- clock.currentDateTime
        _ <- ZIO.logInfo(s"$time - $url")
        _ <- ZIO.logInfo("Begin request")
        basicReq  = basicRequest.get(uri"$url").response(asJson[LiveEventsResponse])
        response <- client.send(basicReq)
          //.catchAllDefect{ex =>ZIO.logError(s"errror = ${ex.getCause + " " + ex.getMessage}") }
        //_ <- console.printLine(s"console response statusText    = ${response.statusText}")
        //_ <- console.printLine(s"console response code          = ${response.code}")
        _ <- ZIO.logInfo(s"Errror response statusText    = ${response.statusText}")
        _ <- ZIO.logInfo(s"Errror response code          = ${response.code}")

        _ <- saveEventsScores(response.body.right.get.events).when(response.code == StatusCode.Ok)

        _ <- ZIO.logInfo/*console.printLine*/("getUrlContent CODE = 503, Service temporarily unavailable sleep 1 minute.")
          .zip(ZIO.sleep(60.seconds))
          .when(response.code == StatusCode.ServiceUnavailable)

        _ <- ZIO.logInfo/*console.printLine*/("getUrlContent CODE != 200 ").when(response.code != StatusCode.Ok)

        res <- ZIO.succeed(1)
      } yield res

    //Return Task[Count successful inserted advices]
    def checkAdvice: Task[Int] = for {
      res <- conn.saveAdvices
      //_ <- ZIO.logInfo(s"checkAdvice in FbDownloader res= [$res]")
    } yield res

  }

  //4. converting service implementation into ZLayer
  object FbDownloaderImpl {
    val layer: ZLayer[SttpBackend[Task, Any]/*SttpClient*/ with DbConnection,Throwable,FbDownloader] =
      ZLayer {
        for {
          //console <- ZIO.console
          clock <- ZIO.clock
          client <- ZIO.service[SttpBackend[Task, Any]/*SttpClient*/]
          conn <- ZIO.service[DbConnection]
          c <- conn.connection
          _ <- ZIO.logInfo/*console.printLine*/(s"[FbDownloaderImpl] connection isOpened = ${!c.isClosed}")
        } yield FbDownloaderImpl(/*console,*/clock,client,conn)
      }
  }



