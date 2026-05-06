package com.example.api;

import com.example.service.ProductService;
import com.example.service.impl.ProductServiceImpl;
import com.example.entity.Product;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Product CRUD API — /api/products
 */
public class ProductApi extends BaseApi {

    private final ProductService productService;

    public ProductApi(Vertx vertx) {
        super(vertx);
        this.productService = new ProductServiceImpl(vertx);
    }

    @Override
    public void registerRoutes(Router router, String contextPath) {
        router.get(contextPath + "/api/products").handler(this::listOrSearch);
        router.get(contextPath + "/api/products/:id").handler(this::getById);
        router.post(contextPath + "/api/products").handler(this::create);
        router.post(contextPath + "/api/products/batch").handler(this::batchCreate);
        router.put(contextPath + "/api/products/:id").handler(this::update);
        router.put(contextPath + "/api/products/batch").handler(this::batchUpdate);
        router.delete(contextPath + "/api/products/:id").handler(this::delete);
        router.delete(contextPath + "/api/products/batch").handler(this::batchDelete);
    }

    private void listOrSearch(RoutingContext ctx) {
        String q    = queryStr(ctx, "q");
        String cat  = queryStr(ctx, "category");
        int page    = queryIntClamped(ctx, "page", 1, 1, 1000);
        int size    = queryIntClamped(ctx, "size", 20, 1, 100);

        if ((q != null && !q.isBlank()) || cat != null) {
            respondPaginated(ctx, productService.searchPaginated(q, cat, page, size));
        } else {
            respondPaginated(ctx, productService.findPaginated(page, size));
        }
    }

    private void getById(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid product ID");
            return;
        }
        respond(ctx, productService.findById(id));
    }

    private void create(RoutingContext ctx) {
        respondCreated(ctx, productService.create(Product.fromJson(bodyJson(ctx))));
    }

    private void update(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid product ID");
            return;
        }
        respond(ctx, productService.update(id, Product.fromJson(bodyJson(ctx))));
    }

    private void delete(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid product ID");
            return;
        }
        respondDeleted(ctx, productService.delete(id));
    }

    // ================================================================
    // Batch operations
    // ================================================================

    private void batchCreate(RoutingContext ctx) {
        JsonArray body = ctx.body().asJsonObject().getJsonArray("items");
        if (body == null || body.isEmpty()) {
            badRequest(ctx, "Request body must contain a non-empty 'items' array");
            return;
        }
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            products.add(Product.fromJson(body.getJsonObject(i)));
        }
        respond(ctx, productService.batchCreate(products));
    }

    private void batchUpdate(RoutingContext ctx) {
        JsonArray body = ctx.body().asJsonObject().getJsonArray("items");
        if (body == null || body.isEmpty()) {
            badRequest(ctx, "Request body must contain a non-empty 'items' array");
            return;
        }
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            products.add(Product.fromJson(body.getJsonObject(i)));
        }
        respond(ctx, productService.batchUpdate(products));
    }

    private void batchDelete(RoutingContext ctx) {
        JsonArray body = ctx.body().asJsonObject().getJsonArray("ids");
        if (body == null || body.isEmpty()) {
            badRequest(ctx, "Request body must contain a non-empty 'ids' array");
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            Long id = parseId(String.valueOf(body.getValue(i)));
            if (id == null) {
                badRequest(ctx, "Invalid id at index " + i);
                return;
            }
            ids.add(id);
        }
        respond(ctx, productService.batchDelete(ids));
    }
}
