package routing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import util.Tuple;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SimClock;
import core.SimScenario;
import core.World;
import routing.ccnutil.*;
import static util.Constants.*;

/**
 * CCN-aware router using Epidemic or Spray-and-Wait or Prophet as the DTN carrier.
 *
 * Processing order for Interests:
 * duplicate nonce -> Static Cache -> Opportunistic Cache -> PIT -> DTN.
 *
 * Processing order for Data:
 * duplicate Data -> Opportunistic Cache -> PIT satisfaction -> DTN.
 */
public class CCNRouter extends ActiveRouter {
    public static final String CCN_NS = "CCNRouter";

    public static final String FORWARDING_MODE_S = "forwardingMode";
    public static final String MODE_EPIDEMIC = "EPIDEMIC";
    public static final String MODE_SPRAY_AND_WAIT = "SPRAY_AND_WAIT";
    public static final String MODE_PROPHET = "PROPHET";

    public static final String LEGACY_CACHE_CAPACITY_S = "cacheCapacity";
    public static final String PIT_TIMEOUT_S = "pitTimeout";
    public static final String INTEREST_SIZE_S = "interestSize";
    public static final String CONTENT_SIZE_S = "contentSize";

    public static final String STATIC_CACHE_CAPACITY_S = "staticCacheCapacity";
    public static final String STATIC_CACHE_RANGE_S = "staticCacheRange";
    public static final String STATIC_CACHE_SEED_S = "seedStaticCache";
    public static final String AVA_CACHE_S = "avaCache";
    public static final String ENABLE_PIT_S = "enablePIT";
    public static final String NODE_MODE_S = "mode";
    public static final String RANDOM_SEED_S = "seed";

    // PROPHET
    public static final String SECONDS_IN_UNIT_S = "secondsInTimeUnit";
    public static final double PROPHET_P_INIT = 0.75;
    public static final double PROPHET_DEFAULT_BETA = 0.25;
    public static final double PROPHET_DEFAULT_GAMMA = 0.98;

    // router now reads only active Group/CCNRouter settings keys.
    public static final String PROCESSED_LIST_CAPACITY_S = "processedListCapacity";

    public static final String NROF_COPIES_S = "nrofCopies";
    public static final String BINARY_MODE_S = "binaryMode";
    public static final String COPY_PROPERTY = CCN_NS + ".copies";

    public static final int MODE_CONSUMER = 1;
    public static final int MODE_PRODUCER = 2;
    public static final int MODE_INTERMEDIATE = 3;

    private String forwardingMode;
    private int opportunisticCacheCapacity;
    private double pitTimeout;
    private int interestSize;
    private int dataMessageSize;
    private int initialNrofCopies;
    private boolean binaryMode;
    private int processedListCapacity;

    private int nodeMode;
    private boolean staticCacheEnabled;
    private boolean opportunisticCacheEnabled;
    private boolean pitEnabled;

    private int staticCacheCapacity;
    private int staticCacheRangeMin;
    private int staticCacheRangeMax;
    private int staticCacheSeed;
    private int randomSeed;

    // PROPHET
    private int secondsInTimeUnit;
    private double beta;
    private double gamma;
    private Map<DTNHost, Double> preds;
    private double lastAgeUpdate;

    private PIT pit;
    private OpportunisticCache opportunisticCache;
    private StaticCache staticCache;
    private CCNRouterStats routerStats;

    /* Data delivered to the local consumer, indexed by request ID. */
    private Map<String, DataPacket> localData;
    /* Bounded duplicate-suppression state. */
    private LinkedHashMap<String, Boolean> seenInterestPackets;
    private LinkedHashMap<String, Boolean> seenDataPackets;

    private Message incomingMessageInProcess;
    private long localMessageSequence;
    private Random randomGenerator;
    private boolean randomStaticContentInstalled;

    public CCNRouter(Settings settings) {
        super(settings);
        Settings ccn = new Settings(CCN_NS);

        forwardingMode = normalizeForwardingMode(getStringAny(settings, ccn,
                new String[] { FORWARDING_MODE_S }, MODE_EPIDEMIC));
        // GroupX.cacheCapacity is primary; CCNRouter.cacheCapacity is fallback.
        opportunisticCacheCapacity = getIntAny(settings, ccn,
                new String[] { LEGACY_CACHE_CAPACITY_S }, 50);
        pitTimeout = getDoubleAny(settings, ccn,
                new String[] { PIT_TIMEOUT_S }, -1.0);
        interestSize = getIntAny(settings, ccn,
                new String[] { INTEREST_SIZE_S }, 50);
        // Data size comes from GroupX.contentSize/CCNRouter.contentSize.
        dataMessageSize = getIntAny(settings, ccn,
                new String[] { CONTENT_SIZE_S }, interestSize * 3);
        initialNrofCopies = getIntAny(settings, ccn,
                new String[] { NROF_COPIES_S }, 4);
        binaryMode = getBooleanAny(settings, ccn,
                new String[] { BINARY_MODE_S }, true);
        processedListCapacity = getIntAny(settings, ccn,
                new String[] { PROCESSED_LIST_CAPACITY_S }, 500);
        nodeMode = normalizeNodeMode(getIntAny(settings, ccn,
                new String[] { NODE_MODE_S }, MODE_INTERMEDIATE));
        // safe default is relay-style: no Static Cache, Opportunistic enabled.
        int[] availability = getCsvIntsAny(settings, ccn,
                new String[] { AVA_CACHE_S }, new int[] { 0, 1 }, 2);
        staticCacheEnabled = availability[0] >= 1;
        opportunisticCacheEnabled = availability[1] >= 1;
        pitEnabled = getBooleanAny(settings, ccn,
                new String[] { ENABLE_PIT_S }, true);

        staticCacheCapacity = getIntAny(settings, ccn,
                new String[] { STATIC_CACHE_CAPACITY_S }, 0);
        int[] range = getCsvIntsAny(settings, ccn,
                new String[] { STATIC_CACHE_RANGE_S },
                new int[] { 1, 100 }, 2);
        staticCacheRangeMin = range[0];
        staticCacheRangeMax = range[1];
        staticCacheSeed = getIntAny(settings, ccn,
                new String[] { STATIC_CACHE_SEED_S }, 16);
        randomSeed = getIntAny(settings, ccn,
                new String[] { RANDOM_SEED_S }, 0);

        // PROPHET
        secondsInTimeUnit = getIntAny(settings, ccn,
                new String[] { SECONDS_IN_UNIT_S }, 30);
        beta = PROPHET_DEFAULT_BETA;
        gamma =  PROPHET_DEFAULT_GAMMA;

        initializeState();
    }

    protected CCNRouter(CCNRouter prototype) {
        super(prototype);
        forwardingMode = prototype.forwardingMode;
        opportunisticCacheCapacity = prototype.opportunisticCacheCapacity;
        pitTimeout = prototype.pitTimeout;
        interestSize = prototype.interestSize;
        dataMessageSize = prototype.dataMessageSize;
        initialNrofCopies = prototype.initialNrofCopies;
        binaryMode = prototype.binaryMode;
        processedListCapacity = prototype.processedListCapacity;
        nodeMode = prototype.nodeMode;
        staticCacheEnabled = prototype.staticCacheEnabled;
        opportunisticCacheEnabled = prototype.opportunisticCacheEnabled;
        pitEnabled = prototype.pitEnabled;

        staticCacheCapacity = prototype.staticCacheCapacity;
        staticCacheRangeMin = prototype.staticCacheRangeMin;
        staticCacheRangeMax = prototype.staticCacheRangeMax;
        staticCacheSeed = prototype.staticCacheSeed;
        randomSeed = prototype.randomSeed;

        secondsInTimeUnit = prototype.secondsInTimeUnit;
        beta = prototype.beta;
        gamma = prototype.gamma;

        initializeState();
    }

    private void initializeState() {
        pit = new PIT();
        opportunisticCache = new OpportunisticCache(opportunisticCacheEnabled ? opportunisticCacheCapacity : 0);
        staticCache = new StaticCache();
        routerStats = new CCNRouterStats();
        localData = new LinkedHashMap<String, DataPacket>();
        seenInterestPackets = new LinkedHashMap<String, Boolean>();
        seenDataPackets = new LinkedHashMap<String, Boolean>();
        incomingMessageInProcess = null;
        localMessageSequence = 0L;
        randomGenerator = null;
        randomStaticContentInstalled = false;

        // PROPHET
        initPreds();
        lastAgeUpdate = 0.0;
    }

    @Override
    public void init(DTNHost host, List<MessageListener> listeners) {
        super.init(host, listeners);
        InitRandomStaticContent();
    }

    /** Handles a completed incoming Interest. */
    public void receiveInterest(InterestPacket interest, DTNHost from) {
        if (interest == null || from == null) {
            throw new IllegalArgumentException("interest and from must not be null");
        }

        routerStats.recordInterestReception();
        pit.clearExpiredEntries();

        /* Nonce/packet duplicate suppression must run before cache and PIT. */
        if (rememberInterestAndCheckDuplicate(interest)) {
            routerStats.recordDuplicateInterestSuppressed();
            suppressCurrentIncomingMessage();
            return;
        }

        String name = interest.getContentName();
        DTNHost requester = interest.getSource();

        DataPacket cached = null;
        if (staticCacheEnabled) {
            cached = staticCache.getData(name);
            routerStats.recordStaticCacheLookup(cached != null);
            if (cached != null) {
                forwardData(cached.forResponse(interest, requester,
                        DataPacket.SOURCE_STATIC_CACHE));
                suppressCurrentIncomingMessage();
                return;
            }
        }

        if (opportunisticCacheEnabled) {
            cached = opportunisticCache.getData(name);
            routerStats.recordOpportunisticCacheLookup(cached != null);
            if (cached != null) {
                forwardData(cached.forResponse(interest, requester,
                        DataPacket.SOURCE_OPPORTUNISTIC_CACHE));
                suppressCurrentIncomingMessage();
                return;
            }
        }

        if (pitEnabled) {
            int result = pit.addInterest(interest, pitTimeout);
            if (result == PIT.AGGREGATED) {
                routerStats.recordPitAggregation();
                suppressCurrentIncomingMessage();
                return;
            }
            if (result == PIT.DUPLICATE_NONCE) {
                routerStats.recordDuplicateInterestSuppressed();
                suppressCurrentIncomingMessage();
                return;
            }
        }

        /* If this host was the temporary destination, continue the Interest. */
        if (incomingMessageInProcess == null
                || !hasMessage(incomingMessageInProcess.getId())) {
            forwardInterest(interest);
        }
    }

    /** Handles a completed incoming Data packet. */
    public void receiveData(DataPacket data, DTNHost from) {
        if (data == null || from == null) {
            throw new IllegalArgumentException("data and from must not be null");
        }

        routerStats.recordDataReception();
        pit.clearExpiredEntries();

        /* Old code suppressed an already processed message before caching. */
        if (rememberDataAndCheckDuplicate(data)) {
            routerStats.recordDuplicateDataSuppressed();
            suppressCurrentIncomingMessage();
            return;
        }

        if (opportunisticCacheEnabled && data.hasContent()) {
            opportunisticCache.insertData(data);
        }

        if (pitEnabled && pit.containsInterest(data.getContentName())) {
            satisfyPIT(data);
            suppressCurrentIncomingMessage();
            return;
        }

        if (data.getDestination() == getHost()) {
            String key = data.getResponseToRequestId() == null
                    ? data.getContentName()
                    : data.getResponseToRequestId();
            localData.put(key, data);
        }
    }

    /** Creates a ONE Interest bundle. */
    public boolean forwardInterest(InterestPacket interest) {
        if (interest == null) {
            return false;
        }

        DTNHost destination = interest.getDestination();
        if (destination == null || destination == getHost()) {
            destination = randomHost(getHost(), interest.getSource());
        }
        if (destination == null) {
            return false;
        }

        InterestPacket outgoing = interest.copyForDestination(destination);
        //query_ad-20-1000-3 - message id
        Message message = outgoing.toMessage(getHost(), nextMessageId("query_ad"), interestSize);
        return createNewMessage(message);
    }

    /** Creates one destination-addressed Data bundle. */
    public void forwardData(DataPacket data) {
        if (data == null || data.getDestination() == null) {
            throw new IllegalArgumentException("Data destination must not be null");
        }
        int size = Math.max(dataMessageSize, data.getDataSize());
        Message message = data.toMessage(getHost(),
                nextMessageId("queryResponse"), size);
        createNewMessage(message);
    }

    /** Sends one response for every pending Interest request. */
    public void satisfyPIT(DataPacket data) {
        PITEntry entry = pit.removeInterest(data.getContentName());
        if (entry == null) {
            return;
        }

        ArrayList<InterestPacket> pending = entry.getInterests();
        for (InterestPacket interest : pending) {
            DTNHost requester = interest.getSource();
            DataPacket response = data.forResponse(interest, requester,
                    data.getResponseSource());
            if (requester == getHost()) {
                localData.put(interest.getPacketId(), response);
            } else {
                forwardData(response);
            }
        }
    }

    /** Consumer request API with an explicit unique request ID. */
    public DataPacket requestContent(String contentName, String requestId) {
        if (contentName == null || contentName.trim().length() == 0
                || requestId == null || requestId.length() == 0) {
            throw new IllegalArgumentException("contentName and requestId must not be empty");
        }

//        Content = 42
//        Source = current consumer
//        Creation time = current simulation time
//        Destination = null
//        Packet ID = unique request ID
        InterestPacket interest = new InterestPacket(contentName.trim(),
                getHost(), SimClock.getTime(), null, requestId);

        DataPacket data = null;
        if (staticCacheEnabled) {
            data = staticCache.getData(contentName);
            routerStats.recordStaticCacheLookup(data != null);
            if (data != null) {
                return data.forResponse(interest, getHost(),
                        DataPacket.SOURCE_STATIC_CACHE);
            }
        }

        if (opportunisticCacheEnabled) {
            data = opportunisticCache.getData(contentName);
            routerStats.recordOpportunisticCacheLookup(data != null);
            if (data != null) {
                return data.forResponse(interest, getHost(),
                        DataPacket.SOURCE_OPPORTUNISTIC_CACHE);
            }
        }

        if (pitEnabled) {
            int result = pit.addInterest(interest, pitTimeout);
            if (result == PIT.AGGREGATED) {
                routerStats.recordPitAggregation();
                return null;
            }
        }

        rememberInterestWithoutDuplicateCount(interest);
        forwardInterest(interest);
        return null;
    }

    /** Compatibility overload. */
    public DataPacket requestContent(String contentName) {
        return requestContent(contentName,
                nextMessageId("localRequest"));
    }


    @Override
    public boolean createNewMessage(Message message) {
        if (MODE_SPRAY_AND_WAIT.equals(forwardingMode)
                && message.getProperty(COPY_PROPERTY) == null) {
            message.addProperty(COPY_PROPERTY,
                    Integer.valueOf(initialNrofCopies));
        }
        return super.createNewMessage(message);
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message message = super.messageTransferred(id, from);
        if (message == null) {
            return null;
        }

        if (MODE_SPRAY_AND_WAIT.equals(forwardingMode)) {
            updateReceivedCopyCount(message);
        }

        incomingMessageInProcess = message;
        try {
            if (InterestPacket.isInterest(message)) {
                receiveInterest(InterestPacket.fromMessage(message), from);
            } else if (DataPacket.isData(message)) {
                receiveData(DataPacket.fromMessage(message), from);
            }
        } finally {
            incomingMessageInProcess = null;
        }
        return message;
    }

    @Override
    public void update() {
        super.update();
        pit.clearExpiredEntries();

        if (isTransferring() || !canStartTransfer()) {
            return;
        }
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        if (MODE_SPRAY_AND_WAIT.equals(forwardingMode)) {
            List<Message> sprayable = sortByQueueMode(
                    getMessagesWithCopiesLeft());
            if (!sprayable.isEmpty()) {
                tryMessagesToConnections(sprayable, getConnections());
            }
        } else if (MODE_PROPHET.equals(forwardingMode)) {
            tryOtherMessages();
        } else {
            tryAllMessagesToAllConnections();
        }
    }

    @Override
    protected void transferDone(Connection connection) {
        Message transferred = connection.getMessage();
        if (transferred == null) {
            return;
        }

        if (InterestPacket.isInterest(transferred)) {
            routerStats.recordInterestTransmission();
        } else if (DataPacket.isData(transferred)) {
            routerStats.recordDataTransmission();
        }

        if (!MODE_SPRAY_AND_WAIT.equals(forwardingMode)) {
            return;
        }

        Message localCopy = getMessage(transferred.getId());
        if (localCopy == null) {
            return;
        }
        Object raw = localCopy.getProperty(COPY_PROPERTY);
        if (!(raw instanceof Integer)) {
            return;
        }

        int copies = ((Integer) raw).intValue();
        int remaining = binaryMode ? copies / 2 : copies - 1;
        localCopy.updateProperty(COPY_PROPERTY,
                Integer.valueOf(Math.max(1, remaining)));
    }

    /**
     * Compatibility rule: CCNRouter forced every received query_ad
     * (Interest) message to two copies, regardless of binary/standard mode.
     * Data messages keep the normal Spray-and-Wait split.
     */
    private void updateReceivedCopyCount(Message message) {
        Object raw = message.getProperty(COPY_PROPERTY);
        if (!(raw instanceof Integer)) {
            message.addProperty(COPY_PROPERTY, Integer.valueOf(1));
            return;
        }

//        if (InterestPacket.isInterest(message)) {
//            message.updateProperty(COPY_PROPERTY, Integer.valueOf(2));
//            return;
//        }

        int copies = ((Integer) raw).intValue();
        int received = binaryMode
                ? (int) Math.ceil(copies / 2.0) : 1;
        message.updateProperty(COPY_PROPERTY,
                Integer.valueOf(Math.max(1, received)));
    }

    private List<Message> getMessagesWithCopiesLeft() {
        List<Message> messages = new ArrayList<Message>();
        for (Message message : getMessageCollection()) {
            Object raw = message.getProperty(COPY_PROPERTY);
            if (raw instanceof Integer
                    && ((Integer) raw).intValue() > 1) {
                messages.add(message);
            }
        }
        return messages;
    }

    private boolean rememberInterestAndCheckDuplicate(InterestPacket interest) {
        String id = interest.getPacketId();
        if (id == null || id.length() == 0) {
            id = interest.getContentName() + "@"
                    + interest.getSource().getAddress() + "@"
                    + interest.getCreationTime();
        }
        return rememberAndCheckDuplicate(seenInterestPackets, id);
    }

    private void rememberInterestWithoutDuplicateCount(
            InterestPacket interest) {
        String id = interest.getPacketId();
        if (id != null) {
            rememberAndCheckDuplicate(seenInterestPackets, id);
        }
    }

    private boolean rememberDataAndCheckDuplicate(DataPacket data) {
        return rememberAndCheckDuplicate(seenDataPackets,
                data.getForwardingFingerprint());
    }

    /** FIFO bounded state, matching the old 500-entry processed list. */
    private boolean rememberAndCheckDuplicate( LinkedHashMap<String, Boolean> table, String key) {
        if (table.containsKey(key)) {
            return true;
        }
        table.put(key, Boolean.TRUE);
        while (table.size() > processedListCapacity) {
            Iterator<String> iterator = table.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            } else {
                break;
            }
        }
        return false;
    }

    private void suppressCurrentIncomingMessage() {
        if (incomingMessageInProcess != null
                && hasMessage(incomingMessageInProcess.getId())) {
            deleteMessage(incomingMessageInProcess.getId(), false);
        }
    }

    /** Reproduces the old random permanent-cache initialization. */
    private void InitRandomStaticContent() {
        if (randomStaticContentInstalled
                || nodeMode != MODE_PRODUCER
                || !staticCacheEnabled
                || staticCacheCapacity <= 0
                || getHost() == null) {
            return;
        }

        int range = staticCacheRangeMax - staticCacheRangeMin;
        if (range <= 0) {
            return;
        }
        int target = Math.min(staticCacheCapacity, range);
        Random random = new Random(staticCacheSeed
                + getHost().getAddress() + 1);

        int inserted = 0;
        while (inserted < target) {
            int key = staticCacheRangeMin + random.nextInt(range);
            DataPacket data = new DataPacket(String.valueOf(key),
                    String.valueOf(inserted + 1), getHost());
            if (staticCache.insertDataIfAbsent(data)) {
                inserted++;
            }
        }
        randomStaticContentInstalled = true;
    }

    /** Old random-host seed: seed + 2 * host address. */
    private DTNHost randomHost(DTNHost excludedOne,
                               DTNHost excludedTwo) {
        World world = SimScenario.getInstance().getWorld();
        List<DTNHost> hosts = world.getHosts();
        if (hosts == null || hosts.size() <= 1) {
            return null;
        }

        if (randomGenerator == null) {
            randomGenerator = new Random(randomSeed
                    + 2 * getHost().getAddress());
        }

        int attempts = Math.max(20, hosts.size() * 3);
        for (int i = 0; i < attempts; i++) {
            DTNHost candidate = hosts.get(
                    randomGenerator.nextInt(hosts.size()));
            if (candidate != getHost() && candidate != excludedOne
                    && candidate != excludedTwo) {
                return candidate;
            }
        }

        for (DTNHost candidate : hosts) {
            if (candidate != getHost() && candidate != excludedOne
                    && candidate != excludedTwo) {
                return candidate;
            }
        }
        return null;
    }

    private String nextMessageId(String prefix) {
        localMessageSequence++;
        return prefix + "-" + getHost().getAddress() + "-"
                + SimClock.getIntTime() + "-" + localMessageSequence;
    }

    private static int normalizeNodeMode(int value) {
        if (value == MODE_CONSUMER || value == MODE_PRODUCER
                || value == MODE_INTERMEDIATE) {
            return value;
        }
        return MODE_INTERMEDIATE;
    }

    private static String normalizeForwardingMode(String value) {
        String normalized = value.trim().toUpperCase().replace('-', '_');
        if (MODE_EPIDEMIC.equals(normalized)) {
            return MODE_EPIDEMIC;
        }
        if (MODE_SPRAY_AND_WAIT.equals(normalized)
                || "SPRAYANDWAIT".equals(normalized)) {
            return MODE_SPRAY_AND_WAIT;
        }
        if (MODE_PROPHET.equals(normalized)) {
            return MODE_PROPHET;
        }
        throw new IllegalArgumentException(
                "Unknown forwardingMode: " + value);
    }



    @Override
    public CCNRouter replicate() {
        return new CCNRouter(this);
    }


    public PIT getPIT() {
        return pit;
    }

    public StaticCache getStaticCache() {
        return staticCache;
    }

    public OpportunisticCache getOpportunisticCache() {
        return opportunisticCache;
    }

    public CCNRouterStats getRouterStats() {
        return routerStats;
    }

    public boolean isStaticCacheEnabled() {
        return staticCacheEnabled;
    }

    public boolean isOpportunisticCacheEnabled() {
        return opportunisticCacheEnabled;
    }

    public boolean isPitEnabled() {
        return pitEnabled;
    }

    public int getNodeMode() {
        return nodeMode;
    }

    // PROPHET
    private void initPreds() {
        this.preds = new HashMap<DTNHost, Double>();
    }

    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);

        if (!MODE_PROPHET.equals(forwardingMode) || !con.isUp()) {
            return;
        }

        DTNHost otherHost = con.getOtherNode(getHost());

        updateDeliveryPredFor(otherHost);
        updateTransitivePreds(otherHost);
    }

    /**
     * Updates delivery predictions for a host.
     * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT</CODE>
     * @param host The host we just met
     */
    private void updateDeliveryPredFor(DTNHost host) {
        double oldValue = getPredFor(host);
        double newValue = oldValue + (1 - oldValue) * PROPHET_P_INIT;
        preds.put(host, newValue);
    }

    /**
     * Updates transitive (A->B->C) delivery predictions.
     * <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
     * </CODE>
     * @param host The B host who we just met
     */
    private void updateTransitivePreds(DTNHost host) {
        CCNRouter otherRouter = (CCNRouter) host.getRouter();

        double pForHost = getPredFor(host);
        Map<DTNHost, Double> otherPreds =
                otherRouter.getDeliveryPreds();

        for (Map.Entry<DTNHost, Double> entry : otherPreds.entrySet()) {
            if (entry.getKey() == getHost()) {
                continue;
            }

            double pOld = getPredFor(entry.getKey());
            double pNew = pOld
                    + (1.0 - pOld)
                    * pForHost
                    * entry.getValue()
                    * beta;
            preds.put(entry.getKey(), pNew);
        }
    }

    /**
     * Returns the current prediction (P) value for a host or 0 if entry for
     * the host doesn't exist.
     * @param host The host to look the P for
     * @return the current P value
     */
    public double getPredFor(DTNHost host) {
        ageDeliveryPreds();
        Double value = preds.get(host);
        return value == null ? 0.0 : value.doubleValue();
    }

    /**
     * Ages all entries in the delivery predictions.
     * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of
     * time units that have elapsed since the last time the metric was aged.
     * @see #SECONDS_IN_UNIT_S
     */
    private void ageDeliveryPreds() {
        double timeDiff =
                (SimClock.getTime() - lastAgeUpdate)
                        / secondsInTimeUnit;

        if (timeDiff <= 0.0) {
            return;
        }

        double multiplier = Math.pow(gamma, timeDiff);
        for (Map.Entry<DTNHost, Double> entry : preds.entrySet()) {
            entry.setValue(entry.getValue() * multiplier);
        }

        lastAgeUpdate = SimClock.getTime();
    }

    /**
     * Returns a map of this router's delivery predictions
     * @return a map of this router's delivery predictions
     */
    private Map<DTNHost, Double> getDeliveryPreds() {
        ageDeliveryPreds();
        return preds;
    }

    /**
     * Tries to send all other messages to all connected hosts ordered by
     * their delivery probability
     * @return The return value of {@link #tryMessagesForConnected(List)}
     */
    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages =
                new ArrayList<Tuple<Message, Connection>>();

        Collection<Message> msgCollection = getMessageCollection();

        for (Connection con : getConnections()) {
            DTNHost otherHost = con.getOtherNode(getHost());

            CCNRouter otherRouter = (CCNRouter) otherHost.getRouter();
            if (otherRouter.isTransferring()) {
                continue;
            }

            for (Message message : msgCollection) {
                if (otherRouter.hasMessage(message.getId())) {
                    continue;
                }

                DTNHost destination = message.getTo();
                if (destination == null) {
                    continue;
                }

                if (otherRouter.getPredFor(destination)
                        > getPredFor(destination)) {
                    messages.add(
                            new Tuple<Message, Connection>(message, con));
                }
            }
        }

        if (messages.size() == 0) {
            return null;
        }

        Collections.sort(messages, new TupleComparator());
        return tryMessagesForConnected(messages);
    }

    /**
     * Comparator for Message-Connection-Tuples that orders the tuples by
     * their delivery probability by the host on the other side of the
     * connection (GRTRMax)
     */
    private class TupleComparator
            implements Comparator<Tuple<Message, Connection>> {

        @Override
        public int compare(Tuple<Message, Connection> first,
                           Tuple<Message, Connection> second) {

            DTNHost firstPeer =
                    first.getValue().getOtherNode(getHost());
            DTNHost secondPeer =
                    second.getValue().getOtherNode(getHost());

            CCNRouter firstRouter =
                    (CCNRouter) firstPeer.getRouter();
            CCNRouter secondRouter =
                    (CCNRouter) secondPeer.getRouter();

            double p1 =
                    firstRouter.getPredFor(first.getKey().getTo());
            double p2 =
                    secondRouter.getPredFor(second.getKey().getTo());

            if (p2 - p1 == 0.0) {
                return compareByQueueMode(
                        first.getKey(), second.getKey());
            } else if (p2 - p1 < 0.0) {
                return -1;
            } else {
                return 1;
            }
        }
    }


}