//line 1 "M:/EMBS/EMBS_assessment2/part2/src/embs/SourceNode.java"
package embs;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;
//import embs.SinkParameter;


public class SourceNode {

	static Timer  timer0;
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
	static int currentSinkIndex = 0;
	static byte ownPanId = 0x11;
	static byte ownShortAddr = 0x1;


	static byte YELLOW_LED = (byte) 0;
	static byte GREEN_LED = (byte) 1;
	static byte RED_LED = (byte) 2;

	static int N_MIN = 2;
	static int N_MAX = 10;
	static long T_MIN = 250;
	static long T_MAX = 1500;
	

//	static ChannelSwitch[] channelSwitches = new ChannelSwitch[3];
	static Broadcast[] broadcasts = new Broadcast[3];

	static byte previousChannel;
	static boolean broadcastSet = false;
	static long TIME_ADJUSTMENT = 3;
	static long lastChannelSwitch = 0;
	
	
	static {
		// Open the default radio
		radio.open(Radio.DID, null, 0, 0);

		LED.setState((byte) 0, (byte) 1);
		LED.setState((byte) 1, (byte) 1);
		LED.setState((byte) 2, (byte) 1);

		timer0 = new Timer();
		timer0.setParam((byte) sinks[0].getChannel());
		timer0.setCallback(new TimerEvent(null){
			public void invoke(byte param, long time){
				SourceNode.broadcastToSink(param, time);
			}
		});
		
		timer1 = new Timer();
		timer1.setParam((byte) sinks[1].getChannel());
		timer1.setCallback(new TimerEvent(null){
			public void invoke(byte param, long time){
				SourceNode.broadcastToSink(param, time);
			}
		});
		
		timer2 = new Timer();
		timer2.setParam((byte) sinks[2].getChannel());
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
		radio.setRxMode(Radio.RXMODE_PROMISCUOUS);
		radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
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
		
		if (currentTime-lastChannelSwitch>T_MIN){
			long beaconT = sinks[currentSinkIndex].getBeaconT();
			if (beaconT==-1 || (beaconT!=-1 && (currentTime-beaconT>T_MIN))){
				sinks[currentSinkIndex].setN(n);
			}
		}

		//If T is known for sink
		if (sinks[currentSinkIndex].getT()!=-1){
			createNextBroadcast(n, currentTime, currentSinkIndex, sinks[currentSinkIndex].getT(), currentTime);
		} else {
			if (sinks[currentSinkIndex].getBeaconT()!=-1 && sinks[currentSinkIndex].getBeaconN()!=-1 &&!broadcastSet){
				broadcastSet  = true;
				int diffN = getDiffN(sinks[currentSinkIndex].getBeaconN(), n);
				long diffT = getDiffT(sinks[currentSinkIndex].getBeaconT(), currentTime);
				if (diffN>0 && diffT-TIME_ADJUSTMENT <=(T_MAX*diffN)){
					long t = diffT/diffN;
					sinks[currentSinkIndex].setT(t);
					Logger.appendString(csr.s2b("Calculated T: "));
					Logger.appendLong(t);
					Logger.flush(Mote.WARN);
					createNextBroadcast(sinks[currentSinkIndex].getBeaconN(), sinks[currentSinkIndex].getBeaconT(), currentSinkIndex, t, currentTime);
				}
			}
		}
		sinks[currentSinkIndex].setBeaconN(n);
		sinks[currentSinkIndex].setBeaconT(currentTime);
		Logger.appendString(csr.s2b("Current Beacon: "));
		Logger.appendByte(data[11]);
		Logger.flush(Mote.WARN);
		toggleLed(2);
		return 0;
	}


	protected static void broadcastToSink(byte channelNum, long time){
		
		toggleLed(currentSinkIndex);
		Logger.appendString(csr.s2b("BROADCASTING!"));
		Logger.flush(Mote.WARN);
		
		radio.stopRx();
		previousChannel = radio.getChannel();
		radio.setChannel(channelNum);
		radio.transmit(Device.ASAP|Radio.TXMODE_POWER_MAX, xmit, 0, 12, 0);
		Logger.appendString(csr.s2b("Transmitted."));
		Logger.flush(Mote.INFO);
	}

	private static int onTransmit(int flags, byte[] data, int len, int info, long time) {
		byte channel = radio.getChannel();
		SinkParameters currentSink = null;
		for (SinkParameters s: sinks){
			if (channel == s.getChannel()){
				currentSink = s;
				break;
			}
		}
		if (currentSink.getN()!=-1){
			//TODO schedule channel switch callback AND broadcast
	
		} else {
			//TODO schedule listen event
		}
		broadcastSet = false;
		
		setChannel(previousChannel);
		return 0;
	}
	
	private static byte pickNextChannel(){
		if (currentSinkIndex==0){
			return sinks[1].getChannel();
		} else {
			return sinks[0].getChannel();
		}
	}
	
	
	private static void setChannel(byte channel){
		if (radio.getState()==Device.S_RXEN){
			radio.stopRx();
		}
		radio.setChannel((byte) channel);
		if (radio.getState()!=Device.S_RXEN){
			radio.startRx(Device.TIMED, Time.currentTicks()+Time.toTickSpan(Time.MILLISECS, 10), Time.currentTicks()+0x7FFFFFFF);
		}
	}

	private static void createNextBroadcast(int beaconN, long beaconT, int channel, long t, long currentTimeMS) {
		long broadcastTime = (t  * beaconN) + currentTimeMS;
		long deadline = broadcastTime + T_MIN;
		setupBroadcastAndCallBack(broadcastTime + (T_MIN/2), deadline, channel, currentTimeMS);
		setChannel(pickNextChannel());
	}

	private static void setupBroadcastAndCallBack(long broadcastTime, long deadline, int sinkIndex, long currentTimeMS){
		LED.setState((byte) currentSinkIndex, (byte) 0);
//		ChannelSwitch cs = new ChannelSwitch(broadcastTime, sinks[sinkIndex].getChannel());
//		for (ChannelSwitch c: channelSwitches){
//			if (c!=null && c.getTime()==broadcastTime){
//				return;
//			}
//		}
//		channelSwitches = insertChannelSwitchInBuffer(channelSwitches, cs);
		
		Logger.appendString(csr.s2b("PREPARING BROADCAST! for time: "));
		Logger.appendLong(broadcastTime);
		Logger.flush(Mote.WARN);
		
		switch (sinkIndex){
		case 0:
			timer0.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, broadcastTime-currentTimeMS));
			break;
		case 1:
			timer1.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, broadcastTime-currentTimeMS));
			break;
		case 2:
			timer2.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, broadcastTime-currentTimeMS));
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
		long diffT = beacon1T-beacon2T;
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
				LED.setState(b, (byte) 0);
			} else {
				LED.setState(b, (byte) 1);
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
