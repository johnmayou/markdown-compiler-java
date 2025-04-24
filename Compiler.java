import java.util.Map;
import java.util.List;
import java.util.Stack;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.regex.Pattern;

import java.util.regex.Matcher;
import java.text.MessageFormat;

public class Compiler {
  public String compile(String md) {
    return gen(parse(tokenize(md)));
  }

  public List<Lexer.Token> tokenize(String md) {
    return new Lexer(md).tokenize();
  }

  public Parser.ASTRootNode parse(List<Lexer.Token> tks) {
    return new Parser(tks).parse();
  }

  public String gen(Parser.ASTRootNode ast) {
    return new CodeGen(ast).gen();
  }

  public static void main(String[] args) {
  }
}

class Lexer {
  private String md;
  private List<Token> tks;

  int LIST_INDENT_SIZE = 2;

  public interface Token {
  }

  public static record HeaderToken(int size) implements Token {
  }

  public static record TextToken(String text, boolean bold, boolean italic) implements Token {
  }

  public static record ListItemToken(int indent, boolean ordered, int digit) implements Token {
  }

  public static record CodeBlockToken(String lang, String code) implements Token {
  }

  public static record CodeInlineToken(String lang, String code) implements Token {
  }

  public static record BlockQuoteToken(int indent) implements Token {
  }

  public static record ImageToken(String alt, String src) implements Token {
  }

  public static record LinkToken(String text, String href) implements Token {
  }

  public static record HorizontalRuleToken() implements Token {
  }

  public static record NewLineToken() implements Token {
  }

  public Lexer(String md) {
    this.md = md;
    this.tks = new ArrayList<>();
  }

  public List<Token> tokenize() {
    while (!this.md.isEmpty()) {
      if (tryTokenizeHeader()) {
        continue;
      }

      if (tryTokenizeCodeBlock()) {
        continue;
      }

      if (tryTokenizeBlockQuote()) {
        continue;
      }

      if (tryTokenizeHorizontalRule()) {
        continue;
      }

      if (tryTokenizeList()) {
        continue;
      }

      if (tryTokenizeHeaderAlt()) {
        continue;
      }

      if (tryTokenizeNewLine()) {
        continue;
      }

      tokenizeCurrentLine();
    }

    if (!this.tks.isEmpty() && !(this.tks.get(this.tks.size() - 1) instanceof NewLineToken)) {
      this.tks.add(new NewLineToken());
    }

    return this.tks;
  }

  private static final Pattern HEADER_PATTERN = Pattern.compile("\\A(######|#####|####|###|##|#) ");

  private boolean tryTokenizeHeader() {
    Matcher matcher = HEADER_PATTERN.matcher(this.md);
    if (!matcher.find()) {
      return false;
    }

    int hSize = matcher.group(1).length();
    this.tks.add(new HeaderToken(hSize));
    this.md = this.md.substring(hSize + 1); // header + space
    tokenizeCurrentLine();
    this.tks.add(new HorizontalRuleToken());
    this.tks.add(new NewLineToken());

    return true;
  }

  private static final Pattern CODEBLOCK_PATTERN = Pattern.compile("\\A```(.*?)\\s*\n");

  private boolean tryTokenizeCodeBlock() {
    Matcher matcher = CODEBLOCK_PATTERN.matcher(this.md);
    if (!matcher.find()) {
      return false;
    }

    String lang = matcher.group(1) == null ? "" : matcher.group(1);

    int codeStart = lang.length();
    while (true) {
      codeStart++;
      if (codeStart >= this.md.length()) { // no ending to code block
        return false;
      }
      if (this.md.charAt(codeStart) == '\n') {
        codeStart++;
        break;
      }
    }

    int codeEnd = codeStart;
    char tick = '`';
    while (true) {
      if (codeEnd + 2 >= this.md.length()) { // no ending to code block
        return false;
      }
      if (this.md.charAt(codeEnd) == tick && this.md.charAt(codeEnd + 1) == tick
          && this.md.charAt(codeEnd + 2) == tick) {
        codeEnd--;
        break;
      }
      codeEnd++;
    }

    // make sure we haven't deleted from this.md until this point, since we need to
    // ensure there is an ending block. If there is no ending block, we would have
    // returned false somewhere above, and we can try tokenizing a different token.

    String code = this.md.substring(codeStart, codeEnd + 1);
    this.md = this.md.substring(codeEnd + 1 + 3); // 3 = ```
    this.tks.add(new CodeBlockToken(lang, code));
    this.tks.add(new NewLineToken());

    return true;
  }

  private static final Pattern BLOCKQUOTE_PATTERN = Pattern.compile("\\A(>(?: >)* ?).*$", Pattern.MULTILINE);

  private boolean tryTokenizeBlockQuote() {
    Matcher matcher = BLOCKQUOTE_PATTERN.matcher(this.md);
    if (!matcher.find()) {
      return false;
    }

    int indent = (int) matcher.group(1).chars().filter(c -> c == '>').count();
    this.tks.add(new BlockQuoteToken(indent));
    this.md = this.md.substring(matcher.group(1).length());
    tokenizeCurrentLine();

    return true;
  }

  private static final Pattern HORIZONTAL_RULE_PATTERN = Pattern.compile("\\A(\\*{3,}[\\* ]*|-{3,}[- ]*)$",
      Pattern.MULTILINE);

  private boolean tryTokenizeHorizontalRule() {
    Matcher matcher = HORIZONTAL_RULE_PATTERN.matcher(this.md);
    if (!matcher.find()) {
      return false;
    }

    this.tks.add(new HorizontalRuleToken());
    this.tks.add(new NewLineToken());
    if (matcher.group(1).length() + 1 < this.md.length()) {
      this.md = this.md.substring(matcher.group(1).length() + 1); // 1 for newl
    } else {
      this.md = "";
    }

    return true;
  }

  private static final Pattern LIST_PATTERN = Pattern.compile("\\A *(?:([0-9]\\.)|(\\*|-)) ");

  private boolean tryTokenizeList() {
    Matcher matcher = LIST_PATTERN.matcher(this.md);
    if (!matcher.find()) {
      return false;
    }

    int spaces = 0;
    while (this.md.charAt(spaces) == ' ') {
      spaces += 1;
    }

    switch (this.md.charAt(spaces)) {
      // un-ordered
      case '*':
      case '-':
        this.tks.add(new ListItemToken(spaces / LIST_INDENT_SIZE, false, -1));
        if (spaces + 2 < this.md.length()) {
          this.md = this.md.substring(spaces + 2); // 2 = */- + space
        } else {
          this.md = "";
        }
        break;

      // ordered
      default:
        // only support one digit for now
        this.tks.add(new ListItemToken(spaces / LIST_INDENT_SIZE, true, Integer.valueOf(this.md.charAt(spaces) + "")));
        if (spaces + 3 < this.md.length()) {
          this.md = this.md.substring(spaces + 3); // 3 = digit + period + space
        } else {
          this.md = "";
        }
    }

    tokenizeCurrentLine();

    return true;
  }

  private static final Pattern HEADER_ALT_PATTERN = Pattern.compile("\\A.+\\n(=+|-+) *");

  private boolean tryTokenizeHeaderAlt() {
    Matcher matcher = HEADER_ALT_PATTERN.matcher(this.md);
    if (!matcher.find()) {
      return false;
    }

    // search next line for header size
    int pointer = 0;
    while (this.md.charAt(pointer) != '\n') {
      pointer += 1;
    }
    char sizeChar = this.md.charAt(pointer + 2); // newl + space
    switch (sizeChar) {
      case '=':
        this.tks.add(new HeaderToken(1));
        break;
      case '-':
        this.tks.add(new HeaderToken(2));
        break;
      default:
        throw new RuntimeException("Invalid char found for header alt: " + sizeChar);
    }

    tokenizeCurrentLine();
    delCurrentLine(); // ---/=== line
    this.tks.add(new HorizontalRuleToken());
    this.tks.add(new NewLineToken());

    return true;
  }

  private static final Pattern NEW_LINE_PATTERN = Pattern.compile("\\A\\n");

  private boolean tryTokenizeNewLine() {
    Matcher matcher = NEW_LINE_PATTERN.matcher(this.md);
    if (!matcher.find()) {
      return false;
    }

    this.tks.add(new NewLineToken());
    this.md = this.md.substring(1);

    return true;
  }

  private static final Pattern BOLD_AND_ITALIC_PATTERN = Pattern.compile("\\A(\\*{3}[^\\*]+?\\*{3}|_{3}[^_]+?_{3})");
  private static final Pattern BOLD_PATTERN = Pattern.compile("\\A(\\*{2}[^\\*]+?\\*{2}|_{2}[^_]+?_{2})");
  private static final Pattern ITALIC_PATTERN = Pattern.compile("\\A(\\*[^\\*]+?\\*|_[^_]+?_)");
  private static final Pattern IMAGE_PATTERN = Pattern.compile("\\A!\\[(.*)\\]\\((.*)\\)");
  private static final Pattern LINK_PATTERN = Pattern.compile("\\A\\[(.*)\\]\\((.*)\\)");
  private static final Pattern CODE_INLINE_PATTERN = Pattern.compile("\\A`(.+?)`([a-z]*)");

  private void tokenizeCurrentLine() {
    if (this.md.isEmpty()) {
      return;
    }
    if (this.md.charAt(0) == '\n') { // already at the end of current line
      this.tks.add(new NewLineToken());
      this.md = this.md.substring(1);
      return;
    }

    // find current line
    String line = null;
    int lineEnd = 0;
    while (true) {
      lineEnd++;
      if (lineEnd == this.md.length()) { // EOF
        break;
      }
      if (this.md.charAt(lineEnd) == '\n') {
        lineEnd++;
        break;
      }
    }
    if (lineEnd == this.md.length()) {
      line = this.md;
    } else {
      line = this.md.substring(0, lineEnd);
    }
    this.md = this.md.substring(line.length());

    // keep track of current substring
    StringBuilder currStr = new StringBuilder();
    Runnable currPush = () -> {
      this.tks.add(new TextToken(currStr.toString(), false, false));
      currStr.setLength(0);
    };

    while (!line.isEmpty()) {
      // == bold and italic ==
      Matcher matcher = BOLD_AND_ITALIC_PATTERN.matcher(line);
      if (matcher.find()) {
        if (!currStr.isEmpty()) {
          currPush.run();
        }

        this.tks.add(new TextToken(matcher.group(1).replace("*", "").replace("_", ""), true, true));
        line = line.substring(matcher.group(1).length());

        continue;
      }

      // == bold ==
      matcher = BOLD_PATTERN.matcher(line);
      if (matcher.find()) {
        if (!currStr.isEmpty()) {
          currPush.run();
        }

        this.tks.add(new TextToken(matcher.group(1).replace("*", "").replace("_", ""), true, false));
        line = line.substring(matcher.group(1).length());

        continue;
      }

      // == italic ==
      matcher = ITALIC_PATTERN.matcher(line);
      if (matcher.find()) {
        if (!currStr.isEmpty()) {
          currPush.run();
        }

        this.tks.add(new TextToken(matcher.group(1).replace("*", "").replace("_", ""), false, true));
        line = line.substring(matcher.group(1).length());

        continue;
      }

      // == image ==
      matcher = IMAGE_PATTERN.matcher(line);
      if (matcher.find()) {
        if (!currStr.isEmpty()) {
          currPush.run();
        }

        this.tks.add(new ImageToken(matcher.group(1), matcher.group(2)));
        line = line.substring(matcher.group(1).length() + matcher.group(2).length() + 5); // 5 = ![]()

        continue;
      }

      // == link ==
      matcher = LINK_PATTERN.matcher(line);
      if (matcher.find()) {
        if (!currStr.isEmpty()) {
          currPush.run();
        }

        this.tks.add(new LinkToken(matcher.group(1), matcher.group(2)));
        line = line.substring(matcher.group(1).length() + matcher.group(2).length() + 4); // 4 = []()

        continue;
      }

      // == code ==
      matcher = CODE_INLINE_PATTERN.matcher(line);
      if (matcher.find()) {
        if (!currStr.isEmpty()) {
          currPush.run();
        }

        String code = matcher.group(1);
        String lang = matcher.group(2) == null ? "" : matcher.group(2);

        this.tks.add(new CodeInlineToken(lang, code));
        line = line.substring(code.length() + lang.length() + 2); // 2 = ``

        continue;
      }

      // == new line ==
      if (line.charAt(0) == '\n') {
        if (!currStr.isEmpty()) {
          currPush.run();
        }

        this.tks.add(new NewLineToken());
        break;
      }

      currStr.append(line.charAt(0));
      line = line.substring(1);
    }

    if (!currStr.isEmpty()) {
      currPush.run();
    }
  }

  private void delCurrentLine() {
    int pointer = 0;
    while (pointer < this.md.length()) {
      pointer++;
      if (this.md.charAt(pointer) == '\n') {
        this.md = this.md.substring(Math.min(pointer, this.md.length() - 1));
        break;
      }
    }
  }
}

class Parser {
  private List<Lexer.Token> tks;
  private int tksStart;
  private ASTRootNode root;

  public interface ASTNode {
  }

  public static record ASTRootNode(List<ASTNode> children) implements ASTNode {
  }

  public static record ASTHeaderNode(int size, List<ASTNode> children) implements ASTNode {
  }

  public static record ASTCodeBlockNode(String lang, String code) implements ASTNode {
  }

  public static record ASTCodeInlineNode(String lang, String code) implements ASTNode {
  }

  public static record ASTQuoteNode(List<ASTNode> children) implements ASTNode {
  }

  public static record ASTQuoteItemNode(List<ASTNode> children) implements ASTNode {
  }

  public static record ASTParagraphNode(List<ASTNode> children) implements ASTNode {
  }

  public static record ASTTextNode(String text, boolean bold, boolean italic) implements ASTNode {
  }

  public static record ASTHorizontalRuleNode() implements ASTNode {
  }

  public static record ASTImageNode(String alt, String src) implements ASTNode {
  }

  public static record ASTLinkNode(String text, String href) implements ASTNode {
  }

  public static record ASTListNode(boolean ordered, List<ASTNode> children) implements ASTNode {
  }

  public static record ASTListItemNode(List<ASTNode> children) implements ASTNode {
  }

  public Parser(List<Lexer.Token> tks) {
    this.tks = tks;
    this.tksStart = 0;
    this.root = new ASTRootNode(new ArrayList<>());
  }

  public ASTRootNode parse() {
    while (this.tksStart < this.tks.size()) {
      if (peek(Lexer.HeaderToken.class)) {
        parseHeader();
      } else if (peek(Lexer.CodeBlockToken.class)) {
        parseCodeBlock();
      } else if (peek(Lexer.BlockQuoteToken.class)) {
        parseBlockQuote();
      } else if (peek(Lexer.HorizontalRuleToken.class)) {
        parseHorizontalRule();
      } else if (peek(Lexer.ListItemToken.class)) {
        parseList();
      } else if (peek(Lexer.ImageToken.class)) {
        parseImage();
      } else if (peekAny(Lexer.TextToken.class, Lexer.CodeInlineToken.class, Lexer.LinkToken.class)) {
        parseParagraph();
      } else if (peek(Lexer.NewLineToken.class)) {
        consume(Lexer.NewLineToken.class);
      } else {
        throw new RuntimeException("Unable to parse tokens:\n" + this.tks);
      }
    }

    return this.root;
  }

  private void parseHeader() {
    Lexer.HeaderToken token = consume(Lexer.HeaderToken.class);
    this.root.children.add(new ASTHeaderNode(token.size(), parseInline()));
  }

  private void parseCodeBlock() {
    Lexer.CodeBlockToken token = consume(Lexer.CodeBlockToken.class);
    consume(Lexer.NewLineToken.class);
    this.root.children.add(new ASTCodeBlockNode(token.lang(), token.code()));
  }

  private void parseBlockQuote() {
    HashMap<Integer, ASTQuoteNode> blockIndentMap = new HashMap<>();

    // add root quote block
    int rootIndent = consume(Lexer.BlockQuoteToken.class).indent();
    ASTQuoteNode rootBlock = new ASTQuoteNode(new ArrayList<>(List.of(parseQuoteItem())));
    blockIndentMap.put(rootIndent, rootBlock);

    while (peek(Lexer.BlockQuoteToken.class)) {
      Lexer.BlockQuoteToken block = consume(Lexer.BlockQuoteToken.class);
      if (peek(Lexer.NewLineToken.class)) {
        consume(Lexer.NewLineToken.class);
        continue;
      }

      ASTQuoteNode blockNode = blockIndentMap.get(block.indent());
      if (blockNode == null) {
        blockNode = new ASTQuoteNode(new ArrayList<>(List.of(parseQuoteItem())));
        blockIndentMap.put(block.indent(), blockNode);
        ASTQuoteNode blockParent = blockIndentMap.containsKey(block.indent() - 1)
            ? blockIndentMap.get(block.indent() - 1)
            : rootBlock;
        blockParent.children.add(blockNode);
      } else {
        blockNode.children.add(parseQuoteItem());
      }
    }

    this.root.children.add(rootBlock);
  }

  private ASTQuoteItemNode parseQuoteItem() {
    return new ASTQuoteItemNode(new ArrayList<>(parseInlineBlockQuote()));
  }

  private void parseHorizontalRule() {
    consume(Lexer.HorizontalRuleToken.class);
    consume(Lexer.NewLineToken.class);
    this.root.children.add(new ASTHorizontalRuleNode());
  }

  public record ListStackItem(Parser.ASTListNode node, int indent) {
  }

  private void parseList() {
    // create root
    ASTListNode rootList = new ASTListNode(consume(Lexer.ListItemToken.class).ordered(), new ArrayList<>());
    rootList.children.add(new ASTListItemNode(parseInline()));

    // stack of last seen nodes
    Stack<ListStackItem> listStack = new Stack<>();
    listStack.add(new ListStackItem(rootList, 0));

    while (peek(Lexer.ListItemToken.class)) {
      Lexer.ListItemToken currToken = consume(Lexer.ListItemToken.class);
      int currIndent = Math.min(listStack.peek().indent() + 1, currToken.indent()); // only allow 1 additional level at
                                                                                    // a time
      int lastIndent = listStack.peek().indent();
      if (currIndent > lastIndent) { // deeper indentation
        // create new node
        ASTListNode node = new ASTListNode(currToken.ordered(), new ArrayList<>());
        node.children.add(new ASTListItemNode(parseInline()));

        // append to last child of top (of stack) node
        List<ASTNode> topNodeChildren = listStack.peek().node().children();
        ASTNode topNodeLastChild = topNodeChildren.get(topNodeChildren.size() - 1);
        if (topNodeLastChild instanceof ASTListItemNode) {
          ((ASTListItemNode) topNodeLastChild).children.add(node);
        } else {
          throw new RuntimeException("Unexpected top node last child:" + topNodeLastChild);
        }
        listStack.add(new ListStackItem(node, currIndent));
      } else if (currIndent < lastIndent) { // lost indentation
        // pop from stack until we find current level
        while (listStack.peek().indent() > currIndent) {
          listStack.pop();
        }
        listStack.peek().node().children.add(new ASTListItemNode(parseInline()));
      } else { // same indentation
        listStack.peek().node().children.add(new ASTListItemNode(parseInline()));
      }
    }

    this.root.children.add(rootList);
  }

  private void parseImage() {
    Lexer.ImageToken token = consume(Lexer.ImageToken.class);
    consume(Lexer.NewLineToken.class);
    this.root.children.add(new ASTImageNode(token.alt(), token.src()));
  }

  private void parseParagraph() {
    this.root.children.add(new ASTParagraphNode(parseInline()));
  }

  @SuppressWarnings("unchecked")
  private static final Class<? extends Lexer.Token>[] INLINE_TOKENS = new Class[] {
      Lexer.TextToken.class,
      Lexer.CodeInlineToken.class,
      Lexer.LinkToken.class
  };

  private List<ASTNode> parseInline() {
    List<ASTNode> nodes = new ArrayList<>();

    while (peekAny(INLINE_TOKENS) || (peek(Lexer.NewLineToken.class) && peekAny(2, INLINE_TOKENS))) {
      if (peek(Lexer.NewLineToken.class)) {
        consume(Lexer.NewLineToken.class);
        nodes.add(new ASTTextNode(" ", false, false));
      }

      nodes.add(parseInlineSingle());
    }
    consume(Lexer.NewLineToken.class);

    return nodes;
  }

  private List<ASTNode> parseInlineBlockQuote() {
    List<ASTNode> nodes = new ArrayList<>();

    while (peekAny(INLINE_TOKENS)
        || (peek(Lexer.NewLineToken.class) && peek(2, Lexer.BlockQuoteToken.class) && peekAny(3, INLINE_TOKENS))) {
      if (peek(Lexer.NewLineToken.class)) {
        consume(Lexer.NewLineToken.class);
        consume(Lexer.BlockQuoteToken.class);
        nodes.add(new ASTTextNode(" ", false, false));
      }

      nodes.add(parseInlineSingle());
    }
    consume(Lexer.NewLineToken.class);

    return nodes;
  }

  private ASTNode parseInlineSingle() {
    if (peek(Lexer.TextToken.class)) {
      Lexer.TextToken token = consume(Lexer.TextToken.class);
      return new ASTTextNode(token.text(), token.bold(), token.italic());
    } else if (peek(Lexer.CodeInlineToken.class)) {
      Lexer.CodeInlineToken token = consume(Lexer.CodeInlineToken.class);
      return new ASTCodeInlineNode(token.lang(), token.code());
    } else if (peek(Lexer.LinkToken.class)) {
      Lexer.LinkToken token = consume(Lexer.LinkToken.class);
      return new ASTLinkNode(token.text(), token.href());
    } else {
      throw new RuntimeException("Unexpected next token:\n" + this.tks);
    }
  }

  @SafeVarargs
  private boolean peekAny(int depth, Class<? extends Lexer.Token>... tokenTypes) {
    for (Class<? extends Lexer.Token> tokenType : tokenTypes) {
      if (peek(depth, tokenType)) {
        return true;
      }
    }
    return false;
  }

  @SafeVarargs
  private boolean peekAny(Class<? extends Lexer.Token>... tokenTypes) {
    return peekAny(1, tokenTypes);
  }

  private boolean peek(int depth, Class<? extends Lexer.Token> tokenType) {
    int index = this.tksStart + depth - 1;
    if (index >= this.tks.size()) {
      return false;
    }
    return tokenType.isInstance(this.tks.get(index));
  }

  private boolean peek(Class<? extends Lexer.Token> tokenType) {
    return peek(1, tokenType);
  }

  private <T extends Lexer.Token> T consume(Class<T> tokenType) {
    if (this.tksStart == this.tks.size()) {
      throw new RuntimeException("Expected to find token type " + tokenType + " but did not find a token");
    }

    Lexer.Token token = this.tks.get(this.tksStart);
    this.tksStart += 1;
    if (!(tokenType.isInstance(token))) {
      throw new RuntimeException("Expected to find token type " + tokenType + " but did find " + token);
    }

    return tokenType.cast(token);
  }
}

class CodeGen {
  private Parser.ASTRootNode ast;
  private StringBuilder html;

  public CodeGen(Parser.ASTRootNode ast) {
    this.ast = ast;
    this.html = new StringBuilder();
  }

  public String gen() {
    for (Parser.ASTNode node : this.ast.children()) {
      if (node instanceof Parser.ASTHeaderNode) {
        this.html.append(genHeader((Parser.ASTHeaderNode) node));
      } else if (node instanceof Parser.ASTCodeBlockNode) {
        this.html.append(genCodeBlock((Parser.ASTCodeBlockNode) node));
      } else if (node instanceof Parser.ASTQuoteNode) {
        this.html.append(genQuoteBlock((Parser.ASTQuoteNode) node));
      } else if (node instanceof Parser.ASTListNode) {
        this.html.append(genList((Parser.ASTListNode) node));
      } else if (node instanceof Parser.ASTHorizontalRuleNode) {
        this.html.append(genHorizontalRule((Parser.ASTHorizontalRuleNode) node));
      } else if (node instanceof Parser.ASTImageNode) {
        this.html.append(genImage((Parser.ASTImageNode) node));
      } else if (node instanceof Parser.ASTLinkNode) {
        this.html.append(genLink((Parser.ASTLinkNode) node));
      } else if (node instanceof Parser.ASTCodeInlineNode) {
        this.html.append(genCodeInline((Parser.ASTCodeInlineNode) node));
      } else if (node instanceof Parser.ASTParagraphNode) {
        this.html.append(genParagraph((Parser.ASTParagraphNode) node));
      } else {
        throw new RuntimeException("Invalid node: " + node);
      }
    }

    return this.html.toString();
  }

  private String genHeader(Parser.ASTHeaderNode node) {
    return MessageFormat.format("<h{0}>{1}</h{0}>", node.size(), genLine(node.children()));
  }

  private String genCodeBlock(Parser.ASTCodeBlockNode node) {
    return MessageFormat.format("<pre><code class=\"{0}\">{1}</code></pre>", escapeHtml(node.lang()), node.code());
  }

  private String genQuoteBlock(Parser.ASTQuoteNode node) {
    StringBuilder html = new StringBuilder("<blockquote>");

    for (Parser.ASTNode child : node.children()) {
      if (child instanceof Parser.ASTQuoteNode) {
        html.append(genQuoteBlock((Parser.ASTQuoteNode) child));
      } else if (child instanceof Parser.ASTQuoteItemNode) {
        html.append("<p>" + genLine(((Parser.ASTQuoteItemNode) child).children()) + "</p>");
      } else {
        throw new RuntimeException("Invalid child node: " + child);
      }
    }

    html.append("</blockquote>");
    return html.toString();
  }

  private String genList(Parser.ASTListNode node) {
    StringBuilder html = new StringBuilder(node.ordered() ? "<ol>" : "<ul>");
    for (Parser.ASTNode child : node.children()) {
      html.append("<li>");
      if (!(child instanceof Parser.ASTListItemNode)) {
        throw new RuntimeException("Invalid child of list node: " + child);
      }
      for (Parser.ASTNode innerChild : ((Parser.ASTListItemNode) child).children()) {
        if (innerChild instanceof Parser.ASTListNode) {
          html.append(genList((Parser.ASTListNode) innerChild));
        } else {
          html.append(genLine(new ArrayList<>(List.of(innerChild))));
        }
      }
      html.append("</li>");
    }
    html.append(node.ordered() ? "</ol>" : "</ul>");
    return html.toString();
  }

  private String genHorizontalRule(Parser.ASTHorizontalRuleNode node) {
    return "<hr>";
  }

  private String genImage(Parser.ASTImageNode node) {
    return MessageFormat.format("<img alt=\"{0}\" src=\"{1}\"/>", escapeHtml(node.alt()), escapeHtml(node.src()));
  }

  private String genLink(Parser.ASTLinkNode node) {
    return MessageFormat.format("<a href=\"{0}\">{1}</a>", escapeHtml(node.href()), escapeHtml(node.text()));
  }

  private String genCodeInline(Parser.ASTCodeInlineNode node) {
    return MessageFormat.format("<code class=\"{0}\">{1}</code>", escapeHtml(node.lang()), node.code());
  }

  private String genParagraph(Parser.ASTParagraphNode node) {
    return "<p>" + genLine(node.children()) + "</p>";
  }

  private String genLine(List<Parser.ASTNode> nodes) {
    StringBuilder html = new StringBuilder();

    for (Parser.ASTNode node : nodes) {
      if (node instanceof Parser.ASTLinkNode) {
        html.append(genLink((Parser.ASTLinkNode) node));
      } else if (node instanceof Parser.ASTCodeInlineNode) {
        html.append(genCodeInline((Parser.ASTCodeInlineNode) node));
      } else if (node instanceof Parser.ASTTextNode) {
        html.append(genText((Parser.ASTTextNode) node));
      } else {
        throw new RuntimeException("Invalid node: " + node);
      }
    }

    return html.toString();
  }

  private String genText(Parser.ASTTextNode node) {
    StringBuilder html = new StringBuilder();
    if (node.italic()) {
      html.append("<i>");
    }
    if (node.bold()) {
      html.append("<b>");
    }
    html.append(escapeHtml(node.text()));
    if (node.bold()) {
      html.append("</b>");
    }
    if (node.italic()) {
      html.append("</i>");
    }
    return html.toString();
  }

  private static final Map<Character, String> ESCAPE_HTML_MAP = Map.of(
      '<', "&lt;",
      '>', "&gt;",
      '&', "&amp;",
      '"', "&quot;");

  private String escapeHtml(String str) {
    StringBuilder sb = null;

    for (int i = 0; i < str.length(); i++) {
      char ch = str.charAt(i);
      String replacement = ESCAPE_HTML_MAP.get(ch);

      if (replacement != null) {
        if (sb == null) {
          sb = new StringBuilder(str.length() + 20); // extra capacity since string length will go up
          sb.append(str, 0, i); // copy everything up to first match
        }
        sb.append(replacement);
      } else if (sb != null) {
        sb.append(ch);
      }
    }

    // if no replacements were needed, return original
    return sb == null ? str : sb.toString();
  }
}