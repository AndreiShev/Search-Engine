package searchengine;

import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import searchengine.config.SitesList;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.SiteStatus;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utils.IndexingUtils;

import java.util.Date;
import java.util.stream.Stream;


@SpringBootTest
@ActiveProfiles("test")
public class IndexingUtilsTest {
    private static final String content = "<!doctype html>\n" +
            "<html lang=\"ru\">\n" +
            "    <head>\n" +
            "        <title>В Новой Москве подростки обманом арендовали авто, устроили ДТП и попали на видео: Происшествия: Россия: Lenta.ru</title>\n" +
            "        <meta charset=\"utf-8\">\n" +
            "        <meta content=\"#292929\" name=\"theme-color\">\n" +
            "    </head>\n" +
            "    <body id=\"body\">\n" +
            "        <ul class=\"menu__nav-list\">\n" +
            "            <li class=\"menu__nav-item\"><a class=\"menu__nav-link _is-extra\" href=\"/\">Главное</a></li>\n" +
            "            <li class=\"menu__nav-item\"><a class=\"menu__nav-link _is-extra\" href=\"/rubrics/russia/\">Россия</a></li>\n" +
            "            <li class=\"menu__nav-item\"><a class=\"menu__nav-link _is-extra\" href=\"/rubrics/world/\">Мир</a></li>\n" +
            "            <li class=\"menu__nav-item\"><a class=\"menu__nav-link _is-extra\" href=\"/rubrics/wellness/\">Забота о себе</a></li>\n" +
            "            <li class=\"menu__nav-item\"><a target=\"_blank\" aria-label=\"Открыть Вконтакте\" rel=\"noreferrer\" class=\"social-links__link\" href=\"https://vk.com/lentaru?utm_source=lentasocialbuttons&amp;utm_campaign=vk\"></a></li>\n" +
            "            <li class=\"menu__nav-item\"><a class=\"menu__nav-link _is-extra\" href=\"\">Забота о себе</a></li>\n" +
            "            <li class=\"menu__nav-item\"><a href=\"mailto:hello@skillbox.ru\" class=\"ui-footer-contacts__email f f--14\">hello@skillbox.ru</a></li>\n" +
            "            <li class=\"menu__nav-item\"><a class=\"menu__nav-link\" target=\"_blank\" rel=\"noreferrer\" href=\"https://lenta.ru/archive/\">Архив</a></li>\n" +
            "        </ul>\n" +
            "    </body>\n" +
            "</html>";
    private static Page addedPage;
    private static Site addedSite;
    private SitesList sitesList;
    private static SiteRepository siteRepository;
    private static PageRepository pageRepository;
    private static LemmaRepository lemmaRepository;
    private static IndexRepository indexRepository;
    private IndexingUtils indexingUtils;

    @Autowired
    public IndexingUtilsTest(SitesList sitesList,
                             SiteRepository siteRepository,
                             PageRepository pageRepository,
                             IndexingUtils indexingUtils,
                             LemmaRepository lemmaRepository,
                             IndexRepository indexRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.pageRepository = pageRepository;
        this.indexingUtils = indexingUtils;
    }

    @BeforeEach
    public void initialization() {
        this.addedSite = siteRepository.save(new Site(SiteStatus.INDEXED, new Date(), null, "https://www.skillbox.ru", "Skillbox"));
        this.addedPage = pageRepository.save(new Page(addedSite, "https://www.lenta.ru/path", 200, content));
    }

    @AfterEach
    public void clear() {
        pageRepository.deleteAll();
        siteRepository.deleteAll();
    }

    @ParameterizedTest
    @NullSource
    @ArgumentsSource(SitesArgumentsProvider.class)
    public void addSiteTest(Site site) {
        indexingUtils.addSite(site);

        if (site != null && !site.equals(new searchengine.model.Site())) {
            Site addedSite = siteRepository.findByName(site.getName());
            Assert.assertNotNull(addedSite);
            Assert.assertEquals(addedSite.getName(), site.getName());
        }
    }

    static class SitesArgumentsProvider implements ArgumentsProvider {
        SitesArgumentsProvider() {}

        @Override
        public Stream<Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(new Site(SiteStatus.INDEXED, new Date(), null, "https://www.lenta.ru", "Лента.ру")),
                    Arguments.of(new Site())
            );
        }
    }

    @ParameterizedTest
    @NullSource
    @ArgumentsSource(PagesArgumentsProvider.class)
    public void addPageTest(Page page) throws InterruptedException {
        Site site = siteRepository.save(new Site(SiteStatus.INDEXED, new Date(), null, "https://www.skillbox.ru", "Skillbox"));
        if (page != null && !page.equals(new Page())) {
            page.setSiteId(site);
        }
        indexingUtils.addPage(page);

        if (page != null && !page.equals(new Page())) {
            Page addedPage = pageRepository.findByPath(page.getPath());
            Assert.assertNotNull(addedPage);
            Assert.assertEquals(addedPage.getContent(), page.getContent());
        }
    }

    static class PagesArgumentsProvider implements ArgumentsProvider {
        PagesArgumentsProvider() {}

        @Override
        public Stream<Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(new Page()),
                    Arguments.of(new Page("https://www.lenta.ru/news/2023/03/12/trainings/", 200, content))
            );
        }
    }

    @ParameterizedTest
    @MethodSource("nameAndSiteStatusProvider")
    public void changeSiteStatusTest(String name, SiteStatus status, String lastError) {
        indexingUtils.changeSiteStatus(name, status, lastError);
        Site site = siteRepository.findByName(name);

        if (site != null) {
            Assert.assertEquals(site.getStatus(), status);
        }
    }

    private static Stream<Arguments> nameAndSiteStatusProvider() {
        return Stream.of(
                Arguments.of("", null, ""),
                Arguments.of("Skillbox", SiteStatus.INDEXING, "SocketTimeoutException"),
                Arguments.of("Лента.ру", SiteStatus.FAILED, ""),
                Arguments.of("PlayBack.Ru", SiteStatus.INDEXED, null)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("nameAndSiteStatusTimeProvider")
    public void changeSiteStatusTimeTest(String name) {
        Date lastDate = addedSite.getStatusTime();
        indexingUtils.changeSiteStatusTime(addedSite.getId());
        Site site = siteRepository.findByName(name);

        if (site != null) {
            Assert.assertNotEquals(lastDate, site.getStatusTime());
        }
    }

    private static Stream<Arguments> nameAndSiteStatusTimeProvider() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of("Skillbox"),
                Arguments.of("Лента.ру")
        );
    }

    @ParameterizedTest
    @ArgumentsSource(IndexedPagesArgumentsProvider.class)
    public void pageIsIndexedTest(Page page) {
        Assert.assertFalse(indexingUtils.pageIsIndexed(page));
    }

    static class IndexedPagesArgumentsProvider implements ArgumentsProvider {
        IndexedPagesArgumentsProvider() {}

        @Override
        public Stream<Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(new Page()),
                    Arguments.of(addedPage)
            );
        }
    }
}
