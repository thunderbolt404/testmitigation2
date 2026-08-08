package routing.ccnutil;

/**
 * Stores routing, cache, PIT and duplicate-suppression counters for one
 * CCNRouter instance. This class contains no energy model or cost parameters.
 */
public final class CCNRouterStats {
    private long opportunisticCacheHitCount;
    private long opportunisticCacheMissCount;
    private long staticCacheHitCount;
    private long staticCacheMissCount;
    private long pitAggregationCount;
    private long duplicateInterestSuppressedCount;
    private long duplicateDataSuppressedCount;
    private long interestTx;
    private long interestRx;
    private long dataTx;
    private long dataRx;

    public void recordOpportunisticCacheLookup(boolean hit) {
        if (hit) {
            opportunisticCacheHitCount++;
        } else {
            opportunisticCacheMissCount++;
        }
    }

    public void recordStaticCacheLookup(boolean hit) {
        if (hit) {
            staticCacheHitCount++;
        } else {
            staticCacheMissCount++;
        }
    }

    public void recordPitAggregation() {
        pitAggregationCount++;
    }

    public void recordDuplicateInterestSuppressed() {
        duplicateInterestSuppressedCount++;
    }

    public void recordDuplicateDataSuppressed() {
        duplicateDataSuppressedCount++;
    }

    public void recordInterestTransmission() {
        interestTx++;
    }

    public void recordInterestReception() {
        interestRx++;
    }

    public void recordDataTransmission() {
        dataTx++;
    }

    public void recordDataReception() {
        dataRx++;
    }

    public long getOpportunisticCacheHitCount() {
        return opportunisticCacheHitCount;
    }

    public long getOpportunisticCacheMissCount() {
        return opportunisticCacheMissCount;
    }

    public long getStaticCacheHitCount() {
        return staticCacheHitCount;
    }

    public long getStaticCacheMissCount() {
        return staticCacheMissCount;
    }

    public long getPitAggregationCount() {
        return pitAggregationCount;
    }

    public long getDuplicateInterestSuppressedCount() {
        return duplicateInterestSuppressedCount;
    }

    public long getDuplicateDataSuppressedCount() {
        return duplicateDataSuppressedCount;
    }

    public long getInterestTx() {
        return interestTx;
    }

    public long getInterestRx() {
        return interestRx;
    }

    public long getDataTx() {
        return dataTx;
    }

    public long getDataRx() {
        return dataRx;
    }

    public void printStatistics() {
        System.out.println("Static Cache hits: " + staticCacheHitCount);
        System.out.println("Static Cache misses: " + staticCacheMissCount);
        System.out.println("Opportunistic Cache hits: "
                + opportunisticCacheHitCount);
        System.out.println("Opportunistic Cache misses: "
                + opportunisticCacheMissCount);
        System.out.println("PIT aggregations: " + pitAggregationCount);
        System.out.println("Duplicate Interests suppressed: "
                + duplicateInterestSuppressedCount);
        System.out.println("Duplicate Data suppressed: "
                + duplicateDataSuppressedCount);
        System.out.println("Interest transmissions: " + interestTx);
        System.out.println("Interest receptions: " + interestRx);
        System.out.println("Data transmissions: " + dataTx);
        System.out.println("Data receptions: " + dataRx);
    }
}