package common

case class AdviceGroup(
                        adviceId: Long,
                        event_id  : Long,
                        skname    : String,
                        competitionname: String,
                        team1coeff: Double,
                        team1: String,
                        draw_coeff: Double,
                        team2coeff: Double,
                        team2:      String,
                        team1score: String,
                        team2score: String,
                        ins_datetime: String,
                        advice_coeff: String,
                        advice_rest_mis: Int,
                        advice_type: String,
                        groupid: Long,
                        is_active_user: Int
                      ){
  def adviceTypeTxt: String = {
     advice_type match {
       case "team1"   => s"Победа ком. 1 - <u>$team1</u>"
       case "team2"   => s"Победа ком. 2 - <u>$team2</u>"
       case "draw"    => "Ничья"
       case "unknown" => "Не известно"
       case _ => "Не известно"
     }
  }
}

