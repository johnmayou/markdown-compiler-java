package com.johnmayou.compiler;

import java.io.IOException;
import java.nio.file.Files;

import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompilerController {
  @GetMapping("/")
  public String hello(@RequestParam(value = "name", defaultValue = "World") String name) throws IOException {
    ClassPathResource resource = new ClassPathResource("example.text");
    String markdown = Files.readString(resource.getFile().toPath());
    String compiledHtml = new Compiler().compile(markdown);
    return wrapHtml(compiledHtml);
  }

  public String wrapHtml(String body) {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Markdown Preview</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 40px; }
                blockquote { color: gray; border-left: 4px solid #ccc; padding-left: 10px; }
                strong { font-weight: bold; }
            </style>
        </head>
        <body>
            """ + body + """
        </body>
        </html>
        """;
  }
}
