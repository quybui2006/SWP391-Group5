package service;

public class PromotionValidationException extends RuntimeException {
    private final String field;
    public PromotionValidationException(String field, String message) {
        super(message); this.field = field;
    }
    public String getField() { return field; }
}
