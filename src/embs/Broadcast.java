package embs;

/**
 * Represents a scheduled broadcast
 */
public class Broadcast implements Comparable{
    private int channel; // channel: channel on which to send a packet
    private long broadcastTime; // broadcastTime: time at which to broadcast
    private long deadline; // deadline: time at which

    public Broadcast(int channel, long broadcastTime, long cutoffTime) {
        this.channel = channel;
        this.broadcastTime = broadcastTime;
        this.deadline = cutoffTime;
    }

    public int getChannel() {
        return channel;
    }
    public void setChannel(int channel) {
        this.channel = channel;
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

    @Override
    public int compareTo(Object o) {
        Broadcast b = (Broadcast) o;
        return (int) Math.ceil(b.deadline -b.deadline);
    }
}
