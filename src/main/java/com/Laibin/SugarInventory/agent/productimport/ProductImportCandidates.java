package com.Laibin.SugarInventory.agent.productimport;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.security.AgentEntityRefCodec;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.mapper.ProductImportCatalogMapper;
import com.Laibin.SugarInventory.mapper.ProductImportCatalogMapper.Entry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductImportCandidates {
    private static final int SHORTLIST_SIZE = 10;
    private record Ranked(Entry entry, double score, boolean exact) {}
    private final ProductImportAccessService access;
    private final ProductImportDraftStore store;
    private final ProductImportCatalogMapper catalog;
    private final AgentEntityRefCodec refs;
    public record Candidate(String ref, boolean hasInventory, Map<String,Object> values) {}
    public record Evidence(ProductImportWorkbookReader.SourceRow source, List<Integer> duplicateRows,
                           List<Candidate> candidates, boolean allCandidatesShown, int exactCount) {}

    public Evidence read(LoginUser user, String session, String attachment, int rowNumber) {
        int owner = access.requireCurrent(user, session);
        var draft = store.get(owner, session, attachment);
        var source = draft.rows().stream().filter(r -> r.rowNumber() == rowNumber).findFirst()
                .orElseThrow(() -> new BusinessException(400, "未找到所选表格行，请重新选择"));
        var name = key(source.values().get("productName"));
        if (name.isBlank()) return new Evidence(source, List.of(), List.of(), false, 0);
        var duplicates = draft.rows().stream().filter(r -> name.equals(key(r.values().get("productName"))))
                .map(ProductImportWorkbookReader.SourceRow::rowNumber).toList();
        List<Entry> entries = catalog.readForMatching();
        if (entries.size()>500) throw new BusinessException(400, "产品目录超出本次对比上限，未生成新增或修改建议");
        if (entries.stream().anyMatch(e -> e.getId() == null || key(e.getProductName()).isBlank() || e.getHasInventory() == null))
            throw new BusinessException(409, "产品目录信息不完整，暂时无法对比");
        var sourceGrams = grams(name);
        var ranked = entries.stream().map(entry -> {
            String other = key(entry.getProductName());
            boolean exact = name.equals(other);
            return new Ranked(entry, exact ? 10 : score(sourceGrams, other, entry, source.values()), exact);
        }).sorted(Comparator.comparingDouble(Ranked::score).reversed()
                .thenComparing(rank -> rank.entry().getId())).toList();
        int exact = (int) ranked.stream().filter(Ranked::exact).count();
        var matches = ranked.stream().limit(SHORTLIST_SIZE).map(Ranked::entry).map(e -> {
            Map<String,Object> values = new LinkedHashMap<>();
            values.put("productName", e.getProductName()); values.put("productType",e.getProductType());
            values.put("status",e.getStatus()); values.put("packagingMethod",e.getPackagingMethod());
            values.put("weightPerPiece",e.getWeightPerPiece()); values.put("piecesPerPallet",e.getPiecesPerPallet());
            values.put("canStack",e.getCanStack()); values.put("screenMeshName",e.getScreenMeshName());
            values.values().removeIf(Objects::isNull);
            return new Candidate(refs.encode("PRODUCT_IMPORT_TARGET",e.getId(),owner,
                    "product-import:"+session+":"+attachment+":"+rowNumber),Boolean.TRUE.equals(e.getHasInventory()),Map.copyOf(values));
        }).toList();
        return new Evidence(source, duplicates.size()>1 ? duplicates : List.of(), matches,
                entries.size() <= SHORTLIST_SIZE, exact);
    }

    private static String key(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s,Normalizer.Form.NFC).replace('（','(').replace('）',')').replaceAll("(?U)\\s+", " ").strip();
    }
    // Retrieval only. Neither textual overlap nor identical metadata establishes semantic identity.
    private static double score(Set<String> a, String other, Entry e, Map<String,String> source) {
        var b=grams(other);
        double overlap = a.stream().filter(b::contains).count();
        return 2*overlap/Math.max(1,a.size()+b.size())
                + (source.containsKey("productType") && Objects.equals(source.get("productType"), e.getProductType()) ? .15 : 0)
                + (source.containsKey("packagingMethod") && Objects.equals(source.get("packagingMethod"),e.getPackagingMethod()) ? .1 : 0);
    }
    private static Set<String> grams(String s) {
        Set<String> result=new HashSet<>();
        for(int i=0;i<s.length();i++) { result.add(s.substring(i,i+1)); if(i+1<s.length()) result.add(s.substring(i,i+2)); }
        return result;
    }
}
