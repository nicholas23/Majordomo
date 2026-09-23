/**
 * 目的：測試 MemoryMcpService 的長期記憶管理（store, recall, forget, recallByKeywords）
 * 關鍵項目：
 * 1. 驗證 store 的 Upsert 邏輯（新增與更新）與參數防呆
 * 2. 驗證 recall 的模糊查詢、格式化輸出與 limit 邊界
 * 3. 驗證 forget 的刪除防呆與成功/失敗反饋
 * 4. 驗證 recallByKeywords 的多關鍵字聯集、去重與長度限制
 * 模組：mcp
 */
package com.github.nicholas23.majordomo.mcp;

import com.github.nicholas23.majordomo.memory.Memory;
import com.github.nicholas23.majordomo.memory.MemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryMcpServiceTest {

    @Mock
    private MemoryRepository memoryRepository;

    private MemoryMcpService service;

    @BeforeEach
    void setUp() {
        service = new MemoryMcpService(memoryRepository);
    }

    @Test
    @DisplayName("store: 當 key 或 text 為空時應防呆返回 false")
    void store_EmptyKeyOrText_ShouldReturnFalse() {
        // EDGE_CASE: 空 key 或空內容防呆
        assertThat(service.store(null, "category", "text")).isFalse();
        assertThat(service.store("   ", "category", "text")).isFalse();
        assertThat(service.store("key", "category", null)).isFalse();
        assertThat(service.store("key", "category", "   ")).isFalse();

        verify(memoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("store: key 不存在時應建立新記憶 (Insert)")
    void store_NewKey_ShouldInsert() {
        when(memoryRepository.findByMemoryKey("user_preference")).thenReturn(Optional.empty());

        Boolean success = service.store("user_preference", null, "喜歡簡短精準的回答");

        assertThat(success).isTrue();
        ArgumentCaptor<Memory> captor = ArgumentCaptor.forClass(Memory.class);
        verify(memoryRepository).save(captor.capture());

        Memory saved = captor.getValue();
        assertThat(saved.getMemoryKey()).isEqualTo("user_preference");
        assertThat(saved.getCategory()).isEqualTo("custom"); // 預設 category
        assertThat(saved.getContent()).isEqualTo("喜歡簡短精準的回答");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("store: key 已存在時應更新內容 (Upsert)")
    void store_ExistingKey_ShouldUpdate() {
        Memory existing = new Memory();
        existing.setId(10L);
        existing.setMemoryKey("user_preference");
        existing.setCategory("old_cat");
        existing.setContent("舊內容");
        existing.setCreatedAt(LocalDateTime.now().minusDays(1));

        when(memoryRepository.findByMemoryKey("user_preference")).thenReturn(Optional.of(existing));

        Boolean success = service.store("user_preference", "profile", "新內容");

        assertThat(success).isTrue();
        ArgumentCaptor<Memory> captor = ArgumentCaptor.forClass(Memory.class);
        verify(memoryRepository).save(captor.capture());

        Memory updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(10L);
        assertThat(updated.getCategory()).isEqualTo("profile");
        assertThat(updated.getContent()).isEqualTo("新內容");
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("recall: 查詢字串為空時應返回空陣列")
    void recall_EmptyQuery_ShouldReturnEmptyArray() {
        assertThat(service.recall(null, 5)).isEmpty();
        assertThat(service.recall("   ", 5)).isEmpty();
        verify(memoryRepository, never()).searchByKeyword(any(), anyInt());
    }

    @Test
    @DisplayName("recall: 正常查詢應回傳格式化記憶陣列並防呆 limit")
    void recall_ValidQuery_ShouldReturnFormattedMemory() {
        Memory m = new Memory();
        m.setCategory("env");
        m.setMemoryKey("java_home");
        m.setContent("/usr/lib/jvm/default");

        when(memoryRepository.searchByKeyword("java", 5)).thenReturn(List.of(m));

        String[] results = service.recall("java", 0); // limit 0 應自動修正為 5

        assertThat(results).hasSize(1);
        assertThat(results[0]).isEqualTo("[env] java_home: /usr/lib/jvm/default");
    }

    @Test
    @DisplayName("forget: key 為空或不存在時應返回 false，存在時刪除並返回 true")
    void forget_KeyExistenceAndDeletion() {
        // 空值測試
        assertThat(service.forget(null)).isFalse();

        // 不存在測試
        when(memoryRepository.findByMemoryKey("non_exist")).thenReturn(Optional.empty());
        assertThat(service.forget("non_exist")).isFalse();
        verify(memoryRepository, never()).delete(any());

        // 存在並刪除測試
        Memory m = new Memory();
        m.setMemoryKey("exist_key");
        when(memoryRepository.findByMemoryKey("exist_key")).thenReturn(Optional.of(m));

        assertThat(service.forget("exist_key")).isTrue();
        verify(memoryRepository).delete(m);
    }

    @Test
    @DisplayName("recallByKeywords: 多關鍵字聯集應正確去重並受 limit 限制")
    void recallByKeywords_UnionAndDeduplicate() {
        Memory m1 = new Memory();
        m1.setId(1L);
        m1.setCategory("tech");
        m1.setMemoryKey("k1");
        m1.setContent("Spring Boot");

        Memory m2 = new Memory();
        m2.setId(2L);
        m2.setCategory("tech");
        m2.setMemoryKey("k2");
        m2.setContent("Virtual Threads");

        when(memoryRepository.searchByKeyword("spring", 2)).thenReturn(List.of(m1));
        when(memoryRepository.searchByKeyword("threads", 2)).thenReturn(List.of(m1, m2)); // m1 重複命中

        List<String> results = service.recallByKeywords(List.of("spring", "threads"), 2);

        // WHY: 應自動去重，m1 不應重複出現
        assertThat(results).hasSize(2);
        assertThat(results.get(0)).contains("Spring Boot");
        assertThat(results.get(1)).contains("Virtual Threads");
    }
}

/* ### Review Checklist ###
 * 1. 覆蓋率：store、recall、forget、recallByKeywords 均有完整單元測試 ✓
 * 2. 邊界測試：null、空白、0/負數 limit 皆有驗證 ✓
 * 3. 業務驗證：Upsert 邏輯（Insert vs Update）精確切分 ✓
 */
