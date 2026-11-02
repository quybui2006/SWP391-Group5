package service.admin;

import entity.Category;
import repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminCategoryService {

    private final CategoryRepository categories;

    public AdminCategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categories.findAll();
    }

    @Transactional(readOnly = true)
    public List<Category> findActive() {
        return categories.findByIsActiveTrue();
    }

    @Transactional(readOnly = true)
    public Category findById(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục."));
    }

    @Transactional
    public Category create(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new IllegalArgumentException("Tên danh mục phải từ 1 đến 100 ký tự.");
        }
        String safeName = name.trim();
        if (categories.existsByNameIgnoreCase(safeName)) {
            throw new IllegalArgumentException("Danh mục này đã tồn tại.");
        }
        Category category = new Category();
        category.setName(safeName);
        category.setIsActive(true);
        return categories.save(category);
    }

    @Transactional
    public void toggleActive(Long id) {
        Category category = findById(id);
        category.setIsActive(!Boolean.TRUE.equals(category.getIsActive()));
        categories.save(category);
    }
}
