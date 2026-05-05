package com.example.core;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PageResultTest {

    @Test
    void constructor_normalCase() {
        List<JsonObject> items = List.of(
            new JsonObject().put("id", 1),
            new JsonObject().put("id", 2)
        );
        PageResult<JsonObject> result = new PageResult<>(items, 55, 3, 10);
        assertEquals(2, result.getList().size());
        assertEquals(55L, result.getTotal());
        assertEquals(3, result.getPage());
        assertEquals(10, result.getSize());
        assertEquals(6, result.getPages()); // ceil(55/10)
    }

    @Test
    void constructor_exactDivision() {
        PageResult<?> result = new PageResult<>(List.of(), 100, 5, 20);
        assertEquals(5, result.getPages());
    }

    @Test
    void constructor_zeroTotal() {
        PageResult<?> result = new PageResult<>(List.of(), 0, 1, 10);
        assertEquals(0, result.getPages());
        assertEquals(0L, result.getTotal());
    }

    @Test
    void constructor_zeroSize_noDivisionByZero() {
        PageResult<?> result = new PageResult<>(List.of(), 10, 1, 0);
        assertEquals(0, result.getPages()); // guarded against /0
    }

    @Test
    void constructor_singlePage() {
        PageResult<?> result = new PageResult<>(List.of("a", "b"), 2, 1, 20);
        assertEquals(1, result.getPages());
    }

    @Test
    void toJson_basicStructure() {
        List<JsonObject> items = List.of(new JsonObject().put("name", "test"));
        PageResult<JsonObject> result = new PageResult<>(items, 1, 1, 10);
        JsonObject json = result.toJson();

        assertTrue(json.containsKey("list"));
        assertTrue(json.containsKey("total"));
        assertTrue(json.containsKey("page"));
        assertTrue(json.containsKey("size"));
        assertTrue(json.containsKey("pages"));
        assertEquals(1L, json.getLong("total"));
        assertEquals(1, json.getInteger("page"));
        assertEquals(10, json.getInteger("size"));
    }

    @Test
    void toJson_listContainsJsonObjects() {
        List<JsonObject> items = List.of(new JsonObject().put("id", 42));
        PageResult<JsonObject> result = new PageResult<>(items, 1, 1, 10);
        JsonObject json = result.toJson();
        assertEquals(1, json.getJsonArray("list").size());
        assertEquals(42, json.getJsonArray("list").getJsonObject(0).getInteger("id"));
    }

    @Test
    void toJson_handlesStringList() {
        List<String> items = List.of("apple", "banana");
        PageResult<String> result = new PageResult<>(items, 2, 1, 10);
        JsonObject json = result.toJson();
        assertEquals(2, json.getJsonArray("list").size());
        assertEquals("apple", json.getJsonArray("list").getString(0));
    }

    @Test
    void toJson_handlesNullList() {
        PageResult<?> result = new PageResult<>(null, 0, 1, 10);
        JsonObject json = result.toJson();
        assertNotNull(json.getJsonArray("list"));
        assertEquals(0, json.getJsonArray("list").size());
    }

    @Test
    void setters_work() {
        PageResult<Object> result = new PageResult<>();
        result.setList(List.<Object>of("a"));
        result.setTotal(5);
        result.setPage(2);
        result.setSize(10);
        result.setPages(1);

        assertEquals(1, result.getList().size());
        assertEquals(5L, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(10, result.getSize());
    }
}
