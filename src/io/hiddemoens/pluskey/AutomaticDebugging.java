package io.hiddemoens.pluskey;
import java.util.function.BooleanSupplier;
final class AutomaticDebugging {
    interface Toggle { void set(boolean enabled)throws Exception; }
    interface Work { void run()throws Exception; }
    static void run(BooleanSupplier managed,Toggle toggle,Work work)throws Exception {
        boolean startedManaged=managed.getAsBoolean();
        if (startedManaged) toggle.set(true);
        Exception failure = null;
        try { work.run(); }
        catch (Exception error) { failure = error; throw error; }
        finally {
            if (startedManaged || managed.getAsBoolean()) {
                try { toggle.set(false); }
                catch (Exception cleanup) { if (failure != null) failure.addSuppressed(cleanup); else throw cleanup; }
            }
        }
    }
}
