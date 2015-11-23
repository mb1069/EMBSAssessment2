package embs;

public class SinkParameters {
    private byte channel;
    private byte panid;
    private byte address;
    
    private int N = -1;
    private long T = -1;
    private int[] beaconN = new int[3];
    private long[] beaconT = new long[3];
    private int numBeacons = 0;
    
    public SinkParameters(byte channel, byte panid, byte address){
    	this.channel =  channel;
    	this.panid =  panid;
    	this.address =  address;
    }

    public void addBeacon(int n, long t){
    	if (numBeacons<2){
	    	beaconN[numBeacons]=n;
	    	beaconT[numBeacons]=t;
	    	numBeacons++;
    	}
    }
    
	public void setN(int n) {this.N = n;}
	public long getT() {return T;}
	public void setT(long t) {this.T = t;}
	public int getN() {return N;}
	public byte getChannel() {return channel;}
	public byte getPanid() {return panid;}
	public byte getAddress() {return address;}

	public int getNumBeacons() {
		return numBeacons;
	}
	public void setNumBeacons(int numBeacons) {this.numBeacons = numBeacons;

	}

	public int[] getBeaconN() {
		return beaconN;
	}

	public void setBeaconN(int[] beaconN) {

		this.beaconN = beaconN;
	}

	public long[] getBeaconT() {
		return beaconT;
	}

	public void setBeaconT(long[] beaconT) {

		this.beaconT = beaconT;
	}
    
	
}
