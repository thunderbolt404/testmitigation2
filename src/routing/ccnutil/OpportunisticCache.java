/* LRU Opportunistic Cache. */
package routing.ccnutil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Capacity-limited access-ordered cache for transient Data. */
public class OpportunisticCache {
    private final int capacity;
    private final LinkedHashMap<String, DataPacket> cache;

    public OpportunisticCache(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        this.capacity = capacity;
        this.cache = new LinkedHashMap<String, DataPacket>(
                16, 0.75f, true);
    }

    public void insertData(DataPacket data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (capacity == 0 || !data.hasContent()) {
            return;
        }
        cache.put(data.getContentName(), data.asCacheEntry());
        while (cache.size() > capacity) {
            removeOldest();
        }
    }

    /** Returns Data and refreshes its LRU position. */
    public DataPacket getData(String contentName) {
        return cache.get(contentName);
    }

    /** Does not change LRU order. */
    public boolean contains(String contentName) {
        return cache.containsKey(contentName);
    }

    public DataPacket removeOldest() {
        Iterator<Map.Entry<String, DataPacket>> iterator =
                cache.entrySet().iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        Map.Entry<String, DataPacket> eldest = iterator.next();
        DataPacket removed = eldest.getValue();
        iterator.remove();
        return removed;
    }

    public int getCacheSize() {
        return cache.size();
    }

    public int getCapacity() {
        return capacity;
    }

    public ArrayList<String> getContentNames() {
        return new ArrayList<String>(cache.keySet());
    }

    public void clear() {
        cache.clear();
    }
}
