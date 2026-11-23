"""Execute the mapper UPDATE statements in isolated SQLite to check attribution.

No network or production database access. MySQL migration readiness is checked
separately by the backend suite; this verifies the portable UPDATE semantics.
"""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/com/Laibin/SugarInventory/production/mapper/ProductionDailyReportSectionMapper.java"


def mapper_updates():
    source = SOURCE.read_text(encoding="utf-8")
    return {
        name: re.sub(r"#\{(\w+)\}", r":\1", "".join(
            json.loads(literal) for literal in re.findall(r'"(?:[^"\\]|\\.)*"', expression)))
        for expression, name in re.findall(r"@Update\((.*?)\)\s*int (\w+)\(", source, re.S)
    }


class WorkshopPreparerTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.execute("""CREATE TABLE production_daily_report_section (
            id INTEGER PRIMARY KEY, report_id INTEGER, version INTEGER,
            status TEXT, prepared_by_name TEXT, updated_by INTEGER,
            updated_by_name TEXT, updated_at TEXT, submitted_by INTEGER,
            submitted_by_name TEXT, submitted_at TEXT)""")
        self.db.executemany("""INSERT INTO production_daily_report_section
            (id, report_id, version, status) VALUES (?, ?, 0, 'DRAFT')""",
            [(1, 10), (2, 10), (3, 11)])
        self.sql = mapper_updates()

    def run_update(self, name, **values):
        params = dict(id=1, reportId=10, version=0, operatorId=7,
                      operatorName="多晶填写人", updatedAt="2026-11-18",
                      submittedAt="2026-11-18")
        params.update(values)
        return self.db.execute(self.sql[name], params).rowcount

    def authors(self):
        return self.db.execute("SELECT prepared_by_name FROM production_daily_report_section ORDER BY id").fetchall()

    def test_different_workshops_and_submission_keep_their_own_authors(self):
        self.assertEqual(1, self.run_update("updateForSave"))
        self.assertEqual(1, self.run_update("updateForSave", id=2, operatorName="红糖填写人"))
        self.run_update("submitSection", operatorName="车间审核人")
        self.run_update("submitByReportId", operatorName="整表提交人")
        self.assertEqual([("多晶填写人",), ("红糖填写人",), (None,)], self.authors())

    def test_stale_save_and_rollback_do_not_change_author(self):
        self.run_update("updateForSave")
        self.db.commit()
        self.assertEqual(0, self.run_update("updateForSave", operatorName="旧草稿用户"))
        self.run_update("updateForSave", version=1, operatorName="切换后的用户")
        self.assertEqual(("切换后的用户",), self.authors()[0])
        self.db.rollback()
        self.assertEqual(("多晶填写人",), self.authors()[0])

    def test_submitting_historical_section_does_not_guess_author(self):
        self.run_update("submitByReportId", operatorName="整表提交人")
        self.assertEqual([(None,), (None,), (None,)], self.authors())


if __name__ == "__main__":
    unittest.main()
