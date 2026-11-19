package com.Laibin.SugarInventory.agent.productimport;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.security.AgentEntityRefCodec;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.mapper.ProductImportCatalogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses a private ephemeral Redis child process. Never points to the application's database. */
@EnabledIfSystemProperty(named="productImport.redisBinary", matches=".+")
class ProductImportRedisIntegrationTest {
    @TempDir Path temp;

    @Test void uploadRecoverCompareAndRemoveActualWorkbook() throws Exception {
        int port;
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = socket.getLocalPort(); }
        var password = UUID.randomUUID().toString();
        var child = new ProcessBuilder(System.getProperty("productImport.redisBinary"), "--bind", "127.0.0.1",
                "--port", String.valueOf(port), "--requirepass", password, "--appendonly", "no")
                .directory(temp.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            boolean ready = false;
            for (int i=0; i<100 && child.isAlive(); i++) {
                try (var s = new Socket()) { s.connect(new InetSocketAddress("127.0.0.1", port), 100); ready = true; break; }
                catch (java.io.IOException startup) { Thread.sleep(50); }
            }
            assertTrue(ready, "临时 Redis 未启动");
            var config = new RedisStandaloneConfiguration("127.0.0.1", port);
            config.setPassword(password);
            var factory = new LettuceConnectionFactory(config);
            factory.afterPropertiesSet();
            try { verifyWorkflow(new StringRedisTemplate(factory)); } finally { factory.destroy(); }
        } finally {
            child.destroy();
            if (!child.waitFor(5, TimeUnit.SECONDS)) child.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        }
    }

    private static void verifyWorkflow(StringRedisTemplate redis) throws Exception {
        byte[] bytes;
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("产品");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("产品名称"); header.createCell(1).setCellValue("单件重量(kg)");
            var row = sheet.createRow(4);
            row.createCell(0).setCellValue("黄冰糖(袋)"); row.createCell(1).setCellValue(25);
            wb.write(out); bytes = out.toByteArray();
        }
        var file = new MockMultipartFile("file","车间产品.xlsx","application/octet-stream",bytes);
        var user = mock(LoginUser.class);
        var access = mock(ProductImportAccessService.class);
        when(access.requireCurrent(user,"local")).thenReturn(7);
        var store = new ProductImportDraftStore(redis, new ObjectMapper());
        var uploads = new ProductImportUploadService(access,store);
        assertEquals("产品", uploads.inspect(user,"local",file).get(0).name());
        var draft = uploads.upload(user,"local",file,"产品", Map.of(0,"productName",1,"weightPerPiece"));
        var recovered = new ProductImportDraftStore(redis,new ObjectMapper());
        assertEquals(draft, recovered.list(7,"local").get(0));
        assertTrue(recovered.list(7,"unrelated").isEmpty());
        assertThrows(BusinessException.class, () -> recovered.get(8,"local",draft.reference()));
        var catalog = mock(ProductImportCatalogMapper.class);
        var e = new ProductImportCatalogMapper.Entry();
        e.setId(1); e.setProductName("黄冰糖（袋）"); e.setHasInventory(true);
        when(catalog.readForMatching()).thenReturn(List.of(e));
        var service = new ProductImportCandidates(access,recovered,catalog,
                new AgentEntityRefCodec("local-test-only-012345678901234567890123",600));
        var result = service.read(user,"local",draft.reference(),5);
        assertEquals("25",result.source().values().get("weightPerPiece"));
        assertEquals(1,result.exactCount());
        assertEquals("黄冰糖（袋）",result.candidates().get(0).values().get("productName"));
        recovered.delete(7,"local",draft.reference());
        assertTrue(store.list(7,"local").isEmpty());
        assertThrows(BusinessException.class, () -> service.read(user,"local",draft.reference(),5));
    }
}
