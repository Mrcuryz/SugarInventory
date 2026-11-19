package com.Laibin.SugarInventory.agent.productimport;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
public class ProductImportUploadService {
    private final ProductImportAccessService access;
    private final ProductImportDraftStore drafts;
    private final ProductImportWorkbookReader reader = new ProductImportWorkbookReader();
    private final Semaphore parsing = new Semaphore(2);

    /** Metadata only; unknown columns are returned for user mapping, not sent to the model as instructions. */
    public List<ProductImportWorkbookReader.SheetInfo> inspect(LoginUser user, String sessionId, MultipartFile file) {
        access.requireCurrent(user, sessionId);
        return parse(file, reader::inspect);
    }

    public ProductImportDraftStore.Draft upload(LoginUser user, String sessionId, MultipartFile file,
                                                String sheet, Map<Integer, String> mapping) {
        Integer owner = access.requireCurrent(user, sessionId);
        return parse(file, content -> {
            var rows = reader.read(content, sheet, mapping);
            // Recheck after parsing, including permission/session revocation during the upload.
            access.requireCurrent(user, sessionId);
            return drafts.create(owner, sessionId, safeName(file.getOriginalFilename()), digest(content), rows);
        });
    }

    private interface Parser<T> { T read(byte[] content) throws IOException; }

    private <T> T parse(MultipartFile file, Parser<T> parser) {
        if (file == null || file.isEmpty() || file.getSize() > ProductImportWorkbookReader.MAX_BYTES)
            throw new BusinessException(400, "请选择不超过10MB的Excel文件");
        String name = safeName(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls"))
            throw new BusinessException(400, "仅支持xls或xlsx格式的Excel文件");
        if (!parsing.tryAcquire()) throw new BusinessException(429, "正在读取其他表格，请稍后重试");
        try (var stream = file.getInputStream()) {
            byte[] bytes = stream.readNBytes(ProductImportWorkbookReader.MAX_BYTES + 1);
            if (bytes.length > ProductImportWorkbookReader.MAX_BYTES)
                throw new BusinessException(400, "请选择不超过10MB的Excel文件");
            return parser.read(bytes);
        } catch (BusinessException e) {
            throw e;
        } catch (ProductImportWorkbookReader.ValidationException e) {
            throw new BusinessException(400, e.getMessage());
        } catch (IOException | IllegalArgumentException | org.apache.poi.EncryptedDocumentException
                 | org.apache.poi.ooxml.POIXMLException | org.apache.poi.util.RecordFormatException e) {
            throw invalidFile();
        } finally {
            parsing.release();
        }
    }

    private BusinessException invalidFile() { return new BusinessException(400, "无法读取表格，请确认文件未加密且格式完整"); }

    static String safeName(String raw) {
        if (raw == null) return "";
        String name = raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        return name.length() <= 160 ? name : name.substring(name.length() - 160);
    }

    private static String digest(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
