package com.akiban.cserver.api.common;

import java.nio.ByteBuffer;

import com.akiban.util.CacheMap;

public final class IndexId extends ByteBufferWriter {

    private final static CacheMap<Integer,IndexId> cache = new CacheMap<Integer, IndexId>(new CacheMap.Allocator<Integer,IndexId>() {
        @Override
        public IndexId allocateFor(Integer key) {
            return new IndexId(key);
        }
    });

    private final int indexId;

    private IndexId(int indexId) {
        this.indexId = indexId;
    }

    public static IndexId of(int indexId) {
        return cache.get(indexId);
    }

    /**
     * Reads an int from the buffer.
     * @param readFrom the buffer to read from
     * @param allocatedBytes must be 4
     * @throws java.nio.BufferUnderflowException if thrown from reading the buffer
     */
    public IndexId(ByteBuffer readFrom, int allocatedBytes) {
        WrongByteAllocationException.ifNotEqual(allocatedBytes, 4);
        indexId = readFrom.getInt();
    }

    @Override
    protected void writeToBuffer(ByteBuffer output) throws Exception {
        output.putInt(indexId);
    }

    public int getIndexId(IdResolver resolver) {
        return indexId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IndexId indexId1 = (IndexId) o;

        return indexId == indexId1.indexId;

    }

    @Override
    public int hashCode() {
        return indexId;
    }
}
