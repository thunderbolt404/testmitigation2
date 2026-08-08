package routing.ccnutil;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import core.DTNHost;
import core.Message;
import core.SimClock;
import core.SimScenario;

/** Immutable Data packet with request-correlation metadata. */
public final class DataPacket {
    public static final String APP_ID = InterestPacket.APP_ID;

    public static final String PACKET_TYPE = InterestPacket.PACKET_TYPE;
    public static final String TYPE_DATA = "DATA";
    public static final String CONTENT_NAME = InterestPacket.CONTENT_NAME;
    public static final String PRODUCER_ADDRESS = "CCN.producerAddress";
    public static final String CREATION_TIME = InterestPacket.CREATION_TIME;
    public static final String PACKET_ID = InterestPacket.PACKET_ID;
    public static final String PAYLOAD = "CCN.payload";
    public static final String RESPONSE_TO_REQUEST_ID = "CCN.responseToRequestId";
    public static final String REQUEST_CREATION_TIME = "CCN.requestCreationTime";
    public static final String RESPONSE_SOURCE = "CCN.responseSource";
    public static final String SOURCE_STATIC_CACHE = "STATIC_CACHE";
    public static final String SOURCE_OPPORTUNISTIC_CACHE = "OPPORTUNISTIC_CACHE";
    public static final String SOURCE_UNKNOWN = "UNKNOWN";

    /** Legacy properties. */
    public static final String LEGACY_TYPE = InterestPacket.LEGACY_TYPE;
    public static final String LEGACY_DATA_TYPE = "queryResponse";
    public static final String LEGACY_QUERY_KEY =
            InterestPacket.LEGACY_QUERY_KEY;
    public static final String LEGACY_CONTENT = "content";

    private final String contentName;
    private final byte[] data;
    private final DTNHost producer;
    private final DTNHost destination;
    private final double creationTime;
    private final String packetId;
    private final String responseToRequestId;
    private final double requestCreationTime;
    private final String responseSource;

    public DataPacket(String contentName, byte[] data, DTNHost producer) {
        this(contentName, data, producer, null, SimClock.getTime(), null,
                null, -1.0, SOURCE_UNKNOWN);
    }

    public DataPacket(String contentName, String content, DTNHost producer) {
        this(contentName, content == null ? new byte[0]
                : content.getBytes(StandardCharsets.UTF_8), producer);
    }

    private DataPacket(String contentName, byte[] data, DTNHost producer,
            DTNHost destination, double creationTime, String packetId,
            String responseToRequestId, double requestCreationTime,
            String responseSource) {
        if (contentName == null || contentName.trim().length() == 0) {
            throw new IllegalArgumentException("contentName must not be empty");
        }
        if (data == null || producer == null) {
            throw new IllegalArgumentException("data and producer must not be null");
        }

        this.contentName = contentName.trim();
        this.data = data.clone();
        this.producer = producer;
        this.destination = destination;
        this.creationTime = creationTime;
        this.packetId = packetId;
        this.responseToRequestId = responseToRequestId;
        this.requestCreationTime = requestCreationTime;
        this.responseSource = responseSource == null
                ? SOURCE_UNKNOWN : responseSource;
    }

    public String getContentName() {
        return contentName;
    }

    public byte[] getData() {
        return data.clone();
    }

    public String getDataAsString() {
        return new String(data, StandardCharsets.UTF_8);
    }

    public int getDataSize() {
        return data.length;
    }

    public boolean hasContent() {
        return data.length > 0;
    }

    public DTNHost getProducer() {
        return producer;
    }

    public DTNHost getDestination() {
        return destination;
    }

    public double getCreationTime() {
        return creationTime;
    }

    public String getPacketId() {
        return packetId;
    }

    public String getResponseToRequestId() {
        return responseToRequestId;
    }

    public double getRequestCreationTime() {
        return requestCreationTime;
    }

    public String getResponseSource() {
        return responseSource;
    }

    /** Removes request-specific metadata before storing Data in a cache. */
    public DataPacket asCacheEntry() {
        return new DataPacket(contentName, data, producer, null,
                creationTime, null, null, -1.0,
                SOURCE_UNKNOWN);
    }

    /** Creates a new response for one Interest. */
    public DataPacket forResponse(InterestPacket interest,
            DTNHost requester, String sourceType) {
        return new DataPacket(contentName, data, producer, requester,
                creationTime, null, interest.getPacketId(),
                interest.getCreationTime(), sourceType);
    }

    /** Retains response metadata while changing only the DTN destination. */
    public DataPacket copyForDestination(DTNHost requester) {
        return new DataPacket(contentName, data, producer, requester,
                creationTime, packetId, responseToRequestId,
                requestCreationTime, responseSource);
    }

    public Message toMessage(DTNHost sender, String messageId,
            int simulatedSize) {
        if (sender == null || destination == null) {
            throw new IllegalStateException("sender and destination must be selected");
        }

        Message message = new Message(sender, destination, messageId,
                Math.max(1, simulatedSize));
        String logicalId = packetId == null ? messageId : packetId;

        message.addProperty(PACKET_TYPE, TYPE_DATA);
        message.addProperty(CONTENT_NAME, contentName);
        message.addProperty(PRODUCER_ADDRESS,
                Integer.valueOf(producer.getAddress()));
        message.addProperty(CREATION_TIME, Double.valueOf(creationTime));
        message.addProperty(PACKET_ID, logicalId);
        message.addProperty(PAYLOAD, data.clone());
        if (responseToRequestId != null) {
            message.addProperty(RESPONSE_TO_REQUEST_ID,
                    responseToRequestId);
        }
        if (requestCreationTime >= 0.0) {
            message.addProperty(REQUEST_CREATION_TIME,
                    Double.valueOf(requestCreationTime));
        }
        message.addProperty(RESPONSE_SOURCE, responseSource);

        /* Legacy compatibility. */
        message.addProperty(LEGACY_TYPE, LEGACY_DATA_TYPE);
        message.addProperty(LEGACY_QUERY_KEY, contentName);
        message.addProperty(LEGACY_CONTENT, getDataAsString());
        message.setAppID(APP_ID);
        return message;
    }

    public static boolean isData(Message message) {
        if (message == null) {
            return false;
        }
        Object packetType = message.getProperty(PACKET_TYPE);
        if (TYPE_DATA.equals(packetType)) {
            return true;
        }
        Object legacyType = message.getProperty(LEGACY_TYPE);
        return legacyType != null
                && LEGACY_DATA_TYPE.equalsIgnoreCase(
                        String.valueOf(legacyType));
    }

    public static DataPacket fromMessage(Message message) {
        if (!isData(message)) {
            throw new IllegalArgumentException("Message is not Data");
        }

        String name = stringProperty(message, CONTENT_NAME);
        if (name == null || name.length() == 0) {
            name = stringProperty(message, LEGACY_QUERY_KEY);
        }

        byte[] payload = null;
        Object rawPayload = message.getProperty(PAYLOAD);
        if (rawPayload instanceof byte[]) {
            payload = ((byte[]) rawPayload).clone();
        }
        if (payload == null) {
            String legacy = stringProperty(message, LEGACY_CONTENT);
            payload = legacy == null ? new byte[0]
                    : legacy.getBytes(StandardCharsets.UTF_8);
        }

        DTNHost producerHost = message.getFrom();
        Object producerAddress = message.getProperty(PRODUCER_ADDRESS);
        if (producerAddress instanceof Number) {
            producerHost = SimScenario.getInstance().getWorld()
                    .getNodeByAddress(
                            ((Number) producerAddress).intValue());
        }

        double createdAt = numberAsDouble(
                message.getProperty(CREATION_TIME),
                message.getCreationTime());
        String logicalId = stringProperty(message, PACKET_ID);
        if (logicalId == null || logicalId.length() == 0) {
            logicalId = message.getId();
        }

        String responseTo = stringProperty(message,
                RESPONSE_TO_REQUEST_ID);
        double requestCreatedAt = numberAsDouble(
                message.getProperty(REQUEST_CREATION_TIME), -1.0);
        String source = stringProperty(message, RESPONSE_SOURCE);
        if (source == null) {
            source = SOURCE_UNKNOWN;
        }

        return new DataPacket(name, payload, producerHost, message.getTo(),
                createdAt, logicalId, responseTo, requestCreatedAt, source);
    }

    /** Identity of one logical Data bundle and all of its DTN replicas. */
    public String getForwardingFingerprint() {
        if (packetId != null) {
            return packetId;
        }
        return contentName + ":" + producer.getAddress() + ":"
                + creationTime + ":" + Arrays.hashCode(data);
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
