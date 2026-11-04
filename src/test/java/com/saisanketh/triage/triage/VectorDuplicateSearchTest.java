package com.saisanketh.triage.triage;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SET LOCAL settings of the vector search only work inside a transaction. Every public search method must start
 * one when called through the Spring proxy (see the comment on findSimilar(long, int)).
 */
class VectorDuplicateSearchTest {

    private final AnnotationTransactionAttributeSource transactions = new AnnotationTransactionAttributeSource();

    @Test
    void everyPublicSearchMethodIsTransactional() throws Exception {
        for (var method : new java.lang.reflect.Method[]{
                VectorDuplicateSearch.class.getMethod("findSimilar", long.class, int.class),
                VectorDuplicateSearch.class.getMethod("findSimilar", long.class, int.class, boolean.class),
                VectorDuplicateSearch.class.getMethod("findNeighbours", long.class, int.class),
                VectorDuplicateSearch.class.getMethod("findNeighbours", long.class, int.class, boolean.class)}) {
            assertThat(transactions.getTransactionAttribute(method, VectorDuplicateSearch.class))
                    .as(method.toString()).isNotNull();
        }
    }
}
