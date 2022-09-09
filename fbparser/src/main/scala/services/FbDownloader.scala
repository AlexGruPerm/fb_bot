package services

import fb.LiveEventsResponse
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
    def getUrlContent(url: String): Task[LiveEventsResponse]
  }

  //2. accessor method inside companion objects
  object FbDownloader {
    def download(url: String): ZIO[FbDownloader, Throwable, LiveEventsResponse] =
      ZIO.serviceWithZIO(_.getUrlContent(url))
  }


  //3. Service implementations (classes) should accept all dependencies in constructor
  case class FbDownloaderImpl(console: Console, clock: Clock, client: SttpClient, conn: DbConnection) extends FbDownloader {

    val _LiveEventsResponse = root.value.result.string

    override def getUrlContent(url: String): Task[LiveEventsResponse] =
      for {
        time <- clock.currentDateTime
        _ <- console.printLine(s"$time - $url")
        _ <- console.printLine("Begin request")
        basicReq  = basicRequest.post(uri"$url").response(asJson[LiveEventsResponse])
        response <- client.send(basicReq)
        _ <- console.printLine(s" response statusText    = ${response.statusText}")
        _ <- console.printLine(s" response code          = ${response.code}")

        _ <- console.printLine(" ")
        _ <- console.printLine("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
        _ <- console.printLine(s"   Events count = ${response.body.right.get.events.size}")
        _ <- console.printLine("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
        evs = response.body.right.get.events
        evh = evs.head

        //pgc <- conn.connection
        idFbaLoad <- conn.save_fba_load

        _ <- ZIO.foreachDiscard(evs.filter(ei => ei.markets.nonEmpty && ei.timer.nonEmpty && ei.markets.exists(mf => mf.ident == "Results"))){ev =>
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
            ZIO.foreachDiscard(ev.markets.filter(mf => mf.ident == "Results" && mf.rows.nonEmpty && mf.rows.size >= 2)) { m =>
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
                ).map(_ => ZIO.unit)

              } else {
                console.printLine("not interested!!!")
              })
            }
          }

        }

        //full output one event
        //Just for visual debug
        /*
        _ <- ZIO.foreach(evs.filter(ei => ei.markets.nonEmpty && ei.timer.nonEmpty && ei.markets.exists(mf => mf.ident == "Results"))){
          e => console.printLine(s" ${e.id} - ${e.skName} -[ ${e.team1} - ${e.team2} ] - ${e.place} - ${e.timer}") *>
            ZIO.foreach(e.markets.filter(mf => mf.ident == "Results" && mf.rows.nonEmpty && mf.rows.size >= 2)){
              m => console.printLine(s"   ${m.marketId} - ${m.caption} - ${m.ident} - ${m.sortOrder} - rows [${m.rows.size}]") *>
                //ZIO.foreach(m.rows) {r => console.printLine(s"  ROW isTitle = ${r.isTitle} - CELLS SIZE = ${r.cells.size}") *>
                (if (m.rows.nonEmpty &&
                  m.rows.size >= 2 &&
                  m.rows(0).cells.nonEmpty &&
                  m.rows(0).cells.size >= 4 &&
                  m.rows(1).cells.nonEmpty &&
                  m.rows(1).cells.size >= 4 //todo: add more filter.
                    ) {
                    val r0 = m.rows(0)
                    val r1 = m.rows(1)
                    console.printLine{s"${r0.cells(1).caption} - ${r1.cells(1).value} score (this team) : ${e.scores(0).head.c1}"} *>
                      console.printLine(s"${r0.cells(2).caption} - ${r1.cells(2).value}") *>
                      console.printLine(s"${r0.cells(3).caption} - ${r1.cells(3).value} score (this team) : ${e.scores(0).head.c1}")
                } else {
                  console.printLine("not interested!!!")
                })
                  }
        }
        _ <- console.printLine("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
        */
        res <- ZIO.succeed(response.body.right.get)
      } yield res
  }

  //4. converting service implementation into ZLayer
  object FbDownloaderImpl {
    val layer: ZLayer[SttpClient with DbConnection,Throwable,FbDownloader] =
      ZLayer {
        for {
          console <- ZIO.console
          clock <- ZIO.clock
          client <- ZIO.service[SttpClient]
          conn <- ZIO.service[DbConnection]
          c <- conn.connection
          _ <- console.printLine(s"[FbDownloaderImpl] connection isOpened = ${!c.isClosed}")
        } yield FbDownloaderImpl(console,clock,client,conn)
      }
  }



