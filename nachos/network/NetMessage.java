package nachos.network;

import nachos.machine.*;
import java.util.Arrays;

/**
 * A mail message. Includes a packet header, a mail header, and the actual
 * payload.
 *
 * @see	nachos.machine.Packet
 */
public class NetMessage 
{
    /**
     * Allocate a new mail message to be sent, using the specified parameters.
     *
     * @param	dstLink		the destination link address.
     * @param	dstPort		the destination port.
     * @param	srcLink		the source link address.
     * @param	srcPort		the source port.
     * @param	contents	the contents of the packet.
     */
    public NetMessage(int dstLink, int dstPort, int srcLink, int srcPort,
		       int status, int seqNum, byte[] contents) throws MalformedPacketException 
    {
   
    	if (dstPort < 0 || dstPort >= portLimit ||	//
    	    srcPort < 0 || srcPort >= portLimit ||	// these make sure the ports are in a valid range
    	    status < 0 || status >= maxStatus ||	// makes sure the bits are between 0 and 16
    	    seqNum < 0 || seqNum >= maxSeqNum ||	// 
    	    contents.length > maxContentsLength)
	    	throw new MalformedPacketException();
	
	// sets the bytes of each of the parameters 
    	this.dstPort = (byte) dstPort;
    	this.srcPort = (byte) srcPort;
    	this.status = (byte) status;
    	System.out.println("init " + this.status);
    	this.seqNum = seqNum;
    	this.contents = contents;

	// an array of the packetConents to fill with the parameters size
    	byte[] packetContents = new byte[headerLength + contents.length];

    	packetContents[0] = (byte) dstPort;
    	packetContents[1] = (byte) srcPort;
    	packetContents[3] = (byte) status;
    	byte[] seqNumAsByte = Lib.bytesFromInt(seqNum);
    	System.arraycopy(seqNumAsByte, 0, packetContents, 4, seqNumAsByte.length);

    	System.arraycopy(contents, 0, packetContents, headerLength,
    			 contents.length);
    	System.out.println(Arrays.toString(packetContents));
    	packet = new Packet(dstLink, srcLink, packetContents);
    }
	
    /**
     * Allocate a new mail message using the specified packet from the network.
     *
     * @param	packet	the packet containg the mail message.
     */
    public NetMessage(Packet packet) throws MalformedPacketException 
    {
	   this.packet = packet;

    	if (packet.contents.length < headerLength ||	// makes sure the header is valid and within its max length
    	    packet.contents[0] < 0 || packet.contents[0] >= portLimit ||	// these make sure the packet contents from
    	    packet.contents[1] < 0 || packet.contents[1] >= portLimit ||	// the network are valid to create the mail 
    	    packet.contents[3] < 0 || packet.contents[3] >= maxStatus ||	// message
    	    Lib.bytesToInt(Arrays.copyOfRange(packet.contents, 4, 8), 0) < 0 ||
    	    Lib.bytesToInt(Arrays.copyOfRange(packet.contents, 4, 8), 0) >= maxSeqNum)
    	    	throw new MalformedPacketException();

    	dstPort = packet.contents[0];
    	srcPort = packet.contents[1];
    	status = packet.contents[3];
    	seqNum = Lib.bytesToInt(Arrays.copyOfRange(packet.contents, 4, 8), 0);

    	contents = new byte[packet.contents.length - headerLength];
    	System.arraycopy(packet.contents, headerLength, contents, 0,
    			 contents.length);
    }

    /**
     * Return a string representation of the message headers.
     */
    public String toString() 
    {
	return "from (" + packet.srcLink + ":" + srcPort +
	    ") to (" + packet.dstLink + ":" + dstPort +
	    "), " + contents.length + " bytes";
    }
    
    /** This message, as a packet that can be sent through a network link. */
    public Packet packet;
    /** The port used by this message on the destination machine. */
    public int dstPort;
    /** The port used by this message on the source machine. */
    public int srcPort;
    /** Status bits */
    public int status;
    /** Sequence number */
    public int seqNum;
    /** The contents of this message, excluding the mail message header. */
    public byte[] contents;

    /**
     * The number of bytes in a mail header. The header is formatted as
     * follows:
     *
     * <table>
     * <tr><td>offset</td><td>size</td><td>value</td></tr>
     * <tr><td>0</td><td>1</td><td>destination port</td></tr>
     * <tr><td>1</td><td>1</td><td>source port</td></tr>
     * </table>
     */
    public static final int headerLength = 8;

    /** Maximum payload (real data) that can be included in a single mesage. */
    public static final int maxContentsLength =
	Packet.maxContentsLength - headerLength;

    /**
     * The upper limit on mail ports. All ports fall between <tt>0</tt> and
     * <tt>portLimit - 1</tt>.
     */    
    public static final int portLimit = 128;

    /** Maximum allowed status int */
    public static final int maxStatus = 16;

    /** Maximum allowed sequence number */
    public static final int maxSeqNum = java.lang.Integer.MAX_VALUE;
}
