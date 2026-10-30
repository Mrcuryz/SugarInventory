package com.laibin.sugarinventory.dt50;

final class AssayRecordItem {
    final String recordRef;
    final String productLabel;
    final String sampleDate;
    final String judgeLabel;
    final String failedMetricText;
    final int failedMetricCount;
    final String standardLabel;
    final String testerLabel;

    AssayRecordItem(String recordRef, String productLabel, String sampleDate, String judgeLabel,
                    String failedMetricText, int failedMetricCount,
                    String standardLabel, String testerLabel) {
        this.recordRef = recordRef;
        this.productLabel = productLabel;
        this.sampleDate = sampleDate;
        this.judgeLabel = judgeLabel;
        this.failedMetricText = failedMetricText;
        this.failedMetricCount = failedMetricCount;
        this.standardLabel = standardLabel;
        this.testerLabel = testerLabel;
    }
}
