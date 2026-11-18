package io.hiddemoens.pluskey;
import java.io.*;
final class Mailbox {
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
        if (dir == null) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(dir, "helper-status")))) {
            String first = reader.readLine();
            if (first == null) return null;
            long age = now - Long.parseLong(first);
            return age >= 0 && age <= 7000 ? reader.readLine() : null;
        } catch (FileNotFoundException | NumberFormatException e) { return null; }
    }
    void publish(String status, long now) throws IOException {
        if (dir == null || !dir.isDirectory()) throw new IOException("Open Plus Lens app once before starting helper");
        File next = new File(dir, "helper-status.next");
        try (FileOutputStream output = new FileOutputStream(next)) {
            output.write((now + "\n" + status + "\n").getBytes("UTF-8"));
        }
        if (!next.renameTo(new File(dir, "helper-status"))) throw new IOException("Cannot publish status");
    }
    void requestStop() throws IOException {
        if (dir == null) throw new IOException("App storage unavailable");
        try (FileOutputStream output = new FileOutputStream(new File(dir, "helper-stop"))) { output.write(1); }
    }
    boolean stopRequested() { return dir != null && new File(dir, "helper-stop").exists(); }
    void requestPreview(boolean longPress)throws IOException {try(FileOutputStream output=new FileOutputStream(new File(dir,"preview-request"))){output.write(longPress?2:1);}}
    int consumePreview()throws IOException {
        File file=new File(dir,"preview-request");if(!file.exists())return 0;
        int value;try(FileInputStream input=new FileInputStream(file)){value=input.read();}finally{file.delete();}
        return value==1||value==2?value:0;
    }
    void clearStop() { if (dir != null) new File(dir, "helper-stop").delete(); }
}
