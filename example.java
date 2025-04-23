import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class Main {
  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("Error: No command provided.");
      return;
    }

    final String command = args[0];

    switch (command) {
      case "init" -> initializeGitDirectory();
      case "cat-file" -> {
        if (args.length < 3 || !args[1].equals("-p")) {
          System.err.println("Usage: cat-file -p <hash>");
          return;
        }
        displayGitObject(args[2]);
      }
      case "hash-object" -> {
        if (args.length < 3 || !args[1].equals("-w")) {
          System.err.println("Usage: hash-object -w <file>");
          return;
        }
        hashObjectWrite(args[2]);
      }
      default -> System.out.println("Unknown command: " + command);
    }
  }

  private static void initializeGitDirectory() {
    File root = new File(".git");
    File objectsDir = new File(root, "objects");
    File refsDir = new File(root, "refs");
    File headFile = new File(root, "HEAD");

    try {
      if (!root.exists() && !root.mkdir()) {
        throw new IOException("Failed to create .git directory.");
      }

      objectsDir.mkdirs();
      refsDir.mkdirs();

      if (headFile.createNewFile() || headFile.exists()) {
        Files.writeString(headFile.toPath(), "ref: refs/heads/main\n", StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("Initialized git directory");
      }
    } catch (IOException e) {
      System.err.println("Error initializing Git directory: " + e.getMessage());
    }
  }

  private static void displayGitObject(String hash) {
    if (hash.length() < 3) {
      System.err.println("Error: Invalid hash.");
      return;
    }

    String dirHash = hash.substring(0, 2);
    String fileHash = hash.substring(2);
    File blobFile = new File(".git/objects/" + dirHash + "/" + fileHash);

    if (!blobFile.exists()) {
      System.err.println("Error: Object not found.");
      return;
    }

    try (
      InflaterInputStream inflaterStream = new InflaterInputStream(new FileInputStream(blobFile));
      BufferedReader reader = new BufferedReader(new InputStreamReader(inflaterStream))
    ) {
      StringBuilder output = new StringBuilder();
      int ch;
      boolean pastHeader = false;
      while ((ch = reader.read()) != -1) {
        if (!pastHeader && ch == 0) {
          pastHeader = true;
        } else if (pastHeader) {
          output.append((char) ch);
        }
      }
      System.out.print(output);
    } catch (IOException e) {
      System.err.println("Error reading object: " + e.getMessage());
    }
  }

  private static void hashObjectWrite(String fileName) {
    try {
      byte[] content = Files.readAllBytes(new File(fileName).toPath());
      String header = "blob " + content.length + "\0";
      byte[] fullBlob = concatenate(header.getBytes(), content);

      // Compute SHA-1
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] sha1Bytes = md.digest(fullBlob);
      String sha1Hex = bytesToHex(sha1Bytes);

      // Save blob to .git/objects
      String dir = sha1Hex.substring(0, 2);
      String file = sha1Hex.substring(2);
      File objectDir = new File(".git/objects/" + dir);
      File objectFile = new File(objectDir, file);

      if (!objectFile.exists()) {
        objectDir.mkdirs();
        try (
          FileOutputStream fos = new FileOutputStream(objectFile);
          DeflaterOutputStream dos = new DeflaterOutputStream(fos)
        ) {
          dos.write(fullBlob);
        }
      }

      System.out.println(sha1Hex);
    } catch (IOException | NoSuchAlgorithmException e) {
      System.err.println("Error hashing object: " + e.getMessage());
    }
  }

  private static byte[] concatenate(byte[] a, byte[] b) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    outputStream.write(a);
    outputStream.write(b);
    return outputStream.toByteArray();
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
