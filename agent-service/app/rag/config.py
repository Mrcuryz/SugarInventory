from __future__ import annotations

from pathlib import Path


SCHEMA_VERSION = 1
CORPUS_ID = "laibin-warehouse-knowledge"
SOURCE_BASIS = "CLIENT_CONFIRMED_CURRENT"
TIMEZONE = "Asia/Shanghai"
ALLOWED_ROLES = ("ADMIN", "SUPER_ADMIN")
SUPPORTED_SOURCE_SUFFIXES = frozenset({".docx", ".pdf"})

PROJECT_ROOT = Path(__file__).resolve().parents[3]
LOCAL_BUILD_ROOT = PROJECT_ROOT / "agent-service" / "build" / "rag"
DEPLOY_ARTIFACT_ROOT = PROJECT_ROOT / "deploy" / "simple" / "artifacts" / "rag"
