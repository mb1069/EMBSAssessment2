package embs;

import com.ibm.saguaro.system.Util;

public class CircularBuffer {
	int start;
	int end;
	Broadcast[] data;
	int maxIndex;
	
	public CircularBuffer(int size){
		this.data = new Broadcast[size];
		this.maxIndex = size-1;
	}
	
	public void insertByTime(Broadcast b){
		int insertIndex = start;
		while (data[insertIndex]!=null && data[insertIndex].getBroadcastTime()<=b.getBroadcastTime()){
			insertIndex++;
		}
		if (data[insertIndex]!=null){
			int endIndex = insertIndex;
			while (endIndex<maxIndex && data[endIndex]!=null){
				endIndex++;
			}
			shiftDataBy1(insertIndex, endIndex);
		}
		data[insertIndex] = b;
		incrementStop();
	}
	
	public void shiftDataBy1(int startIndex, int endIndex){
		Broadcast[] data2shift = new Broadcast[endIndex-startIndex];
		Util.updatePersistentData(data, startIndex, data2shift, 0, endIndex-startIndex);
		data[startIndex] = null;		
		Util.updatePersistentData(data2shift, 0, data, startIndex+1, maxIndex-(startIndex+1));
		if (data2shift.length>maxIndex-(startIndex+1)){
			Util.updatePersistentData(data2shift, 0, data2shift, 1, 1);
			data[0]= data2shift[data2shift.length-1];
		}
	}
	
	public Broadcast getNext(){
		int index = start;
		incrementStart();
		Broadcast b = data[index];
		data[index] = null;
		return b;
	}
	
	public void incrementStart(){
		this.start = (start+1) % maxIndex;
	}
	
	public void incrementStop(){
		this.end = (end+1) % maxIndex;
	}
	
	@Override
	public String toString(){
		String s = "data: [";
		for (int x=0; x<data.length;x++){
			if (data[x]!=null){
				if (x==data.length-1){
					s+=(data[x].getBroadcastTime());
				} else {
					s+=(data[x].getBroadcastTime()+", ");
				}
			}
		}
		s+= "], length: " + data.length;
		return s;
	}
}
