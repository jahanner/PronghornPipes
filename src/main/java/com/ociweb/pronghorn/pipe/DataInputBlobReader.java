package com.ociweb.pronghorn.pipe;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;
import com.ociweb.pronghorn.util.math.Decimal;

public class DataInputBlobReader<S extends MessageSchema<S>>  extends InputStream implements DataInput {

    private final StringBuilder workspace;
    private final Pipe<S> pipe;
    private byte[] backing;
    private final int byteMask;
    private static final Logger logger = LoggerFactory.getLogger(DataInputBlobReader.class);
    
    private int length;
    private int bytesHighBound;
    private int bytesLowBound;
    protected int position;

    private TrieParser textToNumberParser;
    private TrieParserReader reader;
    
    private int EOF_MARKER = -1;
    
    public DataInputBlobReader(Pipe<S> pipe) {
    	super();
        this.pipe = pipe;
        this.backing = Pipe.blob(pipe);
        this.byteMask = Pipe.blobMask(pipe); 
        this.workspace = new StringBuilder(64);
        assert(this.backing!=null) : "The pipe must be init before use.";
    }
        
	public void debug() {
	    
    	Appendables.appendArray(Appendables.appendValue(System.out,  "read at ", bytesLowBound), '[', backing, bytesLowBound, byteMask, ']',  length);

	}
	
    public int openHighLevelAPIField(int loc) {
        
        this.length         = PipeReader.readBytesLength(pipe, loc);
        this.bytesLowBound  = this.position       = PipeReader.readBytesPosition(pipe, loc);
        this.backing        = PipeReader.readBytesBackingArray(pipe, loc);        
        this.bytesHighBound = pipe.blobMask & (position + length);
        
        assert(Pipe.validatePipeBlobHasDataToRead(pipe, position, length));
        
        return this.length;
    }
    
    public int readFromEndLastInt(int negativeIntOffset) {
    	assert(negativeIntOffset>0) : "there is no data found at the end";
    	
    	int position = bytesHighBound-(4*negativeIntOffset);
    	
    	logger.trace("reading int from position {}",position);
    	
    	return ( ( (       backing[byteMask & position++]) << 24) |
		 ( (0xFF & backing[byteMask & position++]) << 16) |
		 ( (0xFF & backing[byteMask & position++]) << 8) |
		   (0xFF & backing[byteMask & position++]) );
    }
    
    public int openLowLevelAPIField() {
        int meta = Pipe.takeRingByteMetaData(this.pipe);
		this.length    = Pipe.takeRingByteLen(this.pipe);
		this.bytesLowBound = this.position = Pipe.bytePosition(meta, this.pipe, this.length);
		this.backing   = Pipe.byteBackingArray(meta, this.pipe);               
		this.bytesHighBound = this.pipe.blobMask & (this.position + this.length);
		
		assert(Pipe.validatePipeBlobHasDataToRead(this.pipe, this.position, this.length));
		
		return this.length;
    }
    
    @Deprecated
    public static <S extends MessageSchema<S>> int openLowLevelAPIField(DataInputBlobReader<S> that) {
        return that.openLowLevelAPIField();
    }


    
    public int accumLowLevelAPIField() {
        
        if (0==this.length) {
            return openLowLevelAPIField();
        } else {        
        
            Pipe.takeRingByteMetaData(pipe);
            int len = Pipe.takeRingByteLen(pipe);
            
            this.length += len;
            this.bytesHighBound = pipe.blobMask & (bytesHighBound + len);
            
            return len;
        }
        
    }
    
        
    public boolean hasRemainingBytes() {
        return (byteMask & position) != bytesHighBound;
    }

    @Override
    public int available() {        
        return bytesRemaining(this);
    }

    public static int bytesRemaining(DataInputBlobReader<?> that) {
                
        return  that.bytesHighBound >= (that.byteMask & that.position) ? that.bytesHighBound- (that.byteMask & that.position) : (that.pipe.sizeOfBlobRing- (that.byteMask & that.position))+that.bytesHighBound;

    }

    public DataInput nullable() {
        return length<0 ? null : this;
    }
   
    public int position() {
    	return position;
    }
    
    public void position(int byteIndexFromStart) {
    	assert(byteIndexFromStart<length);
    	position = bytesLowBound+byteIndexFromStart;
    }
    
    
    
//    @Override
//	public synchronized void mark(int readlimit) {
//		super.mark(readlimit);
//	}
//
//	@Override
//	public synchronized void reset() throws IOException {
//		super.reset();
//	}
//
//	@Override
//	public boolean markSupported() {
//		return super.markSupported();
//	}

	@Override
    public int read(byte[] b) {
        if ((byteMask & position) == bytesHighBound) {
            return EOF_MARKER;
        }       
        
        int max = bytesRemaining(this);
        int len = b.length > max? max : b.length;      
        Pipe.copyBytesFromToRing(backing, position, byteMask, b, 0, Integer.MAX_VALUE, len);
        position += b.length;
        return len;
    }
    
    @Override
    public int read(byte[] b, int off, int len) {
        if ((byteMask & position) == bytesHighBound) {
            return EOF_MARKER;
        }
        
        int max = bytesRemaining(this);
        if (len > max) {
            len = max;
        }
        Pipe.copyBytesFromToRing(backing, position, byteMask, b, off, Integer.MAX_VALUE, len);
        position += len;
        return len;
    }
    
    @Override
    public void readFully(byte[] b) {
                
        Pipe.copyBytesFromToRing(backing, position, byteMask, b, 0, Integer.MAX_VALUE, b.length);
        position += b.length;
       
    }

    @Override
    public void readFully(byte[] b, int off, int len) {
        
        Pipe.copyBytesFromToRing(backing, position, byteMask, b, off, Integer.MAX_VALUE, len);
        position += len;
        
    }

    @Override
    public int skipBytes(int n) {
        
        int skipCount = Math.min(n, length-position);
        position += skipCount;
        
        return skipCount;
    }

    @Override
    public boolean readBoolean() {
        return 0!=backing[byteMask & position++];
    }

    @Override
    public byte readByte() {
        return backing[byteMask & position++];
    }

    @Override
    public int readUnsignedByte() {
        return 0xFF & backing[byteMask & position++];
    }
    
    private static <S extends MessageSchema<S>> short read16(byte[] buf, int mask, DataInputBlobReader<S> that) {
        return (short)((       buf[mask & that.position++] << 8) |
                       (0xFF & buf[mask & that.position++])); 
    }    
    
    private static <S extends MessageSchema<S>> int read32(byte[] buf, int mask, DataInputBlobReader<S> that) {        
        return ( ( (       buf[mask & that.position++]) << 24) |
                 ( (0xFF & buf[mask & that.position++]) << 16) |
                 ( (0xFF & buf[mask & that.position++]) << 8) |
                   (0xFF & buf[mask & that.position++]) ); 
    }
    
    private static <S extends MessageSchema<S>> long read64(byte[] buf, int mask, DataInputBlobReader<S> that) {        
        return ( ( (  (long)buf[mask & that.position++]) << 56) |              
                 ( (0xFFl & buf[mask & that.position++]) << 48) |
                 ( (0xFFl & buf[mask & that.position++]) << 40) |
                 ( (0xFFl & buf[mask & that.position++]) << 32) |
                 ( (0xFFl & buf[mask & that.position++]) << 24) |
                 ( (0xFFl & buf[mask & that.position++]) << 16) |
                 ( (0xFFl & buf[mask & that.position++]) << 8) |
                   (0xFFl & buf[mask & that.position++]) ); 
    }

    @Override
    public short readShort() {
        return read16(backing,byteMask,this);
    }

    @Override
    public int readUnsignedShort() {
        return 0xFFFF & read16(backing,byteMask,this);
    }

    @Override
    public char readChar() {
       return (char)read16(backing,byteMask,this);
    }

    @Override
    public int readInt() {
        return read32(backing,byteMask,this);
    }

    @Override
    public long readLong() {
        return read64(backing,byteMask,this);
    }

    @Override
    public float readFloat() {        
        return Float.intBitsToFloat(read32(backing,byteMask,this));
    }

    @Override
    public double readDouble() {
        return Double.longBitsToDouble(read64(backing,byteMask,this));
    }

    @Override
    public int read() {
        return (byteMask & position) != bytesHighBound ? backing[byteMask & position++] : EOF_MARKER;//isOpen?0:-1;
    }

    @Override
    public String readLine() {
        
        workspace.setLength(0);        
        if ((byteMask & position) != bytesHighBound) {
            char c = (char)read16(backing,byteMask,this);
            while (
                    ((byteMask & position) != bytesHighBound) &&  //hard stop for EOF but this is really end of field.
                    c != '\n'
                  ) {
                if (c!='\r') {
                    workspace.append(c);            
                    c = (char)read16(backing,byteMask,this);
                }
            }
        }
        return new String(workspace);
    }

    @Override
    public String readUTF() {
        int length = readShort(); //read first 2 byte for length in bytes to convert.
        
        workspace.setLength(0);
        try {
        	return readUTF(this, length, workspace).toString();
        } catch (Exception e) {
        	throw new RuntimeException(e);
        }
    }
    
    public <A extends Appendable> A readUTF(A target) {
        int length = readShort(); //read first 2 byte for length in bytes to convert.        
        try {
        	return readUTF(this, length, target);
        } catch (Exception e) {
        	throw new RuntimeException(e);
        }
    }

	public long parseUTF(TrieParserReader reader, TrieParser trie) {
		int len = readShort();		
		
		long result = reader.query(reader, trie, backing, position, len, byteMask);
		if (result!=-1) {
			position+=len;//only move upon succcesful parse.
		}		
		return result;
	}
    
	public boolean equalUTF(byte[] equalText) {
		int len = readShort();
		if (len!=equalText.length) {
			return false;
		}
		int p = 0;
		int pp = position;
		while (--len>=0) {
			if (equalText[p++]!=backing[byteMask & pp++]) {
				return false;
			}
		}
		//only moves forward when equal.
		position = pp;
		return true;
	}
	
    public static <A extends Appendable, S extends MessageSchema<S>> A readUTF(DataInputBlobReader<S> reader, int length, A target) throws IOException {
        long charAndPos = ((long)reader.position)<<32;
        long limit = ((long)reader.position+length)<<32;

        while (charAndPos<limit) {
            charAndPos = Pipe.decodeUTF8Fast(reader.backing, charAndPos, reader.byteMask);
            target.append((char)charAndPos);
        }
        reader.position+=length;
        return target;
    }
        
    public Object readObject()  {
        
        try {
            return new ObjectInputStream(this).readObject();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    ////
    //support method for direct copy
    ////
    public static <S extends MessageSchema<S>> int read(DataInputBlobReader<S> reader, byte[] b, int off, int len, int mask) {

        int max = bytesRemaining(reader);
        if (len > max) {
            len = max;
        }
        Pipe.copyBytesFromToRing(reader.backing, reader.position, reader.byteMask, b, off, mask, len);
        reader.position += len;
        return len;
    }
        
    public <T extends MessageSchema<T>> void readInto(DataOutputBlobWriter<T> writer, int length) {
    	
    	DataOutputBlobWriter.write(writer, backing, position, length, byteMask);
    	position += length;
    	
    }
    ////////
    //parsing methods
    ///////
    private TrieParser textToNumberParser() {
    	if (textToNumberParser == null) {
    		TrieParser p = new TrieParser(32,false);
    		p.setUTF8Value("%i%.", 1);
    		reader = new TrieParserReader(true);
    	}
    	return textToNumberParser;
    }
    
    public static long readUTFAsLong(DataInputBlobReader<?> reader) {
    	TrieParser parser = reader.textToNumberParser();    	
    	long token = TrieParserReader.query(reader.reader, parser, reader.backing, reader.position,
    			               reader.available(), reader.byteMask);
    	
    	if (token>=0) {
    		return TrieParserReader.capturedLongField(reader.reader, 0);
    	} else {
    		return -1;//none
    	}    	
    }
    
    public static double readUTFAsDecimal(DataInputBlobReader<?> reader) {
    	TrieParser parser = reader.textToNumberParser();    	
    	long token = TrieParserReader.query(reader.reader, parser, reader.backing, reader.position,
    			               reader.available(), reader.byteMask);
    	
    	if (token>=0) {
    		long m = TrieParserReader.capturedDecimalMField(reader.reader, 0);
    		byte e = TrieParserReader.capturedDecimalEField(reader.reader, 0);
    		return Decimal.asDouble(m, e);
    		
    	} else {
    		return -1;//none
    	}    	
    }
    
    ///////
    //Packed Chars
    //////
    
    public <A extends Appendable> A readPackedChars(A target) throws IOException {
        readPackedChars(this,target);
        return target;
    }
    
    public static <S extends MessageSchema<S>> void readPackedChars(DataInputBlobReader<S> that, Appendable target) throws IOException {
        int length = readPackedInt(that);
        int i = length;
        while (--i>=0) {
            target.append((char) readPackedInt(that));
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////////////
    //Support for packed values
    //////////////////////////////////////////////////////////////////////////////////
    //Read signed using variable length encoding as defined in FAST 1.1 specification
    //////////////////////////////////////////////////////////////////////////////////
    
    /**
     * Parse a 64 bit signed value 
     */
    public long readPackedLong() {   
            return readPackedLong(this);
    }

    public int readPackedInt() {   
        return readPackedInt(this);
    }
    
    public double readDecimalAsDouble() {
    	return Decimal.asDouble(readPackedLong(), readByte());
    }
    
    public long readDecimalAsLong() {
    	return Decimal.asLong(readPackedLong(), readByte());
    }
    
    public short readPackedShort() {
        return (short)readPackedInt(this);
    }

    public static <S extends MessageSchema<S>> long readPackedLong(DataInputBlobReader<S> that) {
        byte v = that.backing[that.byteMask & that.position++];
        long accumulator = (~((long)(((v>>6)&1)-1)))&0xFFFFFFFFFFFFFF80l;
        return (v >= 0) ? readPackedLong((accumulator | v) << 7,that.backing,that.byteMask,that) : (accumulator) |(v & 0x7F);
    }

    public static <S extends MessageSchema<S>> int readPackedInt(DataInputBlobReader<S> that) {
        byte v = that.backing[that.byteMask & that.position++];
        int accumulator = (~((int)(((v>>6)&1)-1)))&0xFFFFFF80; 
        return (v >= 0) ? readPackedInt((accumulator | v) << 7,that.backing,that.byteMask,that) : accumulator |(v & 0x7F);
    }
    
    //recursive use of the stack turns out to be a good way to unroll this loop.
    private static <S extends MessageSchema<S>> long readPackedLong(long a, byte[] buf, int mask, DataInputBlobReader<S> that) {
        return readPackedLongB(a, buf, mask, that, buf[mask & that.position++]);
    }

    private static <S extends MessageSchema<S>> long readPackedLongB(long a, byte[] buf, int mask, DataInputBlobReader<S> that, byte v) {
        assert(a!=0 || v!=0) : "malformed data";
        return (v >= 0) ? readPackedLong((a | v) << 7, buf, mask, that) : a | (v & 0x7Fl);
    }
       
    private static <S extends MessageSchema<S>> int readPackedInt(int a, byte[] buf, int mask, DataInputBlobReader<S> that) {
        return readPackedIntB(a, buf, mask, that, buf[mask & that.position++]);
    }

    private static <S extends MessageSchema<S>> int readPackedIntB(int a, byte[] buf, int mask, DataInputBlobReader<S> that, byte v) {
        assert(a!=0 || v!=0) : "malformed data";
        return (v >= 0) ? readPackedInt((a | v) << 7, buf, mask, that) : a | (v & 0x7F);
    }

	public static void setupParser(DataInputBlobReader<?> input, TrieParserReader reader) {
		TrieParserReader.parseSetup(reader, input.backing, input.position, bytesRemaining(input), input.byteMask); 
	}
    
    
    
}
