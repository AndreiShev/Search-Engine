package searchengine;

import java.io.IOException;
import java.util.*;


import static searchengine.TestForTest.linksMap;

public class TestThread implements Runnable {
    private Map<String, Integer> partMap;
    private static Integer maxThreadCount;
    private String name;

    public TestThread(Map<String, Integer> partMap, Integer maxThreadCount) {
        this.partMap = partMap;
        this.maxThreadCount = maxThreadCount;
    }

    @Override
    public void run() {
        try {
            recurseParse();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void recurseParse() throws IOException, InterruptedException {

        for (String item: partMap.keySet()) {
            System.out.println(item);
//            if (linksMap.get(item) == 0) {
//                System.out.println(item);
//            }
        }

        if (linksMap.values().contains(0)) {
            //System.out.println("тест тест тест");
            partMap.clear();
            partMap.putAll(resetPartMap());
            recurseParse();
        }
    }

    public static synchronized Map<String, Integer>  resetPartMap() throws InterruptedException {
        Map<String, Integer> result = new HashMap<>();

        int sizePart = 0;
        List<String> unIndexingUrl = getUnIndexingUrl();

        if (unIndexingUrl.size() < maxThreadCount) {
            sizePart = unIndexingUrl.size();
        } else {
            sizePart = unIndexingUrl.size() / maxThreadCount;
        }

        int count = 0;

        for (String item: unIndexingUrl) {
            if (count >= sizePart) {
                break;
            }

            if (linksMap.get(item) == 0 && count <= sizePart) {
                result.put(item, linksMap.get(item));
                linksMap.replace(item, 1);
                count++;
            }
        }
        return result;
    }

    public static List<String> getUnIndexingUrl() {
        List<String> result = new ArrayList<>();
        for (String item: linksMap.keySet()) {
            if (linksMap.get(item) == 0) {
                result.add(item);
            }
        }

        return result;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
