package searchengine.parserData;

import lombok.Data;
import searchengine.model.Site;


@Data
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
