/**
 * 目的：提供 AgentChatHistory 的基本 CRUD 功能
 * 關鍵項目：
 * 1. 繼承 CrudRepository
 * 2. 定義根據時間排序及分頁的查詢方法供 Web UI 載入歷史對話
 * 模組：history
 */
package com.github.nicholas23.majordomo.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentChatHistoryRepository extends CrudRepository<AgentChatHistory, Long> {

    // WHY: 供 Web UI 以時間降冪排序取得近期的對話，再於前端反轉為從上到下舊到新的對話流
    Page<AgentChatHistory> findAllByOrderByCreateAtDesc(Pageable pageable);
    
    // WHY: 取得最近 N 筆對話紀錄，由舊到新排序 (給直接需要正序的小區塊)
    List<AgentChatHistory> findTop50ByOrderByCreateAtDesc();
}

/* ### Review Checklist ###
 * 1. 查詢效能：已根據時間反向排序查詢需求設計 Repository 方法？ ✓
 * 2. 分頁支援：使用 Pageable 進行大資料量查詢？ ✓
 */
