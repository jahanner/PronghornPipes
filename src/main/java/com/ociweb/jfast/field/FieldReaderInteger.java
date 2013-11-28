package com.ociweb.jfast.field;

import com.ociweb.jfast.primitive.PrimitiveReader;

public class FieldReaderInteger {
	
	//crazy big value?
	private final int INSTANCE_MASK = 0xFFFFF;//20 BITS
	
	private final static byte UNSET     = 0;  //use == 0 to detect (default value)
	private final static byte SET_NULL  = -1; //use < 0 to detect
	private final static byte SET_VALUE = 1;  //use > 0 to detect
	
	private final PrimitiveReader reader;
	
	private final int[]  intValues;
	private final byte[] intValueFlags;


	public FieldReaderInteger(PrimitiveReader reader, int fields) {
		this.reader = reader;
		this.intValues = new int[fields];
		this.intValueFlags = new byte[fields];
	}
	
	public void reset() {
		int i = intValueFlags.length;
		while (--i>=0) {
			intValueFlags[i] = UNSET;
		}
	}

	public int readIntegerUnsigned(int token) {
		//no need to set initValueFlags for field that can never be null
		return intValues[token & INSTANCE_MASK] = reader.readIntegerUnsigned();
	}

	public int readIntegerUnsignedOptional(int token, int valueOfOptional) {
		if (reader.peekNull()) {
			reader.incPosition();
			intValueFlags[token & INSTANCE_MASK] = SET_NULL;
			return valueOfOptional;
		} else {
			int instance = token & INSTANCE_MASK;
			intValueFlags[instance] = SET_VALUE;
			return intValues[instance] = reader.readIntegerUnsignedOptional();
		}
	}
	
	public int readIntegerSigned(int token) {
		//no need to set initValueFlags for field that can never be null
		return intValues[token & INSTANCE_MASK] = reader.readIntegerSigned();
	}

	public int readIntegerSignedOptional(int token, int valueOfOptional) {
		if (reader.peekNull()) {
			reader.incPosition();
			intValueFlags[token & INSTANCE_MASK] = SET_NULL;
			return valueOfOptional;
		} else {
			int instance = token & INSTANCE_MASK;
			intValueFlags[instance] = SET_VALUE;
			return intValues[instance] = reader.readIntegerSignedOptional();
		}
	}

	public int readIntegerUnsignedConstant(int token, int valueOfOptional) {
		return (reader.popPMapBit()==0 ? valueOfOptional : intValues[token & INSTANCE_MASK]);
	}

	public int readIntegerUnsignedCopy(int token) {
		return (reader.popPMapBit()==0 ? 
				 intValues[token & INSTANCE_MASK] : 
			     (intValues[token & INSTANCE_MASK] = reader.readIntegerUnsigned()));
	}

	public int readIntegerUnsignedCopyOptional(int token, int valueOfOptional) {
		
		if (reader.popPMapBit()==0) {
			if (intValueFlags[token & INSTANCE_MASK] < 0) {
				return valueOfOptional;
			} else {
				return intValues[token & INSTANCE_MASK];
			}
		} else {
			if (reader.peekNull()) {
				reader.incPosition();
				intValueFlags[token & INSTANCE_MASK] = SET_NULL;
				return valueOfOptional;
			} else {
				int instance = token & INSTANCE_MASK;
				intValueFlags[instance] = SET_VALUE;
				return intValues[instance] = reader.readIntegerUnsignedOptional();
			}
		}
	}
	
	public int readIntegerUnsignedDelta(int token) {
		
		int index = token & INSTANCE_MASK;
		return (intValues[index] = intValues[index]+reader.readIntegerSigned());
		
	}
	
	public int readIntegerUnsignedDeltaOptional(int token, int valueOfOptional) {
		
		if (reader.popPMapBit()==0) {
			if (intValueFlags[token & INSTANCE_MASK] < 0) {
				return valueOfOptional;
			} else {
				return intValues[token & INSTANCE_MASK];
			}
		} else {
			if (reader.peekNull()) {
				reader.incPosition();
				intValueFlags[token & INSTANCE_MASK] = SET_NULL;
				return valueOfOptional;
			} else {
				int instance = token & INSTANCE_MASK;
				intValueFlags[instance] = SET_VALUE;
				return (intValues[instance] = intValues[instance]+reader.readIntegerSigned());
			}
		}
	}

	public int readIntegerUnsignedDefault(int token) {
		// TODO Auto-generated method stub
		return 0;
	}

	public int readIntegerUnsignedIncrement(int token) {
		// TODO Auto-generated method stub
		return 0;
	}

	public int readIntegerUnsignedDefaultOptional(int token) {
		// TODO Auto-generated method stub
		return 0;
	}

	public int readIntegerUnsignedIncrementOptional(int token) {
		// TODO Auto-generated method stub
		return 0;
	}

	public int readIntegerUnsignedDeltaOptional(int token) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	
}
