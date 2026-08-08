package routing.ccnutil;

import java.util.ArrayList;
import java.util.HashMap;

/** Non-evicting producer content store. */
public class StaticCache {
    private final HashMap<String, DataPacket> cache;

    public StaticCache() {
        cache = new HashMap<String, DataPacket>();
    }

    public void insertData(DataPacket data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        cache.put(data.getContentName(), data.asCacheEntry());
    }

    public boolean insertDataIfAbsent(DataPacket data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (cache.containsKey(data.getContentName())) {
            return false;
        }
        cache.put(data.getContentName(), data.asCacheEntry());
        return true;
    }

    public DataPacket getData(String contentName) {
        return cache.get(contentName);
    }

    public boolean contains(String contentName) {
        return cache.containsKey(contentName);
    }

    public int getCacheSize() {
        return cache.size();
    }

    public ArrayList<String> getContentNames() {
        return new ArrayList<String>(cache.keySet());
    }

    public void clear() {
        cache.clear();
    }
}
