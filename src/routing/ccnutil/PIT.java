/* HashMap-based Pending Interest Table. */
package routing.ccnutil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import core.DTNHost;
import core.SimClock;

/** Name-indexed PIT with aggregation and optional expiration. */
public class PIT {
    public static final int CREATED = 0;
    public static final int AGGREGATED = 1;
    public static final int DUPLICATE_NONCE = 2;

    private final HashMap<String, PITEntry> entries;

    public PIT() {
        entries = new HashMap<String, PITEntry>();
    }

    /**
     * Adds an Interest.
     *
     * @return CREATED, AGGREGATED, or DUPLICATE_NONCE.
     */
    public int addInterest(InterestPacket interest, double timeout) {
        if (interest == null) {
            throw new IllegalArgumentException("interest must not be null");
        }

        String name = interest.getContentName();
        PITEntry entry = entries.get(name);
        if (entry == null || entry.isExpired()) {
            entries.put(name, new PITEntry(interest, timeout));
            return CREATED;
        }

        return entry.addInterest(interest)
                ? AGGREGATED : DUPLICATE_NONCE;
    }

    /** Compatibility overload. */
    public boolean addInterest(InterestPacket interest, DTNHost requester,
            long timeout) {
        return addInterest(interest, (double) timeout) != CREATED;
    }

    public PITEntry removeInterest(String contentName) {
        return entries.remove(contentName);
    }

    public boolean containsInterest(String contentName) {
        PITEntry entry = entries.get(contentName);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            entries.remove(contentName);
            return false;
        }
        return true;
    }

    public PITEntry getEntry(String contentName) {
        return containsInterest(contentName)
                ? entries.get(contentName) : null;
    }

    public ArrayList<DTNHost> getRequesters(String contentName) {
        PITEntry entry = getEntry(contentName);
        return entry == null ? new ArrayList<DTNHost>()
                : entry.getRequesters();
    }

    public int clearExpiredEntries() {
        return clearExpiredEntries(SimClock.getTime());
    }

    public int clearExpiredEntries(double currentTime) {
        int removed = 0;
        Iterator<Map.Entry<String, PITEntry>> iterator =
                entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isExpired(currentTime)) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
