package com.example.service;

import com.example.core.PageResult;
import com.example.entity.User;
import io.vertx.core.Future;

import java.util.List;

/** User Service Interface */
public interface UserService {

    Future<List<User>> findAll();
    Future<User> findById(Long id);
    Future<List<User>> search(String keyword);
    Future<PageResult<User>> findPaginated(int page, int size);
    Future<PageResult<User>> searchPaginated(String keyword, int page, int size);
    Future<User> create(User user);
    Future<User> update(Long id, User user);
    Future<Void> delete(Long id);
    Future<Boolean> exists(String email);
    Future<BatchResult<User>> batchCreate(List<User> users);
    Future<BatchResult<User>> batchUpdate(List<User> users);
    Future<Integer> batchDelete(List<Long> ids);

    // helpers
    Future<List<User>> findByDepartment(String department);
    Future<List<User>> findByStatus(String status);

    /** Batch operation result */
    class BatchResult<T> {
        private final List<T> items;
        private final int created;
        private final int updated;
        private final int deleted;
        private final int failed;

        public BatchResult(List<T> items, int created, int updated, int deleted, int failed) {
            this.items = items;
            this.created = created;
            this.updated = updated;
            this.deleted = deleted;
            this.failed = failed;
        }

        public List<T> getItems() { return items; }
        public int getCreated()   { return created; }
        public int getUpdated()   { return updated; }
        public int getDeleted()   { return deleted; }
        public int getFailed()    { return failed; }
    }
}