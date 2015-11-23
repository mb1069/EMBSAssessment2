//line 1 "M:/EMBS/EMBS_assessment2/part2/src/embs/Broadcast.java"
package embs;

/**
 * Represents a scheduled broadcast
 */
public class Broadcast{
    private long broadcastTime; // broadcastTime: time at which to broadcast
    private long deadline; // deadline: time at which

    public Broadcast(long broadcastTime, long cutoffTime) {
        this.broadcastTime = broadcastTime;
        this.deadline = cutoffTime;
    }

    public long getBroadcastTime() {
        return broadcastTime;
    }
    public void setBroadcastTime(long broadcastTime) {
        this.broadcastTime = broadcastTime;
    }

    public long getDeadline() {
        return deadline;
    }
    public void setDeadline(long deadline) {
        this.deadline = deadline;
    }
}
