package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
	this.conditionLock = conditionLock;
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());

	//conditionLock.release();

	//conditionLock.acquire();
	
		boolean tmp = Machine.interrupt().disable(); //disable interrupts
		KThread currentThread = KThread.currentThread(); //get the current thread
		waitQueue.waitForAccess(currentThread); //wait until current thread is accessible
		conditionLock.release(); //release the condition lock (acting as a semaphore 	release)
		KThread.sleep(); //put the thread to bed
		conditionLock.acquire(); //reacquire the condition lock
		Machine.interrupt().restore(tmp);	//restore interrupts

    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	boolean temp = Machine.interrupt().disable();
	KThread tmpThread = waitQueue.nextThread();
	//boolean tmp;
	if (tmpThread != null){ //if the queue contains threads
		//tmp = Machine.interrupt().disable(); //disable interrupts
		//KThread queued = waitQueue.nextThread(); //get thread from queue
		tmpThread.ready(); //ready the thread 

	}
	Machine.interrupt().restore(temp); //restore interrupts

    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	boolean tmp = Machine.interrupt().disable();
	KThread tmpThread = waitQueue.nextThread();
	
	while (tmpThread != null){
	//while(!waitQueue.isEmpty()){ //while queue is not empty
		//tmp =  //disable interrupts
		//KThread queued = waitQueue.nextThread(); //get thread from queue
		tmpThread.ready(); //ready the thread 
		
		tmpThread = waitQueue.nextThread();
		
	}
	Machine.interrupt().restore(tmp); //restore interrupts

    }

    private Lock conditionLock;
    
    private ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(false);
}
