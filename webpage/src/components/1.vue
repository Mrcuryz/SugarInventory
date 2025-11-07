<template>
  <div class="analysis-container">
    <el-row :gutter="20">
      <!-- 数据表格 -->
      <el-col :span="16">
        <el-card class="table-card">
          <h3>虚假评论检测分析报告（共{{ comments.length }}条）</h3>
          <el-table
              :data="comments"
              style="width: 100%"
              :row-class-name="rowClassName"
              @row-click="handleRowClick">
            <el-table-column prop="id" label="ID" width="100"/>
            <el-table-column label="风险评分" width="120">
              <template #default="{ row }">
                <el-tag :type="getScoreType(row.score)" effect="dark">
                  {{ row.score.toFixed(2) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="user" label="用户" width="120"/>
            <el-table-column prop="content" label="评论内容" min-width="300"/>
            <el-table-column prop="reasons" label="检测原因" width="180">
              <template #default="{ row }">
                <div class="reason-tags">
                  <el-tag
                      v-for="reason in row.reasons"
                      :key="reason"
                      size="small"
                      :type="getReasonTagType(reason)"
                      class="tag-item">
                    {{ reasonMap[reason] || reason }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="time" label="时间" width="140"/>
          </el-table>
        </el-card>
      </el-col>

      <!-- 可视化图表 -->
      <el-col :span="8">
        <el-card class="chart-card">
          <div class="chart-header">
            <h3>风险分布分析</h3>
            <el-radio-group v-model="chartType" size="small">
              <el-radio-button label="pie">饼图</el-radio-button>
              <el-radio-button label="bar">柱状图</el-radio-button>
            </el-radio-group>
          </div>
          <div ref="chart" style="height: 500px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import {ref, onMounted, watch, computed} from 'vue'
import * as echarts from 'echarts'

const chart = ref(null)
const chartType = ref('pie')

const getScoreType = (score) => {
  if (score >= 0.3) return 'danger'
  if (score >= 0.2) return 'warning'
  return 'success'
}

const getReasonTagType = (reason) => {
  /*...*/
}

// 扩展的模拟数据
const comments = ref([
      {
        id: 'REV1000',
        score: 0.29,
        user: '福***王',
        content: '新家具有一点味道，虽然不太刺鼻，但是仍然需要去除甲醛味。这款产品应该效果不错，试试看，买了12瓶，放在家具橱柜，抽屉，柜子，床体箱子里，好用的话，肯定向朋友们推荐一下。',
        reasons: ['异常评论簇'],
        time: '2025-04-11 08:41'
      },
      {
        id: 'REV1001',
        score: 0.32,
        user: 'j***n',
        content: '优诺康除甲醛果冻使用方便，打开即用，无需插电。新房装修后使用，异味明显减少，空气清新不少。变色设计直观可视，效果看得见。适合放在衣柜、房间各角落，性价比高，适合急住用户。',
        reasons: ['异常评论簇', '语义偏离'],
        time: '2025-04-16 11:16'
      },
      {
        id: 'REV1002',
        score: 0.3,
        user: 'd***8',
        content: '味道明显是小了，现在是的',
        reasons: ['语义偏离', '内容过短', '内容疑似乱码/无意义'],
        time: '2025-04-16 10:00'
      },
      {
        id: 'REV1003',
        score: 0.21,
        user: '薄***长',
        content: '包装很好，配方不错，打开看看用用看效果如何。',
        reasons: ['语义偏离', '内容疑似乱码/无意义'],
        time: '2025-04-16 08:57'
      },
      {
        id: 'REV1004',
        score: 0.27,
        user: 'j***p',
        content: '看起来还行，试一下效果',
        reasons: ['内容过短', '内容疑似乱码/无意义'],
        time: '2025-04-15 07:50'
      },
      {
        id: 'REV1005',
        score: 0.35,
        user: 't***g',
        content: '宝贝收到了，太惊喜了，质量非常好，性价比很高，真是出乎我的意料！',
        reasons: ['语义偏离', '情绪词堆砌', '内容疑似乱码/无意义'],
        time: '2025-04-14 19:44'
      },
      {
        id: 'REV1006',
        score: 0.36,
        user: 'X***H',
        content: '不错不错不错不错不错不错不错不错不错',
        reasons: ['语义偏离', '重复率异常高', '内容疑似乱码/无意义'],
        time: '2025-04-14 15:55'
      },
      {
        id: 'REV1007',
        score: 0.33,
        user: 'j***5',
        content: '到货就迫不及待打开了，果真没有让我失望，质量特别好，颜值也相当高，满意的一次购物',
        reasons: ['语义偏离', '情绪词堆砌', '内容疑似乱码/无意义'],
        time: '2025-04-14 13:08'
      },
      {
        id: 'REV1008',
        score: 0.19,
        user: 'u***v',
        content: '这就是用了三天后测的效果，应该有用',
        reasons: ['语义偏离', '内容疑似乱码/无意义'],
        time: '2025-04-13 21:32'
      },
      {
        id: 'REV1009',
        score: 0.27,
        user: 'j***q',
        content: '很好，方便使用后有效果',
        reasons: ['语义偏离', '内容过短', '内容疑似乱码/无意义'],
        time: '2025-04-13 20:46'
      },
      {
        id: 'REV1010',
        score: 0.41,
        user: 'u***9',
        content: '在京东买了个衣柜，包送货上门，还包安装，搞好以后有一点点异味，就又在京东选了这一款除甲醛的产品，本来是试试，没想到效果超级好，把这个东西放在柜子里面，再白天开窗户，几天就没味啦，非常棒',
        reasons: ['异常评论簇', '情绪词堆砌', '内容疑似乱码/无意义'],
        time: '2025-04-13 19:18'
      },
      {
        id: 'REV1011',
        score: 0.23,
        user: 'j***v',
        content: '一开始是绿色的，打开放在屋里一星期变成白色了，然后测试甲醛，显示合格。',
        reasons: ['语义偏离', '内容疑似乱码/无意义'],
        time: '2025-04-10 14:54'
      },
      {
        id: 'REV1012',
        score: 0.34,
        user: 'f***6',
        content: '东西很好用，客服服务也很好',
        reasons: ['语义偏离', '内容过短', '内容疑似乱码/无意义'],
        time: '2025-04-10 12:03'
      },
      {
        id: 'REV1013',
        score: 0.2,
        user: '果***张',
        content: '在持续使用中，味道变淡了，相信会有效果的',
        reasons: ['语义偏离', '内容疑似乱码/无意义'],
        time: '2025-04-10 10:11'
      },
      {
        id: 'REV1014',
        score: 0.38,
        user: 'k***7',
        content: '超级超级超级超级好！必须买买买！绝对正品！',
        reasons: ['情绪词堆砌', '重复率异常高', '内容疑似乱码/无意义'],
        time: '2025-04-16 14:22'
      },
      {
        id: 'REV1015',
        score: 0.25,
        user: 'm***2',
        content: '快递很快，包装完整，还没使用',
        reasons: ['内容过短', '与商品无关'],
        time: '2025-04-16 13:17'
      },
      {
        id: 'REV1016',
        score: 0.41,
        user: 'z***9',
        content: '垃圾商品！完全没效果！骗人的！',
        reasons: ['负面情绪', '攻击性语言', '内容疑似乱码/无意义'],
        time: '2025-04-15 18:33'
      },
      {
        id: 'REV1017',
        score: 0.22,
        user: 'p***4',
        content: '打开是蓝色凝胶，放在柜子里变成白色了',
        reasons: ['语义偏离', '与商品无关'],
        time: '2025-04-15 16:45'
      },
      {
        id: 'REV1018',
        score: 0.31,
        user: 'q***1',
        content: '买来试试看，希望有效果，目前还没发现明显变化',
        reasons: ['语义偏离', '内容疑似乱码/无意义'],
        time: '2025-04-15 11:08'
      },
      {
        id: 'REV1019',
        score: 0.28,
        user: 'r***6',
        content: '包装很严实，快递小哥服务好',
        reasons: ['内容过短', '与商品无关'],
        time: '2025-04-14 22:19'
      },
      {
        id: 'REV1020',
        score: 0.36,
        user: 's***3',
        content: '超级无敌好用！买它买它买它！绝对不后悔！',
        reasons: ['情绪词堆砌', '重复率异常高'],
        time: '2025-04-14 17:55'
      },
      {
        id: 'REV1021',
        score: 0.19,
        user: 'v***8',
        content: '应该是正品，扫码验证通过',
        reasons: ['语义偏离', '内容过短'],
        time: '2025-04-13 16:27'
      },
      {
        id: 'REV1022',
        score: 0.33,
        user: 'w***5',
        content: '效果非常好，甲醛数值从0.08降到0.02，完全达标！',
        reasons: ['数据异常', '语义偏离'],
        time: '2025-04-13 14:36'
      },
      {
        id: 'REV1023',
        score: 0.26,
        user: 'y***0',
        content: '放在新车里，期待效果',
        reasons: ['内容过短', '与商品无关'],
        time: '2025-04-12 10:41'
      },
      {
        id: 'REV1024',
        score: 0.4,
        user: 'b***4',
        content: '完全没效果！商家虚假宣传！要求退货！',
        reasons: ['负面情绪', '攻击性语言', '重复率异常高'],
        time: '2025-04-12 09:15'
      },
      {
        id: 'REV1025',
        score: 0.24,
        user: 'c***7',
        content: '物流很快，第二天就到了',
        reasons: ['内容过短', '与商品无关'],
        time: '2025-04-11 19:22'
      },
      {
        id: 'REV1026',
        score: 0.29,
        user: 'e***9',
        content: '按照说明放置，期待两周后的检测结果',
        reasons: ['语义偏离', '内容疑似乱码/无意义'],
        time: '2025-04-11 15:33'
      },
      {
        id: 'REV1027',
        score: 0.35,
        user: 'f***2',
        content: '这是我这辈子买过最好的除醛产品！感动到哭！',
        reasons: ['情绪词堆砌', '语义偏离'],
        time: '2025-04-10 18:47'
      },
      {
        id: 'REV1028',
        score: 0.21,
        user: 'g***5',
        content: '客服态度很好，耐心解答问题',
        reasons: ['内容过短', '与商品无关'],
        time: '2025-04-10 16:12'
      },
      {
        id: 'REV1029',
        score: 0.37,
        user: 'h***8',
        content: '完全没用！骗钱！大家千万别上当！',
        reasons: ['负面情绪', '攻击性语言', '重复率异常高'],
        time: '2025-04-10'
      }
    ]
)

// 原因类型映射
const reasonMap = {
  '异常评论簇': '异常聚类',
  '语义偏离': '语义异常',
  '内容过短': '内容过短',
  '相似内容': '重复内容',
  '集中时段': '时间集中',
  '重复率高': '重复内容',
  '情绪词堆砌': '情绪词堆砌',
  '符号密度高': '符号异常',
  '内容疑似乱码/无意义' : '内容无意义',
  '内容杂乱或无意义': '内容无意义',
  '与商品无关': '关联异常',
  '重复率异常高': '高度重复',
  '内容疑似乱码或完全无意义': '无效内容',
  '特殊符号密度过高': '符号异常',
  '评论极短': '内容过短'
}
// 风险分布计算
const riskDistribution = computed(() => {
  return comments.value.reduce((acc, cur) => {
    if (cur.score >= 0.35) acc.high++
    else if (cur.score >= 0.2) acc.medium++
    else acc.low++
    return acc
  }, { high: 0, medium: 0, low: 0 })
})

// 初始化图表
let myChart = null
onMounted(() => {
  myChart = echarts.init(chart.value)
  renderChart()
})

// 图表类型切换
watch(chartType, () => {
  renderChart()
})

// 图表渲染逻辑
const renderChart = () => {
  const option = chartType.value === 'pie' ? getPieOption() : getBarOption()
  myChart.setOption(option)
  window.addEventListener('resize', () => myChart.resize())
}

// 饼图配置
const getPieOption = () => ({
  tooltip: {
    trigger: 'item',
    formatter: '{a} <br/>{b}: {c} ({d}%)'
  },
  legend: {
    orient: 'vertical',
    left: 'left',
    data: ['高风险 (≥0.35)', '中风险 (0.2-0.35)', '低风险 (<0.2)']
  },
  series: [{
    name: '风险分布',
    type: 'pie',
    radius: ['40%', '65%'],
    center: ['50%', '55%'],
    itemStyle: {
      borderRadius: 8,
      borderWidth: 2
    },
    label: {
      show: true,
      formatter: '{b|{b}}\n{d}%',
      rich: {
        b: {
          fontSize: 12,
          lineHeight: 20
        }
      }
    },
    data: [
      {
        value: riskDistribution.value.high,
        name: '高风险 (≥0.35)',
        itemStyle: { color: '#c23531' }
      },
      {
        value: riskDistribution.value.medium,
        name: '中风险 (0.2-0.35)',
        itemStyle: { color: '#d48265' }
      },
      {
        value: riskDistribution.value.low,
        name: '低风险 (<0.2)',
        itemStyle: { color: '#91c7ae' }
      }
    ]
  }]
})

// 柱状图配置
const getBarOption = () => ({
  tooltip: {
    trigger: 'axis'
  },
  xAxis: {
    type: 'category',
    data: ['高风险', '中风险', '低风险']
  },
  yAxis: {
    type: 'value'
  },
  series: [{
    data: [
      {
        value: riskDistribution.value.high,
        itemStyle: { color: '#c23531' }
      },
      {
        value: riskDistribution.value.medium,
        itemStyle: { color: '#d48265' }
      },
      {
        value: riskDistribution.value.low,
        itemStyle: { color: '#91c7ae' }
      }
    ],
    type: 'bar',
    barWidth: '60%',
    label: {
      show: true,
      position: 'top'
    }
  }]
})

// 其他逻辑...
</script>

<style scoped>
.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.reason-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tag-item {
  cursor: pointer;
  transition: all 0.3s;
}

.tag-item:hover {
  transform: translateY(-2px);
}

:deep(.el-table__row) {
  cursor: pointer;
  transition: all 0.3s;
}

:deep(.el-table__row:hover) {
  transform: scale(1.02);
  box-shadow: 0 2px 12px rgba(0,0,0,0.1);
}
</style>