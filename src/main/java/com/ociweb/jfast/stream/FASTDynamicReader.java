//Copyright 2013, Nathan Tippy
//See LICENSE file for BSD license details.
//Send support requests to http://www.ociweb.com/contact
package com.ociweb.jfast.stream;

import com.ociweb.jfast.field.TokenBuilder;
import com.ociweb.jfast.primitive.PrimitiveReader;

/*
 * Implementations of read can use this object
 * to pull the most recent parsed values of any available fields.
 * Even those in outer groups may be read however values appearing in the template
 * after the <groupId> will not have been read yet and are not available.
 * If those values are needed wait until this method is called with the
 * desired surrounding <groupId>.
 * 
 * Supports dynamic modification of the templates including:  
 * 		Field compression/operation type changes.
 * 	    Field order changes within a group.
 *      Mandatory/Optional field designation.
 *      Pulling up fields from group to the surrounding group.
 *      Pushing down fields from group to the internal group.
 * 
 * In some cases after modification the data will no longer be available
 * and unexpected results can occur.  Caution must be used whenever pulling up
 * or pushing down fields as it probably changes the meaning of the data. 
 * 
 */
public class FASTDynamicReader implements FASTDataProvider {

    private final FASTDecoder readerDispatch;

    private final byte[] preamble;
    
    private long messageCount = 0;
    // the smaller the better to make it fit inside the cache.
    private final FASTRingBuffer ringBuffer;

    // When setting neededSpace
    // Worst case scenario is that this is full of decimals which each need 3.
    // but for easy math we will use 4, will require a little more empty space
    // in buffer
    // however we will not need a lookup table
    int neededSpaceOrTemplate = -1;
    int lastCapacity = 0;
    private PrimitiveReader reader;

    // read groups field ids and build repeating lists of tokens.

    // only look up the most recent value read and return it to the caller.
    public FASTDynamicReader(FASTDecoder dispatch, PrimitiveReader reader) {
        
        this.preamble = new byte[dispatch.preambleDataLength];
        this.readerDispatch = dispatch;
        this.reader = reader;
        this.ringBuffer = dispatch.ringBuffer();
        this.lastCapacity = ringBuffer.availableCapacity();

    }

    public void reset(boolean clearData) {
        this.messageCount = 0;
        this.readerDispatch.activeScriptCursor = 0;
        this.readerDispatch.activeScriptLimit = 0;
        if (clearData) {
            this.readerDispatch.reset();
        }
    }

    public long messageCount() {
        return messageCount;
    }

    public String toBinary(byte[] input) {
        StringBuilder builder = new StringBuilder();
        for (byte b : input) {
            builder.append(Integer.toBinaryString(0xFF & b)).append(",");
        }
        return builder.toString();
    }

    /**
     * Read up to the end of the next sequence or message (eg. a repeating
     * group)
     * 
     * Rules for making client compatible changes to templates. - Field can be
     * demoted to more general common value before the group. - Field can be
     * promoted to more specific value inside sequence - Field order inside
     * group can change but can not cross sequence boundary. - Group boundaries
     * can be added or removed.
     * 
     * Note nested sequence will stop once for each of the sequences therefore
     * at the bottom hasMore may not have any new data but is only done as a
     * notification that the loop has completed.
     * 
     * @return
     */

    // TODO: X, needs optimization, has more takes up too much time in profiler,
    // must allow for inline of hasMore!
    // TODO: B, Check support for group that may be optional
    public int hasMore() {
        // start new script or detect that the end of the data has been reached
        FASTRingBuffer rb = ringBuffer;
        if (neededSpaceOrTemplate < 0) {
            
            // checking EOF first before checking for blocked queue
            if (PrimitiveReader.isEOF(reader)) { //replaced with 30001==messageCount and found this method is NOT expensive
                //System.err.println(messageCount);
                return 0;
            }
            // must have room to store the new template
            int req = readerDispatch.preambleDataLength + 1;
            if ((lastCapacity < req) && ((lastCapacity = rb.maxSize-(rb.addPos-rb.remPos)) < req)) {
                return 0x80000000;
            }
            neededSpaceOrTemplate=hasMoreNextMessage(req, readerDispatch, reader);
        }
        
        
        if (neededSpaceOrTemplate > 0) {
            if ((lastCapacity < neededSpaceOrTemplate)
                    && ((lastCapacity = rb.maxSize-(rb.addPos-rb.remPos)) < neededSpaceOrTemplate)) {
                return 0x80000000;
            }
            lastCapacity -= neededSpaceOrTemplate;
        }
        
        // returns true for end of sequence or group
        return readerDispatch.decode(reader) ? sequence(readerDispatch, rb) : finishTemplate(ringBuffer, reader);
    }

    private final int sequence(FASTDecoder decoder, FASTRingBuffer rb) {
        rb.unBlockSequence();//expensive call change to static?
        if (decoder.jumpSequence >= 0) {
            return processSequence(decoder, reader);
        }
        return finishTemplate(ringBuffer, reader);
    }

    private int hasMoreNextMessage(int req, FASTDecoder readerDispatch, PrimitiveReader reader) {
        lastCapacity -= req;

        // get next token id then immediately start processing the script
        // /read prefix bytes if any (only used by some implementations)
        if (readerDispatch.preambleDataLength != 0) {
            assert (readerDispatch.gatherReadData(reader, "Preamble"));
            PrimitiveReader.readByteData(preamble, 0, preamble.length, reader);

            int i = 0;
            int s = preamble.length;
            while (i < s) {// TODO: B, convert this to use ByteArray ring buffer
                ringBuffer.appendInt1(((0xFF & preamble[i++]) << 24) | ((0xFF & preamble[i++]) << 16)
                        | ((0xFF & preamble[i++]) << 8) | ((0xFF & preamble[i++])));
            }

        };
        // /////////////////
        // open message (special type of group)
        int templateId = PrimitiveReader.openMessage(readerDispatch.maxTemplatePMapSize, reader);
        if (templateId >= 0) {
            messageCount++;
        }
        int i = templateId;// write template id at the beginning of this message
        ringBuffer.appendInt1(i);
        
        return readerDispatch.requiredBufferSpace(i);
        

    }

    private final int finishTemplate(FASTRingBuffer ringBuffer, PrimitiveReader reader) {
        // reached the end of the script so close and prep for the next one
        ringBuffer.unBlockMessage();
        neededSpaceOrTemplate = -1;
        PrimitiveReader.closePMap(reader);
        return 2;// finished reading full message
    }

    private final int processSequence(FASTDecoder decoder, PrimitiveReader reader) {
        int i = decoder.jumpSequence;
        if (i > 0) { // jumping (backward) to do this sequence again.
            neededSpaceOrTemplate = 1 + (i << 2);
            decoder.activeScriptCursor -= i;
            return 1;// has sequence group to read
        } else {
            // finished sequence, no need to jump
            if (++decoder.activeScriptCursor == decoder.activeScriptLimit) {
                neededSpaceOrTemplate = -1;
                PrimitiveReader.closePMap(reader);
                return 3;// finished reading full message and the sequence
            }
            return 1;// has sequence group to read
        }
    }

    public byte[] prefix() {
        return preamble;
    }

    public FASTRingBuffer ringBuffer() {
        return ringBuffer;
    }

}
