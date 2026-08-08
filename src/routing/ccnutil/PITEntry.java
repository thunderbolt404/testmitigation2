/* Pending Interest Table entry. */
package routing.ccnutil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import core.DTNHost;
import core.SimClock;

/**
 * Stores all unique pending Interest packets for one content name. The packet
 * ID acts as the nonce/request identifier, while each Interest retains its
 * original requesting host.
 */
public class PITEntry {
    private final String contentName;
    private final LinkedHashMap<String, InterestPacket> interests;
    private final double timestamp;
    private final double timeout;

    public PITEntry(InterestPacket interest, double timeout) {
        if (interest == null) {
            throw new IllegalArgumentException("interest must not be null");
        }
        this.contentName = interest.getContentName();
        this.timestamp = SimClock.getTime();
        this.timeout = timeout;
        this.interests = new LinkedHashMap<String, InterestPacket>();
        addInterest(interest);
    }

    /**
     * Adds one unique Interest request.
     *
     * @return true when a new request was added; false for the same nonce.
     */
    public boolean addInterest(InterestPacket interest) {
        if (interest == null) {
            return false;
        }
        String id = interest.getPacketId();
        if (id == null || id.length() == 0) {
            id = interest.getContentName() + "@"
                    + interest.getSource().getAddress() + "@"
                    + interest.getCreationTime();
        }
        if (interests.containsKey(id)) {
            return false;
        }
        interests.put(id, interest);
        return true;
    }

    /** Compatibility method for simple requester-only callers. */
    public boolean addRequester(DTNHost requester) {
        if (requester == null) {
            return false;
        }
        InterestPacket synthetic = new InterestPacket(contentName,
                requester, SimClock.getTime(), null,
                contentName + "@" + requester.getAddress() + "@"
                        + SimClock.getTime());
        return addInterest(synthetic);
    }

    public ArrayList<InterestPacket> getInterests() {
        return new ArrayList<InterestPacket>(interests.values());
    }

    public ArrayList<DTNHost> getRequesters() {
        ArrayList<DTNHost> requesters = new ArrayList<DTNHost>();
        for (Map.Entry<String, InterestPacket> item :
                interests.entrySet()) {
            DTNHost requester = item.getValue().getSource();
            if (!requesters.contains(requester)) {
                requesters.add(requester);
            }
        }
        return requesters;
    }

    public String getContentName() {
        return contentName;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public double getTimeout() {
        return timeout;
    }

    /** A timeout <= 0 disables expiration for old-code compatibility. */
    public boolean isExpired() {
        return isExpired(SimClock.getTime());
    }

    public boolean isExpired(double currentTime) {
        return timeout > 0.0 && currentTime - timestamp >= timeout;
    }

    public int getRequesterCount() {
        return interests.size();
    }
}
