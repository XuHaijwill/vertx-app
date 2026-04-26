package com.example.handlers;

import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User Handler - Handles user-related requests
 */
public class UserHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UserHandler.class);

    /**
     * GET /api/users - List all users
     */
    public static void listUsers(RoutingContext ctx) {
        LOG.info("Listing all users");
        ctx.json(java.util.List.of(
            new User(1, "Alice", "alice@example.com"),
            new User(2, "Bob", "bob@example.com")
        ));
    }

    /**
     * GET /api/users/:id - Get user by ID
     */
    public static void getUser(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        LOG.info("Getting user: {}", id);
        ctx.json(new User(Integer.parseInt(id), "User " + id, "user" + id + "@example.com"));
    }

    /**
     * POST /api/users - Create user
     */
    public static void createUser(RoutingContext ctx) {
        var body = ctx.body().asJsonObject();
        String name = body.getString("name");
        String email = body.getString("email");

        LOG.info("Creating user: name={}, email={}", name, email);

        ctx.response().setStatusCode(201);
        ctx.json(new User(100, name, email));
    }

    /**
     * PUT /api/users/:id - Update user
     */
    public static void updateUser(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        var body = ctx.body().asJsonObject();
        String name = body.getString("name");
        String email = body.getString("email");

        LOG.info("Updating user {}: name={}, email={}", id, name, email);

        ctx.json(new User(Integer.parseInt(id), name, email));
    }

    /**
     * DELETE /api/users/:id - Delete user
     */
    public static void deleteUser(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        LOG.info("Deleting user {}", id);
        ctx.response().setStatusCode(204).end();
    }

    // DTO
    public static class User {
        public int id;
        public String name;
        public String email;

        public User(int id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }
    }
}