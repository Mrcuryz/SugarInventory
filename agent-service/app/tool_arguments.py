from __future__ import annotations

from dataclasses import replace
from typing import Any
from collections.abc import Iterator
from datetime import date
import re
import time

from app.agents import MAIN_AGENT, AgentHandoff, AgentHandoffRouter
from app.business_time import BusinessClock
from app.cancellation import RunCancelledError
from app.context import ContextBuilder
from app.goal_contracts import registered_goal_for_plan
from app.graph.state import WarehouseAgentState
from app.knowledge import IntentRoute, IntentRouter
from app.model import (
    BasicModelClient,
    GoalDraftRequest,
    ModelArgumentRequest,
    ModelClient,
    ModelDirectAnswerRequest,
    ModelPlanDecision,
    ModelPlanRequest,
    ModelStreamError,
)
from app.orchestration import CompoundIntentPlanner, new_orchestration_state
from app.tools.client import ALLOWED_TOOLS


PRODUCT_SCOPE_SCHEMA: dict[str, Any] = {
    "type": "object",
    "additionalProperties": False,
    "required": ["type"],
    "properties": {
        "type": {"type": "string", "enum": ["SINGLE_PRODUCT", "EXACT_PRODUCT_NAME_GROUP", "PRODUCT_TYPE_GROUP", "ALL"]},
        "productId": {"type": "integer", "minimum": 1},
        "productName": {"type": "string", "minLength": 1, "maxLength": 100},
        "productType": {"type": "string", "minLength": 1, "maxLength": 50},
    },
}

DATE_RANGE_SCHEMA: dict[str, Any] = {
    "type": "object",
    "additionalProperties": False,
    "required": ["type"],
    "properties": {
        "type": {"type": "string", "enum": ["EXACT", "LAST_DAYS", "RANGE"]},
        "date": {"type": "string", "format": "date"},
        "days": {"type": "integer", "minimum": 1, "maximum": 366},
        "from": {"type": "string", "format": "date"},
        "to": {"type": "string", "format": "date"},
    },
}


TOOL_SCHEMAS: dict[str, dict[str, Any]] = {
    "resolve_products": {
        "type": "object",
        "additionalProperties": False,
        "required": ["query"],
        "properties": {
            "query": {"type": "string", "minLength": 1, "maxLength": 100},
            "limit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 10},
        },
    },
    "resolve_warehouses": {
        "type": "object",
        "additionalProperties": False,
        "required": ["query"],
        "properties": {
            "query": {"type": "string", "minLength": 1, "maxLength": 100},
            "limit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 10},
        },
    },
    "get_pallet_status": {
        "type": "object",
        "additionalProperties": False,
        "required": ["code"],
        "properties": {
            "code": {"type": "string", "minLength": 1, "maxLength": 100},
            "includeInventory": {"type": "boolean", "default": True},
            "includeAssay": {"type": "boolean", "default": True},
            "includeFlows": {"type": "boolean", "default": True},
            "flowLimit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 20},
        },
    },
    "get_inventory_overview": {
        "type": "object",
        "additionalProperties": False,
        "required": ["productId"],
        "properties": {
            "productId": {"type": "integer", "minimum": 1},
        },
    },
    "get_inventory_distribution": {
        "type": "object",
        "additionalProperties": False,
        "required": ["productScope", "warehouseScope", "groupBy"],
        "properties": {
            "productScope": {
                "type": "object",
                "additionalProperties": False,
                "required": ["type"],
                "properties": {
                    "type": {"type": "string", "enum": ["SINGLE_PRODUCT", "EXACT_PRODUCT_NAME_GROUP", "PRODUCT_TYPE_GROUP", "ALL"]},
                    "productId": {"type": "integer", "minimum": 1},
                    "productName": {"type": "string", "minLength": 1, "maxLength": 100},
                    "productType": {"type": "string", "minLength": 1, "maxLength": 50},
                },
            },
            "warehouseScope": {
                "type": "object",
                "additionalProperties": False,
                "required": ["type"],
                "properties": {
                    "type": {"type": "string", "enum": ["ALL", "SINGLE_WAREHOUSE"]},
                    "warehouseId": {"type": "integer", "minimum": 1},
                },
            },
            "statusFilter": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "productStatuses": {"type": "array", "maxItems": 10, "items": {"enum": ["半成品", "成品"]}},
                    "warehouseStatuses": {"type": "array", "maxItems": 10, "items": {"enum": ["正常", "空置", "满仓", "维护", "临期预警"]}},
                    "palletStatuses": {"type": "array", "maxItems": 10, "items": {"enum": ["FREE", "PENDING", "INSTOCK", "INVALID", "ORDER_RESERVED"]}},
                    "assayStatus": {"enum": ["HAS_ASSAY", "MISSING_ASSAY", "PASS", "FAIL", "NO_STANDARD", "MULTIPLE_CANDIDATES"]},
                    "entryDateFrom": {"type": "string", "format": "date"},
                    "entryDateTo": {"type": "string", "format": "date"},
                },
            },
            "groupBy": {"type": "string", "enum": ["warehouse", "product", "warehouse_product"]},
            "limit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 20},
        },
    },
    "query_assay_records": {
        "type": "object",
        "additionalProperties": False,
        "required": ["productScope"],
        "properties": {
            "productScope": {
                "type": "object",
                "additionalProperties": False,
                "required": ["type"],
                "properties": {
                    "type": {"type": "string", "enum": ["SINGLE_PRODUCT", "EXACT_PRODUCT_NAME_GROUP", "PRODUCT_TYPE_GROUP", "ALL"]},
                    "productId": {"type": "integer", "minimum": 1},
                    "productName": {"type": "string", "minLength": 1, "maxLength": 100},
                    "productType": {"type": "string", "minLength": 1, "maxLength": 50},
                },
            },
            "dateRange": {
                "type": "object",
                "additionalProperties": False,
                "required": ["type"],
                "properties": {
                    "type": {"type": "string", "enum": ["EXACT", "LAST_DAYS", "RANGE"]},
                    "date": {"type": "string", "format": "date"},
                    "days": {"type": "integer", "minimum": 1, "maximum": 366},
                    "from": {"type": "string", "format": "date"},
                    "to": {"type": "string", "format": "date"},
                },
            },
            "judgeStatus": {"type": "string", "enum": ["ANY", "PASS", "FAILED", "NO_STANDARD", "MULTIPLE_CANDIDATES"], "default": "ANY"},
            "sortBy": {"type": "string", "enum": ["sampleDate", "createdAt"], "default": "sampleDate"},
            "sortDirection": {"type": "string", "enum": ["ASC", "DESC"], "default": "DESC"},
            "page": {"type": "integer", "minimum": 1, "default": 1},
            "size": {"type": "integer", "minimum": 1, "maximum": 100, "default": 20},
        },
    },
    "get_assay_report_detail": {
        "type": "object",
        "additionalProperties": False,
        "required": ["reportRef"],
        "properties": {
            "reportRef": {"type": "string", "minLength": 1, "maxLength": 200},
            "includeMetrics": {"type": "boolean", "default": True},
            "includeStandardSnapshot": {"type": "boolean", "default": True},
        },
    },
    "query_assay_abnormalities": {
        "type": "object",
        "additionalProperties": False,
        "required": ["productScope"],
        "properties": {
            "productScope": {
                "type": "object",
                "additionalProperties": False,
                "required": ["type"],
                "properties": {
                    "type": {"type": "string", "enum": ["SINGLE_PRODUCT", "EXACT_PRODUCT_NAME_GROUP", "PRODUCT_TYPE_GROUP", "ALL"]},
                    "productId": {"type": "integer", "minimum": 1},
                    "productName": {"type": "string", "minLength": 1, "maxLength": 100},
                    "productType": {"type": "string", "minLength": 1, "maxLength": 50},
                },
            },
            "dateRange": {
                "type": "object",
                "additionalProperties": False,
                "required": ["type"],
                "properties": {
                    "type": {"type": "string", "enum": ["EXACT", "LAST_DAYS", "RANGE"]},
                    "date": {"type": "string", "format": "date"},
                    "days": {"type": "integer", "minimum": 1, "maximum": 366},
                    "from": {"type": "string", "format": "date"},
                    "to": {"type": "string", "format": "date"},
                },
            },
            "abnormalTypes": {
                "type": "array",
                "minItems": 1,
                "maxItems": 3,
                "items": {"type": "string", "enum": ["FAILED", "NO_STANDARD", "MULTIPLE_CANDIDATES"]},
                "default": ["FAILED", "NO_STANDARD", "MULTIPLE_CANDIDATES"],
            },
            "groupBy": {"type": "string", "enum": ["product", "date", "abnormal_type", "metric"], "default": "product"},
            "limit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 50},
        },
    },
    "query_products_without_recent_assay": {
        "type": "object",
        "additionalProperties": False,
        "required": ["productScope", "warehouseScope"],
        "properties": {
            "productScope": {
                "type": "object",
                "additionalProperties": False,
                "required": ["type"],
                "properties": {
                    "type": {"type": "string", "enum": ["SINGLE_PRODUCT", "EXACT_PRODUCT_NAME_GROUP", "PRODUCT_TYPE_GROUP", "ALL"]},
                    "productId": {"type": "integer", "minimum": 1},
                    "productName": {"type": "string", "minLength": 1, "maxLength": 100},
                    "productType": {"type": "string", "minLength": 1, "maxLength": 50},
                },
            },
            "warehouseScope": {
                "type": "object",
                "additionalProperties": False,
                "required": ["type"],
                "properties": {
                    "type": {"type": "string", "enum": ["ALL", "SINGLE_WAREHOUSE"]},
                    "warehouseId": {"type": "integer", "minimum": 1},
                },
            },
            "population": {"type": "string", "enum": ["CURRENT_INVENTORY"], "default": "CURRENT_INVENTORY"},
            "dateRange": {
                "type": "object",
                "additionalProperties": False,
                "required": ["type"],
                "properties": {
                    "type": {"type": "string", "enum": ["EXACT", "LAST_DAYS", "RANGE"]},
                    "date": {"type": "string", "format": "date"},
                    "days": {"type": "integer", "minimum": 1, "maximum": 366},
                    "from": {"type": "string", "format": "date"},
                    "to": {"type": "string", "format": "date"},
                },
            },
            "groupBy": {"type": "string", "enum": ["product", "warehouse", "product_warehouse"], "default": "product"},
            "limit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 50},
        },
    },
    "query_assay_standard_coverage": {
        "type": "object",
        "additionalProperties": False,
        "required": ["productScope"],
        "properties": {
            "productScope": {
                "type": "object",
                "additionalProperties": False,
                "required": ["type"],
                "properties": {
                    "type": {"type": "string", "enum": ["SINGLE_PRODUCT", "EXACT_PRODUCT_NAME_GROUP", "PRODUCT_TYPE_GROUP", "ALL"]},
                    "productId": {"type": "integer", "minimum": 1},
                    "productName": {"type": "string", "minLength": 1, "maxLength": 100},
                    "productType": {"type": "string", "minLength": 1, "maxLength": 50},
                },
            },
            "dateRange": {
                "type": "object",
                "additionalProperties": False,
                "required": ["type"],
                "properties": {
                    "type": {"type": "string", "enum": ["EXACT", "LAST_DAYS", "RANGE"]},
                    "date": {"type": "string", "format": "date"},
                    "days": {"type": "integer", "minimum": 1, "maximum": 366},
                    "from": {"type": "string", "format": "date"},
                    "to": {"type": "string", "format": "date"},
                },
            },
            "coverageType": {"type": "string", "enum": ["PRODUCT_WITHOUT_STANDARD", "ASSAY_WITHOUT_STANDARD", "UNUSED_STANDARD"], "default": "PRODUCT_WITHOUT_STANDARD"},
            "limit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 50},
        },
    },
    "query_qr_code_lifecycle": {
        "type": "object",
        "additionalProperties": False,
        "required": ["code"],
        "properties": {
            "code": {"type": "string", "minLength": 1, "maxLength": 100},
            "includeInventory": {"type": "boolean", "default": True},
            "includeAssay": {"type": "boolean", "default": True},
            "includeFlows": {"type": "boolean", "default": True},
            "includePrintInfo": {"type": "boolean", "default": False},
            "flowLimit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 50},
        },
    },
    "query_printed_not_inbound_codes": {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "productScope": PRODUCT_SCOPE_SCHEMA,
            "orderNo": {"type": "string", "minLength": 1, "maxLength": 50},
            "batchNo": {"type": "string", "minLength": 1, "maxLength": 80},
            "dateRange": DATE_RANGE_SCHEMA,
            "groupBy": {"type": "string", "enum": ["batch", "order", "product"], "default": "batch"},
            "limit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 50},
        },
    },
    "query_pallet_anomalies": {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "productScope": PRODUCT_SCOPE_SCHEMA,
            "warehouseId": {"type": "integer", "minimum": 1},
            "dateRange": DATE_RANGE_SCHEMA,
            "anomalyTypes": {"type": "array", "minItems": 1, "maxItems": 5, "items": {"type": "string", "enum": ["VOID_CODE_SCANNED", "STATUS_INVENTORY_MISMATCH", "DUPLICATE_INBOUND", "OUTBOUND_WITHOUT_INBOUND", "PRODUCT_BINDING_MISMATCH"]}},
            "limit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 50},
        },
    },
    "query_pallet_flow_records": {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "code": {"type": "string", "minLength": 1, "maxLength": 100},
            "productScope": PRODUCT_SCOPE_SCHEMA,
            "warehouseId": {"type": "integer", "minimum": 1},
            "dateRange": DATE_RANGE_SCHEMA,
            "eventTypes": {"type": "array", "maxItems": 8, "items": {"type": "string", "enum": ["INBOUND", "OUTBOUND", "TRANSFER", "BIND", "ASSAY", "CANCEL", "LABEL"]}},
            "page": {"type": "integer", "minimum": 1, "default": 1},
            "size": {"type": "integer", "minimum": 1, "maximum": 100, "default": 20},
        },
    },
    "query_qr_batch_inbound_completion": {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "batchNo": {"type": "string", "minLength": 1, "maxLength": 80},
            "orderNo": {"type": "string", "minLength": 1, "maxLength": 50},
            "productId": {"type": "integer", "minimum": 1},
            "dateRange": DATE_RANGE_SCHEMA,
            "includeUnfinishedExamples": {"type": "boolean", "default": True},
            "limit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 20},
        },
    },
    "resolve_production_entities": {
        "type": "object",
        "additionalProperties": False,
        "required": ["entityType", "query"],
        "properties": {
            "entityType": {"type": "string", "enum": ["PRODUCTION_ORDER", "BOILING_BATCH"]},
            "query": {"type": "string", "minLength": 1, "maxLength": 100},
            "limit": {"type": "integer", "minimum": 1, "maximum": 10, "default": 5},
        },
    },
    "query_production_order_progress": {
        "type": "object",
        "additionalProperties": False,
        "required": ["orderRef"],
        "properties": {"orderRef": {"type": "string", "minLength": 1, "maxLength": 500}},
    },
    "query_boiling_batch_trace": {
        "type": "object",
        "additionalProperties": False,
        "required": ["batchRef"],
        "properties": {"batchRef": {"type": "string", "minLength": 1, "maxLength": 500}},
    },
    "query_material_pick_trace": {
        "type": "object",
        "additionalProperties": False,
        "required": ["orderRef"],
        "properties": {"orderRef": {"type": "string", "minLength": 1, "maxLength": 500}},
    },
    "query_production_label_completion": {
        "type": "object",
        "additionalProperties": False,
        "required": ["orderRef"],
        "properties": {"orderRef": {"type": "string", "minLength": 1, "maxLength": 500}},
    },
    "query_in_process_materials": {
        "type": "object", "additionalProperties": False,
        "properties": {
            "productName": {"type": "string", "minLength": 1, "maxLength": 100},
            "productType": {"type": "string", "minLength": 1, "maxLength": 50},
            "productionDateStart": {"type": "string", "format": "date"},
            "productionDateEnd": {"type": "string", "format": "date"},
            "page": {"type": "integer", "minimum": 1},
            "size": {"type": "integer", "minimum": 1, "maximum": 50},
        },
    },
    "query_material_candidates": {
        "type": "object", "additionalProperties": False, "required": ["orderRef"],
        "properties": {"orderRef": {"type": "string", "minLength": 1, "maxLength": 500},
                       "page": {"type": "integer", "minimum": 1},
                       "size": {"type": "integer", "minimum": 1, "maximum": 50}},
    },
    "query_pallet_tasks": {
        "type": "object", "additionalProperties": False,
        "properties": {
            "code": {"type": "string", "minLength": 1, "maxLength": 100},
            "taskType": {"type": "string", "enum": ["IN", "SEMI_IN", "FINISH_IN", "OUT", "TRANSFER"]},
            "bizScene": {"type": "string", "enum": ["DIRECT_OUT", "PREPARE_CONSUMED", "FINISH_OUT"]},
            "status": {"type": "string", "enum": ["PENDING", "CONFIRMED", "CANCELED"]},
            "productName": {"type": "string", "minLength": 1, "maxLength": 100},
            "productType": {"type": "string", "minLength": 1, "maxLength": 50},
            "productStatus": {"type": "string", "enum": ["半成品", "成品"]},
            "targetWarehouseName": {"type": "string", "minLength": 1, "maxLength": 100},
            "productionDateStart": {"type": "string", "format": "date"},
            "productionDateEnd": {"type": "string", "format": "date"},
            "page": {"type": "integer", "minimum": 1},
            "size": {"type": "integer", "minimum": 1, "maximum": 50},
        },
    },
    "query_stock_documents": {
        "type": "object", "additionalProperties": False, "required": ["documentType"],
        "properties": {
            "documentType": {"type": "string", "enum": ["INBOUND", "OUTBOUND", "SEMI_PRODUCT"]},
            "productName": {"type": "string", "minLength": 1, "maxLength": 100},
            "warehouseName": {"type": "string", "minLength": 1, "maxLength": 100},
            "operatorName": {"type": "string", "minLength": 1, "maxLength": 100},
            "startDate": {"type": "string", "format": "date"}, "endDate": {"type": "string", "format": "date"},
            "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 50},
        },
    },
    "query_auto_inbound_batches": {
        "type": "object", "additionalProperties": False,
        "properties": {"status": {"type": "string", "minLength": 1, "maxLength": 50},
                       "limit": {"type": "integer", "minimum": 1, "maximum": 20}},
    },
    "get_auto_inbound_batch_detail": {
        "type": "object", "additionalProperties": False, "required": ["batchRef"],
        "properties": {"batchRef": {"type": "string", "minLength": 48, "maxLength": 100,
                                      "pattern": "^aibr_[A-Za-z0-9_-]+$"}},
    },
    "query_warehouse_capacity_distribution": {
        "type": "object", "additionalProperties": False,
        "properties": {
            "warehouseScope": {"type": "object", "additionalProperties": False, "required": ["type"],
                               "properties": {"type": {"type": "string", "enum": ["ALL", "SINGLE_WAREHOUSE"]},
                                              "warehouseId": {"type": "integer", "minimum": 1}}},
            "occupancyBand": {"type": "string", "enum": ["ANY", "EMPTY", "LOW", "MEDIUM", "HIGH", "FULL"]},
            "onlyAvailable": {"type": "boolean"}, "page": {"type": "integer", "minimum": 1},
            "size": {"type": "integer", "minimum": 1, "maximum": 50},
        },
    },
    "query_warehouse_recent_operations": {
        "type": "object", "additionalProperties": False,
        "properties": {"warehouseId": {"type": "integer", "minimum": 1},
                       "from": {"type": "string", "format": "date-time"},
                       "to": {"type": "string", "format": "date-time"},
                       "eventTypes": {"type": "array", "maxItems": 3,
                                      "items": {"type": "string", "enum": ["INBOUND", "OUTBOUND", "TRANSFER"]}},
                       "limit": {"type": "integer", "minimum": 1, "maximum": 50}},
    },
    "query_warehouse_mixed_storage_facts": {
        "type": "object", "additionalProperties": False,
        "properties": {"warehouseId": {"type": "integer", "minimum": 1},
                       "factType": {"type": "string", "enum": ["ANY", "MULTIPLE_PRODUCTS", "MULTIPLE_SPECIFICATIONS"]},
                       "limit": {"type": "integer", "minimum": 1, "maximum": 50}},
    },
    "query_product_catalog": {
        "type": "object", "additionalProperties": False,
        "properties": {"productName": {"type": "string", "minLength": 1, "maxLength": 100},
                       "productType": {"type": "string", "minLength": 1, "maxLength": 50},
                       "productStatus": {"type": "string", "enum": ["半成品", "成品"]},
                       "packagingMethod": {"type": "string", "minLength": 1, "maxLength": 50},
                       "screenMeshName": {"type": "string", "minLength": 1, "maxLength": 100},
                       "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 50}},
    },
    "get_product_detail": {"type": "object", "additionalProperties": False, "required": ["productName"],
                           "properties": {"productName": {"type": "string", "minLength": 1, "maxLength": 100}}},
    "query_screen_mesh_catalog": {"type": "object", "additionalProperties": False,
                                  "properties": {"meshName": {"type": "string", "minLength": 1, "maxLength": 100},
                                                 "page": {"type": "integer", "minimum": 1},
                                                 "size": {"type": "integer", "minimum": 1, "maximum": 50}}},
    "query_assay_groups": {"type": "object", "additionalProperties": False,
                           "properties": {"groupName": {"type": "string", "minLength": 1, "maxLength": 100}, "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 50}}},
    "query_quality_standard_catalog": {"type": "object", "additionalProperties": False,
                                       "properties": {"productType": {"type": "string", "minLength": 1, "maxLength": 50}, "standardName": {"type": "string", "minLength": 1, "maxLength": 100}, "status": {"type": "string", "enum": ["ENABLED", "DISABLED"]}, "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 50}}},
    "get_quality_standard_detail": {"type": "object", "additionalProperties": False, "required": ["standardCode", "version"],
                                    "properties": {"standardCode": {"type": "string", "minLength": 1, "maxLength": 100}, "version": {"type": "integer", "minimum": 1}}},
    "query_product_standard_relations": {"type": "object", "additionalProperties": False, "required": ["productName"],
                                         "properties": {"productName": {"type": "string", "minLength": 1, "maxLength": 100}}},
    "query_employee_roster": {"type": "object", "additionalProperties": False,
                              "properties": {"employeeId": {"type": "string", "minLength": 1, "maxLength": 50}, "name": {"type": "string", "minLength": 1, "maxLength": 100}, "department": {"type": "string", "minLength": 1, "maxLength": 100}, "position": {"type": "string", "minLength": 1, "maxLength": 100}, "status": {"type": "string", "minLength": 1, "maxLength": 20}, "roleCode": {"type": "string", "minLength": 1, "maxLength": 50}, "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 50}}},
    "query_roles": {"type": "object", "additionalProperties": False,
                    "properties": {"keyword": {"type": "string", "minLength": 1, "maxLength": 100}, "status": {"type": "string", "enum": ["ENABLED", "DISABLED"]}, "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 50}}},
    "get_role_permission_summary": {"type": "object", "additionalProperties": False, "required": ["roleCodeOrName"],
                                    "properties": {"roleCodeOrName": {"type": "string", "minLength": 1, "maxLength": 100}}},
    "search_operation_logs": {"type": "object", "additionalProperties": False,
                              "properties": {"module": {"type": "string", "minLength": 1, "maxLength": 100}, "operationType": {"type": "string", "minLength": 1, "maxLength": 20}, "operator": {"type": "string", "minLength": 1, "maxLength": 100}, "startTime": {"type": "string", "format": "date-time"}, "endTime": {"type": "string", "format": "date-time"}, "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 50}}},
    "query_agent_tool_audit": {"type": "object", "additionalProperties": False,
                               "properties": {"capability": {"type": "string", "minLength": 1, "maxLength": 100}, "resultCode": {"type": "string", "minLength": 1, "maxLength": 40}, "errorCode": {"type": "string", "minLength": 1, "maxLength": 80}, "startTime": {"type": "string", "format": "date-time"}, "endTime": {"type": "string", "format": "date-time"}, "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 50}}},
    "query_agent_answer_reviews": {"type": "object", "additionalProperties": False,
                                   "properties": {"reviewStatus": {"type": "string", "minLength": 1, "maxLength": 40}, "answerStatus": {"type": "string", "minLength": 1, "maxLength": 40}, "failureDomain": {"type": "string", "minLength": 1, "maxLength": 80}, "failureCategory": {"type": "string", "minLength": 1, "maxLength": 120}, "suggestedFixType": {"type": "string", "minLength": 1, "maxLength": 80}, "testCaseStatus": {"type": "string", "minLength": 1, "maxLength": 40}, "priorityOnly": {"type": "boolean"}, "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 50}}},
    "query_inventory_ledger": {"type": "object", "additionalProperties": False,
                               "properties": {"productName": {"type": "string", "minLength": 1, "maxLength": 100}, "warehouseName": {"type": "string", "minLength": 1, "maxLength": 100}, "screenMeshName": {"type": "string", "minLength": 1, "maxLength": 100}, "productStatus": {"type": "string", "minLength": 1, "maxLength": 50}, "entryDateStart": {"type": "string", "format": "date"}, "entryDateEnd": {"type": "string", "format": "date"}, "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 50}}},
    "query_prepare_pool_balance": {"type": "object", "additionalProperties": False,
                                    "properties": {"productName": {"type": "string", "minLength": 1, "maxLength": 100}, "productType": {"type": "string", "minLength": 1, "maxLength": 50}, "screenMeshName": {"type": "string", "minLength": 1, "maxLength": 100}, "productionDateStart": {"type": "string", "format": "date"}, "productionDateEnd": {"type": "string", "format": "date"}, "positiveOnly": {"type": "boolean", "const": True}, "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 50}}},
    "query_fixed_product_qr_pool": {"type": "object", "additionalProperties": False,
                                    "properties": {"productName": {"type": "string", "minLength": 1, "maxLength": 100}, "codes": {"type": "array", "maxItems": 20, "uniqueItems": True, "items": {"type": "string", "minLength": 1, "maxLength": 100}}, "status": {"type": "string", "minLength": 1, "maxLength": 30}, "freeOnly": {"type": "boolean"}, "page": {"type": "integer", "minimum": 1}, "size": {"type": "integer", "minimum": 1, "maximum": 50}}},
    "get_warehouse_status": {
        "type": "object",
        "additionalProperties": False,
        "required": ["warehouseId"],
        "properties": {
            "warehouseId": {"type": "integer", "minimum": 1},
        },
    },
    "get_assay_status": {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "productId": {"type": "integer", "minimum": 1},
            "productionDate": {"type": "string", "maxLength": 20},
            "assayId": {"type": "integer", "minimum": 1},
        },
    },
}


LLM_TOOL_DESCRIPTIONS: dict[str, str] = {
    "resolve_products": "把用户明确说出的产品名称解析为受控候选；产品未确认时先调用，多候选必须由用户选择。",
    "resolve_warehouses": "把用户明确说出的库位名称解析为受控候选；库位未确认时先调用，多候选必须由用户选择。",
    "get_inventory_overview": "查询一个已确认具体产品的当前库存总量、折合件数、重量和已有位置摘要。",
    "get_inventory_distribution": "查询当前库存按库位或产品的受控分布；用于‘在哪些库位’、‘某库位有哪些库存’和缩小过滤范围。",
    "query_inventory_ledger": "分页查询当前库存台账明细；适合需要入库日期、状态、库位等明细过滤，不用于历史库存趋势。",
    "query_prepare_pool_balance": "分页查询生产备料池当前正余额；不代表实际消耗、损耗或未来计划。",
    "get_warehouse_status": "查询一个已确认库位的当前容量、占用和状态；不还原历史容量。",
    "query_warehouse_capacity_distribution": "分页查询多个库位当前容量分布；不是历史容量趋势。",
    "query_warehouse_recent_operations": "查询一个已确认库位已登记的近期操作；日志缺失不代表没有发生。",
    "query_warehouse_mixed_storage_facts": "查询一个已确认库位的混放事实和风险标签；只依据已登记规则。",
    "get_assay_status": "查询一个已确认产品在指定生产日期的化验状态；未提供日期时不得声称是最新化验。",
    "query_assay_records": "按受控产品范围和日期查询化验记录列表；按 sampleDate 降序 size=1 可取最新产品化验。",
    "get_assay_report_detail": "使用上一查询返回的受控 recordRef 查看一份化验报告详情。",
    "query_assay_abnormalities": "按产品范围、日期和异常类型统计化验异常；区分不合格、无标准和标准多候选。",
    "query_products_without_recent_assay": "查询当前在库产品中指定日期范围缺少化验的分组；不能证明库存批次是否合格。",
    "query_assay_standard_coverage": "查询产品质量标准覆盖情况；部分覆盖类型仍可能被后端拒绝。",
    "query_assay_groups": "查询已登记化验分组目录，不查询具体产品化验结果。",
    "query_quality_standard_catalog": "查询质量标准目录，不代表某产品或批次已经适用该标准。",
    "get_quality_standard_detail": "使用受控标准引用查询质量标准详情。",
    "query_product_standard_relations": "按产品规范名查询当前产品与质量标准的配置关系。",
    "get_pallet_status": "查询用户明确提供或当前已确认托盘的状态、库存、化验和已登记流转。",
    "query_qr_code_lifecycle": "查询一个明确二维码或托盘码的已登记生命周期；不执行任何码状态变更。",
    "query_printed_not_inbound_codes": "查询已打印但尚未完成入库的二维码分组；只读且不创建任务。",
    "query_pallet_anomalies": "查询托盘状态、库存和流转之间的已登记异常事实。",
    "query_pallet_flow_records": "分页查询明确托盘或受控范围的已登记流转记录；不是完整操作日志。",
    "query_qr_batch_inbound_completion": "查询二维码批次的入库完成情况；不确认或补录入库。",
    "query_fixed_product_qr_pool": "查询固定产品二维码池当前状态；不打印、启用、作废或恢复二维码。",
}


class ToolArgumentBuilder:
    def __init__(
        self,
        model_client: ModelClient | None = None,
        context_builder: ContextBuilder | None = None,
        intent_router: IntentRouter | None = None,
        agent_router: AgentHandoffRouter | None = None,
        expert_model_clients: dict[str, ModelClient] | None = None,
        compound_planner: CompoundIntentPlanner | None = None,
        goal_draft_shadow_enabled: bool = False,
        business_clock: BusinessClock | None = None,
    ) -> None:
        self._model_client = model_client or BasicModelClient()
        self._context_builder = context_builder or ContextBuilder()
        self._intent_router = intent_router or IntentRouter()
        self._agent_router = agent_router or AgentHandoffRouter()
        self._expert_model_clients = dict(expert_model_clients or {})
        self._compound_planner = compound_planner or CompoundIntentPlanner(self._agent_router)
        self._goal_draft_shadow_enabled = goal_draft_shadow_enabled
        self.business_clock = business_clock or BusinessClock()

    def llm_visible_tool_schemas(self, handoff: AgentHandoff) -> dict[str, dict[str, Any]]:
        """Return model-facing schemas with internal database IDs replaced by state refs."""

        visible = self._agent_router.tool_schemas(handoff, TOOL_SCHEMAS)
        result: dict[str, dict[str, Any]] = {}
        for tool_name, schema in visible.items():
            model_schema = self._llm_visible_schema(schema)
            if tool_name in {"get_pallet_status", "query_qr_code_lifecycle", "query_pallet_flow_records"}:
                properties = model_schema.get("properties")
                if isinstance(properties, dict) and "code" in properties:
                    properties["palletRef"] = {
                        "type": "string",
                        "const": "CURRENT_PALLET",
                        "description": "Runtime 绑定的当前已确认托盘；不是数据库 ID 或可猜测的托盘码。",
                    }
                    if tool_name in {"get_pallet_status", "query_qr_code_lifecycle"}:
                        required = model_schema.get("required")
                        if isinstance(required, list):
                            model_schema["required"] = [key for key in required if key != "code"]
                        model_schema["oneOf"] = [
                            {"required": ["code"]},
                            {"required": ["palletRef"]},
                        ]
            if tool_name == "get_assay_report_detail":
                properties = model_schema.get("properties")
                if isinstance(properties, dict):
                    properties["reportRef"] = {
                        "type": "string",
                        "const": "CURRENT_ASSAY_REPORT",
                        "description": "上一轮化验记录列表中的第一条受控报告引用；真实引用不提供给模型。",
                    }
            description = LLM_TOOL_DESCRIPTIONS.get(tool_name)
            if description:
                model_schema["description"] = description
            result[tool_name] = model_schema
        return result

    def validate_llm_arguments(
        self,
        *,
        tool_name: str,
        arguments: dict[str, Any],
        state: WarehouseAgentState,
        user_message: str,
    ) -> dict[str, Any]:
        """Materialize controlled state refs, then apply the existing strict validator."""

        if tool_name not in ALLOWED_TOOLS:
            raise ValueError("tool is not allowed")
        self._reject_model_generated_ids(arguments)
        self._reject_display_label_execution(tool_name, arguments, state, user_message)
        materialized = self._materialize_state_refs(arguments, state)
        if tool_name == "query_pallet_tasks":
            materialized = self._pallet_task_followup_arguments(
                materialized,
                state,
                user_message,
            )
        materialized = self.business_clock.normalize_tool_arguments(
            tool_name,
            materialized,
            user_message,
            TOOL_SCHEMAS.get(tool_name),
        )
        self._reject_unbound_pallet_code(
            tool_name,
            arguments,
            materialized,
            state,
            user_message,
        )
        if tool_name == "get_inventory_distribution":
            product_scope = materialized.get("productScope")
            warehouse_scope = materialized.get("warehouseScope")
            if isinstance(product_scope, dict) and product_scope.get("type") == "ALL":
                warehouse_bounded = (
                    isinstance(warehouse_scope, dict)
                    and warehouse_scope.get("type") == "SINGLE_WAREHOUSE"
                    and any(word in user_message for word in ("库位", "仓库", "库内", "库里"))
                )
                if not self._explicit_all_product_request(user_message) and not warehouse_bounded:
                    raise ValueError("ALL product scope was not explicitly requested")
        if tool_name == "query_assay_records":
            self._require_allowed_all_scope(
                materialized,
                self._assay_records_all_scope_allowed(user_message),
            )
            product_scope = materialized.get("productScope")
            date_range = materialized.get("dateRange")
            if (
                isinstance(product_scope, dict)
                and product_scope.get("type") == "SINGLE_PRODUCT"
                and isinstance(date_range, dict)
                and date_range.get("type") == "EXACT"
                and self._prefers_single_assay_report(user_message)
            ):
                raise ValueError(
                    "single-product exact-date assay query must use get_assay_status"
                )
        if tool_name == "query_assay_abnormalities":
            self._require_allowed_all_scope(
                materialized,
                self._assay_abnormalities_all_scope_allowed(user_message),
            )
        if tool_name == "query_products_without_recent_assay":
            self._require_allowed_all_scope(
                materialized,
                self._products_without_recent_assay_all_scope_allowed(user_message),
            )
        if tool_name == "query_assay_standard_coverage":
            self._require_allowed_all_scope(
                materialized,
                self._assay_standard_coverage_all_scope_allowed(user_message),
            )
        return self._validate(tool_name, materialized)

    def _llm_visible_schema(self, value: Any) -> Any:
        if isinstance(value, list):
            return [self._llm_visible_schema(item) for item in value]
        if not isinstance(value, dict):
            return value
        result: dict[str, Any] = {}
        properties = value.get("properties")
        if isinstance(properties, dict):
            translated: dict[str, Any] = {}
            key_map: dict[str, str | None] = {}
            for key, schema in properties.items():
                replacement = self._llm_ref_key(key)
                key_map[key] = replacement
                if replacement is None:
                    continue
                if replacement == "productRef":
                    translated[replacement] = {
                        "type": "string",
                        "const": "CURRENT_PRODUCT",
                        "description": "Runtime 绑定的当前已确认产品；不是数据库 ID。",
                    }
                elif replacement == "warehouseRef":
                    translated[replacement] = {
                        "type": "string",
                        "const": "CURRENT_WAREHOUSE",
                        "description": "Runtime 绑定的当前已确认库位；不是数据库 ID。",
                    }
                else:
                    translated[replacement] = self._llm_visible_schema(schema)
            result.update({key: self._llm_visible_schema(item) for key, item in value.items() if key not in {"properties", "required"}})
            result["properties"] = translated
            required = value.get("required")
            if isinstance(required, list):
                translated_required = [key_map.get(str(key)) for key in required]
                result["required"] = [key for key in translated_required if key]
            return result
        return {key: self._llm_visible_schema(item) for key, item in value.items()}

    def _llm_ref_key(self, key: str) -> str | None:
        if key == "productId":
            return "productRef"
        if key == "warehouseId":
            return "warehouseRef"
        if key == "id" or key.endswith("Id") or key.endswith("_id"):
            return None
        return key

    def _reject_model_generated_ids(self, value: Any) -> None:
        if isinstance(value, list):
            for item in value:
                self._reject_model_generated_ids(item)
            return
        if not isinstance(value, dict):
            return
        for key, item in value.items():
            key_text = str(key)
            if key_text == "id" or key_text.endswith("Id") or key_text.endswith("_id"):
                raise ValueError("model-generated database IDs are forbidden")
            self._reject_model_generated_ids(item)

    def _materialize_state_refs(self, value: Any, state: WarehouseAgentState) -> Any:
        if isinstance(value, list):
            return [self._materialize_state_refs(item, state) for item in value]
        if not isinstance(value, dict):
            return value
        result: dict[str, Any] = {}
        for key, item in value.items():
            if key == "productRef":
                if item != "CURRENT_PRODUCT" or state.selected_product is None or state.selected_product.internal_id is None:
                    raise ValueError("CURRENT_PRODUCT is not available")
                result["productId"] = state.selected_product.internal_id
                continue
            if key == "warehouseRef":
                if item != "CURRENT_WAREHOUSE" or state.selected_warehouse is None or state.selected_warehouse.internal_id is None:
                    raise ValueError("CURRENT_WAREHOUSE is not available")
                result["warehouseId"] = state.selected_warehouse.internal_id
                continue
            if key == "palletRef":
                code = self._selected_pallet_code(state)
                if item != "CURRENT_PALLET" or code is None:
                    raise ValueError("CURRENT_PALLET is not available")
                result["code"] = code
                continue
            if key == "reportRef" and item == "CURRENT_ASSAY_REPORT":
                report_ref = self._current_assay_report_ref(state)
                if report_ref is None:
                    raise ValueError("CURRENT_ASSAY_REPORT is not available")
                result["reportRef"] = report_ref
                continue
            result[key] = self._materialize_state_refs(item, state)
        return result

    def _reject_unbound_pallet_code(
        self,
        tool_name: str,
        source_arguments: dict[str, Any],
        materialized_arguments: dict[str, Any],
        state: WarehouseAgentState,
        user_message: str,
    ) -> None:
        if tool_name not in {"get_pallet_status", "query_qr_code_lifecycle", "query_pallet_flow_records"}:
            return
        source_code = str(source_arguments.get("code") or "").strip()
        if source_code:
            if source_code not in user_message:
                raise ValueError("pallet code is neither user-provided nor bound to CURRENT_PALLET")
            return
        code = str(materialized_arguments.get("code") or "").strip()
        if code and code != self._selected_pallet_code(state):
            raise ValueError("pallet code is neither user-provided nor bound to CURRENT_PALLET")

    @staticmethod
    def _selected_pallet_code(state: WarehouseAgentState) -> str | None:
        if state.selected_pallet is None:
            return None
        code = str(state.selected_pallet.metadata.get("code") or "").strip()
        return code or None

    @staticmethod
    def _current_assay_report_ref(state: WarehouseAgentState) -> str | None:
        records = (state.last_assay_records or {}).get("records")
        if not isinstance(records, list):
            return None
        for record in records:
            if not isinstance(record, dict):
                continue
            report_ref = str(record.get("recordRef") or "").strip()
            if report_ref:
                return report_ref
        return None

    def _require_allowed_all_scope(self, arguments: dict[str, Any], allowed: bool) -> None:
        scope = arguments.get("productScope")
        if isinstance(scope, dict) and scope.get("type") == "ALL" and not allowed:
            raise ValueError("ALL product scope was not explicitly requested")

    def _reject_display_label_execution(
        self,
        tool_name: str,
        arguments: dict[str, Any],
        state: WarehouseAgentState,
        user_message: str,
    ) -> None:
        if tool_name != "resolve_products":
            return
        query = str(arguments.get("query") or "").strip()
        if not query or query in user_message:
            return
        labels: set[str] = set()
        canonical_names: set[str] = set()
        if state.selected_product is not None:
            labels.add(state.selected_product.display_label.strip())
            canonical = str(state.selected_product.metadata.get("productName") or "").strip()
            if canonical:
                canonical_names.add(canonical)
        distribution = state.last_inventory_distribution or {}
        groups = distribution.get("groups")
        if isinstance(groups, list):
            for group in groups:
                if not isinstance(group, dict):
                    continue
                label = str(group.get("productLabel") or group.get("groupLabel") or "").strip()
                canonical = str(group.get("canonicalProductName") or "").strip()
                if label:
                    labels.add(label)
                if canonical:
                    canonical_names.add(canonical)
        if query in labels and query not in canonical_names:
            raise ValueError("displayLabel is presentation-only and cannot be resolver input")

    def build(self, *, tool_name: str, user_message: str, state: WarehouseAgentState) -> dict[str, Any]:
        if tool_name not in ALLOWED_TOOLS:
            raise ValueError("tool is not allowed")
        profile = self._agent_router.profile_for_tool(tool_name, state.active_agent)
        self._agent_router.authorize_tool(profile.name, tool_name)
        handoff = self._agent_router.handoff_for_agent(profile.name)
        schema = TOOL_SCHEMAS.get(tool_name, {"type": "object", "properties": {}})
        decision = self._model_client_for(handoff).build_tool_arguments(
            ModelArgumentRequest(
                toolName=tool_name,
                userMessage=user_message,
                messages=state.messages,
                state=state,
                domainContext=self._context_for(user_message, state, handoff),
                toolSchema=schema,
            )
        )
        arguments = decision.arguments
        if tool_name == "get_inventory_distribution":
            allow_all = self._explicit_all_product_request(user_message)
            arguments = self._distribution_arguments(arguments, state, allow_all=allow_all)
        if tool_name == "query_assay_records":
            allow_all = self._assay_records_all_scope_allowed(user_message)
            arguments = self._assay_records_arguments(arguments, state, user_message, allow_all=allow_all)
        if tool_name == "get_assay_report_detail":
            arguments = self._assay_report_detail_arguments(arguments, state)
        if tool_name == "query_assay_abnormalities":
            allow_all = self._assay_abnormalities_all_scope_allowed(user_message)
            arguments = self._assay_abnormalities_arguments(arguments, state, user_message, allow_all=allow_all)
        if tool_name == "query_products_without_recent_assay":
            allow_all = self._products_without_recent_assay_all_scope_allowed(user_message)
            arguments = self._products_without_recent_assay_arguments(arguments, state, user_message, allow_all=allow_all)
        if tool_name == "query_assay_standard_coverage":
            allow_all = self._assay_standard_coverage_all_scope_allowed(user_message)
            arguments = self._assay_standard_coverage_arguments(arguments, state, user_message, allow_all=allow_all)
        if tool_name == "query_qr_code_lifecycle":
            arguments = self._qr_code_lifecycle_arguments(arguments, user_message)
        if tool_name == "query_printed_not_inbound_codes":
            arguments = self._printed_not_inbound_arguments(arguments, state, user_message)
        if tool_name == "query_pallet_anomalies":
            arguments = self._pallet_anomalies_arguments(arguments, state, user_message)
        if tool_name == "query_pallet_flow_records":
            arguments = self._pallet_flow_records_arguments(arguments, state, user_message)
        if tool_name == "query_qr_batch_inbound_completion":
            arguments = self._qr_batch_inbound_arguments(arguments, user_message)
        return self._validate(tool_name, arguments)

    def plan(self, *, user_message: str, state: WarehouseAgentState) -> ModelPlanDecision:
        goal_draft_shadow = self._goal_draft_shadow(user_message, state)
        if self._compound_planner.is_unsupported_batch_qualification(user_message):
            handoff = self._agent_router.handoff_for_agent(MAIN_AGENT, mode="direct")
            snapshot = {
                "intent_type": "unsupported_scope",
                "intent_subtype": "inventory_batch_qualification",
                "business_domain": "multi_domain",
                "support_status": "unsupported",
                "next_action": "answer_directly",
                "planned_tools": [],
                "error_code": "UNSUPPORTED_SCOPE",
                "agent_handoff": handoff.to_snapshot(),
            }
            self._attach_goal_draft_shadow(snapshot, goal_draft_shadow)
            return ModelPlanDecision(
                action="answer",
                intent="inventory_batch_qualification",
                responseMode="unsupported_scope",
                answer=(
                    "当前只支持按产品查询最新化验记录，尚不能确认当前库存批次是否逐批合格。"
                    "需要批次或生产日期与化验报告的可靠关联后才能回答，本次未执行任何业务查询。"
                ),
                routeSnapshot=snapshot,
            )
        compound = self._compound_planner.plan(user_message)
        if compound is not None:
            handoff = self._agent_router.handoff_for_agent(MAIN_AGENT, mode="orchestrate")
            snapshot = {
                "intent_type": "compound_data_query",
                "intent_subtype": compound.recipe,
                "business_domain": "multi_domain",
                "support_status": "supported",
                "next_action": "orchestrate",
                "planned_tools": [
                    tool_name
                    for step in compound.steps
                    for tool_name in step.tools
                ],
                "agent_handoff": handoff.to_snapshot(),
                "orchestration": compound.to_snapshot(),
                "orchestrationState": new_orchestration_state(compound),
            }
            self._attach_goal_draft_shadow(snapshot, goal_draft_shadow)
            return ModelPlanDecision(
                action="orchestrate",
                intent=compound.recipe,
                responseMode="compound_inventory_assay",
                arguments={"warehouseQuery": self._compound_planner.warehouse_query(user_message)},
                confidenceNote="allowlisted multi-expert recipe selected",
                routeSnapshot=snapshot,
            )
        route = self._intent_router.route(user_message, state)
        handoff = self._agent_router.route(route)
        snapshot = route.to_snapshot()
        snapshot["agent_handoff"] = handoff.to_snapshot()
        self._attach_goal_draft_shadow(snapshot, goal_draft_shadow)
        routed_decision = self._plan_from_route(user_message, state, route, snapshot)
        if routed_decision is not None:
            return self._finalize_plan(routed_decision, handoff, snapshot)

        visible_schemas = self._agent_router.tool_schemas(handoff, TOOL_SCHEMAS)
        decision = self._model_client_for(handoff).plan_next_action(
            ModelPlanRequest(
                userMessage=user_message,
                messages=state.messages,
                state=state,
                domainContext=self._context_for(user_message, state, handoff),
                toolSchemas=visible_schemas,
            )
        )
        if decision.action != "call_tool":
            return self._finalize_plan(ModelPlanDecision(
                action=decision.action,
                toolName=decision.toolName,
                arguments=decision.arguments,
                intent=decision.intent or route.intent_type,
                responseMode=decision.responseMode,
                answer=decision.answer,
                prompt=decision.prompt,
                suggestions=decision.suggestions,
                confidenceNote=decision.confidenceNote,
            ), handoff, snapshot)
        if not decision.toolName or decision.toolName not in ALLOWED_TOOLS:
            raise ValueError("planned tool is not allowed")
        self._agent_router.authorize_tool(handoff.target_agent, decision.toolName)
        arguments = decision.arguments
        if decision.toolName == "get_inventory_distribution":
            explicit_all = (
                isinstance(arguments.get("productScope"), dict)
                and arguments["productScope"].get("type") == "ALL"
                and self._explicit_all_product_request(user_message)
            )
            if state.selected_product is None and not explicit_all:
                return self._finalize_plan(ModelPlanDecision(
                    action="ask_user",
                    prompt="你想查哪个产品？请先选择或输入一个明确的产品名称。",
                    suggestions=["例如：黄冰糖（袋）在哪些库位？"],
                    confidenceNote="distribution requires a confirmed selected product",
                ), handoff, snapshot)
            arguments = self._distribution_arguments(arguments, state, allow_all=explicit_all)
        if decision.toolName == "query_assay_records":
            explicit_all = (
                isinstance(arguments.get("productScope"), dict)
                and arguments["productScope"].get("type") == "ALL"
                and self._assay_records_all_scope_allowed(user_message)
            )
            if state.selected_product is None and not explicit_all:
                return self._finalize_plan(ModelPlanDecision(
                    action="ask_user",
                    prompt="你想查询哪个产品的化验记录？也可以说“今天有哪些化验记录”查询全部产品。",
                    suggestions=["例如：黄冰糖最近30天化验记录", "今天有哪些化验记录"],
                    confidenceNote="assay records requires a confirmed product scope or explicit all-products scope",
                ), handoff, snapshot)
            arguments = self._assay_records_arguments(arguments, state, user_message, allow_all=explicit_all)
        if decision.toolName == "get_assay_report_detail":
            arguments = self._assay_report_detail_arguments(arguments, state)
        if decision.toolName == "query_assay_abnormalities":
            explicit_all = (
                isinstance(arguments.get("productScope"), dict)
                and arguments["productScope"].get("type") == "ALL"
                and self._assay_abnormalities_all_scope_allowed(user_message)
            )
            if state.selected_product is None and not explicit_all:
                return self._finalize_plan(ModelPlanDecision(
                    action="ask_user",
                    prompt="你想查询哪个产品范围的化验异常？也可以说“全部产品最近7天化验异常”。",
                    suggestions=["例如：全部产品最近7天不合格化验", "黄冰糖最近90天质量异常"],
                    confidenceNote="assay abnormalities requires a confirmed product scope or explicit all-products scope",
                ), handoff, snapshot)
            arguments = self._assay_abnormalities_arguments(arguments, state, user_message, allow_all=explicit_all)
        if decision.toolName == "query_products_without_recent_assay":
            explicit_all = (
                isinstance(arguments.get("productScope"), dict)
                and arguments["productScope"].get("type") == "ALL"
                and self._products_without_recent_assay_all_scope_allowed(user_message)
            )
            if state.selected_product is None and not explicit_all:
                return self._finalize_plan(ModelPlanDecision(
                    action="ask_user",
                    prompt="你想查询哪个产品范围的缺化验库存？也可以说“最近7天全部在库产品缺化验”。",
                    suggestions=["例如：最近7天哪些在库产品没有化验", "1号库位有哪些库存缺化验"],
                    confidenceNote="products without recent assay requires a confirmed product scope or explicit all-products scope",
                ), handoff, snapshot)
            arguments = self._products_without_recent_assay_arguments(arguments, state, user_message, allow_all=explicit_all)
        if decision.toolName == "query_assay_standard_coverage":
            explicit_all = (
                isinstance(arguments.get("productScope"), dict)
                and arguments["productScope"].get("type") == "ALL"
                and self._assay_standard_coverage_all_scope_allowed(user_message)
            )
            if state.selected_product is None and not explicit_all:
                return self._finalize_plan(ModelPlanDecision(
                    action="ask_user",
                    prompt="你想查询哪个产品范围的质量标准覆盖？也可以说“全部在库产品哪些没有质量标准”。",
                    suggestions=["例如：哪些在库产品没有质量标准", "黄冰糖哪些规格未绑定标准"],
                    confidenceNote="assay standard coverage requires a confirmed product scope or explicit all-products scope",
                ), handoff, snapshot)
            arguments = self._assay_standard_coverage_arguments(arguments, state, user_message, allow_all=explicit_all)
        if decision.toolName == "query_qr_code_lifecycle":
            arguments = self._qr_code_lifecycle_arguments(arguments, user_message)
        if decision.toolName == "query_printed_not_inbound_codes":
            arguments = self._printed_not_inbound_arguments(arguments, state, user_message)
        if decision.toolName == "query_pallet_anomalies":
            arguments = self._pallet_anomalies_arguments(arguments, state, user_message)
        if decision.toolName == "query_pallet_flow_records":
            arguments = self._pallet_flow_records_arguments(arguments, state, user_message)
        if decision.toolName == "query_qr_batch_inbound_completion":
            arguments = self._qr_batch_inbound_arguments(arguments, user_message)
        return self._finalize_plan(ModelPlanDecision(
            action=decision.action,
            toolName=decision.toolName,
            arguments=self._validate(decision.toolName, arguments),
            intent=decision.intent,
            responseMode=decision.responseMode,
            answer=decision.answer,
            prompt=decision.prompt,
            suggestions=decision.suggestions,
            confidenceNote=decision.confidenceNote,
        ), handoff, snapshot)

    def _plan_from_route(
        self,
        user_message: str,
        state: WarehouseAgentState,
        route: IntentRoute,
        snapshot: dict[str, Any],
    ) -> ModelPlanDecision | None:
        if route.next_action == "answer_directly":
            return ModelPlanDecision(
                action="answer",
                answer=self._generate_direct_answer(user_message, state, route, snapshot, "已处理。"),
                intent=route.intent_type,
                responseMode=route.intent_subtype,
                routeSnapshot=snapshot,
            )
        if route.next_action == "ask_clarification":
            return ModelPlanDecision(
                action="ask_user",
                prompt=route.clarification_prompt or "请补充更明确的查询条件。",
                suggestions=route.suggestions,
                intent=route.intent_type,
                responseMode=route.intent_subtype,
                routeSnapshot=snapshot,
            )
        if route.next_action == "explain_unsupported":
            if route.intent_subtype == "no_supported_business_intent":
                return None
            return ModelPlanDecision(
                action="answer",
                answer=self._generate_direct_answer(
                    user_message,
                    state,
                    route,
                    snapshot,
                    "当前能力暂不支持这个操作。",
                ),
                intent=route.intent_type,
                responseMode=route.intent_subtype,
                routeSnapshot=snapshot,
            )
        if route.next_action != "call_tool":
            return None

        objects = route.business_objects
        if route.intent_subtype == "production_order_progress":
            order_query = self._production_order_query(user_message)
            if order_query is None and state.selected_production_order is not None:
                order_ref = str(state.selected_production_order.metadata.get("orderRef") or "")
                if order_ref.startswith("aer_"):
                    return ModelPlanDecision(
                        action="call_tool", toolName="query_production_order_progress",
                        arguments=self._validate("query_production_order_progress", {"orderRef": order_ref}),
                        intent="production_order_progress", responseMode="production_order_progress",
                        routeSnapshot=snapshot,
                    )
            if order_query is None:
                return ModelPlanDecision(
                    action="ask_user",
                    prompt="请提供生产订单号，我再查询订单进度。",
                    suggestions=["例如：查询生产订单 PO-20260713-001 的进度"],
                    intent="production_order_progress",
                    responseMode="production_order_progress",
                    routeSnapshot=snapshot,
                )
            return ModelPlanDecision(
                action="call_tool",
                toolName="resolve_production_entities",
                arguments=self._validate("resolve_production_entities", {
                    "entityType": "PRODUCTION_ORDER", "query": order_query, "limit": 5
                }),
                intent="production_order_progress",
                responseMode="production_order_progress",
                confidenceNote="production order progress requires controlled entity resolution",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "material_pick_trace":
            order_query = self._production_order_query(user_message)
            if order_query is None and state.selected_production_order is not None:
                order_ref = str(state.selected_production_order.metadata.get("orderRef") or "")
                if order_ref.startswith("aer_"):
                    return ModelPlanDecision(
                        action="call_tool", toolName="query_material_pick_trace",
                        arguments=self._validate("query_material_pick_trace", {"orderRef": order_ref}),
                        intent="material_pick_trace", responseMode="material_pick_trace",
                        routeSnapshot=snapshot,
                    )
            if order_query is None:
                return ModelPlanDecision(
                    action="ask_user",
                    prompt="请提供生产订单号，我再查询实际领料记录。",
                    intent="material_pick_trace",
                    responseMode="material_pick_trace",
                    routeSnapshot=snapshot,
                )
            return ModelPlanDecision(
                action="call_tool",
                toolName="resolve_production_entities",
                arguments=self._validate("resolve_production_entities", {
                    "entityType": "PRODUCTION_ORDER", "query": order_query, "limit": 5
                }),
                intent="material_pick_trace",
                responseMode="material_pick_trace",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "material_candidates":
            order_query = self._production_order_query(user_message)
            if order_query is None:
                return ModelPlanDecision(action="ask_user", prompt="请提供生产订单号，我再查询领料候选。",
                                         intent="material_candidates", responseMode="material_candidates", routeSnapshot=snapshot)
            return ModelPlanDecision(
                action="call_tool", toolName="resolve_production_entities",
                arguments=self._validate("resolve_production_entities", {
                    "entityType": "PRODUCTION_ORDER", "query": order_query, "limit": 5}),
                intent="material_candidates", responseMode="material_candidates", routeSnapshot=snapshot)
        if route.intent_subtype == "production_label_completion":
            order_query = self._production_order_query(user_message)
            if order_query is None:
                return ModelPlanDecision(
                    action="ask_user", prompt="请提供生产订单号，我再查询标签完成度。",
                    intent="production_label_completion", responseMode="production_label_completion", routeSnapshot=snapshot,
                )
            return ModelPlanDecision(
                action="call_tool", toolName="resolve_production_entities",
                arguments=self._validate("resolve_production_entities", {
                    "entityType": "PRODUCTION_ORDER", "query": order_query, "limit": 5
                }),
                intent="production_label_completion", responseMode="production_label_completion", routeSnapshot=snapshot,
            )
        if route.intent_subtype == "in_process_materials":
            arguments: dict[str, Any] = {"page": 1, "size": 20}
            if objects.product and objects.product != "全部产品":
                arguments["productName"] = objects.product
            date_range = self._assay_date_range_from_message(user_message)
            if date_range and date_range.get("type") == "EXACT":
                arguments["productionDateStart"] = date_range["date"]
                arguments["productionDateEnd"] = date_range["date"]
            return ModelPlanDecision(
                action="call_tool", toolName="query_in_process_materials",
                arguments=self._validate("query_in_process_materials", arguments),
                intent="in_process_materials", responseMode="in_process_materials", routeSnapshot=snapshot,
            )
        if route.intent_subtype == "pallet_tasks":
            args: dict[str, Any] = {"page": 1, "size": 20}
            text = user_message or ""
            if "待处理" in text or "待确认" in text: args["status"] = "PENDING"
            elif "已确认" in text: args["status"] = "CONFIRMED"
            elif "已取消" in text: args["status"] = "CANCELED"
            if "调拨" in text: args["taskType"] = "TRANSFER"
            elif "入库" in text: args["taskType"] = "IN"
            elif "出库" in text: args["taskType"] = "OUT"
            if "半成品" in text: args["productStatus"] = "半成品"
            elif "成品" in text: args["productStatus"] = "成品"
            if objects.pallet: args["code"] = objects.pallet
            return ModelPlanDecision(action="call_tool", toolName="query_pallet_tasks",
                                     arguments=self._validate("query_pallet_tasks", args),
                                     intent="pallet_tasks", responseMode="pallet_tasks", routeSnapshot=snapshot)
        if route.intent_subtype == "stock_documents":
            text = user_message or ""
            doc_type = "SEMI_PRODUCT" if "半成品" in text else "OUTBOUND" if "出库" in text else "INBOUND" if "入库" in text else None
            if doc_type is None:
                return ModelPlanDecision(action="ask_user", prompt="请明确要查入库、出库还是半成品单据。",
                                         suggestions=["入库单据", "出库单据", "半成品单据"],
                                         intent="stock_documents", responseMode="stock_documents", routeSnapshot=snapshot)
            args: dict[str, Any] = {"documentType": doc_type, "page": 1, "size": 20}
            if objects.product and objects.product != "全部产品": args["productName"] = objects.product
            if objects.warehouse: args["warehouseName"] = objects.warehouse
            date_range = self._assay_date_range_from_message(text)
            if date_range and date_range.get("type") == "EXACT":
                args["startDate"] = date_range["date"]; args["endDate"] = date_range["date"]
            return ModelPlanDecision(action="call_tool", toolName="query_stock_documents",
                                     arguments=self._validate("query_stock_documents", args),
                                     intent="stock_documents", responseMode="stock_documents", routeSnapshot=snapshot)
        if route.intent_subtype == "auto_inbound_batches":
            return ModelPlanDecision(action="call_tool", toolName="query_auto_inbound_batches",
                                     arguments=self._validate("query_auto_inbound_batches", {"limit": 20}),
                                     intent="auto_inbound_batches", responseMode="auto_inbound_batches", routeSnapshot=snapshot)
        if route.intent_subtype == "warehouse_capacity_distribution":
            text = user_message or ""
            band = "EMPTY" if "空置" in text else "FULL" if "已满" in text else "HIGH" if "快满" in text else "ANY"
            args = {"warehouseScope": {"type": "ALL"}, "occupancyBand": band, "onlyAvailable": False, "page": 1, "size": 20}
            return ModelPlanDecision(action="call_tool", toolName="query_warehouse_capacity_distribution",
                                     arguments=self._validate("query_warehouse_capacity_distribution", args),
                                     intent="warehouse_capacity_distribution", responseMode="warehouse_capacity_distribution",
                                     routeSnapshot=snapshot)
        if route.intent_subtype == "warehouse_recent_operations":
            if objects.warehouse:
                return ModelPlanDecision(action="call_tool", toolName="resolve_warehouses",
                                         arguments=self._validate("resolve_warehouses", {"query": objects.warehouse, "limit": 10}),
                                         intent="warehouse_recent_operations", responseMode="warehouse_recent_operations",
                                         routeSnapshot=snapshot)
            text = user_message or ""
            event_types = []
            if "入库" in text: event_types.append("INBOUND")
            if "出库" in text: event_types.append("OUTBOUND")
            if "调拨" in text: event_types.append("TRANSFER")
            args = {"eventTypes": event_types, "limit": 20}
            if state.selected_warehouse is not None and state.selected_warehouse.internal_id is not None:
                args["warehouseId"] = state.selected_warehouse.internal_id
            return ModelPlanDecision(action="call_tool", toolName="query_warehouse_recent_operations",
                                     arguments=self._validate("query_warehouse_recent_operations", args),
                                     intent="warehouse_recent_operations", responseMode="warehouse_recent_operations",
                                     routeSnapshot=snapshot)
        if route.intent_subtype == "warehouse_mixed_storage_facts":
            if objects.warehouse:
                return ModelPlanDecision(action="call_tool", toolName="resolve_warehouses",
                                         arguments=self._validate("resolve_warehouses", {"query": objects.warehouse, "limit": 10}),
                                         intent="warehouse_mixed_storage_facts", responseMode="warehouse_mixed_storage_facts",
                                         routeSnapshot=snapshot)
            text = user_message or ""
            fact_type = "MULTIPLE_SPECIFICATIONS" if "规格" in text else "MULTIPLE_PRODUCTS" if "产品" in text else "ANY"
            return ModelPlanDecision(action="call_tool", toolName="query_warehouse_mixed_storage_facts",
                                     arguments=self._validate("query_warehouse_mixed_storage_facts", {"factType": fact_type, "limit": 20}),
                                     intent="warehouse_mixed_storage_facts", responseMode="warehouse_mixed_storage_facts",
                                     routeSnapshot=snapshot)
        if route.intent_subtype == "product_catalog":
            args: dict[str, Any] = {"page": 1, "size": 20}
            # “成品产品目录”“产品目录”等是范围描述，不是具体产品名称。
            # 把它们同时作为 productName 和 productStatus 下发会形成互相冲突的过滤条件。
            generic_catalog_queries = {"产品", "全部产品", "产品目录", "成品", "半成品", "成品产品", "半成品产品", "成品产品目录", "半成品产品目录"}
            extracted_product = objects.product.strip() if objects.product else ""
            if extracted_product and extracted_product not in generic_catalog_queries and not extracted_product.endswith("目录"):
                args["productName"] = extracted_product
            text = user_message or ""
            # “产品目录”默认是完整目录，必须同时包含成品和半成品。
            # 只有用户明确表达筛选/仅查看某状态时，才下发 productStatus。
            explicit_status_filter = any(marker in text for marker in ("只看", "仅看", "只查询", "仅查询", "筛选", "状态为"))
            if explicit_status_filter and "半成品" in text:
                args["productStatus"] = "半成品"
            elif explicit_status_filter and "成品" in text:
                args["productStatus"] = "成品"
            return ModelPlanDecision(action="call_tool", toolName="query_product_catalog",
                                     arguments=self._validate("query_product_catalog", args), intent="product_catalog",
                                     responseMode="product_catalog", routeSnapshot=snapshot)
        if route.intent_subtype == "product_detail":
            product_name = self._product_detail_query(user_message)
            if not product_name:
                return ModelPlanDecision(action="ask_user", prompt="请提供要查看配置详情的准确产品名称。",
                                         suggestions=["例如：查看单晶冰糖的产品详情"], intent="product_detail",
                                         responseMode="product_detail", routeSnapshot=snapshot)
            return ModelPlanDecision(action="call_tool", toolName="resolve_products",
                                     arguments=self._validate("resolve_products", {"query": product_name, "limit": 10}),
                                     intent="product_detail", responseMode="product_detail", routeSnapshot=snapshot)
        if route.intent_subtype == "screen_mesh_catalog":
            return ModelPlanDecision(action="call_tool", toolName="query_screen_mesh_catalog",
                                     arguments=self._validate("query_screen_mesh_catalog", {"page": 1, "size": 20}),
                                     intent="screen_mesh_catalog", responseMode="screen_mesh_catalog", routeSnapshot=snapshot)
        if route.intent_subtype == "assay_groups":
            return ModelPlanDecision(action="call_tool", toolName="query_assay_groups", arguments=self._validate("query_assay_groups", {"page": 1, "size": 20}), intent="assay_groups", responseMode="assay_groups", routeSnapshot=snapshot)
        if route.intent_subtype == "quality_standard_catalog":
            return ModelPlanDecision(action="call_tool", toolName="query_quality_standard_catalog", arguments=self._validate("query_quality_standard_catalog", {"page": 1, "size": 20}), intent="quality_standard_catalog", responseMode="quality_standard_catalog", routeSnapshot=snapshot)
        if route.intent_subtype == "quality_standard_detail":
            import re
            code_match = re.search(r"(?:代码|编号)\s*([A-Za-z0-9_-]+)", user_message or "")
            version_match = re.search(r"(?:版本|v)\s*(\d+)", user_message or "", re.IGNORECASE)
            if not code_match or not version_match:
                return ModelPlanDecision(action="ask_user", prompt="请提供质量标准代码和版本号。", suggestions=["例如：查看标准代码 QS-001 版本 2 的详情"], intent="quality_standard_detail", responseMode="quality_standard_detail", routeSnapshot=snapshot)
            return ModelPlanDecision(action="call_tool", toolName="get_quality_standard_detail", arguments=self._validate("get_quality_standard_detail", {"standardCode": code_match.group(1), "version": int(version_match.group(1))}), intent="quality_standard_detail", responseMode="quality_standard_detail", routeSnapshot=snapshot)
        if route.intent_subtype == "product_standard_relations":
            if not objects.product: return ModelPlanDecision(action="ask_user", prompt="请提供准确产品名称。", suggestions=["例如：单晶冰糖绑定哪些标准"], intent="product_standard_relations", responseMode="product_standard_relations", routeSnapshot=snapshot)
            return ModelPlanDecision(action="call_tool", toolName="query_product_standard_relations", arguments=self._validate("query_product_standard_relations", {"productName": objects.product}), intent="product_standard_relations", responseMode="product_standard_relations", routeSnapshot=snapshot)
        if route.intent_subtype == "employee_roster":
            return ModelPlanDecision(action="call_tool", toolName="query_employee_roster", arguments=self._validate("query_employee_roster", {"page": 1, "size": 20}), intent="employee_roster", responseMode="employee_roster", routeSnapshot=snapshot)
        if route.intent_subtype == "role_catalog":
            return ModelPlanDecision(action="call_tool", toolName="query_roles", arguments=self._validate("query_roles", {"page": 1, "size": 20}), intent="role_catalog", responseMode="role_catalog", routeSnapshot=snapshot)
        if route.intent_subtype == "role_permission_summary":
            import re
            text = user_message or ""
            match = re.search(r"(?:查询|查看)?\s*([\u4e00-\u9fffA-Za-z0-9_-]{1,100})角色(?:有哪些权限|权限摘要|权限详情)", text)
            if not match:
                match = re.search(r"(?:角色|编码)\s*[：:]?\s*([\u4e00-\u9fffA-Za-z0-9_-]+)", text)
            if not match:
                return ModelPlanDecision(action="ask_user", prompt="请提供准确的角色编码或角色名称。", suggestions=["例如：查看角色 WAREHOUSE 的权限摘要"], intent="role_permission_summary", responseMode="role_permission_summary", routeSnapshot=snapshot)
            return ModelPlanDecision(action="call_tool", toolName="get_role_permission_summary", arguments=self._validate("get_role_permission_summary", {"roleCodeOrName": match.group(1)}), intent="role_permission_summary", responseMode="role_permission_summary", routeSnapshot=snapshot)
        if route.intent_subtype == "operation_logs":
            return ModelPlanDecision(action="call_tool", toolName="search_operation_logs", arguments=self._validate("search_operation_logs", {"page": 1, "size": 20}), intent="operation_logs", responseMode="operation_logs", routeSnapshot=snapshot)
        if route.intent_subtype == "agent_tool_audit":
            return ModelPlanDecision(action="call_tool", toolName="query_agent_tool_audit", arguments=self._validate("query_agent_tool_audit", {"page": 1, "size": 20}), intent="agent_tool_audit", responseMode="agent_tool_audit", routeSnapshot=snapshot)
        if route.intent_subtype == "agent_answer_reviews":
            return ModelPlanDecision(action="call_tool", toolName="query_agent_answer_reviews", arguments=self._validate("query_agent_answer_reviews", {"page": 1, "size": 20}), intent="agent_answer_reviews", responseMode="agent_answer_reviews", routeSnapshot=snapshot)
        if route.intent_subtype == "inventory_ledger":
            return ModelPlanDecision(action="call_tool", toolName="query_inventory_ledger", arguments=self._validate("query_inventory_ledger", {"page": 1, "size": 20}), intent="inventory_ledger", responseMode="inventory_ledger", routeSnapshot=snapshot)
        if route.intent_subtype == "prepare_pool_balance":
            return ModelPlanDecision(action="call_tool", toolName="query_prepare_pool_balance", arguments=self._validate("query_prepare_pool_balance", {"positiveOnly": True, "page": 1, "size": 20}), intent="prepare_pool_balance", responseMode="prepare_pool_balance", routeSnapshot=snapshot)
        if route.intent_subtype == "fixed_product_qr_pool":
            args: dict[str, Any] = {"page": 1, "size": 20}
            if "空闲" in (user_message or "") or "可打印" in (user_message or ""): args["freeOnly"] = True
            return ModelPlanDecision(action="call_tool", toolName="query_fixed_product_qr_pool", arguments=self._validate("query_fixed_product_qr_pool", args), intent="fixed_product_qr_pool", responseMode="fixed_product_qr_pool", routeSnapshot=snapshot)
        if route.intent_subtype == "auto_inbound_batch_detail":
            records = ((state.last_auto_inbound_batches or {}).get("records") or [])
            index = 0
            if "第二" in user_message: index = 1
            elif "第三" in user_message: index = 2
            if len(records) <= index or not isinstance(records[index], dict) or not records[index].get("batchRef"):
                return ModelPlanDecision(action="ask_user", prompt="请先查询智能报数批次，再说明要查看第几个批次。",
                                         suggestions=["查询智能报数批次"], intent="auto_inbound_batch_detail",
                                         responseMode="auto_inbound_batch_detail", routeSnapshot=snapshot)
            return ModelPlanDecision(action="call_tool", toolName="get_auto_inbound_batch_detail",
                                     arguments=self._validate("get_auto_inbound_batch_detail", {"batchRef": records[index]["batchRef"]}),
                                     intent="auto_inbound_batch_detail", responseMode="auto_inbound_batch_detail", routeSnapshot=snapshot)
        if route.intent_subtype == "boiling_batch_trace":
            batch_query = self._boiling_batch_query(user_message)
            if batch_query is None:
                return ModelPlanDecision(
                    action="ask_user",
                    prompt="请提供煮糖批次号，我再查询已登记的追溯关系。",
                    suggestions=["例如：查询煮糖批次 BT-20260713-001 的追溯"],
                    intent="boiling_batch_trace",
                    responseMode="boiling_batch_trace",
                    routeSnapshot=snapshot,
                )
            return ModelPlanDecision(
                action="call_tool",
                toolName="resolve_production_entities",
                arguments=self._validate("resolve_production_entities", {
                    "entityType": "BOILING_BATCH", "query": batch_query, "limit": 5
                }),
                intent="boiling_batch_trace",
                responseMode="boiling_batch_trace",
                confidenceNote="boiling trace requires controlled entity resolution",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "warehouse_inventory_contents" and objects.warehouse:
            return ModelPlanDecision(
                action="call_tool",
                toolName="resolve_warehouses",
                arguments=self._validate("resolve_warehouses", {"query": objects.warehouse, "limit": 10}),
                intent="inventory_distribution",
                responseMode="inventory_distribution",
                confidenceNote="intent router selected warehouse resolver before inventory distribution",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "warehouse_status" and objects.warehouse:
            return ModelPlanDecision(
                action="call_tool",
                toolName="resolve_warehouses",
                arguments=self._validate("resolve_warehouses", {"query": objects.warehouse, "limit": 10}),
                intent="warehouse_status",
                responseMode="warehouse_status",
                confidenceNote="intent router selected warehouse status chain",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "warehouse_status" and state.selected_warehouse is not None:
            return ModelPlanDecision(
                action="call_tool",
                toolName="get_warehouse_status",
                arguments=self._validate("get_warehouse_status", {"warehouseId": state.selected_warehouse.internal_id}),
                intent="warehouse_status",
                responseMode="warehouse_status",
                confidenceNote="conversation context selected a controlled warehouse",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "qr_code_lifecycle":
            if not objects.pallet:
                return ModelPlanDecision(
                    action="ask_user",
                    prompt="请提供要查询的二维码或托盘码，我再展开它的生命周期。",
                    suggestions=["例如：查询托盘码 P202607090001 的生命周期"],
                    intent="qr_code_lifecycle",
                    responseMode="qr_code_lifecycle",
                    routeSnapshot=snapshot,
                )
            return ModelPlanDecision(
                action="call_tool",
                toolName="query_qr_code_lifecycle",
                arguments=self._validate("query_qr_code_lifecycle", self._qr_code_lifecycle_arguments({"code": objects.pallet}, user_message)),
                intent="qr_code_lifecycle",
                responseMode="qr_code_lifecycle",
                confidenceNote="intent router selected QR code lifecycle query from explicit pallet code",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "printed_not_inbound_codes":
            if objects.product and objects.product != "全部产品" and state.selected_product is None:
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="resolve_products",
                    arguments=self._validate("resolve_products", {"query": objects.product, "limit": 10}),
                    intent="printed_not_inbound_codes",
                    responseMode="printed_not_inbound_codes",
                    routeSnapshot=snapshot,
                )
            return ModelPlanDecision(
                action="call_tool",
                toolName="query_printed_not_inbound_codes",
                arguments=self._validate("query_printed_not_inbound_codes", self._printed_not_inbound_arguments({}, state, user_message)),
                intent="printed_not_inbound_codes",
                responseMode="printed_not_inbound_codes",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "pallet_anomalies":
            if objects.product and objects.product != "全部产品" and state.selected_product is None:
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="resolve_products",
                    arguments=self._validate("resolve_products", {"query": objects.product, "limit": 10}),
                    intent="pallet_anomalies",
                    responseMode="pallet_anomalies",
                    routeSnapshot=snapshot,
                )
            return ModelPlanDecision(
                action="call_tool",
                toolName="query_pallet_anomalies",
                arguments=self._validate("query_pallet_anomalies", self._pallet_anomalies_arguments({}, state, user_message)),
                intent="pallet_anomalies",
                responseMode="pallet_anomalies",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "pallet_flow_records":
            if objects.product and objects.product != "全部产品" and state.selected_product is None and not objects.pallet:
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="resolve_products",
                    arguments=self._validate("resolve_products", {"query": objects.product, "limit": 10}),
                    intent="pallet_flow_records",
                    responseMode="pallet_flow_records",
                    routeSnapshot=snapshot,
                )
            return ModelPlanDecision(
                action="call_tool",
                toolName="query_pallet_flow_records",
                arguments=self._validate("query_pallet_flow_records", self._pallet_flow_records_arguments({}, state, user_message)),
                intent="pallet_flow_records",
                responseMode="pallet_flow_records",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "qr_batch_inbound_completion":
            arguments = self._qr_batch_inbound_arguments({}, user_message)
            try:
                validated = self._validate("query_qr_batch_inbound_completion", arguments)
            except ValueError:
                return ModelPlanDecision(
                    action="ask_user",
                    prompt="请提供标签批次号、生产订单号或明确的日期范围，我再统计二维码入库完成率。",
                    suggestions=["例如：批次 LB-001 入库完成率", "最近7天二维码入库完成率"],
                    intent="qr_batch_inbound_completion",
                    responseMode="qr_batch_inbound_completion",
                    routeSnapshot=snapshot,
                )
            return ModelPlanDecision(
                action="call_tool",
                toolName="query_qr_batch_inbound_completion",
                arguments=validated,
                intent="qr_batch_inbound_completion",
                responseMode="qr_batch_inbound_completion",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "pallet_status" and objects.pallet:
            return ModelPlanDecision(
                action="call_tool",
                toolName="get_pallet_status",
                arguments=self._validate("get_pallet_status", {"code": objects.pallet}),
                intent="pallet_status",
                responseMode="pallet_status",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "assay_records":
            if objects.product and objects.product != "全部产品":
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="resolve_products",
                    arguments=self._validate("resolve_products", {"query": objects.product, "limit": 10}),
                    intent="assay_records",
                    responseMode="assay_records",
                    confidenceNote="intent router selected product resolver before assay records query",
                    routeSnapshot=snapshot,
                )
            return ModelPlanDecision(
                action="call_tool",
                toolName="query_assay_records",
                arguments=self._validate(
                    "query_assay_records",
                    self._assay_records_arguments({"productScope": {"type": "ALL"}}, state, user_message, allow_all=True),
                ),
                intent="assay_records",
                responseMode="assay_records",
                confidenceNote="intent router selected all-products assay records query",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "assay_report_detail":
            report_ref = self._latest_assay_report_ref(state)
            if report_ref is None:
                return ModelPlanDecision(
                    action="ask_user",
                    prompt="请先查询或选择一条化验记录，再查看报告详情。",
                    suggestions=["例如：黄冰糖最近一次化验记录", "今天有哪些化验记录"],
                    intent="assay_report_detail",
                    responseMode="assay_report_detail",
                    routeSnapshot=snapshot,
                )
            return ModelPlanDecision(
                action="call_tool",
                toolName="get_assay_report_detail",
                arguments=self._validate(
                    "get_assay_report_detail",
                    {"reportRef": report_ref, "includeMetrics": True, "includeStandardSnapshot": True},
                ),
                intent="assay_report_detail",
                responseMode="assay_report_detail",
                confidenceNote="intent router selected assay report detail from prior recordRef",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "assay_abnormalities":
            if self._assay_abnormalities_all_scope_allowed(user_message):
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="query_assay_abnormalities",
                    arguments=self._validate(
                        "query_assay_abnormalities",
                        self._assay_abnormalities_arguments({"productScope": {"type": "ALL"}}, state, user_message, allow_all=True),
                    ),
                    intent="assay_abnormalities",
                    responseMode="assay_abnormalities",
                    confidenceNote="intent router selected all-products assay abnormalities query",
                    routeSnapshot=snapshot,
                )
            if objects.product and objects.product != "全部产品":
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="resolve_products",
                    arguments=self._validate("resolve_products", {"query": objects.product, "limit": 10}),
                    intent="assay_abnormalities",
                    responseMode="assay_abnormalities",
                    confidenceNote="intent router selected product resolver before assay abnormalities query",
                    routeSnapshot=snapshot,
                )
            return ModelPlanDecision(action="ask_user", prompt="你想查询哪个产品范围的化验异常？", routeSnapshot=snapshot)
        if route.intent_subtype == "products_without_recent_assay":
            if objects.warehouse:
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="resolve_warehouses",
                    arguments=self._validate("resolve_warehouses", {"query": objects.warehouse, "limit": 10}),
                    intent="products_without_recent_assay",
                    responseMode="products_without_recent_assay",
                    confidenceNote="intent router selected warehouse resolver before products without recent assay query",
                    routeSnapshot=snapshot,
                )
            if objects.product and objects.product != "全部产品":
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="resolve_products",
                    arguments=self._validate("resolve_products", {"query": objects.product, "limit": 10}),
                    intent="products_without_recent_assay",
                    responseMode="products_without_recent_assay",
                    confidenceNote="intent router selected product resolver before products without recent assay query",
                    routeSnapshot=snapshot,
                )
            return ModelPlanDecision(
                action="call_tool",
                toolName="query_products_without_recent_assay",
                arguments=self._validate(
                    "query_products_without_recent_assay",
                    self._products_without_recent_assay_arguments(
                        {"productScope": {"type": "ALL"}, "warehouseScope": {"type": "ALL"}},
                        state,
                        user_message,
                        allow_all=True,
                    ),
                ),
                intent="products_without_recent_assay",
                responseMode="products_without_recent_assay",
                confidenceNote="intent router selected products without recent assay query",
                routeSnapshot=snapshot,
            )
        if route.intent_subtype == "assay_standard_coverage":
            if objects.product and objects.product != "全部产品" and not self._assay_standard_coverage_all_scope_allowed(user_message):
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="resolve_products",
                    arguments=self._validate("resolve_products", {"query": objects.product, "limit": 10}),
                    intent="assay_standard_coverage",
                    responseMode="assay_standard_coverage",
                    confidenceNote="intent router selected product resolver before assay standard coverage query",
                    routeSnapshot=snapshot,
                )
            return ModelPlanDecision(
                action="call_tool",
                toolName="query_assay_standard_coverage",
                arguments=self._validate(
                    "query_assay_standard_coverage",
                    self._assay_standard_coverage_arguments(
                        {"productScope": {"type": "ALL"}, "coverageType": "PRODUCT_WITHOUT_STANDARD"},
                        state,
                        user_message,
                        allow_all=True,
                    ),
                ),
                intent="assay_standard_coverage",
                responseMode="assay_standard_coverage",
                confidenceNote="intent router selected assay standard coverage query",
                routeSnapshot=snapshot,
            )
        return None

    def _generate_direct_answer(
        self,
        user_message: str,
        state: WarehouseAgentState,
        route: IntentRoute,
        snapshot: dict[str, Any],
        default_answer: str,
    ) -> str:
        fallback = route.answer or default_answer
        generator = getattr(self._model_client, "generate_direct_answer", None)
        if generator is None:
            return fallback
        try:
            answer = generator(
                ModelDirectAnswerRequest(
                    userMessage=user_message,
                    messages=state.messages,
                    state=state,
                    domainContext=self._context_builder.build(user_message, state),
                    routeSnapshot=snapshot,
                    fallbackAnswer=fallback,
                )
            )
        except ModelStreamError:
            return fallback
        return answer or fallback

    def authorize_tool(self, agent_name: str, tool_name: str) -> None:
        self._agent_router.authorize_tool(agent_name, tool_name)

    def handoff_for_agent(
        self,
        agent_name: str,
        *,
        business_domain: str | None = None,
        mode: str = "orchestration_step",
    ) -> AgentHandoff:
        return self._agent_router.handoff_for_agent(
            agent_name,
            business_domain=business_domain,
            mode=mode,
        )

    def _context_for(
        self,
        user_message: str,
        state: WarehouseAgentState,
        handoff: AgentHandoff,
    ) -> list[Any]:
        return [
            *self._context_builder.build(user_message, state),
            self._agent_router.context_pack(handoff),
        ]

    def _model_client_for(self, handoff: AgentHandoff) -> ModelClient:
        return self._expert_model_clients.get(handoff.target_agent, self._model_client)

    def _goal_draft_shadow(
        self,
        user_message: str,
        state: WarehouseAgentState,
    ) -> dict[str, Any] | None:
        if not self._goal_draft_shadow_enabled:
            return None
        started = time.perf_counter()
        draft_goal = getattr(self._model_client, "draft_goal", None)
        if not callable(draft_goal):
            return {
                "schemaVersion": "1.0",
                "status": "UNAVAILABLE",
                "executionInfluence": False,
                "latencyMs": self._shadow_latency_ms(started),
            }
        try:
            draft = draft_goal(
                GoalDraftRequest(
                    userMessage=self._redact_shadow_text(user_message),
                    messages=self._shadow_messages(state.messages),
                    selectedContext=self._selected_context(state),
                )
            )
        except RunCancelledError:
            raise
        except Exception:
            return {
                "schemaVersion": "1.0",
                "status": "UNAVAILABLE",
                "executionInfluence": False,
                "latencyMs": self._shadow_latency_ms(started),
            }
        if draft is None:
            return {
                "schemaVersion": "1.0",
                "status": "UNAVAILABLE",
                "executionInfluence": False,
                "latencyMs": self._shadow_latency_ms(started),
            }
        return {
            "schemaVersion": draft.schemaVersion,
            "status": "AVAILABLE",
            "executionInfluence": False,
            "latencyMs": self._shadow_latency_ms(started),
            "draft": {
                "goalType": draft.goalType,
                "entityMentions": [
                    {
                        "entityType": mention.entityType,
                        "referenceKind": mention.referenceKind,
                    }
                    for mention in draft.entityMentions
                ],
                "contextReuse": list(draft.contextReuse),
                "missingEntities": list(draft.missingEntities),
                "dataNeed": draft.dataNeed,
                "presentationPreference": draft.presentationPreference,
                "needsClarification": draft.needsClarification,
                "clarificationReason": draft.clarificationReason,
                "confidence": draft.confidence,
            },
        }

    def _shadow_latency_ms(self, started: float) -> int:
        return max(0, round((time.perf_counter() - started) * 1000))

    def _attach_goal_draft_shadow(
        self,
        snapshot: dict[str, Any],
        shadow: dict[str, Any] | None,
    ) -> None:
        if shadow is not None:
            snapshot["goalDraftShadow"] = shadow

    def _shadow_messages(self, messages: list[dict[str, Any]]) -> list[dict[str, str]]:
        safe_messages: list[dict[str, str]] = []
        for message in messages[-8:]:
            role = message.get("role")
            content = message.get("content")
            if role not in {"user", "assistant"} or not isinstance(content, str):
                continue
            safe_messages.append(
                {
                    "role": role,
                    "content": self._redact_shadow_text(content)[:1000],
                }
            )
        return safe_messages

    def _selected_context(self, state: WarehouseAgentState) -> dict[str, str]:
        selected: dict[str, str] = {}
        if state.selected_product is not None:
            selected["PRODUCT"] = state.selected_product.display_label[:200]
        if state.selected_warehouse is not None:
            selected["WAREHOUSE"] = state.selected_warehouse.display_label[:200]
        if state.selected_production_order is not None:
            selected["PRODUCTION_ORDER"] = state.selected_production_order.display_label[:200]
        return selected

    def _redact_shadow_text(self, value: str) -> str:
        return re.sub(
            r"(?i)(authorization\s*[:=]\s*(?:bearer\s+)?[^,;\s]+|bearer\s+[a-z0-9._-]+|"
            r"(?:password|secret|api[_ -]?key|token)\s*[:=]\s*[^,;\s]+|"
            r"eyJ[a-z0-9_-]+\.[a-z0-9_-]+\.[a-z0-9_-]+)",
            "[REDACTED]",
            value,
        )

    def _finalize_plan(
        self,
        decision: ModelPlanDecision,
        handoff: AgentHandoff,
        snapshot: dict[str, Any],
    ) -> ModelPlanDecision:
        if decision.action == "call_tool":
            if not decision.toolName:
                raise ValueError("planned tool is missing")
            self._agent_router.authorize_tool(handoff.target_agent, decision.toolName)
        router_goal = registered_goal_for_plan(
            tool_name=decision.toolName,
            arguments=decision.arguments,
            intent=decision.intent,
            response_mode=decision.responseMode,
        )
        if router_goal is not None:
            snapshot["registeredGoalType"] = router_goal
            shadow = snapshot.get("goalDraftShadow")
            draft = shadow.get("draft") if isinstance(shadow, dict) else None
            llm_goal = draft.get("goalType") if isinstance(draft, dict) else None
            snapshot["goalRouteComparison"] = {
                "deterministicGoalType": router_goal,
                "llmGoalType": llm_goal,
                "agreement": llm_goal == router_goal if llm_goal else None,
                "llmLatencyMs": shadow.get("latencyMs") if isinstance(shadow, dict) else None,
                "executionPath": "DETERMINISTIC",
            }
        return replace(decision, routeSnapshot=snapshot)

    def context_packs(self, user_message: str, state: WarehouseAgentState) -> list[str]:
        return [pack.name for pack in self._context_builder.build(user_message, state)]

    def stream_answer_deltas(self, answer: str) -> Iterator[str]:
        return self._model_client.stream_answer_deltas(answer)

    def distribution_arguments_for_state(
        self, arguments: dict[str, Any], state: WarehouseAgentState
    ) -> dict[str, Any]:
        return self._validate(
            "get_inventory_distribution",
            self._distribution_arguments(arguments, state),
        )

    def assay_records_arguments_for_state(
        self, arguments: dict[str, Any], state: WarehouseAgentState, user_message: str
    ) -> dict[str, Any]:
        return self._validate(
            "query_assay_records",
            self._assay_records_arguments(arguments, state, user_message),
        )

    def assay_report_detail_arguments_for_state(
        self, arguments: dict[str, Any], state: WarehouseAgentState
    ) -> dict[str, Any]:
        return self._validate(
            "get_assay_report_detail",
            self._assay_report_detail_arguments(arguments, state),
        )

    def assay_abnormalities_arguments_for_state(
        self, arguments: dict[str, Any], state: WarehouseAgentState, user_message: str
    ) -> dict[str, Any]:
        return self._validate(
            "query_assay_abnormalities",
            self._assay_abnormalities_arguments(arguments, state, user_message),
        )

    def products_without_recent_assay_arguments_for_state(
        self, arguments: dict[str, Any], state: WarehouseAgentState, user_message: str
    ) -> dict[str, Any]:
        return self._validate(
            "query_products_without_recent_assay",
            self._products_without_recent_assay_arguments(arguments, state, user_message, allow_all=True),
        )

    def assay_standard_coverage_arguments_for_state(
        self, arguments: dict[str, Any], state: WarehouseAgentState, user_message: str
    ) -> dict[str, Any]:
        return self._validate(
            "query_assay_standard_coverage",
            self._assay_standard_coverage_arguments(arguments, state, user_message, allow_all=True),
        )

    def qr_code_lifecycle_arguments_for_state(self, arguments: dict[str, Any], user_message: str) -> dict[str, Any]:
        return self._validate("query_qr_code_lifecycle", self._qr_code_lifecycle_arguments(arguments, user_message))

    def printed_not_inbound_arguments_for_state(
        self, arguments: dict[str, Any], state: WarehouseAgentState, user_message: str
    ) -> dict[str, Any]:
        return self._validate("query_printed_not_inbound_codes", self._printed_not_inbound_arguments(arguments, state, user_message))

    def pallet_anomalies_arguments_for_state(
        self, arguments: dict[str, Any], state: WarehouseAgentState, user_message: str
    ) -> dict[str, Any]:
        return self._validate("query_pallet_anomalies", self._pallet_anomalies_arguments(arguments, state, user_message))

    def pallet_flow_records_arguments_for_state(
        self, arguments: dict[str, Any], state: WarehouseAgentState, user_message: str
    ) -> dict[str, Any]:
        return self._validate("query_pallet_flow_records", self._pallet_flow_records_arguments(arguments, state, user_message))

    def qr_batch_inbound_arguments_for_state(self, arguments: dict[str, Any], user_message: str) -> dict[str, Any]:
        return self._validate("query_qr_batch_inbound_completion", self._qr_batch_inbound_arguments(arguments, user_message))

    def _validate(self, tool_name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        if tool_name in {"resolve_products", "resolve_warehouses"}:
            query = str(arguments.get("query") or "").strip()
            if not 1 <= len(query) <= 100:
                raise ValueError("query must be 1..100 characters")
            limit = int(arguments.get("limit", 10))
            if not 1 <= limit <= 100:
                raise ValueError("limit must be 1..100")
            return {"query": query, "limit": limit}
        if tool_name == "get_pallet_status":
            code = str(arguments.get("code") or "").strip()
            if not 1 <= len(code) <= 100:
                raise ValueError("code must be 1..100 characters")
            flow_limit = int(arguments.get("flowLimit", 20))
            if not 1 <= flow_limit <= 100:
                raise ValueError("flowLimit must be 1..100")
            return {
                "code": code,
                "includeInventory": bool(arguments.get("includeInventory", True)),
                "includeAssay": bool(arguments.get("includeAssay", True)),
                "includeFlows": bool(arguments.get("includeFlows", True)),
                "flowLimit": flow_limit,
            }
        if tool_name == "get_inventory_overview":
            product_id = int(arguments.get("productId") or 0)
            if product_id <= 0:
                raise ValueError("productId must be positive")
            return {"productId": product_id}
        if tool_name == "get_inventory_distribution":
            return self._validate_distribution(arguments)
        if tool_name == "query_assay_records":
            return self._validate_assay_records(arguments)
        if tool_name == "get_assay_report_detail":
            return self._validate_assay_report_detail(arguments)
        if tool_name == "query_assay_abnormalities":
            return self._validate_assay_abnormalities(arguments)
        if tool_name == "query_products_without_recent_assay":
            return self._validate_products_without_recent_assay(arguments)
        if tool_name == "query_assay_standard_coverage":
            return self._validate_assay_standard_coverage(arguments)
        if tool_name == "query_qr_code_lifecycle":
            return self._validate_qr_code_lifecycle(arguments)
        if tool_name == "query_printed_not_inbound_codes":
            return self._validate_printed_not_inbound(arguments)
        if tool_name == "query_pallet_anomalies":
            return self._validate_pallet_anomalies(arguments)
        if tool_name == "query_pallet_flow_records":
            return self._validate_pallet_flow_records(arguments)
        if tool_name == "query_qr_batch_inbound_completion":
            return self._validate_qr_batch_inbound(arguments)
        if tool_name == "resolve_production_entities":
            entity_type = str(arguments.get("entityType") or "").strip().upper()
            if entity_type not in {"PRODUCTION_ORDER", "BOILING_BATCH"}:
                raise ValueError("unsupported production entityType")
            return {
                "entityType": entity_type,
                "query": self._required_text(arguments.get("query"), 100),
                "limit": max(1, min(int(arguments.get("limit", 5)), 10)),
            }
        if tool_name == "query_production_order_progress":
            order_ref = self._required_text(arguments.get("orderRef"), 500)
            if not order_ref.startswith("aer_"):
                raise ValueError("orderRef must be a controlled Agent entity ref")
            return {"orderRef": order_ref}
        if tool_name == "query_boiling_batch_trace":
            batch_ref = self._required_text(arguments.get("batchRef"), 500)
            if not batch_ref.startswith("aer_"):
                raise ValueError("batchRef must be a controlled Agent entity ref")
            return {"batchRef": batch_ref}
        if tool_name == "query_material_pick_trace":
            order_ref = self._required_text(arguments.get("orderRef"), 500)
            if not order_ref.startswith("aer_"):
                raise ValueError("orderRef must be a controlled Agent entity ref")
            return {"orderRef": order_ref}
        if tool_name == "query_production_label_completion":
            order_ref = self._required_text(arguments.get("orderRef"), 500)
            if not order_ref.startswith("aer_"):
                raise ValueError("orderRef must be a controlled Agent entity ref")
            return {"orderRef": order_ref}
        if tool_name == "query_in_process_materials":
            result: dict[str, Any] = {}
            for key, limit in (("productName", 100), ("productType", 50)):
                if arguments.get(key) is not None:
                    result[key] = self._required_text(arguments.get(key), limit)
            start = self._optional_date(arguments.get("productionDateStart"))
            end = self._optional_date(arguments.get("productionDateEnd"))
            if start and end and start > end:
                raise ValueError("productionDateStart must not be after productionDateEnd")
            if start: result["productionDateStart"] = start
            if end: result["productionDateEnd"] = end
            page, size = int(arguments.get("page", 1)), int(arguments.get("size", 20))
            if page < 1 or not 1 <= size <= 50:
                raise ValueError("invalid pagination")
            result.update({"page": page, "size": size})
            return result
        if tool_name == "query_material_candidates":
            order_ref = self._required_text(arguments.get("orderRef"), 500)
            if not order_ref.startswith("aer_"):
                raise ValueError("orderRef must be a controlled Agent entity ref")
            page, size = int(arguments.get("page", 1)), int(arguments.get("size", 20))
            if page < 1 or not 1 <= size <= 50:
                raise ValueError("invalid pagination")
            return {"orderRef": order_ref, "page": page, "size": size}
        if tool_name == "query_pallet_tasks":
            result: dict[str, Any] = {}
            for key, limit in (("code", 100), ("productName", 100), ("productType", 50), ("targetWarehouseName", 100)):
                if arguments.get(key) is not None: result[key] = self._required_text(arguments.get(key), limit)
            for key, allowed in (("taskType", {"IN", "SEMI_IN", "FINISH_IN", "OUT", "TRANSFER"}),
                                 ("bizScene", {"DIRECT_OUT", "PREPARE_CONSUMED", "FINISH_OUT"}),
                                 ("status", {"PENDING", "CONFIRMED", "CANCELED"}),
                                 ("productStatus", {"半成品", "成品"})):
                value = arguments.get(key)
                if value is not None:
                    if value not in allowed: raise ValueError(f"unsupported {key}")
                    result[key] = value
            start, end = self._optional_date(arguments.get("productionDateStart")), self._optional_date(arguments.get("productionDateEnd"))
            if start and end and start > end: raise ValueError("invalid production date range")
            if start: result["productionDateStart"] = start
            if end: result["productionDateEnd"] = end
            page, size = int(arguments.get("page", 1)), int(arguments.get("size", 20))
            if page < 1 or not 1 <= size <= 50: raise ValueError("invalid pagination")
            result.update({"page": page, "size": size})
            return result
        if tool_name == "query_stock_documents":
            doc_type = arguments.get("documentType")
            if doc_type not in {"INBOUND", "OUTBOUND", "SEMI_PRODUCT"}: raise ValueError("unsupported documentType")
            result: dict[str, Any] = {"documentType": doc_type}
            for key in ("productName", "warehouseName", "operatorName"):
                if arguments.get(key) is not None: result[key] = self._required_text(arguments.get(key), 100)
            start, end = self._optional_date(arguments.get("startDate")), self._optional_date(arguments.get("endDate"))
            if start and end and start > end: raise ValueError("invalid date range")
            if start: result["startDate"] = start
            if end: result["endDate"] = end
            page, size = int(arguments.get("page", 1)), int(arguments.get("size", 20))
            if page < 1 or not 1 <= size <= 50: raise ValueError("invalid pagination")
            result.update({"page": page, "size": size}); return result
        if tool_name == "query_auto_inbound_batches":
            result: dict[str, Any] = {}
            if arguments.get("status") is not None:
                result["status"] = self._required_text(arguments.get("status"), 50)
            limit = int(arguments.get("limit", 20))
            if not 1 <= limit <= 20: raise ValueError("invalid limit")
            result["limit"] = limit
            return result
        if tool_name == "get_auto_inbound_batch_detail":
            batch_ref = self._required_text(arguments.get("batchRef"), 100)
            if not batch_ref.startswith("aibr_") or len(batch_ref) < 48:
                raise ValueError("batchRef must be a controlled auto-inbound reference")
            return {"batchRef": batch_ref}
        if tool_name == "query_warehouse_capacity_distribution":
            scope = arguments.get("warehouseScope") or {"type": "ALL"}
            if not isinstance(scope, dict) or scope.get("type") not in {"ALL", "SINGLE_WAREHOUSE"}:
                raise ValueError("invalid warehouseScope")
            clean_scope: dict[str, Any] = {"type": scope["type"]}
            if scope["type"] == "SINGLE_WAREHOUSE":
                warehouse_id = int(scope.get("warehouseId") or 0)
                if warehouse_id <= 0: raise ValueError("warehouseId is required")
                clean_scope["warehouseId"] = warehouse_id
            band = arguments.get("occupancyBand", "ANY")
            if band not in {"ANY", "EMPTY", "LOW", "MEDIUM", "HIGH", "FULL"}: raise ValueError("invalid occupancyBand")
            page, size = int(arguments.get("page", 1)), int(arguments.get("size", 20))
            if page < 1 or not 1 <= size <= 50: raise ValueError("invalid pagination")
            return {"warehouseScope": clean_scope, "occupancyBand": band,
                    "onlyAvailable": bool(arguments.get("onlyAvailable", False)), "page": page, "size": size}
        if tool_name == "query_warehouse_recent_operations":
            result: dict[str, Any] = {}
            if arguments.get("warehouseId") is not None:
                warehouse_id = int(arguments.get("warehouseId") or 0)
                if warehouse_id <= 0: raise ValueError("invalid warehouseId")
                result["warehouseId"] = warehouse_id
            event_types = arguments.get("eventTypes") or []
            if not isinstance(event_types, list) or len(event_types) > 3 or any(value not in {"INBOUND", "OUTBOUND", "TRANSFER"} for value in event_types):
                raise ValueError("invalid eventTypes")
            result["eventTypes"] = list(dict.fromkeys(event_types))
            for key in ("from", "to"):
                if arguments.get(key) is not None: result[key] = self._required_text(arguments.get(key), 40)
            limit = int(arguments.get("limit", 20))
            if not 1 <= limit <= 50: raise ValueError("invalid limit")
            result["limit"] = limit
            return result
        if tool_name == "query_warehouse_mixed_storage_facts":
            result: dict[str, Any] = {}
            if arguments.get("warehouseId") is not None:
                warehouse_id = int(arguments.get("warehouseId") or 0)
                if warehouse_id <= 0: raise ValueError("invalid warehouseId")
                result["warehouseId"] = warehouse_id
            fact_type = arguments.get("factType", "ANY")
            if fact_type not in {"ANY", "MULTIPLE_PRODUCTS", "MULTIPLE_SPECIFICATIONS"}: raise ValueError("invalid factType")
            limit = int(arguments.get("limit", 20))
            if not 1 <= limit <= 50: raise ValueError("invalid limit")
            result.update({"factType": fact_type, "limit": limit}); return result
        if tool_name == "query_product_catalog":
            result: dict[str, Any] = {}
            for key, limit in (("productName", 100), ("productType", 50), ("packagingMethod", 50), ("screenMeshName", 100)):
                if arguments.get(key) is not None: result[key] = self._required_text(arguments.get(key), limit)
            status = arguments.get("productStatus")
            if status is not None:
                if status not in {"半成品", "成品"}: raise ValueError("invalid productStatus")
                result["productStatus"] = status
            page, size = int(arguments.get("page", 1)), int(arguments.get("size", 20))
            if page < 1 or not 1 <= size <= 50: raise ValueError("invalid pagination")
            result.update({"page": page, "size": size}); return result
        if tool_name == "get_product_detail":
            return {"productName": self._required_text(arguments.get("productName"), 100)}
        if tool_name == "query_screen_mesh_catalog":
            result: dict[str, Any] = {}
            if arguments.get("meshName") is not None: result["meshName"] = self._required_text(arguments.get("meshName"), 100)
            page, size = int(arguments.get("page", 1)), int(arguments.get("size", 20))
            if page < 1 or not 1 <= size <= 50: raise ValueError("invalid pagination")
            result.update({"page": page, "size": size}); return result
        if tool_name in {"query_assay_groups", "query_quality_standard_catalog"}:
            result: dict[str, Any] = {}
            limits = {"groupName": 100, "productType": 50, "standardName": 100}
            for key, limit in limits.items():
                if arguments.get(key) is not None: result[key] = self._required_text(arguments.get(key), limit)
            if arguments.get("status") is not None:
                if arguments["status"] not in {"ENABLED", "DISABLED"}: raise ValueError("invalid status")
                result["status"] = arguments["status"]
            page, size = int(arguments.get("page", 1)), int(arguments.get("size", 20))
            if page < 1 or not 1 <= size <= 50: raise ValueError("invalid pagination")
            result.update({"page": page, "size": size}); return result
        if tool_name == "get_quality_standard_detail":
            version = int(arguments.get("version") or 0)
            if version <= 0: raise ValueError("invalid version")
            return {"standardCode": self._required_text(arguments.get("standardCode"), 100), "version": version}
        if tool_name == "query_product_standard_relations":
            return {"productName": self._required_text(arguments.get("productName"), 100)}
        if tool_name in {"query_employee_roster", "query_roles"}:
            result: dict[str, Any] = {}
            limits = {"employeeId": 50, "name": 100, "department": 100, "position": 100, "status": 20, "roleCode": 50, "keyword": 100}
            for key, limit in limits.items():
                if arguments.get(key) is not None: result[key] = self._required_text(arguments.get(key), limit)
            if tool_name == "query_roles" and result.get("status") not in {None, "ENABLED", "DISABLED"}: raise ValueError("invalid status")
            page, size = int(arguments.get("page", 1)), int(arguments.get("size", 20))
            if page < 1 or not 1 <= size <= 50: raise ValueError("invalid pagination")
            result.update({"page": page, "size": size}); return result
        if tool_name == "get_role_permission_summary":
            return {"roleCodeOrName": self._required_text(arguments.get("roleCodeOrName"), 100)}
        if tool_name in {"search_operation_logs", "query_agent_tool_audit", "query_agent_answer_reviews"}:
            result: dict[str, Any] = {}
            limits = {"module": 100, "operationType": 20, "operator": 100, "capability": 100, "resultCode": 40, "errorCode": 80,
                      "reviewStatus": 40, "answerStatus": 40, "failureDomain": 80, "failureCategory": 120, "suggestedFixType": 80, "testCaseStatus": 40}
            for key, limit in limits.items():
                if arguments.get(key) is not None: result[key] = self._required_text(arguments.get(key), limit)
            for key in ("startTime", "endTime"):
                if arguments.get(key) is not None: result[key] = self._required_text(arguments.get(key), 40)
            if arguments.get("priorityOnly") is not None: result["priorityOnly"] = bool(arguments.get("priorityOnly"))
            page, size = int(arguments.get("page", 1)), int(arguments.get("size", 20))
            if page < 1 or not 1 <= size <= 50: raise ValueError("invalid pagination")
            result.update({"page": page, "size": size}); return result
        if tool_name in {"query_inventory_ledger", "query_prepare_pool_balance"}:
            result: dict[str, Any] = {}
            limits = {"productName": 100, "warehouseName": 100, "screenMeshName": 100, "productStatus": 50, "productType": 50}
            for key, limit in limits.items():
                if arguments.get(key) is not None: result[key] = self._required_text(arguments.get(key), limit)
            for key in ("entryDateStart", "entryDateEnd", "productionDateStart", "productionDateEnd"):
                if arguments.get(key) is not None: result[key] = self._required_text(arguments.get(key), 10)
            if tool_name == "query_prepare_pool_balance":
                if arguments.get("positiveOnly", True) is not True: raise ValueError("only positiveOnly=true is supported")
                result["positiveOnly"] = True
            page, size = int(arguments.get("page", 1)), int(arguments.get("size", 20))
            if page < 1 or not 1 <= size <= 50: raise ValueError("invalid pagination")
            result.update({"page": page, "size": size}); return result
        if tool_name == "query_fixed_product_qr_pool":
            result: dict[str, Any] = {}
            for key, limit in (("productName", 100), ("status", 30)):
                if arguments.get(key) is not None: result[key] = self._required_text(arguments.get(key), limit)
            if arguments.get("codes") is not None:
                values = arguments.get("codes")
                if not isinstance(values, list) or len(values) > 20: raise ValueError("invalid codes")
                result["codes"] = list(dict.fromkeys(self._required_text(value, 100) for value in values))
            if arguments.get("freeOnly") is not None: result["freeOnly"] = bool(arguments.get("freeOnly"))
            page, size = int(arguments.get("page", 1)), int(arguments.get("size", 20))
            if page < 1 or not 1 <= size <= 50: raise ValueError("invalid pagination")
            result.update({"page": page, "size": size}); return result
        if tool_name == "get_warehouse_status":
            warehouse_id = int(arguments.get("warehouseId") or 0)
            if warehouse_id <= 0:
                raise ValueError("warehouseId must be positive")
            return {"warehouseId": warehouse_id}
        if tool_name == "get_assay_status":
            result: dict[str, Any] = {}
            if arguments.get("assayId") is not None:
                assay_id = int(arguments.get("assayId") or 0)
                if assay_id <= 0:
                    raise ValueError("assayId must be positive")
                result["assayId"] = assay_id
            if arguments.get("productId") is not None:
                product_id = int(arguments.get("productId") or 0)
                if product_id <= 0:
                    raise ValueError("productId must be positive")
                result["productId"] = product_id
            production_date = str(arguments.get("productionDate") or "").strip()
            if production_date:
                result["productionDate"] = production_date[:20]
            if "assayId" not in result and "productId" not in result:
                raise ValueError("get_assay_status requires assayId or productId")
            return result
        return dict(arguments)

    def _distribution_arguments(
        self, arguments: dict[str, Any], state: WarehouseAgentState, *, allow_all: bool = False
    ) -> dict[str, Any]:
        result = dict(arguments)
        requested_scope = arguments.get("productScope")
        if allow_all and isinstance(requested_scope, dict) and requested_scope.get("type") == "ALL":
            result["productScope"] = {"type": "ALL"}
        elif state.selected_product is not None:
            metadata = state.selected_product.metadata
            scope_type = str(metadata.get("scopeType") or "SINGLE_PRODUCT")
            if scope_type == "SINGLE_PRODUCT":
                if state.selected_product.internal_id is None:
                    raise ValueError("selected product has no validated productId")
                result["productScope"] = {
                    "type": scope_type,
                    "productId": state.selected_product.internal_id,
                }
            elif scope_type == "EXACT_PRODUCT_NAME_GROUP":
                result["productScope"] = {"type": scope_type, "productName": metadata.get("productName")}
            elif scope_type == "PRODUCT_TYPE_GROUP":
                result["productScope"] = {"type": scope_type, "productType": metadata.get("productType")}
            else:
                raise ValueError("selected product scope is unsupported")
        elif not isinstance(requested_scope, dict) or requested_scope.get("type") != "ALL":
            raise ValueError("distribution product scope requires resolver provenance")

        warehouse_scope = arguments.get("warehouseScope") or {"type": "ALL"}
        if isinstance(warehouse_scope, dict) and warehouse_scope.get("type") == "SINGLE_WAREHOUSE":
            requested_id = int(warehouse_scope.get("warehouseId") or 0)
            if state.selected_warehouse is None or state.selected_warehouse.internal_id != requested_id:
                raise ValueError("warehouseId does not match selected warehouse")
        result["warehouseScope"] = warehouse_scope
        result.setdefault("statusFilter", {})
        result.setdefault("groupBy", "warehouse")
        result.setdefault("limit", 20)
        return result

    def _assay_report_detail_arguments(self, arguments: dict[str, Any], state: WarehouseAgentState) -> dict[str, Any]:
        result = dict(arguments)
        report_ref = str(result.get("reportRef") or "").strip()
        if not report_ref:
            report_ref = self._latest_assay_report_ref(state) or ""
        if not report_ref:
            raise ValueError("assay report detail requires a prior reportRef")
        result["reportRef"] = report_ref
        result.setdefault("includeMetrics", True)
        result.setdefault("includeStandardSnapshot", True)
        return result

    def _pallet_task_followup_arguments(
        self,
        arguments: dict[str, Any],
        state: WarehouseAgentState,
        user_message: str,
    ) -> dict[str, Any]:
        result = dict(arguments)
        text = re.sub(r"\s+", "", user_message or "")
        reset_scope = any(marker in text for marker in ("全部任务", "所有任务", "不限状态", "清除筛选"))
        is_followup = text.startswith(("只看", "仅看", "只查", "仅查", "筛选", "其中", "这些", "再看", "再查", "换成", "改看"))
        previous = state.last_pallet_task_filters if isinstance(state.last_pallet_task_filters, dict) else {}
        if is_followup and previous and not reset_scope:
            for key in (
                "status", "taskType", "bizScene", "productName", "productType", "productStatus",
                "targetWarehouseName", "productionDateStart", "productionDateEnd", "size",
            ):
                if key in previous:
                    result.setdefault(key, previous[key])

        if "待处理" in text:
            result["status"] = "PENDING"
        elif "已确认" in text:
            result["status"] = "CONFIRMED"
        elif "已取消" in text:
            result["status"] = "CANCELED"
        elif reset_scope:
            result.pop("status", None)

        if "半成品入库" in text:
            result["taskType"] = "SEMI_IN"
        elif "成品入库" in text:
            result["taskType"] = "FINISH_IN"
        elif "调拨" in text:
            result["taskType"] = "TRANSFER"
        elif "出库" in text:
            result["taskType"] = "OUT"
        elif "入库" in text:
            result["taskType"] = "IN"

        if "半成品" in text:
            result["productStatus"] = "半成品"
        elif "成品" in text:
            result["productStatus"] = "成品"
        result["page"] = 1
        return result

    def _assay_records_arguments(
        self,
        arguments: dict[str, Any],
        state: WarehouseAgentState,
        user_message: str,
        *,
        allow_all: bool = False,
    ) -> dict[str, Any]:
        result = dict(arguments)
        requested_scope = arguments.get("productScope")
        if allow_all and isinstance(requested_scope, dict) and requested_scope.get("type") == "ALL":
            result["productScope"] = {"type": "ALL"}
        elif state.selected_product is not None:
            metadata = state.selected_product.metadata
            scope_type = str(metadata.get("scopeType") or "SINGLE_PRODUCT")
            if scope_type == "SINGLE_PRODUCT":
                if state.selected_product.internal_id is None:
                    raise ValueError("selected product has no validated productId")
                result["productScope"] = {"type": scope_type, "productId": state.selected_product.internal_id}
            elif scope_type == "EXACT_PRODUCT_NAME_GROUP":
                result["productScope"] = {"type": scope_type, "productName": metadata.get("productName")}
            elif scope_type == "PRODUCT_TYPE_GROUP":
                result["productScope"] = {"type": scope_type, "productType": metadata.get("productType")}
            else:
                raise ValueError("selected product scope is unsupported")
        elif not isinstance(requested_scope, dict) or requested_scope.get("type") != "ALL":
            raise ValueError("assay records product scope requires resolver provenance")

        result.setdefault("dateRange", self._assay_date_range_from_message(user_message))
        result.setdefault("judgeStatus", self._assay_judge_status_from_message(user_message))
        result.setdefault("sortBy", "sampleDate")
        result.setdefault("sortDirection", "DESC")
        result.setdefault("page", 1)
        result.setdefault("size", 1 if "最近一次" in user_message else 20)
        return result

    def _assay_abnormalities_arguments(
        self,
        arguments: dict[str, Any],
        state: WarehouseAgentState,
        user_message: str,
        *,
        allow_all: bool = False,
    ) -> dict[str, Any]:
        result = dict(arguments)
        requested_scope = arguments.get("productScope")
        if allow_all and isinstance(requested_scope, dict) and requested_scope.get("type") == "ALL":
            result["productScope"] = {"type": "ALL"}
        elif state.selected_product is not None:
            metadata = state.selected_product.metadata
            scope_type = str(metadata.get("scopeType") or "SINGLE_PRODUCT")
            if scope_type == "SINGLE_PRODUCT":
                if state.selected_product.internal_id is None:
                    raise ValueError("selected product has no validated productId")
                result["productScope"] = {"type": scope_type, "productId": state.selected_product.internal_id}
            elif scope_type == "EXACT_PRODUCT_NAME_GROUP":
                result["productScope"] = {"type": scope_type, "productName": metadata.get("productName")}
            elif scope_type == "PRODUCT_TYPE_GROUP":
                result["productScope"] = {"type": scope_type, "productType": metadata.get("productType")}
            else:
                raise ValueError("selected product scope is unsupported")
        elif not isinstance(requested_scope, dict) or requested_scope.get("type") != "ALL":
            raise ValueError("assay abnormalities product scope requires resolver provenance")

        result.setdefault("dateRange", self._assay_date_range_from_message(user_message))
        result.setdefault("abnormalTypes", self._assay_abnormal_types_from_message(user_message))
        result.setdefault("groupBy", self._assay_abnormal_group_by_from_message(user_message))
        result.setdefault("limit", 50)
        return result

    def _products_without_recent_assay_arguments(
        self,
        arguments: dict[str, Any],
        state: WarehouseAgentState,
        user_message: str,
        *,
        allow_all: bool = False,
    ) -> dict[str, Any]:
        result = dict(arguments)
        requested_scope = arguments.get("productScope")
        if allow_all and isinstance(requested_scope, dict) and requested_scope.get("type") == "ALL":
            result["productScope"] = {"type": "ALL"}
        elif state.selected_product is not None:
            metadata = state.selected_product.metadata
            scope_type = str(metadata.get("scopeType") or "SINGLE_PRODUCT")
            if scope_type == "SINGLE_PRODUCT":
                if state.selected_product.internal_id is None:
                    raise ValueError("selected product has no validated productId")
                result["productScope"] = {"type": scope_type, "productId": state.selected_product.internal_id}
            elif scope_type == "EXACT_PRODUCT_NAME_GROUP":
                result["productScope"] = {"type": scope_type, "productName": metadata.get("productName")}
            elif scope_type == "PRODUCT_TYPE_GROUP":
                result["productScope"] = {"type": scope_type, "productType": metadata.get("productType")}
            else:
                raise ValueError("selected product scope is unsupported")
        elif not isinstance(requested_scope, dict) or requested_scope.get("type") != "ALL":
            raise ValueError("products without recent assay product scope requires resolver provenance")

        warehouse_scope = arguments.get("warehouseScope") or {"type": "ALL"}
        if isinstance(warehouse_scope, dict) and warehouse_scope.get("type") == "SINGLE_WAREHOUSE":
            requested_id = int(warehouse_scope.get("warehouseId") or 0)
            if state.selected_warehouse is None or state.selected_warehouse.internal_id != requested_id:
                raise ValueError("warehouseId does not match selected warehouse")
        result["warehouseScope"] = warehouse_scope
        result.setdefault("population", "CURRENT_INVENTORY")
        result.setdefault("dateRange", self._assay_date_range_from_message(user_message))
        result.setdefault("groupBy", self._products_without_recent_assay_group_by_from_message(user_message))
        result.setdefault("limit", 50)
        return result

    def _assay_standard_coverage_arguments(
        self,
        arguments: dict[str, Any],
        state: WarehouseAgentState,
        user_message: str,
        *,
        allow_all: bool = False,
    ) -> dict[str, Any]:
        result = dict(arguments)
        requested_scope = arguments.get("productScope")
        if allow_all and isinstance(requested_scope, dict) and requested_scope.get("type") == "ALL":
            result["productScope"] = {"type": "ALL"}
        elif state.selected_product is not None:
            metadata = state.selected_product.metadata
            scope_type = str(metadata.get("scopeType") or "SINGLE_PRODUCT")
            if scope_type == "SINGLE_PRODUCT":
                if state.selected_product.internal_id is None:
                    raise ValueError("selected product has no validated productId")
                result["productScope"] = {"type": scope_type, "productId": state.selected_product.internal_id}
            elif scope_type == "EXACT_PRODUCT_NAME_GROUP":
                result["productScope"] = {"type": scope_type, "productName": metadata.get("productName")}
            elif scope_type == "PRODUCT_TYPE_GROUP":
                result["productScope"] = {"type": scope_type, "productType": metadata.get("productType")}
            else:
                raise ValueError("selected product scope is unsupported")
        elif not isinstance(requested_scope, dict) or requested_scope.get("type") != "ALL":
            raise ValueError("assay standard coverage product scope requires resolver provenance")

        result.setdefault("coverageType", "PRODUCT_WITHOUT_STANDARD")
        result.setdefault("dateRange", self._assay_date_range_from_message(user_message))
        result.setdefault("limit", 50)
        return result

    def _qr_code_lifecycle_arguments(self, arguments: dict[str, Any], user_message: str) -> dict[str, Any]:
        result = dict(arguments)
        result.setdefault("includeInventory", True)
        result.setdefault("includeAssay", True)
        result.setdefault("includeFlows", True)
        result.setdefault("includePrintInfo", any(word in user_message for word in ["打印", "标签"]))
        result.setdefault("flowLimit", 50)
        return result

    def _printed_not_inbound_arguments(
        self, arguments: dict[str, Any], state: WarehouseAgentState, user_message: str
    ) -> dict[str, Any]:
        result = dict(arguments)
        result["productScope"] = self._selected_product_scope_or_all(result, state)
        if not result.get("batchNo"):
            match = re.search(r"(?:批次|批号)\s*[:：]?\s*([A-Za-z0-9_-]{3,80})", user_message)
            if match:
                result["batchNo"] = match.group(1)
        if not result.get("orderNo"):
            match = re.search(r"(?:订单号|生产订单)\s*[:：]?\s*([A-Za-z0-9_-]{3,50})", user_message)
            if match:
                result["orderNo"] = match.group(1)
        result.setdefault("dateRange", self._assay_date_range_from_message(user_message))
        if "order" in user_message or "订单" in user_message:
            result.setdefault("groupBy", "order")
        elif "产品" in user_message or "品种" in user_message:
            result.setdefault("groupBy", "product")
        else:
            result.setdefault("groupBy", "batch")
        result.setdefault("limit", 50)
        return result

    def _pallet_anomalies_arguments(
        self, arguments: dict[str, Any], state: WarehouseAgentState, user_message: str
    ) -> dict[str, Any]:
        result = dict(arguments)
        result["productScope"] = self._selected_product_scope_or_all(result, state)
        if state.selected_warehouse is not None and result.get("warehouseId") is None:
            result["warehouseId"] = state.selected_warehouse.internal_id
        result.setdefault("dateRange", self._assay_date_range_from_message(user_message))
        result.setdefault("anomalyTypes", self._pallet_anomaly_types_from_message(user_message))
        result.setdefault("limit", 50)
        return result

    def _pallet_flow_records_arguments(
        self, arguments: dict[str, Any], state: WarehouseAgentState, user_message: str
    ) -> dict[str, Any]:
        result = dict(arguments)
        if not result.get("code"):
            match = re.search(r"(?:托盘码|托盘|二维码)\s*[:：]?\s*([A-Za-z0-9_-]{6,64})", user_message)
            if match:
                result["code"] = match.group(1)
        result["productScope"] = self._selected_product_scope_or_all(result, state)
        if state.selected_warehouse is not None and result.get("warehouseId") is None:
            result["warehouseId"] = state.selected_warehouse.internal_id
        result.setdefault("dateRange", self._assay_date_range_from_message(user_message))
        result.setdefault("eventTypes", self._pallet_flow_event_types_from_message(user_message))
        result.setdefault("page", 1)
        result.setdefault("size", 20)
        return result

    def _qr_batch_inbound_arguments(self, arguments: dict[str, Any], user_message: str) -> dict[str, Any]:
        result = dict(arguments)
        if not result.get("batchNo"):
            match = re.search(r"(?:批次|批号)\s*[:：]?\s*([A-Za-z0-9_-]{3,80})", user_message)
            if match:
                result["batchNo"] = match.group(1)
        if not result.get("orderNo"):
            match = re.search(r"(?:订单号|生产订单)\s*[:：]?\s*([A-Za-z0-9_-]{3,50})", user_message)
            if match:
                result["orderNo"] = match.group(1)
        result.setdefault("dateRange", self._assay_date_range_from_message(user_message))
        result.setdefault("includeUnfinishedExamples", True)
        result.setdefault("limit", 20)
        return result

    def _selected_product_scope_or_all(self, arguments: dict[str, Any], state: WarehouseAgentState) -> dict[str, Any]:
        requested_scope = arguments.get("productScope")
        if isinstance(requested_scope, dict) and requested_scope.get("type") == "ALL":
            return {"type": "ALL"}
        if state.selected_product is not None:
            metadata = state.selected_product.metadata
            scope_type = str(metadata.get("scopeType") or "SINGLE_PRODUCT")
            if scope_type == "SINGLE_PRODUCT":
                if state.selected_product.internal_id is None:
                    raise ValueError("selected product has no validated productId")
                return {"type": scope_type, "productId": state.selected_product.internal_id}
            if scope_type == "EXACT_PRODUCT_NAME_GROUP":
                return {"type": scope_type, "productName": metadata.get("productName")}
            if scope_type == "PRODUCT_TYPE_GROUP":
                return {"type": scope_type, "productType": metadata.get("productType")}
        return {"type": "ALL"}

    def _validate_qr_code_lifecycle(self, arguments: dict[str, Any]) -> dict[str, Any]:
        code = self._required_text(arguments.get("code"), 100)
        flow_limit = int(arguments.get("flowLimit", 50))
        if not 1 <= flow_limit <= 100:
            raise ValueError("flowLimit must be 1..100")
        return {
            "code": code,
            "includeInventory": bool(arguments.get("includeInventory", True)),
            "includeAssay": bool(arguments.get("includeAssay", True)),
            "includeFlows": bool(arguments.get("includeFlows", True)),
            "includePrintInfo": bool(arguments.get("includePrintInfo", False)),
            "flowLimit": flow_limit,
        }

    def _validate_printed_not_inbound(self, arguments: dict[str, Any]) -> dict[str, Any]:
        result = {"productScope": self._validate_product_scope(arguments.get("productScope") or {"type": "ALL"})}
        for field, max_length in (("orderNo", 50), ("batchNo", 80)):
            value = arguments.get(field)
            if value:
                result[field] = self._required_text(value, max_length)
        date_range = self._validate_assay_date_range(arguments.get("dateRange"))
        if date_range:
            result["dateRange"] = date_range
        group_by = str(arguments.get("groupBy") or "batch")
        if group_by not in {"batch", "order", "product"}:
            raise ValueError("unsupported groupBy")
        limit = int(arguments.get("limit", 50))
        if not 1 <= limit <= 100:
            raise ValueError("limit must be 1..100")
        result.update({"groupBy": group_by, "limit": limit})
        return result

    def _validate_pallet_anomalies(self, arguments: dict[str, Any]) -> dict[str, Any]:
        result = {"productScope": self._validate_product_scope(arguments.get("productScope") or {"type": "ALL"})}
        if arguments.get("warehouseId") is not None:
            warehouse_id = int(arguments.get("warehouseId") or 0)
            if warehouse_id <= 0:
                raise ValueError("warehouseId must be positive")
            result["warehouseId"] = warehouse_id
        date_range = self._validate_assay_date_range(arguments.get("dateRange"))
        if date_range:
            result["dateRange"] = date_range
        anomaly_types = arguments.get("anomalyTypes") or self._pallet_anomaly_types_from_message("")
        allowed = {"VOID_CODE_SCANNED", "STATUS_INVENTORY_MISMATCH", "DUPLICATE_INBOUND", "OUTBOUND_WITHOUT_INBOUND", "PRODUCT_BINDING_MISMATCH"}
        if not isinstance(anomaly_types, list) or not 1 <= len(anomaly_types) <= 5 or any(item not in allowed for item in anomaly_types):
            raise ValueError("unsupported anomalyTypes")
        limit = int(arguments.get("limit", 50))
        if not 1 <= limit <= 100:
            raise ValueError("limit must be 1..100")
        result.update({"anomalyTypes": list(dict.fromkeys(anomaly_types)), "limit": limit})
        return result

    def _validate_pallet_flow_records(self, arguments: dict[str, Any]) -> dict[str, Any]:
        result: dict[str, Any] = {"productScope": self._validate_product_scope(arguments.get("productScope") or {"type": "ALL"})}
        if arguments.get("code"):
            result["code"] = self._required_text(arguments.get("code"), 100)
        if arguments.get("warehouseId") is not None:
            warehouse_id = int(arguments.get("warehouseId") or 0)
            if warehouse_id <= 0:
                raise ValueError("warehouseId must be positive")
            result["warehouseId"] = warehouse_id
        date_range = self._validate_assay_date_range(arguments.get("dateRange"))
        if date_range:
            result["dateRange"] = date_range
        event_types = arguments.get("eventTypes") or []
        allowed = {"INBOUND", "OUTBOUND", "TRANSFER", "BIND", "ASSAY", "CANCEL", "LABEL"}
        if not isinstance(event_types, list) or len(event_types) > 8 or any(item not in allowed for item in event_types):
            raise ValueError("unsupported eventTypes")
        page = int(arguments.get("page", 1))
        size = int(arguments.get("size", 20))
        if page < 1 or not 1 <= size <= 100:
            raise ValueError("page/size is invalid")
        result.update({"eventTypes": list(dict.fromkeys(event_types)), "page": page, "size": size})
        return result

    def _validate_qr_batch_inbound(self, arguments: dict[str, Any]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for field, max_length in (("batchNo", 80), ("orderNo", 50)):
            if arguments.get(field):
                result[field] = self._required_text(arguments.get(field), max_length)
        if arguments.get("productId") is not None:
            product_id = int(arguments.get("productId") or 0)
            if product_id <= 0:
                raise ValueError("productId must be positive")
            result["productId"] = product_id
        date_range = self._validate_assay_date_range(arguments.get("dateRange"))
        if date_range:
            result["dateRange"] = date_range
        if not any(result.get(field) for field in ("batchNo", "orderNo", "productId", "dateRange")):
            raise ValueError("batchNo, orderNo, productId, or dateRange is required")
        limit = int(arguments.get("limit", 20))
        if not 1 <= limit <= 100:
            raise ValueError("limit must be 1..100")
        result.update({"includeUnfinishedExamples": bool(arguments.get("includeUnfinishedExamples", True)), "limit": limit})
        return result

    def _validate_distribution(self, arguments: dict[str, Any]) -> dict[str, Any]:
        product_scope = arguments.get("productScope")
        if not isinstance(product_scope, dict):
            raise ValueError("productScope is required")
        scope_type = product_scope.get("type")
        safe_product_scope: dict[str, Any] = {"type": scope_type}
        if scope_type == "SINGLE_PRODUCT":
            product_id = int(product_scope.get("productId") or 0)
            if product_id <= 0:
                raise ValueError("productScope.productId must be positive")
            safe_product_scope["productId"] = product_id
        elif scope_type == "EXACT_PRODUCT_NAME_GROUP":
            safe_product_scope["productName"] = self._required_text(product_scope.get("productName"), 100)
        elif scope_type == "PRODUCT_TYPE_GROUP":
            safe_product_scope["productType"] = self._required_text(product_scope.get("productType"), 50)
        elif scope_type != "ALL":
            raise ValueError("unsupported productScope.type")

        warehouse_scope = arguments.get("warehouseScope")
        if not isinstance(warehouse_scope, dict):
            raise ValueError("warehouseScope is required")
        warehouse_type = warehouse_scope.get("type")
        safe_warehouse_scope: dict[str, Any] = {"type": warehouse_type}
        if warehouse_type == "SINGLE_WAREHOUSE":
            warehouse_id = int(warehouse_scope.get("warehouseId") or 0)
            if warehouse_id <= 0:
                raise ValueError("warehouseScope.warehouseId must be positive")
            safe_warehouse_scope["warehouseId"] = warehouse_id
        elif warehouse_type != "ALL":
            raise ValueError("unsupported warehouseScope.type")

        group_by = str(arguments.get("groupBy") or "")
        if group_by not in {"warehouse", "product", "warehouse_product"}:
            raise ValueError("unsupported groupBy")
        limit = int(arguments.get("limit", 20))
        if not 1 <= limit <= 100:
            raise ValueError("limit must be 1..100")
        safe_filter = self._validate_distribution_filter(arguments.get("statusFilter"))
        result = {
            "productScope": safe_product_scope,
            "warehouseScope": safe_warehouse_scope,
            "groupBy": group_by,
            "limit": limit,
        }
        if safe_filter:
            result["statusFilter"] = safe_filter
        return result

    def _validate_distribution_filter(self, value: Any) -> dict[str, Any]:
        if value is None:
            return {}
        if not isinstance(value, dict):
            raise ValueError("statusFilter must be an object")
        result: dict[str, Any] = {}
        for key, allowed in {
            "productStatuses": {"半成品", "成品"},
            "warehouseStatuses": {"正常", "空置", "满仓", "维护", "临期预警"},
            "palletStatuses": {"FREE", "PENDING", "INSTOCK", "INVALID", "ORDER_RESERVED"},
        }.items():
            values = value.get(key, [])
            if (
                not isinstance(values, list)
                or len(values) > 10
                or any(not isinstance(item, str) or item not in allowed for item in values)
            ):
                raise ValueError(f"unsupported {key}")
            if values:
                result[key] = values
        assay_status = value.get("assayStatus")
        if assay_status is not None:
            if assay_status not in {"HAS_ASSAY", "MISSING_ASSAY", "PASS", "FAIL", "NO_STANDARD", "MULTIPLE_CANDIDATES"}:
                raise ValueError("unsupported assayStatus")
            result["assayStatus"] = assay_status
        date_from = self._optional_date(value.get("entryDateFrom"))
        date_to = self._optional_date(value.get("entryDateTo"))
        if date_from and date_to and date_from > date_to:
            raise ValueError("entryDateFrom must not be after entryDateTo")
        if date_from:
            result["entryDateFrom"] = date_from
        if date_to:
            result["entryDateTo"] = date_to
        return result

    def _validate_assay_records(self, arguments: dict[str, Any]) -> dict[str, Any]:
        result: dict[str, Any] = {"productScope": self._validate_product_scope(arguments.get("productScope"))}
        date_range = self._validate_assay_date_range(arguments.get("dateRange"))
        if date_range:
            result["dateRange"] = date_range
        judge_status = str(arguments.get("judgeStatus") or "ANY")
        if judge_status not in {"ANY", "PASS", "FAILED", "NO_STANDARD", "MULTIPLE_CANDIDATES"}:
            raise ValueError("unsupported judgeStatus")
        sort_by = str(arguments.get("sortBy") or "sampleDate")
        if sort_by not in {"sampleDate", "createdAt"}:
            raise ValueError("unsupported sortBy")
        sort_direction = str(arguments.get("sortDirection") or "DESC").upper()
        if sort_direction not in {"ASC", "DESC"}:
            raise ValueError("unsupported sortDirection")
        page = int(arguments.get("page", 1))
        size = int(arguments.get("size", 20))
        if page < 1:
            raise ValueError("page must be >= 1")
        if not 1 <= size <= 100:
            raise ValueError("size must be 1..100")
        result.update({
            "judgeStatus": judge_status,
            "sortBy": sort_by,
            "sortDirection": sort_direction,
            "page": page,
            "size": size,
        })
        return result

    def _validate_assay_report_detail(self, arguments: dict[str, Any]) -> dict[str, Any]:
        report_ref = str(arguments.get("reportRef") or "").strip()
        if not 1 <= len(report_ref) <= 200 or not report_ref.startswith("assay_report_"):
            raise ValueError("reportRef must be a controlled assay_report ref")
        return {
            "reportRef": report_ref,
            "includeMetrics": bool(arguments.get("includeMetrics", True)),
            "includeStandardSnapshot": bool(arguments.get("includeStandardSnapshot", True)),
        }

    def _validate_assay_abnormalities(self, arguments: dict[str, Any]) -> dict[str, Any]:
        result: dict[str, Any] = {"productScope": self._validate_product_scope(arguments.get("productScope"))}
        date_range = self._validate_assay_date_range(arguments.get("dateRange"))
        if date_range:
            result["dateRange"] = date_range
        abnormal_types = arguments.get("abnormalTypes") or ["FAILED", "NO_STANDARD", "MULTIPLE_CANDIDATES"]
        if (
            not isinstance(abnormal_types, list)
            or not 1 <= len(abnormal_types) <= 3
            or any(item not in {"FAILED", "NO_STANDARD", "MULTIPLE_CANDIDATES"} for item in abnormal_types)
        ):
            raise ValueError("unsupported abnormalTypes")
        group_by = str(arguments.get("groupBy") or "product")
        if group_by not in {"product", "date", "abnormal_type", "metric"}:
            raise ValueError("unsupported groupBy")
        limit = int(arguments.get("limit", 50))
        if not 1 <= limit <= 100:
            raise ValueError("limit must be 1..100")
        result.update({"abnormalTypes": list(dict.fromkeys(abnormal_types)), "groupBy": group_by, "limit": limit})
        return result

    def _validate_products_without_recent_assay(self, arguments: dict[str, Any]) -> dict[str, Any]:
        result: dict[str, Any] = {
            "productScope": self._validate_product_scope(arguments.get("productScope")),
            "warehouseScope": self._validate_warehouse_scope(arguments.get("warehouseScope")),
        }
        population = str(arguments.get("population") or "CURRENT_INVENTORY")
        if population != "CURRENT_INVENTORY":
            raise ValueError("unsupported population")
        result["population"] = population
        date_range = self._validate_assay_date_range(arguments.get("dateRange"))
        if date_range:
            result["dateRange"] = date_range
        group_by = str(arguments.get("groupBy") or "product")
        if group_by not in {"product", "warehouse", "product_warehouse"}:
            raise ValueError("unsupported groupBy")
        limit = int(arguments.get("limit", 50))
        if not 1 <= limit <= 100:
            raise ValueError("limit must be 1..100")
        result.update({"groupBy": group_by, "limit": limit})
        return result

    def _validate_assay_standard_coverage(self, arguments: dict[str, Any]) -> dict[str, Any]:
        result: dict[str, Any] = {"productScope": self._validate_product_scope(arguments.get("productScope"))}
        date_range = self._validate_assay_date_range(arguments.get("dateRange"))
        if date_range:
            result["dateRange"] = date_range
        coverage_type = str(arguments.get("coverageType") or "PRODUCT_WITHOUT_STANDARD")
        if coverage_type not in {"PRODUCT_WITHOUT_STANDARD", "ASSAY_WITHOUT_STANDARD", "UNUSED_STANDARD"}:
            raise ValueError("unsupported coverageType")
        result["coverageType"] = coverage_type
        limit = int(arguments.get("limit", 50))
        if not 1 <= limit <= 100:
            raise ValueError("limit must be 1..100")
        result["limit"] = limit
        return result

    def _validate_warehouse_scope(self, value: Any) -> dict[str, Any]:
        if not isinstance(value, dict):
            raise ValueError("warehouseScope is required")
        scope_type = value.get("type")
        result: dict[str, Any] = {"type": scope_type}
        if scope_type == "SINGLE_WAREHOUSE":
            warehouse_id = int(value.get("warehouseId") or 0)
            if warehouse_id <= 0:
                raise ValueError("warehouseScope.warehouseId must be positive")
            result["warehouseId"] = warehouse_id
        elif scope_type != "ALL":
            raise ValueError("unsupported warehouseScope.type")
        return result

    def _validate_product_scope(self, value: Any) -> dict[str, Any]:
        if not isinstance(value, dict):
            raise ValueError("productScope is required")
        scope_type = value.get("type")
        result: dict[str, Any] = {"type": scope_type}
        if scope_type == "SINGLE_PRODUCT":
            product_id = int(value.get("productId") or 0)
            if product_id <= 0:
                raise ValueError("productScope.productId must be positive")
            result["productId"] = product_id
        elif scope_type == "EXACT_PRODUCT_NAME_GROUP":
            result["productName"] = self._required_text(value.get("productName"), 100)
        elif scope_type == "PRODUCT_TYPE_GROUP":
            result["productType"] = self._required_text(value.get("productType"), 50)
        elif scope_type != "ALL":
            raise ValueError("unsupported productScope.type")
        return result

    def _validate_assay_date_range(self, value: Any) -> dict[str, Any] | None:
        if value is None:
            return None
        if not isinstance(value, dict):
            raise ValueError("dateRange must be an object")
        range_type = str(value.get("type") or "")
        if range_type == "EXACT":
            exact = self._optional_date(value.get("date"))
            if not exact:
                raise ValueError("dateRange.date is required")
            return {"type": "EXACT", "date": exact}
        if range_type == "LAST_DAYS":
            days = int(value.get("days") or 0)
            if not 1 <= days <= 366:
                raise ValueError("dateRange.days must be 1..366")
            return {"type": "LAST_DAYS", "days": days}
        if range_type == "RANGE":
            date_from = self._optional_date(value.get("from"))
            date_to = self._optional_date(value.get("to"))
            if not date_from or not date_to:
                raise ValueError("dateRange.from and dateRange.to are required")
            if date.fromisoformat(date_from) > date.fromisoformat(date_to):
                raise ValueError("dateRange.from must not be after dateRange.to")
            return {"type": "RANGE", "from": date_from, "to": date_to}
        raise ValueError("unsupported dateRange.type")

    def _required_text(self, value: Any, max_length: int) -> str:
        text = str(value or "").strip()
        if not 1 <= len(text) <= max_length:
            raise ValueError("scope text is invalid")
        return text

    def _production_order_query(self, user_message: str) -> str | None:
        text = user_message or ""
        match = re.search(r"(?:生产订单|订单号)\s*[:：]?\s*([A-Za-z0-9_-]{3,100})", text)
        if match:
            return match.group(1)
        tokens = re.findall(r"[A-Za-z0-9]+(?:[-_][A-Za-z0-9]+)+", text)
        return tokens[0][:100] if tokens else None

    def _product_detail_query(self, user_message: str) -> str | None:
        text = (user_message or "").strip()
        match = re.search(r"(?:查询|查看|查)?\s*(.+?)\s*(?:的)?产品详情", text)
        if not match:
            return None
        product_name = match.group(1).strip(" ，。？！?；;、")
        return product_name[:100] if product_name else None

    def _boiling_batch_query(self, user_message: str) -> str | None:
        text = user_message or ""
        match = re.search(r"(?:煮糖批次|批次号)\s*[:：]?\s*([A-Za-z0-9_-]{3,100})", text)
        if match:
            return match.group(1)
        tokens = re.findall(r"[A-Za-z0-9]+(?:[-_][A-Za-z0-9]+)+", text)
        return tokens[0][:100] if tokens else None

    def _latest_assay_report_ref(self, state: WarehouseAgentState) -> str | None:
        records_result = state.last_assay_records or {}
        records = records_result.get("records")
        if isinstance(records, list):
            for record in records:
                if isinstance(record, dict):
                    report_ref = str(record.get("recordRef") or "").strip()
                    if report_ref.startswith("assay_report_"):
                        return report_ref
        return None

    def _optional_date(self, value: Any) -> str | None:
        if value is None or value == "":
            return None
        text = str(value)
        try:
            parsed = date.fromisoformat(text)
        except ValueError as exc:
            raise ValueError("date filter must use YYYY-MM-DD")
        return parsed.isoformat()

    def _explicit_all_product_request(self, user_message: str) -> bool:
        return any(phrase in user_message for phrase in ["全部产品", "所有产品", "全产品", "全部品种", "所有品种"])

    def _assay_date_range_from_message(self, user_message: str) -> dict[str, Any] | None:
        resolved = self.business_clock.resolve(user_message)
        return resolved.to_tool_date_range() if resolved is not None else None

    def _prefers_single_assay_report(self, user_message: str) -> bool:
        text = user_message or ""
        if any(marker in text for marker in ("全部版本", "所有版本", "历史版本", "版本列表")):
            return False
        resolved = self.business_clock.resolve(text)
        if resolved is not None and resolved.is_exact:
            return True
        return bool(
            re.search(
                r"\b20\d{2}[-/.](?:0?[1-9]|1[0-2])[-/.](?:0?[1-9]|[12]\d|3[01])\b",
                text,
            )
            or re.search(
                r"20\d{2}年(?:0?[1-9]|1[0-2])月(?:0?[1-9]|[12]\d|3[01])日",
                text,
            )
        )

    def _assay_judge_status_from_message(self, user_message: str) -> str:
        text = user_message or ""
        if "无标准" in text:
            return "NO_STANDARD"
        if any(word in text for word in ["不合格", "未通过", "检测失败"]):
            return "FAILED"
        if "标准多候选" in text or "多个标准" in text:
            return "MULTIPLE_CANDIDATES"
        if "合格" in text:
            return "PASS"
        return "ANY"

    def _assay_abnormal_types_from_message(self, user_message: str) -> list[str]:
        text = user_message or ""
        if "无标准" in text:
            return ["NO_STANDARD"]
        if "标准多候选" in text or "多个标准" in text:
            return ["MULTIPLE_CANDIDATES"]
        if any(word in text for word in ["不合格", "未通过", "检测失败", "越界"]):
            return ["FAILED"]
        return ["FAILED", "NO_STANDARD", "MULTIPLE_CANDIDATES"]

    def _assay_abnormal_group_by_from_message(self, user_message: str) -> str:
        text = user_message or ""
        if "指标" in text or "越界" in text:
            return "metric"
        if "按天" in text or "每天" in text or "日期" in text:
            return "date"
        if "类型" in text or "分类" in text:
            return "abnormal_type"
        return "product"

    def _assay_abnormalities_all_scope_allowed(self, user_message: str) -> bool:
        text = user_message or ""
        return self._explicit_all_product_request(text) or (
            any(word in text for word in ["化验", "质检", "质量"])
            and any(word in text for word in ["异常", "不合格", "无标准", "没有标准", "未匹配标准", "越界", "哪些产品", "哪些化验记录", "统计"])
        )

    def _assay_records_all_scope_allowed(self, user_message: str) -> bool:
        text = user_message or ""
        return self._explicit_all_product_request(text) or (
            "化验" in text and any(word in text for word in ["今天", "昨天", "哪些", "所有", "全部"])
        )

    def _products_without_recent_assay_all_scope_allowed(self, user_message: str) -> bool:
        text = user_message or ""
        return self._explicit_all_product_request(text) or any(
            word in text for word in ["哪些产品", "在库产品", "库存", "库位", "全部", "所有"]
        )

    def _assay_standard_coverage_all_scope_allowed(self, user_message: str) -> bool:
        text = user_message or ""
        return self._explicit_all_product_request(text) or any(
            word in text for word in ["哪些产品", "在库产品", "当前在库", "全部", "所有", "未绑定标准", "没有质量标准"]
        )

    def _pallet_anomaly_types_from_message(self, user_message: str) -> list[str]:
        text = user_message or ""
        types: list[str] = []
        if "作废码" in text or "作废" in text and "扫描" in text:
            types.append("VOID_CODE_SCANNED")
        if "状态与库存" in text or "不一致" in text:
            types.append("STATUS_INVENTORY_MISMATCH")
        if "重复入库" in text:
            types.append("DUPLICATE_INBOUND")
        if "无入库" in text or "未入库出库" in text:
            types.append("OUTBOUND_WITHOUT_INBOUND")
        if "绑定产品" in text or "产品不一致" in text:
            types.append("PRODUCT_BINDING_MISMATCH")
        return types or ["STATUS_INVENTORY_MISMATCH", "DUPLICATE_INBOUND", "OUTBOUND_WITHOUT_INBOUND", "PRODUCT_BINDING_MISMATCH"]

    def _pallet_flow_event_types_from_message(self, user_message: str) -> list[str]:
        text = user_message or ""
        types: list[str] = []
        if "入库" in text:
            types.append("INBOUND")
        if "出库" in text or "领用" in text:
            types.append("OUTBOUND")
        if "调拨" in text or "转移" in text:
            types.append("TRANSFER")
        if "绑定" in text:
            types.append("BIND")
        if "化验" in text:
            types.append("ASSAY")
        if "取消" in text:
            types.append("CANCEL")
        if "标签" in text or "打印" in text:
            types.append("LABEL")
        return list(dict.fromkeys(types))

    def _products_without_recent_assay_group_by_from_message(self, user_message: str) -> str:
        text = user_message or ""
        if "库位和产品" in text or "产品和库位" in text:
            return "product_warehouse"
        if "按库位" in text or "库位分类" in text or "库位统计" in text:
            return "warehouse"
        return "product"
