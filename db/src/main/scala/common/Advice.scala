package common

case class Advice(
                    event_id: Long,
                    skname: String,
                    competitionname: String,
                    eventname: String,
                    team1coeff: Double,
                    draw_coeff: Double,
                    team2coeff: Double,
                    team1score: String,
                    team2score: String,
                    rest_mis: Int
                 )
