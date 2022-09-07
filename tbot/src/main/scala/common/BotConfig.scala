package common

case class BotConfig(
                      token: String,
                      webhookUrl: String,
                      webhook_port: Int,
                      keyStorePassword: String,
                      pubcertpath: String,
                      p12certpath: String,
                    )