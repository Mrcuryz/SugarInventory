"""Ground entry dates in user statements, not model defaults or old report facts.

This is a tool-argument guard, not an intent router. The expert still decides
whether to clarify or call the existing preview tool.
"""
from __future__ import annotations

import re
from typing import Any

from app.business_time import BusinessClock


class AssayInputClarification(ValueError):
    """A recoverable input gap that should be explained to the user."""


class ProductQualifierClarification(ValueError):
    """The expert dropped a user-specified product constraint."""


DATE_PROMPT = "这份样品是哪天取的？可以说‘今天’、‘昨天’或具体日期。已说的检测值会保留，本次尚未保存。"
UNCERTAIN = re.compile(r"可能|大概|好像|应该|记不清|不确定|或者|要么")
SAMPLING = re.compile(r"采样|取样|留样|取的样|留的样|采的样|样.{0,12}(?:取|采|留)")
DATE_ONLY = re.compile(r"^(?:是|就|改成|改为|不是|不对|日期|那就|用|按|的|吧|对|，|,|\s)*"
                       r"(?:今天|今日|昨天|昨日|前天|大前天|\d{4}[-/]\d{1,2}[-/]\d{1,2}|(?:\d{4}年)?\d{1,2}月\d{1,2}[日号]?)"
                       r"(?:的|吧|对|没错|取的|采的|，|,|。|\s)*$")


def grounded_sample_date(messages: list[dict[str, Any]], user_message: str,
                         proposed: str, clock: BusinessClock) -> str:
    """Latest explicit sampling statement wins; measurement time is not sampling time.

Short date replies are accepted only after a sampling question or an established
sampling statement. All normalized values still pass the existing backend bounds.
"""
    history = list(messages[-16:])
    if not history or history[-1].get("role") != "user" or history[-1].get("content") != user_message:
        history.append({"role": "user", "content": user_message})
    confirmed = None
    issue = None
    awaiting_date = False
    for message in history:
        text = str(message.get("content") or "")
        if message.get("role") == "assistant":
            awaiting_date = bool(SAMPLING.search(text) and re.search(r"哪天|哪一天|日期.*[？?]|[？?].*日期", text))
            continue
        if message.get("role") != "user":
            continue
        # A date in an unrelated report query must not silently supply an entry date.
        for clause in re.split(r"[，,。；;！？!?\n]", text):
            if not clause.strip():
                continue
            sampling = bool(SAMPLING.search(clause))
            date_reply = bool((awaiting_date or confirmed) and DATE_ONLY.fullmatch(clause.strip()))
            date_correction = bool(confirmed and re.search(r"日期.{0,6}(?:改|错|是)|(?:改|错).{0,6}日期", clause))
            if not (sampling or date_reply or date_correction):
                continue
            if UNCERTAIN.search(clause):
                confirmed = None
                issue = "采样日期还不确定，请先确认是哪天取的样。检测值会保留，本次尚未保存。"
                continue
            resolved = clock.resolve(clause)
            if resolved is None:
                # '刚测/刚取' does not identify a calendar sampling date.
                continue
            if not resolved.is_exact:
                confirmed = None
                issue = DATE_PROMPT
                continue
            # The general read-only resolver prioritizes relative words. Entry must
            # not choose one of multiple conflicting days within the same clause.
            relative_days = set(re.findall(r"大前天|前天|昨天|昨日|今天|今日", clause))
            if len(relative_days) > 1:
                confirmed = None
                issue = "这里提到了不同日期，请确认这份样品具体是哪天取的。本次尚未保存。"
                continue
            confirmed = resolved.start.isoformat()
            issue = None
            awaiting_date = False
    if confirmed is None:
        raise AssayInputClarification(issue or DATE_PROMPT)
    if proposed != confirmed:
        raise AssayInputClarification("采样日期与用户最后明确提供的日期不一致，请按用户说明重新预览；不确定时先追问，不要默认今天。")
    return confirmed


def validate_product_qualifiers(query: str, message: str) -> None:
    """Do not discard an explicit packaging qualifier during entity discovery."""
    for phrase, token in (("袋装", "袋"), ("箱装", "箱"), ("桶装", "桶")):
        mentions = list(re.finditer(re.escape(phrase), message))
        if not mentions:
            continue
        preceding = message[max(0, mentions[-1].start() - 6):mentions[-1].start()]
        negated = bool(re.search(r"(?:不是|不要|不选|别选|不用)\s*$", preceding))
        if not negated and token not in query:
            raise ProductQualifierClarification(f"产品解析遗漏了用户明确限定的{phrase}。请在产品查询中保留该限定（如黄冰糖（袋）），不能退回全部包装；如限定互相矛盾则追问。")
