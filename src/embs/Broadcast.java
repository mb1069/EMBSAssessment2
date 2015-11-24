package embs;

/**
 * Represents a scheduled broadcast
 */
public class Broadcast{
    private long broadcastTime; // broadcastTime: time at which to broadcast

    public Broadcast(long broadcastTime) {
        this.broadcastTime = broadcastTime;
    }

    public long getBroadcastTime() {
        return broadcastTime;
    }
    public void setBroadcastTime(long broadcastTime) {
        this.broadcastTime = broadcastTime;
    }
}
