import asyncio
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "astrbot-source"))

from astrbot.core.platform.sources.weixin_oc.login_registration import (  # noqa: E402
    poll_weixin_oc_login_once,
)


async def main() -> None:
    result = await poll_weixin_oc_login_once(
        platform_config={"id": "weixin_personal"},
        qrcode="1272b2ffac6985da5475d7e7c65d584e",
    )
    Path("recovered-weixin-login.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(result.get("status", "unknown"))


asyncio.run(main())
