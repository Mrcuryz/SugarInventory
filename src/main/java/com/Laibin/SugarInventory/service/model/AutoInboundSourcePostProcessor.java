package com.Laibin.SugarInventory.service.model;

import com.Laibin.SugarInventory.domain.po.ParsedInboundItem;
import com.Laibin.SugarInventory.domain.po.ParsedSemiSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutoInboundSourcePostProcessor {

    private static final Pattern PRINT_DATE_PATTERN = Pattern.compile(
            "(?:箱印|袋印|合格证|证印)?\\s*(20\\d{2})[.\\-/年](\\d{1,2})[.\\-/月](\\d{1,2})");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(20\\d{2})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*(?:号|日)?");
    private static final Pattern MONTH_DAY_QUANTITY_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*(?:号|日)?\\s*(?:(\\d+)\\s*板)?\\s*(?:(?:十|\\+|＋)\\s*)?(?:(\\d+)\\s*件)?");

    private AutoInboundSourcePostProcessor() {
    }

    public static List<ParsedSemiSource> normalizeSources(ParsedInboundItem item, LocalDate requestDate, LocalDate finishedDate) {
        if (item == null || item.getSources() == null || item.getSources().isEmpty()) {
            return List.of();
        }
        Integer year = inferYear(item, requestDate, finishedDate);
        List<ParsedSemiSource> result = new ArrayList<>();
        for (ParsedSemiSource source : item.getSources()) {
            result.addAll(normalizeSource(source, item.getRawBlock(), year));
        }
        return result;
    }

    public static boolean isInvalidFinishedItem(ParsedInboundItem item) {
        if (item == null || !"FINISHED_PRODUCT_IN".equalsIgnoreCase(item.getType())) {
            return false;
        }
        ParsedInboundItem.Quantity quantity = item.getQuantity();
        int pallets = quantity == null || quantity.getPallets() == null ? 0 : quantity.getPallets();
        int pieces = quantity == null || quantity.getPieces() == null ? 0 : quantity.getPieces();
        return item.getProductId() == null
                && !notBlank(item.getProductName())
                && pallets <= 0
                && pieces <= 0;
    }

    public static ParsedSemiSource sourceFromInvalidFinishedItem(ParsedInboundItem item) {
        if (item == null) {
            return null;
        }
        String materialName = firstNonBlank(item.getProductNameRaw(), item.getProductName());
        if (!notBlank(materialName) && !notBlank(item.getRawBlock())) {
            return null;
        }
        ParsedSemiSource source = new ParsedSemiSource();
        source.setSourceType("SEMI_PRODUCT_IN");
        source.setProductNameRaw(materialName);
        source.setProductName(item.getProductName());
        source.setRemark(firstNonBlank(item.getRawBlock(), materialName));
        ParsedInboundItem.Quantity quantity = item.getQuantity();
        if (quantity != null) {
            source.setBoardCount(quantity.getPallets());
            source.setPieceCount(quantity.getPieces());
        }
        return source;
    }

    static List<ParsedSemiSource> normalizeSource(ParsedSemiSource source, String rawBlock, Integer year) {
        if (source == null) {
            return List.of();
        }
        LocalDate explicitDate = parseDate(source.getProductionDate(), year);
        if (explicitDate != null) {
            ParsedSemiSource copy = copySource(source);
            copy.setProductionDate(explicitDate.toString());
            if ((copy.getBoardCount() == null || copy.getBoardCount() <= 0)
                    && (copy.getPieceCount() == null || copy.getPieceCount() <= 0)) {
                List<DatedQuantity> quantities = extractDatedQuantities(source.getRemark(), explicitDate.getYear());
                if (!quantities.isEmpty()) {
                    copy.setBoardCount(quantities.get(0).boardCount());
                    copy.setPieceCount(quantities.get(0).pieceCount());
                    copy.setRemark(buildRemark(source, quantities.get(0).rawText()));
                }
            }
            return List.of(copy);
        }
        List<DatedQuantity> quantities = extractDatedQuantities(source.getRemark(), year);
        if (quantities.isEmpty() && !notBlank(source.getProductionDate())) {
            quantities = extractDatedQuantities(rawBlock, year);
        }
        if (!quantities.isEmpty()) {
            List<ParsedSemiSource> split = new ArrayList<>();
            for (DatedQuantity quantity : quantities) {
                ParsedSemiSource copy = copySource(source);
                copy.setProductionDate(quantity.date().toString());
                copy.setBoardCount(quantity.boardCount());
                copy.setPieceCount(quantity.pieceCount());
                copy.setRemark(buildRemark(source, quantity.rawText()));
                split.add(copy);
            }
            return split;
        }
        ParsedSemiSource copy = copySource(source);
        copy.setProductionDate(null);
        return List.of(copy);
    }

    static List<DatedQuantity> extractDatedQuantities(String text, Integer year) {
        if (!notBlank(text) || year == null) {
            return List.of();
        }
        List<DatedQuantity> result = new ArrayList<>();
        Matcher matcher = MONTH_DAY_QUANTITY_PATTERN.matcher(text);
        while (matcher.find()) {
            int month = parseInt(matcher.group(1));
            int day = parseInt(matcher.group(2));
            int boards = parseInt(matcher.group(3));
            int pieces = parseInt(matcher.group(4));
            if (boards <= 0 && pieces <= 0) {
                continue;
            }
            LocalDate date = safeDate(year, month, day);
            if (date == null) {
                continue;
            }
            result.add(new DatedQuantity(date, boards, pieces, matcher.group().trim()));
        }
        return result;
    }

    private static Integer inferYear(ParsedInboundItem item, LocalDate requestDate, LocalDate finishedDate) {
        Integer printYear = extractPrintYear(safe(item.getRawBlock()) + "\n" + safe(item.getRemark()));
        if (printYear != null) {
            return printYear;
        }
        LocalDate itemDate = parseDate(item.getProductionDate(), null);
        if (itemDate != null) {
            return itemDate.getYear();
        }
        if (finishedDate != null) {
            return finishedDate.getYear();
        }
        return requestDate == null ? null : requestDate.getYear();
    }

    private static Integer extractPrintYear(String text) {
        if (!notBlank(text)) {
            return null;
        }
        Matcher matcher = PRINT_DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return parseInt(matcher.group(1));
    }

    private static LocalDate parseDate(String text, Integer fallbackYear) {
        if (!notBlank(text)) {
            return null;
        }
        String value = text.trim();
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            // Try relaxed formats below.
        }
        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(value);
        if (isoMatcher.find()) {
            return safeDate(parseInt(isoMatcher.group(1)), parseInt(isoMatcher.group(2)), parseInt(isoMatcher.group(3)));
        }
        if (fallbackYear != null) {
            Matcher monthDayMatcher = MONTH_DAY_PATTERN.matcher(value);
            if (monthDayMatcher.find()) {
                return safeDate(fallbackYear, parseInt(monthDayMatcher.group(1)), parseInt(monthDayMatcher.group(2)));
            }
        }
        return null;
    }

    private static ParsedSemiSource copySource(ParsedSemiSource source) {
        ParsedSemiSource copy = new ParsedSemiSource();
        copy.setSourceType(source.getSourceType());
        copy.setProductNameRaw(source.getProductNameRaw());
        copy.setSemiProductId(source.getSemiProductId());
        copy.setProductName(source.getProductName());
        copy.setProductionDate(source.getProductionDate());
        copy.setBoardCount(source.getBoardCount());
        copy.setPieceCount(source.getPieceCount());
        copy.setWarehouseHint(source.getWarehouseHint());
        copy.setBatchNo(source.getBatchNo());
        copy.setRemark(source.getRemark());
        return copy;
    }

    private static String buildRemark(ParsedSemiSource source, String datedText) {
        String name = firstNonBlank(source.getProductNameRaw(), source.getProductName());
        if (notBlank(name) && notBlank(datedText)) {
            return name + "；" + datedText;
        }
        return firstNonBlank(source.getRemark(), datedText);
    }

    private static LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int parseInt(String value) {
        if (!notBlank(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String first, String second) {
        if (notBlank(first)) {
            return first;
        }
        return notBlank(second) ? second : null;
    }

    record DatedQuantity(LocalDate date, int boardCount, int pieceCount, String rawText) {
    }
}
