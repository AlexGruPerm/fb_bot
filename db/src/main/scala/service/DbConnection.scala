package service

import common.{Advice, AdviceGroup, DbConfig, Group}
import zio.{Task, ZIO, ZLayer}

import java.sql.{Connection, DriverManager, ResultSet, Statement, Types}
import java.util.Properties

/**
 *
 * The 3 Laws of ZIO Environment:
 * 1. Methods inside service definitions (traits) should NEVER use the environment
 * 2. Service implementations (classes) should accept all dependencies in constructor
 * 3. All other code ('business logic') should use the environment to consume services https://t.co/iSWzMhotOv
 *
 */
/**
 * Service pattern 2:
 * 1. define interface
 * 2. accessor method inside companion objects
 * 3. implementation of service interface
 * 4. converting service implementation into ZLayer
 */

//1. service interface - get config
trait DbConnection {

  def connection : Task[Connection]
  def execute(sql: String): Task[String]


  val prepStmtFbaLoad = for{
    pgc <- connection
    pstmt = pgc.prepareStatement("insert into fba_load default values;", Statement.RETURN_GENERATED_KEYS)
  } yield pstmt

  val prepStmtEvents = for {
    pgc <- connection
    pstmt = pgc.prepareStatement(
      s"insert into events(fba_load_id, event_id,event_number,competitionName,skid,skname,timerSeconds,team1Id,team1,team2Id,team2,startTimeTimestamp,eventName) values(?,?,?,?,?,?,?,?,?,?,?,?,?);"
      ,Statement.RETURN_GENERATED_KEYS)
  } yield pstmt

  val prepStmtScore = for {
    pgc <- connection
    pstmt = pgc.prepareStatement(s"insert into score(events_id,team1,team1Coeff,team1score,draw,draw_coeff,team2Coeff,team2,team2score) values(?,?,?,?,?,?,?,?,?);")
  } yield pstmt

  val prepStmtGroup = for {
    pgc <- connection
    pstmt = pgc.prepareStatement("INSERT INTO tgroup(groupid,firstname,lastname,username,lang,loc_latitude,loc_longitude)\n    " +
      "VALUES(?,?,?,?,?,?,?)\n    " +
      "ON CONFLICT (groupid)\n    " +
      "do update set is_blck_by_user_dt = null, last_cmd_start_dt = timeofday()::TIMESTAMP;")
  } yield pstmt

  val prepStmtSaveSentGrp = for {
    pgc <- connection
    pstmt = pgc.prepareStatement("insert into fba.advice_sent(advice_id,groupid) values(?,?);")
  } yield pstmt

  val prepStmtSaveAdvice = for {
    pgc <- connection
    pstmt = pgc.prepareStatement("insert into fba.advice(event_id,advice_text) values(?,?);")
  } yield pstmt

  def getAdvicesGroups: ZIO[Any,Nothing,List[AdviceGroup]]

  def botBlockedByUser(groupid: Long): Task[Int]

  def save_group(
                  chatId: Long,
                  firstName: String,
                  lastName: String,
                  username: String,
                  languageCode: String,
                  latitude: Double,
                  longitude: Double,
                ): Task[Int]

  def save_fba_load: Task[Int]
  def save_event(
                  idFbaLoad: Long,
                  id: Long,
                  evnumber: Long,
                  competitionName: String,
                  skId: Long,
                  skName: String,
                  timerSeconds : Long,
                  team1Id: Long,
                  team1: String,
                  team2Id: Long,
                  team2: String,
                  startTimeTimestamp: Long,
                  eventName: String
                ): Task[Int]
  def save_score(
                   idFbaEvent: Long,
                   team1caption: String,
                   team1Coeff: Double,
                   team1score: String,
                   draw: String,
                   draw_coeff: Double,
                   team2Coeff: Double,
                   team2caption: String,
                   team2score: String,
                ): Task[Int]

  def saveSentGrp(advGrp: AdviceGroup): Task[Int]

  def saveAdvices: Task[Int]
}

//2.accessor method inside companion object

//3. service interface implementation
case class PgConnectionImpl(conf: DbConfig) extends DbConnection {

  override def connection: Task[Connection] = {
    ZIO.attempt {
      val props = new Properties()
      props.setProperty("user", conf.username)
      props.setProperty("password", conf.password)
      val c: Connection = DriverManager.getConnection(conf.url, props)
      c.setClientInfo("ApplicationName", s"fb_adviser")
      val stmt: Statement = c.createStatement
      val rs: ResultSet = stmt.executeQuery("SELECT pg_backend_pid() as pg_backend_pid")
      rs.next()
      val pg_backend_pid: Int = rs.getInt("pg_backend_pid")
      c
    }
  }

  //todo: check for removing this method
  override def execute(sql: String): Task[String] =
    for {
      con <- connection
      stex <- ZIO.attempt {
        con.setAutoCommit(true)
        val stmt = con.prepareCall(sql)
        stmt.execute()
      }
    } yield stex.toString

  def botBlockedByUser(groupid: Long): Task[Int] = for {
    pgc <- connection
    pstmt = pgc.prepareStatement("update tgroup set is_blck_by_user_dt = timeofday()::TIMESTAMP where groupId = ?;")
    _ <- ZIO.succeed {
      pstmt.setLong(1, groupid)
    }
    resUpdate = pstmt.executeUpdate()
    _ = pstmt.close()
    _ = pstmt.getConnection.close()
  } yield resUpdate

  def getAdvicesGroups: ZIO[Any,Nothing,List[AdviceGroup]] =
    (for {
      pgc <- connection
      pstmt = pgc.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
      rs = pstmt.executeQuery("select a.id as advice_id, t.groupid, a.advice_text " +
                                   " from   fba.tgroup t,       " +
                                   "        fba.advice a" +
                                   " where  t.is_blck_by_user_dt is null and " +
                                   "        a.sent_datetime is null and      " +
                                   "        (a.id,t.groupid) not in ( " +
                                   "                                  select ast.advice_id,ast.groupid " +
                                   "                                    from fba.advice_sent ast  )")
      results =
        Iterator.continually(rs).takeWhile(_.next()).map{
          rsi => AdviceGroup(
            rsi.getLong("advice_id"),
            rsi.getLong("groupid"),
            rsi.getString("advice_text")
          )
          //columns.map(cname => rsi.getString(cname._1))
        }.toList
      _ <- ZIO.logInfo(s"There are ${results.size} advice-group(s) to send.")
      _ = pstmt.close()
      _ = pstmt.getConnection.close()
    } yield results).catchAll {
       ex: Throwable =>
        ZIO.logError(s"FBAE-03 Can't get active groups. [${ex.getLocalizedMessage}] [${ex.getMessage}] [${ex.getCause}]").as(List.empty[AdviceGroup])
    }

  def saveAdvices: Task[Int] =
    (for {
      //_ <- ZIO.logInfo("Begin saveAdvices")
      pgc <- connection
      pstmt = pgc.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
      rs = pstmt.executeQuery("select * from fba.v_football")
      listAdvice =
        Iterator.continually(rs).takeWhile(_.next()).map{
          rsi => Advice(
            rsi.getLong("event_id"),
            rsi.getString("skname"),
            rsi.getString("competitionname"),
            rsi.getString("eventname"),
            rsi.getDouble("team1coeff"),
            rsi.getDouble("draw_coeff"),
            rsi.getDouble("team2coeff"),
            rsi.getString("team1score"),
            rsi.getString("team2score"),
            rsi.getInt("rest_mis")
          )
          //columns.map(cname => rsi.getString(cname._1))
        }.toList
      _ <- ZIO.logInfo(s"There are [${listAdvice.size}] rows in saveAdvices.").when(listAdvice.nonEmpty)
      //todo: here plase to insert advice into fba.advice

      pstmt <- prepStmtSaveAdvice
      _ <- ZIO.foreachDiscard(listAdvice){
        adv => ZIO.attempt{
          pstmt.setLong(1, adv.event_id)
          //"<b>Рекомендация № 1</b>"
          pstmt.setString(2,
              s"<u>${adv.skname} (${adv.competitionname})</u>"+
              s"До конца матча <b>${adv.rest_mis.toString}</b> минут."+
              s"<pre>           ${adv.eventname}"+
              s"  Коэфф.     ${adv.team1coeff.toString}  ${adv.draw_coeff.toString}  ${adv.team2coeff.toString}"+
              s"  Счет          ${adv.team1score}  :    ${adv.team2score} </pre>"+
              s"  <b>Совет</b> поставить на <b>${List(adv.team1coeff,adv.draw_coeff,adv.team2coeff).min.toString}</b>"+
              s"(дата рекомендации 13.09.2022 01:51:12 Мск.)"
          )
          pstmt.executeUpdate()
        }.catchAll {
          ex: Throwable =>
            ZIO.logError(s"SAE-02 Can't get list of advices. [${ex.getLocalizedMessage}] [${ex.getMessage}] [${ex.getCause}]").as(0)
        }
      }
      _ = pstmt.close()
      _ = pstmt.getConnection.close()
    } yield listAdvice.size).catchAll {
      ex: Throwable =>
        ZIO.logError(s"SAE-03 Can't get list of advices. [${ex.getLocalizedMessage}] [${ex.getMessage}] [${ex.getCause}]").as(0)
    }

  override def save_fba_load: Task[Int] = for {
    pstmt <- prepStmtFbaLoad
    _ = pstmt.executeUpdate()
    keyset = pstmt.getGeneratedKeys
    _ = keyset.next()
    idFbaLoad: Int = keyset.getInt(1)
    _ = pstmt.close()
    _ = pstmt.getConnection.close()
  } yield idFbaLoad

  def saveSentGrp(advGrp: AdviceGroup): Task[Int] = for {
    pstmt <- prepStmtSaveSentGrp
    res <- ZIO.succeed{
      pstmt.setLong(1, advGrp.adviceId)
      pstmt.setLong(2, advGrp.groupId)
      pstmt.executeUpdate()
    }
    _ = pstmt.close()
    _ = pstmt.getConnection.close()
  } yield res

  override def save_group(
                  chatId: Long,
                  firstName: String,
                  lastName: String,
                  username: String,
                  languageCode: String,
                  latitude: Double,
                  longitude: Double,
                ): Task[Int] = for {
    pstmt <- prepStmtGroup
/*
    _ <- ZIO.logInfo(s" >>>>>>>>>>>>>>>>>  save_group chatId=$chatId")
    _ <- ZIO.logInfo(s" >>>>>>>>>>>>>>>>>  save_group firstName=$firstName")
    _ <- ZIO.logInfo(s" >>>>>>>>>>>>>>>>>  save_group lastName=$lastName")
    _ <- ZIO.logInfo(s" >>>>>>>>>>>>>>>>>  save_group username=$username")
    _ <- ZIO.logInfo(s" >>>>>>>>>>>>>>>>>  save_group languageCode=$languageCode")
    _ <- ZIO.logInfo(s" >>>>>>>>>>>>>>>>>  save_group latitude=$latitude")
    _ <- ZIO.logInfo(s" >>>>>>>>>>>>>>>>>  save_group longitude=$longitude")
*/
    res <- ZIO.succeed{
      pstmt.setLong(  1, chatId)
      pstmt.setString(2,firstName)
      pstmt.setString(3,lastName)
      pstmt.setString(4,username)
      pstmt.setString(5,languageCode)
      pstmt.setDouble(6,latitude)
      pstmt.setDouble(7,longitude)
      pstmt.executeUpdate()
    }

    _ = pstmt.close()
    _ = pstmt.getConnection.close()
    _ <- ZIO.logInfo(s"<<<<<<<<<<<<<<<<< save_group result res = $res")
  } yield res

  def save_event(
                  idFbaLoad: Long,
                  id: Long,
                  evnumber: Long,
                  competitionName: String,
                  skId: Long,
                  skName: String,
                  timerSeconds : Long,
                  team1Id: Long,
                  team1: String,
                  team2Id: Long,
                  team2: String,
                  startTimeTimestamp: Long,
                  eventName: String
                ) : Task[Int] = for {
    pstmt <- prepStmtEvents
    _ <- ZIO.succeed {
      pstmt.setLong(1, idFbaLoad)
      pstmt.setLong(2, id)
      pstmt.setLong(3, evnumber)
      pstmt.setString(4, competitionName)
      pstmt.setLong(5, skId)
      pstmt.setString(6, skName)
      pstmt.setLong(7, timerSeconds)
      pstmt.setLong(8, team1Id)
      pstmt.setString(9, team1)
      pstmt.setLong(10, team2Id)
      pstmt.setString(11, team2)
      pstmt.setLong(12, startTimeTimestamp)
      pstmt.setString(13, eventName)
    }
     resInsertEvent = pstmt.executeUpdate()
     keysetEvnts = pstmt.getGeneratedKeys
     _ = keysetEvnts.next()
     idFbaEvent :Int  = keysetEvnts.getInt(1)
    _ = pstmt.close()
    _ = pstmt.getConnection.close()
  } yield idFbaEvent


  //todo: use batch here https://www.vertica.com/docs/9.2.x/HTML/Content/Authoring/ConnectingToVertica/ClientJDBC/BatchInsertsUsingJDBCPreparedStatements.htm
  def save_score(
                  idFbaEvent: Long,
                  team1caption: String,
                  team1Coeff: Double,
                  team1score: String,
                  draw: String,
                  draw_coeff: Double,
                  team2Coeff: Double,
                  team2caption: String,
                  team2score: String,
                ): Task[Int] = for {
    pstmt <- prepStmtScore
    insertedRows <- ZIO.succeed {
      pstmt.setLong(1, idFbaEvent)
      pstmt.setString(2, team1caption)
      pstmt.setDouble(3, team1Coeff)
      pstmt.setString(4, team1score)
      pstmt.setString(5, draw)
      pstmt.setDouble(6, draw_coeff)
      pstmt.setDouble(7, team2Coeff)
      pstmt.setString(8, team2caption)
      pstmt.setString(9, team2score)
      pstmt.executeUpdate()
    }
    //insertedRows <- ZIO.succeed(123)
    //_ <- ZIO.logInfo(s"Inserted ${insertedRows} rows into score for idFbaEvent = ${idFbaEvent}")
    _ = pstmt.close()
    _ = pstmt.getConnection.close()
  } yield insertedRows


}

//4. converting service implementation into ZLayer
object PgConnectionImpl{
  val layer :ZLayer[DbConfig,Throwable,DbConnection] =
    ZLayer{
      for {
        cfg <- ZIO.service[DbConfig]
        _ <- ZIO.logInfo(s"PgConnectionImpl cfg.url = ${cfg.url}")
      } yield PgConnectionImpl(cfg)
    }
}
