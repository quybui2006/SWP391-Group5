package dto.customer;

import java.util.List;

public class ComplaintRequestDTO {
    private Long orderId;
    private Long orderItemId;
    private String category;
    private String description;
    private String desiredResolution;
    private List<String> evidenceImages;

    public ComplaintRequestDTO() {
    }

    public ComplaintRequestDTO(Long orderId, Long orderItemId, String category, String description, String desiredResolution, List<String> evidenceImages) {
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.category = category;
        this.description = description;
        this.desiredResolution = desiredResolution;
        this.evidenceImages = evidenceImages;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDesiredResolution() {
        return desiredResolution;
    }

    public void setDesiredResolution(String desiredResolution) {
        this.desiredResolution = desiredResolution;
    }

    public List<String> getEvidenceImages() {
        return evidenceImages;
    }

    public void setEvidenceImages(List<String> evidenceImages) {
        this.evidenceImages = evidenceImages;
    }
}
