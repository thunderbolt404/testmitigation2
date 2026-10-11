
package report;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import core.DTNHost;
import core.SimScenario;
import routing.CCNRouter;

/**
 * Reports the observations collected by attacker-mode CCN routers.
 */
public class CCNAttackTrackingReport extends Report {

    private int attackerCount;
    private int trackedContentCount;
    private int consumerObservationCount;
    private int producerObservationCount;
    private int historyObservationCount;

    @Override
    public void done() {
        StringBuilder report = new StringBuilder();

        attackerCount = 0;
        trackedContentCount = 0;
        consumerObservationCount = 0;
        producerObservationCount = 0;
        historyObservationCount = 0;

        report.append("CCN Stage 1 Attacker Tracking Report")
              .append("\nscenario: ").append(getScenarioName())
              .append("\nsim_time: ").append(format(getSimTime()));

        for (DTNHost host :
                SimScenario.getInstance().getWorld().getHosts()) {

            if (!(host.getRouter() instanceof CCNRouter)) {
                continue;
            }

            CCNRouter router = (CCNRouter) host.getRouter();

            if (router.getNodeMode() != CCNRouter.MODE_ATTACKER) {
                continue;
            }

            attackerCount++;

            Map<String, List<String>> consumers =
                    router.getTrackedConsumers();
            Map<String, List<String>> producers =
                    router.getTrackedProducerCandidates();
            Map<String, List<String>> history =
                    router.getTrackingHistory();

            report.append("\n\n----------------------------------------")
                  .append("\nattacker: ").append(host)
                  .append("\nnode_mode: ")
                  .append(router.getNodeMode());

            report.append("\n\ntracked_consumers:");
            appendTrackingMap(report, consumers);

            report.append("\n\ntracked_producer_candidates:");
            appendTrackingMap(report, producers);

            report.append("\n\ntracking_history:");
            appendTrackingMap(report, history);

            consumerObservationCount += countObservations(consumers);
            producerObservationCount += countObservations(producers);
            historyObservationCount += countObservations(history);

            trackedContentCount += countUniqueKeys(consumers, producers);
        }

        report.append("\n\n========== SUMMARY ==========")
              .append("\nattacker_count: ").append(attackerCount)
              .append("\ntracked_content_count: ")
              .append(trackedContentCount)
              .append("\nconsumer_observation_count: ")
              .append(consumerObservationCount)
              .append("\nproducer_observation_count: ")
              .append(producerObservationCount)
              .append("\ntracking_history_entry_count: ")
              .append(historyObservationCount)
              .append("\n");

        write(report.toString());
        super.done();
    }

    private void appendTrackingMap(
            StringBuilder report,
            Map<String, List<String>> trackingMap) {

        if (trackingMap == null || trackingMap.isEmpty()) {
            report.append("\n  (no observations)");
            return;
        }

        for (Entry<String, List<String>> entry :
                trackingMap.entrySet()) {

            report.append("\n  content: ")
                  .append(entry.getKey());

            List<String> observations = entry.getValue();

            if (observations == null || observations.isEmpty()) {
                report.append("\n    (no entries)");
                continue;
            }

            for (String observation : observations) {
                report.append("\n    - ").append(observation);
            }
        }
    }

    private int countObservations(
            Map<String, List<String>> trackingMap) {

        int count = 0;

        if (trackingMap == null) {
            return count;
        }

        for (List<String> observations : trackingMap.values()) {
            if (observations != null) {
                count += observations.size();
            }
        }

        return count;
    }

    private int countUniqueKeys(
            Map<String, List<String>> first,
            Map<String, List<String>> second) {

        java.util.Set<String> keys =
                new java.util.HashSet<String>();

        if (first != null) {
            keys.addAll(first.keySet());
        }

        if (second != null) {
            keys.addAll(second.keySet());
        }

        return keys.size();
    }
}
