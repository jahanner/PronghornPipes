package com.ociweb.jfast.stream;

import com.ociweb.jfast.field.DictionaryFactory;
import com.ociweb.jfast.field.FieldReaderBytes;
import com.ociweb.jfast.field.FieldReaderChar;
import com.ociweb.jfast.field.FieldReaderDecimal;
import com.ociweb.jfast.field.FieldReaderInteger;
import com.ociweb.jfast.field.FieldReaderLong;
import com.ociweb.jfast.field.TextHeap;
import com.ociweb.jfast.field.TokenBuilder;
import com.ociweb.jfast.field.TypeMask;
import com.ociweb.jfast.primitive.PrimitiveReader;

//May drop interface if this causes a performance problem from virtual table
public class FASTStaticReader implements FASTReader {


	
	private int templateStackHead = 0;
	private int[] templateStack = new int[100];// //TODO: need max depth?
	
	private final PrimitiveReader reader;
	private final int[] tokenLookup; //array of tokens as field id locations
	
	//This is the GLOBAL dictionary
	//When unspecified in the template GLOBAL is the default so these are used.
	private final FieldReaderInteger readerInteger;
	private final FieldReaderLong    readerLong;
	private final FieldReaderDecimal readerDecimal;
	private final FieldReaderChar readerChar;
	private final FieldReaderBytes readerBytes;
	
	//constant fields are always the same or missing but never anything else.
	//         manditory constant does not use pmap and has constant injected at destnation never xmit
	//         optional constant does use the pmap 1 (use initial const) 0 (not present)
	//
	//default fields can be the default or overridden this one time with a new value.

	
		
	public FASTStaticReader(PrimitiveReader reader, DictionaryFactory dcr) {
		this.reader = reader;
		this.tokenLookup = dcr.getTokenLookup();
		
		this.readerInteger = new FieldReaderInteger(reader,dcr.integerDictionary());
		this.readerLong = new FieldReaderLong(reader,dcr.longDictionary());
		this.readerDecimal = new FieldReaderDecimal(reader, dcr.decimalExponentDictionary(),dcr.decimalMantissaDictionary());
		this.readerChar = new FieldReaderChar(reader,dcr.charDictionary());
		this.readerBytes = new FieldReaderBytes(reader,dcr.byteDictionary());
		
		
		
	}
	
	public void copyDicionaryTo(FASTStaticWriter target) {
		//TODO: implement.
	}
	

	public void reset(DictionaryFactory df) {
		//clear all previous values to unset
		readerInteger.reset(df);
		readerLong.reset(df);
	}

	
	public TextHeap textHeap() {
		return readerChar.textHeap();
	}
	
	//package protected, unless we find a need to expose it?
	void readToken(int token) {
		//used by groups which hold list of tokens
		//at end of each group call back may be done and FASTDynamicReader used.

	    switch ((token>>TokenBuilder.SHIFT_TYPE)&TokenBuilder.MASK_TYPE) {
			case TypeMask.IntegerUnsigned:
				readIntegerUnsigned(token);
				break;
			case TypeMask.IntegerUnsignedOptional:
				readIntegerUnsignedOptional(token,0);
				break;
			case TypeMask.IntegerSigned:
				readIntegerSigned(token);
				break;
			case TypeMask.IntegerSignedOptional:
				readIntegerSignedOptional(token,0);
				break;
			case TypeMask.LongUnsigned:
				readLongUnsigned(token);
				break;
			case TypeMask.LongUnsignedOptional:
				readLongUnsignedOptional(token,0);
				break;
			case TypeMask.LongSigned:
				readLongSigned(token);
				break;
			case TypeMask.LongSignedOptional:
				readLongSignedOptional(token,0);
				break;
			case TypeMask.TextASCII:
				readTextASCII(token); //TODO: these nulls are not corect but we do not need the result.
			    break;
		    case TypeMask.TextASCIIOptional:
				readTextASCIIOptional(token);
			    break;
			case TypeMask.TextUTF8:
				readTextUTF8(token);
				break;
			case TypeMask.TextUTF8Optional:
				readTextUTF8Optional(token);
				break;
			case TypeMask.Decimal:
				//readerDecimal();
				break;
			case TypeMask.DecimalOptional:
				break;
			case TypeMask.ByteArray:
				break;
			case TypeMask.ByteArrayOptional:
				break;
			default:
				throw new UnsupportedOperationException();
			}
	}
	
	
	@Override
	public long readLong(int id, long valueOfOptional) {
		int token = id>=0 ? tokenLookup[id] : id;
		
		assert(0!=(token&(4<<TokenBuilder.SHIFT_TYPE)));
		
		if (0==(token&(1<<TokenBuilder.SHIFT_TYPE))) {//compiler does all the work.
			//not optional
			if (0==(token&(2<<TokenBuilder.SHIFT_TYPE))) { 
				return readLongUnsigned(token);
			} else {
				return readLongSigned(token);
			}
		} else {
			//optional
			if (0==(token&(2<<TokenBuilder.SHIFT_TYPE))) {
				return readLongUnsignedOptional(token, valueOfOptional);
			} else {
				return readLongSignedOptional(token, valueOfOptional);
			}	
		}
		
	}

	private FieldReaderLong longDictionary(int token) {
		
		if (0==(token&(3<<18))) {
			return readerLong;
		} else {
			//these also take an extra lookup we are optimized for the global above			
			if (0==(token&(2<<18))) {
				//AppType
				//FASTDynamic MUST know the template and therefore the type.
				//The template id is the first byte inside the group if pmap indicates.
				//that value must be read by unsignedInteger but can be done by open/close group!!
				throw new UnsupportedOperationException();
			} else {
				if (0==(token&(1<<18))) {
					//Template
					throw new UnsupportedOperationException();
				} else {
					//Custom
					throw new UnsupportedOperationException();
				}
			}
			
			
		}
	}
	
	private long readLongSignedOptional(int token, long valueOfOptional) {
		if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {
			//none, constant, delta
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//none, delta
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//none
					return  longDictionary(token).readLongSignedOptional(token,valueOfOptional);
				} else {
					//delta
					return  longDictionary(token).readLongSignedDeltaOptional(token,valueOfOptional);
				}	
			} else {
				//constant
				return  longDictionary(token).readLongSignedConstantOptional(token,valueOfOptional);
			}
			
		} else {
			//copy, default, increment
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//copy, increment
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//copy
					return  longDictionary(token).readLongSignedCopyOptional(token,valueOfOptional);
				} else {
					//increment
					return  longDictionary(token).readLongSignedIncrementOptional(token,valueOfOptional);
				}	
			} else {
				// default
				return  longDictionary(token).readLongSignedDefaultOptional(token,valueOfOptional);
			}		
		}
		
	}

	private long readLongSigned(int token) {
		if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {
			//none, constant, delta
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//none, delta
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//none
					return  longDictionary(token).readLongSigned(token);
				} else {
					//delta
					return  longDictionary(token).readLongSignedDelta(token);
				}	
			} else {
				//constant
				return  longDictionary(token).readLongSignedConstant(token);
			}
			
		} else {
			//copy, default, increment
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//copy, increment
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//copy
					return  longDictionary(token).readLongSignedCopy(token);
				} else {
					//increment
					return  longDictionary(token).readLongSignedIncrement(token);	
				}	
			} else {
				// default
				return  longDictionary(token).readLongSignedDefault(token);
			}		
		}
	}

	private long readLongUnsignedOptional(int token, long valueOfOptional) {
		if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {
			//none, constant, delta
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//none, delta
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//none
					return  longDictionary(token).readLongUnsignedOptional(token,valueOfOptional);
				} else {
					//delta
					return  longDictionary(token).readLongUnsignedDeltaOptional(token,valueOfOptional);
				}	
			} else {
				//constant
				return  longDictionary(token).readLongUnsignedConstantOptional(token,valueOfOptional);
			}
			
		} else {
			//copy, default, increment
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//copy, increment
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//copy
					return  longDictionary(token).readLongUnsignedCopyOptional(token,valueOfOptional);
				} else {
					//increment
					return  longDictionary(token).readLongUnsignedIncrementOptional(token,valueOfOptional);
				}	
			} else {
				// default
				return  longDictionary(token).readLongUnsignedDefaultOptional(token,valueOfOptional);
			}		
		}

	}

	private long readLongUnsigned(int token) {
		if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {
			//none, constant, delta
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//none, delta
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//none
					return  longDictionary(token).readLongUnsigned(token);
				} else {
					//delta
					return  longDictionary(token).readLongUnsignedDelta(token);
				}	
			} else {
				//constant
				return  longDictionary(token).readLongUnsignedConstant(token);
			}
			
		} else {
			//copy, default, increment
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//copy, increment
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//copy
					return  longDictionary(token).readLongUnsignedCopy(token);
				} else {
					//increment
					return  longDictionary(token).readLongUnsignedIncrement(token);		
				}	
			} else {
				// default
				return  longDictionary(token).readLongUnsignedDefault(token);
			}		
		}
		
	}

	@Override
	public int readInt(int id, int valueOfOptional) {
		
		int token = id>=0 ? tokenLookup[id] : id;
		if (0==(token&(1<<TokenBuilder.SHIFT_TYPE))) {//compiler does all the work.
			//not optional
			if (0==(token&(2<<TokenBuilder.SHIFT_TYPE))) { 
				return readIntegerUnsigned(token);
			} else {
				return readIntegerSigned(token);
			}
		} else {
			//optional
			if (0==(token&(2<<TokenBuilder.SHIFT_TYPE))) {
				return readIntegerUnsignedOptional(token, valueOfOptional);
			} else {
				return readIntegerSignedOptional(token, valueOfOptional);
			}	
		}		
	}

	private int readIntegerSignedOptional(int token, int valueOfOptional) {
		
		if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {
			//none, constant, delta
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//none, delta
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//none
					return readerInteger.readIntegerSignedOptional(token,valueOfOptional);
				} else {
					//delta
					return readerInteger.readIntegerSignedDeltaOptional(token,valueOfOptional);
				}	
			} else {
				//constant
				return readerInteger.readIntegerSignedConstantOptional(token,valueOfOptional);
			}
			
		} else {
			//copy, default, increment
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//copy, increment
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//copy
					return readerInteger.readIntegerSignedCopyOptional(token,valueOfOptional);
				} else {
					//increment
					return readerInteger.readIntegerSignedIncrementOptional(token,valueOfOptional);
				}	
			} else {
				// default
				return readerInteger.readIntegerSignedDefaultOptional(token,valueOfOptional);
			}		
		}
		
	}

	private int readIntegerSigned(int token) {
		if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {
			//none, constant, delta
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//none, delta
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//none
					return readerInteger.readIntegerSigned(token);
				} else {
					//delta
					return readerInteger.readIntegerSignedDelta(token);
				}	
			} else {
				//constant
				return readerInteger.readIntegerSignedConstant(token);
			}
			
		} else {
			//copy, default, increment
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//copy, increment
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//copy
					return readerInteger.readIntegerSignedCopy(token);
				} else {
					//increment
					return readerInteger.readIntegerSignedIncrement(token);	
				}	
			} else {
				// default
				return readerInteger.readIntegerSignedDefault(token);
			}		
		}
	}

	private int readIntegerUnsignedOptional(int token, int valueOfOptional) {
		if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {
			//none, constant, delta
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//none, delta
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//none
					return readerInteger.readIntegerUnsignedOptional(token,valueOfOptional);
				} else {
					//delta
					return readerInteger.readIntegerUnsignedDeltaOptional(token,valueOfOptional);
				}	
			} else {
				//constant
				return readerInteger.readIntegerUnsignedConstantOptional(token, valueOfOptional);
			}
			
		} else {
			//copy, default, increment
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//copy, increment
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//copy
					return readerInteger.readIntegerUnsignedCopyOptional(token,valueOfOptional);
				} else {
					//increment
					return readerInteger.readIntegerUnsignedIncrementOptional(token,valueOfOptional);	
				}	
			} else {
				// default
				return readerInteger.readIntegerUnsignedDefaultOptional(token,valueOfOptional);
			}		
		}
	
	}

	private int readIntegerUnsigned(int token) {
		
		if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {
			//none, constant, delta
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//none, delta
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//none
					return readerInteger.readIntegerUnsigned(token);
				} else {
					//delta
					return readerInteger.readIntegerUnsignedDelta(token);
				}	
			} else {
				//constant
				return readerInteger.readIntegerUnsignedConstant(token);
			}
			
		} else {
			//copy, default, increment
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
				//copy, increment
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//copy
					return readerInteger.readIntegerUnsignedCopy(token);
				} else {
					//increment
					return readerInteger.readIntegerUnsignedIncrement(token);
				}	
			} else {
				// default
				return readerInteger.readIntegerUnsignedDefault(token);
			}		
		}
	}

	@Override
	public int readBytes(int id) {
		
		int token = id>=0 ? tokenLookup[id] : id;
		assert(0==(token&(4<<TokenBuilder.SHIFT_TYPE)));
		assert(0!=(token&(8<<TokenBuilder.SHIFT_TYPE)));
		
		if (0==(token&(1<<TokenBuilder.SHIFT_TYPE))) {//compiler does all the work.
			return readByteArray(token);
		} else {
			return readByteArrayOptional(token);
		}
	}

	private int readByteArray(int token) {
		if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
			//none constant delta tail 
			if (0==(token&(6<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
				//none tail
				if (0==(token&(8<<TokenBuilder.SHIFT_OPER))) {
					//none
				//	System.err.println("none");
					return readerBytes.readBytes(token);
				} else {
					//tail
				//	System.err.println("tail");
					return readerBytes.readBytesTail(token);
				}
			} else {
				// constant delta
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//constant
				//	System.err.println("const");
					return readerBytes.readBytesConstant(token);
				} else {
					//delta
				//	System.err.println("delta read");
					return readerBytes.readBytesDelta(token);
				}
			}
		} else {
			//copy default
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
				//copy
				//System.err.println("copy");
				return readerBytes.readBytesCopy(token);
			} else {
				//default
				//System.err.println("default");
				return readerBytes.readBytesDefault(token);
			}
		}
	}
	
	private int readByteArrayOptional(int token) {
		if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
			//none constant delta tail 
			if (0==(token&(6<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
				//none tail
				if (0==(token&(8<<TokenBuilder.SHIFT_OPER))) {
					//none
				//	System.err.println("none");
					return readerBytes.readBytesOptional(token);
				} else {
					//tail
				//	System.err.println("tail");
					return readerBytes.readBytesTailOptional(token);
				}
			} else {
				// constant delta
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//constant
				//	System.err.println("const");
					return readerBytes.readBytesConstantOptional(token);
				} else {
					//delta
				//	System.err.println("delta read");
					return readerBytes.readBytesDeltaOptional(token);
				}
			}
		} else {
			//copy default
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
				//copy
				//System.err.println("copy");
				return readerBytes.readBytesCopyOptional(token);
			} else {
				//default
				//System.err.println("default");
				return readerBytes.readBytesDefaultOptional(token);
			}
		}
	}

	
	
	@Override
	public void openGroup(int id) {
		int token = id>=0 ? tokenLookup[id] : id;
		
		int pmapMaxSize = TokenBuilder.extractMaxBytes(token);
		if (pmapMaxSize>0) {
			reader.readPMap(pmapMaxSize);
		}
		
		if (TokenBuilder.extractType(token)==TypeMask.GroupTemplated) {
			//always push something on to the stack
			int newTop = (reader.popPMapBit()!=0) ? reader.readIntegerUnsigned() : templateStack[templateStackHead];
			templateStack[templateStackHead++] = newTop;

		}
	}

	@Override
	public void closeGroup(int id) {
		//must have same token that was used when opening the group.
		int token = id>=0 ? tokenLookup[id] : id;
		int pmapMaxSize = TokenBuilder.extractMaxBytes(token);
		if (pmapMaxSize>0) {
			reader.popPMap();
		}
		
		if (TokenBuilder.extractType(token)==TypeMask.GroupTemplated) {
			//must always pop because open will always push
			templateStackHead--;
		}
		
	}

	@Override
	public int readDecimalExponent(int id, int valueOfOptional) {
		int token = id>=0 ? tokenLookup[id] : id;
		
		assert(0==(token&(2<<TokenBuilder.SHIFT_TYPE))) : TokenBuilder.tokenToString(token);
		assert(0!=(token&(4<<TokenBuilder.SHIFT_TYPE))) : TokenBuilder.tokenToString(token);
		assert(0!=(token&(8<<TokenBuilder.SHIFT_TYPE))) : TokenBuilder.tokenToString(token);
		
		int oppExp = (token>>(TokenBuilder.SHIFT_OPER+TokenBuilder.SHIFT_OPER_DECIMAL))&TokenBuilder.MASK_OPER_DECIMAL;

		if (0==(token&(1<<TokenBuilder.SHIFT_TYPE))) {
			return readerDecimal.readDecimalExponent(token, valueOfOptional);
		} else {
			return readerDecimal.readDecimalExponentOptional(token, oppExp, valueOfOptional);
		}
	}
	

	@Override
	public long readDecimalMantissa(int id, long valueOfOptional) {
		int token = id>=0 ? tokenLookup[id] : id;
		
		assert(0==(token&(2<<TokenBuilder.SHIFT_TYPE))) : TokenBuilder.tokenToString(token);
		assert(0!=(token&(4<<TokenBuilder.SHIFT_TYPE))) : TokenBuilder.tokenToString(token);
		assert(0!=(token&(8<<TokenBuilder.SHIFT_TYPE))) : TokenBuilder.tokenToString(token);
		
		int oppMant = (token>>TokenBuilder.SHIFT_OPER)&TokenBuilder.MASK_OPER_DECIMAL;
		
		if (0==(token&(1<<TokenBuilder.SHIFT_TYPE))) {
			return readerDecimal.readDecimalMantissa(token, oppMant, valueOfOptional);
		} else {
			return readerDecimal.readDecimalMantissaOptional(token, valueOfOptional);
		}
	}

	@Override
	public int readText(int id) {
		int token = id>=0 ? tokenLookup[id] : id;
		assert(0==(token&(4<<TokenBuilder.SHIFT_TYPE)));
		assert(0!=(token&(8<<TokenBuilder.SHIFT_TYPE)));
		
		if (0==(token&(1<<TokenBuilder.SHIFT_TYPE))) {//compiler does all the work.
			if (0==(token&(2<<TokenBuilder.SHIFT_TYPE))) {
				//ascii
				//System.err.println("read ascii");
				return readTextASCII(token);
			} else {
				//utf8
				//System.err.println("read utf8");
				return readTextUTF8(token);
			}
		} else {
			if (0==(token&(2<<TokenBuilder.SHIFT_TYPE))) {
				//ascii optional
				//System.err.println("read ascii opp");
				return readTextASCIIOptional(token);
			} else {
				//utf8 optional
				//System.err.println("read utf8 opp");
				return readTextUTF8Optional(token);
			}
		}
	}

	private int readTextUTF8Optional(int token) {
		
		if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
			//none constant delta tail 
			if (0==(token&(6<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
				//none tail
				if (0==(token&(8<<TokenBuilder.SHIFT_OPER))) {
					//none
					//System.err.println("none");
					return readerChar.readUTF8Optional(token);
				} else {
					//tail
					//System.err.println("tail");
					return readerChar.readUTF8TailOptional(token);
				}
			} else {
				// constant delta
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//constant
					//System.err.println("const");
					return readerChar.readUTF8ConstantOptional(token);
				} else {
					//delta
					//System.err.println("delta");
					return readerChar.readUTF8DeltaOptional(token);
				}
			}
		} else {
			//copy default
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
				//copy
				//System.err.println("copy");
				return readerChar.readUTF8CopyOptional(token);
			} else {
				//default
				//System.err.println("default");
				return readerChar.readUTF8DefaultOptional(token);
			}
		}
		
	}

	private int readTextASCII(int token) {
		
		if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
			//none constant delta tail 
			if (0==(token&(6<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
				//none tail
				if (0==(token&(8<<TokenBuilder.SHIFT_OPER))) {
					//none
					return readerChar.readASCII(token);
				} else {
					//tail
					return readerChar.readASCIITail(token);
				}
			} else {
				// constant delta
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//constant
					return readerChar.readASCIIConstant(token);
				} else {
					//delta
					return readerChar.readASCIIDelta(token);
				}
			}
		} else {
			//copy default
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
				//copy
				return readerChar.readASCIICopy(token);
			} else {
				//default
				return readerChar.readASCIIDefault(token);
			}
		}
	}

	private int readTextUTF8(int token) {
		
		if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
			//none constant delta tail 
			if (0==(token&(6<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
				//none tail
				if (0==(token&(8<<TokenBuilder.SHIFT_OPER))) {
					//none
				//	System.err.println("none");
					return readerChar.readUTF8(token);
				} else {
					//tail
				//	System.err.println("tail");
					return readerChar.readUTF8Tail(token);
				}
			} else {
				// constant delta
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//constant
				//	System.err.println("const");
					return readerChar.readUTF8Constant(token);
				} else {
					//delta
				//	System.err.println("delta read");
					return readerChar.readUTF8Delta(token);
				}
			}
		} else {
			//copy default
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
				//copy
				//System.err.println("copy");
				return readerChar.readUTF8Copy(token);
			} else {
				//default
				//System.err.println("default");
				return readerChar.readUTF8Default(token);
			}
		}
		
	}

	private int readTextASCIIOptional(int token) {
		
		if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
			//none constant delta tail 
			if (0==(token&(6<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
				//none tail
				if (0==(token&(8<<TokenBuilder.SHIFT_OPER))) {
					//none
					return readerChar.readASCII(token);
				} else {
					//tail
					return readerChar.readASCIITailOptional(token);
				}
			} else {
				// constant delta
				if (0==(token&(4<<TokenBuilder.SHIFT_OPER))) {
					//constant
					return readerChar.readASCIIConstantOptional(token);
				} else {
					//delta
					return readerChar.readASCIIDeltaOptional(token);
				}
			}
		} else {
			//copy default
			if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {//compiler does all the work.
				//copy
				return readerChar.readASCIICopyOptional(token);
			} else {
				//default
				return readerChar.readASCIIDefaultOptional(token);
			}
		}

	}


}
