package io.hiddemoens.pluskey;
import java.io.*;
final class Mailbox {
    static final String STOP_REQUEST = "helper-stop";
    static final String PREVIEW_REQUEST = "preview-request";
    static final String STATUS_REQUEST = "status-request";
    static final String STATUS_FILE = "helper-status";
    static final long REQUEST_FALLBACK_MS = 3_000;
    private static final long STATUS_MAX_AGE_MS = 7_000;
    private static final long STATUS_REFRESH_AGE_MS = 2_000;
    private final File dir;
    void selectLong(String target)throws IOException {
        if(target==null){File file=new File(dir,"long-target");if(file.exists()&&!file.delete())throw new IOException("Could not reset long press");return;}
        writeTarget("long-target",target);
    }
    String selectedLong()throws IOException {return readTarget("long-target");}
    void select(String target) throws IOException {
        writeTarget("short-target",target);
    }
    private void writeTarget(String name,String target)throws IOException {
        if (!Options.validTarget(target)) throw new IllegalArgumentException("Choose an app or action");
        File next = new File(dir, name+".next");
        try (FileOutputStream output = new FileOutputStream(next)) { output.write(target.getBytes("UTF-8")); }
        if (!next.renameTo(new File(dir, name))) throw new IOException("Could not save selected app");
    }
    String selected() throws IOException {
        return readTarget("short-target");
    }
    private String readTarget(String name)throws IOException {
        File file = new File(dir, name);
        if (!file.exists()) return null;
        if (file.length() > 32768) throw new IOException("Invalid selection; choose action again");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int)file.length()];
            new DataInputStream(input).readFully(bytes);
            String target = new String(bytes, "UTF-8");
            if (!Options.validTarget(target)) throw new IOException("Invalid selection; choose action again");
            return target;
        }
    }
    Mailbox(File dir) { this.dir = dir; }
    void publishVersion(String version)throws IOException {try(FileOutputStream output=new FileOutputStream(new File(dir,"helper-version"))){output.write(version.getBytes("UTF-8"));}}
    String runningVersion()throws IOException {try(BufferedReader input=new BufferedReader(new FileReader(new File(dir,"helper-version")))){return input.readLine();}catch(FileNotFoundException e){return "";}}
    String readStatus(long now) throws IOException {
        return readStatus(now, false);
    }
    String refreshStatus(long now) throws IOException {
        return readStatus(now, true);
    }
    private String readStatus(long now, boolean refresh) throws IOException {
        if (dir == null) return null;
        long age = Long.MAX_VALUE;
        String status = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(dir, STATUS_FILE)))) {
            String first = reader.readLine();
            if (first != null) {
                age = now - Long.parseLong(first);
                if (age >= 0 && age <= STATUS_MAX_AGE_MS) status = reader.readLine();
            }
        } catch (FileNotFoundException | NumberFormatException ignored) { }
        if (refresh && (status == null || age >= STATUS_REFRESH_AGE_MS)) requestStatus();
        return status;
    }
    void requestStatus() throws IOException {
        if (dir == null) throw new IOException("App storage unavailable");
        // Empty marker. Creation is atomic and repeated requests need only one reply.
        new File(dir, STATUS_REQUEST).createNewFile();
    }
    boolean consumeStatusRequest() {
        File request = new File(dir, STATUS_REQUEST);
        return request.exists() && request.delete();
    }
    void publish(String status, long now) throws IOException {
        if (dir == null || !dir.isDirectory()) throw new IOException("Open Plus Lens app once before starting helper");
        File next = new File(dir, STATUS_FILE + ".next");
        try (FileOutputStream output = new FileOutputStream(next)) {
            output.write((now + "\n" + status + "\n").getBytes("UTF-8"));
        }
        if (!next.renameTo(new File(dir, STATUS_FILE))) throw new IOException("Cannot publish status");
    }
    void requestStop() throws IOException {
        writeRequest(STOP_REQUEST, 1);
    }
    boolean stopRequested() { return dir != null && new File(dir, STOP_REQUEST).exists(); }
    void requestPreview(boolean longPress) throws IOException {
        writeRequest(PREVIEW_REQUEST, longPress ? 2 : 1);
    }
    private void writeRequest(String name, int value) throws IOException {
        if (dir == null) throw new IOException("App storage unavailable");
        File next = new File(dir, name + ".next");
        try (FileOutputStream output = new FileOutputStream(next)) { output.write(value); }
        if (!next.renameTo(new File(dir, name))) throw new IOException("Could not send " + name);
    }
    int consumePreview() throws IOException {
        File file = new File(dir, PREVIEW_REQUEST);
        if (!file.exists()) return 0;
        File claimed = new File(dir, PREVIEW_REQUEST + ".claimed");
        if (!file.renameTo(claimed)) throw new IOException("Could not read preview request");
        try (FileInputStream input = new FileInputStream(claimed)) {
            int value = input.read();
            return value == 1 || value == 2 ? value : 0;
        } finally {
            claimed.delete();
        }
    }
    void clearStop() { if (dir != null) new File(dir, STOP_REQUEST).delete(); }
}
