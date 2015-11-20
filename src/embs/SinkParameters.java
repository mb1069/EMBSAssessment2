package embs;

public class SinkParameters {
    private byte channel;
    private byte panid;
    private byte address;
    
    private int N;
    private int T;
    
    public SinkParameters(int channel, int panid, int address){
    	this.channel = (byte) channel;
    	this.panid = (byte) panid;
    	this.address = (byte) address;
    }

	public void setN(int n) {this.N = n;}
	public int getT() {return T;}
	public void setT(int t) {this.T = t;}
	public int getN() {return N;}
	public byte getChannel() {return channel;}
	public byte getPanid() {return panid;}
	public byte getAddress() {return address;}
    
    
}
