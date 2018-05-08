package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.Hashtable;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);

		myFileList = new OpenFile[16];
		stdin = UserKernel.console.openForReading();
		stdout = UserKernel.console.openForWriting();
		myFileList[0] = stdin; // first two are stin/stdout as states in
								// syscall.h
		myFileList[1] = stdout;
		
		statusLock = new Lock();
		UserKernel.processIDMutex.P();
		UserKernel.processID++;
		processID = UserKernel.processID;
		UserKernel.processIDMutex.V();
		exitStatus = null;
		joinCondition = new Condition(statusLock);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		new UThread(this).setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr
	 *            the starting virtual address of the null-terminated string.
	 * @param maxLength
	 *            the maximum number of characters in the string, not including
	 *            the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @param offset
	 *            the first byte to write in the array.
	 * @param length
	 *            the number of bytes to transfer from virtual memory to the
	 *            array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		byte[] memory = Machine.processor().getMemory();

		if (data == null || offset < 0 || length < 0
				|| (offset + length) < data.length || vaddr < 0
				|| vaddr >= memory.length) {
			return 0;
		}

		int ByteIndex = 0;

		while (ByteIndex < length) {

			int vpn = Machine.processor().pageFromAddress(vaddr + ByteIndex);
			TranslationEntry entry = pageTable[vpn];
			entry.used = true;

			int voffset = Machine.processor().offsetFromAddress(
					vaddr + ByteIndex);

			int paddr = Machine.processor().makeAddress(entry.ppn, voffset);
			if (paddr < 0 || paddr >= memory.length || !entry.valid) {
				return 0;
			}
			System.arraycopy(memory, paddr, data, offset + ByteIndex, 1);
			ByteIndex++;

		}
		return ByteIndex;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @param offset
	 *            the first byte to transfer from the array.
	 * @param length
	 *            the number of bytes to transfer from the array to virtual
	 *            memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		int ByteIndex = 0;

		byte[] memory = Machine.processor().getMemory();

		if (data == null || offset < 0 || length < 0
				|| (offset + length) < data.length || vaddr < 0
				|| vaddr >= memory.length) {
			return 0;
		}

		while (ByteIndex < length) {
			int vpn = Machine.processor().pageFromAddress(vaddr + ByteIndex);
			TranslationEntry entry = pageTable[vpn];
			entry.used = true;

			int voffset = Machine.processor().offsetFromAddress(
					vaddr + ByteIndex);

			int paddr = Machine.processor().makeAddress(entry.ppn, voffset);
			if (paddr < 0 || paddr >= memory.length || !entry.valid
					|| entry.readOnly) {
				return 0;
			}
			System.arraycopy(data, offset + ByteIndex, memory, paddr, 1);
			ByteIndex++;

		}
		return ByteIndex;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > UserKernel.AvailablePages.size()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		pageTable = new TranslationEntry[numPages];
		// for (int i=0; i<numPages; i++)
		// pageTable[i] = new TranslationEntry(i,-1, false,false,false,false);

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				UserKernel.ListLock.acquire();

				int Newppn = 0;
				Newppn = UserKernel.AvailablePages.removeFirst();

				UserKernel.ListLock.release();

				// for now, just assume virtual addresses=physical addresses
				Lib.assertTrue(pageTable[vpn] == null);
				pageTable[vpn] = new TranslationEntry(vpn, Newppn, true,
						section.isReadOnly(), false, false);
				// entry.ppn = Newppn;
				// entry.valid = true;
				// entry.readOnly = section.isReadOnly();
				section.loadPage(i, pageTable[vpn].ppn);
			}
		}

		for (int i = numPages - 9; i < numPages; i++) {

			UserKernel.ListLock.acquire();
			int Newppn = 0;

			Newppn = UserKernel.AvailablePages.removeFirst();
			UserKernel.ListLock.release();

			Lib.assertTrue(pageTable[i] == null);
			pageTable[i] = new TranslationEntry(i, Newppn, true, false, false,
					false);

			// UserKernel.PhysPageSem.V();
			// entry.ppn = PageNum;
			// entry.valid = true;
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for (int i = 0; i < pageTable.length; i++) {
			if (pageTable[i].valid) {
				UserKernel.ListLock.acquire();
				UserKernel.AvailablePages.add(pageTable[i].ppn);
				UserKernel.ListLock.release();
			}
		}
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		/*
		 * Machine.halt();
		 * 
		 * Lib.assertNotReached("Machine.halt() did not halt machine!"); return
		 * 0;
		 */
		if (this != UserKernel.root){
			return 0;
		}

		// need this chunk?
		unloadSections();
		for (int i = 2; i < myFileList.length; i++) {
			if (myFileList[i] != null)
				myFileList[i].close();
		}

		Machine.halt();

		return 0;

	}

	private int handleExit(int status) {
		/**
		 * Terminate the current process immediately. Any open file descriptors
		 * belonging to the process are closed. Any children of the process no
		 * longer have a parent process.
		 * 
		 * status is returned to the parent process as this process's exit
		 * status and can be collected using the join syscall. A process exiting
		 * normally should (but is not required to) set status to 0.
		 * 
		 * exit() never returns.
		 */
		/*
		 * 
		 * foreach(child : getChildren(processId)){ //for each child, shutdown
		 * the child exit(child); //shutdown the child }
		 * getParent(processId).returnCode = 0; //return the return code to the
		 * parent join(getParent(processId)); //join the current process into
		 * the parent
		 */
		/*
		 * Node<UserProcess> local = getProcessFromPID(processId,
		 * UserKernel.processes); if(local.getChildren().size() > 0){ for(int i
		 * = 0; i < local.getChildren().size(); i++){
		 * local.getChildren().get(i).getData().status = status; } }
		 * /*if(myChildProcess!=null){ myChildProcess.status = status; }
		 */
		unloadSections();
		for (int i = 2; i < myFileList.length; i++) {
			if (myFileList[i] != null)
				myFileList[i].close();
		}
		statusLock.acquire();
		exitStatus = status;
		statusLock.release();

		parentMutex.P();
		if (parent != null) {
			parent.statusLock.acquire();
			parent.joinCondition.wakeAll();
			parent.statusLock.release();

		}
		parentMutex.V();

		for (UserProcess aChild : children.values()) {
			aChild.parentMutex.P();
			aChild.parent = null;
			aChild.parentMutex.V();
		}
		
		if(UserKernel.root == this){
			handleHalt();
		}

		// Handles calling terminate when this is the last process
		//decProcessCount();

		UThread.finish();
		return status;
	}

	private int handleExec(int name, int argc, int argv) {
		/**
		 * Execute the program stored in the specified file, with the specified
		 * arguments, in a new child process. The child process has a new unique
		 * process ID, and starts with stdin opened as file descriptor 0, and
		 * stdout opened as file descriptor 1.
		 * 
		 * file is a null-terminated string that specifies the name of the file
		 * containing the executable. Note that this string must include the
		 * ".coff" extension.
		 * 
		 * argc specifies the number of arguments to pass to the child process.
		 * This number must be non-negative.
		 * 
		 * argv is an array of pointers to null-terminated strings that
		 * represent the arguments to pass to the child process. argv[0] points
		 * to the first argument, and argv[argc-1] points to the last argument.
		 * 
		 * exec() returns the child process's process ID, which can be passed to
		 * join(). On error, returns -1.
		 */

		/*
		 * Int processId = new Random(int); //creates a new process ID while
		 * (processId is already in use){ //check to see if the generated
		 * process ID is in use processId = new Random(int); //if in use create
		 * a new one until one’s not in use } Open file; //open the file Open
		 * stdin(0); //open input stream Open stdout(1); //open output stream
		 * Line line = file.getNextLine(); //Grab next line of the file
		 * while(line != null){ //check to see if the line has code Run line;
		 * //run the line of code line = file.getNextLine(); //get new line }
		 * exit(processId); //once out of lines, shutdown the process
		 */
		String fileName = null;
		fileName = readVirtualMemoryString(name, 256);

		if (fileName == null || argc < 0) {
			return -1;
		}
		
		String[] args = new String[argc];
		byte[] buffer = new byte[4];
		for (int i = 0; i < argc; i++) {
			args[i] = readVirtualMemoryString(Lib.bytesToInt(buffer, 0), 256);
			if (args[i] == null) {
				return -1;
			}
		}

		UserProcess child = newUserProcess();

		// Keep track of parent's children
		children.put(child.processID, child);

		// Child keeps track of it's parent
		child.parent = this;

		// successful loading returns child pID to the parent process
		if (child.execute(fileName, args)) {
			return child.processID;
		} else {
			return -1;
		}
		// Daniel and Prab

	}

	private int handleJoin(int pid, int status) {
		/**
		 * Suspend execution of the current process until the child process specified
		 * by the processID argument has exited. If the child has already exited by the
		 * time of the call, returns immediately. When the current process resumes, it
		 * disowns the child process, so that join() cannot be used on that process
		 * again.
		 *
		 * processID is the process ID of the child process, returned by exec().
		 *
		 * status points to an integer where the exit status of the child process will
		 * be stored. This is the value the child passed to exit(). If the child exited
		 * because of an unhandled exception, the value stored is not defined.
		 *
		 * If the child exited normally, returns 1. If the child exited as a result of
		 * an unhandled exception, returns 0. If processID does not refer to a child
		 * process of the current process, returns -1.
		 */

		/*
		 * if(parentId == null || childId == null){  //If the process is empty, quit
        	Return 0;  //quit
			}
    		Int returnStatus = childId.returnCode; //try to shutdown
    		while(returnStatus == null){  //if not shutdown
        	returnStatus = childId.returnCode;  //try again
        	if(returnStatus){  //if shutdown
            	childId = null;  //wipe process ID
            	Return returnStatus;  //return the return status
        	}
		 * 
		 * 
		 * 
		 */
		if (pid < 0 || status < 0){
			return -1;
		}
		if (!children.containsKey(pid)) {
			return -1;
		}

		UserProcess child = children.get(pid);

		child.statusLock.acquire();

		Integer childStatus = child.exitStatus;

		if (childStatus == null) {
			child.statusLock.release();
			statusLock.acquire();
			joinCondition.sleep();
			statusLock.release();

			child.statusLock.acquire();
			childStatus = child.exitStatus;

		}
		child.statusLock.release();
		// Lib.assertTrue(childStatus != null);

		// Child should no longer be joinable as in syscall.h
		children.remove(pid);

		// Write the status to the memory address given
		byte[] statusAry = Lib.bytesFromInt(childStatus.intValue());
		writeVirtualMemory(status, statusAry);
		if (childStatus.intValue() == 0){
			return 1;
		} else {
			return 0;
		}
		// Daniel and Prab

	}

	private int handleCreate(int addr) { // create = 1 if file is created, else
											// 0
		// Max length is 256

		if (addr < 0) {
			return -1; // return -1 instead of throwing exceptions
		}
		String name = readVirtualMemoryString(addr, 256); // name
		if (name == null) {
			return -1;
		}

		//OpenFile myFile = Machine.stubFileSystem().open(name, true);// my file
																	// created
		OpenFile myFile = ThreadedKernel.fileSystem.open(name, true);
		// original implementation

		/*
		 * for(int j = 0; j<16; j++) { //makes all indexes null each time it is
		 * called
		 * 
		 * myFileList[j] = null;
		 * 
		 * }
		 */

		if (myFile == null)
			return -1;

		for (int i = 0; i < 16; i++) {

			if (myFileList[i] == null) {
				myFileList[i] = myFile;
				return i;
			}
		}
		return -1; // list full

	}

	private int handleOpen(int addr) { // create = 1 if file is created, else 0
		// Max length is 256

		if (addr < 0) {
			return -1; // return -1 instead of throwing exceptions
		}
		String name = readVirtualMemoryString(addr, 256); // name = filename
		if (name == null)
			return -1;

		//OpenFile myFile = Machine.stubFileSystem().open(name, false);
		// Original Implementation: OpenFile myfile =
		// ThreadedKernal.fileSystem.open(myfile, false);
		OpenFile myFile = ThreadedKernel.fileSystem.open(name, false);
		if (myFile == null)
			return -1;

		// else if(i == 15)
		// return 0; //list full
		for (int i = 0; i < 16; i++) {

			if (myFileList[i] == null) {
				myFileList[i] = myFile;
				return i;
			}
		}
		return -1;

	}

	private int handleWrite(int i, int addr, int size) {
		// OpenFile myfile = myFileList[i];
		if (size == 0)
			return 0;

		if (i < 0 || i > 15 || size < 0 /* || size > maxSize */|| addr < 0) {
			return -1;
		}

		OpenFile writable = myFileList[i];
		if (writable == null) {
			return -1;
		}

		byte[] author = new byte[size];
		int tmpLength = readVirtualMemory(addr, author, 0, size);

		if (tmpLength != size)
			return -1;

		int numBytesWriten = writable.write(author, 0, tmpLength);

		if (numBytesWriten == -1 || numBytesWriten != size) {
			return -1;
		}
		return numBytesWriten;
	}

	private int handleRead(int i, int addr, int size) {
		if (size == 0) {
			return 0;
		}

		if (i < 0 || i > 15 || size < 0 /* || size > maxSize */|| addr < 0) {
			return -1;
		}
		OpenFile myfile = myFileList[i];
		if (myfile == null) {
			return -1;
		}
		if (pageTable[Processor.pageFromAddress(addr)].readOnly) {
			return -1;
		}
		// pagetable is read only return -1

		// int numBytesWrited = 0;
		byte[] buffer = new byte[size];
		// size -=buffer.length;
		int numBytesRead = myfile.read(buffer, 0, size);
		if (numBytesRead < 0) {
			return -1;
		}
		int numBytesNewlyWrited = writeVirtualMemory(addr, buffer, 0,
				numBytesRead);
		if (numBytesNewlyWrited != numBytesRead) {
			return -1;
		}
		return numBytesNewlyWrited;

	}

	private int handleClose(int i) {
		if (i < 0 || i > 15) {
			return -1;
		}
		if (myFileList[i] == null) {

			return -1;
		}

		myFileList[i].close();
		myFileList[i] = null;
		return 0;
	}

	private int handleUnlink(int addr) {
		if (addr < 0) {
			return -1; // return -1 instead of throwing exceptions
		}
		String name = readVirtualMemoryString(addr, 256); // name = filename

		if (name == null)
			return -1;
		
		Machine.stubFileSystem().remove(name); // remove to free up space for
												// other files to use

		/*
		 * if (ThreadedKernal.fileSystem.remove (name)) //DANNY syntax error.
		 * What are you trying to do return 0;
		 * 
		 * return -1;
		 */
		// Original implementation
		return 0;

	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall
	 *            the syscall number.
	 * @param a0
	 *            the first syscall argument.
	 * @param a1
	 *            the second syscall argument.
	 * @param a2
	 *            the third syscall argument.
	 * @param a3
	 *            the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';

	OpenFile[] myFileList;
	OpenFile stdin;
	OpenFile stdout;

	int maxSize = 256;

	protected int processID;
	protected Semaphore parentMutex = new Semaphore(1);
	protected UserProcess parent;
	protected Hashtable<Integer, UserProcess> children = new Hashtable<Integer, UserProcess>();

	protected Integer exitStatus;
	protected Lock statusLock;
	protected Condition joinCondition;
}