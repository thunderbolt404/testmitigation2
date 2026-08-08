package routing.ccnutil;

/** Per-router counters. A report can aggregate these across all hosts. */
public class CCNEnergyMonitor {
    private long interestTx;
    private long interestRx;
    private long dataTx;
    private long dataRx;

    private long staticCacheLookupCount;
    private long staticCacheHitCount;
    private long staticCacheMissCount;
    private long opportunisticCacheLookupCount;
    private long opportunisticCacheHitCount;
    private long opportunisticCacheMissCount;

    private long pitAggregationCount;
    private long duplicateInterestSuppressedCount;
    private long duplicateDataSuppressedCount;

    private final double interestTxCost;
    private final double interestRxCost;
    private final double dataTxCost;
    private final double dataRxCost;

    public CCNEnergyMonitor() {
        this(1.0, 1.0, 1.0, 1.0);
    }

    public CCNEnergyMonitor(double interestTxCost, double interestRxCost,
            double dataTxCost, double dataRxCost) {
        this.interestTxCost = interestTxCost;
        this.interestRxCost = interestRxCost;
        this.dataTxCost = dataTxCost;
        this.dataRxCost = dataRxCost;
    }

    /** Copies cost configuration but resets counters for a new router. */
    public CCNEnergyMonitor(CCNEnergyMonitor prototype) {
        this(prototype.interestTxCost, prototype.interestRxCost,
                prototype.dataTxCost, prototype.dataRxCost);
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

    public void recordStaticCacheLookup(boolean hit) {
        staticCacheLookupCount++;
        if (hit) {
            staticCacheHitCount++;
        } else {
            staticCacheMissCount++;
        }
    }

    public void recordOpportunisticCacheLookup(boolean hit) {
        opportunisticCacheLookupCount++;
        if (hit) {
            opportunisticCacheHitCount++;
        } else {
            opportunisticCacheMissCount++;
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

    public double calculateEnergy() {
        return interestTx * interestTxCost
                + interestRx * interestRxCost
                + dataTx * dataTxCost
                + dataRx * dataRxCost;
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

    public long getStaticCacheLookupCount() {
        return staticCacheLookupCount;
    }

    public long getStaticCacheHitCount() {
        return staticCacheHitCount;
    }

    public long getStaticCacheMissCount() {
        return staticCacheMissCount;
    }

    public long getOpportunisticCacheLookupCount() {
        return opportunisticCacheLookupCount;
    }

    public long getOpportunisticCacheHitCount() {
        return opportunisticCacheHitCount;
    }

    public long getOpportunisticCacheMissCount() {
        return opportunisticCacheMissCount;
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

    public long getCacheHitCount() {
        return staticCacheHitCount + opportunisticCacheHitCount;
    }

    public long getDuplicateSuppressedCount() {
        return pitAggregationCount + duplicateInterestSuppressedCount
                + duplicateDataSuppressedCount;
    }

    public String toReportString() {
        StringBuilder builder = new StringBuilder();
        builder.append("interestTx: ").append(interestTx).append('\n');
        builder.append("interestRx: ").append(interestRx).append('\n');
        builder.append("dataTx: ").append(dataTx).append('\n');
        builder.append("dataRx: ").append(dataRx).append('\n');
        builder.append("staticCacheHit: ")
                .append(staticCacheHitCount).append('\n');
        builder.append("staticCacheMiss: ")
                .append(staticCacheMissCount).append('\n');
        builder.append("opportunisticCacheHit: ")
                .append(opportunisticCacheHitCount).append('\n');
        builder.append("opportunisticCacheMiss: ")
                .append(opportunisticCacheMissCount).append('\n');
        builder.append("pitAggregation: ")
                .append(pitAggregationCount).append('\n');
        builder.append("duplicateInterestSuppressed: ")
                .append(duplicateInterestSuppressedCount).append('\n');
        builder.append("duplicateDataSuppressed: ")
                .append(duplicateDataSuppressedCount).append('\n');
        builder.append("calculatedEnergy: ").append(calculateEnergy());
        return builder.toString();
    }

    public void printEnergyReport() {
        System.out.println(toReportString());
    }
}
