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

    def build(self, message: str, state: WarehouseAgentState) -> list[DomainContextPack]:
        packs: list[DomainContextPack] = []
        if any(keyword in message for keyword in self.WAREHOUSE_KEYWORDS):
            packs.append(self._warehouse_pack())
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
        return packs

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
