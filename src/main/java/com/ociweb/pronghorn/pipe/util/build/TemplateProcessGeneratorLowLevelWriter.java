package com.ociweb.pronghorn.pipe.util.build;

import static com.ociweb.pronghorn.pipe.util.Appendables.appendClass;
import static com.ociweb.pronghorn.pipe.util.Appendables.appendStaticCall;
import static com.ociweb.pronghorn.pipe.util.Appendables.appendValue;

import java.io.IOException;
import java.util.Arrays;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.MessageSchemaDynamic;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.stream.LowLevelStateManager;
import com.ociweb.pronghorn.pipe.util.Appendables;

public class TemplateProcessGeneratorLowLevelWriter extends TemplateProcessGenerator {
    
    private final Appendable bodyTarget;
    
    protected final String tab = "    ";
    private final boolean hasSimpleMessagesOnly; //for simple messages there is no LowLevelStateManager
    private final String stageMgrClassName = LowLevelStateManager.class.getSimpleName();
    private final String stageMgrVarName = "navState"; 
    protected final String pipeVarName;
    private final Class pipeClass = Pipe.class;
    private final StringBuilder businessExampleWorkspace = new StringBuilder();
    private int businessFieldCount;
    private int businessFirstField;
    private final StringBuilder writeToPipeSignatureWorkspace = new StringBuilder();
    private final StringBuilder writeToPipeBodyWorkspace = new StringBuilder();
    private static final String SeqCountSuffix = "Count";
    private final String cursorVarName = "cursor";
    private final String pipeId;
    private final String doNothingConstantValue = "-3";
    private final String doNothingConstant = "DO_NOTHING";
    

    private final String methodScope;
    private boolean firstField = true;
    
    
    private final String packageName = "com.ociweb.pronghorn.pipe.build";

    private final String className;
    private final String baseText;
    
    
    public TemplateProcessGeneratorLowLevelWriter(MessageSchema schema, Appendable target, String className, String baseClassName) {
        super(schema);

        this.pipeId = "1"; //NOTE: for future development when we need to merge two writers
        this.pipeVarName = "output"; //NOTE: for future development when we need to merge two writers
        
        this.className = className;
        this.baseText = baseClassName;
        this.bodyTarget = target;
        this.hasSimpleMessagesOnly = MessageSchema.from(schema).hasSimpleMessagesOnly;
        
        this.methodScope = "private"; //set to protected if you plan to extend this vs generate this.
    }
    
    public TemplateProcessGeneratorLowLevelWriter(MessageSchema schema, Appendable target) {
        this(schema, target, "LowLevelWriter", "implements Runnable");
       
    }
    
    public String getClassName() {
        return className;
    }
    
    public String getPackageName() {
        return packageName;
        
    }

    protected void defineMembers() throws IOException {
        final FieldReferenceOffsetManager from = MessageSchema.from(schema);
     
        if (!from.hasSimpleMessagesOnly) {
            bodyTarget.append("private ").append(LowLevelStateManager.class.getSimpleName()).append(" navState;\n");
        }
        appendClass(bodyTarget.append("private "), pipeClass, schema.getClass()).append(pipeVarName).append(";\n");

        additionalMembers(bodyTarget);
    }

    protected void additionalMembers(Appendable target) throws IOException {  
        final FieldReferenceOffsetManager from = MessageSchema.from(schema);
    }

    @Override
    protected void processCallerPrep() throws IOException {
      
        
        FieldReferenceOffsetManager from = MessageSchema.from(schema);
        
        from.appendGUID( bodyTarget.append("private final int[] FROM_GUID = ")).append(";\n");
        bodyTarget.append("private final long BUILD_TIME = ");
        Appendables.appendValue(bodyTarget, System.currentTimeMillis()).append("L;\n");
        bodyTarget.append("private static final int ").append(doNothingConstant).append(" = ").append(doNothingConstantValue).append(";\n");
        
        
        bodyTarget.append("\n");
        
        bodyTarget.append("protected int nextMessageIdx() {\n");        
        bodyOfNextMessageIdx(bodyTarget);        
        bodyTarget.append("}\n");
        
        bodyTarget.append("\n");
        bodyTarget.append("@Override\n");
        bodyTarget.append("public void run() {\n");

        //      if (!Pipe.hasRoomForWrite(input)) {
        //      return;
        //  }
        appendStaticCall(bodyTarget.append(tab).append("while ("), pipeClass, "hasRoomForWrite").append(pipeVarName).append(")) {\n");
        
        ///
        ///
        
        
        if (hasSimpleMessagesOnly) {
            
            //switch(cursor)) {
            bodyTarget.append(tab).append("switch(").append("nextMessageIdx()").append(") {\n");
            
            
        } else {
            bodyTarget.append(tab).append("int ").append(cursorVarName).append(";\n");
            bodyTarget.append("\n");
            //if (LowLevelStateManager.isStartNewMessage(navState)) {
            bodyTarget.append(tab).append("if (").append(stageMgrClassName).append(".isStartNewMessage(").append(stageMgrVarName).append(")) {\n");
            
            bodyTarget.append(tab).append(tab).append(cursorVarName).append(" = nextMessageIdx();\n");

            //} else {
            bodyTarget.append(tab).append("} else {\n");
            //    cursor = LowLevelStateManager.activeCursor(navState);
            bodyTarget.append(tab).append(tab).append(cursorVarName).append(" = ").append(stageMgrClassName).append(".activeCursor(").append(stageMgrVarName).append(");\n");
            //}
            bodyTarget.append(tab).append("}\n");
            
            bodyTarget.append("\n");
     //       bodyTarget.append("System.out.println(cursor);//WHYHAPPEN\n");
            
            //switch(cursor)) {
            bodyTarget.append(tab).append("switch(").append(cursorVarName).append(") {\n");
        }
        
    }

    
    //TODO: Solution to mutation of method names,
    //      Add annotation to every method we wish to use, have annotation record method name in lookup table with immutable constant id
    //      When we use the method names for code generation look them up from the immutable constant id.
    
    @Override
    protected void processCaller(int cursor) throws IOException {
        
        FieldReferenceOffsetManager from = MessageSchema.from(schema);
        
        appendCaseMsgIdConstant(bodyTarget.append(tab).append(tab).append("case "), cursor, schema).append(":\n");

        if ( FieldReferenceOffsetManager.isTemplateStart(from, cursor) ) {
            appendStaticCall(bodyTarget.append(tab).append(tab),pipeClass,"addMsgIdx").append(pipeVarName).append(',');
            Appendables.appendValue(bodyTarget, cursor).append(");\n");
        }
        
        bodyTarget.append(tab).append(tab).append(tab);
        appendBusinessMethodName(cursor).append("();\n");
                                       
                                       
        //Pipe.confirmLowLevelWrite(input, 8);
        int fragmentSizeLiteral = from.fragDataSize[cursor];
        
        appendStaticCall(bodyTarget.append(tab).append(tab).append(tab), pipeClass, "confirmLowLevelWrite").append(pipeVarName).append(", ");
        Appendables.appendValue(bodyTarget, fragmentSizeLiteral);
        bodyTarget.append("/* fragment ");
        Appendables.appendValue(bodyTarget, cursor).append("  size ");
        Appendables.appendValue(bodyTarget, from.fragScriptSize[cursor]);
        bodyTarget.append("*/);\n");
                                      
        bodyTarget.append(tab).append(tab).append("break;\n");        

    }
    
    private Appendable appendCaseMsgIdConstant(Appendable target, int cursor, MessageSchema schema) throws IOException {
        FieldReferenceOffsetManager from = MessageSchema.from(schema);

        target.append("/*");
       appendWriteMethodName(target, cursor);
       target.append("*/");
       
        if (schema instanceof MessageSchemaDynamic || null==from.fieldNameScript[cursor]) {
            Appendables.appendValue(target, cursor);
        } else {
            target.append(schema.getClass().getSimpleName()).append(".");
            target.append(FieldReferenceOffsetManager.buildMsgConstName(from, cursor));
        }
        return target;
    }

    @Override
    protected void processCallerPost() throws IOException {

        bodyTarget.append(tab).append(tab).append("case ").append(doNothingConstant).append(":\n");
        //TODO; consume message can call request shutdown.
        
        bodyTarget.append(tab).append(tab).append(tab).append("return;\n");
        
        bodyTarget.append(tab).append(tab).append("default:\n");
        
        bodyTarget.append(tab).append(tab).append(tab).append("throw new UnsupportedOperationException(\"Unknown message type ");
        if (!hasSimpleMessagesOnly) {
            bodyTarget.append("\"+").append(cursorVarName).append("+\"");
        }
        bodyTarget.append(", rebuid with the new schema.\");\n");
        
                
        bodyTarget.append(tab).append("}\n"); //close of the switch statement
               
        //Pipe.releaseReads{input);
        appendStaticCall(bodyTarget.append(tab), pipeClass, "publishWrites").append(pipeVarName).append(");\n");
                  
        bodyTarget.append(tab).append("}\n");
        
        bodyTarget.append("}\n");
        bodyTarget.append("\n"); 
        
    }
    

    @Override
    protected void processFragmentClose(int fragmentCursor) throws IOException {
        
        if (!hasSimpleMessagesOnly) {
          //  businessMethodCall();
            //LowLevelStateManager.closeFragment(navState);
            bodyTarget.append(tab).append(stageMgrClassName).append(".closeFragment(").append(stageMgrVarName).append(");\n");
        }   
    }

    @Override
    protected void processDictionary() throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    protected void processByteArrayOptional(String name, int idx, int fieldCursor, long id) throws IOException {
        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(name, "null", "ByteBuffer", fieldCursor);

        //build arg list for method signature
        appendTypeSignatureForPipeWriter(name, "ByteBuffer"); 
        
        //build the line to add value into the the pipe, this will not be modified.
        appendWriteToPipe(name, "addByteBuffer");
        
        firstField = false;
    }

    @Override
    protected void processByteArray(String name, int idx, int fieldCursor, long id) throws IOException {
        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(name, "ByteBuffer.allocate(0)", "ByteBuffer", fieldCursor);

        //build arg list for method signature
        appendTypeSignatureForPipeWriter(name, "ByteBuffer"); 
        
        //build the line to add value into the the pipe, this will not be modified.
        appendWriteToPipe(name, "addByteBuffer");
        
        firstField = false;
   
    }

    @Override
    protected void processTextUTF8Optional(String name, int idx, int fieldCursor, long id) throws IOException {
        
        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(name, "\"\"", "CharSequence", fieldCursor);
        
        //build arg list for method signature
        appendTypeSignatureForPipeWriter(name, "CharSequence"); 
        
        //build the line to add value into the the pipe, this will not be modified.
        appendWriteToPipe(name, "addUTF8");
        
        firstField = false;
        
    }

    @Override
    protected void processTextUTF8(String name, int idx, int fieldCursor, long id) throws IOException {
        
        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(name, "\"\"", "CharSequence", fieldCursor);
        
        //build arg list for method signature
        appendTypeSignatureForPipeWriter(name, "CharSequence"); 
        
        //build the line to add value into the the pipe, this will not be modified.
        appendWriteToPipe(name, "addUTF8");
        
        firstField = false;
    }

    @Override
    protected void processTextASCIIOptional(String name, int idx, int fieldCursor, long id) throws IOException {
        
        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(name, "\"\"", "CharSequence", fieldCursor);
        
        //build arg list for method signature
        appendTypeSignatureForPipeWriter(name, "CharSequence"); 
        
        //build the line to add value into the the pipe, this will not be modified.
        appendWriteToPipe(name, "addASCII");
        
        firstField = false;
        
    }

    @Override
    protected void processTextASCII(String name, int idx, int fieldCursor, long id) throws IOException {
        
        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(name, "\"\"", "CharSequence", fieldCursor);
        
        //build arg list for method signature
        appendTypeSignatureForPipeWriter(name, "CharSequence"); 
        
        //build the line to add value into the the pipe, this will not be modified.
        appendWriteToPipe(name, "addASCII");
        
        firstField = false;

    }


    @Override
    protected void processDecimalOptional(String name, int idx, int fieldCursor, long id) throws IOException {
        int nullLiteral = FieldReferenceOffsetManager.getAbsent32Value(MessageSchema.from(schema));
       
        String e = name+"E";
        String m = name+"M";
               
        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(e, "0", "int", fieldCursor);
        appendArgumentForBusinessCall(m, "0", "long", fieldCursor);
        
        //build arg list for method signature
        appendTypeSignatureForPipeWriter(e, "int");
        appendTypeSignatureForPipeWriter(m, "long");   
        
        //build the line to add value into the the pipe, this will not be modified.
        appendWirteOptionalToPipe(e, nullLiteral, "addIntValue");
        appendWriteToPipe(m, "addLongValue");
        
        firstField = false;

    }

    @Override
    protected void processDecimal(String name, int idx, int fieldCursor, long id) throws IOException {

        String e = name+"E";
        String m = name+"M";
        
        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(e, "0", "int", fieldCursor);
        appendArgumentForBusinessCall(m, "0", "long", fieldCursor);
        
        //build arg list for method signature
        appendTypeSignatureForPipeWriter(e, "int");
        appendTypeSignatureForPipeWriter(m, "long");   
        
        //build the line to add value into the the pipe, this will not be modified.
        appendWriteToPipe(e, "addIntValue"); 
        appendWriteToPipe(m, "addLongValue");
        
        firstField = false;

    }

    @Override
    protected void processLongUnsignedOptional(String name, int idx, int fieldCursor, long id) throws IOException {
        long nullLiteral = FieldReferenceOffsetManager.getAbsent64Value(MessageSchema.from(schema));

        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(name, "null", "Long", fieldCursor);
        
        //build arg list for method signature
        appendTypeSignatureForPipeWriter(name, "Long");
        
        //    Pipe.addIntValue(null==source? nullLiteral : source, output);
        //build the line to add value into the the pipe, this will not be modified.
        appendWirteOptionalToPipe(name, nullLiteral, "addLongValue");
        
        firstField = false;
    }

    @Override
    protected void processLongSignedOptional(String name, int idx, int fieldCursor, long id) throws IOException {
        long nullLiteral = FieldReferenceOffsetManager.getAbsent64Value(MessageSchema.from(schema));

        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(name, "null", "Long", fieldCursor);
        
        //build arg list for method signature
        appendTypeSignatureForPipeWriter(name, "Long");
        
        //    Pipe.addIntValue(null==source? nullLiteral : source, output);
        //build the line to add value into the the pipe, this will not be modified.
        appendWirteOptionalToPipe(name, nullLiteral, "addLongValue");
        
        firstField = false;
    }

    @Override
    protected void processLongUnsigned(String name, int idx, int fieldCursor, long id) throws IOException {

        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(name, "0", "long", fieldCursor);
        
        //build arg list for method signature
        appendTypeSignatureForPipeWriter(name, "long");
        
        //build the line to add value into the the pipe, this will not be modified.
        appendWriteToPipe(name, "addLongValue");
        
        firstField = false;
    }

    @Override
    protected void processLongSigned(String name, int idx, int fieldCursor, long id) throws IOException {

        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(name, "0", "long", fieldCursor);
        
        //build arg list for method signature
        appendTypeSignatureForPipeWriter(name, "long");   
        
        //build the line to add value into the the pipe, this will not be modified.
        appendWriteToPipe(name, "addLongValue");
        
        firstField = false;
   
    }

    @Override
    protected void processIntegerUnsignedOptional(String name, int i, int fieldCursor, long id) throws IOException {
        int nullLiteral = FieldReferenceOffsetManager.getAbsent32Value(MessageSchema.from(schema));

        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(name, "null", "Integer", fieldCursor);
        
        //build arg list for method signature
        appendTypeSignatureForPipeWriter(name, "Integer"); 
        
        //    Pipe.addIntValue(null==source? nullLiteral : source, output);
        //build the line to add value into the the pipe, this will not be modified.
        appendWirteOptionalToPipe(name, nullLiteral, "addIntValue");
        
        firstField = false;
    }
   
    @Override
    protected void processIntegerSignedOptional(String name, int i, int fieldCursor, long id) throws IOException {
        
        int nullLiteral = FieldReferenceOffsetManager.getAbsent32Value(MessageSchema.from(schema));

        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(name, "null", "Integer", fieldCursor);
        
        //build arg list for method signature
        appendTypeSignatureForPipeWriter(name, "Integer");  
        
        //    Pipe.addIntValue(null==source? nullLiteral : source, output);
        //build the line to add value into the the pipe, this will not be modified.
        appendWirteOptionalToPipe(name, nullLiteral, "addIntValue");
        
        firstField = false;
    }

    @Override
    protected void processIntegerUnsigned(String name, int i, int fieldCursor, long id) throws IOException {
        
        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(name, "0", "int", fieldCursor);
        
        //build arg list for method signature
        appendTypeSignatureForPipeWriter(name, "int");
        
        //build the line to add value into the the pipe, this will not be modified.
        appendWriteToPipe(name, "addIntValue"); 
        
        firstField = false;
    }
    
    @Override
    protected void pronghornIntegerSigned(String name, int i, int fieldCursor, long id) throws IOException {
        
        //build the argument for calling, this will be modified for specific business logic.
        appendArgumentForBusinessCall(name, "0", "int", fieldCursor);
        
        //build arg list for method signature
        appendTypeSignatureForPipeWriter(name, "int");        
        
        //build the line to add value into the the pipe, this will not be modified.
        appendWriteToPipe(name, "addIntValue");
        
        firstField = false;
    }


    private void appendArgumentForBusinessCall(String name, String defaultValue, String type, int fieldCursor) throws IOException {
        businessFieldCount++;
        if (businessFirstField<0) {
            businessFirstField = fieldCursor;
        }
        appendVar(appendComma(businessExampleWorkspace.append(tab).append(tab).append(tab)).append(defaultValue).append(" /*"),name).append("*/\n");
    }

    private void appendWriteToPipe(String name, String method) throws IOException {
        appendVar(appendStaticCall(writeToPipeBodyWorkspace.append(tab), pipeClass, method), name).append(',').append(pipeVarName).append(");\n");
    }

    private void appendTypeSignatureForPipeWriter(String name, String type) throws IOException {
        appendVar(appendComma(writeToPipeSignatureWorkspace).append(type).append(' '),name);
    }

    private void appendWirteOptionalToPipe(String name, long nullLiteral, String methodName) throws IOException {
        appendVar(               
                appendValue( 
                        appendVar(
                                appendStaticCall(writeToPipeBodyWorkspace, pipeClass, methodName).append("null=="),name).append("?"), nullLiteral).append("L:"), name).
        append(',').
        append(pipeVarName).
        append(");\n");
    }

    private Appendable appendComma(Appendable target) throws IOException {
        if (!firstField) {
            target.append(',');            
        } else {
            target.append(' ');
        }
        return target;
    }
        
    private <A extends Appendable> A appendVar(A target, String name) throws IOException {
        return (A)target.append('p').append(name.replace(' ', '_')); //TODO: this replacement code should be doen in Appendable.
    }
    
    protected Appendable appendWriteMethodName(Appendable target, int cursor) throws IOException {
        return appendFragmentName(target.append("processPipe").append(pipeId).append("Write"), cursor);
    }
    
    private Appendable appendBusinessMethodName(int cursor) throws IOException {
        return appendFragmentName(bodyTarget.append("process"), cursor);
    }
    
    private Appendable appendFragmentName(Appendable target, int cursor) throws IOException {
        FieldReferenceOffsetManager from = MessageSchema.from(schema);
        if (null!=from.fieldNameScript[cursor]) {
            //if this is NOT a message start then also prefix with its name
            if ( Arrays.binarySearch(from.messageStarts, cursor)<0) {
                appendMessageName(target, cursor, from);
            }
            target.append(from.fieldNameScript[cursor]);
        } else {
            //Go Up and find parent name then add cursor position to make it unique
            int msgCursor = appendMessageName(target, cursor, from);
            appendSequenceName(cursor, from, msgCursor);
            target.append("End");
        }
        return target;
    }

    private void appendSequenceName(int cursor, FieldReferenceOffsetManager from, int msgCursor) throws IOException {
        int j = cursor;
        final int depth = from.fragDepth[j];
        String foundName = null;
        while (--j>=msgCursor) {
            if (from.fragDepth[j]==depth) {
                if (null!=from.fieldNameScript[j]) {
                    foundName = from.fieldNameScript[j];
                }
            }
        }
        bodyTarget.append(foundName);
    }

    private int appendMessageName(Appendable target, int cursor, FieldReferenceOffsetManager from) throws IOException {
        int i = cursor;
        while (--i>=0 && (null==from.fieldNameScript[i] || Arrays.binarySearch(from.messageStarts, i)<0)  ) {}            
        target.append(from.fieldNameScript[i]);
        return i;
    }

    @Override //group length, last field in fragment and marks start of new sequence
    protected void processSequenceOpen(int fragmentCursor, String name, int idx, int fieldCursor, long id) throws IOException {
        if (hasSimpleMessagesOnly) {
            
            throw new UnsupportedOperationException();
            
        } else {
               CharSequence varName = appendVar(new StringBuilder(), name);  
               businessFieldCount++;
               if (businessFirstField<0) {
                   businessFirstField = fieldCursor;
               }
               appendSequenceCounterVar(appendComma(businessExampleWorkspace.append(tab).append(tab).append(tab)).append("0").append(" /*"),varName).append("*/\n");
               
               appendSequenceCounterVar(appendComma(writeToPipeSignatureWorkspace).append("int").append(' '),varName);            

                if (0!=id) {
                    writeToPipeBodyWorkspace.append("/* id:").append(Long.toString(id));
                    writeToPipeBodyWorkspace.append("  */");
                }
                writeToPipeBodyWorkspace.append("\n");
                
                appendSequenceCounterVar(appendStaticCall(writeToPipeBodyWorkspace.append(tab), pipeClass, "addIntValue"), varName).append(',').append(pipeVarName).append(");\n"); //hacktest.
                
                writeToPipeBodyWorkspace.append(tab).append(stageMgrClassName).append(".processGroupLength(").append(stageMgrVarName);                
                Appendables.appendValue(writeToPipeBodyWorkspace.append(", "), fragmentCursor).append(", ");
                appendSequenceCounterVar(writeToPipeBodyWorkspace, varName).append(");\n");

            firstField = false;

            
        }
    }


    private Appendable appendSequenceCounterVar(Appendable target, CharSequence varName) throws IOException {
        return target.append(varName).append(SeqCountSuffix);
    }
    
    @Override //set cursor after end of sequence
    protected void postProcessSequence(int fieldCursor) throws IOException {
        if (hasSimpleMessagesOnly) {            
            throw new UnsupportedOperationException();            
        } else {        
            writeToPipeBodyWorkspace.append(tab).append(stageMgrClassName).append(".continueAtThisCursor(").append(stageMgrVarName).append(", ");
            writeToPipeBodyWorkspace.append("/*");
            appendWriteMethodName(writeToPipeBodyWorkspace, fieldCursor);
            writeToPipeBodyWorkspace.append("*/");
            writeToPipeBodyWorkspace.append(Integer.toString(fieldCursor)).append(");\n");
        }
    }

    @Override 
    protected void processMessageClose(String name, long id, boolean needsToCloseFragment) throws IOException {
        if (!hasSimpleMessagesOnly && needsToCloseFragment) {    
            //businessMethodCall(); //DO we need this hook?
            writeToPipeBodyWorkspace.append(tab).append(stageMgrClassName).append(".closeFragment(").append(stageMgrVarName).append(");\n");  
        }
    }

    @Override //count down for end of sequence
    protected boolean processSequenceInstanceClose(String name, long id, int fieldCursor) throws IOException {

        if (hasSimpleMessagesOnly) {            
            throw new UnsupportedOperationException();            
        } else { 
            
         //   businessMethodCall(); //DO we need this hook?
            
            writeToPipeBodyWorkspace.append(tab).append("if (!").append(stageMgrClassName).append(".closeSequenceIteration(").append(stageMgrVarName).append(")) {\n");
            writeToPipeBodyWorkspace.append(tab).append(tab).append("return; /* Repeat this fragment*/\n");
            writeToPipeBodyWorkspace.append(tab).append("}\n");
            
         //   processEndOfSequence(name,id); ///DO we need this hook?
                
            writeToPipeBodyWorkspace.append(tab).append(stageMgrClassName).append(".closeFragment(").append(stageMgrVarName).append(");\n");
            
        }
        
        //this is fixed because we are doing code generation
        return false;
        
        
    }

    @Override //fragment group open, not called for message open
    protected void processFragmentOpen(String name, int fieldCursor, long id) throws IOException {
    }

    @Override //This is the beginning of a new fragment
    protected void processCalleeOpen(int cursor) throws IOException {
                
        firstField = true;
        writeToPipeSignatureWorkspace.setLength(0);
        writeToPipeBodyWorkspace.setLength(0);
        businessExampleWorkspace.setLength(0);
        businessFieldCount = 0;
        businessFirstField = -1;
        
    }
    
    
    @Override //this is the end of a fragment
    protected void processCalleeClose(int cursor) throws IOException {
        
        
        bodyTarget.append(methodScope).append(" void ");
        appendBusinessMethodName(cursor).append("() {\n");
        
        bodyOfBusinessProcess(bodyTarget, cursor, businessFirstField, businessFieldCount);
        
        bodyTarget.append("}\n");
        bodyTarget.append('\n');
        
        
        bodyTarget.append(methodScope).append(" void ");
        appendWriteMethodName(bodyTarget, cursor).append("(").append(writeToPipeSignatureWorkspace).append(") {\n");
        bodyTarget.append(writeToPipeBodyWorkspace);
        bodyTarget.append("}\n");
        bodyTarget.append('\n');
        
    }

    @Override
    protected void headerConstruction() throws IOException {
        bodyTarget.append("package ").append(packageName).append(";\n");
        
        bodyTarget.append("import com.ociweb.pronghorn.pipe.stream.LowLevelStateManager;\n");
        bodyTarget.append("import com.ociweb.pronghorn.pipe.Pipe;\n");
        bodyTarget.append("import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;\n");
        bodyTarget.append("import com.ociweb.pronghorn.pipe.util.Appendables;\n");
        bodyTarget.append("import com.ociweb.pronghorn.pipe.MessageSchemaDynamic;\n");
        additionalImports(bodyTarget);
        
        defineClassAndConstructor();
    }
    
    private void defineClassAndConstructor() throws IOException {
        bodyTarget.append("public class ").append(className).append(" ").append(baseText).append(" {\n");
        bodyTarget.append("\n");
        
        buildConstructors(bodyTarget, className);
        
    }
    
    protected void buildConstructors(Appendable target, String className) throws IOException {
    }

    protected void additionalImports(Appendable target) throws IOException {
    }

    @Override
    protected void footerConstruction() throws IOException {
        
        final FieldReferenceOffsetManager from = MessageSchema.from(schema);
        if (!from.hasSimpleMessagesOnly) {
            if (!baseText.contains("Runnable")) {
                bodyTarget.append("@Override\n");
            }
            bodyTarget.append("public void startup() {\n");
            bodyTarget.append(tab).append("navState").append(" = new ");
            bodyTarget.append(LowLevelStateManager.class.getSimpleName()).append("(").append(pipeVarName).append(");\n");
            bodyTarget.append("}\n");
        }
        
        additionalMethods(bodyTarget);
        bodyTarget.append("};\n");
    }   

    protected void additionalMethods(Appendable target) throws IOException {
        target.append("private void requestShutdown() {};\n"); //only here so generated code passes compile.
    }
    
    protected void bodyOfNextMessageIdx(Appendable target) throws IOException {
        target.append(tab).append("/* Override as needed and put your business specific logic here */\n");                
        target.append(tab).append("return ").append(doNothingConstant).append(";\n");
    }
    
    protected void bodyOfBusinessProcess(Appendable target, int cursor, int firstField, int fieldCount) throws IOException {
        target.append('\n');
        target.append(tab).append("/* Override as needed and put your business specific logic here */\n");
        target.append('\n');

        appendWriteMethodName(target.append(tab), cursor).append("(\n");        
        
        target.append(businessExampleWorkspace);//needs to be exposed for write
        
        target.append(tab).append(");\n");
    }

    
}