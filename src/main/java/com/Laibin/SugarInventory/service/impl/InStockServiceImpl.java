package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.*;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.domain.vo.InStockVO;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.domain.vo.VInventorySummary;
import com.Laibin.SugarInventory.mapper.*;
import com.Laibin.SugarInventory.service.*;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 闂傚倷绀侀幖顐︽偋閸℃瑧鐭撻悗娑櫳戦崣蹇涙煟閺冨洢鈧偓闁稿鎹囧畷鐑筋敇閻愮増鍩涙俊銈囧Х閸嬫稑煤閵娾晜鍋?
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Service
public class InStockServiceImpl extends ServiceImpl<InStockMapper, InStock> implements InStockService, LoggableService<InStock> {
    @Autowired
    private InStockMapper inStockMapper;
    @Autowired
    private SemiProductRecordMapper semiProductRecordMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private InventoryMapper inventoryMapper;
    @Autowired
    private AssayMapper assayMapper;
    @Lazy
    @Autowired
    private OutStockService outStockService;
    @Autowired
    private WarehouseMapper warehouseMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SemiProductRecordService semiProductRecordService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private InventorySummaryMapper inventorySummaryMapper;
    @Autowired
    private PalletCodeMapper palletCodeMapper;
    @Autowired
    private SemiPreparePoolMapper semiPreparePoolMapper;

    @Autowired
    private AssayStandardJudgeService assayStandardJudgeService;

    private final Integer page = 1;
    private final Integer size = 10;

    @Transactional
    @Override
    public InVO stockIn(InStockRequestDTO dto, Integer operatorId) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 2. 获取库位信息
        Warehouse warehouse = warehouseMapper.selectByWarehouseName(dto.getWarehouseName());
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
        // 婵犲痉鏉库偓妤佹叏閹绢喗鍎楀〒姘ｅ亾闁诡垯鐒﹀鍕箛椤掑偆鏀ㄥ┑鐘垫暩婵挳宕锔藉€堕柛銉墯閻撴洘绻濋棃娑欏櫤缂佷胶澧楅妵鍕棘閹稿海鈹涢梺闈涙处閸旀瑥顕ｉ弶鎴僵闁告劖褰冪粻?useAssay 婵?true 闂傚倷鐒﹂惇褰掑礉瀹€鈧埀顒佸嚬閸ㄥ爼銆佸▎鎾崇倞妞ゅ繐鍊峰Ч妤呮⒑閸濆嫭宸濋柛鐘冲姇閳绘捇宕奸弴鐔叉嫼?
        List<SemiRecordDTO> semiRecords = dto.getSemiRecords() == null ? new ArrayList<>() : dto.getSemiRecords();
        long useAssayCount = semiRecords.stream()
                .filter(record -> Boolean.TRUE.equals(record.getUseAssay()))
                .count();

        if (useAssayCount > 1) {
            throw new BusinessException(ErrorCode.MULTIPLE_USE_ASSAY_FLAGS);
        }
        // 闂傚倷绀侀幉锛勬暜閸ヮ剙纾归柡宥庡幖閽冪喖鏌涢妷锝呭闁稿海鍠栭弻鐔煎箚瑜忛敍宥夋煙閻ｅ苯鈻堥柡宀嬬節瀹曠喖妫冨☉姘摋闂備浇顕栭崹顖滄濮橆剛鏆︽俊銈呮噹瀹告繈鏌℃径濠勪虎闁? 濠德板€楁慨鐑藉磻閻愯鑰块柛锔诲幘缁犳棃鏌″搴″箹缂佺姰鍎甸弻銊モ攽閸℃ê娅ｅ┑鐐叉噷閸婃繈寮诲☉妯滄棃鍩€椤掑嫭鏅濋柕澶嗘櫅閸ㄥ倹鎱ㄥΟ鎸庣【濞磋偐濮撮湁闁挎繂鐗滃鎰箾閸繄鍩ｆ慨?
        //returnInStockFlag缂傚倸鍊烽悞锔剧矙閹次诲洭顢欓幑鎰?闂傚倷绀侀幖顐﹀疮椤栨熬鑰块柛锔诲幗鐎氬鏌ｉ弬鍨倯闁绘帒顭烽弻宥堫檨闁告挻鐩獮蹇曟兜閸滀焦些闂備礁鎲￠悷銉ノ涘▎鎾崇畾闁哄啫鐗嗙粻濂告煕閺囥劌骞樻い锔诲枟缁绘盯骞嬮悙鏉戠缂備礁顦紞濠囧箖閺夊簱鏋庨柟瀵稿仜閻濈増绻涙潏鍓у埌闁硅绱曢埀顒佺閻擄繝寮婚悢鍝勬瀳濠㈣泛鑻崺宀勬⒑闂堚晝鎮奸柛搴涘€濋獮鍡涘籍閸繄顔掗悗瑙勬礀濞诧箓骞?
        if (!"1".equals(dto.getReturnInStockFlag())) {
            this.judgeInventory(semiRecords, operatorId);
            // 闂備礁鎼ˇ顐﹀疾濠婂牊鍋￠柍鍝勬噹闂傤垰顪冪€ｎ亜顒㈤柛鐔锋惈闇夐柨婵嗘祩閻掗箖鏌￠崨顔炬噰闁哄瞼鍠庨悾锟犳嚋椤戣法閽电紓鍌欑劍濮婄懓顭囧▎鎾崇厺閹兼番鍔岄悘宕団偓瑙勬礀濞诧箓鎯冮幋鐐电閺夊牆澧介崚鎵磼婢跺孩鐝紒宀冮哺缁绘繈宕惰閻ｅ啿顪冮妶鍡欏ⅵ闁稿﹥顨堢划鍫熷緞閹邦厾鍘电紒鐐緲椤﹂亶宕氭导瀛樼厱闁挎繂娲ら崝瀣煙椤栨稒顥堟鐐疵悾鐑藉炊閵婏箑鏋涢梻鍌欑閹诧繝鎳濋崜褑濮抽柤娴嬫櫅閸ㄦ繈鏌曟繝蹇涙闁稿海鍠栭弻鐔兼倷椤掆偓婢ь垶鏌涢悢閿嬪枠婵﹥妞介獮鎾诲箳閺冨偆鍞规繝鐢靛仦瑜板啫顭囬垾鎰佸殨?
            warehouse = warehouseMapper.selectByWarehouseName(dto.getWarehouseName());
        }
        Assay assay = null;
        Assay semiAssay;

        if (useAssayCount == 1) {
            // 闁兼儳鍢茶ぐ鍥冀閸ヮ亶鍞跺☉?useAssay 闁汇劌瀚畷鎰板箣閹邦剚鎯傞悹浣规緲缂?
            SemiRecordDTO selectedSemi = semiRecords.stream()
                    .filter(record -> Boolean.TRUE.equals(record.getUseAssay()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SEMI_RECORD));
            // 闂傚倷绀侀幖顐ょ矓閻戞枻缍栧璺猴功閺嗐倕銆掑锝呬壕閻庤娲嶉崜婵堟崲濠靛绀冮柕濞у倹鎹ｉ梻鍌欑閹诧紕鎹㈤埀顒佺箾鐎靛憡鎳欓梻鍌欑閹诧紕鍒掑畝鍕剶濠靛倻顭堥弸渚€鏌熼幆褜鍤熸い鈺勫皺閹插憡鎯旈埈銉︾洴閸╋繝宕ㄩ鐐靛綁闂備胶绮弻銊╁箺濠婂牆鐭楅柍褜鍓熼弻锝夋偐閸欏鍋嶉梺鎼炲妼濠€閬嶅焵椤掑嫭娑х€殿喖鐖奸妴鍐Ψ閵壯勬畷闂佸憡鍔栭崕鎶藉极瑜版帗鍋?
            semiAssay = getAssayByProductIdAndDate(selectedSemi.getSemiProductId(), selectedSemi.getProductionDate());
            if (semiAssay != null) {
                AssaySubmitDTO dtoAssay = new AssaySubmitDTO();
                dtoAssay.setProductId(dto.getProductId());
                dtoAssay.setSampleDate(dto.getEntryDate());
                dtoAssay.setColorValue(semiAssay.getColorValue());
                dtoAssay.setReducingSugar(semiAssay.getReducingSugar());
                dtoAssay.setDryWeight(semiAssay.getDryWeight());
                dtoAssay.setInsolubleImpurity(semiAssay.getInsolubleImpurity());
                dtoAssay.setPhValue(semiAssay.getPhValue());
                dtoAssay.setSucrose(semiAssay.getSucrose());
                dtoAssay.setConductivityAsh(semiAssay.getConductivityAsh());

                AssayJudgeOutcome judgeOutcome = assayStandardJudgeService.judge(product, dtoAssay);
                assay = new Assay();
                BeanUtils.copyProperties(dtoAssay, assay);
                assay.setTestedBy(operatorId);
                assay.setQualifiedStandards(judgeOutcome.getQualifiedStandardsJson());
                assay.setIsQualified(judgeOutcome.getCompatibleConclusion());
                assay.setAppliedStandardId(judgeOutcome.getAppliedStandardId());
                assay.setAppliedStandardName(judgeOutcome.getAppliedStandardName());
                assay.setAppliedStandardVersion(judgeOutcome.getAppliedStandardVersion());
                assay.setJudgeResult(judgeOutcome.getJudgeResult());
                assay.setJudgeMessage(judgeOutcome.getJudgeMessage());
                assay.setFailedMetricCount(judgeOutcome.getFailedMetricCount());
                assay.setFailedMetricsJson(judgeOutcome.getFailedMetricsJson());
                assay.setStandardSnapshotJson(judgeOutcome.getStandardSnapshotJson());

                Assay todaysAssay = getAssayByProductIdAndDate(dto.getProductId(), dto.getEntryDate());
                if (todaysAssay != null) {
                    int version = todaysAssay.getVersion() + 1;
                    assay.setVersion(version);
                } else {
                    assay.setVersion(1);
                }
                assay.setCreatedAt(LocalDateTime.now());

                assayMapper.insert(assay);
                assay = assayMapper.selectByProductIdAndDate(dto.getProductId(), dto.getEntryDate());
            }
        } else {
            // 闂傚倷绀侀幖顐ょ矓閻戞枻缍栧璺猴功閺嗐倕銆掑锝呬壕濡ょ姷鍋涢澶愮嵁閸ヮ剦鏁嗗ù锝囨嚀閸撶悂D闂傚倷绀侀幉锛勫垝瀹€鍕殣妞ゆ牜鍋涢惌妤呮煕閳╁叐鎴﹀磿閻斿吋鐓欓柟顖嗗懏鎲奸梺鎸庣☉椤︾敻寮婚敓鐘查唶闁靛繒濮甸悗楣冩⒑缂佹ɑ鎯堢紒杈ㄦ礋楠炲繘鎮╃拠宸綂闂佺粯锚濡﹪宕靛▎鎰箚闁靛牆娲ゅ瓭闂佹椿鍘虹欢姘躲€佸▎鎾崇倞妞ゅ繐鍊峰Ч?
            assay = getAssayByProductIdAndDate(dto.getProductId(), dto.getEntryDate());
        }

        InStock inStock = new InStock();
        // 3. 闂備浇宕甸崰鎰版偡鏉堚晛绶ゅΔ锝呭暞閸婇潧霉閻樺樊鍎忕紒鈧崼鈶╁亾楠炲灝鍔氭繛鏉戝€圭€靛ジ宕掑В顓炵秺閹虫牠鍩℃担鍥风稻缁绘盯鎳栭埡鍌涙瘓闂佽鍨伴崯鏉戠暦閻旂⒈鏁冮柕蹇ｆ緛缁鳖噣姊绘担鐑樺殌闁宦板姂瀹曟繈寮撮姀鐘殿啇?JSON闂傚倷鐒︾€笛呯矙閹达附鍤愭い鏍仦閸ゆ劙鏌ｉ弮鍌氬付闁藉啰鍠栭弻鏇熷緞濡厧甯ラ梺鎼炲€曠€氫即寮婚埄鍐╁闁告繂瀚烽弳顓㈡倵鐟欏嫭灏柣鎺炵畵楠?
        if (!semiRecords.isEmpty()) {
            for (SemiRecordDTO recordDTO : semiRecords) {
                if (Boolean.TRUE.equals(recordDTO.getFromPreparePool())) {
                    continue;
                }
                int count = semiProductRecordMapper.existsByProductIdAndDate
                        (recordDTO.getSemiProductId(), recordDTO.getProductionDate());
                if (count == 0)
                    throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
            }
            String semiProductRecordsJson = convertToJson(semiRecords);
            inStock.setSemiProductRecords(semiProductRecordsJson);
        }

        int maxRows = warehouse.getMaxRows();
        int remainingQuantity = dto.getQuantity();
        String currentSide = dto.getSide(); // 婵犳鍠楃敮妤冪矙閹烘せ鈧箓宕奸妷顔芥櫍缂傚倷鐒﹂…鍥€呴悜钘夌缂侇喖鍘滈崑鎾绘嚑椤掑鏁告繝鐢靛仦閹稿鎯冨鍫濈妞ゅ繐鎳忛崰鏍⒒娴ｅ鈧偓闁?
        boolean canStack = product.getCanStack(); // 闂傚倷绀侀幖顐も偓姘卞厴瀹曡瀵奸弶鎴犵暰婵炴挻鍩冮崑鎾垛偓瑙勬穿缂嶄線銆佸☉姗嗘僵闁告鍋熸导鍕磽?

        // **3. 婵犵妲呴崑鍛熆濡皷鍋撳鐓庣仸闁绘侗鍠涚粻娑樷槈濡偐鍘梻浣告惈鐠囩偤宕ㄩ鍜佷邯濮婃椽宕ㄦ繝鍐ㄧ缂備礁顦顓㈡嚍鏉堛劍缍囬柕濠忕畱椤庢捇鎮楅獮鍨姎婵炶绠撻幃楣冩焼瀹ュ棗鈧灚绻涢幋鐑嗕痪妞ゅ繐鐗嗙壕鑽も偓骞垮劚椤︻垳绮诲鑸电厱婵炴垵宕獮鏍煛?*
        int leftUsedRowsLayer1 = inventoryMapper.getUsedRows(warehouse.getId(), "\u5DE6", 1);
        int rightUsedRowsLayer1 = inventoryMapper.getUsedRows(warehouse.getId(), "\u53F3", 1);
        int leftUsedRowsLayer2 = inventoryMapper.getUsedRows(warehouse.getId(), "\u5DE6", 2);
        int rightUsedRowsLayer2 = inventoryMapper.getUsedRows(warehouse.getId(), "\u53F3", 2);

        int currentLayer = (leftUsedRowsLayer2 > 0 || rightUsedRowsLayer2 > 0) ? 2 : 1;

        int remainingCapacity = 2 * maxRows - leftUsedRowsLayer1 - rightUsedRowsLayer1;
        if (product.getCanStack() && currentLayer == 1) {
            remainingCapacity += 2 * maxRows;
        }

        if (remainingCapacity <= 0) {
            throw new BusinessException(ErrorCode.WAREHOUSE_FULL);
        }

        int quantity = dto.getQuantity();

        BigDecimal totalWeight = calculateInStockTotalWeight(product, quantity, dto.getUnit());

        // **4. 闂備浇宕垫慨鎶芥倿閿曗偓椤灝螣閼测晝顦悗骞垮劚椤︻垳绮堥崒鐐寸厪濠㈣泛鐗嗛崝銈嗘叏濡濡奸懣鎰版煕閵夘垳鍒板褎褰冮湁?*
        inStock.setWarehouseId(warehouse.getId());
        inStock.setProductId(dto.getProductId());
        inStock.setQuantity(quantity);
        inStock.setCreatedBy(operatorId);
        inStock.setEntryDate(dto.getEntryDate());
        inStock.setAssayId(assay == null ? null : assay.getId());
        inStock.setScreenMeshId(product.getScreenMeshId());
        inStock.setTotalWeight(totalWeight);
        inStock.setCreatedAt(LocalDateTime.now());
        inStock.setUnit(dto.getUnit());
        inStockMapper.insert(inStock);
        Integer inStockId = inStock.getId();
        // 婵犵數鍎戠徊钘壝洪敂鐐床闁稿瞼鍋為崑銈夋煏婵炵偓娅呯紒鈧崒鐐寸厪濠㈣泛鐗嗛崝銈嗘叏濡濮傞柡灞剧☉铻栭柍褜鍓熼弫鍐Ψ閳轰礁鐎┑顔筋焾濞夋稒瀵奸悩瑁佸綊鏁愰崼顐ｇ秷濡炪値鍋呴悧婊呮?
        if (!semiRecords.isEmpty()) {
            inStockMapper.saveInStockItem(semiRecords, inStockId);
        }
        dto.setInStockId(inStockId);
        // 闂備浇顕х€涒晝绮欓幒妤佹櫔闂備礁鎲￠悷銉ノ涘┑鍡╁殨闁割偅娲栫粻浼村箹濞ｎ剙鐏い?
        InVO inVO;
        if (dto.getUnit().equals("0")) {
            // 闂傚倷娴囧銊х矆娓氣偓椤㈡岸顢橀悢鍛婄彿濠德板€曢幊搴ｇ矆閸岀偞鐓忓璺虹墕閸斻倖鎱?
            inVO = semiProductRecordService.handlerInStock(dto, product, warehouse, assay, false, 0);
        } else {
            // 闂傚倷娴囬鏍礈濞戞艾鍨濇い鏍仜濮规煡骞栧ǎ顒€鐏い鈺冨厴閺屻倗绮欑捄銊ょ驳濠?
            inVO = semiProductRecordService.handlerInStockPieces(dto, product, warehouse, assay);
        }
        int actualQuantity = Math.max(quantity - (inVO.getRemainingQuantity() == null ? 0 : inVO.getRemainingQuantity()), 0);
        inStock.setQuantity(actualQuantity);
        inStock.setTotalWeight(calculateInStockTotalWeight(product, actualQuantity, dto.getUnit()));
        inStockMapper.updateById(inStock);
//        // **5. 闂佽瀛╅鏍窗閹烘纾婚柟鐐灱閺€鑺ャ亜閺冨倵鎷￠柛搴＄箻閺岋絾骞婇柛鏃€鍨甸?*
//        while (remainingQuantity > 0) {
//            int usedRows = (currentSide.equals("闂?)) ?
//                    (currentLayer == 1 ? leftUsedRowsLayer1 : leftUsedRowsLayer2)
//                    : (currentLayer == 1 ? rightUsedRowsLayer1 : rightUsedRowsLayer2);
//
//            if (usedRows < maxRows) {
//                int rowNumber = usedRows + 1;
//
//                // **闂備浇顕х€涒晝绮欓幒妤佹櫔闂備胶顭堥鍛矓閻熸壆鏆﹂柨鐔哄Т閸楁娊鏌ｉ弬鎸庢喐闁?*
//                Inventory inventory = new Inventory();
//                inventory.setWarehouseId(warehouse.getId());
//                inventory.setProductId(dto.getProductId());
//                inventory.setSide(currentSide);
//                inventory.setRowNumber(rowNumber);
//                inventory.setLayer(currentLayer);
//                inventory.setQuantity(1);
//                inventory.setEntryDate(dto.getEntryDate());
//                inventory.setInStockId(inStockId);
//                inventory.setScreenMeshId(dto.getScreenMeshId());
//                inventory.setAssayId(assay.getId());
//                inventory.setProductStatus(product.getStatus());
//                inventory.setSemiRecordId(null);
//                inventory.setCreatedAt(LocalDateTime.now());
//
//                try {
//                    inventoryMapper.insert(inventory);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    throw new BusinessException(ErrorCode.STOCK_IN_FAILED);
//                }
//                remainingQuantity--;
//
//                // **闂傚倷绀侀幖顐⒚洪妶澶嬪仱闁靛ň鏅涢拑鐔封攽閻樺弶鎼愰悗姘槹閵囧嫰骞掗崱妞惧婵犵數濮崑鎾绘煙缂併垹鏋涢柣顓燁殕閹便劌顪冪拠韫闂?*
//                if (currentSide.equals("闂?)) {
//                    if (currentLayer == 1) leftUsedRowsLayer1++;
//                    else leftUsedRowsLayer2++;
//                } else {
//                    if (currentLayer == 1) rightUsedRowsLayer1++;
//                    else rightUsedRowsLayer2++;
//                }
//            }
//
//            // **婵犵數濮烽。浠嬪焵椤掆偓閸熷潡鍩€椤掆偓缂嶅﹪骞冨Ο璇茬窞閹煎瓨绋愬Ч妤呮⒑鐟欏嫬鍔ら柛鐔锋健璺柛娑樼摠閻撴洟鏌熼悜妯绘儎婵炲牊娲滈埀顒侇問閸燁偊宕堕妸锔界彨闂佽绻掗崑鐔煎疾椤愨懣鎺楁晝閸屾稑浠梺鎼炲劘閸斿秶绮堥埀顒勬⒑閹肩偛濡肩紒缁橈耿閻涱噣骞嬮敃鈧～鍛存煟濡灝鐨洪柛妯诲浮濮婃椽宕ㄦ繝鍕殏缂傚倸鍊瑰銊у垝閳哄啠鍋撳☉娆樼劷闁?*
//            if (remainingQuantity > 0 && usedRows >= maxRows) {
//                currentSide = currentSide.equals("闂?) ? "闂? : "闂?;
//                usedRows = (currentSide.equals("闂?)) ?
//                        (currentLayer == 1 ? leftUsedRowsLayer1 : leftUsedRowsLayer2)
//                        : (currentLayer == 1 ? rightUsedRowsLayer1 : rightUsedRowsLayer2);
//            }
//
//            // **婵犵數濮烽。浠嬪焵椤掆偓閸熷潡鍩€椤掆偓缂嶅﹪骞冨Ο璇茬窞鐎光偓閳ь剛绮婚弮鈧妵鍕箣閿濆棛銆婂銈呯箻娴滃爼骞冨鈧幃娆撳箵閹哄棗浜鹃柣鐔煎亰閸ゆ洜鎲告惔锝囩焿鐎广儱顦獮銏°亜閹捐泛啸妞わ富鍠栭埞鎴﹀煡閸℃ぞ绨梺绋款儐閹瑰洭寮婚敍鍕勃閻犲洦褰冩慨鏇炩攽閳ュ啿绾ч柟顔煎€搁悾鐑藉Ψ閳哄倹娅囬梺閫炲苯澧查柕鍥ㄥ姍瀹曪絾寰勫畝濠冪カ闂備線娼чˇ顓㈠磿閹惰棄鍨傞柤濮愬€楃壕?*
//            if (remainingQuantity > 0 && leftUsedRowsLayer1 >= maxRows && rightUsedRowsLayer1 >= maxRows) {
//                if (canStack && currentLayer == 1) {
//                    // **闂傚倷绀侀幉锛勬暜閹烘嚚娲晝閳ь剟鎮鹃悜钘夎摕闁靛鍎抽ˇ顐︽⒑鐟欏嫷鍟忛柛锝庡櫍瀹曟洟骞嬪婵堟嚀椤劑宕橀鐑嗕純闂?*
//                    currentLayer = 2;
//                    leftUsedRowsLayer2 = 0;
//                    rightUsedRowsLayer2 = 0;
//                } else if (!canStack || (leftUsedRowsLayer2 >= maxRows && rightUsedRowsLayer2 >= maxRows)) {
//                    // **婵犵數濮烽。浠嬪焵椤掆偓閸熷潡鍩€椤掆偓缂嶅﹪骞冨Ο璇茬窞閻庯綆鍏橀弸鏍倵楠炲灝鍔氶柟鍐叉捣閹峰寮婚妷锔惧幈濡炪倖鍔戦崹娲夐悩鍨仏鐟滄棃寮婚妸銉㈡婵☆垳绮幏閬嶆⒑閹肩偛濡搁柛鏃€鍨块獮濠囧箻鐠囨彃绐涘銈嗙壄缁蹭粙宕滈搹顐ょ閻庢稒顭囬惌濠囨煠濞茶鐏﹂柟顖氭喘閹囧醇閳垛晜鐏嗛梻浣虹帛濮婂鈥﹂崼鐔稿弿妞ゆ巻鍋撻棁澶嬬節婵犲倸鏆為柨娑欐⒒缁辨帗娼忛妸褏鐤勯悗瑙勬礃閼归箖鍩㈡惔銊ョ疀妞ゆ帒鍊搁埀顒佸笧缁辨挻鎷呴幓鎺嶅闂備礁鎲＄换鍌溾偓姘煎枛椤繈濡搁妷鍐ㄧ秺閺佹劙宕ㄩ鍛灲閺屾盯骞嬮弬鍝勪壕闁归鑳堕崣?*
//                    warehouseMapper.updateCurCapacity(
//                            warehouse.getId(), warehouse.getCurCapacity() + quantity);
//                    if (product.getCanStack() && warehouse.getMaxCapacity() != warehouse.getMaxRows() * 2 * 2)
//                        warehouseMapper.updateMaxCapacity(warehouse.getId(), warehouse.getMaxRows() * 2 * 2);
//
//                    InVO inVO = new InVO();
//                    inVO.setRemainingQuantity(remainingQuantity);
//                    inVO.setMessage("闂備礁婀遍崢褔鎮洪妸鈺佺濡炲娴风粈濠囨煕濞戝崬寮鹃柛鐔锋嚇閺屾稑鈻庤箛锝喰︽繝娈垮櫘閸撶喖寮婚妸銉㈡婵°倕鍠氬Σ顔界節閳封偓閸曨偒鍤嬮梺?" + remainingQuantity + " 闂傚倷绀侀幖顐λ囬鐐参︽俊顖涙た濞堜粙鏌涢妷顔煎濞磋偐濮撮妴鎺戭潩椤掑﹤濮涚紓浣插亾濠㈣埖鍔栭崑锝夋煕閵夈垺娅呴柛妯绘尦閺屽秷顧侀柛鎾寸懇钘濋柟娈垮枓閸嬫挸顫濋妷銉ヮ潎閻庢鍣崜鐔奉嚕閸撲焦宕夐柕濠忓濞堛倕鈹?);
//                    return inVO;
//                }
//            }
//        }
//
//        // **6. 闂傚倷绀侀幉锟犳嚌妤ｅ啫瀚夋い鎺戝閺佸棝鏌ｉ幇顒佹儓闁告濞婇幃妤€鈽夊▍铏灴閹繝鍩€椤掑嫭鐓欓柣鎾虫捣閹界娀鏌熼幖渚囨缂佽京鍋ゅ畷銊╊敊闁款垱鏁甸梻浣规灱閺呮盯宕埡鍐╊潟?*
//        warehouseMapper.updateCurCapacity(
//                warehouse.getId(), warehouse.getCurCapacity() + quantity);
//        if (product.getCanStack() && warehouse.getMaxCapacity() != warehouse.getMaxRows() * 2 * 2)
//            warehouseMapper.updateMaxCapacity(warehouse.getId(), warehouse.getMaxRows() * 2 * 2);
//
//        InVO inVO = new InVO();
//        inVO.setRemainingQuantity(remainingQuantity);
//        if (inVO.getMessage() == null) {
//            inVO.setMessage("闂傚倷鑳堕…鍫㈡崲閸儱纾块弶鍫氭櫈婵娊姊洪鈧粔瀵哥不閿濆鐓欓梺顓ㄧ畱婢ь喗銇勯銈呪枅闁?);
//        }
        return inVO;
    }

    /**
     * 闂傚倷绀侀幉锛勬暜閸ヮ剙纾归柡宥庡幖閽冪喖鏌涢妷锝呭闁稿海鍠栭弻鐔煎箚瑜忛敍宥夋煙閻ｅ苯鈻堥柡宀嬬節瀹曠喖妫冨☉姘摋闂備浇顕栭崹顖滅矆娓氣偓閳ワ箓濡搁埡鍌も偓鎰版⒑閸涘﹤濮囨い顓炲槻閻ｅ嘲螖閸涱厙銊╂煏婢诡垰瀚▓銈夋⒑閼姐倕鏋戞繛鍙夊灴閹偤鏁冮崒娑樺壋?     *
     * @param semiRecords 闂傚倷绀侀幉锟犮€冮崨瀛樻櫇闁靛鏅涢崹鍌涙叏濡寧纭惧ù鑲╁Т闇夐柨婵嗘祩閻掑墽鈧鍠楁繛濠囩嵁閺嶎偀鍋撳☉娅虫垵鐣烽崟顑句簻闁哄倹瀵х粚鍧楁煏?
     */
    private void judgeInventory(List<SemiRecordDTO> semiRecords, Integer operatorId) {
        if (semiRecords == null || semiRecords.isEmpty()) {
            return;
        }
        List<Integer> productIds = semiRecords.stream()
                .filter(record -> !Boolean.TRUE.equals(record.getFromPreparePool()))
                .map(SemiRecordDTO::getSemiProductId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Integer, Product> productMap = productIds.isEmpty()
                ? Map.of()
                : productMapper.selectBatchIds(productIds).stream().collect(Collectors.toMap(Product::getId, s -> s));
        for (SemiRecordDTO semiRecord : semiRecords) {
            if (Boolean.TRUE.equals(semiRecord.getFromPreparePool())) {
                validatePreparePoolSemiRecord(semiRecord);
                continue;
            }
            Integer warehouseId = semiRecord.getWarehouseId();
            LocalDate productionDate = semiRecord.getProductionDate();
            Product product = productMap.get(semiRecord.getSemiProductId());
            if (product == null) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND.getCode(), "\u534A\u6210\u54C1" + semiRecord.getProductName() + "\u5DF2\u88AB\u5220\u9664");
            }
            VInventorySummary inventorySummary = inventorySummaryMapper.selectOne(
                    new LambdaQueryWrapper<VInventorySummary>()
                            .eq(VInventorySummary::getWarehouseId, warehouseId)
                            .eq(VInventorySummary::getProductId, semiRecord.getSemiProductId())
                            .eq(VInventorySummary::getEntryDate, productionDate)
                            .last("limit 1")
            );
            String msg = "\u534A\u6210\u54C1" + semiRecord.getProductName() + "\uFF0C\u751F\u4EA7\u65E5\u671F:" + semiRecord.getProductionDate();
            if (inventorySummary == null) {
                throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND.getCode(), msg + "\u7684\u5E93\u5B58\u4FE1\u606F\u672A\u627E\u5230");
            }
            String inventoryMsg = (inventorySummary.getTotalQuantity() > 0 ? inventorySummary.getTotalQuantity() + "\u677F" : "")
                    + (inventorySummary.getTotalPieces() > 0 ? inventorySummary.getTotalPieces() + "\u4EF6" : "");
            // 0=闁哄灏呯槐?=濞?
            if (semiRecord.getUnit().equals("0")) {
                if ((inventorySummary.getTotalQuantity() + inventorySummary.getTotalPieces() / product.getPiecesPerPallet()) < semiRecord.getQuantity()) {
                    throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK.getCode(), msg + "\u7684\u5E93\u5B58\u4E0D\u8DB3\uFF0C\u5269\u4F59:" + inventoryMsg);
                }
            } else {
                // 1=浠?
                if ((inventorySummary.getTotalQuantity() * product.getPiecesPerPallet() + inventorySummary.getTotalPieces()) < semiRecord.getQuantity()) {
                    throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK.getCode(), msg + "\u7684\u5E93\u5B58\u4E0D\u8DB3\uFF0C\u5269\u4F59:" + inventoryMsg);
                }
            }
            outStockService.outStock(product, warehouseId, semiRecord.getProductionDate(), semiRecord.getQuantity(), semiRecord.getUnit(), operatorId, 1);
        }
    }

    private void validatePreparePoolSemiRecord(SemiRecordDTO semiRecord) {
        if (semiRecord.getSemiProductId() == null) {
            throw new BusinessException("半成品来源缺少产品信息");
        }
        if (semiRecord.getProductionDate() == null) {
            throw new BusinessException("半成品来源缺少生产日期");
        }
        if (semiRecord.getQuantity() == null || semiRecord.getQuantity() <= 0) {
            throw new BusinessException("半成品来源缺少消耗数量");
        }
    }

    private BigDecimal calculateInStockTotalWeight(Product product, int quantity, String unit) {
        if (quantity <= 0) {
            return BigDecimal.ZERO;
        }
        if ("1".equals(unit)) {
            return product.getWeightPerPiece().multiply(BigDecimal.valueOf(quantity));
        }
        return product.getWeightPerPiece()
                .multiply(BigDecimal.valueOf(quantity))
                .multiply(BigDecimal.valueOf(product.getPiecesPerPallet()));
    }

    private boolean checkStandardCompliance(AssaySubmitDTO assay, QualityStandard standard) {
        return checkValue(assay.getColorValue(), standard.getColorMin(), standard.getColorMax()) &&
                checkValue(assay.getReducingSugar(), standard.getReducingSugarMin(), standard.getReducingSugarMax()) &&
                checkValue(assay.getDryWeight(), standard.getDryWeightMin(), standard.getDryWeightMax()) &&
                checkValue(assay.getConductivityAsh(), standard.getConductivityAshMin(), standard.getConductivityAshMax()) &&
                checkValue(assay.getSucrose(), standard.getSucroseMin(), standard.getSucroseMax()) &&
                checkValue(assay.getInsolubleImpurity(), standard.getInsolubleImpurityMin(), standard.getInsolubleImpurityMax()) &&
                checkValue(assay.getPhValue(), standard.getPhMin(), standard.getPhMax());
    }

    //闂傚倷绀侀幖顐ょ矙閸曨厽宕叉繝闈涱儐閸嬫ɑ绻涢崱妯诲碍闁藉啰鍠栭弻鐔兼焽閿斿潡鍋楅梺鎼炲€曢澶愬蓟閳╁啯濯撮悷娆忓閸戯繝姊洪柅鐐茶嫰婢ь噣鏌涘顒夊剰閸楅亶鏌熼梻瀵割槮閻熸瑱绠撻弻銊╁籍閸屾繃顎楅梺绋块閿曨亪寮诲☉姗嗘僵妞ゆ帒鍊搁·鈧梺璇查閻忔艾鐣濈粙娆惧殨?
    private boolean checkValue(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null && min == null && max == null) {
            return true;
        } else if (value == null) {
            return false; // 闂傚倷绀侀幉锟犳偋濡ゅ懏鍋嬮柣妯肩帛閸嬫ɑ绻涢崱妯诲碍闁哄绶氶弻锝呂旈埀顒勬偋閸℃瑧鐭堥柨鏇楀亾闁宠棄顦甸獮妯兼喆閸曞吀鍝楅梻浣哥枃椤鎮ч悩璇叉瀬鐎广儱娲ｅ▽顏堟煢濡警妲哄ù鐘欏嫮绠鹃柟瀵稿仦鐏忣厾绱掓径瀣唉妤犵偛鍟幆鏃堝Ω閵夈儱鏁?
        }
        if (min != null && max == null) {
            return value.compareTo(min) >= 0;  // 闂傚倷绀侀幉锟犳偡椤栨稓顩叉繝闈涙４閼板灝霉閿濆懏璐￠柍缁樻閹鏁愭惔鈩冪亶濡炪倕瀛╅悷鈺呭蓟閵娿儮妲堟俊顖氱仢椤忣厾绱撴担鍝勫姦闁稿鎸绘穱濠囨倷椤忓嫧鍋撳☉銏╂晪鐟滄梹淇婇悽绋跨倞闁靛ě灞鹃敜闂佺澹堥幓顏嗗緤閸ф绠归柣鎾崇瘍閻熼偊鐓ラ柛鎰典簻閸ゎ剛绱撴担璇℃當闁硅櫕锚椤?
        }
        if (min == null && max != null) {
            return value.compareTo(max) <= 0;  // 闂傚倷绀侀幉锟犳偡椤栨稓顩叉繝闈涙４閼板灝霉閿濆懏璐￠柍缁樻煥閳规垿鎮╅崣澶婎槱濡炪倕瀛╅悷鈺呭蓟閵娿儮妲堟俊顖氱仢椤忣厾绱撴担鍝勫姦闁稿鎸绘穱濠囨倷椤忓嫧鍋撳☉銏╂晪鐟滄棃骞嗛崘顕呮晢濞达絼璀﹀ú鎼佹煙閼圭増褰х紒鎻掓健楠炴牠鎮￠獮鐔烘嚀椤劑宕橀鍕瘓缂傚倷鐒﹂〃鍛村磹閸噮娼?
        }
        if (min != null && max != null) {
            return value.compareTo(min) >= 0 && value.compareTo(max) <= 0; // 闂傚倷绀侀幉锟犳嚌妤ｅ啫瀚夋い鎺嗗亾妞ゎ亜鍟村畷鎺戔攦閹傚濠电偞鍨堕顏堫敂閸曟儼鈧寧銇勮箛鎾村櫧闁崇粯鏌ㄩ埞鎴︽偐鏉堫偄鍘￠梺鑽ゅ枑閹瑰洤顫?
        }
        return true; // 婵犵數濮烽。浠嬪焵椤掆偓閸熷潡鍩€椤掆偓缂嶅﹪骞冨Ο璇茬窞闁归偊鍓欓崬銊╂⒑閹肩偛鍔€闁告劏鏅濊ぐ鏌ユ⒒閸屾瑧鍔嶇憸鏉垮暞缁轰粙寮崒婊呯暥闂佺鏈粙鎾诲煝閺冨牊鍊甸柨婵嗛閺嬬喐銇勬惔銏㈡噰婵﹥妞介、娆撴寠婢跺奔鍠婇梺鑽ゅУ閸旀宕伴幘璺哄灊婵鍩栭崑鏍倵闂堟稒鎲告い锔诲櫍濮婃椽宕ㄦ繝鍌滅懖闂佽崵鍟块弲婵嬪礆閹烘鏁嶆繝濠傚椤庢盯姊洪棃娑氬闁瑰啿绻樺畷鎰板Χ婢跺鍘?
    }

    // 闂傚倷鑳堕…鍫㈡崲閸儱纾块弶鍫氭櫈婵娊姊洪鈧粔瀵哥不閼测斁鍋撻獮鍨姎閻庢凹鍠氱划娆愮節閸ャ劎鍙嗗┑鐐村灦椤洦鏅堕弬搴撴斀闁绘劕寮堕崰姗€鏌熼鑽ょ煓濠碘剝鎮傛俊鐑芥晜閸撗€鏋岄梻鍌欑閹碱偆绮旈弶鎳ㄦ椽鏁冮崒姘虫憰闂佺粯鏌ㄩ〃搴ㄥ吹閺囥垻鍙撻柛銉ｅ劚閸斻倗绱掗埀?
//    @Transactional
//    @Override
//    public void handleStockIn(InStockRequestDTO request, Integer operatorId) {
//        Product product = productMapper.selectById(request.getProductId());
//        if (product == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
//
//        // 2. 闂備浇宕垫慨宕囨閵堝洦顫曢柡鍥ュ灪閸嬧晛鈹戦悩瀹犲缂佺姵鍨圭槐鎾存媴閼测剝鍨甸埢鎾诲醇閺囩啿鎷?
//        Integer totalQuantity = request.getLocations().stream()
//                .map(InStockRequestDTO.LocationDTO::getQuantity)
//                .reduce(0, Integer::sum);
//
//        // 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷顔煎缂佲偓瀹€鍕厸鐎广儱楠搁獮妯尖偓瑙勬穿缂嶄線骞冪憴鍕閻熸瑥瀚崙锛勭磽?//        Assay assay = getAssayByProductIdAndDate(request.getProductId(), LocalDate.now());
//        if (assay == null) {
//            throw new BusinessException(ErrorCode.ASSAY_RECORD_NOT_FOUND);
//        }
//
//        SemiProductRecord semiProductRecord = semiProductRecordMapper
//                .selectByProductIdAndDate(request.getSemiProductId(), request.getSemiDate());
//        if(semiProductRecord == null){
//            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
//        }
//
//        // 3. 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰靛殭缂佲偓閸岀偞鐓忓璺虹墕閸斻倖鎱ㄥΟ绋垮闁诡喛顫夐幏鍛喆閸曨厼鍤掔紓?//        InStock inStock = new InStock();
//        inStock.setProductId(request.getProductId());
//        inStock.setWarehouseId(request.getWarehouseId());
//        inStock.setQuantity(totalQuantity);
//        inStock.setWeightPerPiece(product.getWeightPerPiece());
//        inStock.setTotalWeight(product.getWeightPerPiece().multiply(new BigDecimal(totalQuantity)));
//        inStock.setEntryDate(LocalDate.now());
//        inStock.setAssayId(assay.getId());
//        inStock.setSemiProductRecordId(semiProductRecord.getId());
//        inStock.setScreenMeshId(request.getScreenMeshId());
//        inStock.setCreatedAt(LocalDateTime.now());
//        inStock.setCreatedBy(operatorId);
//
//        if(inStockMapper.insert(inStock) < 1){
//            throw new BusinessException(ErrorCode.STOCK_IN_FAILED);
//        }
//
//        Inventory inventory = inventoryMapper.existSameInventory(
//                request.getWarehouseId(), product.getId(), LocalDate.now(), request.getScreenMeshId());
//        // 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍛櫣闁告瑥锕ラ妵鍕冀閵娧屾殹闂佺楠搁敃锕傚焵椤掑倹鍤€闁圭寽銈冧汗闁告劦鍠楅崑銈夋煏婵炵偓娅呴柟鐟扮埣閺屾洘绻涢崹顔煎Б濠殿喗菧閸旀垿骞冨Δ鈧埥澶娾枎濡厧濮兼俊鐐€栭弻銊ッ洪埡鍛?
//        if(inventory != null) {
//            // 闂傚倷绀侀幖顐⒚洪妶澶嬪仱闁靛ň鏅涢拑鐔封攽閻樻彃鏆為柛搴ｅ枛閺岀喖骞嗚閿涘秹鏌熼悾灞叫㈤柍钘夘樀楠炴鈧稒锕╁鈥愁渻閵堝棙鐓ユ繛娴嬫櫊婵?
//            inventoryMapper.updateInventory(
//                    null,
//                    inventory.getTotalQuantity() + totalQuantity,
//                    null,
//                    null,
//                    inventory.getInStockId(),
//                    request.getScreenMeshId()
//            );
//        } else {
//            // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幆褏鎽犻柛搴ｅ枛閺岀喖骞嗚閿涘秹鏌熼悾灞叫㈤柍钘夘樀楠炴鈧稒锕╁鈥愁渻閵堝棙鐓ユ繛娴嬫櫊婵?
//            inventory = new Inventory();
//            inventory.setProductId(product.getId());
//            inventory.setWarehouseId(request.getWarehouseId());
//            inventory.setEntryDate(LocalDate.now());
//            inventory.setInStockId(inStock.getId());
//            inventory.setScreenMeshId(request.getScreenMeshId());
//            inventory.setAssayId(assay.getId());
//            inventory.setTotalQuantity(totalQuantity);
//            inventory.setCreatedAt(LocalDateTime.now());
//            if (inventoryMapper.insert(inventory) < 1){
//                throw new BusinessException(ErrorCode.STOCK_IN_FAILED);
//            }
//        }
//
//        // 5. 闂傚倷绀佸﹢杈╁垝椤栨粍鏆滈柟鐑橆殔閻鏌涢埄鍐槈缂佺媴缍侀弻鈥愁吋閸愩劌顬夌紓浣鸿檸閸ㄥ爼寮婚敓鐘茬＜婵炴垶鐟ч崙锛勭磽娴ｉ潧濮€濞存粠鍓熼崺鈧い鎺嶈兌閳洟鎯囨径宀€纾界€广儱鎳忛崳鐣岀磼鏉堛劍顥堟い銏＄懃閳诲酣骞嗚閺嗩亪鏌ｉ悢鍝ョ煂濠⒀勵殔鐓ら柍鍝勫暞椤洘绻涢崱妯诲碍缂佺姰鍎抽幉鎼佹偋閸繄鐟ㄥ┑鐐叉噷閸婃繈寮婚悢鐓庡瀭妞ゆ梹瀚庨敍鍕＝鐎广儱妫欓ˉ鍫ユ煛?
//        List<InventoryLocation> locationsToInsert = new ArrayList<>();
//        for (InStockRequestDTO.LocationDTO dto : request.getLocations()) {
//            // 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍛櫣闁告瑥锕ラ妵鍕冀閵娧屾殹闂佺楠搁敃锕傚焵椤掑倹鍤€闁圭寽銈冧汗闁告劦鍠楅崑銈夋煏婵炵偓娅呴柟鐟扮埣閺屾洘绻濇惔锝呭弗闂佹悶鍊曢ˇ顖炴箒闂佹寧绻傞幊鎰€撮梻?//            InventoryLocation location = inventoryLocationMapper.selectForUpdate(
//                    inventory.getId(),
//                    dto.getCoordinates().getX(),
//                    dto.getCoordinates().getY()
//            );
//
//            if (location == null) {
//                // 婵犵數濮烽。浠嬪焵椤掆偓閸熷潡鍩€椤掆偓缂嶅﹪骞冨Ο璇茬窞閻庯綆鍋勯鎾绘倵楠炲灝鍔氭繛灞傚姂瀵悂宕掗悙鏉戜化婵炶揪缍€椤娆㈤懠顒傜＝鐎广儱鎳愭牎缂備礁顑呴ˇ顖烆敇婵傜宸濇い鏍ㄧ⊕閸犳牠姊绘笟鈧鑽も偓闈涚焸閹剝寰勭仦缁㈡綗濠电娀娼ч鍛涘鈧弻鈩冨緞鐎ｉ潧鍔岄梺鍝勵槸椤兘骞冪憴鍕閻熸瑥瀚崙锛勭磽?//                location = new InventoryLocation();
//                location.setInventoryId(inventory.getId());
//                location.setCoordinateX(dto.getCoordinates().getX());
//                location.setCoordinateY(dto.getCoordinates().getY());
//                location.setQuantity(dto.getQuantity());
//                locationsToInsert.add(location);
//            } else {
//                // 婵犵數濮烽。浠嬪焵椤掆偓閸熷潡鍩€椤掆偓缂嶅﹪骞冨Ο璇茬窞閻庯綆鍋勯鎾绘倵楠炲灝鍔氭繛灞傚姂瀵悂宕掗悙鏉戜化婵炶揪缍€椤娆㈤懠顒傜＝鐎广儱鎳愬瓭濡炪倖鏌ㄧ换姗€鐛幘璇茬闁哄啠鍋撻柟鎻掋偢濮婃椽妫冮埡浣烘В闂佺粯鎸搁ˇ鍗炍ｉ幇顑芥斀閻庯綆浜滃畵鍡涙煟鎼淬垻鈯曢拑閬嶆煟閹绢垰浜鹃梻鍌欐祰濡椼劌顪冮幒妤€纭€闁规儼妫勯梻?//                inventoryLocationMapper.AddQuantity(location.getId(), dto.getQuantity());
//            }
//        }
//
//        // 闂傚倷绀佺紞濠傤焽瑜忕槐鐐寸節閸パ囨７濠电偛妯婃禍婊呯不閻㈠憡鐓欓柣鎴灻悘銉р偓娈垮枟婵炲﹪寮婚敓鐘茬＜婵炴垶锕╁Λ鍡椻攽閻愯尙澧涢柛銊ョ埣楠炲棝寮崼婵堫啋閻庤娲栧ú锕傚箟閸忚偐绠鹃弶鍫濆⒔閸掓壆绱掓径濠勫煟闁诡喖娼″畷鍗炩枎閹邦剦鈧盯姊洪崫鍕垫Ъ婵炲娲滅划?
//        if (!locationsToInsert.isEmpty()) {
//            if (inventoryLocationMapper.batchInsert(locationsToInsert) < 1) {
//                throw new BusinessException(ErrorCode.INSERT_INVENTORY_LOCATION_FAILED);
//            }
//        }
//    }

    @Override
    public PageResult<InStockVO> queryInStockRecords(InStockQueryDTO queryDTO, User currentUser) {
        // 闂備浇宕垫慨宕囨閵堝洦顫曢柡鍥ュ灪閸嬧晛鈹戦悩瀹犲缂佲偓閸℃稒鐓曢柍鈺佸暟閳笺倝鏌嶈閸撴瑩藝闂堟稓鏆﹂柟杈剧悼閻も偓闂佸搫娲ら幊澶愬疾閻樿钃?
        int offset = (queryDTO.getPage() - 1) * queryDTO.getSize();

        // 闂傚倷绀侀幉锛勬暜閸ヮ剙纾归柡宥庡幖閽冪喖鏌涢妷顔煎闁告瑥锕ラ妵鍕冀閵娧屾殹闂佺楠搁敃銈夊煡婢舵劕绠婚柧蹇ｅ亝閸庢捇姊虹憴鍕祷闁哥喎娼￠、?
        Boolean isStaff = currentUser.getRoleCode().equals("STAFF");

        // 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷顔煎缂佲偓閸℃稒鐓曢柍鈺佸暟閳笺倝鏌嶈閸撴瑩藝闂堟侗鍤曟い鎰堕檮閸嬪鏌涢锝囩畼闁?
        List<InStockVO> records = inStockMapper.selectInStockList(
                queryDTO,
                currentUser.getId(),
                isStaff,
                offset,
                queryDTO.getSize()
        );

        for (InStockVO record : records) {
            Integer id = record.getTestedBy();
            if (id == null) continue;
            String testerName = userMapper.selectById(id).getName();
            record.setTesterName(testerName);

            if (isStaff) {
                record.setAssayId(null);
                record.setSampleDate(null);
                record.setColorValue(null);
                record.setReducingSugar(null);
                record.setDryWeight(null);
                record.setConductivityAsh(null);
                record.setSucrose(null);
                record.setInsolubleImpurity(null);
                record.setPhValue(null);
                record.setTestedBy(null);
                record.setTesterName(null);
                record.setIsQualified(null);
                record.setQualifiedStandards(null);
            }
        }

        System.out.println("records:" + records);

        // 闁兼儳鍢茶ぐ鍥箑閺勫浚鍞剁憸鐗堟礃閺?
        Long total = inStockMapper.countInStockRecords(
                queryDTO,
                currentUser.getId(),
                isStaff
        );
        System.out.println("total:" + total);

        return new PageResult<>(total, records);
    }

    private Assay getAssayByProductIdAndDate(Integer productId, LocalDate date) {
        // 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柛娆忣槺閻濊埖淇婇婵撻獜濞存粌缍婇弻鐔煎礈瑜嶆禒鍝劽瑰搴＄仸闁哄矉缍佹俊鎼佸Ψ閵夘喕鎮ｉ梺璇查叄濞佳冾嚕閸洏鈧啴濡烽妷褎娈曢梺鍛婂姈閸庢娊寮ぐ鎺撳仭?
        return assayMapper.selectByProductIdAndDate(productId, date);
    }

    @Override
    public InStock findById(Integer id) {
        return inStockMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "in_stock";
    }

    private String convertToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]"; // 闂傚倷绀侀幉锟犳偡閿曞倸鍨傜憸鐗堝笒閺嬩線鏌熼悙顒€澧柛銈嗘礋閺屾稓浠﹂悙顒傛闂佺硶鏅滈〃濠囧蓟閿熺姴绀冩い鎾楀啯娅嶇紓鍌欐祰妞村摜鎹㈤崘顭戠劷濠电姵鑹惧Λ姗€鏌熺粙鎸庢崳妞わ负鍎崇槐?JSON
        }
    }
}
