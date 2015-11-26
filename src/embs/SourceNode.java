package embs;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;


public class SourceNode {

	
	// Timers to schedule broadcast callbacks
	static Timer  timer0; // Timer for sink0
	static Timer  timer1; // Timer for sink1
	static Timer  timer2; // Timer for sink 2
	
	static byte[] xmit;
	static long   wait;
	static Radio radio = new Radio();
	
	private static SinkParameters[] sinks = {
		new SinkParameters((byte) 0,(byte)  0x11,(byte)  0x11), 
		new SinkParameters((byte) 1, (byte) 0x12, (byte) 0x12),
		new SinkParameters((byte) 2, (byte) 0x13, (byte) 0x13)};
	
	// settings for initial sink
	static int currentSinkIndex = 0;


	static byte YELLOW_LED = (byte) 0;
	static byte GREEN_LED = (byte) 1;
	static byte RED_LED = (byte) 2;

	static int N_MIN = 2;
	static int N_MAX = 10;
	static long T_MIN = 250;
	static long T_MAX = 1500;
	
	static int[] sendPerSink = new int[]{0,0,0};

	static int previousSinkIndex;
	static long TIME_ADJUSTMENT = 3;
	static long lastChannelSwitch = 0;
	
	
	static {
		// Open the default radio
		radio.open(Radio.DID, null, 0, 0);

		LED.setState((byte) 11, (byte) 1);
		LED.setState((byte) 12, (byte) 1);
		LED.setState((byte) 13, (byte) 1);

		timer0 = new Timer();
		timer0.setParam((byte) 0);
		timer0.setCallback(new TimerEvent(null){
			public void invoke(byte param, long time){
				SourceNode.broadcastToSink(param, time);
			}
		});
		
		timer1 = new Timer();
		timer1.setParam((byte) 1);
		timer1.setCallback(new TimerEvent(null){
			public void invoke(byte param, long time){
				SourceNode.broadcastToSink(param, time);
			}
		});
		
		timer2 = new Timer();
		timer2.setParam((byte) 2);
		timer2.setCallback(new TimerEvent(null){
			public void invoke(byte param, long time){
				SourceNode.broadcastToSink(param, time);
			}
		});

		// Set channel 
		radio.setChannel((byte) sinks[currentSinkIndex].getChannel());
		// Set the PAN ID and the short address
		radio.setPanId(sinks[currentSinkIndex].getPanid(), true);
		radio.setShortAddr(sinks[currentSinkIndex].getAddress());

		// Prepare beacon frame with source and destination addressing
		xmit = new byte[12];
		xmit[0] = Radio.FCF_BEACON;
		xmit[1] = Radio.FCA_SRC_SADDR|Radio.FCA_DST_SADDR;
		Util.set16le(xmit, 3, sinks[currentSinkIndex].getPanid()); // destination PAN address 
		Util.set16le(xmit, 5, 0xFFFF); // broadcast address 
		Util.set16le(xmit, 7, sinks[currentSinkIndex].getPanid()); // own PAN address 
		Util.set16le(xmit, 9, sinks[currentSinkIndex].getAddress()); // own short address 

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

		radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
	}

	// Called when a frame is received or at the end of a reception period 
	private static int onReceive (int flags, byte[] data, int len, int info, long time) {

		if (data == null) { // marks end of reception period
			return 0;
		}
			
		int n = data[11];
		
		if (n>sinks[currentSinkIndex].getMaxObservedN()){
			sinks[currentSinkIndex].setMaxObservedN(n);
		}
		Logger.appendString(csr.s2b("Received packet c:"));
		Logger.appendByte(sinks[currentSinkIndex].getChannel());
		Logger.appendString(csr.s2b(" n:"));
		Logger.appendByte(data[11]);
		Logger.flush(Mote.WARN);
		long currentTime = Time.currentTime(Time.MILLISECS);
		
		//Case where first beacon received is n=1 and no previous known beacons, switch channel
		if (sinks[currentSinkIndex].getBeaconN()==-1 && n==1){
			sinks[currentSinkIndex].setNextBeaconTime(currentTime + (11*T_MIN));
			setChannel(pickNextSink(currentSinkIndex));
			startListening();
			return 0;
		}
		if (!sinks[currentSinkIndex].getBroadcastSet()){
			//If T is known for sink
			if (sinks[currentSinkIndex].getT()!=-1){
				createNextBroadcast(n, currentTime, currentSinkIndex, sinks[currentSinkIndex].getT(), currentTime);
			} else {
				if (sinks[currentSinkIndex].getBeaconT()!=-1 && sinks[currentSinkIndex].getBeaconN()!=-1){
					int diffN = getDiffN(sinks[currentSinkIndex].getBeaconN(), n);
					long diffT = getDiffT(sinks[currentSinkIndex].getBeaconT(), currentTime);
					if (diffN>0 && diffT-TIME_ADJUSTMENT <=(T_MAX*diffN)  && isValidPeriod(diffT/diffN)){
						long t = diffT/diffN;
						sinks[currentSinkIndex].setT(t);
						Logger.appendString(csr.s2b("Calculated T: "));
						Logger.appendLong(t);
						Logger.flush(Mote.WARN);
						createNextBroadcast(n, currentTime, currentSinkIndex, t, currentTime);
					}
				}
			}
		}
		sinks[currentSinkIndex].setBeaconN(n);
		sinks[currentSinkIndex].setBeaconT(currentTime);
		return 0;
	}

	protected static boolean isValidPeriod(long period){
		return T_MIN-TIME_ADJUSTMENT<=period && period <= T_MAX+TIME_ADJUSTMENT;
	}

	protected static void broadcastToSink(byte sinkIndex, long time){
		if (radio.getState()==Device.S_RXEN){
			radio.stopRx();
		}
		Logger.appendString(csr.s2b("BROADCASTING! on channel: "));
		Logger.appendByte(sinks[sinkIndex].getChannel());
		Logger.appendString(csr.s2b("at time: "));
		Logger.appendLong(Time.currentTicks());
		Logger.flush(Mote.WARN);

		previousSinkIndex = (int) currentSinkIndex;
		
		setChannel((int) sinkIndex);
		sendPerSink[sinkIndex]++;
		long sinkDelay = sinks[sinkIndex].getT()/2;
		
		radio.transmit(Device.TIMED, xmit, 0, 12, Time.currentTicks()+Time.toTickSpan(Time.MILLISECS, sinkDelay));
		Logger.appendString(csr.s2b("Finished broadcast."));
		Logger.flush(Mote.INFO);
	}

	private static int onTransmit(int flags, byte[] data, int len, int info, long time) {
		sinks[currentSinkIndex].setNextBeaconTime(Time.currentTime(Time.MILLISECS)+(sinks[currentSinkIndex].getT()*(11+sinks[currentSinkIndex].getMaxObservedN())));

		sinks[currentSinkIndex].setBroadcastSet(false);
		if (sinks[previousSinkIndex].getT()==-1){
			setChannel(previousSinkIndex);
		} else {
			setChannel(pickNextSink(currentSinkIndex));
		}
		startListening();
		Logger.appendString(csr.s2b("Broadcast results: "));
		Logger.appendInt(sendPerSink[0]);
		Logger.appendString(csr.s2b("/"));
		Logger.appendInt(sendPerSink[1]);
		Logger.appendString(csr.s2b("/"));
		Logger.appendInt(sendPerSink[2]);
		Logger.flush(Mote.WARN);
		return 0;
	}
	
	private static int pickNextSink(int currSinkIndex){
		int x = 0;
		long nextBeaconTime = -1;
		int nextBeaconSinkIndex = -1;
		for (x=0; x<sinks.length;x++){
			if (x!=currSinkIndex){
				if (nextBeaconTime==-1 | sinks[x].getNextBeaconTime()<nextBeaconTime){
					nextBeaconTime = sinks[x].getNextBeaconTime();
					nextBeaconSinkIndex = x;
				}
			}
		}
		if (nextBeaconSinkIndex==-1){
			return (currSinkIndex+1) % (sinks.length);
		} else {
			return nextBeaconSinkIndex;
		}
	}
	
	
	private static void setChannel(int sinkIndex){

		if (radio.getState()==Device.S_RXEN){
			radio.stopRx();
		}
		
		Util.set16le(xmit, 3, sinks[sinkIndex].getPanid()); // destination PAN address 
		Util.set16le(xmit, 5, 0xFFFF); // broadcast address 
		Util.set16le(xmit, 7, sinks[sinkIndex].getPanid()); // own PAN address 
		
		radio.setChannel((byte) sinks[sinkIndex].getChannel());
		radio.setPanId(sinks[sinkIndex].getPanid(), true);
//		radio.setShortAddr(sinks[sinkIndex].getAddress());
				
		currentSinkIndex = sinkIndex;
		setLEDListening((byte) sinkIndex);

		Logger.appendString(csr.s2b("Changed channel to: "));
		Logger.appendByte(sinks[sinkIndex].getChannel());
		Logger.appendString(csr.s2b(" at time: "));
		Logger.appendLong(Time.currentTicks());
		Logger.flush(Mote.WARN);
	}

	
	private static void startListening(){
		radio.startRx(Device.TIMED, Time.currentTicks()+Time.toTickSpan(Time.MILLISECS, 10), Time.currentTicks()+0x7FFFFFFF);
	}
	
	private static void createNextBroadcast(int beaconN, long beaconT, int channel, long t, long currentTimeMS) {
		long broadcastTimeByMSSpan = (t  * beaconN);
		long deadline = broadcastTimeByMSSpan + T_MIN;
		setupBroadcastAndCallBack(broadcastTimeByMSSpan, deadline, channel, currentTimeMS);
		sinks[currentSinkIndex].setBroadcastSet(true);
		setChannel(pickNextSink(currentSinkIndex));
		startListening();
	}

	private static void setupBroadcastAndCallBack(long broadcastTime, long deadline, int sinkIndex, long currentTimeMS){
//		ChannelSwitch cs = new ChannelSwitch(broadcastTime, sinks[sinkIndex].getChannel());
//		for (ChannelSwitch c: channelSwitches){
//			if (c!=null && c.getTime()==broadcastTime){
//				return;
//			}
//		}
//		channelSwitches = insertChannelSwitchInBuffer(channelSwitches, cs);
		
		Logger.appendString(csr.s2b("PREPARING BROADCAST! for channel: "));
		Logger.appendInt(sinkIndex);
		Logger.flush(Mote.WARN);
		
		switch (sinkIndex){
		case 0:
			timer0.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, broadcastTime));
			break;
		case 1:
			timer1.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, broadcastTime));
			break;
		case 2:
			timer2.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, broadcastTime));
			break;
		}
	}
	
	
//	private static ChannelSwitch[] insertChannelSwitchInBuffer(ChannelSwitch[] css, ChannelSwitch cs){
//		// If space available in buffer
//		int x;
//		for (x=0; x<css.length; x++){
//			if (css[x]==null){
//				css[x] = cs;
//				return css;
//			}
//		}
//		ChannelSwitch[] newCss = new ChannelSwitch[css.length+2];
//		for (x=0; x<css.length; x++){
//			newCss[x] = css[x];
//		}
//		newCss[x+1] = cs;
//		return newCss;
//	}


	private static int getDiffN(int beacon1N, int beacon2N) {
		return beacon1N - beacon2N;
	}

	private static long getDiffT(long beacon1T, long beacon2T) {
		long diffT = beacon2T-beacon1T;
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

	private static void setLEDListening(byte led){
		for (byte b: new byte[]{YELLOW_LED, RED_LED, GREEN_LED}){
			if (b==led){
				LED.setState(b, (byte) 1);
			} else {
				LED.setState(b, (byte) 0);
			}
		}
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
