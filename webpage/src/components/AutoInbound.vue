<template>
  <div class="auto-inbound-page">
    <!-- 解析区 -->
    <el-card class="search-card" style="max-width: 1200px">
      <div class="card-title">智能报数处理</div>
      <el-dialog
          v-model="semiEditVisible"
          title="关联半成品"
          width="820px"
      >
        <el-form label-width="100px">
          <div
              v-for="(item, index) in semiEditRecords"
              :key="index"
              style="margin-bottom: 12px"
          >
            <el-row :gutter="16">
              <!-- 半成品名称 -->
              <el-col :span="11">
                <el-form-item label="半成品名称">
                  <el-select
                      v-model="item.semiProductId"
                      placeholder="选择半成品"
                      clearable
                      filterable
                      @change="onSemiProductChange(item)"
                  >
                    <el-option
                        v-for="semi in semiProductList"
                        :key="semi.productId"
                        :label="semi.productName"
                        :value="semi.productId"
                    />
                  </el-select>
                </el-form-item>
              </el-col>

              <!-- 库位 -->
              <el-col :span="10">
                <el-form-item label="库位">
                  <el-select
                      v-model="item.warehouseId"
                      placeholder="选择库位"
                      clearable
                      filterable
                      style="width:100%"
                  >
                    <el-option
                        v-for="w in warehouseList"
                        :key="w.warehouseId || w.id"
                        :label="w.warehouseName"
                        :value="w.warehouseId || w.id"
                    />
                  </el-select>
                </el-form-item>
              </el-col>

              <!-- 数量 + 单位（板/件） -->
            </el-row>

            <el-row :gutter="16">
              <!-- 生产日期 -->
              <el-col :span="11">
                <el-form-item label="生产日期">
                  <el-date-picker
                      v-model="item.productionDate"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="生产日期"
                      style="width:100%"
                  />
                </el-form-item>
              </el-col>

              <el-col :span="9">
                <el-form-item label="数量">
                  <div style="display:flex;gap:4px">
                    <el-input
                        v-model="item.quantity"
                        clearable
                        width:20px
                    />
                    <el-select
                        v-model="item.unit"
                        placeholder="单位"
                        style="width:110px"
                    >
                      <el-option label="板" value="0" />
                      <el-option label="件" value="1" />
                    </el-select>
                  </div>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <!-- 套用该半成品化验数据 -->
              <el-col :span="8">
                <el-form-item label=" ">
                  <el-switch
                      v-model="item.useAssay"
                      active-text="套用化验数据"
                      :active-value="true"
                      :inactive-value="false"
                      @change="handleSemiUseAssay(index)"
                  />
                </el-form-item>
              </el-col>

              <!-- 删除按钮 -->
              <el-col :span="6" style="display:flex;align-items:center;margin-left:auto;margin-right:auto;">
                <el-button
                    type="danger"
                    :icon="Delete"
                    circle
                    @click="removeSemiEditRecord(index)"
                />
              </el-col>
            </el-row>

            <el-divider v-if="index !== semiEditRecords.length - 1" />
          </div>

          <el-button
              type="primary"
              plain
              size="small"
              @click="addSemiEditRecord"
          >
            添加半成品
          </el-button>
        </el-form>

        <template #footer>
          <el-button @click="semiEditVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmSemiEdit">确定</el-button>
        </template>
      </el-dialog>
      <el-form :model="parseForm" label-width="90px" class="parse-form">
        <div class="parse-basic-panel">
          <el-form-item label="报数日期">
            <el-date-picker
                v-model="parseForm.entryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 180px"
            />
          </el-form-item>
        </div>

        <div class="mode-card-grid">
          <div
              class="mode-card"
              :class="{ active: isSemiMode }"
              @click="setParseType('SEMI_PRODUCT')"
          >
            <div class="mode-title">半成品快速入库</div>
            <div class="mode-desc">沿用原解析、固定码分配、半成品入库任务和确认入库流程。</div>
          </div>
          <div
              class="mode-card"
              :class="{ active: isFinishMode }"
              @click="setParseType('FINISHED_PRODUCT')"
          >
            <div class="mode-title">成品生产报数</div>
            <div class="mode-desc">用于识别生产产出和半成品用料，关联生产订单后生成生产处理草稿，不直接入库。</div>
          </div>
        </div>

        <div class="parse-text-panel">
          <div class="parse-text-header">
            <div>
              <div class="parse-text-title">原始报数文本</div>
              <div class="parse-text-subtitle">{{ parseModeSubtitle }}</div>
            </div>
          </div>
          <el-form-item label="报数文本" class="raw-text-item">
            <el-input
                v-model="parseForm.rawText"
                type="textarea"
                :rows="4"
                placeholder="直接粘贴报数文本"
          />
          </el-form-item>
          <div class="parse-actions">
            <el-button type="primary" :loading="loadingParse" @click="handleParse">
              解析报数
            </el-button>
            <el-button @click="handleResetParse">清空</el-button>
          </div>
        </div>
      </el-form>
    </el-card>

    <el-card v-if="isFinishMode" class="table-card finish-flow-card" style="max-width: 1200px">
      <div class="finish-flow-header">
        <div>
          <div class="card-title">成品报数智能处理草稿</div>
          <div class="parse-text-subtitle">系统先生成处理草稿，用户只需要确认异常项和关联生产订单；本页不直接入库。</div>
        </div>
        <el-select
            v-model="batchId"
            placeholder="暂无历史解析"
            size="small"
            clearable
            filterable
            :loading="loadingHistory"
            style="width: 260px"
            @change="handleHistoryChange"
        >
          <el-option
              v-for="item in filteredHistoryOptions"
              :key="item.batchId"
              :label="item.displayName"
              :value="item.batchId"
          >
            <div class="history-option">
              <span>{{ item.displayName }}</span>
              <span>{{ historyOptionTypeLabel(item) }} · {{ item.taskCount || 0 }} 条</span>
            </div>
          </el-option>
        </el-select>
      </div>

      <el-empty
          v-if="!taskList.length && !batchId"
          description="暂无成品报数解析结果，可先解析报数文本，或从右上角选择历史解析。"
      />

      <template v-else>
      <el-steps :active="finishStep - 1" finish-status="success" class="finish-steps">
        <el-step title="识别报数" />
        <el-step title="确认草稿" />
        <el-step title="处理异常" />
        <el-step title="生成处理" />
      </el-steps>

      <div class="draft-overview">
        <div class="draft-overview-card">
          <div class="overview-label">系统已识别</div>
          <strong>成品产出 {{ finishDraftSummary.outputCount }} 项，半成品用料 {{ finishDraftSummary.materialCount }} 项</strong>
          <span>已自动匹配库存 {{ finishDraftSummary.autoMatchedCount }} 项，未识别内容 {{ finishDraftSummary.unmatchedCount }} 项</span>
        </div>
        <div class="draft-overview-card">
          <div class="overview-label">系统推荐处理</div>
          <strong>{{ finishRecommendation.title }}</strong>
          <span>{{ finishRecommendation.detail }}</span>
        </div>
        <div class="draft-overview-card warning">
          <div class="overview-label">需要用户确认</div>
          <strong>{{ finishDraftSummary.pendingConfirmCount }} 项待确认，库存不足 {{ finishDraftSummary.shortageCount }} 项</strong>
          <span>{{ finishDraftSummary.preprintText }}</span>
        </div>
      </div>

      <div v-if="finishStep === 1" class="summary-stat-grid six">
        <div class="summary-stat"><span>成品产出</span><strong>{{ finishDraftSummary.outputCount }}</strong><em>项</em></div>
        <div class="summary-stat"><span>半成品用料</span><strong>{{ finishDraftSummary.materialCount }}</strong><em>项</em></div>
        <div class="summary-stat"><span>已自动匹配库存</span><strong>{{ finishDraftSummary.autoMatchedCount }}</strong><em>项</em></div>
        <div class="summary-stat"><span>需要用户确认</span><strong>{{ finishDraftSummary.pendingConfirmCount }}</strong><em>项</em></div>
        <div class="summary-stat"><span>库存不足</span><strong>{{ finishDraftSummary.shortageCount }}</strong><em>项</em></div>
        <div class="summary-stat"><span>未识别内容</span><strong>{{ finishDraftSummary.unmatchedCount }}</strong><em>项</em></div>
      </div>

      <div v-if="finishStep === 1" class="summary-panel">
        <el-collapse class="draft-detail-collapse">
          <el-collapse-item name="raw">
            <template #title>
              <div class="smart-collapse-title">
                <div class="smart-collapse-left">
                  <el-icon class="smart-collapse-icon"><Document /></el-icon>
                  <div>
                    <div class="smart-collapse-main">原始报数文本</div>
                    <div class="smart-collapse-sub">用于核对模型识别结果。</div>
                  </div>
                </div>
                <el-tag size="small" type="info">已保留</el-tag>
              </div>
            </template>
            <div class="raw-text-preview compact">{{ finishFlow.rawText || parseForm.rawText || '没有原始报数文本' }}</div>
          </el-collapse-item>
          <el-collapse-item name="outputs">
            <template #title>
              <div class="smart-collapse-title">
                <div class="smart-collapse-left">
                  <el-icon class="smart-collapse-icon"><Goods /></el-icon>
                  <div>
                    <div class="smart-collapse-main">识别到的成品产出</div>
                    <div class="smart-collapse-sub">请确认产品、生产日期和数量是否正确。</div>
                  </div>
                </div>
                <el-tag size="small" type="success">{{ finishDraftSummary.outputCount }} 项</el-tag>
              </div>
            </template>
            <el-table :data="finishFlow.finishOutputItems" border size="small" empty-text="暂未识别到成品产出">
              <el-table-column prop="productName" label="产品" min-width="160" />
              <el-table-column prop="productionDate" label="生产日期" width="120" />
              <el-table-column label="数量" width="130">
                <template #default="{ row }">{{ quantityText(row.boardCount, row.pieceCount) }}</template>
              </el-table-column>
              <el-table-column label="预计标签数" width="110">
                <template #default="{ row }">{{ estimatedLabelCount(row) || '待计算' }}</template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
          <el-collapse-item name="materials">
            <template #title>
              <div class="smart-collapse-title">
                <div class="smart-collapse-left">
                  <el-icon class="smart-collapse-icon"><Box /></el-icon>
                  <div>
                    <div class="smart-collapse-main">识别到的半成品用料</div>
                    <div class="smart-collapse-sub">系统将根据产品和日期查询可领用库存。</div>
                  </div>
                </div>
                <el-tag size="small" type="warning">{{ finishDraftSummary.pendingConfirmCount }} 项待确认 / {{ finishDraftSummary.autoMatchedCount }} 项已匹配</el-tag>
              </div>
            </template>
            <div v-if="!activeMaterialHints.length" class="empty-panel">暂未识别到半成品用料。</div>
            <div v-else class="compact-material-list">
              <div v-for="hint in activeMaterialHints.slice(0, 8)" :key="hint.key" class="compact-material-row">
                <strong>{{ hint.productName || hint.materialNameRaw || '未匹配半成品' }}</strong>
                <span>{{ hint.productionDate || '日期待确认' }}</span>
                <em>{{ quantityText(hint.boardCount, hint.pieceCount) }}</em>
              </div>
              <div v-if="activeMaterialHints.length > 8" class="empty-text">其余 {{ activeMaterialHints.length - 8 }} 项将在异常处理步骤中确认。</div>
            </div>
          </el-collapse-item>
          <el-collapse-item name="risks">
            <template #title>
              <div class="smart-collapse-title">
                <div class="smart-collapse-left">
                  <el-icon class="smart-collapse-icon"><Warning /></el-icon>
                  <div>
                    <div class="smart-collapse-main">待确认问题</div>
                    <div class="smart-collapse-sub">请处理未识别内容、库存不足或匹配异常。</div>
                  </div>
                </div>
                <el-tag size="small" type="danger">{{ finishDraftSummary.riskCount }} 条风险</el-tag>
              </div>
            </template>
            <div v-if="finishRiskItems.length" class="risk-list">
              <div v-for="item in finishRiskItems" :key="item.key" class="risk-item">
                <el-tag :type="item.type" size="small">{{ item.label }}</el-tag>
                <span>{{ item.text }}</span>
              </div>
            </div>
            <div v-else class="empty-text">
              暂无需要处理的问题。
            </div>
          </el-collapse-item>
        </el-collapse>
        <div class="step-actions">
          <el-button type="primary" @click="finishStep = 2">下一步：生成处理草稿</el-button>
        </div>
      </div>

      <div v-if="finishStep === 4" class="primary-action-card">
        <div>
          <div class="summary-title">下一步建议</div>
          <div class="section-subtitle">{{ finishPrimaryAction.description }}</div>
        </div>
        <el-button
            type="primary"
            size="large"
            :loading="finishPrimaryAction.loading"
            :disabled="finishPrimaryAction.disabled"
            @click="handleFinishPrimaryAction"
        >
          {{ finishPrimaryAction.label }}
        </el-button>
      </div>

      <div v-if="finishStep === 2" class="summary-panel">
        <div class="section-title-row">
          <div>
            <div class="summary-title">推荐生产订单</div>
            <div class="section-subtitle">系统按生产日期、产出产品和订单状态推荐；没有高匹配订单时优先新建生产订单。</div>
          </div>
        </div>
        <div v-if="recommendedFinishOrders.length && finishRecommendation.mode !== 'CREATE'" class="recommend-card-grid">
          <div
              v-for="item in recommendedFinishOrders"
              :key="item.id"
              class="recommend-card"
              :class="{ active: finishFlow.orderId === item.id }"
              @click="selectRecommendedOrder(item)"
          >
            <div class="recommend-head">
              <strong>{{ item.orderNo }}</strong>
              <el-tag size="small" :type="orderStatusTagType(item.status)">{{ orderStatusLabel(item.status) }}</el-tag>
            </div>
            <div class="recommend-grid">
              <div><span>生产日期</span><em>{{ item.productionDate || '暂无' }}</em></div>
              <div><span>计划产出</span><em>{{ item.plannedOutputText || '暂无' }}</em></div>
              <div><span>已领用</span><em>{{ item.actualMaterialText || `${item.materialCount || 0} 项` }}</em></div>
              <div><span>已预打印</span><em>{{ item.labelReservedText || '暂无' }}</em></div>
              <div><span>入库进度</span><em>{{ item.inboundProgressText || '暂无' }}</em></div>
            </div>
            <div class="match-reason">{{ item.matchReason }}</div>
          </div>
        </div>
        <div v-else class="create-recommend-card">
          <strong>推荐新建生产订单</strong>
          <span>未找到高匹配的未完成订单。系统会用当前识别结果自动填入计划产出和计划领用。</span>
        </div>

        <el-collapse class="manual-order-collapse">
          <el-collapse-item name="manual-order">
            <template #title>
              <div class="smart-collapse-title">
                <div class="smart-collapse-left">
                  <el-icon class="smart-collapse-icon"><Search /></el-icon>
                  <div>
                    <div class="smart-collapse-main">手动选择生产订单</div>
                    <div class="smart-collapse-sub">找不到推荐订单时，可按订单号、日期或产品搜索。</div>
                  </div>
                </div>
                <el-tag size="small">可选</el-tag>
              </div>
            </template>
            <div class="order-search-grid">
              <el-input v-model="finishOrderSearch.orderNo" clearable placeholder="订单号" class="order-search-field" />
              <el-date-picker v-model="finishOrderSearch.productionDate" type="date" value-format="YYYY-MM-DD" clearable placeholder="生产日期" class="order-search-field" />
              <el-select v-model="finishOrderSearch.status" clearable placeholder="状态" class="order-search-field">
                <el-option v-for="item in orderStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-input v-model="finishOrderSearch.productKeyword" clearable placeholder="产品名称" class="order-search-field" />
              <el-button class="order-search-refresh" :loading="loadingFinishOrders" @click="loadFinishOrderOptions">刷新订单</el-button>
            </div>
            <div class="order-result-list">
              <div
                  v-for="item in filteredFinishOrderOptions"
                  :key="item.id"
                  class="order-result-card"
                  :class="{ active: finishFlow.orderId === item.id }"
                  @click="selectRecommendedOrder(item)"
              >
                <div class="order-result-head">
                  <div>
                    <strong>{{ item.orderNo }}</strong>
                    <span>{{ item.productionDate || '暂无日期' }}</span>
                  </div>
                  <div class="order-result-status">
                    <el-tag size="small" :type="orderStatusTagType(item.status)">{{ orderStatusLabel(item.status) }}</el-tag>
                    <span v-if="finishFlow.orderId === item.id" class="order-selected-badge">已选择</span>
                  </div>
                </div>
                <div class="order-result-summary">
                  <div>
                    <span>计划产出</span>
                    <em>{{ item.plannedOutputText || '暂无' }}</em>
                  </div>
                  <div>
                    <span>已领用</span>
                    <em>{{ item.materialSummaryText || item.actualMaterialText || '尚未领用' }}</em>
                  </div>
                  <div>
                    <span>已预打印</span>
                    <em>{{ item.labelReservedText || '暂无' }}</em>
                  </div>
                  <div>
                    <span>入库进度</span>
                    <em>{{ item.inboundProgressText || '暂无' }}</em>
                  </div>
                </div>
              </div>
              <div v-if="!filteredFinishOrderOptions.length" class="empty-panel">没有匹配的生产订单。</div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <div v-if="finishStep === 2" class="step-actions">
        <el-button @click="finishStep = 1">上一步</el-button>
        <el-button type="primary" :loading="loadingCreateFinishOrder || loadingFinishDetail" @click="useRecommendedFinishPlan">
          {{ finishRecommendation.mode === 'CREATE' && !finishFlow.orderId ? '创建生产订单并保存草稿' : '下一步' }}
        </el-button>
      </div>

      <el-collapse v-if="finishStep === 3" v-model="finishOpenPanels" class="draft-detail-collapse">
        <el-collapse-item name="pending">
          <template #title>
            <div class="smart-collapse-title">
              <div class="smart-collapse-left">
                <el-icon class="smart-collapse-icon"><Warning /></el-icon>
                <div>
                  <div class="smart-collapse-main">待确认问题</div>
                  <div class="smart-collapse-sub">请处理未识别内容、库存不足或匹配异常。</div>
                </div>
              </div>
              <el-tag size="small" type="warning">{{ pendingMaterialHints.length }} 条风险</el-tag>
            </div>
          </template>
          <div v-if="!pendingMaterialHints.length" class="empty-panel">没有需要人工确认的半成品用料。</div>
          <div v-for="hint in pendingMaterialHints" :key="hint.key" class="material-hint-card">
            <div class="hint-header">
              <div>
                <strong>{{ hint.productName || hint.materialNameRaw || '未匹配半成品' }}</strong>
                <span>{{ hint.productionDate || '日期待确认' }} / {{ quantityText(hint.boardCount, hint.pieceCount) }}</span>
                <span v-if="hint.candidateRisk" class="hint-risk">{{ hint.candidateRisk }}</span>
              </div>
              <el-button
                  size="small"
                  :disabled="!hint.productId || !finishFlow.orderId"
                  :loading="hint.loading"
                  @click="loadMaterialCandidatesForHint(findMaterialHintIndex(hint))"
              >
                查询库存候选
              </el-button>
            </div>
            <el-alert v-if="!hint.productId" type="warning" :closable="false" title="该用料未匹配到半成品产品，请先选择产品。" />
            <div v-if="hint.recommendation" class="material-recommendation">
              <div>
                <span>用料需求</span>
                <strong>{{ quantityText(hint.recommendation.requiredBoardCount, hint.recommendation.requiredPieceCount) }}</strong>
              </div>
              <div>
                <span>建议领用</span>
                <strong>{{ materialRecommendationPickText(hint.recommendation) }}</strong>
              </div>
              <div>
                <span>预计退回</span>
                <strong :class="{ 'return-warning': hint.recommendation.expectedReturnPieces > 0 }">
                  {{ materialRecommendationReturnText(hint.recommendation) }}
                </strong>
              </div>
            </div>
            <div class="hint-edit-grid">
              <el-cascader
                  v-model="hint.productId"
                  :options="semiProductOptions"
                  :props="cascaderProps"
                  filterable
                  clearable
                  placeholder="选择半成品"
                  @change="value => handleMaterialHintProductChange(hint, value)"
              />
              <el-date-picker v-model="hint.productionDate" type="date" value-format="YYYY-MM-DD" placeholder="生产日期" @change="clearMaterialHintCandidates(hint)" />
              <el-input-number v-model="hint.boardCount" :min="0" :controls="false" placeholder="板数" @change="clearMaterialHintCandidates(hint)" />
              <el-input-number v-model="hint.pieceCount" :min="0" :controls="false" placeholder="件数" @change="clearMaterialHintCandidates(hint)" />
              <el-button :type="hint.skipped ? 'success' : 'warning'" plain @click="toggleMaterialHintSkipped(hint)">
                {{ hint.skipped ? '恢复' : '跳过该条' }}
              </el-button>
            </div>
            <el-table
                v-if="hint.productId && hint.candidates && hint.candidates.length"
                :data="hint.candidates"
                border
                size="small"
                empty-text="暂无库存候选二维码"
            >
              <el-table-column label="选择" width="56">
                <template #default="{ row }">
                  <el-checkbox
                      :model-value="hint.selectedCandidateIds.includes(row.palletCodeId)"
                      @change="checked => toggleHintCandidate(hint, row, checked)"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="palletCode" label="二维码" min-width="150" />
              <el-table-column prop="productName" label="产品" min-width="140" />
              <el-table-column prop="productionDate" label="生产日期" width="120" />
              <el-table-column prop="quantityText" label="库存数量" width="110" />
              <el-table-column label="推荐" width="92">
                <template #default="{ row }">
                  <el-tag v-if="isRecommendedCandidate(hint, row)" type="success" size="small">建议领用</el-tag>
                  <span v-else class="muted-text">-</span>
                </template>
              </el-table-column>
              <el-table-column label="库位" min-width="150">
                <template #default="{ row }">{{ candidateLocationText(row) }}</template>
              </el-table-column>
            </el-table>
            <div v-if="hint.selectedCandidateIds.length" class="candidate-selection-tip">已选择 {{ hint.selectedCandidateIds.length }} 个库存二维码，确认后才会扣库存并关联生产订单。</div>
          </div>
        </el-collapse-item>

        <el-collapse-item name="matched">
          <template #title>
            <div class="smart-collapse-title">
              <div class="smart-collapse-left">
                <el-icon class="smart-collapse-icon"><CircleCheck /></el-icon>
                <div>
                  <div class="smart-collapse-main">识别到的半成品用料</div>
                  <div class="smart-collapse-sub">系统将根据产品和日期查询可领用库存。</div>
                </div>
              </div>
              <el-tag size="small" type="success">{{ autoMatchedMaterialHints.length }} 项已匹配</el-tag>
            </div>
          </template>
          <div v-if="!autoMatchedMaterialHints.length" class="empty-panel">暂无已自动匹配的半成品用料。</div>
          <div v-else class="matched-list">
            <div v-for="hint in autoMatchedMaterialHints" :key="hint.key" class="matched-row">
              <strong>{{ hint.productName || hint.materialNameRaw }}</strong>
              <span>{{ hint.productionDate || '日期待确认' }} / {{ quantityText(hint.boardCount, hint.pieceCount) }}</span>
              <span v-if="hint.recommendation">建议：{{ materialRecommendationPickText(hint.recommendation) }}</span>
              <em>预计退回：{{ materialRecommendationReturnText(hint.recommendation) }}</em>
            </div>
          </div>
        </el-collapse-item>

        <el-collapse-item v-if="false" name="outputs">
          <template #title>
            <div class="smart-collapse-title">
              <div class="smart-collapse-left">
                <el-icon class="smart-collapse-icon"><Goods /></el-icon>
                <div>
                  <div class="smart-collapse-main">识别到的成品产出</div>
                  <div class="smart-collapse-sub">请确认产品、生产日期和数量是否正确。</div>
                </div>
              </div>
              <el-tag size="small" type="success">{{ finishDraftSummary.outputCount }} 项</el-tag>
            </div>
          </template>
          <el-table :data="finishFlow.finishOutputItems" border size="small" empty-text="暂未识别到成品产出">
            <el-table-column label="产品" min-width="210">
              <template #default="{ row }">
                <el-cascader
                    v-model="row.productId"
                    :options="finishedProductOptions"
                    :props="cascaderProps"
                    filterable
                    clearable
                    placeholder="选择成品"
                    style="width: 100%"
                    @change="value => handleDraftOutputProductChange(row, value)"
                />
              </template>
            </el-table-column>
            <el-table-column label="生产日期" width="150">
              <template #default="{ row }">
                <el-date-picker v-model="row.productionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="板数" width="110">
              <template #default="{ row }">
                <el-input-number v-model="row.boardCount" :min="0" :controls="false" style="width: 78px" />
              </template>
            </el-table-column>
            <el-table-column label="件数" width="110">
              <template #default="{ row }">
                <el-input-number v-model="row.pieceCount" :min="0" :controls="false" style="width: 78px" />
              </template>
            </el-table-column>
            <el-table-column label="数量" width="130">
              <template #default="{ row }">{{ quantityText(row.boardCount, row.pieceCount) }}</template>
            </el-table-column>
            <el-table-column label="每板件数" width="100">
              <template #default="{ row }">{{ productPiecesPerPallet(row.productId) || '未配置' }}</template>
            </el-table-column>
            <el-table-column label="预计需要标签数" width="130">
              <template #default="{ row }">{{ estimatedLabelCount(row) || '待计算' }}</template>
            </el-table-column>
          </el-table>
        </el-collapse-item>

        <el-collapse-item v-if="false" name="risks">
          <template #title>
            <div class="smart-collapse-title">
              <div class="smart-collapse-left">
                <el-icon class="smart-collapse-icon"><Warning /></el-icon>
                <div>
                  <div class="smart-collapse-main">待确认问题</div>
                  <div class="smart-collapse-sub">请处理未识别内容、库存不足或匹配异常。</div>
                </div>
              </div>
              <el-tag size="small" type="danger">{{ finishDraftSummary.riskCount }} 条风险</el-tag>
            </div>
          </template>
          <div v-if="finishRiskItems.length" class="risk-list">
            <div v-for="item in finishRiskItems" :key="item.key" class="risk-item">
              <el-tag :type="item.type" size="small">{{ item.label }}</el-tag>
              <span>{{ item.text }}</span>
            </div>
          </div>
          <div v-else class="empty-text">
            暂无需要处理的问题。
          </div>
          <div class="risk-list compact">
            <div>成品报数不会直接创建成品入库任务，必须先关联成品生产订单。</div>
            <div>半成品用料不会直接扣库存，必须由用户选择真实库存二维码后确认领用。</div>
            <div>固定产品二维码不足的风险在预打印或确认产出时暴露，不在本页走旧贴码流程。</div>
          </div>
        </el-collapse-item>
      </el-collapse>
      <div v-if="finishStep === 3" class="step-actions">
        <el-button @click="finishStep = 2">上一步</el-button>
        <el-button type="primary" @click="finishStep = 4">确认异常处理结果</el-button>
      </div>

      <div v-if="finishStep === 4" class="summary-panel">
        <div class="summary-title">最终处理摘要</div>
        <div class="final-summary-grid">
          <div><span>生产订单</span><strong>{{ finishFlow.selectedOrder?.orderNo || '尚未关联，将新建生产订单' }}</strong></div>
          <div><span>计划产出</span><strong>{{ finishDraftSummary.outputCount }} 项</strong></div>
          <div><span>计划领用</span><strong>{{ finishDraftSummary.materialCount }} 项</strong></div>
          <div><span>半成品领用</span><strong>{{ hasMaterialPickConfirmed ? '已确认或无需领用' : '待确认' }}</strong></div>
          <div><span>实际产出</span><strong>{{ finishFlow.finishOutputItems.length ? '可确认' : '缺少产出' }}</strong></div>
          <div><span>预打印标签</span><strong>{{ selectedOrderReservedLabelCount }}/{{ requiredOutputLabelCount }}</strong></div>
        </div>
        <el-collapse class="draft-detail-collapse">
          <el-collapse-item name="outputs">
            <template #title>
              <div class="smart-collapse-title">
                <div class="smart-collapse-left">
                  <el-icon class="smart-collapse-icon"><Goods /></el-icon>
                  <div>
                    <div class="smart-collapse-main">识别到的成品产出</div>
                    <div class="smart-collapse-sub">请确认产品、生产日期和数量是否正确。</div>
                  </div>
                </div>
                <el-tag size="small" type="success">{{ finishDraftSummary.outputCount }} 项</el-tag>
              </div>
            </template>
            <el-table :data="finishFlow.finishOutputItems" border size="small" empty-text="暂未识别到成品产出">
              <el-table-column prop="productName" label="产品" min-width="160" />
              <el-table-column prop="productionDate" label="生产日期" width="120" />
              <el-table-column label="数量" width="130">
                <template #default="{ row }">{{ quantityText(row.boardCount, row.pieceCount) }}</template>
              </el-table-column>
              <el-table-column label="预计标签数" width="110">
                <template #default="{ row }">{{ estimatedLabelCount(row) || '待计算' }}</template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
        <div class="step-actions">
          <el-button @click="finishStep = 3">上一步</el-button>
        </div>
      </div>
      </template>
    </el-card>

    <!-- 任务列表区 -->
    <el-card v-if="isSemiMode" class="table-card semi-flow-card" style="max-width: 1200px">
      <div class="finish-flow-header">
        <div>
          <div class="card-title">半成品快速入库处理</div>
          <div class="parse-text-subtitle">系统先生成半成品入库草稿，确认产品、日期、库位和固定二维码后再执行入库。</div>
        </div>
        <div class="semi-history-actions">
          <el-select
              v-model="batchId"
              placeholder="暂无历史解析"
              size="small"
              clearable
              filterable
              :loading="loadingHistory"
              style="width: 260px"
              @change="handleHistoryChange"
          >
            <el-option
                v-for="item in filteredHistoryOptions"
                :key="item.batchId"
                :label="item.displayName"
                :value="item.batchId"
            >
              <div class="history-option">
                <span>{{ item.displayName }}</span>
                <span>{{ historyOptionTypeLabel(item) }} · {{ item.taskCount || 0 }} 条</span>
              </div>
            </el-option>
          </el-select>
          <el-button
              v-if="batchId"
              type="primary"
              link
              size="small"
              :loading="loadingBatch"
              @click="handleReloadBatch"
          >
            重新加载
          </el-button>
        </div>
      </div>

      <el-empty
          v-if="!taskList.length && !batchId"
          description="暂无半成品报数解析结果，可先解析报数文本，或从右上角选择历史解析。"
      />

      <template v-else>
      <div class="draft-overview semi-overview">
        <div class="draft-overview-card">
          <div class="overview-label">系统已识别</div>
          <strong>半成品入库 {{ semiTaskSummary.total }} 项，可直接确认 {{ semiTaskSummary.ready }} 项</strong>
          <span>已选择 {{ selectedTaskIds.length }} 项，待补充信息 {{ semiTaskSummary.needConfirm }} 项</span>
        </div>
        <div class="draft-overview-card">
          <div class="overview-label">固定码准备</div>
          <strong>预计需要 {{ semiTaskSummary.requiredQr }} 个二维码</strong>
          <span>当前可用 {{ semiTaskSummary.availableQr }} 个，库存和库位容量仍按原流程校验。</span>
        </div>
        <div class="draft-overview-card warning">
          <div class="overview-label">需要用户确认</div>
          <strong>{{ semiTaskSummary.risk }} 项风险，{{ semiTaskSummary.blocked }} 项暂不可入库</strong>
          <span>请确认产品、生产日期、库位、侧别和数量后再执行入库。</span>
        </div>
      </div>

      <el-alert
          v-if="globalRemarks.length"
          type="info"
          show-icon
          class="global-remark-alert"
          title="解析备注"
      >
        <template #default>
          <div v-for="(r, idx) in globalRemarks" :key="idx">
            {{ idx + 1 }}. {{ r }}
          </div>
        </template>
      </el-alert>

      <el-collapse class="draft-detail-collapse semi-detail-collapse">
        <el-collapse-item name="tasks">
          <template #title>
            <div class="smart-collapse-title">
              <div class="smart-collapse-left">
                <el-icon class="smart-collapse-icon"><Box /></el-icon>
                <div>
                  <div class="smart-collapse-main">识别到的半成品入库任务</div>
                  <div class="smart-collapse-sub">请确认产品、生产日期、库位和数量是否正确。</div>
                </div>
              </div>
              <el-tag size="small" type="success">{{ semiTaskSummary.total }} 项</el-tag>
            </div>
          </template>

          <div class="semi-table-toolbar">
            <el-form :inline="true" :model="filterForm" class="filter-form">
              <el-form-item label="风险">
                <el-select v-model="filterForm.risk" size="small" style="width: 120px">
                  <el-option label="全部" value="ALL" />
                  <el-option label="高" value="RED" />
                  <el-option label="中" value="YELLOW" />
                  <el-option label="低" value="GREEN" />
                </el-select>
              </el-form-item>
            </el-form>
          </div>

      <el-table
          :data="filteredTasks"
          border
          stripe
          style="width: 100%"
          v-loading="loadingBatch"
          empty-text="暂无解析结果"
          @selection-change="handleSelectionChange"
          :row-key="row => row.taskId"
          height="480"
      >

        <!--        <el-table-column type="expand">-->
<!--          <template #default="{ row }">-->
<!--            <el-button type="primary" link @click="openSemiEditDialog(row)">-->
<!--              {{ (row.semiRecords && row.semiRecords.length) ? '编辑' : '添加' }}-->
<!--            </el-button>-->
<!--            <div v-if="row.type === 'FINISHED_PRODUCT' && row.suggestedSemiRecords && row.suggestedSemiRecords.length">-->
<!--              <div class="semi-title">关联半成品记录</div>-->
<!--              <el-table-->
<!--                  :data="row.suggestedSemiRecords"-->
<!--                  size="small"-->
<!--                  border-->
<!--                  style="width: 100%; margin-bottom: 8px"-->
<!--              >-->
<!--                <el-table-column prop="productName" label="半成品" min-width="100" />-->
<!--                <el-dialog-->
<!--                    v-model="semiEditVisible"-->
<!--                    title="关联半成品"-->
<!--                    width="820px"-->
<!--                >-->
<!--                  <el-form>-->
<!--                    <div v-for="(item, index) in semiEditRecords" :key="index" style="margin-bottom: 12px">-->
<!--                      <el-row :gutter="16">-->
<!--                        &lt;!&ndash; 半成品名称 &ndash;&gt;-->
<!--                        <el-col :span="8">-->
<!--                          <el-form-item :label="'半成品名称'" :prop="`semiEditRecords.${index}.semiProductId`">-->
<!--                            <el-select-->
<!--                                v-model="item.semiProductId"-->
<!--                                placeholder="选择半成品"-->
<!--                                clearable-->
<!--                                filterable-->
<!--                                @change="onSemiProductChange(item)"-->
<!--                            >-->
<!--                              <el-option-->
<!--                                  v-for="semi in semiProductList"-->
<!--                                  :key="semi.productId"-->
<!--                                  :label="semi.productName"-->
<!--                                  :value="semi.productId"-->
<!--                              />-->
<!--                            </el-select>-->
<!--                          </el-form-item>-->
<!--                        </el-col>-->

<!--                        &lt;!&ndash; 库位 &ndash;&gt;-->
<!--                        <el-col :span="6">-->
<!--                          <el-form-item :label="'库位'" :prop="`semiEditRecords.${index}.warehouseId`">-->
<!--                            <el-select-->
<!--                                v-model="item.warehouseId"-->
<!--                                placeholder="选择库位"-->
<!--                                clearable-->
<!--                                filterable-->
<!--                                style="width: 100%"-->
<!--                            >-->
<!--                              <el-option-->
<!--                                  v-for="w in warehouseList"-->
<!--                                  :key="w.warehouseId || w.id"-->
<!--                                  :label="w.warehouseName"-->
<!--                                  :value="w.warehouseId || w.id"-->
<!--                              />-->
<!--                            </el-select>-->
<!--                          </el-form-item>-->
<!--                        </el-col>-->

<!--                        &lt;!&ndash; 数量 + 单位（板/件） &ndash;&gt;-->
<!--                        <el-col :span="6">-->
<!--                          <el-form-item :label="'数量'" :prop="`semiEditRecords.${index}.quantity`">-->
<!--                            <div style="display: flex; gap: 4px">-->
<!--                              <el-input-->
<!--                                  v-model="item.quantity"-->
<!--                                  clearable-->
<!--                                  style="flex: 1"-->
<!--                              />-->
<!--                              <el-select-->
<!--                                  v-model="item.unit"-->
<!--                                  placeholder="单位"-->
<!--                                  style="width: 80px"-->
<!--                              >-->
<!--                                <el-option label="板" value="0" />-->
<!--                                <el-option label="件" value="1" />-->
<!--                              </el-select>-->
<!--                            </div>-->
<!--                          </el-form-item>-->
<!--                        </el-col>-->

<!--                        &lt;!&ndash; 删除按钮 &ndash;&gt;-->
<!--                        <el-col :span="4" style="display:flex;align-items:center">-->
<!--                          <el-button-->
<!--                              type="danger"-->
<!--                              :icon="Delete"-->
<!--                              circle-->
<!--                              @click="removeSemiEditRecord(index)"-->
<!--                          />-->
<!--                        </el-col>-->
<!--                      </el-row>-->

<!--                      <el-row :gutter="16">-->
<!--                        &lt;!&ndash; 生产日期 &ndash;&gt;-->
<!--                        <el-col :span="8">-->
<!--                          <el-form-item :label="'生产日期'" :prop="`semiEditRecords.${index}.productionDate`">-->
<!--                            <el-date-picker-->
<!--                                v-model="item.productionDate"-->
<!--                                type="date"-->
<!--                                placeholder="生产日期"-->
<!--                                value-format="YYYY-MM-DD"-->
<!--                                style="width: 100%"-->
<!--                            />-->
<!--                          </el-form-item>-->
<!--                        </el-col>-->

<!--                        &lt;!&ndash; 套用化验数据开关，可选 &ndash;&gt;-->
<!--                        <el-col :span="8">-->
<!--                          <el-form-item label=" " >-->
<!--                            <el-switch-->
<!--                                v-model="item.useAssay"-->
<!--                                active-text="套用该半成品化验数据"-->
<!--                                :active-value="true"-->
<!--                                :inactive-value="false"-->
<!--                                @change="handleSemiUseAssay(index)"-->
<!--                            />-->
<!--                          </el-form-item>-->
<!--                        </el-col>-->
<!--                      </el-row>-->

<!--                      <el-divider v-if="index !== semiEditRecords.length - 1" />-->
<!--                    </div>-->

<!--                    <el-button-->
<!--                        type="primary"-->
<!--                        plain-->
<!--                        size="small"-->
<!--                        @click="addSemiEditRecord"-->
<!--                    >-->
<!--                      添加半成品-->
<!--                    </el-button>-->
<!--                  </el-form>-->

<!--                  <template #footer>-->
<!--                    <el-button @click="semiEditVisible = false">取消</el-button>-->
<!--                    <el-button type="primary" @click="confirmSemiEdit">确定</el-button>-->
<!--                  </template>-->
<!--                </el-dialog>-->

<!--                <el-table-column label="生产日期" width="140">-->
<!--                  <template #default="{ row: semi }">-->
<!--                    <el-date-picker-->
<!--                        v-model="semi.productionDate"-->
<!--                        type="date"-->
<!--                        value-format="YYYY-MM-DD"-->
<!--                        placeholder="日期"-->
<!--                        size="small"-->
<!--                        style="width: 120px"-->
<!--                    />-->
<!--                  </template>-->
<!--                </el-table-column>-->

<!--                <el-table-column label="库位" min-width="100">-->
<!--                  <template #default="{ row: semi }">-->
<!--                    <el-input-->
<!--                        v-model="semi.warehouseName"-->
<!--                        size="small"-->
<!--                        placeholder="库位名称"-->
<!--                        clearable-->
<!--                    />-->
<!--                  </template>-->
<!--                </el-table-column>-->

<!--                <el-table-column label="数量" width="300">-->
<!--                  <template #default="{ row: semi }">-->
<!--                    <el-input-number-->
<!--                        v-model="semi.quantity"-->
<!--                        :min="0"-->
<!--                        size="small"-->
<!--                        style="width: 90px"-->
<!--                    />-->
<!--                    <el-select-->
<!--                        v-model="semi.unit"-->
<!--                        size="small"-->
<!--                        style="width: 70px; margin-left: 6px"-->
<!--                    >-->
<!--                      <el-option label="板" value="0" />-->
<!--                      <el-option label="件" value="1" />-->
<!--                    </el-select>-->
<!--                  </template>-->
<!--                </el-table-column>-->

<!--                <el-table-column label="套用化验" width="110">-->
<!--                  <template #default="{ row: semi }">-->
<!--                    <el-switch-->
<!--                        v-model="semi.useAssay"-->
<!--                        :active-value="true"-->
<!--                        :inactive-value="false"-->
<!--                        size="small"-->
<!--                    />-->
<!--                  </template>-->
<!--                </el-table-column>-->
<!--              </el-table>-->
<!--            </div>-->
<!--            <div v-else class="no-semi-info">-->
<!--              当前任务无关联半成品记录-->
<!--            </div>-->
<!--          </template>-->
<!--        </el-table-column>-->

        <el-table-column type="selection" width="48" />

        <el-table-column label="日期" min-width="170">
          <template #default="{ row }">
            <el-date-picker
                v-model="row.entryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="日期"
                size="small"
                style="width: 145px"
            />
          </template>
        </el-table-column>

        <el-table-column label="产品" min-width="220">
          <template #default="{ row }">
            <el-cascader
                v-model="row._productId"
                :options="row.type === 'SEMI_PRODUCT' ? semiProductOptions : finishedProductOptions"
                :props="cascaderProps"
                clearable
                filterable
                size="small"
                controls-position="right"
                :show-all-levels="false"
                placeholder="选择产品"
                @change="(val) => handleProductChange(row, val)"
            />
          </template>
        </el-table-column>


        <el-table-column label="库位" min-width="150">
          <template #default="{ row }">
            <!-- 简单版：直接编辑库位名称，后端用名称匹配仓库 -->
            <el-input
                v-if="row.type === 'SEMI_PRODUCT'"
                v-model="row.semiWarehouseName"
                size="small"
                placeholder="输入库位"
                clearable
                style="width: 120px"
            />
            <el-input
                v-else
                v-model="row.warehouseName"
                size="small"
                placeholder="输入库位"
                clearable
                style="width: 120px"
            />
          </template>
        </el-table-column>

        <el-table-column label="侧别" min-width="110">
          <template #default="{ row }">
            <el-select v-model="row.side" size="small" style="width: 86px">
              <el-option label="左" value="左" />
              <el-option label="右" value="右" />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="板/件数" min-width="230">
          <template #default="{ row }">
            <div class="quantity-edit">
              <el-input-number
                  v-if="row.type === 'SEMI_PRODUCT'"
                  v-model="row.semiBoardQuantity"
                  :min="0"
                  size="small"
                  controls-position="right"
              />
              <el-input-number
                  v-else
                  v-model="row.finishedBoardQuantity"
                  :min="0"
                  size="small"
                  controls-position="right"
              />
              <span>板</span>
              <el-input-number
                  v-if="row.type === 'SEMI_PRODUCT'"
                  v-model="row.semiPieceQuantity"
                  :min="0"
                  size="small"
                  controls-position="right"
              />
              <el-input-number
                  v-else
                  v-model="row.finishedPieceQuantity"
                  :min="0"
                  size="small"
                  controls-position="right"
              />
              <span>件</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="canAutoStockIn" label="可入库" min-width="100">
          <template #default="{ row }">
            <el-tag
                size="small"
                :type="row.canAutoStockIn ? 'success' : 'info'"
            >
              {{ row.canAutoStockIn ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTaskDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <div class="left">
          已选择 <b>{{ selectedTaskIds.length }}</b> 条任务
        </div>
        <div class="right">
          <el-button
              type="primary"
              :loading="loadingConfirm"
              :disabled="!selectedTaskIds.length"
              @click="handleConfirm"
          >
            确认入库
          </el-button>
        </div>
      </div>
        </el-collapse-item>
      </el-collapse>
      </template>
    </el-card>

    <el-drawer v-model="detailDrawerVisible" title="解析详情" size="65%" class="auto-inbound-detail-drawer">
      <div v-if="detailTask" class="detail-drawer">
        <div class="drawer-section">
          <div class="block-title">基础信息</div>
          <div class="info-grid">
            <div class="info-card">
              <span>类型</span>
              <strong>{{ getDetailTypeLabel(detailTask) }}</strong>
            </div>
            <div class="info-card">
              <span>日期</span>
              <strong>{{ detailTask.entryDate || '-' }}</strong>
            </div>
            <div class="info-card">
              <span>产品</span>
              <strong>{{ getDetailProductName(detailTask) }}</strong>
            </div>
            <div class="info-card">
              <span>库位</span>
              <strong>{{ getDetailWarehouseName(detailTask) }}</strong>
            </div>
            <div class="info-card">
              <span>侧别</span>
              <strong>{{ detailTask.side || '-' }}</strong>
            </div>
            <div class="info-card">
              <span>板/件数</span>
              <strong>{{ getDetailQuantityText(detailTask) }}</strong>
            </div>
          </div>
        </div>

        <div class="drawer-section">
          <div class="block-title">风险与校验</div>
          <div class="info-grid">
            <div class="info-card">
              <span>风险等级</span>
              <strong>
                <el-tag size="small" :type="riskLevelTagType(detailTask.riskLevel)">
                  {{ riskLevelLabel(detailTask.riskLevel) }}
                </el-tag>
              </strong>
            </div>
            <div class="info-card">
              <span>化验记录</span>
              <strong>
                <el-tag size="small" :type="detailTask.hasAssay ? 'success' : 'warning'">
                  {{ detailTask.hasAssay ? '已匹配' : '暂无化验记录，不影响入库' }}
                </el-tag>
              </strong>
            </div>
          </div>
          <div class="conclusion-card">
            <div class="conclusion-title">风险原因</div>
            <div class="conclusion-text risk-reason">{{ detailTask.riskReason || '-' }}</div>
          </div>
        </div>

        <div class="drawer-section">
          <div class="block-title">二维码与拆分</div>
          <div class="info-grid">
            <div class="info-card">
              <span>二维码</span>
              <strong>当前需要：{{ detailTask.requiredQrCount ?? 0 }}个　可用：{{ detailTask.availableQrCount ?? 0 }}个</strong>
            </div>
            <div class="info-card">
              <span>可入库</span>
              <strong>{{ detailTask.canAutoStockIn ? '是' : '否' }}</strong>
            </div>
          </div>
          <div class="conclusion-card">
            <div class="conclusion-title">拆分结果</div>
            <div class="split-items">
              <el-tag
                  v-for="item in detailTask.taskItems || []"
                  :key="item.seq"
                  size="small"
                  :type="item.unit === '1' ? 'warning' : 'info'"
              >
                {{ item.displayQuantity }}
              </el-tag>
              <span v-if="!detailTask.taskItems || !detailTask.taskItems.length">-</span>
            </div>
          </div>
        </div>

        <div class="drawer-section">
          <div class="block-title">执行与消耗</div>
          <div class="conclusion-card">
            <div class="conclusion-title">执行结果</div>
            <div class="result-items">
              <div
                  v-for="item in detailTask.taskItems || []"
                  :key="`${item.seq}-${item.code || 'pending'}`"
                  class="result-line"
              >
                <el-tag
                    size="small"
                    :type="item.status === 'SUCCESS' ? 'success' : (item.status === 'FAILED' ? 'danger' : 'info')"
                >
                  {{ formatExecutionStatus(item) }}
                </el-tag>
                <span>{{ item.message || '-' }}</span>
              </div>
              <span v-if="!detailTask.taskItems || !detailTask.taskItems.length">-</span>
            </div>
          </div>
          <div class="conclusion-card">
            <div class="conclusion-title">生产消耗处理</div>
            <el-button
                v-if="detailTask.productionConsumptionItems && detailTask.productionConsumptionItems.length"
                size="small"
                type="primary"
                @click="openConsumptionDialog(detailTask)"
            >
              查看
            </el-button>
            <span v-else class="empty-text">-</span>
          </div>
        </div>

        <div class="drawer-section">
          <div class="block-title">备注</div>
          <div class="conclusion-card">
            <div class="conclusion-text remark-text">{{ detailTask.remark || '-' }}</div>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无可展示的解析详情" />
    </el-drawer>

    <el-dialog v-model="consumptionDialogVisible" title="生产消耗处理" width="760px">
      <el-table :data="consumptionRows" border>
        <el-table-column prop="materialName" label="半成品" min-width="150"/>
        <el-table-column prop="productionDate" label="生产日期" width="130"/>
        <el-table-column prop="quantityText" label="数量" width="130"/>
        <el-table-column prop="result" label="处理结果" min-width="240" show-overflow-tooltip/>
      </el-table>
      <template #footer>
        <el-button @click="consumptionDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { getSemiProduct, getStProduct } from '@/api/assay'
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTokenStore } from '@/stores/token'
import { confirmAutoInbound, getAutoInboundBatch, listAutoInboundHistory, parseAutoInbound } from '@/api/autoInbound'
import { getWarehouse } from '@/api/warehouse'
import { Box, CircleCheck, Delete, Document, Goods, Search, Warning } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import {
  createProductionOrder,
  finishProductionOrder,
  getProductionOrderDetail,
  listProductionOrderOptions,
  pageMaterialCandidates,
  pickProductionMaterials
} from '@/api/production'
import {
  buildMaterialPickRecommendation,
  materialPickRecommendationIds,
  materialPickRecommendationPickText,
  materialPickRecommendationReturnText
} from '@/utils/materialPickRecommendation.mjs'

// ---------- 工具：从 JWT 里解析出当前用户ID（sub） ----------
function decodeJwtSub (token) {
  if (!token) return null
  try {
    const parts = token.split('.')
    if (parts.length < 2) return null
    let payload = parts[1]
    payload = payload.replace(/-/g, '+').replace(/_/g, '/')
    while (payload.length % 4 !== 0) {
      payload += '='
    }
    const json = JSON.parse(atob(payload))
    return json.sub ? parseInt(json.sub, 10) : null
  } catch (e) {
    console.error('解析 JWT 失败', e)
    return null
  }
}

// ---------- 表单 & 状态 ----------
const today = new Date().toISOString().slice(0, 10)

const parseForm = ref({
  entryDate: today,
  parseType: 'SEMI_PRODUCT',
  rawText: ''
})

const batchId = ref('')
const taskList = ref([])
const globalRemarks = ref([])
const historyOptions = ref([])

const loadingParse = ref(false)
const loadingBatch = ref(false)
const loadingConfirm = ref(false)
const loadingHistory = ref(false)
const detailDrawerVisible = ref(false)
const detailTask = ref(null)
const consumptionDialogVisible = ref(false)
const consumptionTask = ref(null)

const filterForm = ref({
  type: 'ALL',
  risk: 'ALL'
})

const selectedTaskIds = ref([])

// 当前用户 ID（操作员）
const tokenStore = useTokenStore()
const operatorId = ref(decodeJwtSub(tokenStore.token))
const router = useRouter()

const createEmptyFinishFlow = () => ({
  rawText: '',
  finishOutputItems: [],
  materialHints: [],
  unmatchedNames: [],
  orderMode: 'EXISTING',
  orderId: undefined,
  selectedOrder: null,
  createForm: {
    productionDate: today,
    remark: '智能报数新建成品生产订单'
  },
  boundCodes: []
})

const finishStep = ref(1)
const finishFlow = ref(createEmptyFinishFlow())
const finishOpenPanels = ref(['pending'])
const finishOrderOptions = ref([])
const finishOrderSearch = ref({
  orderNo: '',
  productionDate: '',
  status: '',
  productKeyword: ''
})
const loadingFinishOrders = ref(false)
const loadingCreateFinishOrder = ref(false)
const loadingFinishDetail = ref(false)
const loadingPickMaterials = ref(false)
const loadingSaveOutputs = ref(false)
const loadingFinishProduction = ref(false)
const loadingAutoMatchMaterials = ref(false)

const isSemiMode = computed(() => parseForm.value.parseType === 'SEMI_PRODUCT')
const isFinishMode = computed(() => parseForm.value.parseType === 'FINISHED_PRODUCT')
const parseModeSubtitle = computed(() => isSemiMode.value
    ? '半成品报数将解析为半成品快速入库任务，支持确认入库和批量打印。'
    : '成品报数用于识别生产产出和半成品用料，关联生产订单后生成生产处理草稿，不直接入库。'
)
const filteredHistoryOptions = computed(() => historyOptions.value.filter(item => item.parseType === parseForm.value.parseType))
const historyOptionTypeLabel = (item) => {
  if (item?.parseType === 'SEMI_PRODUCT') return '半成品'
  if (item?.parseType === 'FINISHED_PRODUCT') return '成品'
  return '未知'
}
const selectedMaterialCandidateIds = computed(() => finishFlow.value.materialHints
    .filter(item => !item.skipped)
    .flatMap(item => item.selectedCandidateIds || [])
)

const orderStatusOptions = [
  { value: 'ISSUED', label: '已下发' },
  { value: 'MATERIALING', label: '领料中' },
  { value: 'MATERIALED', label: '已领料' },
  { value: 'PREPRINTED', label: '已预打印' },
  { value: 'WAIT_INBOUND', label: '待入库' },
  { value: 'PART_INBOUND', label: '部分入库' }
]

// ---------- 关联半成品弹窗 ----------
const semiEditVisible = ref(false)
const semiEditRecords = ref([])  // 弹窗里的临时列表
const semiEditTask = ref(null)   // 当前正在编辑的任务

const warehouseList = ref([])

const loadWarehouseList = async () => {
  try {
    const res = await getWarehouse({ page: 1, size: 500 })
    const records = Array.isArray(res.data) ? res.data : (res.data.records || [])
    warehouseList.value = records
  } catch (e) {
    console.error('获取库位列表失败', e)
  }
}

const findWarehouseNameById = (id) => {
  if (!id) return ''
  const w = warehouseList.value.find(
      w => w.warehouseId === id || w.id === id
  )
  return w ? w.warehouseName : ''
}

// 打开弹窗：从 task.semiRecords / suggestedSemiRecords 里拷贝一份到 semiEditRecords
const openSemiEditDialog = (task) => {
  semiEditTask.value = task
  let records = []

  if (Array.isArray(task.semiRecords) && task.semiRecords.length) {
    records = task.semiRecords.map(r => ({
      semiProductId: r.semiProductId || null,
      productName: r.productName || '',
      warehouseId: r.warehouseId || null,
      warehouseName: findWarehouseNameById(r.warehouseId),
      quantity: r.quantity ?? '',
      unit: String(r.unit ?? '1'),
      productionDate: r.productionDate || '',
      useAssay: !!r.useAssay
    }))
  } else if (task.suggestedSemiRecords && task.suggestedSemiRecords.length) {
    records = task.suggestedSemiRecords.map(s => ({
      semiProductId: s.semiProductId || null,
      productName: s.productName || '',
      warehouseId: s.warehouseId || null,
      warehouseName: findWarehouseNameById(s.warehouseId),
      quantity: s.quantity ?? '',
      unit: String(s.unit ?? '1'),
      productionDate: s.productionDate || '',
      useAssay: !!s.useAssay
    }))
  }

  if (!records.length) {
    records.push({
      semiProductId: null,
      productName: '',
      warehouseId: null,
      warehouseName: '',
      quantity: '',
      unit: '0',
      productionDate: '',
      useAssay: false
    })
  }

  semiEditRecords.value = JSON.parse(JSON.stringify(records))
  semiEditVisible.value = true
}

const addSemiEditRecord = () => {
  semiEditRecords.value.push({
    semiProductId: null,
    productName: '',
    warehouseId: null,
    warehouseName: '',
    quantity: '',
    unit: '0',
    productionDate: '',
    useAssay: false
  })
}

const removeSemiEditRecord = (index) => {
  semiEditRecords.value.splice(index, 1)
}

const onSemiProductChange = (item) => {
  const found = semiProductList.value.find(p => p.productId === item.semiProductId)
  if (found) {
    item.productName = found.productName
  }
}

// 只能选一个“套用化验”
const handleSemiUseAssay = (index) => {
  semiEditRecords.value.forEach((r, i) => {
    if (i !== index) r.useAssay = false
  })
}

// 弹窗“确定”：把编辑结果写回对应 task 的 semiRecords，并让 expand 立刻刷新
const confirmSemiEdit = () => {
  if (!semiEditTask.value) return

  for (const r of semiEditRecords.value) {
    if (!r.semiProductId) {
      ElMessage.error('请完整填写半成品名称')
      return
    }
    if (!r.warehouseId) {
      ElMessage.error('请选择库位')
      return
    }
  }

  const newRecords = semiEditRecords.value.map(r => ({
    semiProductId: r.semiProductId,
    productName: r.productName,
    productionDate: r.productionDate || null,
    warehouseId: r.warehouseId,
    quantity: r.quantity ? Number(r.quantity) : null,
    unit: r.unit,
    useAssay: !!r.useAssay
  }))

  console.log('newRecords:', newRecords)

  const taskId = semiEditTask.value.taskId
  console.log('taskId:', taskId)
  const idx = taskList.value.findIndex(t => t.taskId === taskId)
  console.log('idx:', idx)
  if (idx !== -1) {
    const old = taskList.value[idx]
    taskList.value[idx] = { ...old, semiRecords: newRecords }
    console.log('taskList[idx]:', taskList.value[idx])
    semiEditTask.value = taskList.value[idx]
    console.log('semiEditTask:', semiEditTask.value)
  }

  semiEditVisible.value = false
  ElMessage.success('关联半成品已更新')
}

// ---------- 计算属性：过滤后的任务列表 ----------
const filteredTasks = computed(() => {
  return taskList.value.filter((t) => {
    if (filterForm.value.type !== 'ALL' && t.type !== filterForm.value.type) {
      return false
    }
    if (filterForm.value.risk !== 'ALL' && t.riskLevel !== filterForm.value.risk) {
      return false
    }
    return true
  })
})

const semiTasks = computed(() => taskList.value.filter(t => t.type === 'SEMI_PRODUCT'))

const semiTaskSummary = computed(() => {
  const tasks = semiTasks.value
  const requiredQr = tasks.reduce((sum, item) => sum + Number(item.requiredQrCount || 0), 0)
  const availableQr = tasks.reduce((sum, item) => sum + Number(item.availableQrCount || 0), 0)
  const risk = tasks.filter(item => item.riskLevel && item.riskLevel !== 'GREEN').length
  const blocked = tasks.filter(item => !item.canAutoStockIn).length
  const needConfirm = tasks.filter(item =>
      !item.semiProductId
      || !item.entryDate
      || !item.semiWarehouseName
      || !item.side
      || (Number(item.semiBoardQuantity || 0) <= 0 && Number(item.semiPieceQuantity || 0) <= 0)
  ).length
  return {
    total: tasks.length,
    ready: tasks.filter(item => item.canAutoStockIn).length,
    needConfirm,
    risk,
    blocked,
    requiredQr,
    availableQr
  }
})

// -------- 产品级联选择数据 --------
const semiProductList = ref([])
const stProductList = ref([])

const cascaderProps = {
  emitPath: false,
  label: 'label',
  value: 'value',
  children: 'children',
  expandTrigger: 'hover'
}

const productOptions = computed(() => {
  const combined = [
    ...stProductList.value.map(p => ({ ...p, category: '成品' })),
    ...semiProductList.value.map(p => ({ ...p, category: '半成品' }))
  ]

  const categoryMap = {}

  combined.forEach(p => {
    const catKey = p.category
    if (!categoryMap[catKey]) {
      categoryMap[catKey] = {
        value: catKey,
        label: catKey,
        children: {}
      }
    }
    const catNode = categoryMap[catKey]

    const typeKey = p.productType || '未分类'
    if (!catNode.children[typeKey]) {
      catNode.children[typeKey] = {
        value: typeKey,
        label: typeKey,
        children: []
      }
    }
    const typeNode = catNode.children[typeKey]

    typeNode.children.push({
      value: p.productId,
      label: p.productName
    })
  })

  return Object.values(categoryMap).map(cat => ({
    ...cat,
    children: Object.values(cat.children)
  }))
})

const semiProductOptions = computed(() =>
    productOptions.value.filter(o => o.label === '半成品')
)

const finishedProductOptions = computed(() =>
    productOptions.value.filter(o => o.label === '成品')
)

const activeMaterialHints = computed(() => finishFlow.value.materialHints.filter(item => !item.skipped))

const autoMatchedMaterialHints = computed(() => activeMaterialHints.value.filter(item => ['AUTO_MATCHED', 'PICKED'].includes(item.matchStatus)))

const shortageMaterialHints = computed(() => activeMaterialHints.value.filter(item => item.matchStatus === 'SHORTAGE'))

const pendingMaterialHints = computed(() => activeMaterialHints.value.filter(item =>
    !['AUTO_MATCHED', 'PICKED', 'SKIPPED'].includes(item.matchStatus)
))

const materialHintGroups = computed(() => {
  const groups = new Map()
  finishFlow.value.materialHints.forEach(item => {
    const key = item.productId || item.productName || item.materialNameRaw || '未匹配半成品'
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        name: item.productName || item.materialNameRaw || '未匹配半成品',
        items: []
      })
    }
    groups.get(key).items.push(item)
  })
  return Array.from(groups.values())
})

const finishDraftSummary = computed(() => {
  const outputCount = finishFlow.value.finishOutputItems.length
  const materialCount = activeMaterialHints.value.length
  const unmatchedCount = finishFlow.value.unmatchedNames.length
  const autoMatchedCount = autoMatchedMaterialHints.value.length
  const shortageCount = shortageMaterialHints.value.length
  const materialNeedConfirm = pendingMaterialHints.value.length
  const notMatched = finishFlow.value.finishOutputItems.filter(item => !item.productId).length
      + activeMaterialHints.value.filter(item => !item.productId).length
  const noDate = finishFlow.value.finishOutputItems.filter(item => !item.productionDate).length
      + activeMaterialHints.value.filter(item => !item.productionDate).length
  const noPreprint = finishFlow.value.selectedOrder && !hasSelectedOrderPreprint(finishFlow.value.selectedOrder) ? 1 : 0
  const riskCount = unmatchedCount + notMatched + noDate + materialNeedConfirm + noPreprint
  return {
    outputCount,
    materialCount,
    autoMatchedCount,
    shortageCount,
    unmatchedCount,
    pendingConfirmCount: materialNeedConfirm,
    riskCount,
    pendingMaterialText: materialNeedConfirm
        ? `${materialNeedConfirm} 条半成品用料需要选择实际库存二维码`
        : '半成品用料已处理或无需补录',
    preprintText: finishFlow.value.selectedOrder
        ? (noPreprint ? '当前订单未看到预打印记录，需先预打印订单码。' : '当前订单已有预打印记录，可继续确认实际产出。')
        : '尚未关联生产订单，无法判断预打印状态。'
  }
})

const finishRiskItems = computed(() => {
  const items = []
  finishFlow.value.unmatchedNames.forEach((name, index) => {
    items.push({
      key: `unmatched-${index}-${name}`,
      label: '未识别内容',
      type: 'warning',
      text: `未能识别或匹配：${name}`
    })
  })
  finishFlow.value.finishOutputItems.forEach((item, index) => {
    const name = item.productName || item.productNameRaw || `第 ${index + 1} 条成品产出`
    if (!item.productId) {
      items.push({
        key: `output-product-${index}`,
        label: '成品待确认',
        type: 'danger',
        text: `${name} 未匹配到成品产品，请确认产品。`
      })
    }
    if (!item.productionDate) {
      items.push({
        key: `output-date-${index}`,
        label: '日期待确认',
        type: 'warning',
        text: `${name} 缺少生产日期，请确认日期。`
      })
    }
  })
  activeMaterialHints.value.forEach((hint, index) => {
    const name = hint.productName || hint.materialNameRaw || `第 ${index + 1} 条半成品用料`
    if (!hint.productId) {
      items.push({
        key: `material-product-${hint.key || index}`,
        label: '用料待确认',
        type: 'danger',
        text: `${name} 未匹配到半成品产品，请选择产品。`
      })
    }
    if (!hint.productionDate) {
      items.push({
        key: `material-date-${hint.key || index}`,
        label: '日期待确认',
        type: 'warning',
        text: `${name} 缺少生产日期，请确认日期后查询库存。`
      })
    }
    if (hint.matchStatus === 'SHORTAGE') {
      items.push({
        key: `material-shortage-${hint.key || index}`,
        label: '库存不足',
        type: 'danger',
        text: hint.candidateRisk || `${name} 候选库存不足，请调整条件或人工处理。`
      })
    } else if (!['AUTO_MATCHED', 'PICKED', 'SKIPPED'].includes(hint.matchStatus)) {
      items.push({
        key: `material-pending-${hint.key || index}`,
        label: '用料待确认',
        type: 'warning',
        text: hint.candidateRisk || `${name} 需要选择实际库存二维码。`
      })
    }
  })
  if (finishFlow.value.selectedOrder && !hasSelectedOrderPreprint(finishFlow.value.selectedOrder)) {
    items.push({
      key: 'preprint-missing',
      label: '待预打印',
      type: 'warning',
      text: '当前生产订单未看到预打印记录，需先预打印订单码。'
    })
  }
  return items
})

const finishRecommendation = computed(() => {
  if (finishFlow.value.selectedOrder) {
    return {
      mode: 'EXISTING',
      title: `关联生产订单 ${finishFlow.value.selectedOrder.orderNo}`,
      detail: '系统会把半成品领用和实际产出保存到该订单，后续通过预打印标签生成待入库任务。'
    }
  }
  const best = recommendedFinishOrders.value[0]
  if (best && best.matchScore >= 2) {
    return {
      mode: 'EXISTING',
      order: best,
      title: `推荐关联 ${best.orderNo}`,
      detail: best.matchReason
    }
  }
  return {
    mode: 'CREATE',
    title: '推荐新建生产订单',
    detail: '未找到高匹配订单，系统将用识别结果自动填入计划产出和计划领用。'
  }
})

const hasMaterialPickConfirmed = computed(() =>
    activeMaterialHints.value.length === 0 || activeMaterialHints.value.every(item => item.pickConfirmed || item.skipped)
)

const requiredOutputLabelCount = computed(() =>
    finishFlow.value.finishOutputItems.reduce((sum, item) => sum + Number(estimatedLabelCount(item) || 0), 0)
)

const selectedOrderReservedLabelCount = computed(() => {
  const order = finishFlow.value.selectedOrder
  if (!order || !Array.isArray(order.labelBatches)) return 0
  return order.labelBatches.reduce((sum, item) => sum + Number(item.reservedCount || 0), 0)
})

const finishPrimaryAction = computed(() => {
  if (!finishFlow.value.orderId) {
    return {
      label: '创建生产订单并保存草稿',
      description: finishRecommendation.value.mode === 'EXISTING'
          ? '已找到推荐订单，也可以直接点击推荐卡关联；主操作会在没有关联订单时新建订单。'
          : '系统将用识别结果自动创建生产订单，并保存计划产出和计划领用。',
      loading: loadingCreateFinishOrder.value,
      disabled: !finishFlow.value.finishOutputItems.length,
      action: 'CREATE_ORDER'
    }
  }
  if (!hasMaterialPickConfirmed.value) {
    return {
      label: '确认半成品领用',
      description: '系统已尽量自动匹配库存二维码，请确认异常项后再执行领用。',
      loading: loadingPickMaterials.value || loadingAutoMatchMaterials.value,
      disabled: !selectedMaterialCandidateIds.value.length || pendingMaterialHints.value.some(item => !item.selectedCandidateIds?.length && !item.skipped),
      action: 'PICK_MATERIALS'
    }
  }
  if (!selectedOrderReservedLabelCount.value) {
    return {
      label: '保存草稿并去预打印',
      description: '当前订单未看到预打印标签，需要先预打印订单码给现场贴码。',
      loading: loadingSaveOutputs.value,
      disabled: false,
      action: 'PREPRINT'
    }
  }
  if (selectedOrderReservedLabelCount.value < requiredOutputLabelCount.value) {
    return {
      label: '保存草稿并去追加预打印',
      description: `预计需要 ${requiredOutputLabelCount.value} 个标签，当前可用 ${selectedOrderReservedLabelCount.value} 个。`,
      loading: loadingSaveOutputs.value,
      disabled: false,
      action: 'APPEND_PREPRINT'
    }
  }
  return {
    label: '确认产出并生成入库任务',
    description: '系统会按已预打印标签顺序登记实际产出，并生成待入库任务。',
    loading: loadingFinishProduction.value,
    disabled: !finishFlow.value.finishOutputItems.length,
    action: 'CONFIRM_OUTPUT'
  }
})

const filteredFinishOrderOptions = computed(() => {
  const q = finishOrderSearch.value
  const orderNo = q.orderNo.trim()
  const productKeyword = q.productKeyword.trim()
  return finishOrderOptions.value.filter(item => {
    if (orderNo && !String(item.orderNo || '').includes(orderNo)) return false
    if (q.productionDate && item.productionDate !== q.productionDate) return false
    if (q.status && item.status !== q.status) return false
    if (productKeyword) {
      const text = [
        item.plannedOutputText,
        item.outputSummaryText,
        item.plannedMaterialText,
        item.materialSummaryText
      ].filter(Boolean).join(' ')
      if (!text.includes(productKeyword)) return false
    }
    return true
  })
})

const recommendedFinishOrders = computed(() => {
  const outputProducts = finishFlow.value.finishOutputItems.map(item => item.productName).filter(Boolean)
  return finishOrderOptions.value
      .map(item => {
        const reasons = []
        if (item.productionDate && item.productionDate === finishFlow.value.createForm.productionDate) {
          reasons.push('生产日期一致')
        }
        if (outputProducts.some(name => (item.plannedOutputText || '').includes(name) || (item.outputSummaryText || '').includes(name))) {
          reasons.push('产品与报数产出匹配')
        }
        if (['ISSUED', 'MATERIALING', 'MATERIALED', 'PREPRINTED'].includes(item.status)) {
          reasons.push('订单仍可处理')
        }
        return {
          ...item,
          matchScore: reasons.length,
          matchReason: reasons.length ? `匹配原因：${reasons.join('、')}` : '匹配原因：近期未完成成品生产订单'
        }
      })
      .sort((a, b) => b.matchScore - a.matchScore || String(b.productionDate || '').localeCompare(String(a.productionDate || '')))
      .slice(0, 4)
})

const loadProductOptions = async () => {
  try {
    const [semiRes, stRes] = await Promise.all([
      getSemiProduct(),
      getStProduct()
    ])
    if (semiRes.code === 200) {
      semiProductList.value = semiRes.data || []
    }
    if (stRes.code === 200) {
      stProductList.value = stRes.data || []
    }
  } catch (e) {
    console.error('加载产品列表失败', e)
  }
}

// ---------- 风险等级 -> Tag 类型 ----------
const riskLevelTagType = (level) => {
  if (level === 'RED') return 'danger'
  if (level === 'YELLOW') return 'warning'
  if (level === 'GREEN') return 'success'
  return 'info'
}

const riskLevelLabel = (level) => {
  if (level === 'RED') return '高'
  if (level === 'YELLOW') return '中'
  if (level === 'GREEN') return '低'
  return '-'
}

// 根据 productId 在级联数据中找到产品名（只是为了回填 row.productName）
const findProductLabelById = (id) => {
  if (!id || !productOptions.value.length) return null

  const dfs = (nodes) => {
    for (const n of nodes) {
      if (n.value === id) return n.label
      if (n.children && n.children.length) {
        const r = dfs(n.children)
        if (r) return r
      }
    }
    return null
  }

  return dfs(productOptions.value)
}

const findProductById = (id) => {
  if (!id) return null
  return [...stProductList.value, ...semiProductList.value].find(item =>
      item.productId === id || item.id === id
  ) || null
}

const productPiecesPerPallet = (productId) => {
  const product = findProductById(productId)
  return Number(product?.piecesPerPallet || product?.pieces_per_pallet || 0)
}

const estimatedLabelCount = (row) => {
  const perPallet = productPiecesPerPallet(row?.productId)
  const boards = Number(row?.boardCount || 0)
  const pieces = Number(row?.pieceCount || 0)
  if (!perPallet || (boards <= 0 && pieces <= 0)) return 0
  return boards + Math.ceil(pieces / perPallet)
}

const hasSelectedOrderPreprint = (order) => {
  if (!order) return false
  return Array.isArray(order.labelBatches) && order.labelBatches.some(item =>
      Number(item.reservedCount || 0) + Number(item.usedCount || 0) + Number(item.recycledCount || 0) > 0
  )
}

// 级联选择变化时，同步写回任务对象里的 productId / semiProductId & 名称
const handleProductChange = (row, value) => {
  const label = findProductLabelById(value) || ''

  if (row.type === 'SEMI_PRODUCT') {
    row.semiProductId = value
    row.semiProductName = label
  } else {
    row.productId = value
    row.productName = label
  }
}

const handleDraftOutputProductChange = (row, value) => {
  row.productName = findProductLabelById(value) || row.productName || ''
}

const handleMaterialHintProductChange = (row, value) => {
  row.productName = findProductLabelById(value) || row.productName || row.materialNameRaw || ''
  clearMaterialHintCandidates(row)
  row.matchStatus = row.productId && row.productionDate ? 'PENDING_QUERY' : 'NEEDS_CONFIRM'
}

const clearMaterialHintCandidates = (row) => {
  row.candidates = []
  row.selectedCandidateIds = []
  row.recommendation = null
  row.candidateRisk = ''
  row.matchStatus = row.productId && row.productionDate ? 'PENDING_QUERY' : 'NEEDS_CONFIRM'
}

const toggleMaterialHintSkipped = (row) => {
  row.skipped = !row.skipped
  if (row.skipped) {
    row.selectedCandidateIds = []
    row.recommendation = null
    row.candidateRisk = '已跳过该条用料提示，不会参与本次领用确认。'
    row.matchStatus = 'SKIPPED'
  } else {
    row.candidateRisk = ''
    row.matchStatus = row.productId && row.productionDate ? 'PENDING_QUERY' : 'NEEDS_CONFIRM'
  }
}

const findMaterialHintIndex = (row) => finishFlow.value.materialHints.findIndex(item => item.key === row?.key)

const toggleHintCandidate = (hint, row, checked) => {
  const id = row.palletCodeId
  if (!id) return
  const set = new Set(hint.selectedCandidateIds || [])
  if (checked) {
    set.add(id)
  } else {
    set.delete(id)
  }
  hint.selectedCandidateIds = Array.from(set)
}

const materialRecommendationPickText = recommendation => materialPickRecommendationPickText(recommendation)

const materialRecommendationReturnText = recommendation => materialPickRecommendationReturnText(recommendation)

const materialRecommendationTypeText = recommendation => ({
  EXACT_PIECE_COMBO: '散件刚好匹配',
  NEAREST_PIECE_OVER: '散件最接近匹配',
  PIECE_PLUS_FULL_PALLET: '散件不足，补整板',
  FULL_ONLY: '仅整板领用',
  INSUFFICIENT: '库存不足'
}[recommendation?.matchType] || '待推荐')

const isRecommendedCandidate = (hint, row) => {
  const id = row?.palletCodeId
  return Boolean(id && materialPickRecommendationIds(hint?.recommendation).includes(id))
}

const buildHintRecommendation = (hint) => buildMaterialPickRecommendation({
  requiredBoardCount: hint?.boardCount || 0,
  requiredPieceCount: hint?.pieceCount || 0,
  piecesPerPallet: productPiecesPerPallet(hint?.productId),
  candidates: hint?.candidates || []
})

const setParseType = (type) => {
  if (parseForm.value.parseType === type) return
  parseForm.value.parseType = type
  batchId.value = ''
  taskList.value = []
  globalRemarks.value = []
  selectedTaskIds.value = []
  finishFlow.value = createEmptyFinishFlow()
  finishStep.value = 1
}

const quantityText = (boards, pieces) => {
  const boardCount = Number(boards || 0)
  const pieceCount = Number(pieces || 0)
  if (boardCount && pieceCount) return `${boardCount}板${pieceCount}件`
  if (boardCount) return `${boardCount}板`
  if (pieceCount) return `${pieceCount}件`
  return '暂无'
}

const orderStatusLabel = value => ({
  ISSUED: '已下发',
  MATERIALING: '领料中',
  MATERIALED: '已领料',
  OUTPUT_BINDING: '产出中',
  PREPRINTED: '已预打印',
  WAIT_INBOUND: '待入库',
  PART_INBOUND: '部分入库',
  COMPLETED: '已完成',
  CANCELED: '已取消'
}[value] || value || '暂无')

const orderStatusTagType = value => ({
  ISSUED: 'primary',
  MATERIALING: 'warning',
  MATERIALED: 'success',
  PREPRINTED: 'warning',
  WAIT_INBOUND: 'primary',
  PART_INBOUND: 'warning',
  COMPLETED: 'success',
  CANCELED: 'info'
}[value] || 'info')

const orderOptionLabel = item => `${item.orderNo} / 成品生产订单 / ${item.productionDate || '-'} / ${orderStatusLabel(item.status)}`

const candidateLocationText = row => {
  const location = [row.warehouseName, row.side, row.rowNumber ? `${row.rowNumber}排` : '', row.layer ? `${row.layer}层` : '']
      .filter(Boolean)
      .join(' ')
  return location || '暂无'
}

const normalizeOutputItem = (task, index) => ({
  key: `output-${task.taskId || index}`,
  taskId: task.taskId,
  productId: task.productId || task._productId,
  productName: task.productName || findProductLabelById(task.productId || task._productId) || '未匹配成品',
  productionDate: task.entryDate || parseForm.value.entryDate || today,
  boardCount: Number(task.finishedBoardQuantity || 0),
  pieceCount: Number(task.finishedPieceQuantity || 0),
  rawBlock: task.rawBlock || task.sourceText || '',
  saved: false
})

const normalizeMaterialHints = (tasks) => {
  const hints = []
  tasks.forEach((task, taskIndex) => {
    const items = Array.isArray(task.productionConsumptionItems) ? task.productionConsumptionItems : []
    items.forEach((item, itemIndex) => {
      const entries = Array.isArray(item.items) && item.items.length ? item.items : [{}]
      entries.forEach((entry, entryIndex) => {
        hints.push({
          key: `hint-${task.taskId || taskIndex}-${itemIndex}-${entryIndex}`,
          taskId: task.taskId,
          productId: item.productId,
          productName: item.productName,
          materialNameRaw: item.materialNameRaw,
          productionDate: entry.productionDate || item.productionDate || '',
          boardCount: Number(entry.boardCount || 0),
          pieceCount: Number(entry.pieceCount || 0),
          quantityText: entry.quantityText || '',
          warehouseHint: item.warehouseHint || '',
          candidates: [],
          selectedCandidateIds: [],
          recommendation: null,
          skipped: false,
          candidateRisk: '',
          matchStatus: item.productId && entry.productionDate ? 'PENDING_QUERY' : 'NEEDS_CONFIRM',
          pickConfirmed: false,
          loading: false
        })
      })
    })
  })
  return hints
}

const distinctNames = names => Array.from(new Set((names || []).filter(Boolean)))

const initFinishFlow = () => {
  const tasks = taskList.value.filter(t => t.type === 'FINISHED_PRODUCT')
  finishFlow.value = {
    ...createEmptyFinishFlow(),
    rawText: parseForm.value.rawText || tasks.map(t => t.rawBlock || t.sourceText || '').filter(Boolean).join('\n'),
    finishOutputItems: tasks.map(normalizeOutputItem),
    materialHints: normalizeMaterialHints(tasks),
    unmatchedNames: distinctNames(tasks.flatMap(t => t.unmatchedNames || [])),
    createForm: {
      productionDate: parseForm.value.entryDate || today,
      remark: '智能报数新建成品生产订单'
    }
  }
  finishStep.value = 1
  loadFinishOrderOptions()
}

const applyBatchResponse = (data = {}) => {
  batchId.value = data.batchId || batchId.value || ''
  globalRemarks.value = data.globalRemarks || []

  const rawTasks = data.tasks || []
  const firstTaskType = rawTasks.find(t => t.type)?.type
  if (firstTaskType === 'SEMI_PRODUCT' || firstTaskType === 'FINISHED_PRODUCT') {
    parseForm.value.parseType = firstTaskType
  }
  taskList.value = rawTasks.map(t => {
    const _productId = t.type === 'SEMI_PRODUCT' ? t.semiProductId : t.productId
    return {
      ...t,
      _productId,
      productionConsumptionItems: t.productionConsumptionItems || [],
      productionConsumptionResults: t.productionConsumptionResults || [],
      unmatchedNames: t.unmatchedNames || []
    }
  })

  selectedTaskIds.value = []
  if (parseForm.value.parseType === 'FINISHED_PRODUCT') {
    initFinishFlow()
  } else {
    finishFlow.value = createEmptyFinishFlow()
    finishStep.value = 1
  }
}

const summarizeLabelBatches = batches => {
  const reserved = (batches || []).reduce((sum, item) => sum + Number(item.reservedCount || 0), 0)
  const used = (batches || []).reduce((sum, item) => sum + Number(item.usedCount || 0), 0)
  const recycled = (batches || []).reduce((sum, item) => sum + Number(item.recycledCount || 0), 0)
  if (!reserved && !used && !recycled) return ''
  return `${reserved}预留 / ${used}已用 / ${recycled}回收`
}

const summarizeInboundProgress = outputs => {
  const inbound = (outputs || []).reduce((sum, item) => sum + Number(item.inboundQrCount || 0), 0)
  const total = (outputs || []).reduce((sum, item) => sum + Number(item.boundQrCount || item.requiredQrCount || 0), 0)
  return total ? `${inbound}/${total}` : ''
}

const decorateOrderOptionWithDetail = (option, detail) => {
  const base = detail?.baseInfo || {}
  const materials = detail?.materials || []
  const outputs = detail?.outputs || []
  const labelBatches = detail?.labelBatches || []
  return {
    ...option,
    ...base,
    materials,
    outputs,
    labelBatches,
    materialCount: materials.length,
    plannedMaterialText: base.plannedMaterialText || option.plannedMaterialText || '',
    plannedOutputText: base.plannedOutputText || option.plannedOutputText || '',
    actualMaterialText: materials.length ? `${materials.length} 项` : '',
    materialSummaryText: materials.length ? `已领用 ${materials.length} 项` : '尚未领用',
    outputSummaryText: outputs.map(item => item.productName).filter(Boolean).join('、'),
    labelReservedText: summarizeLabelBatches(labelBatches),
    inboundProgressText: summarizeInboundProgress(outputs)
  }
}

const loadFinishOrderOptions = async () => {
  loadingFinishOrders.value = true
  try {
    const res = await listProductionOrderOptions({ orderType: 'FINISH' })
    const options = res.data || []
    finishOrderOptions.value = options
    const detailed = await Promise.all(options.slice(0, 30).map(async item => {
      try {
        const detailRes = await getProductionOrderDetail(item.id)
        return decorateOrderOptionWithDetail(item, detailRes.data || {})
      } catch (error) {
        return item
      }
    }))
    const detailedMap = new Map(detailed.map(item => [item.id, item]))
    finishOrderOptions.value = options.map(item => detailedMap.get(item.id) || item)
  } catch (e) {
    console.error('加载成品生产订单失败', e)
  } finally {
    loadingFinishOrders.value = false
  }
}

const loadSelectedFinishOrder = async () => {
  if (!finishFlow.value.orderId) {
    ElMessage.warning('请先选择成品生产订单')
    return
  }
  loadingFinishDetail.value = true
  try {
    const res = await getProductionOrderDetail(finishFlow.value.orderId)
    finishFlow.value.selectedOrder = res.data?.baseInfo || null
    if (finishFlow.value.selectedOrder) {
      finishFlow.value.selectedOrder.materials = res.data?.materials || []
      finishFlow.value.selectedOrder.outputs = res.data?.outputs || []
      finishFlow.value.selectedOrder.labelBatches = res.data?.labelBatches || []
    }
  } finally {
    loadingFinishDetail.value = false
  }
}

const handleFinishOrderSelected = async () => {
  await loadSelectedFinishOrder()
  await autoMatchMaterialCandidates()
}

const selectRecommendedOrder = async (item) => {
  finishFlow.value.orderMode = 'EXISTING'
  finishFlow.value.orderId = item.id
  finishFlow.value.selectedOrder = item
  await loadSelectedFinishOrder()
  await autoMatchMaterialCandidates()
}

const useRecommendedFinishPlan = async () => {
  if (finishFlow.value.orderId) {
    await loadSelectedFinishOrder()
    await autoMatchMaterialCandidates()
    finishStep.value = 3
    return
  }
  if (finishRecommendation.value.mode === 'EXISTING' && finishRecommendation.value.order) {
    await selectRecommendedOrder(finishRecommendation.value.order)
    finishStep.value = 3
    return
  }
  finishFlow.value.orderMode = 'CREATE'
  await createFinishProductionOrder()
  if (finishFlow.value.orderId) {
    finishStep.value = 3
  }
}

const autoMatchMaterialCandidates = async () => {
  if (!finishFlow.value.orderId || !activeMaterialHints.value.length) return
  loadingAutoMatchMaterials.value = true
  try {
    for (let i = 0; i < finishFlow.value.materialHints.length; i += 1) {
      const hint = finishFlow.value.materialHints[i]
      if (!hint || hint.skipped) continue
      if (!hint.productId || !hint.productionDate) {
        hint.matchStatus = 'NEEDS_CONFIRM'
        hint.candidateRisk = !hint.productId ? '产品待确认，无法自动查询库存。' : '日期待确认，需确认日期后查询库存。'
        continue
      }
      await loadMaterialCandidatesForHint(i)
    }
  } finally {
    loadingAutoMatchMaterials.value = false
    if (pendingMaterialHints.value.length) {
      finishOpenPanels.value = ['pending']
    } else {
      finishOpenPanels.value = ['matched']
    }
  }
}

const createFinishProductionOrder = async () => {
  if (!finishFlow.value.createForm.productionDate) {
    ElMessage.warning('请选择生产日期')
    return
  }
  loadingCreateFinishOrder.value = true
  try {
    const payload = {
      orderType: 'FINISH',
      productionDate: finishFlow.value.createForm.productionDate,
      plannedMaterialJson: activeMaterialHints.value.map(item => ({
        productId: item.productId,
        productName: item.productName || item.materialNameRaw,
        productionDate: item.productionDate || null,
        boardCount: item.boardCount || 0,
        pieceCount: item.pieceCount || 0,
        quantityText: item.quantityText || quantityText(item.boardCount, item.pieceCount)
      })),
      plannedOutputJson: finishFlow.value.finishOutputItems.map(item => ({
        productId: item.productId,
        productName: item.productName,
        productionDate: item.productionDate || finishFlow.value.createForm.productionDate,
        boardCount: item.boardCount || 0,
        pieceCount: item.pieceCount || 0
      })),
      remark: finishFlow.value.createForm.remark
    }
    const res = await createProductionOrder(payload)
    finishFlow.value.orderId = res.data?.id
    finishFlow.value.orderMode = 'EXISTING'
    await loadFinishOrderOptions()
    await loadSelectedFinishOrder()
    await autoMatchMaterialCandidates()
    ElMessage.success('生产订单已创建')
  } finally {
    loadingCreateFinishOrder.value = false
  }
}

const enterFinishMaterialStep = async () => {
  if (!finishFlow.value.orderId) {
    ElMessage.warning('请先选择或新建成品生产订单')
    return
  }
  if (!finishFlow.value.selectedOrder) {
    await loadSelectedFinishOrder()
  }
  finishStep.value = 3
  for (let i = 0; i < finishFlow.value.materialHints.length; i += 1) {
    const hint = finishFlow.value.materialHints[i]
    if (hint.productId && !hint.candidates.length) {
      await loadMaterialCandidatesForHint(i)
    }
  }
}

const loadMaterialCandidatesForHint = async (index) => {
  const hint = finishFlow.value.materialHints[index]
  if (!hint || !finishFlow.value.orderId) return
  if (hint.skipped) {
    ElMessage.warning('该条用料提示已跳过')
    return
  }
  if (!hint.productId) {
    ElMessage.warning('该半成品提示未匹配到产品，无法查询库存候选二维码')
    return
  }
  hint.candidateRisk = ''
  hint.loading = true
  try {
    const res = await pageMaterialCandidates(finishFlow.value.orderId, {
      productId: hint.productId,
      productionDate: hint.productionDate || undefined,
      page: 1,
      size: 100
    })
    hint.candidates = res.data?.records || []
    const recommendation = buildHintRecommendation(hint)
    hint.recommendation = recommendation
    const selectedIds = materialPickRecommendationIds(recommendation)
    if (recommendation.selectable && selectedIds.length) {
      hint.selectedCandidateIds = selectedIds
      const returnText = materialRecommendationReturnText(recommendation)
      hint.candidateRisk = `${materialRecommendationTypeText(recommendation)}：已推荐 ${selectedIds.length} 个二维码。预计退回：${returnText}。请确认后再领用。`
      hint.matchStatus = 'AUTO_MATCHED'
    } else if (!recommendation.selectable) {
      hint.selectedCandidateIds = []
      hint.candidateRisk = recommendation.reason || '候选库存不足，无法生成完整领用推荐。'
      hint.matchStatus = 'SHORTAGE'
    } else if (hint.candidates.length) {
      hint.candidateRisk = `已找到 ${hint.candidates.length} 个候选二维码，请选择实际领用项。`
      hint.matchStatus = 'MULTIPLE'
    } else {
      hint.selectedCandidateIds = []
      hint.candidateRisk = '未找到符合条件的库存二维码，可调整产品、日期或数量后重新查询。'
      hint.matchStatus = 'SHORTAGE'
    }
  } finally {
    hint.loading = false
  }
}

const handleHintSelectionChange = (index, rows) => {
  const hint = finishFlow.value.materialHints[index]
  if (!hint) return
  hint.selectedCandidateIds = rows.map(row => row.palletCodeId).filter(Boolean)
}

const confirmFinishMaterialPick = async () => {
  if (!finishFlow.value.orderId) {
    ElMessage.warning('请先选择生产订单')
    return
  }
  const ids = selectedMaterialCandidateIds.value
  if (!ids.length) {
    ElMessage.warning('请先选择要领用的半成品二维码')
    return
  }
  await ElMessageBox.confirm(
      `确认领用选中的 ${ids.length} 个半成品二维码？领用后库存移出、二维码释放，如需撤销需走人工退料或重新入库流程。`,
      '确认领用半成品',
      {
        confirmButtonText: '确认领用',
        cancelButtonText: '取消',
        type: 'warning'
      }
  )
  loadingPickMaterials.value = true
  try {
    await pickProductionMaterials(finishFlow.value.orderId, {
      palletCodeIds: ids,
      remark: '智能报数成品生产订单补录领用'
    })
    finishFlow.value.materialHints.forEach(item => {
      if ((item.selectedCandidateIds || []).length) {
        item.pickConfirmed = true
        item.matchStatus = 'PICKED'
      }
    })
    await loadSelectedFinishOrder()
    ElMessage.success('半成品领用已确认')
  } finally {
    loadingPickMaterials.value = false
  }
}

const validateFinishOutputItems = () => {
  if (!finishFlow.value.orderId) {
    ElMessage.warning('请先选择生产订单')
    return false
  }
  const invalid = finishFlow.value.finishOutputItems.find(item => !item.productId || !item.productionDate)
  if (invalid) {
    ElMessage.warning('存在未匹配产品或生产日期的成品产出，无法登记')
    return false
  }
  if (!finishFlow.value.finishOutputItems.length) {
    ElMessage.warning('没有可保存的成品产出')
    return false
  }
  return true
}

const saveFinishOutputsToOrder = async () => {
  loadingSaveOutputs.value = true
  try {
    if (!validateFinishOutputItems()) return
    finishFlow.value.finishOutputItems.forEach(item => {
      item.saved = true
    })
    ElMessage.success('实际产出已暂存。本页不会创建入库任务，确认产出时才会提交。')
  } finally {
    loadingSaveOutputs.value = false
  }
}

const confirmFinishProduction = async () => {
  if (!validateFinishOutputItems()) return
  await ElMessageBox.confirm(
      '确认实际产出后，系统会按预打印订单码顺序登记使用情况，未使用标签将回收，并为已使用标签生成待入库任务。该操作不能按旧贴码流程撤销。',
      '确认产出并生成入库任务',
      {
        confirmButtonText: '确认产出',
        cancelButtonText: '取消',
        type: 'warning'
      }
  )
  loadingFinishProduction.value = true
  try {
    const payload = {
      items: finishFlow.value.finishOutputItems.map(item => ({
        productId: item.productId,
        productionDate: item.productionDate,
        boardCount: item.boardCount || 0,
        pieceCount: item.pieceCount || 0
      })),
      remark: '智能报数确认实际产出'
    }
    const res = await finishProductionOrder(finishFlow.value.orderId, payload)
    if (res.data?.baseInfo) {
      finishFlow.value.selectedOrder = res.data.baseInfo
      finishFlow.value.selectedOrder.materials = res.data.materials || []
      finishFlow.value.selectedOrder.outputs = res.data.outputs || []
    }
    finishFlow.value.finishOutputItems.forEach(item => {
      item.saved = true
    })
    await loadSelectedFinishOrder()
    ElMessage.success('实际产出已确认，待入库任务已生成')
  } finally {
    loadingFinishProduction.value = false
  }
}

const goPreprintOrderCodes = () => {
  if (!finishFlow.value.orderId) {
    ElMessage.warning('请先选择生产订单')
    return
  }
  router.push({
    path: '/production/output-bind',
    query: { orderId: finishFlow.value.orderId }
  })
}

const handleFinishPrimaryAction = async () => {
  const action = finishPrimaryAction.value.action
  if (action === 'CREATE_ORDER') {
    finishFlow.value.orderMode = 'CREATE'
    await createFinishProductionOrder()
    return
  }
  if (action === 'PICK_MATERIALS') {
    await confirmFinishMaterialPick()
    return
  }
  if (action === 'PREPRINT' || action === 'APPEND_PREPRINT') {
    await saveFinishOutputsToOrder()
    goPreprintOrderCodes()
    return
  }
  if (action === 'CONFIRM_OUTPUT') {
    await confirmFinishProduction()
  }
}

const loadHistoryOptions = async (selectLatest = false) => {
  loadingHistory.value = true
  try {
    const res = await listAutoInboundHistory()
    historyOptions.value = res.data || []
    if (selectLatest && filteredHistoryOptions.value.length) {
      batchId.value = filteredHistoryOptions.value[0].batchId
      await handleReloadBatch()
    }
  } catch (e) {
    console.error('加载报数解析历史失败', e)
  } finally {
    loadingHistory.value = false
  }
}

const handleHistoryChange = async (value) => {
  if (!value) {
    taskList.value = []
    globalRemarks.value = []
    selectedTaskIds.value = []
    finishFlow.value = createEmptyFinishFlow()
    finishStep.value = 1
    return
  }
  await handleReloadBatch()
}

// ---------- 解析报数 ----------
const handleParse = async () => {
  if (!parseForm.value.rawText || !parseForm.value.rawText.trim()) {
    ElMessage.warning('请先粘贴原始报数文本')
    return
  }

  if (!parseForm.value.entryDate) {
    parseForm.value.entryDate = today
  }

  const payload = {
    rawText: parseForm.value.rawText,
    entryDate: parseForm.value.entryDate,
    parseType: parseForm.value.parseType
  }

  loadingParse.value = true
  try {
    const res = await parseAutoInbound(payload)
    applyBatchResponse(res.data)
    await loadHistoryOptions(false)
    ElMessage.success('解析成功')
  } catch (e) {
    console.error(e)
  } finally {
    loadingParse.value = false
  }
}

// ---------- 从批次重新加载 ----------
const handleReloadBatch = async () => {
  if (!batchId.value) return
  loadingBatch.value = true
  try {
    const res = await getAutoInboundBatch(batchId.value)
    applyBatchResponse(res.data)
    ElMessage.success('已从服务器重新加载该批次')
  } catch (e) {
    console.error(e)
  } finally {
    loadingBatch.value = false
  }
}

// ---------- 清空解析表单 ----------
const handleResetParse = () => {
  parseForm.value = {
    entryDate: today,
    parseType: 'SEMI_PRODUCT',
    rawText: ''
  }
  batchId.value = ''
  taskList.value = []
  globalRemarks.value = []
  selectedTaskIds.value = []
  finishFlow.value = createEmptyFinishFlow()
  finishStep.value = 1
}

// ---------- 表格勾选 ----------
const handleSelectionChange = (rows) => {
  selectedTaskIds.value = rows.map((r) => r.taskId)
}

const openTaskDetail = (row) => {
  detailTask.value = row
  detailDrawerVisible.value = true
}

const openConsumptionDialog = (row) => {
  consumptionTask.value = row
  consumptionDialogVisible.value = true
}

const formatExecutionStatus = (item) => {
  if (item.status === 'SUCCESS') return item.code || '已入库'
  if (item.status === 'FAILED') return '失败'
  return item.code || '待执行'
}

const getDetailTypeLabel = (task) => {
  return task?.type === 'SEMI_PRODUCT' ? '半成品' : '成品'
}

const getDetailProductName = (task) => {
  if (!task) return '-'
  return task.type === 'SEMI_PRODUCT'
      ? (task.semiProductName || '-')
      : (task.productName || '-')
}

const getDetailWarehouseName = (task) => {
  if (!task) return '-'
  return task.type === 'SEMI_PRODUCT'
      ? (task.semiWarehouseName || '-')
      : (task.warehouseName || '-')
}

const getDetailQuantityText = (task) => {
  if (!task) return '-'
  const boardQuantity = task.type === 'SEMI_PRODUCT'
      ? (task.semiBoardQuantity ?? 0)
      : (task.finishedBoardQuantity ?? 0)
  const pieceQuantity = task.type === 'SEMI_PRODUCT'
      ? (task.semiPieceQuantity ?? 0)
      : (task.finishedPieceQuantity ?? 0)
  return `${boardQuantity}板 / ${pieceQuantity}件`
}

const consumptionRows = computed(() => {
  const task = consumptionTask.value
  if (!task || !Array.isArray(task.productionConsumptionItems)) return []
  const resultText = Array.isArray(task.productionConsumptionResults) && task.productionConsumptionResults.length
      ? task.productionConsumptionResults.join('；')
      : '待保存到生产订单后，由用户确认实际领用二维码'
  return task.productionConsumptionItems.flatMap(item => {
    const materialName = item.materialNameRaw || item.productName || '未识别原料'
    const entries = item.items || []
    if (!entries.length) {
      return [{
        materialName,
        productionDate: '-',
        quantityText: '数量未识别',
        result: resultText
      }]
    }
    return entries.map(entry => ({
      materialName,
      productionDate: entry.productionDate || '-',
      quantityText: entry.quantityText || `${entry.boardCount || 0}板${entry.pieceCount || 0}件`,
      result: resultText
    }))
  })
})

// ---------- 确认入库 ----------
const handleConfirm = async () => {
  if (!selectedTaskIds.value.length) {
    ElMessage.warning('请至少选择一条任务')
    return
  }

  if (!operatorId.value) {
    ElMessage.error('无法获取当前用户ID，请重新登录后再试')
    return
  }

  try {
    const selectedTasks = taskList.value.filter((t) =>
        selectedTaskIds.value.includes(t.taskId)
    )
    const hasConsumptionArchive = selectedTasks.some(t =>
        Array.isArray(t.productionConsumptionItems) && t.productionConsumptionItems.length
    )
    const confirmMessage = hasConsumptionArchive
        ? `确认对选中的 ${selectedTaskIds.value.length} 条任务执行入库操作？\n\n本次报数包含半成品领用提示。后续应保存到生产订单，由用户确认实际领用二维码；未匹配部分仅留档提示。`
        : `确认对选中的 ${selectedTaskIds.value.length} 条任务执行入库操作？`
    await ElMessageBox.confirm(
        confirmMessage,
        '确认入库',
        {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )
  } catch {
    ElMessage.info('已取消入库操作')
    return
  }

  const updatedTasks = taskList.value.filter((t) =>
      selectedTaskIds.value.includes(t.taskId)
  )
  console.log('updatedTasks:', updatedTasks)

  const payload = {
    operatorId: operatorId.value,
    confirmedTaskIds: selectedTaskIds.value,
    updatedTasks
  }
  console.log('payload:', payload)
  loadingConfirm.value = true
  try {
    const res = await confirmAutoInbound(batchId.value, payload)
    applyBatchResponse(res.data)
    await loadHistoryOptions(false)
    ElMessage.success('入库成功')
  } catch (e) {
    console.error(e)
  } finally {
    loadingConfirm.value = false
  }
}

const formatConsumptionArchive = (row) => {
  if (!row.productionConsumptionItems || !row.productionConsumptionItems.length) {
    return '无生产消耗信息'
  }
  const lines = ['半成品领用提示将迁移到生产订单确认；未匹配或未选择二维码的部分仅留档。']
  row.productionConsumptionItems.forEach(item => {
    const name = item.materialNameRaw || item.productName || '未识别原料'
    const entries = item.items || []
    if (!entries.length) {
      lines.push(`${name}：数量未识别`)
      return
    }
    entries.forEach(entry => {
      const date = entry.productionDate || '日期未识别'
      const quantity = entry.quantityText || `${entry.boardCount || 0}板${entry.pieceCount || 0}件`
      lines.push(`${name}：${date} ${quantity}`)
    })
  })
  if (row.unmatchedNames && row.unmatchedNames.length) {
    lines.push(`未匹配产品：${row.unmatchedNames.join('、')}`)
  }
  if (row.productionConsumptionResults && row.productionConsumptionResults.length) {
    lines.push('处理结果：')
    row.productionConsumptionResults.forEach(result => lines.push(result))
  }
  return lines.join('\n')
}

onMounted(async () => {
  await Promise.all([
    loadProductOptions(),
    loadWarehouseList()
  ])
  await loadHistoryOptions(true)
})
</script>

<style scoped>
.auto-inbound-page {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
  background: #ffffff;
}

.table-card {
  background: rgba(255, 255, 255, 0.9);
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}

.parse-form {
  display: block !important;
  margin-top: 10px;
}

.parse-form :deep(.el-form-item:last-child) {
  margin-left: 0 !important;
  padding-left: 0;
}

.parse-basic-panel {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 24px;
  padding-bottom: 10px;
}

.mode-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.mode-card {
  min-height: 92px;
  padding: 16px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #fbfcff;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, background-color 0.16s ease;
}

.mode-card:hover {
  border-color: #8bb8ff;
}

.mode-card.active {
  border-color: #2563eb;
  background: #eff6ff;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.12);
}

.mode-title {
  color: var(--app-text);
  font-size: 15px;
  font-weight: 700;
}

.mode-desc {
  margin-top: 8px;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.parse-text-panel {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #fbfcff;
}

.parse-text-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.parse-text-title {
  color: var(--app-text);
  font-size: 15px;
  font-weight: 650;
}

.parse-text-subtitle {
  margin-top: 4px;
  color: var(--app-text-tertiary);
  font-size: 13px;
}

.raw-text-item {
  display: block;
}

.raw-text-item :deep(.el-form-item__label) {
  float: none;
  display: block;
  margin-bottom: 8px;
  text-align: left;
}

.raw-text-item :deep(.el-form-item__content) {
  margin-left: 0 !important;
}

.raw-text-item :deep(.el-textarea__inner) {
  min-height: 112px;
  line-height: 1.6;
}

.parse-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.table-header .left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.semi-history-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.semi-overview {
  margin-top: 18px;
}

.semi-table-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.history-option {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
}

.filter-form .el-form-item {
  margin-bottom: 0;
}

.ellipsis-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-footer {
  margin-top: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.global-remark-alert {
  margin-bottom: 10px;
}

.qr-demand {
  display: grid;
  gap: 2px;
  color: var(--app-text-secondary);
  font-size: 12px;
}

.quantity-edit {
  display: grid;
  grid-template-columns: 78px 18px 78px 18px;
  align-items: center;
  gap: 6px;
}

.quantity-edit :deep(.el-input-number) {
  width: 78px;
}

.detail-drawer {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 100%;
}

.drawer-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.block-title {
  color: var(--app-text);
  font-size: 16px;
  font-weight: 700;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.info-card,
.conclusion-card {
  padding: 14px 16px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #fbfcff;
}

.info-card span {
  display: block;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.info-card strong {
  display: block;
  margin-top: 6px;
  color: var(--app-text);
  font-size: 14px;
  line-height: 1.5;
}

.conclusion-title {
  color: var(--app-text);
  font-size: 15px;
  font-weight: 700;
}

.conclusion-text {
  margin-top: 8px;
  color: var(--app-text-secondary);
  line-height: 1.7;
}

.split-items,
.result-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.result-items {
  display: grid;
}

.result-line {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 12px;
}

.consumption-archive {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.risk-reason {
  white-space: pre-wrap;
  line-height: 1.6;
}

.remark-text {
  white-space: normal;
}

.empty-text {
  color: var(--app-text-tertiary);
}

.finish-flow-card {
  margin-bottom: 20px;
}

.finish-flow-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.finish-steps {
  padding: 10px 10px 18px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--app-border-soft);
}

.draft-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.draft-overview-card {
  padding: 14px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #f8fafc;
}

.draft-overview-card.warning {
  border-color: #fed7aa;
  background: #fff7ed;
}

.overview-label {
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.draft-overview-card strong {
  display: block;
  margin-top: 6px;
  color: var(--app-text);
  line-height: 1.5;
}

.draft-overview-card span {
  display: block;
  margin-top: 6px;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.finish-step-panel {
  display: grid;
  gap: 14px;
}

.step-title {
  color: var(--app-text);
  font-size: 15px;
  font-weight: 700;
}

.finish-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.summary-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-stat-grid.six {
  grid-template-columns: repeat(6, minmax(0, 1fr));
  margin-bottom: 14px;
}

.summary-stat {
  padding: 14px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #fbfcff;
}

.summary-stat span,
.summary-stat em {
  color: var(--app-text-tertiary);
  font-size: 12px;
  font-style: normal;
}

.summary-stat strong {
  margin: 0 4px 0 0;
  color: var(--app-text);
  font-size: 26px;
  line-height: 1.2;
}

.primary-action-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  padding: 16px;
  margin-bottom: 14px;
  border: 1px solid #bfdbfe;
  border-radius: var(--app-radius);
  background: #eff6ff;
}

.summary-panel,
.selected-order-card,
.material-hint-card,
.empty-panel {
  padding: 14px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #fbfcff;
}

.summary-title {
  margin-bottom: 10px;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 700;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-subtitle {
  margin-top: -4px;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.material-group-list {
  display: grid;
  gap: 12px;
}

.material-group-title {
  margin-bottom: 8px;
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.recommend-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.create-recommend-card {
  display: grid;
  gap: 6px;
  padding: 14px;
  border: 1px solid #bfdbfe;
  border-radius: var(--app-radius);
  background: #f8fbff;
}

.create-recommend-card span {
  color: var(--app-text-secondary);
  font-size: 13px;
}

.recommend-card {
  padding: 14px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #ffffff;
  cursor: pointer;
}

.recommend-card.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.recommend-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.recommend-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.recommend-grid span {
  display: block;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.recommend-grid em {
  display: block;
  margin-top: 3px;
  color: var(--app-text-secondary);
  font-size: 13px;
  font-style: normal;
  line-height: 1.4;
}

.match-reason {
  margin-top: 10px;
  color: #2563eb;
  font-size: 12px;
}

.order-search-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  align-items: stretch;
  padding: 14px;
  margin-bottom: 14px;
  border: 1px solid #edf1f7;
  border-radius: 10px;
  background: #f8fafc;
}

.order-search-field {
  width: 100%;
}

.order-search-field :deep(.el-input),
.order-search-field :deep(.el-input__wrapper),
.order-search-field :deep(.el-select__wrapper) {
  width: 100%;
}

.order-search-refresh {
  justify-self: start;
}

.order-result-list {
  display: grid;
  gap: 10px;
}

.order-result-card {
  display: grid;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--app-border-soft);
  border-radius: 10px;
  background: #ffffff;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, background-color 0.16s ease;
}

.order-result-card:hover {
  border-color: #9bbcff;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.08);
}

.order-result-card.active {
  border-color: #2563eb;
  background: linear-gradient(180deg, #eff6ff 0%, #ffffff 100%);
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.12);
}

.order-result-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.order-result-head strong,
.order-result-head span {
  display: block;
}

.order-result-head strong {
  color: var(--app-text-primary);
  font-size: 15px;
}

.order-result-head span {
  margin-top: 3px;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.order-result-status {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.order-selected-badge {
  display: inline-flex !important;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: #2563eb;
  color: #ffffff !important;
  font-size: 12px;
}

.order-result-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.order-result-summary div {
  min-width: 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
}

.order-result-summary span {
  display: block;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.order-result-summary em {
  display: block;
  margin-top: 4px;
  color: var(--app-text-secondary);
  font-size: 13px;
  font-style: normal;
  line-height: 1.45;
  word-break: break-word;
}

.manual-order-collapse,
.draft-detail-collapse {
  margin-top: 14px;
}

.manual-order-collapse :deep(.el-collapse-item),
.draft-detail-collapse :deep(.el-collapse-item) {
  margin-bottom: 10px;
  overflow: hidden;
  border: 1px solid #e6edf7;
  border-radius: 10px;
  background: #ffffff;
}

.manual-order-collapse :deep(.el-collapse-item__header),
.draft-detail-collapse :deep(.el-collapse-item__header) {
  justify-content: flex-start;
  min-height: 64px;
  height: auto;
  padding: 0 14px;
  border-bottom: 0;
  background: #f5f8fc;
  text-align: left;
  transition: background-color 0.16s ease;
}

.manual-order-collapse :deep(.el-collapse-item__header:hover),
.draft-detail-collapse :deep(.el-collapse-item__header:hover) {
  background: #eef5ff;
}

.manual-order-collapse :deep(.el-collapse-item__arrow),
.draft-detail-collapse :deep(.el-collapse-item__arrow) {
  margin-left: 12px;
}

.manual-order-collapse :deep(.el-collapse-item__content),
.draft-detail-collapse :deep(.el-collapse-item__content) {
  padding: 14px;
}

.smart-collapse-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  min-width: 0;
  text-align: left;
}

.smart-collapse-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1 1 auto;
  justify-content: flex-start;
  min-width: 0;
  text-align: left;
}

.smart-collapse-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: #e8f1ff;
  color: #2563eb;
  font-size: 14px;
  font-weight: 700;
}

.smart-collapse-main {
  color: var(--app-text);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
  text-align: left;
}

.smart-collapse-sub {
  margin-top: 4px;
  color: var(--app-text-tertiary);
  font-size: 12px;
  line-height: 1.35;
  text-align: left;
  white-space: normal;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.risk-list {
  display: grid;
  gap: 6px;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.raw-text-preview {
  max-height: 130px;
  overflow: auto;
  white-space: pre-wrap;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.raw-text-preview.compact {
  max-height: 96px;
}

.step-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.order-mode-switch,
.order-select-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.create-order-form {
  max-width: 760px;
}

.plan-preview-list {
  display: grid;
  gap: 4px;
  color: var(--app-text-secondary);
  line-height: 1.6;
}

.order-context-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.order-context-grid span {
  display: block;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.order-context-grid strong {
  display: block;
  margin-top: 5px;
  color: var(--app-text);
  font-size: 14px;
}

.stage-alert {
  margin-bottom: 2px;
}

.hint-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.hint-header strong,
.hint-header span {
  display: block;
}

.hint-header span {
  margin-top: 4px;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.hint-risk {
  color: #b45309 !important;
}

.material-recommendation {
  display: grid;
  grid-template-columns: minmax(140px, 0.8fr) minmax(240px, 1.4fr) minmax(180px, 1fr);
  gap: 10px;
  margin: 12px 0;
  padding: 10px 12px;
  border: 1px solid #bfdbfe;
  border-radius: var(--app-radius);
  background: #eff6ff;
}

.material-recommendation div {
  display: grid;
  gap: 4px;
}

.material-recommendation span {
  color: var(--app-text-secondary);
  font-size: 12px;
}

.material-recommendation strong {
  color: #1e3a8a;
  font-size: 13px;
  font-weight: 600;
}

.material-recommendation .return-warning {
  color: #b45309;
}

.hint-edit-grid {
  display: grid;
  grid-template-columns: minmax(220px, 1.4fr) 160px 100px 100px;
  gap: 10px;
  margin-bottom: 12px;
}

.hint-edit-grid :deep(.el-input-number),
.hint-edit-grid :deep(.el-date-editor) {
  width: 100%;
}

.candidate-selection-tip {
  margin-top: 10px;
  color: #2563eb;
  font-size: 13px;
}

.matched-list {
  display: grid;
  gap: 8px;
}

.matched-row {
  display: grid;
  grid-template-columns: minmax(150px, 0.8fr) minmax(140px, 0.7fr) minmax(220px, 1.2fr) minmax(140px, 0.7fr);
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #f8fafc;
}

.matched-row span,
.matched-row em {
  color: var(--app-text-secondary);
  font-size: 13px;
  font-style: normal;
}

.compact-material-list {
  display: grid;
  gap: 8px;
}

.compact-material-row,
.final-summary-grid > div {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) minmax(120px, 0.7fr) minmax(100px, 0.6fr);
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #f8fafc;
}

.compact-material-row span,
.compact-material-row em {
  color: var(--app-text-secondary);
  font-size: 13px;
  font-style: normal;
}

.final-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.final-summary-grid > div {
  grid-template-columns: 96px minmax(0, 1fr);
}

.final-summary-grid span {
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.final-summary-grid strong {
  color: var(--app-text);
  font-size: 14px;
}

.risk-list.compact {
  margin-top: 12px;
}

:deep(.auto-inbound-detail-drawer .el-drawer__body) {
  padding: 8px 22px 22px;
}

@media (max-width: 1200px) {
  .info-grid {
    grid-template-columns: 1fr;
  }

  .mode-card-grid,
  .finish-summary-grid,
  .draft-overview,
  .summary-stat-grid,
  .summary-stat-grid.six,
  .recommend-card-grid,
  .order-search-grid,
  .order-result-summary,
  .matched-row,
  .compact-material-row,
  .final-summary-grid,
  .final-summary-grid > div,
  .hint-edit-grid,
  .order-context-grid {
    grid-template-columns: 1fr;
  }

  .primary-action-card {
    align-items: stretch;
    flex-direction: column;
  }
}

</style>
