package routing.ccnutil;

import core.DTNHost;
import core.Message;
import core.SimClock;
import core.SimScenario;

/** Immutable Interest carrying only the fields needed by the CCN-DTN study. */
public final class InterestPacket {
    public static final String APP_ID = "fi.dcslab.CCN_application";

    public static final String PACKET_TYPE = "CCN.packetType";
    public static final String TYPE_INTEREST = "INTEREST";
    public static final String CONTENT_NAME = "CCN.contentName";
    public static final String SOURCE_ADDRESS = "CCN.sourceAddress";
    public static final String CREATION_TIME = "CCN.creationTime";
    public static final String PACKET_ID = "CCN.packetId";

    /** Legacy CCN_application properties. */
    public static final String LEGACY_TYPE = "type";
    public static final String LEGACY_INTEREST_TYPE = "query_ad";
    public static final String LEGACY_QUERY_KEY = "queryMsg";
    public static final String LEGACY_START_TIME = "startTime";

    private final String contentName;
    private final DTNHost source;
    private final double creationTime;
    private final DTNHost destination;
    private final String packetId;

    public InterestPacket(String contentName, DTNHost source) {
        this(contentName, source, SimClock.getTime(), null, null);
    }

    public InterestPacket(String contentName, DTNHost source,
            double creationTime, DTNHost destination, String packetId) {
        if (contentName == null || contentName.trim().length() == 0) {
            throw new IllegalArgumentException(
                    "contentName must not be empty");
        }
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }

        this.contentName = contentName.trim();
        this.source = source;
        this.creationTime = creationTime;
        this.destination = destination;
        this.packetId = packetId;
    }

    public String getContentName() {
        return contentName;
    }

    public DTNHost getSource() {
        return source;
    }

    public double getCreationTime() {
        return creationTime;
    }

    public DTNHost getDestination() {
        return destination;
    }

    public String getPacketId() {
        return packetId;
    }

    public InterestPacket copyForDestination(DTNHost newDestination) {
        return new InterestPacket(contentName, source, creationTime,
                newDestination, packetId);
    }

    /** Converts the Interest to a ONE Message. */
    public Message toMessage(DTNHost sender, String messageId, int size) {
        if (sender == null || destination == null) {
            throw new IllegalStateException(
                    "sender and destination must be selected");
        }

        Message message = new Message(sender, destination, messageId,
                Math.max(1, size));
        String logicalId = packetId == null ? messageId : packetId;

        message.addProperty(PACKET_TYPE, TYPE_INTEREST);
        message.addProperty(CONTENT_NAME, contentName);
        message.addProperty(SOURCE_ADDRESS,
                Integer.valueOf(source.getAddress()));
        message.addProperty(CREATION_TIME, Double.valueOf(creationTime));
        message.addProperty(PACKET_ID, logicalId);

        /* Legacy compatibility. */
        message.addProperty(LEGACY_TYPE, LEGACY_INTEREST_TYPE);
        message.addProperty(LEGACY_QUERY_KEY, contentName);
        message.addProperty(LEGACY_START_TIME,
                Double.valueOf(creationTime));
        message.setAppID(APP_ID);
        return message;
    }

    public static boolean isInterest(Message message) {
        if (message == null) {
            return false;
        }
        Object packetType = message.getProperty(PACKET_TYPE);
        if (TYPE_INTEREST.equals(packetType)) {
            return true;
        }
        Object legacyType = message.getProperty(LEGACY_TYPE);
        return legacyType != null
                && LEGACY_INTEREST_TYPE.equalsIgnoreCase(
                        String.valueOf(legacyType));
    }

    public static InterestPacket fromMessage(Message message) {
        if (!isInterest(message)) {
            throw new IllegalArgumentException("Message is not an Interest");
        }

        String name = stringProperty(message, CONTENT_NAME);
        if (name == null || name.length() == 0) {
            name = stringProperty(message, LEGACY_QUERY_KEY);
        }

        DTNHost sourceHost = message.getFrom();
        Object sourceAddress = message.getProperty(SOURCE_ADDRESS);
        if (sourceAddress instanceof Number) {
            sourceHost = SimScenario.getInstance().getWorld()
                    .getNodeByAddress(
                            ((Number) sourceAddress).intValue());
        }

        double createdAt = numberAsDouble(
                message.getProperty(CREATION_TIME), Double.NaN);
        if (Double.isNaN(createdAt)) {
            createdAt = numberAsDouble(
                    message.getProperty(LEGACY_START_TIME),
                    message.getCreationTime());
        }

        String logicalId = stringProperty(message, PACKET_ID);
        if (logicalId == null || logicalId.length() == 0) {
            logicalId = message.getId();
        }

        return new InterestPacket(name, sourceHost, createdAt,
                message.getTo(), logicalId);
    }

    private static String stringProperty(Message message, String key) {
        Object value = message.getProperty(key);
        return value == null ? null : String.valueOf(value);
    }

    private static double numberAsDouble(Object value,
            double defaultValue) {
        return value instanceof Number
                ? ((Number) value).doubleValue() : defaultValue;
    }
}
