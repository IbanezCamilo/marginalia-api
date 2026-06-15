package com.blog.blog_literario.services.categories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.blog.blog_literario.dto.categories.CategoryResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.repositories.CategoryRepository;

@ExtendWith(MockitoExtension.class)
class PublicCategoryServiceTest {

    @Mock CategoryRepository categoryRepository;

    @InjectMocks PublicCategoryService publicCategoryService;

    @Test
    void listCategories_returnsMappedList() {
        Category category = new Category("Fiction", "fiction");
        category.setId(1);
        given(categoryRepository.findAll()).willReturn(List.of(category));

        List<CategoryResponse> result = publicCategoryService.listCategories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Fiction");
    }

    @Test
    void listCategories_emptyRepository_returnsEmptyList() {
        given(categoryRepository.findAll()).willReturn(List.of());

        List<CategoryResponse> result = publicCategoryService.listCategories();

        assertThat(result).isEmpty();
    }

    @Test
    void getBySlug_existingSlug_returnsCategoryResponse() {
        Category category = new Category("Fiction", "fiction");
        category.setId(1);
        given(categoryRepository.findBySlug("fiction")).willReturn(Optional.of(category));

        CategoryResponse result = publicCategoryService.getBySlug("fiction");

        assertThat(result.slug()).isEqualTo("fiction");
        assertThat(result.name()).isEqualTo("Fiction");
    }

    @Test
    void getBySlug_nonExistentSlug_throwsResourceNotFoundException() {
        given(categoryRepository.findBySlug("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> publicCategoryService.getBySlug("unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
