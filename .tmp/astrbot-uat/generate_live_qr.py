import json
from pathlib import Path

import qrcode


login_id = json.loads(
    Path("weixin-login-session.json").read_text(encoding="utf-8")
)["qrcode"]
url = (
    "https://liteapp.weixin.qq.com/q/7GiQu1"
    f"?qrcode={login_id}&bot_type=3"
)
qrcode.make(url).save("wechat-login-qr-live.png")
