package com.example.core;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * Page Result - 分页结果
 */
public class PageResult<T> {

    private List<T> list;      // 数据列表
    private long total;        // 总记录数
    private int page;          // 当前页
    private int size;          // 每页大小
    private long pages;        // 总页数

    public PageResult() {}

    public PageResult(List<T> list, long total, int page, int size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = (total + size - 1) / size;
    }

    // ========== Convert to JSON ==========

    public JsonObject toJson() {
        return new JsonObject()
            .put("list", toJsonArray())
            .put("total", total)
            .put("page", page)
            .put("size", size)
            .put("pages", pages);
    }

    private JsonArray toJsonArray() {
        JsonArray arr = new JsonArray();
        if (list != null) {
            for (T item : list) {
                if (item instanceof JsonObject) {
                    arr.add((JsonObject) item);
                } else if (item instanceof JsonArray) {
                    arr.add((JsonArray) item);
                } else {
                    arr.add(item);
                }
            }
        }
        return arr;
    }

    // ========== Getters & Setters ==========

    public List<T> getList() { return list; }
    public void setList(List<T> list) { this.list = list; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public long getPages() { return pages; }
    public void setPages(long pages) { this.pages = pages; }
}