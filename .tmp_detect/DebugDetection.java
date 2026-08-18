import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.*;

public class DebugDetection {
    static final Pattern NEW_FETCH = Pattern.compile(
        "fetch\\s*\\(\\s*[`'\"]([^`'\"]+)[`'\"]|" +
            "axios\\s*\\.\\s*(?:get|post|put|delete|request)\\s*\\(\\s*[`'\"]([^`'\"]+)[`'\"]|" +
            "axios\\s*\\(\\s*\\{\\s*(?:[^{}]*?)\\burl\\s*:\\s*[`'\"]([^`'\"]+)[`'\"]|" +
            "\\$\\s*\\.\\s*(?:get|post|getJSON|ajax)\\s*\\(\\s*[`'\"]([^`'\"]+)[`'\"]|" +
            "\\$\\s*\\.\\s*(?:get|post|ajax)\\s*\\(\\s*\\{\\s*(?:[^{}]*?)\\burl\\s*:\\s*[`'\"]([^`'\"]+)[`'\"]|" +
            "(?:uni\\.request|request)\\s*\\(\\s*\\{\\s*url\\s*:\\s*[`'\"]([^`'\"]+)[`'\"]|" +
            "XMLHttpRequest[^;]*?\\.\\s*open\\s*\\(\\s*['\"](?:GET|POST)['\"]\\s*,\\s*[`'\"]([^`'\"]+)[`'\"]|" +
            "new\\s+Request\\s*\\(\\s*[`'\"]([^`'\"]+)[`'\"]",
        Pattern.CASE_INSENSITIVE
    );
    static final Pattern OLD_FETCH = Pattern.compile(
        "fetch\\s*\\(\\s*[`'\"]([^`'\"]+)[`'\"]|" +
            "axios\\s*\\.\\s*(?:get|post|put|delete|request)\\s*\\(\\s*[`'\"]([^`'\"]+)[`'\"]|" +
            "axios\\s*\\(\\s*\\{\\s*url\\s*:\\s*[`'\"]([^`'\"]+)[`'\"]|" +
            "\\$\\s*\\.\\s*(?:get|post|ajax)\\s*\\(\\s*[`'\"]([^`'\"]+)[`'\"]|" +
            "(?:uni\\.request|request)\\s*\\(\\s*\\{\\s*url\\s*:\\s*[`'\"]([^`'\"]+)[`'\"]|" +
            "XMLHttpRequest[^;]*?\\.\\s*open\\s*\\(\\s*['\"](?:GET|POST)['\"]\\s*,\\s*[`'\"]([^`'\"]+)[`'\"]",
        Pattern.CASE_INSENSITIVE
    );
    static final Pattern postMethodPattern = Pattern.compile(
        "(?:method|type)\\s*[:=]\\s*['\"]POST['\"]", Pattern.CASE_INSENSITIVE);
    static final Pattern postBodyPattern = Pattern.compile(
        "(?:body|data)\\s*[:=]\\s*[`'\"]([^`'\"]+)[`'\"]", Pattern.CASE_INSENSITIVE);
    static final Pattern postBodyObjectPattern = Pattern.compile(
        "(?:body|data)\\s*[:=]\\s*(?:JSON\\s*\\.\\s*stringify\\s*\\(\\s*)?\\{\\s*([^}]*)\\}", Pattern.CASE_INSENSITIVE);
    static final Pattern implicitPostPattern = Pattern.compile(
        "(?:axios|http|https?)\\s*\\.\\s*post\\s*\\(|(?:\\$|jQuery)\\s*\\.\\s*post\\s*\\(", Pattern.CASE_INSENSITIVE);
    static final Pattern rawObjectBodyPattern = Pattern.compile(
        "(?:\\)|['\"])\\s*,\\s*\\{\\s*([^{}]*)\\}", Pattern.CASE_INSENSITIVE);
    static final Set<String> searchParamNames = new HashSet<>(Arrays.asList(
        "wd", "q", "kw", "so", "query", "key", "word", "find",
        "keyword", "searchkey", "searchword", "search_key", "skey", "keywrod",
        "bookname", "name", "searchtext", "searchvalue", "searchname", "novelname",
        "searchs", "sousuo", "sosuo", "txtname", "articlename"));
    static final Set<String> keywordVars = new HashSet<>(Arrays.asList(
        "keyword", "kw", "q", "wd", "name", "key", "searchkey", "searchword",
        "bookname", "word", "novelname", "searchname", "searchtext", "searchvalue",
        "keys", "sou", "so", "find", "query"));
    static final Set<String> idParamNames = new HashSet<>(Arrays.asList(
        "id", "book_id", "bookid", "novel_id", "novelid", "book", "detail", "bookinfo"));
    static final Pattern keywordConcatPattern = Pattern.compile(
        "\\s*\\+\\s*(?:encodeURIComponent\\s*\\(\\s*)?[`'\"]?\\s*(keyword|kw|q|wd|name|key|searchkey|searchword|bookname|word|novelname|searchname|searchtext|searchvalue|keys|so|sou|find|query)\\s*[`'\"]?\\s*(?:\\)\\s*)?(?=[+\\-;,&?)}])",
        Pattern.CASE_INSENSITIVE);
    static final Pattern blankSearchParamPattern = Pattern.compile(
        "([?&])(?:keyword|searchKey|search_keyword|search_key|searchkey|searchword|searchtext|searchvalue|keywrod|novelname|wd|q|kw|so|query|word|find|bookname|name|skey|key)=([^&]*)",
        Pattern.CASE_INSENSITIVE);
    static final List<Map.Entry<String, String>> templateReplacements = Arrays.asList(
        new AbstractMap.SimpleEntry<>("\\$\\{encodeURIComponent\\s*\\(\\s*keyword\\s*\\)\\}", "{{key}}"),
        new AbstractMap.SimpleEntry<>("\\$\\{\\s*keyword\\s*\\}", "{{key}}"),
        new AbstractMap.SimpleEntry<>("\\$\\{encodeURIComponent\\s*\\(\\s*page\\s*\\)\\}", "{{page}}"),
        new AbstractMap.SimpleEntry<>("\\$\\{\\s*page\\s*\\}", "{{page}}"),
        new AbstractMap.SimpleEntry<>("\\$\\{encodeURIComponent\\s*\\(\\s*bookId\\s*\\)\\}", "{{bookId}}"),
        new AbstractMap.SimpleEntry<>("\\$\\{\\s*bookId\\s*\\}", "{{bookId}}"),
        new AbstractMap.SimpleEntry<>("\\$\\{encodeURIComponent\\s*\\(\\s*chapterId\\s*\\)\\}", "{{chapterId}}"),
        new AbstractMap.SimpleEntry<>("\\$\\{\\s*chapterId\\s*\\}", "{{chapterId}}"));

    static String replaceAll(String s, String regex, String rep) {
        return s.replaceAll(regex, Matcher.quoteReplacement(rep));
    }
    static String guessType(String url, String postBody) {
        String u = url.toLowerCase(Locale.ROOT);
        if (u.contains("{{key}}")) return "search";
        if (u.contains("{{bookid}}")) return "detail";
        if (u.contains("search") || u.contains("keyword") || u.contains("searchword") ||
            u.contains("searchkey") || u.contains("find") || u.contains("sousuo") ||
            u.contains("sosuo")) return "search";
        if (u.contains("catalog") || u.contains("chapterlist") || u.contains("toc")) return "catalog";
        if (u.contains("chaptercontent") || (u.contains("content") && u.contains("chapter"))) return "content";
        if (u.contains("detail") || u.contains("bookinfo")) return "detail";
        String query = u.contains("?") ? u.substring(u.indexOf('?') + 1).replaceAll("#.*", "") : "";
        Set<String> params = new HashSet<>();
        for (String p : query.split("&")) {
            if (p.isEmpty()) continue;
            String k = p.contains("=") ? p.substring(0, p.indexOf('=')) : p;
            k = k.trim().toLowerCase(Locale.ROOT);
            if (!k.isEmpty()) params.add(k);
        }
        for (String p : params) if (searchParamNames.contains(p)) return "search";
        for (String p : params) if (idParamNames.contains(p)) return "detail";
        if (postBody != null && !postBody.isBlank()) {
            for (String b : postBody.split("&")) {
                if (b.isEmpty()) continue;
                String k = b.contains("=") ? b.substring(0, b.indexOf('=')) : b;
                k = k.trim().toLowerCase(Locale.ROOT);
                if (k.isEmpty()) continue;
                if (searchParamNames.contains(k)) return "search";
                if (idParamNames.contains(k)) return "detail";
            }
        }
        return null;
    }
    static String objectBodyToForm(String bodyObject) {
        List<String> keys = new ArrayList<>();
        Matcher m = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*:").matcher(bodyObject);
        while (m.find()) keys.add(m.group(1));
        if (keys.isEmpty()) return "";
        String searchKey = null;
        for (String k : keys) if (searchParamNames.contains(k.toLowerCase(Locale.ROOT))) { searchKey = k; break; }
        if (searchKey == null) {
            for (String k : keys) {
                for (String t : Arrays.asList("kw", "q", "wd", "key", "word")) {
                    if (k.toLowerCase(Locale.ROOT).contains(t)) { searchKey = k; break; }
                }
                if (searchKey != null) break;
            }
        }
        if (searchKey == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            if (i > 0) sb.append('&');
            sb.append(k).append('=').append(k.equals(searchKey) ? "{{key}}" : "");
        }
        return sb.toString();
    }
    static String fillBlankSearchParams(String url, String kw) {
        Matcher m = blankSearchParamPattern.matcher(url);
        StringBuffer sb = new StringBuffer();
        boolean changed = false;
        while (m.find()) {
            if (m.group(2).isBlank()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + m.group(2) + "=" + kw));
                changed = true;
            }
        }
        m.appendTail(sb);
        return changed ? sb.toString() : url;
    }
    static class Ep {
        String type, url, method = "GET";
        String postBody = "";
        Ep(String t, String u, String m) { type = t; url = u; method = m; }
        public String toString() {
            return method + " " + type + " " + url + (postBody.isEmpty() ? "" : " body=" + postBody);
        }
    }
    static List<Ep> newDiscover(String html, String baseUrl) {
        LinkedHashMap<String, Ep> found = new LinkedHashMap<>();
        LinkedHashMap<String, Ep> candidates = new LinkedHashMap<>();
        Matcher matcher = NEW_FETCH.matcher(html);
        while (matcher.find()) {
            String template = null;
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String g = matcher.group(i);
                if (g != null && !g.isBlank()) { template = g; break; }
            }
            if (template == null) continue;
            if (template.startsWith("$")) continue;
            String cleaned = template;
            for (Map.Entry<String, String> e : templateReplacements) cleaned = replaceAll(cleaned, e.getKey(), e.getValue());
            cleaned = cleaned.replaceAll("\\$\\{[^}]*\\}", "");
            if (cleaned.isBlank()) continue;
            String tail = html.substring(matcher.end(), Math.min(html.length(), matcher.end() + 200));
            Matcher cm = keywordConcatPattern.matcher(tail);
            if (cm.lookingAt() && keywordVars.contains(cm.group(1).toLowerCase(Locale.ROOT))) {
                String lastSeg = cleaned.substring(cleaned.lastIndexOf('/') + 1);
                if (cleaned.endsWith("=") || cleaned.endsWith("/") || !lastSeg.contains(".")) {
                    cleaned += "{{key}}";
                }
            }
            String resolved;
            try { resolved = new URL(new URL(baseUrl), cleaned).toString(); }
            catch (Exception e) { continue; }
            if (resolved.isBlank() || !resolved.startsWith("http")) continue;
            int contextStart = Math.max(0, matcher.start() - 120);
            String context = html.substring(contextStart, Math.min(html.length(), matcher.end() + 200));
            String matched = matcher.group();
            boolean isPost = implicitPostPattern.matcher(matched).find() || postMethodPattern.matcher(context).find();
            String postBody = "";
            if (isPost) {
                Matcher pbm = postBodyPattern.matcher(context);
                if (pbm.find()) postBody = pbm.group(1) != null ? pbm.group(1) : "";
                if (postBody.isEmpty()) {
                    Matcher obm = postBodyObjectPattern.matcher(context);
                    if (obm.find()) postBody = objectBodyToForm(obm.group(1) != null ? obm.group(1) : "");
                }
                if (postBody.isEmpty()) {
                    Matcher robm = rawObjectBodyPattern.matcher(context);
                    if (robm.find()) postBody = objectBodyToForm(robm.group(1) != null ? robm.group(1) : "");
                }
            }
            String type = guessType(resolved, postBody);
            if (type == null) {
                if (!candidates.containsKey(resolved)) {
                    Ep ep = new Ep("other", resolved, isPost ? "POST" : "GET");
                    if (isPost && !postBody.isBlank()) ep.postBody = postBody;
                    candidates.put(resolved, ep);
                }
                continue;
            }
            Ep existing = found.get(type);
            boolean better;
            if (existing == null) better = true;
            else if (type.equals("search") && cleaned.contains("keyword") && !existing.url.contains("{{key}}")) better = true;
            else if (type.equals("detail") && cleaned.contains("bookid") && !existing.url.contains("{{bookId}}")) better = true;
            else better = false;
            if (better) {
                Ep ep = new Ep(type, resolved, isPost ? "POST" : "GET");
                if (isPost && !postBody.isBlank()) ep.postBody = postBody;
                found.put(type, ep);
            }
        }
        List<Ep> forms = newFormEndpoints(html, baseUrl);
        for (Ep fe : forms) {
            if (!fe.type.equals("other") && !found.containsKey(fe.type)) found.put(fe.type, fe);
        }
        List<Ep> result = new ArrayList<>();
        for (String t : Arrays.asList("search", "detail", "catalog", "content")) {
            Ep e = found.get(t);
            if (e != null) result.add(e);
        }
        int n = 0;
        for (Ep c : candidates.values()) { if (n++ >= 8) break; result.add(c); }
        return result;
    }
    static List<Ep> newFormEndpoints(String html, String baseUrl) {
        LinkedHashMap<String, Ep> found = new LinkedHashMap<>();
        Pattern inputNameRegex = Pattern.compile("<input\\b[^>]*\\bname\\s*=\\s*[\"']?([A-Za-z_][A-Za-z0-9_]*)[\"']?", Pattern.CASE_INSENSITIVE);
        Pattern formTagRegex = Pattern.compile("<form\\b[^>]*>", Pattern.CASE_INSENSITIVE);
        Matcher fm = formTagRegex.matcher(html);
        while (fm.find()) {
            String tag = fm.group();
            Matcher am = Pattern.compile("\\baction\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(tag);
            String action = null;
            if (am.find()) {
                String a = am.group(1).trim();
                if (!a.isBlank() && !a.startsWith("#") && !a.startsWith("javascript:")) action = a;
            }
            if (action == null) action = baseUrl;
            String resolved;
            try { resolved = new URL(new URL(baseUrl), action).toString(); }
            catch (Exception e) { continue; }
            if (resolved.isBlank() || !resolved.startsWith("http")) continue;
            String formBody = html.substring(fm.end(), Math.min(html.length(), fm.end() + 800));
            boolean isPost = Pattern.compile("\\bmethod\\s*=\\s*[\"']post[\"']", Pattern.CASE_INSENSITIVE).matcher(tag).find();
            String inputName = null;
            Matcher im = inputNameRegex.matcher(formBody);
            while (im.find()) {
                String n = im.group(1);
                if (searchParamNames.contains(n.toLowerCase(Locale.ROOT))) { inputName = n; break; }
            }
            if (inputName == null) {
                Matcher im2 = inputNameRegex.matcher(formBody);
                if (im2.find()) inputName = im2.group(1);
            }
            boolean isSearchForm = inputName != null && searchParamNames.contains(inputName.toLowerCase(Locale.ROOT));
            if (isSearchForm) {
                if (isPost) {
                    if (!found.containsKey("search")) {
                        Ep ep = new Ep("search", resolved, "POST");
                        ep.postBody = inputName + "={{key}}";
                        found.put("search", ep);
                    }
                } else {
                    String sep = resolved.contains("?") ? "&" : "?";
                    if (!found.containsKey("search")) {
                        found.put("search", new Ep("search", resolved + sep + inputName + "={{key}}", "GET"));
                    }
                }
                continue;
            }
            String type = guessType(resolved, null);
            if (type == null) continue;
            if (!found.containsKey(type)) {
                Ep ep = new Ep(type, resolved, isPost ? "POST" : "GET");
                if (isPost && inputName != null) ep.postBody = inputName + "={{key}}";
                found.put(type, ep);
            }
        }
        return new ArrayList<>(found.values());
    }
    static List<Ep> oldDiscover(String html, String baseUrl) {
        LinkedHashMap<String, Ep> found = new LinkedHashMap<>();
        Matcher matcher = OLD_FETCH.matcher(html);
        while (matcher.find()) {
            String template = null;
            for (int i = 1; i <= 6; i++) {
                String g = matcher.group(i);
                if (g != null && !g.isBlank()) { template = g; break; }
            }
            if (template == null) continue;
            if (template.startsWith("$")) continue;
            String cleaned = template;
            for (Map.Entry<String, String> e : templateReplacements) cleaned = replaceAll(cleaned, e.getKey(), e.getValue());
            cleaned = cleaned.replaceAll("\\$\\{[^}]*\\}", "");
            if (cleaned.isBlank()) continue;
            String type;
            if (cleaned.contains("keyword") || cleaned.contains("search")) type = "search";
            else if (cleaned.contains("catalog") || cleaned.contains("chapterlist")) type = "catalog";
            else if (cleaned.contains("content") || cleaned.contains("chapter_id") || cleaned.contains("chapterid")) type = "content";
            else if (cleaned.contains("detail") || cleaned.contains("book_id") || cleaned.contains("bookid")) type = "detail";
            else type = "other";
            if (type.equals("other")) continue;
            String resolved;
            try { resolved = new URL(new URL(baseUrl), cleaned).toString(); }
            catch (Exception e) { continue; }
            if (resolved.isBlank() || !resolved.startsWith("http")) continue;
            int contextStart = Math.max(0, matcher.start() - 120);
            String context = html.substring(contextStart, Math.min(html.length(), matcher.end() + 200));
            boolean isPost = postMethodPattern.matcher(context).find();
            String postBody = "";
            if (isPost) {
                Matcher pbm = postBodyPattern.matcher(context);
                if (pbm.find()) postBody = pbm.group(1) != null ? pbm.group(1) : "";
            }
            Ep existing = found.get(type);
            boolean better;
            if (existing == null) better = true;
            else if (type.equals("search") && cleaned.contains("keyword") && !existing.url.contains("{{key}}")) better = true;
            else if (type.equals("detail") && cleaned.contains("bookid") && !existing.url.contains("{{bookId}}")) better = true;
            else better = false;
            if (better) {
                Ep ep = new Ep(type, resolved, isPost ? "POST" : "GET");
                if (isPost && !postBody.isBlank()) ep.postBody = postBody;
                found.put(type, ep);
            }
        }
        List<Ep> result = new ArrayList<>();
        for (String t : Arrays.asList("search", "detail", "catalog", "content")) {
            Ep e = found.get(t);
            if (e != null) result.add(e);
        }
        return result;
    }
    static String buildSearchUrlOld(Ep search, String keyword) {
        String kw;
        try { kw = URLEncoder.encode(keyword, "UTF-8"); } catch (Exception e) { kw = keyword; }
        Matcher km = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)=\\{\\{key\\}\\}").matcher(search.url);
        String keyParam = km.find() ? km.group(1) : null;
        Matcher pm = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)=\\{\\{page\\}\\}").matcher(search.url);
        String pageParam = pm.find() ? pm.group(1) : null;
        String url = search.url.replace("{{key}}", kw).replace("{{page}}", "1").replaceAll("\\{\\{[^}]*\\}\\}", "");
        if (keyParam == null && !url.matches(".*[?&](?:keyword|searchKey|search_keyword|q|key)=.*")) {
            url = url.contains("?") ? url + "&keyword=" + kw : url + "?keyword=" + kw;
        }
        if (pageParam == null && !url.matches(".*[?&]page\\d*=.*")) {
            url = url.contains("?") ? url + "&page=1" : url + "?page=1";
        }
        return url;
    }
    static String buildSearchUrlNew(Ep search, String keyword) {
        String kw;
        try { kw = URLEncoder.encode(keyword, "UTF-8"); } catch (Exception e) { kw = keyword; }
        Matcher km = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)=\\{\\{key\\}\\}").matcher(search.url);
        String keyParam = km.find() ? km.group(1) : null;
        Matcher pm = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)=\\{\\{page\\}\\}").matcher(search.url);
        String pageParam = pm.find() ? pm.group(1) : null;
        String url = search.url.replace("{{key}}", kw).replace("{{page}}", "1").replaceAll("\\{\\{[^}]*\\}\\}", "");
        url = fillBlankSearchParams(url, kw);
        if (keyParam == null && !url.matches(".*[?&](?:keyword|searchKey|search_keyword|searchkey|searchword|wd|q|kw|so|query|word|find|bookname|name|skey|key)=.*")) {
            url = url.contains("?") ? url + "&keyword=" + kw : url + "?keyword=" + kw;
        }
        if (pageParam == null && !url.matches(".*[?&]page\\d*=.*")) {
            url = url.contains("?") ? url + "&page=1" : url + "?page=1";
        }
        return url;
    }
    public static void main(String[] args) throws Exception {
        String base = "https://www.example.com/";
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("A: template literal fetch with ${keyword}",
            "<script>fetch(`/api/search?keyword=${keyword}&page=${page}`)</script>");
        cases.put("B: axios.get with + keyword concat",
            "<script>axios.get('/api/search?key=' + keyword)</script>");
        cases.put("C: fetch with + q concat",
            "<script>fetch('/search?q=' + q)</script>");
        cases.put("D: $.ajax object form POST with data object",
            "<script>$.ajax({url:'/search.php',method:'post',data:{key: keyword,submit:'search'}})</script>");
        cases.put("E: axios.post with object body",
            "<script>axios.post('/api/query',{ wd: kw, page: 1 })</script>");
        cases.put("F: plain fetch with search keyword concat",
            "<script>fetch('/api/search?keyword=' + keyword)</script>");
        cases.put("G: path-based search concat",
            "<script>fetch('/search/' + name)</script>");
        cases.put("H: form GET search",
            "<form action='/search' method='get'><input name='keyword'></form>");
        cases.put("I: form POST search",
            "<form action='/s.php' method='post'><input name='key'></form>");
        cases.put("J: detail + search in same page",
            "<script>fetch('/api/book/' + id); axios.get('/api/search?q=' + q)</script>");
        cases.put("K: fetch with ${keyword} path",
            "<script>fetch(`/so/${keyword}`)</script>");
        cases.put("L: uni.request object",
            "<script>uni.request({url:'/api/search', method:'POST', data:{keyword: kw}})</script>");
        for (Map.Entry<String, String> e : cases.entrySet()) {
            String label = e.getKey();
            String html = e.getValue();
            System.out.println("===== " + label + " =====");
            List<Ep> oldEps = oldDiscover(html, base);
            List<Ep> newEps = newDiscover(html, base);
            System.out.println("  OLD: " + oldEps);
            System.out.println("  NEW: " + newEps);
            Ep oldSearch = oldEps.stream().filter(x -> x.type.equals("search")).findFirst().orElse(null);
            Ep newSearch = newEps.stream().filter(x -> x.type.equals("search")).findFirst().orElse(null);
            System.out.println("  OLD searchUrl => " + (oldSearch != null ? buildSearchUrlOld(oldSearch, "斗破苍穹") : "(no search endpoint)"));
            System.out.println("  NEW searchUrl => " + (newSearch != null ? buildSearchUrlNew(newSearch, "斗破苍穹") : "(no search endpoint)"));
        }
    }
}
