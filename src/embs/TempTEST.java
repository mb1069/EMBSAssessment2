package embs;

public class TempTEST {

	public static void main(String[] args) {
		CircularBuffer buffer = new CircularBuffer(10);
		System.out.println(buffer.toString());
		Broadcast b = new Broadcast(0);
		for (int x =0; x<10; x++){
			b = new Broadcast(x);
			buffer.insertByTime(b);
			System.out.println(buffer.toString());
		}
		b = new Broadcast(5);
		buffer.insertByTime(b);
		System.out.println(buffer.toString());
		
	}

}
