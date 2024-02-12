package searchengine.parser;

import lombok.Getter;
import lombok.Setter;
import searchengine.model.Site;


@Getter
@Setter
public class PageLoaderInfo {
    private Site site;
    private String link;
    private String html;

    public PageLoaderInfo(Site site, String link, String html) {
        this.site = site;
        this.link = link;
        this.html = html;
    }
}
