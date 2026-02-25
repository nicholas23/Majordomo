package com.github.nicholas23.majordomo.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 目的：測試 WorkspaceService 的業務邏輯，包含路徑驗證、CRUD 與軟刪除。
 */
@ExtendWith(MockitoExtension.class)
public class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @InjectMocks
    private WorkspaceService workspaceService;

    @TempDir
    Path tempDir;

    private String validPath;

    @BeforeEach
    void setUp() {
        validPath = tempDir.toAbsolutePath().toString();
    }

    @Test
    void testNewWorkspace_WithValidPath_ShouldSaveSuccessfully() {
        // Arrange
        String name = "Test Workspace";
        String desc = "Description";
        Workspace mockSaved = new Workspace();
        mockSaved.setId(1L);
        mockSaved.setName(name);
        mockSaved.setAbsolutePath(validPath);

        when(workspaceRepository.save(any(Workspace.class))).thenReturn(mockSaved);

        // Act
        Workspace result = workspaceService.newWorkspace(name, desc, validPath);

        // Assert
        assertNotNull(result);
        assertEquals(name, result.getName());
        assertEquals(validPath, result.getAbsolutePath());
        verify(workspaceRepository).save(any(Workspace.class));
    }

    @Test
    void testNewWorkspace_WithInvalidPath_ShouldThrowException() {
        // Arrange
        String invalidPath = "/non/existent/path";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            workspaceService.newWorkspace("Invalid", "Desc", invalidPath));
    }

    @Test
    void testNewWorkspace_WithPathIsFile_ShouldThrowException() throws Exception {
        // Arrange
        File file = new File(tempDir.toFile(), "test.txt");
        file.createNewFile();
        String filePath = file.getAbsolutePath();

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            workspaceService.newWorkspace("File", "Desc", filePath));
    }

    @Test
    void testGetWorkspace_WhenExists_ShouldReturnWorkspace() {
        // Arrange
        long id = 1L;
        Workspace ws = new Workspace();
        ws.setId(id);
        when(workspaceRepository.findById(id)).thenReturn(Optional.of(ws));

        // Act
        Workspace result = workspaceService.getWorkspace(id);

        // Assert
        assertNotNull(result);
        assertEquals(id, result.getId());
    }

    @Test
    void testGetWorkspace_WhenNotExists_ShouldThrowException() {
        // Arrange
        when(workspaceRepository.findById(anyLong())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(java.util.NoSuchElementException.class, () -> 
            workspaceService.getWorkspace(99L));
    }

    @Test
    void testList_ShouldReturnPageOfWorkspaces() {
        // Arrange
        PageRequest pageRequest = PageRequest.of(0, 5);
        Workspace ws = new Workspace();
        Page<Workspace> mockPage = new PageImpl<>(Collections.singletonList(ws), pageRequest, 1);
        when(workspaceRepository.findAll(any(PageRequest.class))).thenReturn(mockPage);

        // Act
        Page<Workspace> result = workspaceService.list(0, 5);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(workspaceRepository).findAll(eq(pageRequest));
    }

    @Test
    void testDeleteWorkspace_ShouldPerformSoftDelete() {
        // Arrange
        long id = 1L;
        Workspace ws = new Workspace();
        ws.setId(id);
        ws.setActive(true);
        when(workspaceRepository.findById(id)).thenReturn(Optional.of(ws));

        // Act
        workspaceService.deleteWorkspace(id);

        // Assert
        assertFalse(ws.getActive());
        verify(workspaceRepository).save(ws);
        assertNotNull(ws.getUpdateAt());
    }
}
