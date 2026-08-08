package report;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import applications.CCNRequestEvent;
import core.Application;
import core.ApplicationListener;
import core.DTNHost;
import core.SimClock;
import core.SimScenario;
import routing.CCNRouter;
import routing.ccnutil.CCNRouterStats;

/**
 * Combines request-level application events with network-wide forwarding
 * counters from every CCNRouter. Cache, PIT, duplicate and transmission values
 * remain available without an energy model.
 */
public class CCNApplicationReport extends Report
        implements ApplicationListener {

    private static final class RequestRecord {
        private final double sentTime;
        private boolean completed;
        private double receivedTime;
        private String source;

        private RequestRecord(CCNRequestEvent event, DTNHost host) {
            sentTime = event.getCreationTime();
            completed = false;
            receivedTime = -1.0;
            source = CCNRequestEvent.SOURCE_UNKNOWN;
        }

        private double latency() {
            return completed ? receivedTime - sentTime : -1.0;
        }
    }

    private long queryCount;
    private long duplicatedQuery;
    private long responseCount;

    private final Map<String, RequestRecord> requests = new HashMap<String, RequestRecord>();
    private final Set<String> satisfiedRequestIds = new HashSet<String>();

    private double totalResponseLatency;
    private long responseLatencyCount;

    /* Aggregated routing-layer values. */
    private long oppoCacheHit;
    private long oppoCacheMiss;
    private long staticCacheHit;
    private long staticCacheMiss;
    private long dropList;
    private long dropPit;
    private long dropNonce;

    @Override
    public void gotEvent(String event, Object params, Application app,
                         DTNHost host) {
        if ("SentQuery".equals(event)) {
            CCNRequestEvent request = asRequestEvent(params, host, false);
            queryCount++;
            if (!requests.containsKey(request.getRequestId())) {
                requests.put(request.getRequestId(),
                        new RequestRecord(request, host));
            }
            return;
        }

        if ("SentDuplicatedQuery".equals(event)) {
            duplicatedQuery++;
            return;
        }

        if ("OriginalGotResponse".equals(event)) {
            CCNRequestEvent response = asRequestEvent(params, host, true);
            if (satisfiedRequestIds.contains(response.getRequestId())) {
                return;
            }

            RequestRecord record = requests.get(response.getRequestId());
            if (record == null) {
                record = new RequestRecord(new CCNRequestEvent(
                        response.getRequestId(), response.getContentName(),
                        response.getCreationTime()), host);
                requests.put(response.getRequestId(), record);
            }

            record.completed = true;
            record.receivedTime = response.getCompletionTime() >= 0.0
                    ? response.getCompletionTime() : SimClock.getTime();
            record.source = response.getResponseSource();

            double latency = record.latency();
            if (latency >= 0.0) {
                totalResponseLatency += latency;
                responseLatencyCount++;

            }

            responseCount++;
            satisfiedRequestIds.add(response.getRequestId());
         }
    }

    /** Supports both the corrected event object and old String events. */
    private CCNRequestEvent asRequestEvent(Object params, DTNHost host,
                                           boolean completed) {
        if (params instanceof CCNRequestEvent) {
            return (CCNRequestEvent) params;
        }

        String content = params == null ? "unknown"
                : String.valueOf(params);
        String id = "LEGACY-" + host.getAddress() + "-" + content + "-"
                + SimClock.getIntTime() + "-" + requests.size();
        double now = SimClock.getTime();
        return completed
                ? new CCNRequestEvent(id, content, now, now,
                CCNRequestEvent.SOURCE_UNKNOWN)
                : new CCNRequestEvent(id, content, now);
    }

    /** Reads all forwarding-plane counters from every active CCNRouter. */
    private void aggregateRouterStatistics() {
        for (DTNHost host : SimScenario.getInstance().getWorld().getHosts()) {
            if (!(host.getRouter() instanceof CCNRouter)) {
                continue;
            }

            CCNRouterStats routerStats = ((CCNRouter) host.getRouter())
                    .getRouterStats();
            oppoCacheHit +=
                    routerStats.getOpportunisticCacheHitCount();
            oppoCacheMiss +=
                    routerStats.getOpportunisticCacheMissCount();
            staticCacheHit += routerStats.getStaticCacheHitCount();
            staticCacheMiss += routerStats.getStaticCacheMissCount();
            dropPit += routerStats.getPitAggregationCount();
            dropNonce +=
                    routerStats.getDuplicateInterestSuppressedCount();
            dropList += routerStats.getDuplicateDataSuppressedCount();
        }
    }

    @Override
    public void done() {
        aggregateRouterStatistics();

        double averageInterval = responseLatencyCount == 0
                ? 0.0 : totalResponseLatency / responseLatencyCount;

        write("CCN-DTN stats for scenario " + getScenarioName()
                + "\nsim_time: " + format(getSimTime()));

        StringBuilder stats = new StringBuilder();
        stats.append("\noppo_cache_hit: ").append(oppoCacheHit);
        stats.append("\noppo_cache_miss: ").append(oppoCacheMiss);
        stats.append("\ndrop_list:  ").append(dropList);
        stats.append("\ndrop_pit:  ").append(dropPit);
        stats.append("\ndrop_nonce:  ").append(dropNonce);
        stats.append("\nquery_count: ").append(queryCount);
        stats.append("\nduplicated_query: ").append(duplicatedQuery);
        stats.append("\nstatic_cache_hit: ").append(staticCacheHit);
        stats.append("\nstatic_cache_miss: ").append(staticCacheMiss);
        stats.append("\nresponse_count: ").append(responseCount);
        stats.append("\naverage_interval: ")
                .append(String.format("%.2f", averageInterval));
        write(stats.toString());
        super.done();
    }

}