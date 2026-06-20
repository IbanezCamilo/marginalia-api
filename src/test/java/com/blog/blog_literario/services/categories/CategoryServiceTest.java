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
import com.blog.blog_literario.repositories.PostRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @Mock PostRepository postRepository;

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
    void createCategory_nameUniqueButSlugCollides_throwsIllegalStateException() {
        // "Sci-Fi" and "Sci Fi" are different names but normalize to the same slug
        given(categoryRepository.findByName("Sci-Fi")).willReturn(Optional.empty());
        given(categoryRepository.existsBySlug("sci-fi")).willReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(new CreateCategoryRequest("Sci-Fi")))
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
        given(categoryRepository.findByName("Drama")).willReturn(Optional.empty());
        given(categoryRepository.existsBySlug("drama")).willReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(1, new UpdateCategoryRequest("Drama")))
                .isInstanceOf(IllegalStateException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_newNameCollidesWithAnotherCategory_throwsIllegalStateException() {
        Category existing = new Category("Fiction", "fiction");
        existing.setId(1);
        Category other = new Category("Drama", "drama");
        other.setId(2);
        given(categoryRepository.findById(1)).willReturn(Optional.of(existing));
        given(categoryRepository.findByName("Drama")).willReturn(Optional.of(other));

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
        given(categoryRepository.findByName("Drama")).willReturn(Optional.empty());
        given(categoryRepository.existsBySlug("drama")).willReturn(false);
        given(categoryRepository.save(existing)).willReturn(existing);

        CategoryResponse result = categoryService.updateCategory(1, new UpdateCategoryRequest("Drama"));

        assertThat(result.name()).isEqualTo("Drama");
        assertThat(result.slug()).isEqualTo("drama");
    }

    @Test
    void deleteCategory_existingNoPosts_deletesById() {
        given(categoryRepository.existsById(1)).willReturn(true);
        given(postRepository.countByCategoryId(1)).willReturn(0L);

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

    @Test
    void deleteCategory_hasReferencingPosts_throwsIllegalState_andDoesNotDelete() {
        given(categoryRepository.existsById(1)).willReturn(true);
        given(postRepository.countByCategoryId(1)).willReturn(3L);

        assertThatThrownBy(() -> categoryService.deleteCategory(1))
                .isInstanceOf(IllegalStateException.class);

        verify(categoryRepository, never()).deleteById(any());
    }
}
