package embs;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;
//import embs.SinkParameter;


public class SourceNode {

    private static Timer  tsend;
    private static Timer  tstart;
    private static boolean light=false;
    
    private static byte[] xmit;
    private static long   wait;
    static Radio radio = new Radio();

    private static SinkParameters[] sinks = {new SinkParameters(1, 0x11, 0x11), new SinkParameters(2, 0x12, 0x12),new SinkParameters(3, 0x13, 0x13)};
    // settings for SourceNode A
    private static int currentChannel = 0;
    private static byte ownPanId = 0x11;
    private static byte ownShortAddr = 0x1;
    static {
        // Open the default radio
        radio.open(Radio.DID, null, 0, 0);

        // Set channel 
        radio.setChannel(sinks[currentChannel].getChannel());
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
        Util.set16le(xmit, 9, ownShortAddr); // own short address 

		// register delegate for received frames
        radio.setRxHandler(new DevCallback(null){
                public int invoke (int flags, byte[] data, int len, int info, long time) {
                    return  SourceNode.onReceive(flags, data, len, info, time);
                }
            });

        radio.startRx(Device.TIMED, Time.toTickSpan(Time.MILLISECS, 5), Time.currentTicks()+0x7FFFFFFF);
    }

    // Called when a frame is received or at the end of a reception period 
    private static int onReceive (int flags, byte[] data, int len, int info, long time) {
        if (data == null) { // marks end of reception period
            // turn green LED off 
	        LED.setState((byte)1, (byte)0);
	        
	        //set alarm to restart protocol
	    	tstart.setAlarmBySpan(10*wait);
                    
            return 0;
        }


		// frame received, so blink red LED and log its payload
        toggleLed(currentChannel);
//        int nextChannel = (currentChannel++) % 3;
//        setChannel(currentChannel);

		Logger.appendByte(data[11]);
        Logger.flush(Mote.WARN);
        return 0;
        
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
    	
    	SinkParameters sp = sinks[channel];
    	radio.stopRx();
        radio.setChannel(sinks[channel].getChannel());
        radio.setPanId(sinks[channel].getPanid(), true);
        radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
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
