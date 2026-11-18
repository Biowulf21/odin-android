package io.hiddemoens.pluskey;
import java.util.concurrent.atomic.AtomicBoolean;
public final class AutomaticDebuggingTest {
    public static void main(String[] args)throws Exception{
        AtomicBoolean enabled=new AtomicBoolean(false);
        AtomicBoolean managed=new AtomicBoolean(true);
        AutomaticDebugging.run(managed::get,enabled::set,()->{
            CoreTest.check(enabled.get(),"Wireless debugging must be on before connection");
        });
        CoreTest.check(!enabled.get(),"Wireless debugging must be off after ready");
        try{
            AutomaticDebugging.run(managed::get,enabled::set,()->{throw new Exception("startup failed");});
            throw new AssertionError("Startup failure must propagate");
        }catch(Exception expected){CoreTest.check("startup failed".equals(expected.getMessage()),"original failure retained");}
        CoreTest.check(!enabled.get(),"Failed startup must not leave debugging enabled");
        managed.set(false);enabled.set(true);
        AutomaticDebugging.run(managed::get,value->{throw new AssertionError("Manual mode must not change settings");},()->{});
        CoreTest.check(enabled.get(),"Manual mode preserves existing debugging setting");
        enabled.set(true);
        AutomaticDebugging.run(managed::get,enabled::set,()->managed.set(true));
        CoreTest.check(!enabled.get(),"Permission granted during first setup enables cleanup");
        managed.set(true);enabled.set(false);
        AutomaticDebugging.run(managed::get,enabled::set,()->managed.set(false));
        CoreTest.check(!enabled.get(),"Turning automatic mode off mid-start must still clean up connection");
        System.out.println("Automatic debugging lifecycle tests passed");
    }
}
