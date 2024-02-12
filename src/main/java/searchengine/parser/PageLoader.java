package searchengine.parser;

import lombok.Getter;
import lombok.Setter;
import searchengine.model.Site;
import searchengine.utils.IndexingUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import static searchengine.parser.ParserWrapper.*;

@Setter
@Getter
public class PageLoader extends Thread {
    private ParserWrapper parserWrapper;
    private Site site;
    private Set<String> linkSet = new HashSet<>();
    private final int limitPage = 200;
    private final int optimalQuantity = 50;

    public PageLoader(ParserWrapper parserWrapper, Site site) {
        this.parserWrapper = parserWrapper;
        this.site = site;
    }

    /**
     * Метод запускает индексацию списка выбранных страниц при их достаточном количестве. В противном случае уменьшает
     * время ожидания для потоков, занимающихся скрапингом. Увеличение/уменьшение времени ожидания необходимо для
     * регулировки использования оперативной памяти. Если объектов накапливается больше лимита, значит загрузчик
     * страниц не успевает их загружать, из-за чего объекты будут копиться в памяти.
     */
    @Override
    public void run() {
        while(true) {
            try {
                if (pageLoaderMap.size() == 0) {
                    adjustSleepTime(0);
                    Thread.sleep(5000);
                    continue;
                }

                updateLinkSet();
                IndexingUtils.addLemmas(site, linkSet, lemmaMap, indexDataSet);
                clearLinksDataMap();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            } finally {
                clearLinksDataMap();
            }
        }
    }

    /**
     * Метод забирает страницы для индексации. Если страниц больше заданного лимита, то он повышает время ожидания для
     * потоков, занимающихся скрапингом.
     */
    private void updateLinkSet() {
        linkSet = pageLoaderMap.entrySet().stream()
                .map(Map.Entry::getKey)
                .limit(limitPage)
                .collect(Collectors.toSet());

        adjustSleepTime(linkSet.size());

        for (String item : linkSet) {
            pageLoaderMap.remove(item);
        }
    }

    /**
     * Метод корректирует время сна потоков парсера. Если количество найденных ссылок равно лимиту - время сна
     * увеличивается. Если количество ссылок меньше оптимального - время сна уменьшается. Время сна может уменьшаться
     * пока не достигнет своей нижней границы, заданной в ParserWrapper.
     */
    private void adjustSleepTime(int count) {
        if (count == limitPage) {
            parserSleepTime += 300;
        }

        if (count < optimalQuantity) {
            parserSleepTime = parserSleepTime - 100 > minWaitingTime ? parserSleepTime - 100 : parserSleepTime;
        }
    }


    /**
     * Метод очищает ссылки и связанные с ними данные после завершения итерации лемматизации.
     */
    private void clearLinksDataMap() {
        linkSet.clear();
        lemmaMap.clear();
        indexDataSet.clear();
    }
}
