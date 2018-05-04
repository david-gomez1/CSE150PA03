package nachos.threads;

import nachos.machine.*;

import java.util.*;

/**
 * A scheduler that chooses threads based on their priorities.
 * <p/>
 * <p/>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 * <p/>
 * <p/>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 * <p/>
 * <p/>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks,` and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }

    /**
     * Allocate a new priority thread queue.
     *
     * @param transferPriority <tt>true</tt> if this queue should
     *                         transfer priority from waiting threads
     *                         to the owning thread.
     * @return a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());

        Lib.assertTrue(priority >= priorityMinimum &&
                priority <= priorityMaximum);

        getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMaximum)
            return false;

        setPriority(thread, priority + 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    public boolean decreasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMinimum)
            return false;

        setPriority(thread, priority - 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param thread the thread whose scheduling state to return.
     * @return the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null)
            thread.schedulingState = new ThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {

        PriorityQueue(boolean transferPriority) {
            this.transferPriority = transferPriority;
            this.threadWait = new LinkedList<ThreadState>();
        }

        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            final ThreadState state = getThreadState(thread);
            this.threadWait.add(state);
            state.waitForAccess(this);
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            final ThreadState state = getThreadState(thread);
            if (this.holdres != null) {
                this.holdres.release(this);
            }
            this.holdres = state;
            state.acquire(this);
        }

        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());

            
            final ThreadState NextT = this.pickNextThread();// chooses next thread

            if (NextT == null) return null;

            this.threadWait.remove(NextT);  // Remove thread thats next in the queue 


            
            this.acquire(NextT.getThread()); // resource is given to 

            return NextT.getThread();		
        }

   
        public ThreadState peekNext() {
            return this.pickNextThread();
        }

 
        protected ThreadState pickNextThread() {  //returns next theard, without modfication of the queue 
            int nextPriority = priorityMinimum;
            ThreadState next = null;
            for (final ThreadState currThread : this.threadWait) {
                int currPriority = currThread.getEffectivePriority();
                if (next == null || (currPriority > nextPriority)) {
                    next = currThread;
                    nextPriority = currPriority;
                }
            }
            return next;
        }

        /**
        * Return the effective priority of the associated thread.
    	 	*
    	 	*	 @return	the effective priority of the associated thread.
    	 	*/
        public int getEffectivePriority() {
            if (!this.transferPriority) {
                return priorityMinimum;
            } else if (this.priChange) {
                // Recalculate effective priorities, do da maths
                this.effPriority = priorityMinimum;
                for (final ThreadState curr : this.threadWait) {
                    this.effPriority = Math.max(this.effPriority, curr.getEffectivePriority());
                }
                this.priChange = false;
            }						
            return effPriority;		
        }

        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());
            for (final ThreadState state : this.threadWait) {
                System.out.println(state.getEffectivePriority());
            }
        }

        private void invalidateCachedPriority() {
            if (!this.transferPriority) return;

            this.priChange = true;

            if (this.holdres != null) {
                holdres.invalidateCachedPriority();
            }
        }

       
        protected final List<ThreadState> threadWait; // waiting threads in a list.
      
        protected ThreadState holdres = null;//thread currently holding resource 
     
        protected int effPriority = priorityMinimum; // seeting effective Pri to pri min
        
        protected boolean priChange = false; // True if the effective priority of this queue has been invalidated.
        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
        /**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param thread the thread this state belongs to.
         */
        public ThreadState(KThread thread) {
            this.thread = thread;

            this.currentResources = new LinkedList<PriorityQueue>();
            this.waitingRes = new LinkedList<PriorityQueue>();

            setPriority(priorityDefault);

        }

        /**
         * Return the priority of the associated thread.
         *
         * @return the priority of the associated thread.
         */
        public int getPriority() {
            return priority;
        }

        /**
         * Return the effective priority of the associated thread.
         *
         * @return the effective priority of the associated thread.
         */
        public int getEffectivePriority() {

            if (this.currentResources.isEmpty()) {
                return this.getPriority();
            } else if (this.priChange) {
                this.effPriority = this.getPriority();
                for (final PriorityQueue pq : this.currentResources) {
                    this.effPriority = Math.max(this.effPriority, pq.getEffectivePriority());
                }
                this.priChange = false;
            }
            return this.effPriority;
        }

        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param priority the new priority.
         */
        public void setPriority(int priority) {
            if (this.priority == priority)
                return;
            this.priority = priority;
            // force priority invalidation
            for (final PriorityQueue priQueue : waitingRes) {
            		priQueue.invalidateCachedPriority();
            }
        }

        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the
         * resource guarded by <tt>waitQueue</tt>. This method is only called
         * if the associated thread cannot immediately obtain access.
         *
         * @param waitQueue the queue that the associated thread is
         *                  now waiting on.
         * @see nachos.threads.ThreadQueue#waitForAccess
         */
        public void waitForAccess(PriorityQueue waitQueue) {	//add to queue if waiting for access to something
        	
            this.waitingRes.add(waitQueue);
            this.currentResources.remove(waitQueue);
            waitQueue.invalidateCachedPriority();  //don't forget to clear
        }

        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see nachos.threads.ThreadQueue#acquire
         * @see nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue waitQueue) {
            this.currentResources.add(waitQueue);		//add if currently using
            
            this.waitingRes.remove(waitQueue);
            this.invalidateCachedPriority();			//clear it up yo
        }

       
        public void release(PriorityQueue waitQueue) {// called when the associated thread has relinquished access 
            this.currentResources.remove(waitQueue);    //to whatever is guarded by waitQueue.
            this.invalidateCachedPriority();
        }

        public KThread getThread() {
            return thread;
        }

        private void invalidateCachedPriority() {
            if (this.priChange) return;
            this.priChange = true;
            for (final PriorityQueue priQue : this.waitingRes) {
            		priQue.invalidateCachedPriority();
            }
        }


        /**
         * The thread with which this object is associated.
         */
        protected KThread thread;
        /**
         * The priority of the associated thread.
         */
        protected int priority;

        protected final List<PriorityQueue> currentResources; //A list of the queues for which I am the current resource holder.

        protected final List<PriorityQueue> waitingRes;// A list of the queues in which I am waiting.

        protected int effPriority = priorityMinimum; //Holds the effective priority of this Thread State.

        protected boolean priChange = false; // True if the effective priority of this queue has been invalidated.
        
        
      
    }
}