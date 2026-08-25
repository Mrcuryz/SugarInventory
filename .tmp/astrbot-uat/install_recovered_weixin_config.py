import json
from pathlib import Path


result_path = Path("weixin-login-result.json")
config_path = Path("data/cmd_config.json")

login = json.loads(result_path.read_text(encoding="utf-8"))
config = json.loads(config_path.read_text(encoding="utf-8-sig"))

platform = {
    "id": "weixin_personal",
    "type": "weixin_oc",
    "enable": True,
    "weixin_oc_base_url": login["weixin_oc_base_url"],
    "weixin_oc_bot_type": "3",
    "weixin_oc_qr_poll_interval": 1,
    "weixin_oc_long_poll_timeout_ms": 35_000,
    "weixin_oc_api_timeout_ms": 120_000,
    "weixin_oc_token": login["weixin_oc_token"],
    "weixin_oc_account_id": login["weixin_oc_account_id"],
    "weixin_oc_user_id": login.get("weixin_oc_user_id", ""),
}

platforms = config.setdefault("platform", [])
platforms[:] = [item for item in platforms if item.get("id") != "weixin_personal"]
platforms.append(platform)

config.setdefault("platform_settings", {})["enable_id_white_list"] = False
config.setdefault("provider_settings", {})["enable"] = False
config["log_level"] = "DEBUG"
config["log_file_enable"] = True
config["log_file_path"] = "logs/astrbot-weixin-uat.log"

config_path.write_text(
    json.dumps(config, ensure_ascii=False, indent=2) + "\n",
    encoding="utf-8",
)
print("WEIXIN_CONFIG_INSTALLED")
