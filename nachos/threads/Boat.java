package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
    static BoatGrader bg;
    
	//static int O_counter;
	static int O_child;
	static int M_child;
	static int O_adult;
	static int M_adult;
	static int On_boat;
	static boolean boat;
	static int M_Arrival;

	static Lock Lkey = new Lock();					//lock so that threads can be synched

	static Condition Adult_Queue = new Condition(Lkey);		//condition variable for adult threads waiting to go to Molokai
															//met when Oahu Children = 1
	static Condition OBoat_Queue = new Condition(Lkey);		//condition variable for loading the boat
															//use this so two threads can go into the boat at the same time
	static Condition MChild_Wait = new Condition(Lkey);		//condition variable for children on Molokai waiting to return to Oahu after adult thread arrives

	static Condition OChild_Wait = new Condition(Lkey);		//condition variable for Oahu children waiting to get on the boat

	static Semaphore test = new Semaphore(0);				//so the main thread knows when the threads are finished
    
    public static void selfTest()
    {
	BoatGrader b = new BoatGrader();
	
//	System.out.println("\n Testing Boats with only 2 children");
//	begin(0, 2, b);

//	System.out.println("\n Testing Boats with 2 children, 1 adult");
//  	begin(1, 2, b);

  	System.out.println("\n Testing Boats with 3 children, 3 adults");
  	begin(5, 2, b);
    }

    public static void begin( int adults, int children, BoatGrader b )
    {
	// Store the externally generated autograder in a class
	// variable to be accessible by children.
	bg = b;

	// Instantiate global variables here
	O_child = children;
	M_child = 0;
	O_adult = adults;
	M_adult = 0;
	//O_counter = O_child + O_adult;
	boat = true;
	On_boat = 0;
	M_Arrival = 0;
	
	
	
	// Create threads here. See section 3.4 of the Nachos for Java
	// Walkthrough linked from the projects page.
	
	for(int i = 0; i < children; i++)
	{
		Runnable r = new Runnable()
        {
			public void run()
			{
				ChildItinerary();
			}
        };
        KThread c = new KThread(r);
        c.setName("child" + i + "thread");
        c.fork();
	}
	
	for(int j = 0; j < adults; j++)
	{
		Runnable r = new Runnable()
        {
			public void run()
			{
				AdultItinerary();
			}
        };
        KThread a = new KThread(r);
        a.setName("adult" + j + "thread");
        a.fork();
	}
	test.P();
	/*
	Runnable r = new Runnable() {
	    public void run() {
                SampleItinerary();
            }
        };
        KThread t = new KThread(r);
        t.setName("Sample Boat Thread");
        t.fork();
        */

    }

    static void AdultItinerary()
    {
	//bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE. 

	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	*/
	Lkey.acquire();
	while (O_child > 1 || !boat)						//sets threads to sleep while Oahu children doesn't equal 1
	{
		Adult_Queue.sleep();
	}
	//if (O_child == 1 && boat == true)
	//{		
		
		O_adult--;
		boat = false;
		bg.AdultRowToMolokai();
		
		M_adult++;
		MChild_Wait.wake();
		
		//System.out.println(O_adult + " Oahu adults and " + M_adult + " Molokai adults.");
	//}
		Lkey.release();
    }

    static void ChildItinerary()
    {
    	
	//bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE.     
	while(O_child + O_adult > 1){					//loops until there are no longer people on Oahu
		
		Lkey.acquire();

    	while(On_boat >= 2 || boat == false){				//while there are 2 children threads waiting to get on the boat, set all other threads to sleep
    		OChild_Wait.sleep();
    	}
    	if(On_boat == 0)
    	{					//if there aren't any threads waiting to get on the boat
    		On_boat++;						//
    		OChild_Wait.wake();				//wake a waiting thread if any
    		OBoat_Queue.sleep();			//wait for companion thread
    		bg.ChildRideToMolokai();
    		OBoat_Queue.wake();
    	}
    	else
    	{
    		On_boat++;						//get on Boat
   	 		OBoat_Queue.wake();				//wake other thread and head to Molokai
    		bg.ChildRowToMolokai();
    		OBoat_Queue.sleep();
    	}
    	
    	if(O_child == 1) 					//wakes the adult thread once there is only one child left on Oahu
		{	
			Adult_Queue.wake();	 
		}
    	
		O_child--;
		On_boat--;							//both get off the boat
		boat = false;
		
		M_child++;
		M_Arrival++;
		

		if (M_Arrival == 1)					//one child waits on Molokai for adult
		{
			MChild_Wait.sleep();
		}
		
//		if (O_adult == 0 && O_child == 0)
//		{			
//			//System.out.println(O_child + " Oahu children and " + M_child + " Molokai children.");
//			//System.out.println("Program finished");
//			return;
//		}
		
		M_Arrival = 0;					//reset new arrival
		M_child--;
		
		bg.ChildRowToOahu();			//1 child rows back to Oahu
		
		O_child++;
		boat = true;
		
		if (O_adult == 0 && O_child == 1)		//last check to see if anyone left on Oahu
		{
			bg.ChildRowToMolokai();
			O_child--;
			M_child++;
			boat = false;
		}
		
		Lkey.release();
		
		//System.out.println(O_child + " Oahu children and " + M_child + " Molokai children.");

    }
	
	// while (M_child != 0 && M_adult >= 1 && O_boat == false)
	// {
	// 	bg.ChildRowToOahu();
	// 	O_child++;
	// 	M_child--;
	// }
	test.V();
	
    }
   
    static void SampleItinerary()
    {
	// Please note that this isn't a valid solution (you can't fit
	// all of them on the boat). Please also note that you may not
	// have a single thread calculate a solution and then just play
	// it back at the autograder -- you will be caught.
	System.out.println("\n Everyone piles on the boat and goes to Molokai");
	bg.AdultRowToMolokai();
	bg.ChildRideToMolokai();
	bg.AdultRideToMolokai();
	bg.ChildRideToMolokai();
    }
    
}