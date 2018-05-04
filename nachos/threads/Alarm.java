package nachos.threads;

import nachos.machine.*;
import java.util.PriorityQueue;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {

	boolean status = Machine.interrupt().disable(); //making operation atomic "unsplittable"
   	while(!waitQueue.isEmpty() && waitQueue.peek().finish < Machine.timer().getTime()) //waking all ready threads
            waitQueue.poll().getThread().ready(); 

    	Machine.interrupt().restore(status);//restore status

    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
	long finish = Machine.timer().getTime() + x; //set time to wake 
	KThread temp = KThread.currentThread();
	boolean status = Machine.interrupt().disable(); //making operation atomic "unsplittable"
	WaitList wait = new WaitList(temp,finish); //create object to store thread to wait and wake time
	waitQueue.add(wait); //add thread to wait to waitQueue
	KThread.sleep(); //sleep thread
	Machine.interrupt().restore(status); //restore status

   }
    private class WaitList implements Comparable<WaitList>{ //helper object containing wake times and threads.
    	KThread thread;
    	long finish;

	public WaitList(KThread t, long f){thread = t; finish = f;}

    	public int compareTo(WaitList temp){ //implementing comparator, prioritize members with a smaller wake time
        	if(finish < temp.finish) 
            		return -1;
        	else if (finish > temp.finish)
            		return 1;
        	else 
            		return 0;

    	}public long finishTime(){return finish;} //functions to access member variables
    	public KThread getThread(){return thread;}

    }

   public PriorityQueue<WaitList> waitQueue = new PriorityQueue<WaitList>();//Priority queue to store and sort threads
   private static boolean test = false;
   public static void selfTest(){
	long start = Machine.timer().getTime();   	
	new Alarm().waitUntil(1000);
	long finish = Machine.timer().getTime();
	Lib.assertTrue(finish - start >= 1000);//basic test to check that it waits
	
	KThread temp = new KThread(new Runnable(){public void run(){new Alarm().waitUntil(1000); test = false;}}).setName("Bool");
	KThread temp1 = new KThread(new Runnable(){public void run(){new Alarm().waitUntil(10); test = true;}}).setName("Bool");//testing whether threads are prioritized by finish time
	temp.fork();
	temp1.fork();
	temp1.join();
	Lib.assertTrue(test); //will only be true if temp1 finishes before temp, checks that priorityqueue sorts correctly
	temp.join();

   }
}