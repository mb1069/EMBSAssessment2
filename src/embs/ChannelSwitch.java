package embs;

/**
 * Class used to represent channel switch events
 */
public class ChannelSwitch {

    // Time: time at which to switch from current channel to channel specified below
    private long time;

    // Channel: channel to begin listening to at time Time
    private byte channel;

    public ChannelSwitch(long time, byte channel) {
        this.time = time;
        this.channel = channel;
    }

    public long getTime() {
        return time;
    }
    public void setTime(long time) {
        this.time = time;
    }

    public void setChannel(byte channel) {
        this.channel = channel;
    }
    public byte getChannel() {
        return channel;
    }
}
