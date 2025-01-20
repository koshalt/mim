package org.motechproject.nms.imi.domain;

import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;
import org.motechproject.nms.imi.service.impl.CsrHelper;
import org.motechproject.nms.kilkari.dto.CallSummaryRecordDto;
import org.motechproject.nms.kilkari.exception.InvalidCallRecordDataException;
import org.motechproject.nms.props.domain.RequestId;
import org.motechproject.nms.props.domain.WhatsAppOptInStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jdo.annotations.Index;
import javax.jdo.annotations.Unique;

/**
 * See NMS API - 4.4.2 CDR Summary File Format
 */
@Entity(tableName = "nms_imi_csrs")
@Unique(name = "unique_requestId", members = { "requestId"})
@Index(members = { "requestId" })
public class CallSummaryRecord {

    @Field
    private String requestId;

    @Field
    private String serviceId;

    @Field
    private Long msisdn;

    @Field
    private String cli;

    @Field
    private Integer priority;

    @Field
    private String callFlowUrl;

    @Field
    private String contentFileName;

    @Field
    private String weekId;

    @Field
    private String languageLocationCode;

    @Field
    private String circle;

    @Field
    private Integer finalStatus;

    @Field
    private Integer statusCode;

    @Field
    private Integer attempts;

    @Field
    private boolean opt_in_call_eligibility;

    @Field
    private String opt_in_input;

    private static final Logger LOGGER = LoggerFactory.getLogger(CallSummaryRecord.class);

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public Long getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(Long msisdn) {
        this.msisdn = msisdn;
    }

    public String getCli() {
        return cli;
    }

    public void setCli(String cli) {
        this.cli = cli;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getCallFlowUrl() {
        return callFlowUrl;
    }

    public void setCallFlowUrl(String callFlowUrl) {
        this.callFlowUrl = callFlowUrl;
    }

    public String getContentFileName() {
        return contentFileName;
    }

    public void setContentFileName(String contentFileName) {
        this.contentFileName = contentFileName;
    }

    public String getWeekId() {
        return weekId;
    }

    public void setWeekId(String weekId) {
        this.weekId = weekId;
    }

    public String getLanguageLocationCode() {
        return languageLocationCode;
    }

    public void setLanguageLocationCode(String languageLocationCode) {
        this.languageLocationCode = languageLocationCode;
    }

    public String getCircle() {
        return circle;
    }

    public void setCircle(String circle) {
        this.circle = circle;
    }

    public Integer getFinalStatus() {
        return finalStatus;
    }

    public void setFinalStatus(Integer finalStatus) {
        this.finalStatus = finalStatus;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public boolean isOpt_in_call_eligibility() {return opt_in_call_eligibility;}

    public void setOpt_in_call_eligibility(boolean opt_in_call_eligibility) {this.opt_in_call_eligibility = opt_in_call_eligibility;}

    public String getOpt_in_input() {return opt_in_input;}

    public void setOpt_in_input(String opt_in_input) {this.opt_in_input = opt_in_input;}

    public CallSummaryRecordDto toDto() {
        LOGGER.info("Inside DTO");
        String subscriptionId;
        String timestamp;
        LOGGER.info("OptInInputValue");
        try {
            RequestId r = RequestId.fromString(requestId);
            subscriptionId = r.getSubscriptionId();
            timestamp = r.getTimestamp();
            LOGGER.info("Before OptInInputValue ");
        } catch (IllegalArgumentException e) {
            throw new InvalidCallRecordDataException(e);
        }
        return new CallSummaryRecordDto(
                subscriptionId,
                statusCode,
                finalStatus,
                contentFileName,
                weekId,
                languageLocationCode,
                circle,
                timestamp,
                opt_in_call_eligibility,
                opt_in_input,
                serviceId
        );
    }
}
