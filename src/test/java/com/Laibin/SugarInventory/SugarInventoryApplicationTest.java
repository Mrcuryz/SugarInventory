package com.Laibin.SugarInventory;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SugarInventoryApplicationTest {

    @Test
    void registersMybatisPlusPaginationInterceptor() {
        MybatisPlusInterceptor interceptor =
                new SugarInventoryApplication().mybatisPlusInterceptor();

        assertEquals(1, interceptor.getInterceptors().size());
        assertInstanceOf(
                PaginationInnerInterceptor.class,
                interceptor.getInterceptors().get(0)
        );
    }
}
