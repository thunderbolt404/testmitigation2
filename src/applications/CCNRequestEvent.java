package applications;

/**
 * Immutable request event. A unique request ID prevents repeated content names
 * from corrupting latency and satisfaction statistics.
 */
public final class CCNRequestEvent {

    public static final String SOURCE_UNKNOWN = "UNKNOWN";

    private final String requestId;
    private final String contentName;
    private final double creationTime;
    private final double completionTime;
    private final String responseSource;

    public CCNRequestEvent(String requestId, String contentName,
            double creationTime) {
        this(requestId, contentName, creationTime, -1.0, SOURCE_UNKNOWN);
    }

    public CCNRequestEvent(String requestId, String contentName,
            double creationTime, double completionTime,
            String responseSource) {
        if (requestId == null || requestId.length() == 0) {
            throw new IllegalArgumentException("requestId must not be empty");
        }
        if (contentName == null || contentName.length() == 0) {
            throw new IllegalArgumentException(
                    "contentName must not be empty");
        }

        this.requestId = requestId;
        this.contentName = contentName;
        this.creationTime = creationTime;
        this.completionTime = completionTime;
        this.responseSource = responseSource == null
                ? SOURCE_UNKNOWN : responseSource;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getContentName() {
        return contentName;
    }

    public double getCreationTime() {
        return creationTime;
    }

    public double getCompletionTime() {
        return completionTime;
    }

    public String getResponseSource() {
        return responseSource;
    }

    public boolean isCompleted() {
        return completionTime >= creationTime;
    }

    public double getLatency() {
        return isCompleted() ? completionTime - creationTime : -1.0;
    }
}
