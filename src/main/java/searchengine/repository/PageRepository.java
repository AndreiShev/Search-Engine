package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;
import java.util.Set;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    List<Page> findBySiteId_Id(int id);

    Page findBySiteIdAndPath(Site site, String path);

    Page findByPath(String path);

    @Modifying
    @Transactional
    void deleteById(int id);

    @Modifying
    @Transactional
    void deleteBySiteId_Id(int id);

    int countAllBySiteId(Site site);


    @Query(value = "SELECT p.path FROM Page p WHERE p.siteId =?1")
    List<String> getListPathBySite(Site id);

    @Query(value = "SELECT p.id FROM Page p WHERE p.siteId =?1")
    List<Integer> getListIdBySite(Site id);

    List<Page> findAllBySiteId(Site site);

    @Query(value = "SELECT * FROM Page p WHERE p.id IN ?1", nativeQuery = true)
    List<Page> getByIdList(List<Integer> idList);


}

