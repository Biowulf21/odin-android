package io.hiddemoens.pluskey;
import java.io.File;
import java.nio.file.Files;
public final class MailboxTest {
    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("pluslens-test-").toFile();
        Mailbox box = new Mailbox(dir);
        CoreTest.check(box.readStatus(1000) == null, "missing helper is unavailable");
        box.publish("Listening|1|Google Lens opened", 1000);
        CoreTest.check("Listening|1|Google Lens opened".equals(box.readStatus(2000)), "fresh heartbeat reports running");
        CoreTest.check(box.readStatus(8001) == null, "dead helper heartbeat expires");
        CoreTest.check(box.readStatus(999) == null, "future heartbeat rejected");
        box.requestStop();
        CoreTest.check(box.stopRequested(), "app stop request reaches helper");
        box.clearStop();
        CoreTest.check(!box.stopRequested(), "restarted helper clears old stop request");
        box.requestPreview(false);CoreTest.check(box.consumePreview()==1,"short action preview reaches helper");
        CoreTest.check(box.consumePreview()==0,"preview not repeated");
        box.requestPreview(true);CoreTest.check(box.consumePreview()==2,"long action preview reaches helper");
        for (File file : dir.listFiles()) file.delete();
        dir.delete();
        System.out.println("Mailbox lifecycle tests passed");
    }
}
