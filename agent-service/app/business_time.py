from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta, timezone
import re
from typing import Any


BEIJING_TIMEZONE_NAME = "Asia/Shanghai"
BEIJING_TIMEZONE = timezone(timedelta(hours=8), name=BEIJING_TIMEZONE_NAME)


@dataclass(frozen=True)
class BusinessDateRange:
    label: str
    start: date
    end: date
    rolling_days: int | None = None

    @property
    def is_exact(self) -> bool:
        return self.start == self.end

    def to_tool_date_range(self) -> dict[str, Any]:
        if self.rolling_days is not None:
            return {"type": "LAST_DAYS", "days": self.rolling_days}
        if self.is_exact:
            return {"type": "EXACT", "date": self.start.isoformat()}
        return {
            "type": "RANGE",
            "from": self.start.isoformat(),
            "to": self.end.isoformat(),
        }


class BusinessClock:
    """Authoritative business clock and Chinese relative-date resolver."""

    def __init__(self, now_provider: Callable[[], datetime] | None = None) -> None:
        self._now_provider = now_provider or (lambda: datetime.now(BEIJING_TIMEZONE))

    def now(self) -> datetime:
        value = self._now_provider()
        if value.tzinfo is None:
            return value.replace(tzinfo=BEIJING_TIMEZONE)
        return value.astimezone(BEIJING_TIMEZONE)

    def today(self) -> date:
        return self.now().date()

    def context(self) -> dict[str, Any]:
        now = self.now()
        today = now.date()
        this_week = self._this_week(today)
        last_week = self._last_week(today)
        this_month = self._this_month(today)
        last_month = self._last_month(today)
        return {
            "trustedSource": "SERVER_RUNTIME",
            "timezone": BEIJING_TIMEZONE_NAME,
            "utcOffset": "+08:00",
            "currentDateTime": now.isoformat(timespec="seconds"),
            "currentDate": today.isoformat(),
            "weekStartsOn": "MONDAY",
            "today": self._range_payload(BusinessDateRange("今天", today, today)),
            "yesterday": self._range_payload(
                BusinessDateRange("昨天", today - timedelta(days=1), today - timedelta(days=1))
            ),
            "thisWeek": self._range_payload(this_week),
            "lastWeek": self._range_payload(last_week),
            "thisMonth": self._range_payload(this_month),
            "lastMonth": self._range_payload(last_month),
        }

    def resolve(self, message: str) -> BusinessDateRange | None:
        text = re.sub(r"\s+", "", message or "")
        if not text:
            return None
        today = self.today()

        if any(marker in text for marker in ("大前天",)):
            target = today - timedelta(days=3)
            return BusinessDateRange("大前天", target, target)
        if any(marker in text for marker in ("前天",)):
            target = today - timedelta(days=2)
            return BusinessDateRange("前天", target, target)
        if any(marker in text for marker in ("昨天", "昨日")):
            target = today - timedelta(days=1)
            return BusinessDateRange("昨天", target, target)
        if any(marker in text for marker in ("今天", "今日", "当天")):
            return BusinessDateRange("今天", today, today)

        explicit_range = self._resolve_explicit_date_range(text, today)
        if explicit_range is not None:
            return explicit_range

        explicit_date = self._resolve_explicit_date(text, today)
        if explicit_date is not None:
            return explicit_date

        rolling_match = re.search(
            r"(?:最近|近|过去)([0-9零〇一二两三四五六七八九十百]{1,6})天",
            text,
        )
        if rolling_match:
            days = _parse_positive_integer(rolling_match.group(1))
            if days is not None and 1 <= days <= 366:
                return BusinessDateRange(
                    f"最近{days}天",
                    today - timedelta(days=days - 1),
                    today,
                    rolling_days=days,
                )
        if any(marker in text for marker in ("最近一周", "近一周", "过去一周")):
            return BusinessDateRange("最近7天", today - timedelta(days=6), today, rolling_days=7)
        if any(marker in text for marker in ("最近半个月", "近半个月", "过去半个月")):
            return BusinessDateRange("最近15天", today - timedelta(days=14), today, rolling_days=15)
        if any(marker in text for marker in ("最近一个月", "近一个月", "过去一个月")):
            return BusinessDateRange("最近30天", today - timedelta(days=29), today, rolling_days=30)
        if any(marker in text for marker in ("最近半年", "近半年", "过去半年")):
            return BusinessDateRange("最近180天", today - timedelta(days=179), today, rolling_days=180)

        if any(marker in text for marker in ("上周", "上星期", "上个星期")):
            return self._last_week(today)
        if any(marker in text for marker in ("本周", "这周", "本星期", "这个星期")):
            return self._this_week(today)
        if any(marker in text for marker in ("上个月", "上月")):
            return self._last_month(today)
        if any(marker in text for marker in ("本月", "这个月", "当月")):
            return self._this_month(today)
        if any(marker in text for marker in ("上季度", "上个季度")):
            return self._last_quarter(today)
        if any(marker in text for marker in ("本季度", "这个季度", "当季")):
            return self._this_quarter(today)
        if any(marker in text for marker in ("去年", "上年度")):
            return BusinessDateRange("去年", date(today.year - 1, 1, 1), date(today.year - 1, 12, 31))
        if any(marker in text for marker in ("今年", "本年", "本年度")):
            return BusinessDateRange("今年", date(today.year, 1, 1), today)
        return None

    @staticmethod
    def _resolve_explicit_date_range(text: str, today: date) -> BusinessDateRange | None:
        chinese_pattern = re.compile(
            r"(?<!\d)(?:(?P<start_year>20\d{2})年)?"
            r"(?P<start_month>0?[1-9]|1[0-2])月"
            r"(?P<start_day>0?[1-9]|[12]\d|3[01])(?:日|号)?"
            r"(?:至|到|~|～|—)"
            r"(?:(?P<end_year>20\d{2})年)?"
            r"(?:(?P<end_month>0?[1-9]|1[0-2])月)?"
            r"(?P<end_day>0?[1-9]|[12]\d|3[01])(?:日|号)"
        )
        iso_pattern = re.compile(
            r"(?<!\d)(?P<start_year>20\d{2})[-/.]"
            r"(?P<start_month>0?[1-9]|1[0-2])[-/.]"
            r"(?P<start_day>0?[1-9]|[12]\d|3[01])"
            r"(?:至|到|~|～|—)"
            r"(?P<end_year>20\d{2})[-/.]"
            r"(?P<end_month>0?[1-9]|1[0-2])[-/.]"
            r"(?P<end_day>0?[1-9]|[12]\d|3[01])(?!\d)"
        )
        for pattern in (chinese_pattern, iso_pattern):
            match = pattern.search(text)
            if match is None:
                continue
            try:
                start_year = int(match.group("start_year") or today.year)
                end_year = int(match.group("end_year") or start_year)
                start_month = int(match.group("start_month"))
                end_month = int(match.group("end_month") or start_month)
                start = date(start_year, start_month, int(match.group("start_day")))
                end = date(end_year, end_month, int(match.group("end_day")))
            except ValueError:
                return None
            if end < start:
                raise ValueError("explicit date range end must not be earlier than start")
            return BusinessDateRange(match.group(0), start, end)
        return None

    @staticmethod
    def _resolve_explicit_date(text: str, today: date) -> BusinessDateRange | None:
        patterns = (
            re.compile(
                r"(?<!\d)(?:(?P<year>20\d{2})年)?"
                r"(?P<month>0?[1-9]|1[0-2])月"
                r"(?P<day>0?[1-9]|[12]\d|3[01])(?:日|号)"
            ),
            re.compile(
                r"(?<!\d)(?P<year>20\d{2})[-/.]"
                r"(?P<month>0?[1-9]|1[0-2])[-/.]"
                r"(?P<day>0?[1-9]|[12]\d|3[01])(?!\d)"
            ),
        )
        for pattern in patterns:
            for match in pattern.finditer(text):
                try:
                    target = date(
                        int(match.group("year") or today.year),
                        int(match.group("month")),
                        int(match.group("day")),
                    )
                except ValueError:
                    continue
                return BusinessDateRange(match.group(0), target, target)
        return None

    def normalize_tool_arguments(
        self,
        tool_name: str,
        arguments: dict[str, Any],
        user_message: str,
        tool_schema: dict[str, Any] | None,
    ) -> dict[str, Any]:
        resolved = self.resolve(user_message)
        result = dict(arguments)
        if resolved is None or not isinstance(tool_schema, dict):
            return result
        properties = tool_schema.get("properties")
        if not isinstance(properties, dict):
            return result

        if "dateRange" in properties:
            result["dateRange"] = resolved.to_tool_date_range()

        if "productionDate" in properties:
            if not resolved.is_exact:
                raise ValueError(
                    f"{tool_name} accepts one productionDate, but the user requested {resolved.label}"
                )
            result["productionDate"] = resolved.start.isoformat()

        for start_key, end_key in (
            ("entryDateStart", "entryDateEnd"),
            ("productionDateStart", "productionDateEnd"),
            ("startDate", "endDate"),
        ):
            if start_key in properties or end_key in properties:
                result[start_key] = resolved.start.isoformat()
                result[end_key] = resolved.end.isoformat()

        if "startTime" in properties or "endTime" in properties:
            result["startTime"] = datetime.combine(
                resolved.start, time.min, tzinfo=BEIJING_TIMEZONE
            ).isoformat(timespec="seconds")
            result["endTime"] = datetime.combine(
                resolved.end, time.max.replace(microsecond=0), tzinfo=BEIJING_TIMEZONE
            ).isoformat(timespec="seconds")

        if "from" in properties or "to" in properties:
            result["from"] = datetime.combine(
                resolved.start, time.min, tzinfo=BEIJING_TIMEZONE
            ).isoformat(timespec="seconds")
            result["to"] = datetime.combine(
                resolved.end, time.max.replace(microsecond=0), tzinfo=BEIJING_TIMEZONE
            ).isoformat(timespec="seconds")

        status_filter_schema = properties.get("statusFilter")
        if isinstance(status_filter_schema, dict):
            nested_properties = status_filter_schema.get("properties")
            if isinstance(nested_properties, dict) and (
                "entryDateFrom" in nested_properties or "entryDateTo" in nested_properties
            ):
                status_filter = result.get("statusFilter")
                clean_filter = dict(status_filter) if isinstance(status_filter, dict) else {}
                clean_filter["entryDateFrom"] = resolved.start.isoformat()
                clean_filter["entryDateTo"] = resolved.end.isoformat()
                result["statusFilter"] = clean_filter
        return result

    @staticmethod
    def _range_payload(value: BusinessDateRange) -> dict[str, str]:
        return {
            "label": value.label,
            "from": value.start.isoformat(),
            "to": value.end.isoformat(),
        }

    @staticmethod
    def _this_week(today: date) -> BusinessDateRange:
        start = today - timedelta(days=today.weekday())
        return BusinessDateRange("本周", start, today)

    @staticmethod
    def _last_week(today: date) -> BusinessDateRange:
        this_week_start = today - timedelta(days=today.weekday())
        end = this_week_start - timedelta(days=1)
        return BusinessDateRange("上周", end - timedelta(days=6), end)

    @staticmethod
    def _this_month(today: date) -> BusinessDateRange:
        return BusinessDateRange("本月", today.replace(day=1), today)

    @staticmethod
    def _last_month(today: date) -> BusinessDateRange:
        this_month_start = today.replace(day=1)
        end = this_month_start - timedelta(days=1)
        return BusinessDateRange("上月", end.replace(day=1), end)

    @staticmethod
    def _this_quarter(today: date) -> BusinessDateRange:
        start_month = ((today.month - 1) // 3) * 3 + 1
        return BusinessDateRange("本季度", date(today.year, start_month, 1), today)

    @staticmethod
    def _last_quarter(today: date) -> BusinessDateRange:
        current_start_month = ((today.month - 1) // 3) * 3 + 1
        current_start = date(today.year, current_start_month, 1)
        end = current_start - timedelta(days=1)
        start_month = ((end.month - 1) // 3) * 3 + 1
        return BusinessDateRange("上季度", date(end.year, start_month, 1), end)


def _parse_positive_integer(value: str) -> int | None:
    if value.isdigit():
        return int(value)
    normalized = value.replace("两", "二").replace("〇", "零")
    digits = {"零": 0, "一": 1, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9}
    if all(char in digits for char in normalized):
        return int("".join(str(digits[char]) for char in normalized))
    total = 0
    current = 0
    for char in normalized:
        if char in digits:
            current = digits[char]
        elif char == "十":
            total += (current or 1) * 10
            current = 0
        elif char == "百":
            total += (current or 1) * 100
            current = 0
        else:
            return None
    return total + current
