package com.Laibin.SugarInventory.agent.productimport;

import com.Laibin.SugarInventory.common.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Short-lived source data, NOT a confirmed operation or a durable execution archive. */
@Service
public class ProductImportDraftStore {
    static final int MAX_DRAFTS_PER_USER = 3;
    static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private static final DefaultRedisScript<Long> DELETE_IF_UNCHANGED = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public ProductImportDraftStore(StringRedisTemplate redis, ObjectMapper json) {
        this(redis, json, Clock.systemUTC());
    }

    ProductImportDraftStore(StringRedisTemplate redis, ObjectMapper json, Clock clock) {
        this.redis = redis;
        this.json = json;
        this.clock = clock;
    }

    public record Draft(String reference, Integer owner, String sessionId, String fileName, String sha256,
                        long expiresAt, List<ProductImportWorkbookReader.SourceRow> rows) {
        public Draft { rows = List.copyOf(rows); }
    }

    public Draft create(Integer owner, String sessionId, String fileName, String sha256,
                        List<ProductImportWorkbookReader.SourceRow> rows) {
        if (rows == null || rows.isEmpty() || rows.size() > ProductImportWorkbookReader.MAX_ROWS)
            throw new BusinessException(400, "表格记录数无效");
        Draft draft = new Draft(UUID.randomUUID().toString(), owner, sessionId, fileName, sha256,
                clock.millis() + TTL.toMillis(), rows);
        String encoded;
        try { encoded = json.writeValueAsString(draft); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize product import draft", e); }
        if (encoded.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES)
            throw new BusinessException(400, "表格文字过多，请拆分文件后再上传");
        // Fixed per-user slots: atomic SET NX + TTL bounds concurrent uploads across every session and JVM.
        for (int slot = 0; slot < MAX_DRAFTS_PER_USER; slot++) {
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(owner, slot), encoded, TTL))) return draft;
        }
        throw new BusinessException(429, "最多同时保留3份导入表格，请先移除不再使用的表格，或30分钟后重试");
    }

    public Draft get(Integer owner, String sessionId, String reference) {
        return find(owner, sessionId, reference).draft();
    }

    /** Recovery after a lost upload response; bounded to this user's three slots and the current session. */
    public List<Draft> list(Integer owner, String sessionId) {
        var result = new java.util.ArrayList<Draft>();
        for (int slot = 0; slot < MAX_DRAFTS_PER_USER; slot++) {
            String encoded = redis.opsForValue().get(key(owner, slot));
            if (encoded == null) continue;
            Draft draft = decode(encoded);
            if (Objects.equals(draft.owner(), owner) && Objects.equals(draft.sessionId(), sessionId)
                    && draft.expiresAt() > clock.millis()) result.add(draft);
        }
        result.sort(java.util.Comparator.comparingLong(Draft::expiresAt).reversed());
        return List.copyOf(result);
    }

    public void delete(Integer owner, String sessionId, String reference) {
        Stored found = find(owner, sessionId, reference);
        // Do not delete a newer upload if the old slot expired/reused between read and delete.
        redis.execute(DELETE_IF_UNCHANGED, List.of(found.key()), found.encoded());
    }

    private record Stored(String key, String encoded, Draft draft) {}

    private Stored find(Integer owner, String sessionId, String reference) {
        if (reference == null || !reference.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) throw missing();
        for (int slot = 0; slot < MAX_DRAFTS_PER_USER; slot++) {
            String key = key(owner, slot);
            String encoded = redis.opsForValue().get(key);
            if (encoded == null) continue;
            Draft draft = decode(encoded);
            if (Objects.equals(draft.reference(), reference) && Objects.equals(draft.owner(), owner)
                    && Objects.equals(draft.sessionId(), sessionId) && draft.expiresAt() > clock.millis())
                return new Stored(key, encoded, draft);
        }
        throw missing();
    }

    private Draft decode(String encoded) {
        try { return json.readValue(encoded, Draft.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot read product import draft", e); }
    }

    private static String key(Integer owner, int slot) { return "agent:product-import:{" + owner + "}:" + slot; }
    private static BusinessException missing() { return new BusinessException(404, "表格不存在或已过期，请重新上传"); }
}
