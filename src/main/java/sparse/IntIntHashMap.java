package sparse;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public class IntIntHashMap implements Map<Integer, Integer>, Cloneable {

    static final int INITIAL_CAP = 16;

    static final int MAX_CAP = 1 << 30;

    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    static final Item[] EMPTY_TABLE = {};

    Item[] table = EMPTY_TABLE;

    int size;

    int threshold;

    final float loadFactor;

    private final int valIfNoKey;

    public IntIntHashMap(int initialCapacity, float loadFactor, int valueIfKeyNotFound) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        if (initialCapacity > MAX_CAP)
            initialCapacity = MAX_CAP;
        if (loadFactor <= 0.0f || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " + loadFactor);

        this.loadFactor = loadFactor;
        threshold = initialCapacity;
        valIfNoKey = valueIfKeyNotFound;
    }

    public IntIntHashMap(int initialCapacity, int valueIfKeyNotFound) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, valueIfKeyNotFound);
    }

    public IntIntHashMap(int valueIfKeyNotFound) {
        this(INITIAL_CAP, DEFAULT_LOAD_FACTOR, valueIfKeyNotFound);
    }

    public IntIntHashMap(Map<? extends Integer, ? extends Integer> m, int valueIfKeyNotFound) {
        this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1, INITIAL_CAP), DEFAULT_LOAD_FACTOR,
                valueIfKeyNotFound);
        inflateTable(threshold);

        putAllForCreate(m);
    }

    private static int roundUpToPowerOf2(int number) {
        return number >= MAX_CAP ? MAX_CAP : (number > 1) ? Integer.highestOneBit((number - 1) << 1) : 1;
    }

    private void inflateTable(int toSize) {
        // Find a power of 2 >= toSize
        int capacity = roundUpToPowerOf2(toSize);

        threshold = (int) Math.min(capacity * loadFactor, MAX_CAP + 1);
        table = new Item[capacity];
    }

    // internal utilities

    final int hashInt(int k) {
        return k ^ (k >>> 16);
    }

    final int hash(Object k) {
        return hashInt(k.hashCode());
    }

    static int indexFor(int h, int length) {
        return h & (length - 1);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int getInt(int key) {
        Item item = getEntryInt(key);
        return null == item ? valIfNoKey : item.getValueInt();
    }

    public Integer get(Object key) {
        if (key == null)
            throw new NullPointerException();
        Item entry = getEntry(key);

        return null == entry ? null : entry.getValue();
    }

    public boolean containsKeyInt(int key) {
        return getEntryInt(key) != null;
    }

    public boolean containsKey(Object key) {
        if (key == null)
            throw new NullPointerException();
        return getEntry(key) != null;
    }

    final Item getEntryInt(int key) {
        if (size == 0) {
            return null;
        }

        int hash = hashInt(key);
        for (Item e = table[indexFor(hash, table.length)]; e != null; e = e.next) {
            if (e.hash == hash && e.key == key)
                return e;
        }
        return null;
    }

    final Item getEntry(Object key) {
        if (size == 0) {
            return null;
        }

        int hash = hash(key);
        for (Item e = table[indexFor(hash, table.length)]; e != null; e = e.next) {
            Integer k;
            if (e.hash == hash && ((k = e.key) == key || (key.equals(k))))
                return e;
        }
        return null;
    }

    public int putInt(int key, int value) {
        if (table == EMPTY_TABLE) {
            inflateTable(threshold);
        }
        int hash = hashInt(key);
        int i = indexFor(hash, table.length);
        for (Item e = table[i]; e != null; e = e.next) {
            if (e.hash == hash && e.key == key) {
                int oldValue = e.value;
                e.value = value;
                return oldValue;
            }
        }

        addItemInt(hash, key, value, i);
        return valIfNoKey;
    }

    public Integer put(Integer key, Integer value) {
        if (key == null || value == null)
            throw new NullPointerException();
        if (table == EMPTY_TABLE) {
            inflateTable(threshold);
        }
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        for (Item e = table[i]; e != null; e = e.next) {
            Integer k;
            if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
                Integer oldValue = e.value;
                e.value = value;
                return oldValue;
            }
        }

        addItem(hash, key, value, i);
        return null;
    }

    private void putForCreate(Integer key, Integer value) {
        if (key == null || value == null)
            throw new NullPointerException();
        int hash = hash(key);
        int i = indexFor(hash, table.length);

        for (Item e = table[i]; e != null; e = e.next) {
            Integer k;
            if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
                e.value = value;
                return;
            }
        }

        createItem(hash, key, value, i);
    }

    private void putAllForCreate(Map<? extends Integer, ? extends Integer> m) {
        for (Map.Entry<? extends Integer, ? extends Integer> e : m.entrySet())
            putForCreate(e.getKey(), e.getValue());
    }

    void resize(int newCapacity) {
        Item[] oldTable = table;
        int oldCapacity = oldTable.length;
        if (oldCapacity == MAX_CAP) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        Item[] newTable = new Item[newCapacity];
        transfer(newTable);
        table = newTable;
        threshold = (int) Math.min(newCapacity * loadFactor, MAX_CAP + 1);
    }

    void transfer(Item[] newTable) {
        int newCapacity = newTable.length;
        for (Item e : table) {
            while (null != e) {
                Item next = e.next;
                int i = indexFor(e.hash, newCapacity);
                e.next = newTable[i];
                newTable[i] = e;
                e = next;
            }
        }
    }

    public void putAll(Map<? extends Integer, ? extends Integer> m) {
        int numKeysToBeAdded = m.size();
        if (numKeysToBeAdded == 0)
            return;

        if (table == EMPTY_TABLE) {
            inflateTable((int) Math.max(numKeysToBeAdded * loadFactor, threshold));
        }

        if (numKeysToBeAdded > threshold) {
            int targetCapacity = (int) (numKeysToBeAdded / loadFactor + 1);
            if (targetCapacity > MAX_CAP)
                targetCapacity = MAX_CAP;
            int newCapacity = table.length;
            while (newCapacity < targetCapacity)
                newCapacity <<= 1;
            if (newCapacity > table.length)
                resize(newCapacity);
        }

        for (Map.Entry<? extends Integer, ? extends Integer> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }

    public int removeInt(int key) {
        Item e = removeEntryForKeyInt(key);
        return (e == null ? valIfNoKey : e.value);
    }

    public Integer remove(Object key) {
        Item e = removeEntryForKey(key);
        return (e == null ? null : e.value);
    }

    final Item removeEntryForKeyInt(int key) {
        if (size == 0) {
            return null;
        }
        int hash = hashInt(key);
        int i = indexFor(hash, table.length);
        Item prev = table[i];
        Item e = prev;

        while (e != null) {
            Item next = e.next;
            if (e.hash == hash && e.key == key) {
                size--;
                if (prev == e)
                    table[i] = next;
                else
                    prev.next = next;
                return e;
            }
            prev = e;
            e = next;
        }

        return e;
    }

    final Item removeEntryForKey(Object key) {
        if (key == null)
            throw new NullPointerException();
        if (size == 0) {
            return null;
        }
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        Item prev = table[i];
        Item e = prev;

        while (e != null) {
            Item next = e.next;
            Integer k;
            if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
                size--;
                if (prev == e)
                    table[i] = next;
                else
                    prev.next = next;
                return e;
            }
            prev = e;
            e = next;
        }

        return e;
    }

    final Item removeMapping(Object o) {
        if (size == 0 || !(o instanceof Map.Entry))
            return null;

        @SuppressWarnings("unchecked")
        Map.Entry<Integer, Integer> entry = (Map.Entry<Integer, Integer>) o;
        Integer key = entry.getKey();
        int hash = (key == null) ? 0 : hash(key);
        int i = indexFor(hash, table.length);
        Item prev = table[i];
        Item e = prev;

        while (e != null) {
            Item next = e.next;
            if (e.hash == hash && e.equals(entry)) {
                size--;
                if (prev == e)
                    table[i] = next;
                else
                    prev.next = next;
                return e;
            }
            prev = e;
            e = next;
        }

        return e;
    }

    public void clear() {
        Arrays.fill(table, null);
        size = 0;
    }

    public boolean containsValueInt(int value) {
        Item[] tab = table;
        for (int i = 0; i < tab.length; i++)
            for (Item e = tab[i]; e != null; e = e.next)
                if (value == e.value)
                    return true;
        return false;
    }

    public boolean containsValue(Object value) {
        if (value == null)
            throw new NullPointerException();

        Item[] tab = table;
        for (int i = 0; i < tab.length; i++)
            for (Item e = tab[i]; e != null; e = e.next)
                if (value.equals(e.value))
                    return true;
        return false;
    }

    public Object clone() {
        IntIntHashMap result = null;
        try {
            result = (IntIntHashMap) super.clone();
        } catch (CloneNotSupportedException e) {
        }
        if (result.table != EMPTY_TABLE) {
            result.inflateTable(Math.min((int) Math.min(size * Math.min(1 / loadFactor, 4.0f), IntIntHashMap.MAX_CAP),
                    table.length));
        }
        result.entrySet = null;
        result.keySet = null;
        result.values = null;
        result.size = 0;
        result.putAllForCreate(this);

        return result;
    }

    static class Item implements Map.Entry<Integer, Integer> {
        final int key;
        int value;
        Item next;
        int hash;

        Item(int h, int k, int v, Item n) {
            value = v;
            next = n;
            key = k;
            hash = h;
        }

        public final int getKeyInt() {
            return key;
        }

        public final Integer getKey() {
            return key;
        }

        public final int getValueInt() {
            return value;
        }

        public final Integer getValue() {
            return value;
        }

        public final Integer setValue(Integer newValue) {
            throw new UnsupportedOperationException();
        }

        public final boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            @SuppressWarnings("unchecked")
            Map.Entry<Integer, Integer> e = (Map.Entry<Integer, Integer>) o;
            Integer k1 = getKey();
            Integer k2 = e.getKey();
            if (k1 == k2 || (k1 != null && k1.equals(k2))) {
                Integer v1 = getValue();
                Integer v2 = e.getValue();
                if (v1 == v2 || (v1 != null && v1.equals(v2)))
                    return true;
            }
            return false;
        }

        public final int hashCode() {
            return key ^ value;
        }

        public final String toString() {
            return key + "=" + value;
        }
    }

    void addItemInt(int hash, int key, int value, int bucketIndex) {
        if ((size >= threshold) && (null != table[bucketIndex])) {
            resize(2 * table.length);
            hash = hashInt(key);
            bucketIndex = indexFor(hash, table.length);
        }

        createItemInt(hash, key, value, bucketIndex);
    }

    void addItem(int hash, Integer key, Integer value, int bucketIndex) {
        if ((size >= threshold) && (null != table[bucketIndex])) {
            resize(2 * table.length);
            hash = hash(key);
            bucketIndex = indexFor(hash, table.length);
        }

        createItem(hash, key, value, bucketIndex);
    }

    void createItemInt(int hash, int key, int value, int bucketIndex) {
        Item e = table[bucketIndex];
        table[bucketIndex] = new Item(hash, key, value, e);
        size++;
    }

    void createItem(int hash, Integer key, Integer value, int bucketIndex) {
        Item e = table[bucketIndex];
        table[bucketIndex] = new Item(hash, key, value, e);
        size++;
    }

    private abstract class HashIterator<E> implements Iterator<E> {
        Item next; // next entry to return
        int index; // current slot
        Item current; // current entry

        HashIterator() {
            if (size > 0) { // advance to first entry
                Item[] t = table;
                while (index < t.length && (next = t[index++]) == null) //
                    ;
            }
        }

        public final boolean hasNext() {
            return next != null;
        }

        final Item nextEntry() {
            Item e = next;
            if (e == null)
                throw new NoSuchElementException();

            if ((next = e.next) == null) {
                Item[] t = table;
                while (index < t.length && (next = t[index++]) == null) //
                    ;
            }
            current = e;
            return e;
        }

        public void remove() {
            if (current == null)
                throw new IllegalStateException();
            Integer k = current.key;
            current = null;
            IntIntHashMap.this.removeEntryForKey(k);
        }
    }

    /* package */ final class ValueIterator extends HashIterator<Integer> {
        public Integer next() {
            return nextEntry().getValue();
        }
    }

    /* package */ final class KeyIterator extends HashIterator<Integer> {
        public Integer next() {
            return nextEntry().getKey();
        }
    }

    /* package */ final class EntryIterator extends HashIterator<Map.Entry<Integer, Integer>> {
        public Map.Entry<Integer, Integer> next() {
            return nextEntry();
        }
    }

    Iterator<Integer> newKeyIterator() {
        return new KeyIterator();
    }

    Iterator<Integer> newValueIterator() {
        return new ValueIterator();
    }

    Iterator<Map.Entry<Integer, Integer>> newEntryIterator() {
        return new EntryIterator();
    }

    // Views

    private transient Set<Map.Entry<Integer, Integer>> entrySet = null;

    public Set<Map.Entry<Integer, Integer>> entrySet() {
        return entrySet0();
    }

    private Set<Map.Entry<Integer, Integer>> entrySet0() {
        Set<Map.Entry<Integer, Integer>> es = entrySet;
        return es != null ? es : (entrySet = new EntrySet());
    }

    /* package */ final class EntrySet extends AbstractSet<Map.Entry<Integer, Integer>> {
        public Iterator<Map.Entry<Integer, Integer>> iterator() {
            return newEntryIterator();
        }

        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            @SuppressWarnings("unchecked")
            Map.Entry<Integer, Integer> e = (Map.Entry<Integer, Integer>) o;
            Item candidate = getEntry(e.getKey());
            return candidate != null && candidate.equals(e);
        }

        public boolean remove(Object o) {
            return removeMapping(o) != null;
        }

        public int size() {
            return size;
        }

        public void clear() {
            IntIntHashMap.this.clear();
        }
    }

    private Set<Integer> keySet = null;
    private Collection<Integer> values = null;

    public Collection<Integer> values() {
        Collection<Integer> vs = values;
        return (vs != null ? vs : (values = new Values()));
    }

    public Set<Integer> keySet() {
        Set<Integer> ks = keySet;
        return (ks != null ? ks : (keySet = new KeySet()));
    }

    /* package */ final class KeySet extends AbstractSet<Integer> {
        public Iterator<Integer> iterator() {
            return newKeyIterator();
        }

        public int size() {
            return size;
        }

        public boolean contains(Object o) {
            return containsKey(o);
        }

        public boolean remove(Object o) {
            return IntIntHashMap.this.removeEntryForKey(o) != null;
        }

        public void clear() {
            IntIntHashMap.this.clear();
        }
    }

    /* package */ final class Values extends AbstractCollection<Integer> {
        public Iterator<Integer> iterator() {
            return newValueIterator();
        }

        public int size() {
            return size;
        }

        public boolean contains(Object o) {
            return containsValue(o);
        }

        public void clear() {
            IntIntHashMap.this.clear();
        }
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof Map))
            return false;
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> m = (Map<Integer, Integer>) o;
        if (m.size() != size())
            return false;

        try {
            Iterator<Entry<Integer, Integer>> i = entrySet().iterator();
            while (i.hasNext()) {
                Entry<Integer, Integer> e = i.next();
                Integer key = e.getKey();
                Integer value = e.getValue();
                if (value == null) {
                    if (!(m.get(key) == null && m.containsKey(key)))
                        return false;
                } else {
                    if (!value.equals(m.get(key)))
                        return false;
                }
            }
        } catch (ClassCastException unused) {
            return false;
        }

        return true;
    }

    public int hashCode() {
        int h = 0;
        Iterator<Entry<Integer, Integer>> i = entrySet().iterator();
        while (i.hasNext())
            h += i.next().hashCode();
        return h;
    }

    public String toString() {
        Iterator<Entry<Integer, Integer>> i = entrySet().iterator();
        if (!i.hasNext())
            return "{}";

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (;;) {
            Entry<Integer, Integer> e = i.next();
            Integer key = e.getKey();
            Integer value = e.getValue();
            sb.append(key);
            sb.append('=');
            sb.append(value);
            if (!i.hasNext())
                return sb.append('}').toString();
            sb.append(',').append(' ');
        }
    }
}
