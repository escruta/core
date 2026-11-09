package com.escruta.core.repositories;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Optional;

/**
 * Pageable that uses an absolute row offset instead of a page number. This keeps
 * the public API contract (offset/limit) correct when offset is not a multiple of limit.
 */
public final class OffsetPageRequest implements Pageable {
    private final long offset;
    private final int limit;
    private final Sort sort;

    public OffsetPageRequest(long offset, int limit, Sort sort) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be greater than zero");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be negative");
        }
        this.offset = offset;
        this.limit = limit;
        this.sort = sort != null ?
                sort :
                Sort.unsorted();
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / limit);
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public @NonNull Sort getSort() {
        return sort;
    }

    @Override
    public @NonNull Pageable next() {
        return new OffsetPageRequest(offset + limit, limit, sort);
    }

    @Override
    public @NonNull Pageable previousOrFirst() {
        return hasPrevious() ?
                new OffsetPageRequest(Math.max(0, offset - limit), limit, sort) :
                first();
    }

    @Override
    public @NonNull Pageable first() {
        return new OffsetPageRequest(0, limit, sort);
    }

    @Override
    public @NonNull Pageable withPage(int pageNumber) {
        return new OffsetPageRequest((long) pageNumber * limit, limit, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }

    @Override
    public @NonNull Optional<Pageable> toOptional() {
        return Optional.of(this);
    }
}
