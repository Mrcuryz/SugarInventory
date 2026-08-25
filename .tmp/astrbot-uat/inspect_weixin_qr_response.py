import asyncio
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "astrbot-source"))

from astrbot.core.platform.sources.weixin_oc.login_registration import (  # noqa: E402
    request_weixin_oc_login_qr,
)


async def main() -> None:
    registration = await request_weixin_oc_login_qr({"id": "weixin_personal"})
    info = {
        "qrcode": registration.qrcode,
        "content_length": len(registration.qrcode_img_content),
        "content_prefix": registration.qrcode_img_content[:120],
        "interval": registration.interval,
    }
    Path("weixin-qr-inspection.json").write_text(
        json.dumps(info, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(info, ensure_ascii=False))


asyncio.run(main())
