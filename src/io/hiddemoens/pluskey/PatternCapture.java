package io.hiddemoens.pluskey;

import java.io.*;
import java.util.UUID;

final class PatternCapture {
    static final long TIMEOUT = 90000;
    private final File dir, request;

    PatternCapture(File dir) { this.dir = dir; request = new File(dir, "capture-request"); }

    String start() throws IOException {
        String token = UUID.randomUUID().toString();
        File next = new File(dir, "capture-request.next");
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(next))) { output.writeUTF(token); }
        if (!next.renameTo(request)) throw new IOException("Could not start recording pattern");
        return token;
    }
    String active() {
        long age = System.currentTimeMillis() - request.lastModified();
        if (age < 0 || age >= TIMEOUT) return null;
        try (DataInputStream input = new DataInputStream(new FileInputStream(request))) {
            String token = input.readUTF();
            return token.matches("[0-9a-f-]{36}") ? token : null;
        } catch (IOException missing) { return null; }
    }
    static String filename(String token) { return "capture-result-" + token; }
    void append(char symbol) {
        String token = active();
        if (token == null) return;
        File file = new File(dir, filename(token));
        if (file.length() >= 9) return;
        try (FileOutputStream output = new FileOutputStream(file,true)) { output.write(symbol); }
        catch (IOException error) { System.err.println("Cannot report recorded press: " + error); }
    }
    String read(String token) throws IOException {
        File file = new File(dir, filename(token));
        if (!file.exists()) return "";
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
            byte[] bytes = new byte[(int)Math.min(9,file.length())];
            input.readFully(bytes);
            return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
        }
    }
    void stop(String token) {
        if (token.equals(active())) request.delete();
        new File(dir, filename(token)).delete();
    }
}
