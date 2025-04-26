package com.johnmayou.compiler;

import java.nio.file.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.text.MessageFormat;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompilerTests {
	@Test
	void tokenizeParagraph() {
		List<Lexer.Token> expected = List.of(
				new Lexer.TextToken("text", false, false),
				new Lexer.NewLineToken());
		List<Lexer.Token> actual = new Compiler().tokenize("text");

		assertEquals(expected, actual);
	}

	@Test
	void parseParagraph() {
		Parser.ASTNode expected = new Parser.ASTRootNode(new ArrayList<>(List.of(
				new Parser.ASTParagraphNode(List.of(
						new Parser.ASTTextNode("text", false, false))))));
		Parser.ASTNode actual = new Compiler().parse(new ArrayList<>(List.of(
				new Lexer.TextToken("text", false, false),
				new Lexer.NewLineToken())));

		assertEquals(expected, actual);
	}

	@Test
	void genParagraph() {
		String expected = "<p>text</p>";
		String actual = new Compiler().gen(new Parser.ASTRootNode(new ArrayList<>(List.of(
				new Parser.ASTParagraphNode(new ArrayList<>(List.of(
						new Parser.ASTTextNode("text", false, false))))))));

		assertEquals(expected, actual);
	}

	@Test
	void goldenFiles() throws IOException, URISyntaxException {
		List<String> failures = new ArrayList<>();
		Path testDataDir = Paths.get(getClass().getResource("/testdata").toURI());

		try (DirectoryStream<Path> files = Files.newDirectoryStream(testDataDir, "*.text")) {
			for (Path filepath : files) {
				String testName = "golden_" + filepath.getFileName();
				try {
					String md = Files.readString(filepath);
					String html = prettifyHtml(new Compiler().compile(md));

					Path htmlPath = Paths.get(
							getClass().getResource("/testdata/" + filepath.getFileName().toString().replace(".text", ".html"))
									.toURI());

					String actual = Files.readString(htmlPath);
					if (!actual.equals(html)) {
						failures.add(MessageFormat.format("{0}\n - Expected:\n{1}\n - Actual:\n{2}", testName, html, actual));
					}
				} catch (Exception e) {
					failures.add(MessageFormat.format("{0} - Exception thrown: {1}", testName, e));
				}
			}
		}

		StringBuilder message = new StringBuilder();
		for (String failure : failures) {
			message.append("\n=======================\n" + failure);
		}

		assertEquals(failures.size(), 0, message.toString());
	}

	private String prettifyHtml(String html) throws IOException, InterruptedException {
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
}
