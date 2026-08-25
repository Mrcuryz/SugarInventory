import asyncio
import base64
import json
import os
import sys
import time
from pathlib import Path

import qrcode


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "astrbot-source"))

from astrbot.core.platform.sources.weixin_oc.login_registration import (  # noqa: E402
    poll_weixin_oc_login_once,
    request_weixin_oc_login_qr,
)


QR_PATH = Path("wechat-login-qr-live.png")
SESSION_PATH = Path("weixin-login-session.json")
RESULT_PATH = Path("weixin-login-result.json")
STATUS_PATH = Path("weixin-login-status.json")


def save_qr_image(content: str, login_id: str) -> None:
    temp_path = QR_PATH.with_suffix(".tmp.png")
    if content.startswith("data:image/"):
        payload = content.split(",", 1)[1]
        temp_path.write_bytes(base64.b64decode(payload))
    else:
        login_url = content or (
            "https://liteapp.weixin.qq.com/q/7GiQu1"
            f"?qrcode={login_id}&bot_type=3"
        )
        qrcode.make(login_url).save(temp_path)
    os.replace(temp_path, QR_PATH)


def save_status(status: str, generation: int) -> None:
    STATUS_PATH.write_text(
        json.dumps(
            {
                "status": status,
                "generation": generation,
                "updatedAt": int(time.time() * 1000),
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )


async def main() -> None:
    platform_config = {"id": "weixin_personal"}
    generation = 0
    while True:
        generation += 1
        registration = await request_weixin_oc_login_qr(platform_config)
        save_qr_image(registration.qrcode_img_content, registration.qrcode)
        SESSION_PATH.write_text(
            json.dumps(
                {"qrcode": registration.qrcode, "generation": generation},
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )
        save_status("ready", generation)
        print(f"QR_READY_{generation}", flush=True)

        while True:
            result = await poll_weixin_oc_login_once(
                platform_config=platform_config,
                qrcode=registration.qrcode,
            )
            status = result.get("status")
            if status == "created":
                RESULT_PATH.write_text(
                    json.dumps(result, ensure_ascii=False, indent=2),
                    encoding="utf-8",
                )
                save_status("confirmed", generation)
                print("LOGIN_CONFIRMED", flush=True)
                return
            if status == "expired":
                save_status("refreshing", generation)
                print(f"QR_EXPIRED_{generation}", flush=True)
                break
            if status in {"denied", "error"}:
                save_status(str(status), generation)
                print(f"LOGIN_{str(status).upper()}", flush=True)
                return
            await asyncio.sleep(max(1, registration.interval))


asyncio.run(main())
