package service

import common.DbConfig
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


  override def save_fba_load: Task[Int] = for {
    pstmt <- prepStmtFbaLoad
    _ = pstmt.executeUpdate()
    keyset = pstmt.getGeneratedKeys
    _ = keyset.next()
    idFbaLoad: Int = keyset.getInt(1)
    _ = pstmt.close()
    _ = pstmt.getConnection.close()
  } yield idFbaLoad



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
