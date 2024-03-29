//line 1 "M:/EMBS/EMBS_assessment2/part2/src/embs/SourceNode.java"
package embs;

import javax.swing.text.html.CSS;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;
//import embs.SinkParameter;


public class SourceNode {

	static Timer  timer1;
	static Timer  timer2;
	static byte[] xmit;
	static long   wait;
	static Radio radio = new Radio();

	private static SinkParameters[] sinks = {
		new SinkParameters((byte) 11,(byte)  0x11,(byte)  0x11), 
		new SinkParameters((byte) 12, (byte) 0x12, (byte) 0x12),
		new SinkParameters((byte) 13, (byte) 0x13, (byte) 0x13)};
	// settings for SourceNode A
	static int currentChannel = 0;
	static byte ownPanId = 0x11;
	static byte ownShortAddr = 0x1;


	static byte YELLOW_LED = (byte) 0;
	static byte GREEN_LED = (byte) 1;
	static byte RED_LED = (byte) 2;

	static int N_MIN = 2;
	static int N_MAX = 10;
	static long T_MIN = 250;
	static long T_MAX = 1500;
	

	static ChannelSwitch[] channelSwitches = new ChannelSwitch[5];

	static byte previousChannel;
	static boolean broadcastSet = false;
	
	static {
		// Open the default radio
		radio.open(Radio.DID, null, 0, 0);

		LED.setState((byte) 0, (byte) 1);
		LED.setState((byte) 1, (byte) 1);
		LED.setState((byte) 2, (byte) 1);

		timer1 = new Timer();
		timer1.setCallback(new TimerEvent(null){
			public void invoke(byte param, long time){
				SourceNode.broadcastToSink(param, time);
			}
		});
		timer2 = new Timer();
		timer2.setCallback(new TimerEvent(null){
			public void invoke(byte param, long time){
				SourceNode.startListening(param, time);
			}
		});

		// Set channel 
		radio.setChannel((byte) sinks[currentChannel].getChannel());
		// Set the PAN ID and the short address
		radio.setPanId(sinks[currentChannel].getPanid(), true);
		radio.setShortAddr(sinks[currentChannel].getAddress());

		// Prepare beacon frame with source and destination addressing
		xmit = new byte[12];
		xmit[0] = Radio.FCF_BEACON;
		xmit[1] = Radio.FCA_SRC_SADDR|Radio.FCA_DST_SADDR;
		Util.set16le(xmit, 3, sinks[currentChannel].getPanid()); // destination PAN address 
		Util.set16le(xmit, 5, 0xFFFF); // broadcast address 
		Util.set16le(xmit, 7, sinks[currentChannel].getPanid()); // own PAN address 
		Util.set16le(xmit, 9, sinks[currentChannel].getAddress()); // own short address 

		// register delegate for received frames
		radio.setRxHandler(new DevCallback(null){
			public int invoke (int flags, byte[] data, int len, int info, long time) {
				return  SourceNode.onReceive(flags, data, len, info, time);
			}
		});

		radio.setTxHandler(new DevCallback(null){
			public int invoke(int flags, byte[] data, int len, int info, long time) {
				return SourceNode.onTransmit(flags, data, len, info, time);
			}
		});
		radio.setRxMode(Radio.RXMODE_PROMISCUOUS);

		radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
	}

	private static int onTransmit(int flags, byte[] data, int len, int info, long time) {
		Logger.appendString(csr.s2b("Reset channel."));
		Logger.flush(Mote.INFO);
		Logger.appendString(csr.s2b("Set broadcastSet."));
		Logger.flush(Mote.INFO);
		timer2.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, 250)-Time.currentTicks());

		return 0;
	}


	protected static void startListening(byte param, long time) {
		Logger.appendString(csr.s2b("Restarting listening."));
		Logger.flush(Mote.INFO);
		
		broadcastSet = false;
		radio.stopRx();
		radio.setChannel(previousChannel);
		radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
		Logger.appendString(csr.s2b("Turned on radio."));
		Logger.flush(Mote.INFO);
	}


	// Called when a frame is received or at the end of a reception period 
	private static int onReceive (int flags, byte[] data, int len, int info, long time) {
		Logger.appendString(csr.s2b("Received packet"));
		Logger.flush(Mote.INFO);
		if (data == null) { // marks end of reception period
			// turn green LED off 
			LED.setState((byte)1, (byte)0);
			
			return 0;
		}
		int n = data[11];
		long currentTime = Time.currentTime(Time.MILLISECS);
		sinks[currentChannel].addBeacon(n, currentTime);
		int numBeacons = sinks[currentChannel].getNumBeacons();
		if (numBeacons>1 && !broadcastSet){
			broadcastSet  = true;
			int diffN = getDiffN(sinks[currentChannel].getBeaconN(), sinks[currentChannel].getNumBeacons());
			long diffT = getDiffT(sinks[currentChannel].getBeaconT(), sinks[currentChannel].getNumBeacons());
			if (diffN>0 && diffT<=(T_MAX*diffN)){
				long t = diffT/diffN;
				Logger.appendString(csr.s2b("Calculated T: "));
				Logger.appendLong(t);
				Logger.flush(Mote.ERROR);
				createNextBroadcast(sinks[currentChannel].getBeaconN()[numBeacons-1], sinks[currentChannel].getBeaconT()[numBeacons-1], currentChannel, t, currentTime);
			}
		}
		Logger.appendString(csr.s2b("Current Beacon: "));
		Logger.appendByte(data[11]);
		Logger.flush(Mote.WARN);
		// frame received, so blink red LED and log its payload
		toggleLed(currentChannel);
		//        if (n==1){
		//	        int nextChannel = (currentChannel+1) % 3;
		//	        setChannel(nextChannel);
		//        }


		return 0;
	}


	protected static void broadcastToSink(byte param, long time){
		
		LED.setState((byte) 2, (byte) 1);
		Logger.appendString(csr.s2b("BROADCASTING!"));
		Logger.flush(Mote.ERROR);
		
		radio.stopRx();
		toggleLed(2);
		byte channelNum = getChannelSwitch(time, channelSwitches);
		Logger.appendString(csr.s2b("Got channelNum: "));
		Logger.appendByte(channelNum);
		Logger.flush(Mote.INFO);
		previousChannel = radio.getChannel();
		radio.setChannel(channelNum);
		Logger.appendString(csr.s2b("Set channel."));
		Logger.flush(Mote.INFO);
		radio.transmit(Device.ASAP|Radio.TXMODE_POWER_MAX, xmit, 0, 12, 0);
		Logger.appendString(csr.s2b("Transmitted."));
		Logger.flush(Mote.INFO);
		
		Logger.appendString(csr.s2b("Listening."));
		Logger.flush(Mote.INFO);
	}

	protected static byte getChannelSwitch(long time, ChannelSwitch[] css){
		for (ChannelSwitch cs: css){
			if (cs!=null && cs.getTime()==time){
				return cs.getChannel();
			}
		}
		return sinks[currentChannel].getChannel();
	}

	private static long createNextBroadcast(int beaconN, long beaconT, int channel, long t, long currentTime) {
		long broadcastTime = (t  * beaconN) + currentTime;
		long deadline = broadcastTime + T_MIN;
		setupBroadcastAndCallBack(broadcastTime + (T_MIN/2), deadline, channel);
		return broadcastTime;
	}

	private static void setupBroadcastAndCallBack(long broadcastTime, long deadline, int sinkIndex){
		broadcastTime+= T_MIN/2;
		LED.setState((byte) 2, (byte) 0);
		Broadcast b = new Broadcast(broadcastTime, deadline);
		ChannelSwitch cs = new ChannelSwitch(broadcastTime, sinks[sinkIndex].getChannel());
		for (ChannelSwitch c: channelSwitches){
			if (c!=null && c.getTime()==broadcastTime){
				return;
			}
		}
		
		Logger.appendString(csr.s2b("PREPARING BROADCAST! for time: "));
		Logger.appendLong(broadcastTime);
		Logger.flush(Mote.ERROR);
		channelSwitches = insertChannelSwitchInBuffer(channelSwitches, cs);
		timer1.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, broadcastTime)-Time.currentTicks());
	}
	
	
	private static ChannelSwitch[] insertChannelSwitchInBuffer(ChannelSwitch[] css, ChannelSwitch cs){
		// If space available in buffer
		int x;
		for (x=0; x<css.length; x++){
			if (css[x]==null){
				css[x] = cs;
				return css;
			}
		}
		ChannelSwitch[] newCss = new ChannelSwitch[css.length+2];
		for (x=0; x<css.length; x++){
			newCss[x] = css[x];
		}
		newCss[x+1] = cs;
		return newCss;
	}


	private static int getDiffN(int[] beaconN, int numBeacons) {
		return beaconN[numBeacons-2]-beaconN[numBeacons-1];
	}

	private static long getDiffT(long[] beaconT, int numBeacons) {
		long diffT = beaconT[numBeacons-1]-beaconT[numBeacons-2];
		return (diffT < 0) ? -diffT : diffT;
	}

	private static void toggleLed(int led){
		int ledState = LED.getState((byte) led);
		if (ledState==0){
			LED.setState((byte) led, (byte) 1);
		} else {
			LED.setState((byte) led, (byte) 0);
		}
	}


	private static void setChannel(int channel) {
		Logger.appendString(csr.s2b("Changing channel to: "));
		Logger.appendInt(channel);
		Logger.flush(Mote.ERROR);
		SinkParameters sp = sinks[channel];
		radio.stopRx();
		radio.setChannel(sp.getChannel());
		radio.setPanId(sp.getPanid(), true);
		radio.startRx(Device.TIMED, Time.toTickSpan(Time.MILLISECS, 5), Time.currentTicks()+0x7FFFFFFF);
		currentChannel = channel;
	}


	//	// Called on a timer alarm
	//    public static void periodicSend(byte param, long time) {
	//        
	//        if(nc>0){
	//	        // transmit a beacon 
	//    	    radio.transmit(Device.ASAP|Radio.TXMODE_POWER_MAX, xmit, 0, 12, 0);
	//        	// program new alarm
	//        	tsend.setAlarmBySpan(wait);
	//        	nc--;
	//        	xmit[11]--;
	//        }
	//        else{
	//        	//start reception phase
	//	        radio.startRx(Device.ASAP, 0, Time.currentTicks()+wait);
	//	        // turn green LED on 
	//	        LED.setState((byte)1, (byte)1);
	//	        
	//        }
	//        
	//    }
}
