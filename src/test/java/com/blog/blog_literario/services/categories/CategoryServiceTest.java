package com.blog.blog_literario.services.categories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.blog.blog_literario.dto.categories.CategoryResponse;
import com.blog.blog_literario.dto.categories.CreateCategoryRequest;
import com.blog.blog_literario.dto.categories.UpdateCategoryRequest;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.repositories.CategoryRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;

    @InjectMocks CategoryService categoryService;

    @Test
    void getAllCategories_returnsMappedList() {
        Category category = new Category("Fiction", "fiction");
        category.setId(1);
        given(categoryRepository.findAll()).willReturn(List.of(category));

        List<CategoryResponse> result = categoryService.getAllCategories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Fiction");
        assertThat(result.get(0).slug()).isEqualTo("fiction");
    }

    @Test
    void getCategoryById_existing_returnsCategoryResponse() {
        Category category = new Category("Fiction", "fiction");
        category.setId(1);
        given(categoryRepository.findById(1)).willReturn(Optional.of(category));

        CategoryResponse result = categoryService.getCategoryById(1);

        assertThat(result.id()).isEqualTo(1);
        assertThat(result.name()).isEqualTo("Fiction");
    }

    @Test
    void getCategoryById_nonExistent_throwsResourceNotFoundException() {
        given(categoryRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(99))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createCategory_uniqueName_generatesSlugAndSaves() {
        CreateCategoryRequest request = new CreateCategoryRequest("Ciencia Ficción");
        given(categoryRepository.findByName("Ciencia Ficción")).willReturn(Optional.empty());
        given(categoryRepository.save(any(Category.class))).willAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse result = categoryService.createCategory(request);

        assertThat(result.name()).isEqualTo("Ciencia Ficción");
        assertThat(result.slug()).isEqualTo("ciencia-ficcion");
    }

    @Test
    void createCategory_duplicateName_throwsIllegalStateException() {
        Category existing = new Category("Fiction", "fiction");
        given(categoryRepository.findByName("Fiction")).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> categoryService.createCategory(new CreateCategoryRequest("Fiction")))
                .isInstanceOf(IllegalStateException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_nonExistentId_throwsResourceNotFoundException() {
        given(categoryRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(99, new UpdateCategoryRequest("New Name")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCategory_newSlugCollides_throwsIllegalStateException() {
        Category existing = new Category("Fiction", "fiction");
        existing.setId(1);
        given(categoryRepository.findById(1)).willReturn(Optional.of(existing));
        given(categoryRepository.existsBySlug("drama")).willReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(1, new UpdateCategoryRequest("Drama")))
                .isInstanceOf(IllegalStateException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_sameSlugAsExisting_skipsCollisionCheck() {
        Category existing = new Category("Fiction", "fiction");
        existing.setId(1);
        given(categoryRepository.findById(1)).willReturn(Optional.of(existing));
        given(categoryRepository.save(existing)).willReturn(existing);

        categoryService.updateCategory(1, new UpdateCategoryRequest("Fiction"));

        verify(categoryRepository, never()).existsBySlug(any());
    }

    @Test
    void updateCategory_validUpdate_savesWithNewNameAndSlug() {
        Category existing = new Category("Fiction", "fiction");
        existing.setId(1);
        given(categoryRepository.findById(1)).willReturn(Optional.of(existing));
        given(categoryRepository.existsBySlug("drama")).willReturn(false);
        given(categoryRepository.save(existing)).willReturn(existing);

        CategoryResponse result = categoryService.updateCategory(1, new UpdateCategoryRequest("Drama"));

        assertThat(result.name()).isEqualTo("Drama");
        assertThat(result.slug()).isEqualTo("drama");
    }

    @Test
    void deleteCategory_existing_deletesById() {
        given(categoryRepository.existsById(1)).willReturn(true);

        categoryService.deleteCategory(1);

        verify(categoryRepository).deleteById(1);
    }

    @Test
    void deleteCategory_nonExistent_throwsResourceNotFoundException() {
        given(categoryRepository.existsById(99)).willReturn(false);

        assertThatThrownBy(() -> categoryService.deleteCategory(99))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(categoryRepository, never()).deleteById(any());
    }
}
