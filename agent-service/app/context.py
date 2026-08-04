from __future__ import annotations

from dataclasses import dataclass, field

from app.graph.state import WarehouseAgentState


@dataclass(frozen=True)
class DomainContextPack:
    name: str
    triggerReason: str
    instructions: list[str] = field(default_factory=list)


class ContextBuilder:
    """Builds small domain context packs for the model layer."""

    WAREHOUSE_KEYWORDS = ("库位", "仓库", "容量", "存放", "位置")
    PRODUCT_KEYWORDS = ("产品", "库存", "冰糖", "白砂糖", "糖")
    PALLET_KEYWORDS = ("托盘", "托盘码", "二维码")
    ASSAY_KEYWORDS = ("化验", "质检", "合格", "不合格")
    TASK_KEYWORDS = ("任务", "待处理", "已确认", "已取消", "调拨")
    PRODUCTION_KEYWORDS = ("生产订单", "煮糖", "领料", "原料消耗", "产出", "入库去向")

    def build(self, message: str, state: WarehouseAgentState) -> list[DomainContextPack]:
        packs: list[DomainContextPack] = []
        packs.append(self._safety_pack())
        if any(keyword in message for keyword in self.PRODUCT_KEYWORDS):
            packs.append(self._product_pack())
        if any(keyword in message for keyword in self.WAREHOUSE_KEYWORDS):
            packs.append(self._warehouse_pack())
        if any(keyword in message for keyword in self.PALLET_KEYWORDS):
            packs.append(self._pallet_pack())
        if any(keyword in message for keyword in self.ASSAY_KEYWORDS):
            packs.append(self._assay_pack())
        if any(keyword in message for keyword in self.TASK_KEYWORDS):
            packs.append(self._task_pack())
        if any(keyword in message for keyword in self.PRODUCTION_KEYWORDS):
            packs.append(self._production_pack())
        if state.selected_product is not None:
            packs.append(
                DomainContextPack(
                    name="selected_product",
                    triggerReason="current conversation has selected product",
                    instructions=[
                        f"最近已确认产品：{state.selected_product.display_label}。",
                        "涉及“这些、它、刚才那个产品”时，优先沿用已确认产品，不要重新解析产品。",
                    ],
                )
            )
        if state.selected_warehouse is not None:
            packs.append(
                DomainContextPack(
                    name="selected_warehouse",
                    triggerReason="current conversation has selected warehouse",
                    instructions=[
                        f"最近已确认库位：{state.selected_warehouse.display_label}。",
                        "涉及“这个库位、刚才那个库位”时，优先沿用已确认库位，不要猜 warehouseId。",
                    ],
                )
            )
        if state.selected_production_order is not None:
            packs.append(
                DomainContextPack(
                    name="selected_production_order",
                    triggerReason="current conversation has selected production order",
                    instructions=[
                        f"最近已确认生产订单：{state.selected_production_order.display_label}。",
                        "涉及“这个订单、该订单、刚才的订单”时，必须沿用受控 orderRef，不得重新猜测订单。",
                    ],
                )
            )
        if state.selected_boiling_batch is not None:
            packs.append(
                DomainContextPack(
                    name="selected_boiling_batch",
                    triggerReason="current conversation has selected boiling batch",
                    instructions=[
                        f"最近已确认煮糖批次：{state.selected_boiling_batch.display_label}。",
                        "涉及‘这个批次、该批次、刚才的煮糖批次’时，必须沿用受控 batchRef，不得猜测批次。",
                    ],
                )
            )
        if state.selected_pallet is not None:
            packs.append(
                DomainContextPack(
                    name="selected_pallet",
                    triggerReason="current conversation has selected pallet",
                    instructions=[
                        f"最近已确认托盘：{state.selected_pallet.display_label}。",
                        "涉及‘这个托盘、它、刚才的托盘’时，只能沿用 CURRENT_PALLET，不得猜测托盘码。",
                    ],
                )
            )
        return packs

    def _safety_pack(self) -> DomainContextPack:
        return DomainContextPack(
            name="mcp_safety_boundary",
            triggerReason="always include read-only MCP safety rules",
            instructions=[
                "只能选择白名单内的 L1 只读工具。",
                "不得生成 SQL、任意 HTTP 请求、写库存、入库、出库、调拨或配置修改。",
                "解析结果为 AMBIGUOUS 时必须追问，不得猜 productId 或 warehouseId。",
            ],
        )

    def _product_pack(self) -> DomainContextPack:
        return DomainContextPack(
            name="product_inventory",
            triggerReason="message contains product or inventory semantics",
            instructions=[
                "查询产品库存前必须先用 resolve_products 解析产品名称。",
                "如果已有 selected_product 且用户使用“它、这些、刚才那个产品”等指代，可以沿用 structured state 中的已确认产品。",
                "get_inventory_overview 只能使用已确认 productId，不得从自然语言猜 ID。",
            ],
        )

    def _warehouse_pack(self) -> DomainContextPack:
        return DomainContextPack(
            name="warehouse_naming",
            triggerReason="message contains warehouse/location/capacity semantics",
            instructions=[
                "库位命名规则：数据库中的 warehouseName 可能存为纯数字，例如 “2”。",
                "用户可能说 “2号库位”、“2号库”、“库位2”、“二号库位”。",
                "查询库位前必须先调用 resolve_warehouses。",
                "resolve_warehouses 的 query 应传入用户提到的库位名称片段，不要传整句。",
                "不要猜 warehouseId。",
            ],
        )

    def _pallet_pack(self) -> DomainContextPack:
        return DomainContextPack(
            name="pallet_status",
            triggerReason="message contains pallet semantics",
            instructions=[
                "托盘查询使用 get_pallet_status。",
                "只有用户提供明确托盘码时才能查询；缺少托盘码时应追问。",
                "不得作废、恢复、创建或确认托盘任务。",
            ],
        )

    def _assay_pack(self) -> DomainContextPack:
        return DomainContextPack(
            name="assay_status",
            triggerReason="message contains assay or quality semantics",
            instructions=[
                "单产品/单日期化验查询使用 get_assay_status。",
                "如果用户说“它今天有没有化验”，并且 structured state 有 selected_product，可以使用该产品和今天日期。",
                "批量趋势分析和导出尚未开放安全工具，应说明能力缺口。",
            ],
        )

    def _task_pack(self) -> DomainContextPack:
        return DomainContextPack(
            name="pallet_tasks",
            triggerReason="message contains pallet task semantics",
            instructions=[
                "任务查询使用 query_pallet_tasks，只允许读取当前任务记录。",
                "成品入库任务处理预览使用 preview_task_transition，只接受用户已经明确选择的托盘码；预览不执行任何任务。",
                "待处理、已确认、已取消必须映射为受控状态过滤，不能把查询解释为执行任务。",
                "多个任务应先给摘要，再通过可展开卡片展示安全详情。",
            ],
        )

    def _production_pack(self) -> DomainContextPack:
        return DomainContextPack(
            name="production_trace",
            triggerReason="message contains production order or boiling batch semantics",
            instructions=[
                "生产订单和煮糖批次必须先通过 resolve_production_entities 解析受控引用。",
                "生产追踪按煮糖批次、关联订单、实际领料、产出与已确认入库去向逐步展开。",
                "没有登记的原料、产出或去向不得推断；计划数据不能当作实际数据。",
            ],
        )
