package embs;

public class SinkParameters {
    private byte channel;
    private byte panid;
    private byte address;
    
    private int N;
    private int T;
    
    public SinkParameters(byte channel, byte panid, byte address){
    	this.channel =  channel;
    	this.panid =  panid;
    	this.address =  address;
    }

	public void setN(int n) {this.N = n;}
	public int getT() {return T;}
	public void setT(int t) {this.T = t;}
	public int getN() {return N;}
	public byte getChannel() {return channel;}
	public byte getPanid() {return panid;}
	public byte getAddress() {return address;}
    
    
}
