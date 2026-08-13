import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import java.nio.file.*;
import java.util.*;

/** Tests which Jayway JSONPath filter keeps only BookType==1 (读小说) items. */
public class VerifyFilter {

    static String load(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("fixtures/" + name)));
    }

    static void dump(String label, List<?> list) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (Object o : list) {
            String bt = "?";
            try { bt = JsonPath.read(o, "$.BookType").toString(); } catch (Exception ignored) {}
            dist.merge(bt, 1, Integer::sum);
        }
        System.out.println(label + " 条数=" + list.size() + " BookType分布=" + dist);
    }

    public static void main(String[] args) throws Exception {
        ReadContext search = JsonPath.parse(load("qd_search_mixed.json"));

        // 候选1: 在 ItemData 后过滤(可能失败,因为ItemData是对象不是数组)
        try {
            List<?> r1 = search.read("$.Data.CardList[*].Body[*].ItemData[?(@.BookType == 1)]");
            dump("候选1 ItemData[?()]:", r1);
        } catch (Exception e) {
            System.out.println("候选1 抛异常: " + e.getMessage());
        }

        // 候选2: 在 Body 对象上过滤, 再取 .ItemData
        try {
            List<?> r2 = search.read("$.Data.CardList[*].Body[*][?(@.ItemData.BookType == 1)].ItemData");
            dump("候选2 Body[?()].ItemData:", r2);
        } catch (Exception e) {
            System.out.println("候选2 抛异常: " + e.getMessage());
        }

        // 候选3: 不带 Body[*] 通配, 直接在 Body 数组过滤
        try {
            List<?> r3 = search.read("$.Data.CardList[*].Body[?(@.ItemData.BookType == 1)].ItemData");
            dump("候选3 Body[?()].ItemData:", r3);
        } catch (Exception e) {
            System.out.println("候选3 抛异常: " + e.getMessage());
        }

        // 候选4: 不过滤, 用 JS 思路 —— 检查 undefined BookType 的条目是什么
        List<?> all = search.read("$.Data.CardList[*].Body[*].ItemData");
        for (Object o : all) {
            String bt = "?";
            try { bt = JsonPath.read(o, "$.BookType").toString(); } catch (Exception ignored) {}
            if (!"1".equals(bt)) {
                String n = "?"; try { n = JsonPath.read(o, "$.BookName").toString(); } catch (Exception ignored) {}
                System.out.println("非小说条目: BookType=" + bt + " BookName=" + n);
            }
        }

        // ---------- EXPLORE (ranking) 过滤 ----------
        ReadContext ranking = JsonPath.parse(load("qd_ranking.json"));
        List<?> rk1 = ranking.read("$.Data.Books[?(@.BookType == 1)]");
        dump("发现过滤 Books[?()]:", rk1);
        List<?> rkAll = ranking.read("$.Data.Books");
        System.out.println("发现总数=" + rkAll.size());

        System.out.println("========================");
    }
}
