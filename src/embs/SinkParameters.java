package embs;

import com.ibm.saguaro.system.Timer;

public class SinkParameters {
    private byte channel;
    private byte panid;
    private byte address;
    
    private long T = -1;
    private int beaconN = -1;
    private long beaconT = -1;
    private boolean broadcastSet = false;
	private long nextBeaconTime;
    
    public SinkParameters(byte channel, byte panid, byte address){
    	this.channel =  channel;
    	this.panid =  panid;
    	this.address =  address;
    }

    public boolean getBroadcastSet(){
    	return this.broadcastSet;
    }
    
    public void setBroadcastSet(boolean value){
    	this.broadcastSet = value;
    }
    
    public void addBeacon(int n, long t){
    	this.beaconN = n;
    	this.beaconT = t;
    }
    
	public long getT() {return T;}
	public void setT(long t) {this.T = t;}
	public byte getChannel() {return channel;}
	public byte getPanid() {return panid;}
	public byte getAddress() {return address;}

	public int getBeaconN() {
		return beaconN;
	}
	public void setBeaconN(int beaconN) {
		this.beaconN = beaconN;
	}
	public long getBeaconT() {
		return beaconT;
	}
	public void setBeaconT(long beaconT) {
		this.beaconT = beaconT;
	}

	public void setNextBeaconTime(long l) {
		this.nextBeaconTime = l;
	}
	
	public long getNextBeaconTime(){
		return this.nextBeaconTime;
	}
  
	
}
