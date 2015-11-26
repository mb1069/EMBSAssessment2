package embs;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;

/**
 * Exam candidate number: Y0076159
 */
public class SourceNode {


	// Timers to schedule broadcast callbacks
	static Timer  timer0; // Timer for sink 0
	static Timer  timer1; // Timer for sink 1
	static Timer  timer2; // Timer for sink 2

	static byte[] xmit; // Xmit: array of bytes send during transmission
	static Radio radio = new Radio(); // Radio: reference to radio used to transmit and receive from sinks

	// Sinks: array of SinkParameter objects used to record information about each individual sink
	private static SinkParameters[] sinks = {
		new SinkParameters((byte) 0,(byte)  0x11,(byte)  0x11), 
		new SinkParameters((byte) 1, (byte) 0x12, (byte) 0x12),
		new SinkParameters((byte) 2, (byte) 0x13, (byte) 0x13)};


	static int currentSinkIndex = 1; // Current Sink Index: used to set initial context of mote (Channel to listen to and SinkParameter object to record data into)


	static byte YELLOW_LED = (byte) 0; // Yellow LED: constant for easier reference to LED 
	static byte GREEN_LED = (byte) 1; // Green LED: constant for easier reference to LED 
	static byte RED_LED = (byte) 2; // Red LED: constant for easier reference to LED 

	static int N_MIN = 2; // N_Min: constant reference to minimum number of beacons per cycle from a sink
	static int N_MAX = 10; // N_Max: constant reference to maximum number of beacons per cycle from a sink
	static long T_MIN = 250; // T_Min: constant reference to minimum time interval (in milliseconds) between beacons from the same sink
	static long T_MAX = 1500; // T_Min: constant reference to maximum time interval (in milliseconds) between beacons from the same sink

	static int[] sendPerSink = new int[]{0,0,0}; // Send per sink: used to record the number of transmissions to each sink

	static int previousSinkIndex; // Previous sink index: used to record previous context when executing a call back to transmit to a sink
	static long ERROR_MARGIN = 3; // Error margin: permissible variation in results of time calculations, used to account for variations in performance of the mote's processing capabilities
	static long lastChannelSwitch = 0; // Last channel switch: used to record the time of the most recent channel switch (in milliseconds since start of execution)


	static {
		// Open the default radio
		radio.open(Radio.DID, null, 0, 0);

		// Initialise all LED's to on state to signal correct load
		LED.setState((byte) YELLOW_LED, (byte) 1);
		LED.setState((byte) GREEN_LED, (byte) 1);
		LED.setState((byte) RED_LED, (byte) 1);

		// Initialise timer for sink index 0
		timer0 = new Timer();
		timer0.setParam((byte) 0); // Set param to sink index in sinks, used to carry forward context into callback
		timer0.setCallback(new TimerEvent(null){
			public void invoke(byte param, long time){
				SourceNode.broadcastToSink(param, time);
			}
		});

		// Initialise timer for sink index 1
		timer1 = new Timer();
		timer1.setParam((byte) 1); // Set param to sink index in sinks, used to carry forward context into callback
		timer1.setCallback(new TimerEvent(null){
			public void invoke(byte param, long time){
				SourceNode.broadcastToSink(param, time);
			}
		});

		// Initialise timer for sink index 2
		timer2 = new Timer();
		timer2.setParam((byte) 2); // Set param to sink index in sinks, used to carry forward context into callback
		timer2.setCallback(new TimerEvent(null){
			public void invoke(byte param, long time){
				SourceNode.broadcastToSink(param, time);
			}
		});


		radio.setChannel((byte) sinks[currentSinkIndex].getChannel()); // Set channel 

		// Set the PAN ID and the short address
		radio.setPanId(sinks[currentSinkIndex].getPanid(), true);
		radio.setShortAddr(sinks[currentSinkIndex].getAddress());

		// Initialise xmit with parameters of local radio
		xmit = new byte[12];
		xmit[0] = Radio.FCF_BEACON;
		xmit[1] = Radio.FCA_SRC_SADDR|Radio.FCA_DST_SADDR;
		Util.set16le(xmit, 9, sinks[currentSinkIndex].getAddress()); // own short address 


		// register callback for received beacons
		radio.setRxHandler(new DevCallback(null){
			public int invoke (int flags, byte[] data, int len, int info, long time) {
				return  SourceNode.onReceive(flags, data, len, info, time);
			}
		});

		// register callback for all transmissions to sinks
		radio.setTxHandler(new DevCallback(null){
			public int invoke(int flags, byte[] data, int len, int info, long time) {
				return SourceNode.onTransmit(flags, data, len, info, time);
			}
		});

		// begin listening
		radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
	}

	// Called when a frame is received or at the end of a reception period 
	private static int onReceive (int flags, byte[] data, int len, int info, long time) {

		if (data == null) { // marks end of reception period
			return 0;
		}

		int n = data[11]; // Retrieve payload from beacon


		long currentTime = Time.currentTime(Time.MILLISECS); // Get current time in milliseconds

		//Case where first beacon received is n=1 and no previous known beacons, switch channel
		if (sinks[currentSinkIndex].getBeaconN()==-1 && n==1){
			sinks[currentSinkIndex].setNextBeaconTime(currentTime + (11*T_MIN));
			setChannel(pickNextSink(currentSinkIndex));
			startListening();
			return 0;
		}

		// If no broadcast scheduled for this sink
		if (!sinks[currentSinkIndex].getBroadcastSet()){
			//If T is known for sink
			if (sinks[currentSinkIndex].getT()!=-1){

				// Calculate broadcast for current cycle
				createNextBroadcast(n, currentTime, currentSinkIndex, sinks[currentSinkIndex].getT(), currentTime);
				setChannel(pickNextSink(currentSinkIndex)); // Pick next most appropriate sink to listen to, change context
				startListening(); // and begin listening

			} else {
				// If T unknown, attempt to calculate using current sinks
				if (sinks[currentSinkIndex].getBeaconT()!=-1 && sinks[currentSinkIndex].getBeaconN()!=-1){

					int diffN = getDiffN(sinks[currentSinkIndex].getBeaconN(), n);
					long diffT = getDiffT(sinks[currentSinkIndex].getBeaconT(), currentTime);
					// If sinks are from same cycle and computed value are valid

					if (diffN>0 && diffT-ERROR_MARGIN <=(T_MAX*diffN)  && isValidPeriod(diffT/diffN)){

						long t = diffT/diffN;
						sinks[currentSinkIndex].setT(t); // Store interval
						createNextBroadcast(n, currentTime, currentSinkIndex, t, currentTime); // create broadcast for current cycle
						setChannel(pickNextSink(currentSinkIndex)); // Pick next most appropriate sink to listen to, change context
						startListening(); // and begin listening
					}
				}
			}
			// Record latest beacon for future reference
			sinks[currentSinkIndex].setBeaconN(n);
			sinks[currentSinkIndex].setBeaconT(currentTime);
		} else {
			// Record latest beacon for future reference
			sinks[currentSinkIndex].setBeaconN(n);
			sinks[currentSinkIndex].setBeaconT(currentTime);
			// If broadcast already scheduled to this sink, start listening to another channel
			setChannel(pickNextSink(currentSinkIndex));
			startListening();
		}
		return 0;
	}

	/**
	 * Validate if calculated period is permissible given constants
	 * @param period: calculated period to be verified
	 * @return boolean True if period is valid, false otherwise
	 */
	protected static boolean isValidPeriod(long period){
		return T_MIN-ERROR_MARGIN<=period && period <= T_MAX+ERROR_MARGIN;
	}

	/**
	 * Send message to sink
	 * @param sinkIndex: index of sink in sinks
	 * @param time: current time
	 */
	protected static void broadcastToSink(byte sinkIndex, long time){
		if (radio.getState()==Device.S_RXEN){ // Stop receiving if necessary
			radio.stopRx();
		}
		previousSinkIndex = (int) currentSinkIndex; // Record context for possible channel changing callback following the transmission

		// Set context for sent packet
		Util.set16le(xmit, 3, sinks[(int) sinkIndex].getPanid()); // set destination PAN id
		Util.set16le(xmit, 5, 0xFFFF); // set broadcast address 
		Util.set16le(xmit, 7, sinks[(int) sinkIndex].getPanid()); // set own PAN address 

		setChannel((int) sinkIndex); // Set channel to match targeted sink's channel
		sendPerSink[sinkIndex]++; // Increment num sent at equivalent index of sendPerSink
		long sinkDelay = sinks[sinkIndex].getT()/2; // Add delay to target center of receive period

		radio.transmit(Device.TIMED, xmit, 0, 12, Time.currentTicks()+Time.toTickSpan(Time.MILLISECS, sinkDelay)); // Transmit to sink at delay
	}

	/**
	 * Callback for post-transmission operations
	 */
	private static int onTransmit(int flags, byte[] data, int len, int info, long time) {
		sinks[currentSinkIndex].setNextBeaconTime(Time.currentTime(Time.MILLISECS)+(sinks[currentSinkIndex].getT()*(11))); // Record predicted time of earliest beacon of next cycle
		sinks[currentSinkIndex].setBroadcastSet(false); // Reset sink state
		// If previous channel has no known T (and therefore no broadcasts), return to it
		if (sinks[previousSinkIndex].getT()==-1){
			setChannel(previousSinkIndex);
		} else {
			// Else pick channel using standard algorithm
			setChannel(pickNextSink(currentSinkIndex));
		}
		startListening(); // Restart radio listening
		Logger.appendString(csr.s2b("Broadcast results: "));
		Logger.appendInt(sendPerSink[0]);
		Logger.appendString(csr.s2b("/"));
		Logger.appendInt(sendPerSink[1]);
		Logger.appendString(csr.s2b("/"));
		Logger.appendInt(sendPerSink[2]);
		Logger.flush(Mote.WARN);
		return 0;
	}

	/**
	 * Pick the sink with the earliest next beacon
	 * @param currSinkIndex: current sink index to avoid switching channel to same sink
	 * @return new sink index
	 */
	private static int pickNextSink(int currSinkIndex){
		int x = 0;
		long nextBeaconTime = -1;
		int nextBeaconSinkIndex = -1;
		for (x=0; x<sinks.length;x++){
			if (x!=currSinkIndex){
				if (nextBeaconTime==-1 | sinks[x].getNextBeaconTime()<nextBeaconTime){ // Select sink with soonest next beacon
					nextBeaconTime = sinks[x].getNextBeaconTime();
					nextBeaconSinkIndex = x;
				}
			}
		}
		if (nextBeaconSinkIndex==-1){ // if no intelligent choice available, increment to next sink using circular buffer concept
			return (currSinkIndex+1) % (sinks.length);
		} else {
			return nextBeaconSinkIndex;
		}
	}


	/**
	 * Set radio parameters to appropriate channel and panid for given sink Index
	 * @param sinkIndex: index of sink in sinks array to retrieve radio configuration from
	 */
	private static void setChannel(int sinkIndex){

		if (radio.getState()==Device.S_RXEN){ // Stop receiving if necessary
			radio.stopRx();
		}
		radio.setChannel((byte) sinks[sinkIndex].getChannel()); // set channel
		radio.setPanId(sinks[sinkIndex].getPanid(), true); // set destination pan id

		currentSinkIndex = sinkIndex; // Change 
		setLEDListening((byte) sinkIndex); // Set LED to indicate context of the SourceNode using sinkIndex
	}

	/**
	 * Start listening on current channel
	 */
	private static void startListening(){
		radio.startRx(Device.TIMED, Time.currentTicks()+Time.toTickSpan(Time.MILLISECS, 10), Time.currentTicks()+0x7FFFFFFF);
	}

	/**
	 * Calculate broadcast time and schedule callback to broadcast
	 * @param beaconN: latest beacon's payload value
	 * @param beaconT: absolute time of arrival (since start of execution, in milliseconds) of latest beacon
	 * @param sinkIndex: index of sink (in sinks array) for which to schedule broadcast
	 * @param t: interval between beacon arrivals (in milliseconds) of channel
	 * @param currentTimeMS: current time in millisecs
	 */
	private static void createNextBroadcast(int beaconN, long beaconT, int sinkIndex, long t, long currentTime) {
		long broadcastTimeByMSSpan = (t  * beaconN); // calculate current cycle's broadcast time
		setupBroadcastCallback(broadcastTimeByMSSpan, sinkIndex); // setup broadcast and callback
		sinks[currentSinkIndex].setBroadcastSet(true); // set to avoid creating multiple callbacks for the same receive period
	}

	/**
	 * Create callback on appropriate timer for broadcast
	 * @param sinkIndex: targetted sink
	 * @param broadcastTime: relative time in milliseconds at which to broadcast time (relative to currentTime)
	 */
	private static void setupBroadcastCallback(long broadcastTime, int sinkIndex){
		switch (sinkIndex){ // Switch to schedule on appropriate timer
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

	/**
	 * Calculate difference in value of beacon payloads
	 * @param beacon1N: earliest received beacon
	 * @param beacon2N: beacon received after beacon1
	 * @return: difference in payload value
	 */
	private static int getDiffN(int beacon1N, int beacon2N) {
		return beacon1N - beacon2N;
	}

	/**
	 * Calculate difference in value of beacon payloads
	 * @param beacon1T: earliest received beacon's arrival time
	 * @param beacon2T: beacon's (received after beacon1) arrival time
	 * @return: difference in payload value
	 */
	private static long getDiffT(long beacon1T, long beacon2T) {
		long diffT = beacon2T-beacon1T;
		return diffT;
	}

	/**
	 * Turn on only selected LED, turn all others off
	 * @param led: index of LED to turn on
	 */
	private static void setLEDListening(byte led){
		for (byte b: new byte[]{YELLOW_LED, RED_LED, GREEN_LED}){
			if (b==led){
				LED.setState(b, (byte) 1);
			} else {
				LED.setState(b, (byte) 0);
			}
		}
	}

}
