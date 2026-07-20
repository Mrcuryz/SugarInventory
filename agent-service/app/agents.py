from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from app.context import DomainContextPack
from app.knowledge import IntentRoute


MAIN_AGENT = "main_agent"


class ExpertBoundaryError(ValueError):
    """Raised when a planner or caller crosses an expert tool boundary."""


@dataclass(frozen=True)
class ExpertAgentProfile:
    name: str
    business_name: str
    domains: frozenset[str]
    allowed_tools: frozenset[str]
    instructions: tuple[str, ...]
    model_policy_key: str
    model_parameters: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class AgentHandoff:
    source_agent: str
    target_agent: str
    business_domain: str | None
    mode: str
    allowed_tools: tuple[str, ...]
    model_policy_key: str
    model_parameters: dict[str, Any]

    def to_snapshot(self) -> dict[str, Any]:
        return {
            "source_agent": self.source_agent,
            "target_agent": self.target_agent,
            "business_domain": self.business_domain,
            "mode": self.mode,
            "allowed_tools": list(self.allowed_tools),
            "model_policy_key": self.model_policy_key,
            "model_parameters": dict(self.model_parameters),
        }


def _profiles() -> dict[str, ExpertAgentProfile]:
    profiles = [
        ExpertAgentProfile(
            name=MAIN_AGENT,
            business_name="主 Agent",
            domains=frozenset({"assistant_experience", "security"}),
            allowed_tools=frozenset(),
            instructions=(
                "负责意图识别、上下文、HITL、安全边界和最终自然语言回答。",
                "主 Agent 不直接持有仓储业务工具。",
            ),
            model_policy_key="main",
            model_parameters={"temperature": 0.2},
        ),
        ExpertAgentProfile(
            name="inventory_expert",
            business_name="库存专家 Agent",
            domains=frozenset({"inventory", "product"}),
            allowed_tools=frozenset(
                {
                    "resolve_products",
                    "resolve_warehouses",
                    "get_inventory_overview",
                    "get_inventory_distribution",
                    "query_inventory_ledger",
                    "query_prepare_pool_balance",
                }
            ),
            instructions=(
                "只处理产品解析、库存概览和受控库存分布。",
                "产品或库位未唯一确认时先调用对应 resolver，不得猜内部 ID。",
                "不得执行入库、出库、调拨、盘点或任何库存写入。",
            ),
            model_policy_key="inventory",
            model_parameters={"temperature": 0.1},
        ),
        ExpertAgentProfile(
            name="warehouse_expert",
            business_name="库位专家 Agent",
            domains=frozenset({"warehouse"}),
            allowed_tools=frozenset({"resolve_warehouses", "get_warehouse_status", "query_warehouse_capacity_distribution", "query_warehouse_recent_operations", "query_warehouse_mixed_storage_facts"}),
            instructions=(
                "只处理库位名称解析、容量和当前状态。",
                "不得把自然语言中的数字直接当作 warehouseId。",
                "不得维护库位、修改容量或执行库存移动。",
            ),
            model_policy_key="warehouse",
            model_parameters={"temperature": 0.1},
        ),
        ExpertAgentProfile(
            name="assay_expert",
            business_name="化验质量专家 Agent",
            domains=frozenset({"assay", "quality"}),
            allowed_tools=frozenset(
                {
                    "resolve_products",
                    "resolve_warehouses",
                    "get_assay_status",
                    "query_assay_records",
                    "get_assay_report_detail",
                    "query_assay_abnormalities",
                    "query_products_without_recent_assay",
                    "query_assay_standard_coverage",
                    "query_assay_groups",
                    "query_quality_standard_catalog",
                    "get_quality_standard_detail",
                    "query_product_standard_relations",
                }
            ),
            instructions=(
                "区分无化验、无标准、标准多候选和明确不合格，不得混用口径。",
                "ASSAY_WITHOUT_STANDARD 和 UNUSED_STANDARD 仍待确认，必须安全拒绝。",
                "不得导入、更新、删除化验或修改质量标准。",
            ),
            model_policy_key="assay",
            model_parameters={"temperature": 0.0},
        ),
        ExpertAgentProfile(
            name="logistics_expert",
            business_name="任务单据物流专家 Agent",
            domains=frozenset({"logistics", "task", "document", "auto_inbound"}),
            allowed_tools=frozenset({
                "query_pallet_tasks", "query_stock_documents",
                "query_auto_inbound_batches", "get_auto_inbound_batch_detail",
            }),
            instructions=(
                "只处理托盘任务、出入库单据和自动报数批次的只读查询。",
                "任务查询不得解释为任务已执行，也不得确认、取消、入库、出库或调拨。",
                "不得暴露内部任务、产品、库位、化验或托盘数据库 ID。",
                "用户说‘只看、筛选、换成’时应沿用上一轮任务查询中未被明确替换的过滤条件。",
                "上一轮任务卡片已包含安全详情时，可基于受控会话上下文回答‘第一条详情’，不得猜测缺失字段。",
            ),
            model_policy_key="logistics",
            model_parameters={"temperature": 0.0},
        ),
        ExpertAgentProfile(
            name="master_data_expert",
            business_name="系统基础资料专家 Agent",
            domains=frozenset({"master_data", "product_catalog", "screen_mesh"}),
            allowed_tools=frozenset({"resolve_products", "query_product_catalog", "get_product_detail", "query_screen_mesh_catalog"}),
            instructions=(
                "只查询产品与筛网当前主数据配置，不回答库存、质量合格或生产可用性。",
                "不得暴露产品、筛网、创建人或更新人的内部数据库 ID。",
                "不得创建、更新或删除产品与筛网配置。",
            ),
            model_policy_key="master_data",
            model_parameters={"temperature": 0.0},
        ),
        ExpertAgentProfile(
            name="administration_expert",
            business_name="员工与权限管理专家 Agent",
            domains=frozenset({"administration", "employee", "rbac"}),
            allowed_tools=frozenset({"query_employee_roster", "query_roles", "get_role_permission_summary"}),
            instructions=(
                "只查询员工名册、角色目录和角色权限摘要，员工手机号只能展示掩码。",
                "不得暴露内部数据库 ID、登录凭据、微信绑定信息、鉴权令牌或 Agent 内部配置。",
                "不得新增、修改、停用员工或角色，不得分配权限。",
            ),
            model_policy_key="administration",
            model_parameters={"temperature": 0.0},
        ),
        ExpertAgentProfile(
            name="pallet_expert",
            business_name="二维码托盘专家 Agent",
            domains=frozenset({"pallet", "qr_code"}),
            allowed_tools=frozenset(
                {
                    "resolve_products",
                    "resolve_warehouses",
                    "get_pallet_status",
                    "query_qr_code_lifecycle",
                    "query_printed_not_inbound_codes",
                    "query_pallet_anomalies",
                    "query_pallet_flow_records",
                    "query_qr_batch_inbound_completion",
                    "query_fixed_product_qr_pool",
                }
            ),
            instructions=(
                "只处理二维码、托盘当前状态、生命周期、流转、异常和入库完成率。",
                "打印以标签批次 printed_at 为第一版来源；入库按已确认的库存关联口径判断。",
                "没有独立扫描日志时不得断言作废码未被扫描。",
                "不得作废、恢复、确认托盘任务或执行出入库。",
            ),
            model_policy_key="pallet",
            model_parameters={"temperature": 0.0},
        ),
        ExpertAgentProfile(
            name="production_expert",
            business_name="生产专家 Agent",
            domains=frozenset({"production"}),
            allowed_tools=frozenset({
                "resolve_production_entities",
                "query_production_order_progress",
                "query_boiling_batch_trace",
                "query_material_pick_trace",
                "query_production_label_completion",
                "query_in_process_materials",
                "query_material_candidates",
            }),
            instructions=(
                "只处理生产订单或煮糖批次解析、订单进度、煮糖追溯、实际领料、领料候选、标签完成度和在制半成品查询。",
                "订单详情必须使用 resolver 返回的短期 orderRef，不得猜测或传递内部 ID。",
                "不得创建、修改、取消订单，不得领料、生成标签或确认生产完成。",
            ),
            model_policy_key="production",
            model_parameters={"temperature": 0.1},
        ),
        ExpertAgentProfile(
            name="audit_expert",
            business_name="审计专家 Agent",
            domains=frozenset({"audit", "operation_log"}),
            allowed_tools=frozenset({"search_operation_logs", "query_agent_tool_audit", "query_agent_answer_reviews"}),
            instructions=(
                "只查询经过字段级过滤的业务操作日志、Agent 工具调用审计和回答 Review 摘要。",
                "不得输出用户原问题/回答、oldData、字段值、参数、Prompt、模型上下文、内部 ID、密钥或原始堆栈。",
                "不得修改 Review 状态、权限或任何业务数据。",
            ),
            model_policy_key="audit",
            model_parameters={"temperature": 0.0},
        ),
    ]
    return {profile.name: profile for profile in profiles}


class AgentHandoffRouter:
    """Routes a main-agent intent snapshot to one least-privilege expert."""

    def __init__(self, profiles: dict[str, ExpertAgentProfile] | None = None) -> None:
        self._profiles = profiles or _profiles()

    @property
    def profiles(self) -> dict[str, ExpertAgentProfile]:
        return dict(self._profiles)

    def route(self, route: IntentRoute) -> AgentHandoff:
        target = MAIN_AGENT
        mode = "direct"
        if route.next_action == "call_tool":
            target = self._agent_for_route(route)
            mode = "delegate"
        profile = self.profile(target)
        if mode == "delegate":
            for tool_name in route.planned_tools:
                self.authorize_tool(profile.name, tool_name)
        return self.handoff_for_agent(
            profile.name,
            business_domain=route.business_domain,
            mode=mode,
        )

    def handoff_for_agent(
        self,
        agent_name: str,
        *,
        business_domain: str | None = None,
        mode: str = "delegate",
    ) -> AgentHandoff:
        profile = self.profile(agent_name)
        return AgentHandoff(
            source_agent=MAIN_AGENT,
            target_agent=profile.name,
            business_domain=business_domain,
            mode=mode,
            allowed_tools=tuple(sorted(profile.allowed_tools)),
            model_policy_key=profile.model_policy_key,
            model_parameters=dict(profile.model_parameters),
        )

    def profile(self, agent_name: str) -> ExpertAgentProfile:
        profile = self._profiles.get(agent_name)
        if profile is None:
            raise ExpertBoundaryError("unknown expert agent")
        return profile

    def profile_for_tool(self, tool_name: str, preferred_agent: str | None = None) -> ExpertAgentProfile:
        if preferred_agent:
            preferred = self._profiles.get(preferred_agent)
            if preferred is not None and tool_name in preferred.allowed_tools:
                return preferred
        matches = [profile for profile in self._profiles.values() if tool_name in profile.allowed_tools]
        if not matches:
            raise ExpertBoundaryError("tool is not owned by an expert agent")
        return matches[0]

    def handoff_for_tool(
        self,
        tool_name: str,
        *,
        business_domain: str | None = None,
    ) -> AgentHandoff:
        preferred_agent = None
        if business_domain:
            try:
                preferred_agent = self._agent_for_domain(business_domain)
            except ValueError:
                preferred_agent = None
        profile = self.profile_for_tool(tool_name, preferred_agent)
        return self.handoff_for_agent(profile.name, business_domain=business_domain)

    def authorize_tool(self, agent_name: str, tool_name: str) -> None:
        profile = self.profile(agent_name)
        if tool_name not in profile.allowed_tools:
            raise ExpertBoundaryError("tool is outside the expert agent boundary")

    def tool_schemas(self, handoff: AgentHandoff, schemas: dict[str, dict[str, Any]]) -> dict[str, dict[str, Any]]:
        return {name: schemas[name] for name in handoff.allowed_tools if name in schemas}

    def context_pack(self, handoff: AgentHandoff) -> DomainContextPack:
        profile = self.profile(handoff.target_agent)
        return DomainContextPack(
            name=f"expert_agent:{profile.name}",
            triggerReason=f"main agent delegated {handoff.business_domain or 'direct'} task",
            instructions=[
                f"当前角色：{profile.business_name}。",
                *profile.instructions,
                "只能从当前专家白名单中选择工具；完成后把结构化安全结果交回主 Agent。",
            ],
        )

    def _agent_for_domain(self, domain: str | None) -> str:
        if domain:
            for profile in self._profiles.values():
                if domain in profile.domains:
                    return profile.name
        raise ExpertBoundaryError("no expert agent is registered for this business domain")

    def _agent_for_route(self, route: IntentRoute) -> str:
        try:
            preferred = self._agent_for_domain(route.business_domain)
        except ValueError:
            preferred = self._agent_for_subtype(route.intent_subtype)
        planned = set(route.planned_tools)
        if not planned or planned.issubset(self.profile(preferred).allowed_tools):
            return preferred
        matches = [
            profile.name
            for profile in self._profiles.values()
            if profile.name != MAIN_AGENT and planned.issubset(profile.allowed_tools)
        ]
        if not matches:
            raise ExpertBoundaryError("no expert agent owns the planned tool chain")
        return matches[0]

    def _agent_for_subtype(self, subtype: str | None) -> str:
        value = subtype or ""
        if value.startswith("inventory") or value in {"readonly_analysis", "warehouse_inventory_contents"}:
            return "inventory_expert"
        if value.startswith("prepare_pool"):
            return "inventory_expert"
        if value.startswith("warehouse"):
            return "warehouse_expert"
        if value.startswith("assay") or value == "products_without_recent_assay":
            return "assay_expert"
        if value.startswith("pallet") or value.startswith("qr_") or value == "printed_not_inbound_codes":
            return "pallet_expert"
        if value.startswith("fixed_product_qr"):
            return "pallet_expert"
        if value.startswith("production"):
            return "production_expert"
        if value.startswith("product_catalog") or value.startswith("product_detail") or value.startswith("screen_mesh"):
            return "master_data_expert"
        if value.startswith("employee") or value.startswith("role"):
            return "administration_expert"
        if value.startswith("operation_log") or value.startswith("agent_tool_audit") or value.startswith("agent_answer_review"):
            return "audit_expert"
        raise ExpertBoundaryError("no expert agent is registered for this intent subtype")
