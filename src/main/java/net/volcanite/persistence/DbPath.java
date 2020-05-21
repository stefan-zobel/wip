package net.volcanite.persistence;

// TODO
public final class DbPath {

    public static String getPath() {
        return "D:\\Temp\\rocksdb_database";
    }

    private DbPath() {
        throw new AssertionError();
    }
}
