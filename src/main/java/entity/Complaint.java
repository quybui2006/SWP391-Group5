package entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "complaints")
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_code", nullable = false, unique = true, length = 40)
    private String complaintCode;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "reason_type", nullable = false, length = 40)
    private String reasonType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "evidence_image_url", length = 1000)
    private String evidenceImageUrl;

    @Column(name = "priority", nullable = false, length = 20)
    private String priority = "NORMAL";

    @Column(name = "status", nullable = false, length = 25)
    private String status = "PENDING_OPERATIONS";

    @Column(name = "source_type", nullable = false, length = 25)
    private String sourceType = "CUSTOMER";

    @Column(name = "desired_resolution", length = 30)
    private String desiredResolution;

    @Column(name = "requested_refund_amount", nullable = false)
    private Long requestedRefundAmount = 0L;

    @Column(name = "final_refund_amount", nullable = false)
    private Long finalRefundAmount = 0L;

    @Column(name = "replacement_order_id")
    private Long replacementOrderId;

    @Column(name = "voucher_id")
    private Long voucherId;

    @Column(name = "voucher_code", length = 50)
    private String voucherCode;

    @Column(name = "shop_decision", length = 25)
    private String shopDecision;

    @Column(name = "shop_response", length = 1500)
    private String shopResponse;

    @Column(name = "shop_responded_at")
    private LocalDateTime shopRespondedAt;

    @Column(name = "operations_decision", length = 25)
    private String operationsDecision;

    @Column(name = "operations_note", length = 1500)
    private String operationsNote;

    @Column(name = "operations_reviewed_at")
    private LocalDateTime operationsReviewedAt;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "shop_remind_count", nullable = false)
    private Integer shopRemindCount = 0;

    @Column(name = "last_reminded_at")
    private LocalDateTime lastRemindedAt;

    @Column(name = "cs_shop_request_note", length = 1000)
    private String csShopRequestNote;

    @Column(name = "resolution_type", length = 40)
    private String resolutionType;

    @Column(name = "resolution_note", length = 1500)
    private String resolutionNote;

    @Column(name = "compensated_points", nullable = false)
    private Long compensatedPoints = 0L;

    @Column(name = "is_shop_fault", nullable = false)
    private Integer isShopFault = 0;

    @Column(name = "fault_party", nullable = false, length = 25)
    private String faultParty = "SHOP";

    @Column(name = "refund_method", length = 30)
    private String refundMethod = "BANK_TRANSFER";

    @Column(name = "refund_status", length = 25)
    private String refundStatus = "NONE";

    @Column(name = "refund_completed_at")
    private LocalDateTime refundCompletedAt;

    @Column(name = "escalated_to_ops", nullable = false)
    private Integer escalatedToOps = 0;

    @Column(name = "sla_due_at", nullable = false)
    private LocalDateTime slaDueAt;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Complaint() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getComplaintCode() { return complaintCode; }
    public void setComplaintCode(String complaintCode) { this.complaintCode = complaintCode; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getReasonType() { return reasonType; }
    public void setReasonType(String reasonType) { this.reasonType = reasonType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getEvidenceImageUrl() { return evidenceImageUrl; }
    public void setEvidenceImageUrl(String evidenceImageUrl) { this.evidenceImageUrl = evidenceImageUrl; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getDesiredResolution() { return desiredResolution; }
    public void setDesiredResolution(String desiredResolution) { this.desiredResolution = desiredResolution; }

    public Long getRequestedRefundAmount() { return requestedRefundAmount; }
    public void setRequestedRefundAmount(Long requestedRefundAmount) { this.requestedRefundAmount = requestedRefundAmount; }

    public Long getFinalRefundAmount() { return finalRefundAmount; }
    public void setFinalRefundAmount(Long finalRefundAmount) { this.finalRefundAmount = finalRefundAmount; }

    public Long getReplacementOrderId() { return replacementOrderId; }
    public void setReplacementOrderId(Long replacementOrderId) { this.replacementOrderId = replacementOrderId; }

    public Long getVoucherId() { return voucherId; }
    public void setVoucherId(Long voucherId) { this.voucherId = voucherId; }

    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }

    public String getShopDecision() { return shopDecision; }
    public void setShopDecision(String shopDecision) { this.shopDecision = shopDecision; }

    public String getShopResponse() { return shopResponse; }
    public void setShopResponse(String shopResponse) { this.shopResponse = shopResponse; }

    public LocalDateTime getShopRespondedAt() { return shopRespondedAt; }
    public void setShopRespondedAt(LocalDateTime shopRespondedAt) { this.shopRespondedAt = shopRespondedAt; }

    public String getOperationsDecision() { return operationsDecision; }
    public void setOperationsDecision(String operationsDecision) { this.operationsDecision = operationsDecision; }

    public String getOperationsNote() { return operationsNote; }
    public void setOperationsNote(String operationsNote) { this.operationsNote = operationsNote; }

    public LocalDateTime getOperationsReviewedAt() { return operationsReviewedAt; }
    public void setOperationsReviewedAt(LocalDateTime operationsReviewedAt) { this.operationsReviewedAt = operationsReviewedAt; }

    public LocalDateTime getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(LocalDateTime escalatedAt) { this.escalatedAt = escalatedAt; }

    public Integer getShopRemindCount() { return shopRemindCount; }
    public void setShopRemindCount(Integer shopRemindCount) { this.shopRemindCount = shopRemindCount; }

    public LocalDateTime getLastRemindedAt() { return lastRemindedAt; }
    public void setLastRemindedAt(LocalDateTime lastRemindedAt) { this.lastRemindedAt = lastRemindedAt; }

    public String getCsShopRequestNote() { return csShopRequestNote; }
    public void setCsShopRequestNote(String csShopRequestNote) { this.csShopRequestNote = csShopRequestNote; }

    public String getResolutionType() { return resolutionType; }
    public void setResolutionType(String resolutionType) { this.resolutionType = resolutionType; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public Long getCompensatedPoints() { return compensatedPoints; }
    public void setCompensatedPoints(Long compensatedPoints) { this.compensatedPoints = compensatedPoints; }

    public Integer getIsShopFault() { return isShopFault; }
    public void setIsShopFault(Integer isShopFault) { this.isShopFault = isShopFault; }

    public String getFaultParty() { return faultParty; }
    public void setFaultParty(String faultParty) { this.faultParty = faultParty; }

    public String getRefundMethod() { return refundMethod; }
    public void setRefundMethod(String refundMethod) { this.refundMethod = refundMethod; }

    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }

    public LocalDateTime getRefundCompletedAt() { return refundCompletedAt; }
    public void setRefundCompletedAt(LocalDateTime refundCompletedAt) { this.refundCompletedAt = refundCompletedAt; }

    public Integer getEscalatedToOps() { return escalatedToOps; }
    public void setEscalatedToOps(Integer escalatedToOps) { this.escalatedToOps = escalatedToOps; }

    public LocalDateTime getSlaDueAt() { return slaDueAt; }
    public void setSlaDueAt(LocalDateTime slaDueAt) { this.slaDueAt = slaDueAt; }

    public Long getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Long assignedTo) { this.assignedTo = assignedTo; }

    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
