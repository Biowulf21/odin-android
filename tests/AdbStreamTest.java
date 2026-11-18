package io.github.muntashirakon.adb;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import sun.misc.Unsafe;

/** Regression fixture initializes only real stream read state, without Android sockets. */
public final class AdbStreamTest {
    static void field(Object object,String name,Object value)throws Exception{
        Field field=AdbStream.class.getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    static AdbStream stream()throws Exception{
        Field field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        AdbStream stream=(AdbStream)((Unsafe)field.get(null)).allocateInstance(AdbStream.class);
        field(stream,"mReadQueue",new ConcurrentLinkedQueue<byte[]>());
        field(stream,"mReadBuffer",ByteBuffer.allocate(4096).flip());
        return stream;
    }
    public static void main(String[] args)throws Exception{
        AdbStream stream=stream();
        stream.addPayload("done".getBytes("UTF-8"));
        stream.notifyClose(true);
        InputStream input=stream.openInputStream();
        byte[] bytes=new byte[32];
        if(input.read(bytes)!=4)throw new AssertionError("Queued command output lost");
        ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r);t.setDaemon(true);return t;});
        Future<Integer> end=worker.submit(()->input.read(bytes));
        try{
            if(end.get(500,TimeUnit.MILLISECONDS)!=-1)throw new AssertionError("Closed command must return EOF");
        }catch(TimeoutException error){throw new AssertionError("Reader waits forever after command completed",error);}
        finally{end.cancel(true);worker.shutdownNow();}
        System.out.println("ADB completed-command EOF regression passed");
    }
}
