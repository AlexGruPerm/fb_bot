package app

import com.typesafe.config.Config
import common._

case object ConfigHelper{
  def getConfig(fileConfig :Config): AppConfig = {
    val pgPrefix = "postgres."
    AppConfig(
      DbConfig(
        driver   = fileConfig.getString(pgPrefix+"driver"),
        url      = fileConfig.getString(pgPrefix+"url"),
        username = fileConfig.getString(pgPrefix+"username"),
        password = fileConfig.getString(pgPrefix+"password"),
      )
    )
  }
}
