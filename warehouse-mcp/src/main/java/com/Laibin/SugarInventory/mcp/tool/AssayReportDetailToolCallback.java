package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayReportDetailRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public class AssayReportDetailToolCallback implements ToolCallback {
    private final WarehouseTools warehouseTools;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public AssayReportDetailToolCallback(WarehouseTools warehouseTools, ObjectMapper objectMapper, String inputSchema) {
        this.warehouseTools = warehouseTools;
        this.objectMapper = objectMapper;
        this.toolDefinition = DefaultToolDefinition.builder()
                .name("get_assay_report_detail")
                .description("Read one assay report detail by a controlled opaque reportRef returned by assay record tools.")
                .inputSchema(inputSchema)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        AssayReportDetailRequest request;
        try {
            request = objectMapper.readValue(toolInput, AssayReportDetailRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid get_assay_report_detail arguments.");
        }
        try {
            return objectMapper.writeValueAsString(warehouseTools.getAssayReportDetail(request));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize get_assay_report_detail result.");
        }
    }
}
