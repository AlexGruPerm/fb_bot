
Win10 - cmd : cd /d E:\PROJECTS\min_bot4s_zio\tbot\src\main\resources>

set webhook mnually from directory with self-signed certificate mts.pem


curl  --ssl-no-revoke "https://api.telegram.org/bot886094130:AAENizOGvqrl7vrLVeOZwKfJdIJyQEU_123/deleteWebhook"
{"ok":true,"result":true,"description":"Webhook was deleted"}


curl -F "url=https://91.236.15.73:8443/" --ssl-no-revoke -F "certificate=@mts.pem" "https://api.telegram.org/bot886094130:AAENizOGvqrl7vrLVeOZwKfJdIJyQEU_123/setwebhook"
{"ok":true,"result":true,"description":"Webhook was set"}


curl  --ssl-no-revoke "https://api.telegram.org/bot886094130:AAENizOGvqrl7vrLVeOZwKfJdIJyQEU_123/getWebhookInfo"
{"ok":true,"result":{"url":"https://91.236.15.73:8443/","has_custom_certificate":true,"pending_update_count":21,"max_connections":40,"ip_address":"91.236.15.73","allowed_updates":["message","edited_message","inline_query","chosen_inline_result"]}}




-------

https://api.telegram.org/bot886094130:AAENizOGvqrl7vrLVeOZwKfJdIJyQEU_123/getWebhookInfo

https://api.telegram.org/bot886094130:AAENizOGvqrl7vrLVeOZwKfJdIJyQEU_123/deleteWebhook
