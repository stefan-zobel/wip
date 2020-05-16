package org.rocksdb;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ComparatorBug {

    public static void main(String[] args) {
        ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions();
        columnFamilyOptions.setComparator(
                new VersionedComparator(
                        new ComparatorOptions()
                )
        );

        byte[] columnFamilyName = RocksDB.DEFAULT_COLUMN_FAMILY;
        ColumnFamilyDescriptor columnFamilyDescriptor = new ColumnFamilyDescriptor(columnFamilyName, columnFamilyOptions);

        List<ColumnFamilyDescriptor> columnFamilyDescriptors = Collections.singletonList(columnFamilyDescriptor);
        ArrayList<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>(1);

        DBOptions dbOptions = new DBOptions();
        dbOptions.setCreateIfMissing(true);
        dbOptions.setCreateMissingColumnFamilies(true);

        try(RocksDB rocksdb = RocksDB.open(dbOptions, "test", columnFamilyDescriptors, columnFamilyHandles)) {
            ColumnFamilyHandle cf1 = columnFamilyHandles.get(0);

            rocksdb.put(cf1, ("justanotherrandomkey").getBytes(), "value".getBytes());

            System.out.println(new String(rocksdb.get(cf1, ("justanotherrandomkey").getBytes())));

        } catch (RocksDBException e) {
            e.printStackTrace();
        }
    }


    public static class VersionedComparator extends AbstractComparator {
        private final int versionSize = 8;

        protected VersionedComparator(ComparatorOptions copt) {
            super(copt);
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public int compare(ByteBuffer a, ByteBuffer b) {
            return compareToWithOffsetAndLength(a, a.remaining() - versionSize, versionSize, b, b.remaining() - versionSize, versionSize);
        }

        private int compareToWithOffsetAndLength(ByteBuffer a, int aOffset, int aLength, ByteBuffer b, int bOffset, int bLength) {
            int minLength = Math.min(aLength, bLength);

            for (int i = 0; i < minLength; i++) {
                int aByte = a.get(i + aOffset) & 0xFF;
                int bByte = b.get(i + bOffset) & 0xFF;
                if (aByte != bByte) {
                    return aByte - bByte;
                }
            }
            return aLength - bLength;
        }
    }
}
