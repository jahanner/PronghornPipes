package com.ociweb.jfast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.ociweb.jfast.error.FASTException;
import com.ociweb.jfast.generator.DispatchLoader;
import com.ociweb.jfast.generator.FASTClassLoader;
import com.ociweb.jfast.loader.ClientConfig;
import com.ociweb.jfast.loader.FieldReferenceOffsetManager;
import com.ociweb.jfast.loader.TemplateCatalogConfig;
import com.ociweb.jfast.loader.TemplateLoader;
import com.ociweb.jfast.primitive.PrimitiveReader;
import com.ociweb.jfast.primitive.adapter.FASTInputByteArray;
import com.ociweb.jfast.primitive.adapter.FASTInputByteBuffer;
import com.ociweb.jfast.primitive.adapter.FASTInputStream;
import com.ociweb.jfast.stream.FASTDecoder;
import com.ociweb.jfast.stream.FASTInputReactor;
import com.ociweb.jfast.stream.FASTListener;
import com.ociweb.jfast.stream.FASTReaderInterpreterDispatch;
import com.ociweb.jfast.stream.FASTRingBuffer;
import com.ociweb.jfast.stream.FASTRingBufferReader;
import com.ociweb.jfast.stream.RingBuffers;

public class Test {

    public static void main(String[] args) {
        
        //this example uses the preamble feature
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setPreableBytes((short)4);
        String templateSource = "/performance/example.xml";
        String dataSource = "/performance/complex30000.dat";
        
        new Test().decode(clientConfig, templateSource, dataSource);
    }
    
    public void decode(ClientConfig clientConfig, String templateSource, String dataSource) {
         int count = 1024000;
                
                  
          byte[] catBytes = buildRawCatalogData(clientConfig, templateSource);

          TemplateCatalogConfig catalog = new TemplateCatalogConfig(catBytes); 
          int maxPMapCountInBytes = TemplateCatalogConfig.maxPMapCountInBytes(catalog);             
          
          
          FASTClassLoader.deleteFiles();
          FASTDecoder readerDispatch = DispatchLoader.loadDispatchReader(catBytes);
    //      FASTDecoder readerDispatch = new FASTReaderInterpreterDispatch(catBytes);
          
          final AtomicInteger msgs = new AtomicInteger();
          
        

          
          int queuedBytes = 10;

          int iter = count;
          while (--iter >= 0) {
              FASTInputStream fastInputStream = new FASTInputStream(testDataInputStream(dataSource));
              PrimitiveReader reader = new PrimitiveReader(1024*1024*2,fastInputStream, maxPMapCountInBytes);
              
              PrimitiveReader.fetch(reader);//Pre-load the file so we only count the parse time.
                            
              FASTInputReactor reactor = new FASTInputReactor(readerDispatch, reader);
                            
              msgs.set(0);                         
              
              double duration = singleThreadedExample(readerDispatch, msgs, reactor);
            //  double duration = multiThreadedExample(readerDispatch, msgs, reactor, reader);
              
                            
              if (shouldPrint(iter)) {
                  printSummary(msgs.get(), queuedBytes, duration, fastInputStream.totalBytes()); 
              }

              //reset the dictionary to run the test again.
              readerDispatch.reset(catalog.dictionaryFactory());

          }

      }

    private double singleThreadedExample(FASTDecoder readerDispatch, final AtomicInteger msgs, FASTInputReactor reactor) {
        
        double start = System.nanoTime();
          
          /////////////////////////////////////
          //Example of single threaded usage
          /////////////////////////////////////
          boolean ok = true;
          int bufId;
          while (ok) {
              switch (bufId = FASTInputReactor.pump(reactor)) {
                  case -1:
                      ok = false;
                      break;
                  default:
                      FASTRingBuffer rb = RingBuffers.get(readerDispatch.ringBuffers,bufId);
                      
                      FASTRingBuffer.moveNext(rb);
                      
                      if (rb.isNewMessage) {
                          msgs.incrementAndGet();
                      }
                      
                      //your usage of these fields would go here.                      
                      
                      break;
              }
              
          }
          //System.err.println(".");
          //////////////////////////////////
          //End of single threaded example
          //////////////////////////////////
          
          double duration = System.nanoTime() - start;
        return duration;
    }
    
    private double multiThreadedExample(FASTDecoder readerDispatch, final AtomicInteger msgs, FASTInputReactor reactor, PrimitiveReader reader) {
        
        ThreadPoolExecutor executor = (ThreadPoolExecutor)Executors.newFixedThreadPool(1);//number of reactors to support 
        
        int a =0;
        
        double start = System.nanoTime();
          
            reactor.start(executor, reader);
            
            FASTRingBuffer rb = RingBuffers.get(readerDispatch.ringBuffers,0);
            int j = 0;
            long rp = rb.remPos.value;
            do {
                
                //TODO: B, fix async use to ensure it does not get ahead of its self
//                if (FASTRingBuffer.moveNext(rb)) {
//                    int x = FASTRingBufferReader.readInt(rb, 0); //How much data is there to read? must read it
//                    
//                };
                
                ///TODO: we are checking on zero change way too often!!
                //need to be notified of add count change? eg lock.
                int limit = (int)FASTRingBuffer.contentRemaining(rb);
                if (limit>0) {
                  //  FASTRingBuffer.moveNext(rb);

                    while (--limit>=0 ) {
                           int x = FASTRingBufferReader.readInt(rb, j++);
                           //read all the data
                           rp++;    
                    }
                    if (j>60) {//estimated field size
                        rb.removeForward2(rp);
                        j=0;
                    }
                    
                }
            }while (!executor.isShutdown());
    
          
          double duration = System.nanoTime() - start;
        return duration;
    }
    //TODO: C, need test for optional groups this is probably broken. 

    private boolean shouldPrint(int iter) {
        return (0x7F & iter) == 0;
    }

    private void printSummary(int msgs, int queuedBytes, double duration, long totalTestBytes) {
        int ns = (int) duration;
          float mmsgPerSec = (msgs * (float) 1000l / ns);
          float nsPerByte = (ns / (float) totalTestBytes);
          int mbps = (int) ((1000l * totalTestBytes * 8l) / ns);

          System.err.println("Duration:" + ns + "ns " + " " + mmsgPerSec + "MM/s " + " " + nsPerByte + "nspB "
                  + " " + mbps + "mbps " + " In:" + totalTestBytes + " Out:" + queuedBytes + " pct "
                  + (totalTestBytes / (float) queuedBytes) + " Messages:" + msgs);
    }
    
    static FASTInputByteArray buildInputForTestingByteArray(File fileSource) {
        byte[] fileData = null;
        try {
            // do not want to time file access so copy file to memory
            fileData = new byte[(int) fileSource.length()];
            FileInputStream inputStream = new FileInputStream(fileSource);
            int readBytes = inputStream.read(fileData);
            inputStream.close();
            assert(fileData.length==readBytes);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        FASTInputByteArray fastInput = new FASTInputByteArray(fileData);
        return fastInput;
    }
    
    private static InputStream testDataInputStream(String resource) {
        
        InputStream resourceInput = Test.class.getResourceAsStream(resource);
        if (null!=resourceInput) {
            return resourceInput;            
        }
        
        try {
            return new FileInputStream(new File(resource));
        } catch (FileNotFoundException e) {
            throw new FASTException(e);
        }
    }

    private static byte[] buildRawCatalogData(ClientConfig clientConfig, String source) {


        ByteArrayOutputStream catalogBuffer = new ByteArrayOutputStream(4096);
        try {
            TemplateLoader.buildCatalog(catalogBuffer, source, clientConfig);
        } catch (Exception e) {
            e.printStackTrace();
        }

        assert(catalogBuffer.size() > 0);

        byte[] catalogByteArray = catalogBuffer.toByteArray();
        return catalogByteArray;
    }

}