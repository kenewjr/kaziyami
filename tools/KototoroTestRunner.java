package app.kaziyami.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Executes Kototoro's 7-step test harness against an Aniyomi AnimeSource instance.
 * Replicates SourceTester.kt from org.skepsun.kototoro.settings.sources.test.
 */
public class KototoroTestRunner {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: KototoroTestRunner <extClass> <fixtureDir> [--live]");
            System.exit(1);
        }

        String extClass = args[0];
        File fixtureDir = new File(args[1]);
        boolean isLive = args.length > 2 && "--live".equalsIgnoreCase(args[2]);

        try {
            Class<?> clazz = Class.forName(extClass);
            Object instance = clazz.getDeclaredConstructor().newInstance();

            boolean isFactory = false;
            for (Class<?> iface : clazz.getInterfaces()) {
                if ("eu.kanade.tachiyomi.animesource.AnimeSourceFactory".equals(iface.getName())) {
                    isFactory = true;
                    break;
                }
            }

            Object source = instance;
            if (isFactory) {
                Method createSources = clazz.getMethod("createSources");
                List<?> sources = (List<?>) createSources.invoke(instance);
                if (sources.isEmpty()) {
                    System.err.println("No sources returned by factory " + extClass);
                    System.exit(2);
                }
                source = sources.get(0);
            }

            String jsonResult = runKototoroTest(source, fixtureDir, isLive);
            System.out.println(jsonResult);
        } catch (Throwable t) {
            System.err.println("KOTOTORO_TEST_RUNNER_ERROR: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(3);
        }
    }

    private static String runKototoroTest(Object source, File fixtureDir, boolean isLive) throws Exception {
        Class<?> sc = source.getClass();
        long id = (Long) sc.getMethod("getId").invoke(source);
        String name = (String) sc.getMethod("getName").invoke(source);
        String baseUrl = (String) sc.getMethod("getBaseUrl").invoke(source);

        String sourceKey = "ANIYOMI_" + id;

        boolean listOk = false;
        boolean coversOk = false;
        boolean detailsOk = false;
        boolean chaptersOk = false;
        boolean pagesOk = false;
        boolean randomOk = false; // Always skip
        boolean searchOk = false;

        String listMsg = "";
        String coversMsg = "";
        String detailsMsg = "";
        String chaptersMsg = "";
        String pagesMsg = "";
        String randomMsg = "Unsupported";
        String searchMsg = "";

        List<?> animes = null;
        Object sampleAnime = null;
        List<?> chapters = null;
        Object sampleChapter = null;
        List<?> videos = null;

        // ================= 1. LIST =================
        try {
            if (isLive) {
                Request req = (Request) sc.getMethod("popularAnimeRequest", int.class).invoke(source, 1);
                Response resp = executeRequest(source, req);
                Object animesPage = sc.getMethod("popularAnimeParse", Response.class).invoke(source, resp);
                animes = (List<?>) animesPage.getClass().getMethod("getAnimes").invoke(animesPage);
            } else {
                String html = readFixture(fixtureDir, "popular.html");
                Response resp = mockResponse(baseUrl, html);
                Object animesPage = sc.getMethod("popularAnimeParse", Response.class).invoke(source, resp);
                animes = (List<?>) animesPage.getClass().getMethod("getAnimes").invoke(animesPage);
            }

            if (animes != null && !animes.isEmpty()) {
                listOk = true;
                listMsg = "Count: " + animes.size();
                sampleAnime = animes.get(0);
            } else {
                listMsg = "Anime list is empty";
            }
        } catch (Throwable t) {
            listMsg = formatError(t);
        }

        // ================= 2. COVERS =================
        if (listOk && sampleAnime != null) {
            try {
                String thumb = (String) sampleAnime.getClass().getMethod("getThumbnail_url").invoke(sampleAnime);
                if (thumb != null && (thumb.startsWith("http://") || thumb.startsWith("https://"))) {
                    coversOk = true;
                    coversMsg = "1/1 covers valid";
                } else {
                    coversMsg = "No valid cover URL found";
                }
            } catch (Throwable t) {
                coversMsg = formatError(t);
            }
        }

        // ================= 3. DETAILS & CHAPTERS =================
        if (listOk && sampleAnime != null) {
            try {
                Object details = null;
                if (isLive) {
                    Request req = (Request) sc.getMethod("animeDetailsRequest", sampleAnime.getClass()).invoke(source, sampleAnime);
                    Response resp = executeRequest(source, req);
                    details = sc.getMethod("animeDetailsParse", Response.class).invoke(source, resp);

                    Request epReq = (Request) sc.getMethod("episodeListRequest", sampleAnime.getClass()).invoke(source, sampleAnime);
                    Response epResp = executeRequest(source, epReq);
                    chapters = (List<?>) sc.getMethod("episodeListParse", Response.class).invoke(source, epResp);
                } else {
                    String html = readFixture(fixtureDir, "details.html");
                    Response resp = mockResponse(baseUrl, html);
                    details = sc.getMethod("animeDetailsParse", Response.class).invoke(source, resp);

                    File epFile = new File(fixtureDir, "episodes.html");
                    String epHtml = epFile.exists() ? readFixture(fixtureDir, "episodes.html") : html;
                    Response epResp = mockResponse(baseUrl, epHtml);
                    chapters = (List<?>) sc.getMethod("episodeListParse", Response.class).invoke(source, epResp);
                }

                if (details != null && chapters != null && !chapters.isEmpty()) {
                    detailsOk = true;
                    String title = (String) details.getClass().getMethod("getTitle").invoke(details);
                    detailsMsg = "Details loaded: " + title;

                    chaptersOk = true;
                    chaptersMsg = "Chapters: " + chapters.size();
                    sampleChapter = chapters.get(0);
                } else if (chapters == null || chapters.isEmpty()) {
                    detailsMsg = "No chapters found in details";
                    chaptersMsg = "Episode list is empty";
                }
            } catch (Throwable t) {
                detailsMsg = formatError(t);
                chaptersMsg = formatError(t);
            }
        }

        // ================= 5. PAGES =================
        if (chaptersOk && sampleChapter != null) {
            try {
                if (isLive) {
                    Request req = (Request) sc.getMethod("videoListRequest", sampleChapter.getClass()).invoke(source, sampleChapter);
                    Response resp = executeRequest(source, req);
                    videos = (List<?>) sc.getMethod("videoListParse", Response.class).invoke(source, resp);
                } else {
                    String html = readFixture(fixtureDir, "video_player.html");
                    Response resp = mockResponse(baseUrl, html);
                    videos = (List<?>) sc.getMethod("videoListParse", Response.class).invoke(source, resp);
                }

                if (videos != null && !videos.isEmpty()) {
                    Object firstVideo = videos.get(0);
                    String videoUrl = (String) firstVideo.getClass().getMethod("getVideoUrl").invoke(firstVideo);
                    if (videoUrl == null) {
                        videoUrl = (String) firstVideo.getClass().getMethod("getUrl").invoke(firstVideo);
                    }
                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        pagesOk = true;
                        pagesMsg = "Playable video streams: " + videos.size();
                    } else {
                        pagesMsg = "Page list is empty";
                    }
                } else {
                    pagesMsg = "Page list is empty";
                }
            } catch (Throwable t) {
                pagesMsg = formatError(t);
            }
        }

        // ================= 6. RANDOM =================
        // Permanently skipped as in Kototoro upstream
        randomOk = false;

        // ================= 7. SEARCH =================
        try {
            if (isLive) {
                Method getFilters = sc.getMethod("getFilterList");
                Object filterList = getFilters.invoke(source);
                Request req = (Request) sc.getMethod("searchAnimeRequest", int.class, String.class, filterList.getClass())
                    .invoke(source, 1, "a", filterList);
                Response resp = executeRequest(source, req);
                Object page = sc.getMethod("searchAnimeParse", Response.class).invoke(source, resp);
                List<?> results = (List<?>) page.getClass().getMethod("getAnimes").invoke(page);
                if (results != null && !results.isEmpty()) {
                    searchOk = true;
                    searchMsg = "Query 'a' returned " + results.size() + " hits";
                } else {
                    searchMsg = "Search empty for query: a";
                }
            } else {
                String html = readFixture(fixtureDir, "search.html");
                Response resp = mockResponse(baseUrl, html);
                Object page = sc.getMethod("searchAnimeParse", Response.class).invoke(source, resp);
                List<?> results = (List<?>) page.getClass().getMethod("getAnimes").invoke(page);
                if (results != null && !results.isEmpty()) {
                    searchOk = true;
                    searchMsg = "Hits: " + results.size();
                } else {
                    searchMsg = "Search empty";
                }
            }
        } catch (Throwable t) {
            searchMsg = formatError(t);
        }

        boolean overallPass = listOk && coversOk && detailsOk && chaptersOk && pagesOk && searchOk;
        String overallStatus = overallPass ? "pass" : "fail";

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"name\":\"").append(sourceKey).append("\",");
        sb.append("\"status\":\"").append(overallStatus).append("\",");
        sb.append("\"steps\":{");
        sb.append("\"LIST\":\"").append(listOk ? "pass" : "fail").append("\",");
        sb.append("\"COVERS\":\"").append(!listOk ? "skip" : (coversOk ? "pass" : "fail")).append("\",");
        sb.append("\"DETAILS\":\"").append(!listOk ? "skip" : (detailsOk ? "pass" : "fail")).append("\",");
        sb.append("\"CHAPTERS\":\"").append(!detailsOk ? "skip" : (chaptersOk ? "pass" : "fail")).append("\",");
        sb.append("\"PAGES\":\"").append(!chaptersOk ? "skip" : (pagesOk ? "pass" : "fail")).append("\",");
        sb.append("\"RANDOM\":\"skip\",");
        sb.append("\"SEARCH\":\"").append(searchOk ? "pass" : "fail").append("\"");
        sb.append("}");

        if (!overallPass) {
            sb.append(",\"messages\":{");
            boolean first = true;
            if (!listOk) {
                sb.append("\"LIST\":\"").append(escapeJson(listMsg)).append("\"");
                first = false;
            }
            if (listOk && !coversOk) {
                if (!first) sb.append(",");
                sb.append("\"COVERS\":\"").append(escapeJson(coversMsg)).append("\"");
                first = false;
            }
            if (listOk && !detailsOk) {
                if (!first) sb.append(",");
                sb.append("\"DETAILS\":\"").append(escapeJson(detailsMsg)).append("\"");
                first = false;
            }
            if (detailsOk && !chaptersOk) {
                if (!first) sb.append(",");
                sb.append("\"CHAPTERS\":\"").append(escapeJson(chaptersMsg)).append("\"");
                first = false;
            }
            if (chaptersOk && !pagesOk) {
                if (!first) sb.append(",");
                sb.append("\"PAGES\":\"").append(escapeJson(pagesMsg)).append("\"");
                first = false;
            }
            if (!searchOk) {
                if (!first) sb.append(",");
                sb.append("\"SEARCH\":\"").append(escapeJson(searchMsg)).append("\"");
            }
            sb.append("}");
        }

        sb.append("}");
        return sb.toString();
    }

    private static Response executeRequest(Object source, Request req) throws Exception {
        Method getClient = source.getClass().getMethod("getClient");
        OkHttpClient client = (OkHttpClient) getClient.invoke(source);
        return client.newCall(req).execute();
    }

    private static Response mockResponse(String url, String bodyContent) {
        Request req = new Request.Builder().url(url).build();
        ResponseBody body = ResponseBody.Companion.create(bodyContent, MediaType.parse("text/html; charset=utf-8"));
        return new Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build();
    }

    private static String readFixture(File dir, String filename) throws Exception {
        File f = new File(dir, filename);
        if (!f.exists()) {
            throw new IllegalArgumentException("Fixture missing: " + f.getAbsolutePath());
        }
        try (InputStreamReader isr = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            StringBuilder sb = new StringBuilder();
            int read;
            while ((read = isr.read(buf)) != -1) {
                sb.append(buf, 0, read);
            }
            return sb.toString();
        }
    }

    private static String formatError(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return t.getClass().getSimpleName() + ": " + (msg.length() > 160 ? msg.substring(0, 160) : msg);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}

