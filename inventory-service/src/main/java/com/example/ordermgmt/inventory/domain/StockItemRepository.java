package com.example.ordermgmt.inventory.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link StockItem}. */
public interface StockItemRepository extends JpaRepository<StockItem, String> {

    /**
     * Loads stock rows and locks them ({@code SELECT ... FOR UPDATE}) until the transaction ends.
     *
     * <p>Without the lock, two orders reserving the last unit at the same time could both read
     * "1 available" and both succeed (overselling). Rows are always returned sorted by id so two
     * transactions locking several products acquire the locks in the same order, which prevents
     * deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockItem s where s.productId in :ids order by s.productId")
    List<StockItem> findAllForUpdate(@Param("ids") Collection<String> ids);
}
