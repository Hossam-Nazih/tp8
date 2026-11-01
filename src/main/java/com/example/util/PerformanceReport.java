package com.example.util;

import org.hibernate.SessionFactory;
import org.hibernate.stat.CacheRegionStatistics;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityManagerFactory;
import java.util.Arrays;

/**
 * Rapport tiré des statistiques Hibernate (hibernate.generate_statistics=true).
 * Les compteurs portent sur toute l'EntityManagerFactory depuis le dernier reinitialiser().
 */
public class PerformanceReport {

    private final Statistics stats;

    public PerformanceReport(EntityManagerFactory emf) {
        this.stats = emf.unwrap(SessionFactory.class).getStatistics();
    }

    public void reinitialiser() {
        stats.clear();
    }

    /**
     * Nombre d'instructions SQL réellement envoyées à la base, em.find() compris.
     * C'est le bon indicateur pour le cache : getQueryExecutionCount() ne compte que les requêtes JPQL/Criteria.
     */
    public long requetesSql() {
        return stats.getPrepareStatementCount();
    }

    public void printReport() {
        System.out.println();
        System.out.println("=== RAPPORT DE PERFORMANCE ===");
        System.out.println("Instructions SQL envoyées        : " + stats.getPrepareStatementCount());
        System.out.println("Requêtes JPQL / Criteria         : " + stats.getQueryExecutionCount());
        System.out.println("Requête la plus lente (ms)       : " + stats.getQueryExecutionMaxTime());
        System.out.println("   -> " + stats.getQueryExecutionMaxTimeQueryString());
        System.out.println("Durée totale des requêtes (ms)   : " + dureeTotaleRequetes());
        System.out.println("Entités chargées / insérées      : " + stats.getEntityLoadCount() + " / " + stats.getEntityInsertCount());
        System.out.println("Entités modifiées / supprimées   : " + stats.getEntityUpdateCount() + " / " + stats.getEntityDeleteCount());
        System.out.println("Conflits de version (optimiste)  : " + stats.getOptimisticFailureCount());
        System.out.println("Transactions réussies / total    : " + stats.getSuccessfulTransactionCount() + " / " + stats.getTransactionCount());
        System.out.println("Cache L2 hits / misses / ajouts  : " + stats.getSecondLevelCacheHitCount() + " / "
                + stats.getSecondLevelCacheMissCount() + " / " + stats.getSecondLevelCachePutCount());
        System.out.println("Cache de requêtes hits / misses  : " + stats.getQueryCacheHitCount() + " / " + stats.getQueryCacheMissCount());

        System.out.println();
        // Pas de colonne "éléments en mémoire" : JCache ne sait pas les compter et Hibernate renverrait une valeur absurde
        System.out.println(String.format("%-44s %6s %7s %7s", "Région de cache", "Hits", "Misses", "Ajouts"));
        String[] regions = stats.getSecondLevelCacheRegionNames();
        Arrays.sort(regions);
        for (String region : regions) {
            CacheRegionStatistics r = stats.getCacheRegionStatistics(region);
            if (r != null) {
                System.out.println(String.format("%-44s %6d %7d %7d", region,
                        r.getHitCount(), r.getMissCount(), r.getPutCount()));
            }
        }
    }

    // getQueryExecutionTotalTime() de l'énoncé n'existe pas dans Hibernate 5.4 :
    // on additionne, requête par requête, nombre d'exécutions x durée moyenne
    private long dureeTotaleRequetes() {
        long total = 0;
        for (String requete : stats.getQueries()) {
            QueryStatistics q = stats.getQueryStatistics(requete);
            total += q.getExecutionCount() * q.getExecutionAvgTime();
        }
        return total;
    }
}
