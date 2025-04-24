import java.nio.file.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

public class CompilerTest {
  public static void main(String[] args) {
    boolean allPassed = true;

    try {
      printTestName("testTokenizeParagraph");
      testTokenizeParagraph();
    } catch (Exception e) {
      printException(e);
      allPassed = false;
    }

    try {
      printTestName("testTokenizeHeader");
      testTokenizeHeader();
    } catch (Exception e) {
      printException(e);
      allPassed = false;
    }

    try {
      printTestName("testTokenizeBlockQuote");
      testTokenizeBlockQuote();
    } catch (Exception e) {
      printException(e);
      allPassed = false;
    }

    try {
      printTestName("testTokenizeBlockQuote2");
      testTokenizeBlockQuote2();
    } catch (Exception e) {
      printException(e);
      allPassed = false;
    }

    try {
      printTestName("testParseParagraph");
      testParseParagraph();
    } catch (Exception e) {
      printException(e);
      allPassed = false;
    }

    try {
      printTestName("testGenParagraph");
      testGenParagraph();
    } catch (Exception e) {
      printException(e);
      allPassed = false;
    }

    try {
      printTestName("Running testGoldenFiles");
      testGoldenFiles();
    } catch (Exception e) {
      printException(e);
      allPassed = false;
    }

    if (allPassed) {
      System.out.println("All tests passed!");
      System.exit(0);
    } else {
      System.exit(1);
    }
  }

  public static void printTestName(String testName) {
    System.out.println("\n---\n" + testName + "\n---\n");
  }

  public static void printException(Exception e) {
    System.out.println("Exception thrown: " + e + "\n");
    e.printStackTrace();
  }

  private static void testTokenizeParagraph() {
    List<Lexer.Token> expected = List.of(
        new Lexer.TextToken("text", false, false),
        new Lexer.NewLineToken());
    List<Lexer.Token> actual = new Compiler().tokenize("text");

    assertEqual(expected, actual);
  }

  private static void testTokenizeHeader() {
    List<Lexer.Token> expected = List.of(
        new Lexer.HeaderToken(1),
        new Lexer.TextToken("header", false, false),
        new Lexer.HorizontalRuleToken(),
        new Lexer.NewLineToken());
    List<Lexer.Token> actual = new Compiler().tokenize("# header");

    assertEqual(expected, actual);
  }

  private static void testTokenizeBlockQuote() {
    List<Lexer.Token> expected = List.of(
        new Lexer.BlockQuoteToken(1),
        new Lexer.TextToken("line 1", false, false),
        new Lexer.NewLineToken(),
        new Lexer.BlockQuoteToken(1),
        new Lexer.TextToken("line 2", false, false),
        new Lexer.NewLineToken());
    List<Lexer.Token> actual = new Compiler().tokenize("> line 1\n> line 2");

    assertEqual(expected, actual);
  }

  private static void testTokenizeBlockQuote2() {
    List<Lexer.Token> expected = List.of(
        new Lexer.BlockQuoteToken(1),
        new Lexer.TextToken("line 1", false, false),
        new Lexer.NewLineToken(),
        new Lexer.NewLineToken(),
        new Lexer.BlockQuoteToken(1),
        new Lexer.TextToken("line 2", false, false),
        new Lexer.NewLineToken());
    List<Lexer.Token> actual = new Compiler().tokenize("> line 1\n\n> line 2");

    assertEqual(expected, actual);
  }

  private static void testParseParagraph() {
    Parser.ASTNode expected = new Parser.ASTRootNode(new ArrayList<>(List.of(
        new Parser.ASTParagraphNode(List.of(
            new Parser.ASTTextNode("text", false, false))))));
    Parser.ASTNode actual = new Compiler().parse(new ArrayList<>(List.of(
        new Lexer.TextToken("text", false, false),
        new Lexer.NewLineToken())));

    assertEqual(expected, actual);
  }

  private static void testGenParagraph() {
    String expected = "<p>text</p>";
    String actual = new Compiler().gen(new Parser.ASTRootNode(new ArrayList<>(List.of(
        new Parser.ASTParagraphNode(new ArrayList<>(List.of(
            new Parser.ASTTextNode("text", false, false))))))));

    assertEqual(expected, actual);
  }

  private static void testGoldenFiles() throws IOException {
    boolean update = "true".equals(System.getenv("UPDATE"));
    List<String> failures = new ArrayList<>();

    try (DirectoryStream<Path> files = Files.newDirectoryStream(Paths.get("testdata"), "*.text")) {
      for (Path filepath : files) {
        String testName = "golden_" + filepath.getFileName();
        if (!(testName.contains("list.text"))) {
          System.out.println("skipping: " + testName);
          continue;
        }
        System.out.println("running: " + testName);
        try {
          String md = Files.readString(filepath);
          String html = prettifyHtml(new Compiler().compile(md));

          Path htmlPath = Paths.get("testdata", filepath.getFileName().toString().replace(".text", ".html"));
          if (update) {
            if (!Files.exists(htmlPath)) {
              Files.createFile(htmlPath);
            }
            Files.writeString(htmlPath, html);
          }

          if (!Files.exists(htmlPath)) {
            throw new RuntimeException("Expected html file path to exist: " + htmlPath);
          }

          assertEqual(Files.readString(htmlPath), html);
        } catch (Exception e) {
          printException(e);
          failures.add(testName);
        }
      }
    }

    if (failures.size() > 0) {
      StringBuilder message = new StringBuilder();
      for (String failure : failures) {
        message.append("\n- " + failure);
      }
      throw new RuntimeException("File test failure:\n");
    }
  }

  private static String prettifyHtml(String html) throws IOException, InterruptedException {
    Process process = (new ProcessBuilder("prettier", "--parser", "html")).start();

    // send html to stdin
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
    writer.write(html);
    writer.close();

    // validate success
    int exitCode = process.waitFor();
    String stderr = new String(process.getErrorStream().readAllBytes());
    if (exitCode != 0 || stderr.length() > 0) {
      throw new RuntimeException("Prettier command failed: " + stderr);
    }

    return new String(process.getInputStream().readAllBytes());
  }

  private static void assertEqual(Object expected, Object actual) {
    if (!expected.equals(actual)) {
      System.out.println("Expected:\n" + expected);
      System.out.println("Actual:\n" + actual);
      throw new RuntimeException("Assertion failed!");
    }
  }
}