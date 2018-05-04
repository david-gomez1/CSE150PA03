package nachos.threads;

import nachos.machine.*;


/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		
	}

	/**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
	public void speak(int word) {
		lock.acquire();
		while (ASpeaker != 0) {
			WSpeaker++;
			speakerCondition.sleep();
			WSpeaker--;
		}

		ASpeaker++;

		this.word = word;

		if (AListener != 0)
			returnCondition.wake();
		else {
			if (WListener != 0)
				listenerCondition.wake();
			returnCondition.sleep();

			ASpeaker--;
			AListener--;

			if (WSpeaker != 0)
				speakerCondition.wake();
		}

		lock.release();
	}

	/**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
	public int listen() {
		lock.acquire();

		while (AListener != 0) {
			WListener++;
			listenerCondition.sleep();
			WListener--;
		}

		AListener++;

		if (ASpeaker != 0)
			returnCondition.wake();
		else {
			if (WSpeaker != 0)
				speakerCondition.wake();
			returnCondition.sleep();

			AListener--;
			ASpeaker--;

			if (WListener != 0)
				listenerCondition.wake();
		}
		int word = this.word;

		lock.release();

		return word;
	}

	private static class Speaker implements Runnable {
		Communicator com;
		int index;
		int message;
		Speaker(int _index, Communicator _com, int _message) {
			index = _index;
			com = _com;
			message = _message;
		}
		public void run() {
			System.out.println("Speaker " + index + " starts speaking");
			com.speak(message);
			System.out.println("Speaker " + index + " speaks "+ message);
			System.out.println("Speaker " + index + " ends speaking");
		}
	}
	private static class Listener implements Runnable {
		int index;
		Communicator com;
		Listener(int _index, Communicator _com) {
			index = _index;
			com = _com;
		}
		public void run() {
			System.out.println("Listener " + index + " starts listening");
			int temp = com.listen();
			System.out.println("Listener " + index + " hears " + temp + " from speaker "+ temp);
			System.out.println("Listener " + index + " ends listening");
		}
	}
	public static void selfTest() {
		Communicator com = new Communicator();
		for(int i = 1; i < 20; i++){
			//test with a range of listeners
			(new KThread(new Listener(i, com))).fork();
		}
		for(int i = 20; i > 0; i--){
			//test with a range of speakers
			(new KThread(new Speaker(i, com, i))).fork();	
		}
		ThreadedKernel.alarm.waitUntil(10000);
	}
	
	Lock lock = new Lock();
	Condition2 speakerCondition = new Condition2(lock), listenerCondition = new Condition2(lock), returnCondition = new Condition2(lock);
	int ASpeaker = 0, WSpeaker = 0, AListener = 0, WListener = 0;
	int word;
}