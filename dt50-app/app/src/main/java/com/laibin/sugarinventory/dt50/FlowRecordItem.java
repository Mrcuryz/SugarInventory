package com.laibin.sugarinventory.dt50;

final class FlowRecordItem {
    final String time;
    final String eventLabel;
    final String codeLabel;
    final String productLabel;
    final String fromWarehouseLabel;
    final String toWarehouseLabel;
    final String operatorLabel;

    FlowRecordItem(String time, String eventLabel, String codeLabel, String productLabel,
                   String fromWarehouseLabel, String toWarehouseLabel, String operatorLabel) {
        this.time = time;
        this.eventLabel = eventLabel;
        this.codeLabel = codeLabel;
        this.productLabel = productLabel;
        this.fromWarehouseLabel = fromWarehouseLabel;
        this.toWarehouseLabel = toWarehouseLabel;
        this.operatorLabel = operatorLabel;
    }
}
