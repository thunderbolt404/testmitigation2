package applications;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import core.Application;
import core.DTNHost;
import core.DTNSim;
import core.Message;
import core.Settings;
import core.SimClock;
import routing.CCNRouter;
import routing.ccnutil.DataPacket;

/**
 * Generates Interests and reports request completion. All forwarding-plane
 * cache/PIT/nonce decisions are owned by CCNRouter.
 */
public class CCN_application extends Application {
    public static final String APP_ID = InterestPacketAppId.VALUE;

    public static final String CCN_INTERVAL = "interval";
    public static final String CCN_OFFSET = "offset";
    public static final String CCN_SEED = "seed";
    public static final String SEED_QUERY = "seedQuery";
    public static final String QUERY_RANGE = "queryRange";
    public static final String NUM_OF_MSG_TO_GENERATE = "numOfMsgToGenerate";
    public static final String QUERY_DISTRIBUTION = "queryDistribution";

    // CHANGED: cache, PIT, role and packet-size settings are owned by CCNRouter.

    /* Same custom Zipf parameters as the old CCN_application. */
    private static final int ZIPF_RANGE = 500;
    private static final double TOP_RATIO = 0.2;
    private static final int ZIPF_S = 1;
    private static final int POPULAR_CONTENT_SEED = 2;

    private double lastPing;
    private double interval;
    private int seed;
    private int seedForQuery;


    private int queryRangeMin;
    private int queryRangeMax;
    private int queryDistribution;
    private int numberOfMessagesToGenerate;

    private int generatedAttemptCount;
    private long requestSequence;
    private Random randomQueryGenerator;
    private Random zipfQueryGenerator;
    private int[] contentLadder;
    private int[] popularContent;
    private int numberOfPopularContent;

    private HashMap<String, PendingQuery> pendingByRequestId;
    private HashMap<String, String> pendingRequestIdByContent;

    /** Avoids duplicating the APP_ID string without importing routing internals. */
    private static final class InterestPacketAppId {
        private static final String VALUE = "fi.dcslab.CCN_application";
    }

    private static final class PendingQuery {
        private final String requestId;
        private final String contentName;
        private final double creationTime;

        private PendingQuery(String requestId, String contentName,
                             double creationTime) {
            this.requestId = requestId;
            this.contentName = contentName;
            this.creationTime = creationTime;
        }
    }

    static {
        DTNSim.registerForReset(
                CCN_application.class.getCanonicalName());
        reset();
    }

    public static void reset() {
        /* No global mutable state. */
    }

    public CCN_application(Settings settings) {
        lastPing = settings.contains(CCN_OFFSET)
                ? settings.getDouble(CCN_OFFSET) : 0.0;
        interval = settings.contains(CCN_INTERVAL)
                ? settings.getDouble(CCN_INTERVAL) : 500.0;
        seed = settings.contains(CCN_SEED)
                ? settings.getInt(CCN_SEED) : 0;
        seedForQuery = settings.contains(SEED_QUERY)
                ? settings.getInt(SEED_QUERY) : 6;
        // CHANGED: router configuration is no longer read by the application.

        int[] queryRange = settings.contains(QUERY_RANGE)
                ? settings.getCsvInts(QUERY_RANGE, 2)
                : new int[] { 1, 100 };
        queryRangeMin = queryRange[0];
        queryRangeMax = queryRange[1];
        queryDistribution = settings.contains(QUERY_DISTRIBUTION)
                ? settings.getInt(QUERY_DISTRIBUTION) : 1;
        if (queryDistribution != 1 && queryDistribution != 2) {
            queryDistribution = 1;
        }

        numberOfMessagesToGenerate =
                settings.contains(NUM_OF_MSG_TO_GENERATE)
                        ? settings.getInt(NUM_OF_MSG_TO_GENERATE) : 50;

        validateSettings();
        initializeRuntimeState();
        setAppID(APP_ID);
    }

    public CCN_application(CCN_application prototype) {
        super(prototype);
        lastPing = prototype.lastPing;
        interval = prototype.interval;
        seed = prototype.seed;
        seedForQuery = prototype.seedForQuery;
        // CHANGED: no router configuration is copied by the workload application.

        queryRangeMin = prototype.queryRangeMin;
        queryRangeMax = prototype.queryRangeMax;
        queryDistribution = prototype.queryDistribution;
        numberOfMessagesToGenerate =
                prototype.numberOfMessagesToGenerate;

        initializeRuntimeState();
        if (prototype.contentLadder != null) {
            contentLadder = prototype.contentLadder.clone();
        }
        if (prototype.popularContent != null) {
            popularContent = prototype.popularContent.clone();
        }
        numberOfPopularContent = prototype.numberOfPopularContent;
        setAppID(APP_ID);
    }

    private void validateSettings() {
        if (interval <= 0.0) {
            throw new IllegalArgumentException(
                    "interval must be positive");
        }
        if (queryRangeMax <= queryRangeMin) {
            throw new IllegalArgumentException(
                    "queryRange max must be greater than min");
        }
        if (numberOfMessagesToGenerate < 0) {
            throw new IllegalArgumentException(
                    "numOfMsgToGenerate must be >= 0");
        }
    }

    private void initializeRuntimeState() {
        generatedAttemptCount = 0;
        requestSequence = 0L;
        randomQueryGenerator = null;
        zipfQueryGenerator = null;
        pendingByRequestId = new HashMap<String, PendingQuery>();
        pendingRequestIdByContent = new HashMap<String, String>();

        // CHANGED: only applications configured with Zipf need these tables.
        if (queryDistribution == 2) {
            createContentLadder();
            createPopularContent();
        }
    }

    /**
     * Receives final Data only for request-level reporting. It never performs
     * cache, PIT, nonce, or forwarding decisions.
     */
    @Override
    public Message handle(Message message, DTNHost host) {
        if (!DataPacket.isData(message) || message.getTo() != host) {
            return message;
        }

        DataPacket data = DataPacket.fromMessage(message);
        PendingQuery pending = null;
        String requestId = data.getResponseToRequestId();

        if (requestId != null) {
            pending = pendingByRequestId.remove(requestId);
        }
        if (pending == null) {
            String fallbackId = pendingRequestIdByContent.get(
                    data.getContentName());
            if (fallbackId != null) {
                pending = pendingByRequestId.remove(fallbackId);
                requestId = fallbackId;
            }
        }

        /* A duplicate Data packet must not create another response event. */
        if (pending == null) {
            return message;
        }

        pendingRequestIdByContent.remove(pending.contentName);
        CCNRequestEvent response = new CCNRequestEvent(
                pending.requestId, pending.contentName,
                pending.creationTime, SimClock.getTime(),
                data.getResponseSource());
        sendEventToListeners("OriginalGotResponse", response, host);
        return message;
    }

    @Override
    public void update(DTNHost host) {
        CCNRouter router = requireCCNRouter(host);

        // CHANGED: CCNRouter is the single source of truth for node role.
        if (router.getNodeMode() != CCNRouter.MODE_CONSUMER) {
            return;
        }

        double now = SimClock.getTime();
        if (generatedAttemptCount >= numberOfMessagesToGenerate
                || now - lastPing < interval) {
            return;
        }

        /* Every scheduled attempt consumes the configured quota, as before. */
        generatedAttemptCount++;
        lastPing = now;

        String contentName = String.valueOf(nextQueryKey(host));
        if (pendingRequestIdByContent.containsKey(contentName)) {
            sendEventToListeners("SentDuplicatedQuery", contentName, host);
            return;
        }

        String requestId = nextRequestId(host);
        CCNRequestEvent request = new CCNRequestEvent(
                requestId, contentName, now);
        sendEventToListeners("SentQuery", request, host);

        DataPacket immediate = router.requestContent(contentName, requestId);
        if (immediate != null) {
            CCNRequestEvent response = new CCNRequestEvent(
                    requestId, contentName, now, SimClock.getTime(),
                    immediate.getResponseSource());
            sendEventToListeners("OriginalGotResponse", response, host);
            return;
        }

        PendingQuery pending = new PendingQuery(requestId,
                contentName, now);
        pendingByRequestId.put(requestId, pending);
        pendingRequestIdByContent.put(contentName, requestId);
    }

    private String nextRequestId(DTNHost host) {
        requestSequence++;
        return "REQ-" + host.getAddress() + "-"
                + SimClock.getIntTime() + "-" + requestSequence;
    }

    /** Restores the old random and custom-Zipf workload generation. */
    private int nextQueryKey(DTNHost host) {
        if (queryDistribution == 2) {
            if (zipfQueryGenerator == null) {
                zipfQueryGenerator = new Random(host.getAddress());
            }
            int rank;
            do {
                int value = zipfQueryGenerator.nextInt(ZIPF_RANGE);
                rank = getRankInContentLadder(value);
            } while (rank < 0 || rank >= popularContent.length);
            return popularContent[rank];
        }

        if (randomQueryGenerator == null) {
            /* The old random mode used the same seed on every consumer. */
            randomQueryGenerator = new Random(seedForQuery);
        }
        return queryRangeMin + randomQueryGenerator.nextInt(
                queryRangeMax - queryRangeMin);
    }

    private void createContentLadder() {
        contentLadder = new int[ZIPF_RANGE];
        int total = 0;
        double topValue = ZIPF_RANGE * TOP_RATIO;

        for (int i = 0; i < ZIPF_RANGE; i++) {
            if (total < ZIPF_RANGE) {
                total += (int) Math.ceil(topValue
                        / Math.pow(i + 1, ZIPF_S));
                contentLadder[i] = total;
            } else {
                numberOfPopularContent = i;
                for (int j = i; j < ZIPF_RANGE; j++) {
                    contentLadder[j] = total + 1;
                }
                break;
            }
        }

        if (numberOfPopularContent <= 0) {
            numberOfPopularContent = ZIPF_RANGE;
        }
    }

    private void createPopularContent() {
        popularContent = new int[numberOfPopularContent];
        Random generator = new Random(POPULAR_CONTENT_SEED);
        for (int i = 0; i < popularContent.length; i++) {
            popularContent[i] = queryRangeMin + generator.nextInt(
                    queryRangeMax - queryRangeMin);
        }
    }

    private int getRankInContentLadder(int value) {
        for (int i = 0; i < numberOfPopularContent; i++) {
            if (value < contentLadder[i]) {
                return i;
            }
        }
        return -1;
    }

    private CCNRouter requireCCNRouter(DTNHost host) {
        if (!(host.getRouter() instanceof CCNRouter)) {
            throw new IllegalStateException(
                    "CCN_application requires routing.CCNRouter");
        }
        return (CCNRouter) host.getRouter();
    }

    // CHANGED: application role normalization removed; role belongs to CCNRouter.
    @Override
    public Application replicate() {
        return new CCN_application(this);
    }

    public int getGeneratedCount() {
        return generatedAttemptCount;
    }

    public ArrayList<String> getPendingContentNames() {
        return new ArrayList<String>(
                pendingRequestIdByContent.keySet());
    }

    public Map<String, String> getPendingRequests() {
        return new HashMap<String, String>(
                pendingRequestIdByContent);
    }
}