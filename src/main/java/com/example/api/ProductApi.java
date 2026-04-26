package com.example.api;

import com.example.service.ProductService;
import com.example.service.ProductServiceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

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
    public void registerRoutes(Router router) {
        router.get("/api/products").handler(this::listOrSearch);
        router.get("/api/products/:id").handler(this::getById);
        router.post("/api/products").handler(this::create);
        router.put("/api/products/:id").handler(this::update);
        router.delete("/api/products/:id").handler(this::delete);
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
        respondCreated(ctx, productService.create(bodyJson(ctx)));
    }

    private void update(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid product ID");
            return;
        }
        respond(ctx, productService.update(id, bodyJson(ctx)));
    }

    private void delete(RoutingContext ctx) {
        Long id = parseId(ctx.pathParam("id"));
        if (id == null) {
            badRequest(ctx, "Invalid product ID");
            return;
        }
        respondDeleted(ctx, productService.delete(id));
    }
}
