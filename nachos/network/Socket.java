package nachos.network;

import nachos.machine.*;
import nachos.threads.*;

// Socket class
// should be full-duplex
// will work with PostOffice

enum SocketState {
	CLOSED, CLOSING, DEADLOCK, ESTABLISHED, STP_RCVD, STP_SENT, SYN_RCVD, SYN_SENT, VIOLATION
};

enum SocketMsg {
	ACK, DATA, FIN, FINACK, RECV, SEND, STP, SYN, SYNACK/*, TIMER*/
//	-1 , -2  , -3 , -4    , -5  , -6  , -7 , -8 , -9
};//_

public class Socket {
	public Socket(int port) {
		localHost = Machine.networkLink().getLinkAddress();
		localPort = port;
		remoteHost = remotePort = -1;
		state = SocketState.CLOSED;
		sendLock = new Lock();
		recvLock = new Lock();
		waitLock = new Lock();
		wait = new Condition(waitLock);
	}
	private int MsgToInt(SocketMsg msg) {
		switch (msg) {
			case ACK:		
				return -1;
			case DATA:	
				return -2;
			case FIN:
				return -3;
			case FINACK:	
				return -4;
			case RECV:
				return -5;
			case SEND:	
				return -6;
			case STP:
				return -7;
			case SYN:
				return -8;
			case SYNACK:
				return -9;
			default:
			System.out.println("Invalid");
			return -10;
		}
	}
	private SocketMsg IntToMsg(int i) {
		switch (i) {
			case -1:	return SocketMsg.ACK;
			case -2:	return SocketMsg.DATA;
			case -3:	return SocketMsg.FIN;
			case -4:	return SocketMsg.FINACK;
			case -5:	return SocketMsg.RECV;
			case -6:	return SocketMsg.SEND;
			case -7:	return SocketMsg.STP;
			case -8:	return SocketMsg.SYN;
			case -9:	return SocketMsg.SYNACK;
			default:
			System.out.println("Invalid");
			return null;
		}
	}
	public int connect(int host, int port) {
		// should set up a connection by sending an ACK signal
		if (state != SocketState.CLOSED) return -1;
		remoteHost = host;
		remotePort = port;
		byte[] message = Lib.bytesFromInt(MsgToInt(SocketMsg.SYN));
		MailMessage syn = null;
		try {
			syn = new MailMessage(remoteHost,remotePort,localHost,localPort,message);
		} catch (MalformedPacketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		NetKernel.postOffice.send(syn);
		state = SocketState.SYN_SENT;
		// CONNECT: send SYN, block
		// should block here
		MailMessage synack = NetKernel.postOffice.receive(localPort);
		int returnMsg = Lib.bytesToInt(synack.contents,0);
		if (IntToMsg(returnMsg) == SocketMsg.SYNACK) {
			state = SocketState.ESTABLISHED;
		}
		return 0;
	}
	public int accept() {
		// ACCEPT: send SYNACK
		// SYNACK: wake CONNECT
		MailMessage syn = NetKernel.postOffice.receive(localPort);
		int rcvdMsg = Lib.bytesToInt(syn.contents,0);
		SocketMsg converted = IntToMsg(rcvdMsg);
		if (converted == SocketMsg.SYN) {
			state = SocketState.SYN_RCVD;syn
			remoteHost = converted.srcLink;
			remotePort = converted.srcPort;
			byte[] message = Lib.bytesFromInt(MsgToInt(SocketMsg.SYNACK));
			MailMessage synack = new MailMessage(remoteHost,remotePort,localHost,localPort,message);
			NetKernel.postOffice.send(synack);
			state = SocketState.ESTABLISHED;
			return 0;
		}
		return -1;
	}
	public int send(byte[] buffer, int size) {
		if (remoteHost == -1 || remotePort == -1) return -1;
		// SEND: enqueue, shift window (EST), fail(STP_RCVD)
		return -1;
	}
	private int send(SocketState sState) {
	
		return 0;
	}
	public int recieve(byte[] buffer, int size) {
		if (remoteHost == -1 || remotePort == -1) return -1;
		// RECV: dequeue (EST, STP_RCVD)
		return -1;
	}
	private int recieve(SocketState sState) {
	
		return 0;
	}
	
	public int close() {
		// CLOSE (sendQ not empty): send STP
		return -1;
	}
	// 
	private int localHost;
	private int localPort;
	private int remoteHost;
	private int remotePort;
	public SocketState state;
	/*private Lock sendLock;
	private Lock recvLock;
	private Lock waitLock;
	private Condition wait;*/
	private static final int maxPackets = 16;
}