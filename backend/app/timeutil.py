"""Days and months are Vietnam calendar days; storage is UTC."""
from datetime import date, datetime, time, timedelta, timezone
from zoneinfo import ZoneInfo

VN_TZ = ZoneInfo("Asia/Ho_Chi_Minh")


def today_vn() -> date:
    return datetime.now(VN_TZ).date()


def day_range(first: date, last: date) -> tuple[datetime, datetime]:
    """UTC [start, end) covering Vietnam days first..last inclusive."""
    start = datetime.combine(first, time.min, VN_TZ)
    end = datetime.combine(last + timedelta(days=1), time.min, VN_TZ)
    return start.astimezone(timezone.utc), end.astimezone(timezone.utc)


def parse_month(value: str) -> date:
    """'YYYY-MM' -> first day of that month. Raises ValueError."""
    year, month = value.split("-")
    if len(year) != 4 or len(month) != 2:
        raise ValueError(value)
    return date(int(year), int(month), 1)


def shift_month(first: date, months: int) -> date:
    index = first.year * 12 + first.month - 1 + months
    return date(index // 12, index % 12 + 1, 1)


def month_range(first: date) -> tuple[datetime, datetime]:
    """UTC [start, end) covering the Vietnam calendar month starting at `first`."""
    return day_range(first, shift_month(first, 1) - timedelta(days=1))
