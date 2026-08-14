import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import java.nio.file.*;
import java.util.*;

/** Verifies the Legado book source JSONPath rules for https://full.hnxianxin.cn/qd
 *  using the same jayway json-path (2.10.0) library the app depends on. */
public class VerifyRules {

    static String load(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("fixtures/" + name)));
    }

    /** Mimics Legado {{$.X}} substitution inside a URL rule against a JSON element. */
    static String substitute(String rule, Object json) {
        String out = rule;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\{\\{(\\$\\.[^}]+|\\$\\[[^}]+)\\}\\}");
        java.util.regex.Matcher m = p.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            try {
                Object v = JsonPath.read(json, m.group(1));
                String s = v instanceof List ? ((List<?>) v).get(0).toString() : v.toString();
                if (s.endsWith(".0") && s.matches("\\d+\\.0")) s = s.substring(0, s.length() - 2);
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(s));
            } catch (Exception e) {
                m.appendReplacement(sb, "MISSING:" + m.group(1));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        int pass = 0, fail = 0;

        // ---------- SEARCH ----------
        ReadContext search = JsonPath.parse(load("qd_search.json"));
        Object items = search.read("$.Data.CardList.*.Body.*.ItemData");
        System.out.println("[SEARCH] bookList count = " + ((List<?>) items).size());
        System.out.println("[SEARCH] name = " + JsonPath.read(((List<?>) items).get(0), "$.BookName"));
        System.out.println("[SEARCH] author = " + JsonPath.read(((List<?>) items).get(0), "$.AuthorName"));
        System.out.println("[SEARCH] kind = " + JsonPath.read(((List<?>) items).get(0), "$.CategoryName"));
        System.out.println("[SEARCH] wordCount = " + JsonPath.read(((List<?>) items).get(0), "$.WordsCount"));
        Object bookUrl = substitute("https://full.hnxianxin.cn/qd/detail.php?book_id={{$.BookId}}", ((List<?>) items).get(0));
        System.out.println("[SEARCH] bookUrl = " + bookUrl);
        if (((List<?>) items).size() > 0 && bookUrl.toString().contains("book_id=")) { pass++; } else { fail++; System.out.println("  !! FAIL"); }

        // ---------- DETAIL ----------
        ReadContext detail = JsonPath.parse(load("qd_detail.json"));
        String dName = detail.read("$.Data.BaseBookInfo.BookName");
        String dAuthor = detail.read("$.Data.AuthorInfo.Author");
        String dIntro = detail.read("$.Data.BaseBookInfo.Description");
        String dKind = detail.read("$.Data.BaseBookInfo.CategoryName");
        String dLast = detail.read("$.Data.BaseBookInfo.ChapterInfo.LastUpdateChapterName");
        String tocUrl = substitute("https://full.hnxianxin.cn/qd/catalog.php?book_id={{$.Data.BaseBookInfo.BookId}}", detail.read("$"));
        String coverUrl = substitute("https://bookcover.yuewen.com/qdbimg/349573/{{$.Data.BaseBookInfo.BookId}}/180", detail.read("$"));
        System.out.println("[DETAIL] name=" + dName);
        System.out.println("[DETAIL] author=" + dAuthor);
        System.out.println("[DETAIL] kind=" + dKind);
        System.out.println("[DETAIL] lastChapter=" + dLast);
        System.out.println("[DETAIL] introLen=" + (dIntro == null ? -1 : dIntro.length()));
        System.out.println("[DETAIL] tocUrl=" + tocUrl);
        System.out.println("[DETAIL] coverUrl=" + coverUrl);
        if (dName != null && !dName.isEmpty() && tocUrl.contains("catalog.php?book_id=1045086787")) { pass++; } else { fail++; System.out.println("  !! FAIL"); }

        // ---------- CATALOG (TOC) ----------
        ReadContext catalog = JsonPath.parse(load("qd_catalog.json"));
        List<?> chs = catalog.read("$.Data.Chapters[?(@.C > 0)]");
        System.out.println("[TOC] chapterList(filter C>0) count = " + chs.size());
        Object first = chs.get(0);
        System.out.println("[TOC] chapterName = " + JsonPath.read(first, "$.N"));
        String realChUrl = "https://full.hnxianxin.cn/qd/content.php?book_id=1045086787&chapter_id=" + JsonPath.read(first, "$.C");
        String cPart = substitute("{{$.C}}", first);
        System.out.println("[TOC] chapter_id substitute = " + cPart);
        String detailUrl = "https://full.hnxianxin.cn/qd/detail.php?book_id=1045086787";
        String bid = detailUrl.replaceAll(".*book_id=(\\d+).*", "$1");
        String finalChUrl = "https://full.hnxianxin.cn/qd/content.php?book_id=" + bid + "&chapter_id=" + cPart;
        System.out.println("[TOC] final chapterUrl = " + finalChUrl);
        System.out.println("[TOC] expected          = " + realChUrl);
        if (chs.size() > 0 && finalChUrl.equals(realChUrl)) { pass++; } else { fail++; System.out.println("  !! FAIL"); }
        System.out.println("[TOC] isVip($.V) first = " + JsonPath.read(first, "$.V"));

        // ---------- CONTENT ----------
        ReadContext content = JsonPath.parse(load("qd_content.json"));
        String body = content.read("$.Content");
        System.out.println("[CONTENT] $.Content len = " + (body == null ? -1 : body.length()));
        if (body != null && body.length() > 100) { pass++; } else { fail++; System.out.println("  !! FAIL"); }

        System.out.println("========================");
        System.out.println("PASS=" + pass + " FAIL=" + fail);
    }
}
